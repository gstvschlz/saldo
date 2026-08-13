package com.scholze.saldo.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.scholze.saldo.domain.CartaoConfig
import java.io.IOException
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

enum class Tema { SISTEMA, CLARO, ESCURO }

data class Settings(
    val saldoInicialCentavos: Long?,
    val saldoInicialData: LocalDate?,
    val cartao: CartaoConfig,
    val comecarOculto: Boolean,
    val tema: Tema,
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
        )
    }

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

    /**
     * Volta tudo ao default de instalação nova.
     *
     * Passa pela MESMA instância de [DataStore] de propósito: apagar o arquivo por fora
     * não invalida o cache em memória do singleton, então só isto realmente reseta o
     * estado dentro de um processo já em execução (o caso dos testes instrumentados).
     */
    suspend fun limpar() {
        dataStore.edit { it.clear() }
    }
}
