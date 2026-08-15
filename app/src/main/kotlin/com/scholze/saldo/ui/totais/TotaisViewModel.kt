package com.scholze.saldo.ui.totais

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.scholze.saldo.AppContainer
import com.scholze.saldo.data.SaldoRepository
import com.scholze.saldo.domain.FiltroLedger
import com.scholze.saldo.domain.ProjectionEngine
import com.scholze.saldo.domain.TotaisMes
import java.time.YearMonth
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class TotaisUiState(
    val mesAtual: YearMonth,
    /** `null` enquanto o primeiro `LedgerInput` não chegou do banco. */
    val totais: TotaisMes?,
    val estimativaCentavos: Long = 0,
)

class TotaisViewModel(private val repo: SaldoRepository) : ViewModel() {

    private val mesAtual = MutableStateFlow(YearMonth.now())

    val state: StateFlow<TotaisUiState> = combine(repo.ledger, mesAtual) { input, mes ->
        TotaisUiState(
            mesAtual = mes,
            totais = ProjectionEngine.totais(input, mes),
            estimativaCentavos = ProjectionEngine.mes(input, mes, FiltroLedger.TODAS).estimativaCentavos,
        )
    }
        // `totais` projeta o mês inteiro (e `mes` de novo, para a estimativa): fora da main thread.
        .flowOn(Dispatchers.Default)
        // Mesma razão do LedgerViewModel: uma exceção subindo do banco cancelaria o
        // StateFlow e a aba ficaria congelada para sempre, sem crash que explicasse.
        .catch { Log.e(TAG, "fluxo de totais falhou", it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TotaisUiState(mesAtual.value, null))

    init {
        abrir(mesAtual.value)
    }

    fun mesAnterior() = irPara(mesAtual.value.minusMonths(1))
    fun proximoMes() = irPara(mesAtual.value.plusMonths(1))

    private fun irPara(mes: YearMonth) {
        mesAtual.value = mes
        abrir(mes)
    }

    private fun abrir(mes: YearMonth) {
        viewModelScope.launch {
            try {
                repo.abrirMes(mes)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "abrirMes($mes) falhou", e)
            }
        }
    }

    companion object {
        private const val TAG = "saldo"

        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { TotaisViewModel(container.repository) }
        }
    }
}
