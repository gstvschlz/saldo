package com.scholze.saldo.ui.mais

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.scholze.saldo.AppContainer
import com.scholze.saldo.data.Settings
import com.scholze.saldo.data.SettingsStore
import com.scholze.saldo.data.Tema
import com.scholze.saldo.domain.CartaoConfig
import java.time.LocalDate
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MaisViewModel(private val settingsStore: SettingsStore) : ViewModel() {

    val settings: StateFlow<Settings?> =
        settingsStore.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * Reancora o saldo inicial em hoje — é a semântica pretendida ("meu saldo HOJE é X"),
     * e não uma correção retroativa: o engine ignora tudo antes de `saldoInicialData`,
     * então movimentações anteriores deixam de contar a partir daqui.
     */
    fun definirSaldoInicial(centavos: Long) =
        escrever("definirSaldoInicial") { settingsStore.definirSaldoInicial(centavos, LocalDate.now()) }

    fun definirCartao(config: CartaoConfig) = escrever("definirCartao") { settingsStore.definirCartao(config) }
    fun definirComecarOculto(v: Boolean) = escrever("definirComecarOculto") { settingsStore.definirComecarOculto(v) }
    fun definirTema(t: Tema) = escrever("definirTema") { settingsStore.definirTema(t) }

    private fun escrever(qual: String, bloco: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                bloco()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "$qual falhou", e)
            }
        }
    }

    companion object {
        private const val TAG = "saldo"

        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { MaisViewModel(container.settings) }
        }
    }
}
