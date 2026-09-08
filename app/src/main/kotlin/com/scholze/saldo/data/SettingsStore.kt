package com.scholze.saldo.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.scholze.saldo.domain.BackupConfig
import com.scholze.saldo.domain.Cadencia
import com.scholze.saldo.domain.CapturaConfig
import com.scholze.saldo.domain.CartaoConfig
import com.scholze.saldo.domain.LembretesConfig
import java.io.IOException
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

enum class Tema { SISTEMA, CLARO, ESCURO }

data class Settings(
    val saldoInicialCentavos: Long?,
    val saldoInicialData: LocalDate?,
    val cartao: CartaoConfig,
    val comecarOculto: Boolean,
    val tema: Tema,
    /** O widget mostra dinheiro na tela inicial? Padrão `false`: `R$ •••••` até o usuário optar. */
    val widgetMostrarValores: Boolean = false,
    val lembretes: LembretesConfig = LembretesConfig(),
    val captura: CapturaConfig = CapturaConfig(),
    /**
     * O backup automático. **Não vai no dump exportado**: a pasta e a permissão são deste aparelho,
     * e um arquivo restaurado noutro celular apontaria para uma árvore que ele não pode escrever.
     */
    val backup: BackupConfig = BackupConfig(),
)

class SettingsStore(private val dataStore: DataStore<Preferences>) {

    private object Keys {
        val saldoInicial = longPreferencesKey("saldo_inicial_centavos")
        val saldoInicialData = longPreferencesKey("saldo_inicial_epoch_day")
        val cartaoNome = stringPreferencesKey("cartao_nome")
        val cartaoFechamento = intPreferencesKey("cartao_fechamento_dia")
        val cartaoVencimento = intPreferencesKey("cartao_vencimento_dia")
        val comecarOculto = booleanPreferencesKey("comecar_oculto")
        val tema = stringPreferencesKey("tema")
        val widgetMostrarValores = booleanPreferencesKey("widget_mostrar_valores")
        val lembreteFaturaAmanha = booleanPreferencesKey("lembrete_fatura_amanha")
        val lembreteRecorrenciaHoje = booleanPreferencesKey("lembrete_recorrencia_hoje")
        val lembreteRegistrarGastos = booleanPreferencesKey("lembrete_registrar_gastos")
        val lembreteFechamentoMes = booleanPreferencesKey("lembrete_fechamento_mes")
        /** Minutos desde a meia-noite (0..1439). */
        val lembretesHoraInformativos = intPreferencesKey("lembretes_hora_informativos")
        val lembretesHoraNudge = intPreferencesKey("lembretes_hora_nudge")
        val capturaLigada = booleanPreferencesKey("captura_ligada")
        val capturaMarcados = stringSetPreferencesKey("captura_marcados")
        val capturaVistos = stringSetPreferencesKey("captura_vistos")
        val backupPastaUri = stringPreferencesKey("backup_pasta_uri")
        val backupCadencia = stringPreferencesKey("backup_cadencia")
        val backupUltimoSucessoEpochDay = longPreferencesKey("backup_ultimo_sucesso_epoch_day")
        val backupUltimoErro = stringPreferencesKey("backup_ultimo_erro")
        val backupUltimoErroEpochDay = longPreferencesKey("backup_ultimo_erro_epoch_day")
    }

    // Um disco ilegível não pode travar a primeira composição: o app cai nos defaults.
    val settings: Flow<Settings> = dataStore.data.catch { e ->
        if (e is IOException) emit(emptyPreferences()) else throw e
    }.map { p ->
        Settings(
            saldoInicialCentavos = p[Keys.saldoInicial],
            saldoInicialData = p[Keys.saldoInicialData]?.let(LocalDate::ofEpochDay),
            cartao = CartaoConfig(
                nome = p[Keys.cartaoNome] ?: CartaoConfig().nome,
                fechamentoDia = p[Keys.cartaoFechamento] ?: CartaoConfig().fechamentoDia,
                vencimentoDia = p[Keys.cartaoVencimento] ?: CartaoConfig().vencimentoDia,
            ),
            comecarOculto = p[Keys.comecarOculto] ?: true,
            // Tolerante a um valor gravado por uma versão futura/antiga do enum.
            tema = p[Keys.tema]?.let { v -> Tema.entries.find { it.name == v } } ?: Tema.SISTEMA,
            widgetMostrarValores = p[Keys.widgetMostrarValores] ?: false,
            lembretes = p.lembretes(),
            captura = p.captura(),
            backup = p.backup(),
        )
    }

    /**
     * Igual à leitura de `lembretes` em [settings], mas SEM engolir `IOException` num disco
     * ilegível. Usada só pelo `LembretesWorker`: ele precisa diferenciar "não deu pra ler"
     * (infraestrutura — `retry`) de "o usuário desligou tudo de propósito" (produto — reagenda
     * com tudo off, o que mataria o loop do slot se fosse confundido com o primeiro caso).
     * [settings] cai nos defaults porque não pode travar a primeira composição; o worker não tem
     * essa pressa e pode se dar ao luxo de tentar de novo.
     */
    suspend fun lerLembretes(): LembretesConfig = dataStore.data.first().lembretes()

    private fun Preferences.captura(): CapturaConfig = CapturaConfig(
        ligada = this[Keys.capturaLigada] ?: false,
        marcados = this[Keys.capturaMarcados].orEmpty(),
        vistos = this[Keys.capturaVistos].orEmpty(),
    )

    /** Sem engolir IOException, pela mesma razão de [lerLembretes]: o listener precisa saber. */
    suspend fun lerCaptura(): CapturaConfig = dataStore.data.first().captura()

    suspend fun definirCapturaLigada(v: Boolean) {
        dataStore.edit { it[Keys.capturaLigada] = v }
    }

    suspend fun definirAppMarcado(pacote: String, marcado: Boolean) {
        dataStore.edit { p ->
            val marcados = p[Keys.capturaMarcados].orEmpty()
            p[Keys.capturaMarcados] = if (marcado) marcados + pacote else marcados - pacote
        }
    }

    /**
     * Anota que [pacote] emitiu uma notificação com valor, para ele aparecer na tela
     * esperando a marcação. **Só o nome do pacote** — nada do que veio na notificação.
     *
     * Sai cedo quando já está lá: uma escrita no DataStore por notificação recebida seria
     * absurda, e o listener é chamado para toda notificação do aparelho.
     */
    suspend fun registrarAppVisto(pacote: String) {
        val atuais = dataStore.data.first()[Keys.capturaVistos].orEmpty()
        if (pacote in atuais) return
        dataStore.edit { it[Keys.capturaVistos] = atuais + pacote }
    }

    private fun Preferences.lembretes(): LembretesConfig = LembretesConfig(
        faturaAmanha = this[Keys.lembreteFaturaAmanha] ?: false,
        recorrenciaHoje = this[Keys.lembreteRecorrenciaHoje] ?: false,
        registrarGastos = this[Keys.lembreteRegistrarGastos] ?: false,
        fechamentoMes = this[Keys.lembreteFechamentoMes] ?: false,
        horaInformativos = hora(Keys.lembretesHoraInformativos, LembretesConfig().horaInformativos),
        horaNudge = hora(Keys.lembretesHoraNudge, LembretesConfig().horaNudge),
    )

    private fun Preferences.backup(): BackupConfig = BackupConfig(
        pastaUri = this[Keys.backupPastaUri],
        // Tolerante a uma cadência gravada por uma versão futura do enum, como o tema.
        cadencia = this[Keys.backupCadencia]?.let { v -> Cadencia.entries.find { it.name == v } } ?: BackupConfig().cadencia,
        ultimoSucesso = this[Keys.backupUltimoSucessoEpochDay]?.let(LocalDate::ofEpochDay),
        ultimoErro = this[Keys.backupUltimoErro],
        ultimoErroEm = this[Keys.backupUltimoErroEpochDay]?.let(LocalDate::ofEpochDay),
    )

    /**
     * Sem engolir IOException, pela mesma razão de [lerLembretes]: é por aqui que o `BackupWorker`
     * decide se há pasta para gravar. Por [settings] um disco ilegível viraria `pastaUri = null` —
     * indistinguível de "o usuário não escolheu pasta" — e o worker sairia em silêncio, sem
     * reagendar, matando o trabalho único até o próximo arranque do app. Ilegível é infraestrutura:
     * `retry`.
     */
    suspend fun lerBackup(): BackupConfig = dataStore.data.first().backup()

    suspend fun definirSaldoInicial(centavos: Long, data: LocalDate) {
        dataStore.edit {
            it[Keys.saldoInicial] = centavos
            it[Keys.saldoInicialData] = data.toEpochDay()
        }
    }

    suspend fun definirCartao(config: CartaoConfig) {
        dataStore.edit {
            it[Keys.cartaoNome] = config.nome
            it[Keys.cartaoFechamento] = config.fechamentoDia
            it[Keys.cartaoVencimento] = config.vencimentoDia
        }
    }

    suspend fun definirComecarOculto(v: Boolean) {
        dataStore.edit { it[Keys.comecarOculto] = v }
    }

    suspend fun definirTema(tema: Tema) {
        dataStore.edit { it[Keys.tema] = tema.name }
    }

    suspend fun definirWidgetMostrarValores(v: Boolean) {
        dataStore.edit { it[Keys.widgetMostrarValores] = v }
    }

    suspend fun definirLembretes(config: LembretesConfig) {
        dataStore.edit {
            it[Keys.lembreteFaturaAmanha] = config.faturaAmanha
            it[Keys.lembreteRecorrenciaHoje] = config.recorrenciaHoje
            it[Keys.lembreteRegistrarGastos] = config.registrarGastos
            it[Keys.lembreteFechamentoMes] = config.fechamentoMes
            it[Keys.lembretesHoraInformativos] = config.horaInformativos.toSecondOfDay() / 60
            it[Keys.lembretesHoraNudge] = config.horaNudge.toSecondOfDay() / 60
        }
    }

    /** `null` desliga: sem pasta, nada é agendado e a tela desabilita os outros controles. */
    suspend fun definirPastaBackup(uri: String?) {
        dataStore.edit { p ->
            if (uri == null) p.remove(Keys.backupPastaUri) else p[Keys.backupPastaUri] = uri
            // Trocar de pasta zera o histórico de estado: "último sucesso" da pasta velha não diz
            // nada sobre a nova, e o erro dela ficaria congelado na tela para sempre.
            p.remove(Keys.backupUltimoSucessoEpochDay)
            p.remove(Keys.backupUltimoErro)
            p.remove(Keys.backupUltimoErroEpochDay)
        }
    }

    suspend fun definirCadenciaBackup(cadencia: Cadencia) {
        dataStore.edit { it[Keys.backupCadencia] = cadencia.name }
    }

    /** Um sucesso apaga o erro anterior: a linha da tela mostra um estado, não um histórico. */
    suspend fun registrarBackupOk(data: LocalDate) {
        dataStore.edit {
            it[Keys.backupUltimoSucessoEpochDay] = data.toEpochDay()
            it.remove(Keys.backupUltimoErro)
            it.remove(Keys.backupUltimoErroEpochDay)
        }
    }

    /** O erro fica com a data junto: "falhou" sozinho não diz se foi ontem ou em março. */
    suspend fun registrarBackupErro(mensagem: String, data: LocalDate) {
        dataStore.edit {
            it[Keys.backupUltimoErro] = mensagem
            it[Keys.backupUltimoErroEpochDay] = data.toEpochDay()
        }
    }

    /** Permissão revogada ou pasta apagada: o backup para e a tela pede a pasta de novo. */
    suspend fun desligarBackup(motivo: String, data: LocalDate) {
        dataStore.edit {
            it.remove(Keys.backupPastaUri)
            it[Keys.backupUltimoErro] = motivo
            it[Keys.backupUltimoErroEpochDay] = data.toEpochDay()
        }
    }

    /**
     * Reset das preferências: volta tudo ao default de instalação nova, incluindo o saldo
     * inicial — ou seja, o app cai de volta no onboarding.
     *
     * É uma API de produto, não um utilitário de teste, ainda que hoje só o `EstadoLimpo`
     * dos testes instrumentados a chame; é daqui que sai o "apagar dados" quando ele
     * existir. Note o que ela NÃO faz: o banco de movimentações continua intacto, então
     * um reset sozinho deixaria o ledger com lançamentos e sem saldo inicial — quem
     * chamar precisa limpar o banco também.
     *
     * Passa pela MESMA instância de [DataStore] de propósito: apagar o arquivo por fora
     * não invalida o cache em memória do singleton, então só isto realmente reseta o
     * estado dentro de um processo já em execução.
     */
    suspend fun limpar() {
        dataStore.edit { it.clear() }
    }
}

/** Minutos do dia gravados → hora; um valor fora de 0..1439 (versão futura, disco corrompido) cai no padrão. */
private fun Preferences.hora(key: Preferences.Key<Int>, padrao: LocalTime): LocalTime =
    this[key]?.takeIf { it in 0..1439 }?.let { LocalTime.ofSecondOfDay(it * 60L) } ?: padrao
