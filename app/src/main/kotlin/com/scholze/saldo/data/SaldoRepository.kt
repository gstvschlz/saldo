package com.scholze.saldo.data

import androidx.room.withTransaction
import com.scholze.saldo.data.db.MesMaterializadoEntity
import com.scholze.saldo.data.db.MovimentacaoTagCross
import com.scholze.saldo.data.db.RecorrenciaTagCross
import com.scholze.saldo.data.db.SaldoDatabase
import com.scholze.saldo.data.db.toAnoMes
import com.scholze.saldo.data.db.toDomain
import com.scholze.saldo.data.db.toEntity
import com.scholze.saldo.data.db.toYearMonth
import com.scholze.saldo.domain.EscopoEdicao
import com.scholze.saldo.domain.EscopoExclusao
import com.scholze.saldo.domain.LedgerInput
import com.scholze.saldo.domain.Movimentacao
import com.scholze.saldo.domain.Recorrencia
import com.scholze.saldo.domain.RecurrenceExpander
import com.scholze.saldo.domain.RepetirOpcao
import com.scholze.saldo.domain.Tag
import com.scholze.saldo.domain.TagSnapshot
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

interface SaldoRepository {
    val ledger: Flow<LedgerInput>
    val tags: Flow<List<Tag>>
    suspend fun abrirMes(mes: YearMonth)
    suspend fun criar(mov: Movimentacao, repetir: RepetirOpcao)

    /**
     * Grava uma edição.
     *
     * **Precondição ([EscopoEdicao.SO_ESTE_MES]): `mov.id != 0`.** Uma ocorrência virtual
     * — a expansão que o `ProjectionEngine` faz de uma recorrência num mês ainda não
     * materializado — não tem linha no banco, e o `UPDATE ... WHERE id = 0` não acertaria
     * nada. Chamar assim lança `IllegalArgumentException`.
     *
     * [EscopoEdicao.DAQUI_EM_DIANTE] é a exceção deliberada: ele reescreve o *template* da
     * recorrência, não a linha, então aceita `id == 0` desde que `recorrenciaId != null`.
     *
     * A UI deve chamar [abrirMes] (e esperar) antes de oferecer ações de linha, e nunca
     * abrir o editor para uma linha com `id == 0`. Cuidado também com a data: mover uma
     * instância para um mês nunca aberto materializa aquele mês inteiro (comportamento
     * documentado, ver `moverInstanciaParaMesNaoAbertoMaterializaDestino`).
     */
    suspend fun editar(mov: Movimentacao, escopo: EscopoEdicao)

    /**
     * Apaga a linha e devolve o snapshot para o "desfazer".
     *
     * **Precondição: `mov.id != 0`.** Numa ocorrência virtual o delete seria um no-op e o
     * "desfazer" seguinte inseriria uma duplicata permanente; por isso lança
     * `IllegalArgumentException` em vez de falhar em silêncio. A UI deve chamar [abrirMes]
     * antes de oferecer ações de linha.
     */
    suspend fun excluir(mov: Movimentacao): Movimentacao
    suspend fun restaurar(mov: Movimentacao)
    suspend fun excluirRecorrencia(recorrenciaId: Long, aPartirDe: YearMonth, escopo: EscopoExclusao)

    /**
     * Uma avulsa vira mensal: cria o template a partir dela (começa no mês dela, no dia
     * [diaDoMes]), liga a linha e semeia todo mês já materializado depois. Os campos visíveis
     * da linha (descrição, valor, data, natureza, tags) são gravados primeiro, na mesma
     * transação — exatamente como [editar] faz para [EscopoEdicao.SO_ESTE_MES] — então o que o
     * usuário mudou na sheet chega ao banco mesmo quando ele também mexeu em "repetir" e a UI
     * não chama [editar] à parte. A linha fica marcada como editada se a data dela não cai em
     * [diaDoMes] — é o mesmo sinal que uma instância movida de dia carrega.
     */
    suspend fun converterEmRecorrencia(mov: Movimentacao, diaDoMes: Int)

    /**
     * "repetir: não" numa instância: grava primeiro os campos visíveis de [mov] na linha (mesma
     * transação, como [converterEmRecorrencia] faz), desliga-a do template e apaga as
     * instâncias não editadas do mês seguinte a [mesDaSerie] em diante. A série acaba no mês
     * ANTERIOR a [mesDaSerie] — o mês a que a instância pertencia quando a sheet foi aberta, não
     * `mov.data`, que pode já ter sido movida para outro mês no mesmo formulário antes de salvar.
     * Se a série acabaria antes de começar, o template é apagado.
     */
    suspend fun encerrarRecorrencia(mov: Movimentacao, mesDaSerie: YearMonth)

    /** Desliga o template e apaga as instâncias não editadas do mês seguinte a [dia] em diante. */
    suspend fun pausar(recorrenciaId: Long, dia: LocalDate)

    /**
     * Liga o template de volta. Os meses entre a pausa e [dia] que nunca foram abertos são
     * materializados ANTES de religar — ficam vazios para este template, e é isso que
     * "pausada" quer dizer. Todo mês já materializado a partir de [dia] (inclusive) que ficou
     * sem instância deste template — a pausa apagou a dele, ou ele nunca teve uma — ganha a
     * sua de volta.
     */
    suspend fun retomar(recorrenciaId: Long, dia: LocalDate)

    suspend fun criarTag(nome: String, cor: Long): Long
    suspend fun renomearTag(id: Long, nome: String)

    /** Apaga a tag (os vínculos caem por CASCADE) e devolve o que [restaurarTag] precisa. */
    suspend fun excluirTag(id: Long): TagSnapshot
    suspend fun restaurarTag(snapshot: TagSnapshot)
    suspend fun recolorirTag(id: Long, cor: Long)
}

class RoomSaldoRepository(
    private val db: SaldoDatabase,
    private val settingsStore: SettingsStore,
    /** Ver [diaAtual]: um fluxo, para que a virada do dia chegue ao ledger sem depender de uma escrita. */
    private val hoje: Flow<LocalDate> = diaAtual(),
) : SaldoRepository {

    private val movDao = db.movimentacaoDao()
    private val recDao = db.recorrenciaDao()
    private val tagDao = db.tagDao()
    private val mesDao = db.mesMaterializadoDao()

    override val ledger: Flow<LedgerInput> = combine(
        movDao.observeAll(),
        recDao.observeAll(),
        mesDao.observeTodos(),
        settingsStore.settings,
        hoje,
    ) { movs, recs, meses, settings, hoje ->
        LedgerInput(
            saldoInicialCentavos = settings.saldoInicialCentavos ?: 0L,
            saldoInicialData = settings.saldoInicialData ?: hoje,
            movimentacoes = movs.map { it.toDomain() },
            recorrencias = recs.map { it.toDomain() },
            mesesMaterializados = meses.map { it.toYearMonth() }.toSet(),
            cartao = settings.cartao,
            hoje = hoje,
        )
    }

    override val tags: Flow<List<Tag>> = tagDao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun abrirMes(mes: YearMonth) = db.withTransaction {
        val marcados = mesDao.todos().map { it.toYearMonth() }.toSet()
        if (mes in marcados) return@withTransaction
        materializar(mes)
    }

    override suspend fun criar(mov: Movimentacao, repetir: RepetirOpcao) = db.withTransaction {
        when (repetir) {
            is RepetirOpcao.Nao -> insertComTags(mov)
            is RepetirOpcao.TodoMes -> {
                val inicio = YearMonth.from(mov.data)
                val marcados = mesDao.todos().map { it.toYearMonth() }.toSet()
                val recId = recDao.insert(
                    Recorrencia(
                        descricao = mov.descricao, valorCentavos = mov.valorCentavos,
                        natureza = mov.natureza, diaDoMes = repetir.dia, inicio = inicio,
                    ).toEntity(),
                )
                recDao.setTags(recId, mov.tags.map { it.id })
                val template = Recorrencia(
                    id = recId, descricao = mov.descricao, valorCentavos = mov.valorCentavos,
                    natureza = mov.natureza, diaDoMes = repetir.dia, inicio = inicio, tags = mov.tags,
                )
                // Instância deste mês usa a data digitada; meses já materializados > início são semeados.
                insertComTags(mov.copy(recorrenciaId = recId))
                marcados.filter { it > inicio }.forEach { m ->
                    RecurrenceExpander.ocorrenciaNoMes(template, m)?.let { insertComTags(it) }
                }
                // O mês da própria movimentação precisa ficar materializado — a instância acima já é
                // uma linha real, e sem a marca o template seria AINDA expandido virtualmente ali.
                // Marcar um mês nunca aberto, porém, desliga a expansão virtual das OUTRAS
                // recorrências nele; então elas são semeadas antes, como faria `abrirMes`.
                if (inicio !in marcados) materializar(inicio, excetoRecorrenciaId = recId)
            }
        }
    }

    override suspend fun editar(mov: Movimentacao, escopo: EscopoEdicao) = db.withTransaction {
        when (escopo) {
            EscopoEdicao.SO_ESTE_MES -> {
                require(mov.id != 0L) { "movimentação virtual — abra o mês antes de editar" }
                // UPDATE campo a campo: `updateCampos` toca só os campos visíveis ao usuário,
                // deixando `recorrenciaId` e `criadaEm` intocados — nenhum dos dois é editável
                // numa edição de instância.
                movDao.updateCampos(
                    id = mov.id,
                    descricao = mov.descricao,
                    valorCentavos = mov.valorCentavos,
                    dataEpochDay = mov.data.toEpochDay(),
                    natureza = mov.natureza.name,
                    editadaManualmente = mov.editadaManualmente || mov.recorrenciaId != null,
                )
                movDao.setTags(mov.id, mov.tags.map { it.id })
                // Mover a instância para um mês nunca aberto deixaria o template dela ainda
                // expandindo virtualmente lá — a recorrência apareceria duas vezes no destino.
                // Materializa o destino sem o próprio template, cuja instância é a linha movida.
                val recId = mov.recorrenciaId
                if (recId != null) {
                    val mesDestino = YearMonth.from(mov.data)
                    val marcados = mesDao.todos().map { it.toYearMonth() }.toSet()
                    if (mesDestino !in marcados) materializar(mesDestino, excetoRecorrenciaId = recId)
                }
            }
            EscopoEdicao.DAQUI_EM_DIANTE -> {
                val recId = requireNotNull(mov.recorrenciaId) { "escopo DAQUI_EM_DIANTE exige recorrência" }
                val mesInicio = YearMonth.from(mov.data)
                val templateAntigo = requireNotNull(
                    recDao.todos().firstOrNull { it.rec.id == recId },
                ) { "recorrência $recId não existe" }.toDomain()
                // Congela o passado ANTES de mexer no template: meses entre o início da recorrência
                // e o mês da edição que nunca foram abertos ainda são expandidos virtualmente, e
                // passariam a render os valores NOVOS. Materializá-los com os valores antigos
                // (mês completo, como `abrirMes`) mantém "daqui em diante" olhando só para frente.
                congelarAte(templateAntigo.inicio, mesInicio.minusMonths(1))
                val templateNovo = templateAntigo.copy(
                    descricao = mov.descricao, valorCentavos = mov.valorCentavos,
                    natureza = mov.natureza, diaDoMes = mov.data.dayOfMonth, tags = mov.tags,
                )
                recDao.update(templateNovo.toEntity())
                recDao.setTags(recId, mov.tags.map { it.id })
                movDao.deleteInstanciasNaoEditadasAPartirDe(recId, mesInicio.atDay(1).toEpochDay())
                // Re-semeia meses materializados >= mesInicio que ficaram sem instância.
                mesDao.todos().map { it.toYearMonth() }
                    .filter { it >= mesInicio }
                    .forEach { m ->
                        val existentes = movDao.countInstancias(
                            recId, m.atDay(1).toEpochDay(), m.atEndOfMonth().toEpochDay(),
                        )
                        // Em `mesInicio` a expansão já cai exatamente em `mov.data`:
                        // `templateNovo.diaDoMes` é `mov.data.dayOfMonth`, sempre válido no mês.
                        if (existentes == 0) {
                            RecurrenceExpander.ocorrenciaNoMes(templateNovo, m)?.let { insertComTags(it) }
                        }
                    }
            }
        }
    }

    override suspend fun excluir(mov: Movimentacao): Movimentacao {
        // Ocorrência virtual (id 0) não tem linha para apagar: o delete seria um no-op e o
        // "desfazer" depois inseriria uma duplicata permanente.
        require(mov.id != 0L) { "movimentação virtual — abra o mês antes de excluir" }
        movDao.deleteById(mov.id)
        return mov
    }

    override suspend fun restaurar(mov: Movimentacao) = db.withTransaction {
        insertComTags(mov.copy(id = 0))
    }

    override suspend fun excluirRecorrencia(recorrenciaId: Long, aPartirDe: YearMonth, escopo: EscopoExclusao) =
        db.withTransaction {
            when (escopo) {
                EscopoExclusao.SO_FUTURAS -> {
                    movDao.deleteInstanciasNaoEditadasAPartirDe(recorrenciaId, aPartirDe.atDay(1).toEpochDay())
                    val template = requireNotNull(
                        recDao.todos().firstOrNull { it.rec.id == recorrenciaId },
                    ) { "recorrência $recorrenciaId não existe" }.toDomain()
                    recDao.update(template.copy(fim = aPartirDe.minusMonths(1)).toEntity())
                }
                EscopoExclusao.TODAS -> {
                    movDao.deleteTodasInstancias(recorrenciaId)
                    recDao.deleteById(recorrenciaId)
                }
            }
        }

    override suspend fun converterEmRecorrencia(mov: Movimentacao, diaDoMes: Int) = db.withTransaction {
        require(mov.id != 0L) { "movimentação virtual — abra o mês antes de converter" }
        require(mov.recorrenciaId == null) { "a movimentação já é de uma recorrência" }
        val editada = mov.data.dayOfMonth != diaDoMes
        // Mesmo UPDATE campo a campo que `editar(SO_ESTE_MES)` faz: a UI não chama `editar` à
        // parte, então é aqui que a descrição/valor/data/natureza/tags digitados na sheet
        // chegam ao banco.
        movDao.updateCampos(
            id = mov.id,
            descricao = mov.descricao,
            valorCentavos = mov.valorCentavos,
            dataEpochDay = mov.data.toEpochDay(),
            natureza = mov.natureza.name,
            editadaManualmente = editada,
        )
        movDao.setTags(mov.id, mov.tags.map { it.id })
        val inicio = YearMonth.from(mov.data)
        val recId = recDao.insert(
            Recorrencia(
                descricao = mov.descricao, valorCentavos = mov.valorCentavos,
                natureza = mov.natureza, diaDoMes = diaDoMes, inicio = inicio,
            ).toEntity(),
        )
        recDao.setTags(recId, mov.tags.map { it.id })
        movDao.ligarARecorrencia(mov.id, recId, editada = editada)
        val template = Recorrencia(
            id = recId, descricao = mov.descricao, valorCentavos = mov.valorCentavos,
            natureza = mov.natureza, diaDoMes = diaDoMes, inicio = inicio, tags = mov.tags,
        )
        val marcados = mesDao.todos().map { it.toYearMonth() }.toSet()
        marcados.filter { it > inicio }.forEach { m ->
            RecurrenceExpander.ocorrenciaNoMes(template, m)?.let { insertComTags(it) }
        }
        // Mesma razão de `criar`: o mês da linha precisa ficar materializado, e marcar um mês
        // nunca aberto desliga a expansão virtual das outras recorrências nele.
        if (inicio !in marcados) materializar(inicio, excetoRecorrenciaId = recId)
    }

    override suspend fun encerrarRecorrencia(mov: Movimentacao, mesDaSerie: YearMonth) = db.withTransaction {
        require(mov.id != 0L) { "movimentação virtual — abra o mês antes de encerrar" }
        val recId = requireNotNull(mov.recorrenciaId) { "a movimentação não é de uma recorrência" }
        // Mesmo UPDATE campo a campo que `editar(SO_ESTE_MES)` e `converterEmRecorrencia` fazem
        // — `desligarDaRecorrencia`, abaixo, zera `editadaManualmente` de novo; o valor passado
        // aqui não sobrevive, só os outros campos.
        movDao.updateCampos(
            id = mov.id,
            descricao = mov.descricao,
            valorCentavos = mov.valorCentavos,
            dataEpochDay = mov.data.toEpochDay(),
            natureza = mov.natureza.name,
            // `mov.recorrenciaId` já foi conferido não-nulo acima (`recId`): esta linha É uma
            // instância, então sempre "editada" — o valor não sobrevive de qualquer forma,
            // `desligarDaRecorrencia` zera de novo a seguir.
            editadaManualmente = true,
        )
        movDao.setTags(mov.id, mov.tags.map { it.id })
        val template = requireNotNull(
            recDao.todos().firstOrNull { it.rec.id == recId },
        ) { "recorrência $recId não existe" }.toDomain()
        movDao.desligarDaRecorrencia(mov.id)
        // `mesDaSerie` é o mês a que a instância pertencia quando a sheet abriu — não
        // `mov.data`, que pode ter sido movida para outro mês no mesmo formulário. Usar
        // `mov.data` aqui cortaria (ou apagaria) o mês errado da série.
        movDao.deleteInstanciasNaoEditadasAPartirDe(recId, mesDaSerie.plusMonths(1).atDay(1).toEpochDay())
        val fim = mesDaSerie.minusMonths(1)
        if (fim < template.inicio) {
            // A série acabaria antes de começar: o template inteiro some. Uma instância
            // editada à mão (fora do alcance do delete acima) manteria `recorrenciaId`
            // apontando para um id que não existe mais — desliga todas antes de apagar.
            movDao.desligarTodasDaRecorrencia(recId)
            recDao.deleteById(recId)
        } else {
            recDao.update(template.copy(fim = fim).toEntity())
        }
    }

    override suspend fun pausar(recorrenciaId: Long, dia: LocalDate) = db.withTransaction {
        val template = requireNotNull(
            recDao.todos().firstOrNull { it.rec.id == recorrenciaId },
        ) { "recorrência $recorrenciaId não existe" }.toDomain()
        val mesHoje = YearMonth.from(dia)
        // Congela o passado ANTES de desligar, como `editar(DAQUI_EM_DIANTE)` faz: um mês entre
        // o início e hoje que nunca foi aberto ainda é expandido virtualmente, e com o template
        // inativo a expansão sumiria — a academia de fevereiro deixaria de ter existido.
        // Materializado agora, com o template ativo, ele guarda a ocorrência.
        congelarAte(template.inicio, mesHoje)
        movDao.deleteInstanciasNaoEditadasAPartirDe(recorrenciaId, mesHoje.plusMonths(1).atDay(1).toEpochDay())
        recDao.definirAtiva(recorrenciaId, false)
    }

    override suspend fun retomar(recorrenciaId: Long, dia: LocalDate) = db.withTransaction {
        val template = requireNotNull(
            recDao.todos().firstOrNull { it.rec.id == recorrenciaId },
        ) { "recorrência $recorrenciaId não existe" }.toDomain()
        val mesHoje = YearMonth.from(dia)
        // O passado até a pausa já está congelado (ver `pausar`); o que sobra sem abrir é o
        // intervalo da pausa. Materializá-lo com o template AINDA inativo deixa esses meses
        // vazios para ele — as outras recorrências ganham as suas linhas — e é isso que
        // "pausada" quer dizer.
        congelarAte(template.inicio, mesHoje.minusMonths(1))
        recDao.definirAtiva(recorrenciaId, true)
        val ativo = template.copy(ativa = true)
        // Não é só `mesHoje` que pode ter ficado sem instância: um mês DEPOIS dele também pode
        // já estar materializado (totais/ledger navegam pra frente e chamam `abrirMes`), e a
        // pausa apagou a instância não editada de lá. Sem re-semear todos, esses meses ficariam
        // permanentemente vazios para este template — religar não bastaria para trazê-lo de volta.
        mesDao.todos().map { it.toYearMonth() }
            .filter { it >= mesHoje }
            .forEach { m ->
                val existentes = movDao.countInstancias(
                    recorrenciaId, m.atDay(1).toEpochDay(), m.atEndOfMonth().toEpochDay(),
                )
                if (existentes == 0) RecurrenceExpander.ocorrenciaNoMes(ativo, m)?.let { insertComTags(it) }
            }
    }

    /** Materializa todo mês de [de] a [ate] (inclusive) que ainda não foi aberto, com os templates como estão. */
    private suspend fun congelarAte(de: YearMonth, ate: YearMonth) {
        val marcados = mesDao.todos().map { it.toYearMonth() }.toSet()
        var m = de
        while (m <= ate) {
            if (m !in marcados) materializar(m)
            m = m.plusMonths(1)
        }
    }

    override suspend fun criarTag(nome: String, cor: Long): Long =
        tagDao.insert(Tag(nome = nome, cor = cor).toEntity())

    override suspend fun renomearTag(id: Long, nome: String) = tagDao.rename(id, nome)

    override suspend fun excluirTag(id: Long): TagSnapshot = db.withTransaction {
        val tag = requireNotNull(tagDao.porId(id)) { "tag $id não existe" }.toDomain()
        val snapshot = TagSnapshot(
            tag = tag,
            movimentacaoIds = tagDao.movimentacoesDaTag(id),
            recorrenciaIds = tagDao.recorrenciasDaTag(id),
        )
        tagDao.deleteById(id)
        snapshot
    }

    override suspend fun restaurarTag(snapshot: TagSnapshot) = db.withTransaction {
        tagDao.insertComId(snapshot.tag.toEntity())
        snapshot.movimentacaoIds.forEach { tagDao.insertMovCross(MovimentacaoTagCross(it, snapshot.tag.id)) }
        snapshot.recorrenciaIds.forEach { tagDao.insertRecCross(RecorrenciaTagCross(it, snapshot.tag.id)) }
    }

    override suspend fun recolorirTag(id: Long, cor: Long) = tagDao.recolor(id, cor)

    /**
     * Núcleo de [abrirMes]: expande todo template ativo em [mes] e marca o mês como materializado.
     * [excetoRecorrenciaId] pula um template cuja instância do mês já foi inserida à mão.
     * O chamador é responsável por só chamar quando [mes] ainda não estiver materializado.
     */
    private suspend fun materializar(mes: YearMonth, excetoRecorrenciaId: Long? = null) {
        val templates = recDao.todos()
            .map { it.toDomain() }
            .filter { it.id != excetoRecorrenciaId }
        RecurrenceExpander.ocorrenciasNoMes(templates, mes).forEach { insertComTags(it) }
        mesDao.marcar(MesMaterializadoEntity(mes.toAnoMes()))
    }

    private suspend fun insertComTags(mov: Movimentacao) {
        val id = movDao.insert(mov.toEntity())
        if (mov.tags.isNotEmpty()) movDao.setTags(id, mov.tags.map { it.id })
    }
}
