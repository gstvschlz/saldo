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
import com.scholze.saldo.domain.ACaminho
import com.scholze.saldo.domain.InsightsEngine
import com.scholze.saldo.domain.ParaOndeFoi
import com.scholze.saldo.domain.PontoMes
import com.scholze.saldo.domain.ProjectionEngine
import com.scholze.saldo.domain.Ritmo
import com.scholze.saldo.domain.RitmoEngine
import com.scholze.saldo.domain.TagsNoTempo
import com.scholze.saldo.domain.ResumoRecorrencias
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
    /** "para onde foi" do mês visto; `null` junto com [totais]. */
    val insights: ParaOndeFoi? = null,
    /** 6 meses até o mês visto; `null` junto com [totais]. */
    val tendencia: List<PontoMes>? = null,
    /** O que ainda passa pelo saldo depois de hoje até o fim do mês visto. */
    val aCaminho: ACaminho? = null,
    /** Só o resumo, para a linha de atalho; a tela própria tem seu ViewModel. */
    val recorrencias: ResumoRecorrencias? = null,
    /** O acumulado de saídas do mês contra o costume dos anteriores; `null` junto com [totais]. */
    val ritmo: Ritmo? = null,
    /** As mesmas fatias de "para onde foi", nos 6 meses até o mês visto. */
    val tagsNoTempo: TagsNoTempo? = null,
)

class TotaisViewModel(private val repo: SaldoRepository) : ViewModel() {

    private val mesAtual = MutableStateFlow(YearMonth.now())

    val state: StateFlow<TotaisUiState> = combine(repo.ledger, mesAtual) { input, mes ->
        TotaisUiState(
            mesAtual = mes,
            totais = ProjectionEngine.totais(input, mes),
            estimativaCentavos = ProjectionEngine.mes(input, mes, FiltroLedger.TODAS).estimativaCentavos,
            insights = InsightsEngine.paraOndeFoi(input, mes),
            tendencia = InsightsEngine.tendencia(input, mes),
            aCaminho = InsightsEngine.aCaminho(input, mes),
            recorrencias = InsightsEngine.recorrencias(input, mes),
            ritmo = RitmoEngine.ritmo(input, mes),
            tagsNoTempo = InsightsEngine.tagsAoLongoDoTempo(input, mes),
        )
    }
        // `totais` projeta o mês inteiro (e `mes` de novo, para a estimativa); `insights` soma mais
        // duas passadas de `movimentacoesDoMes` (mês atual e anterior) e os padrões; `tendencia` roda
        // mais seis passagens INTEIRAS de ProjectionEngine.totais (uma por mês, cada uma com sua
        // própria expansão e cálculo de fatura) — a cada emissão do ledger, esteja a aba "tendência"
        // aberta ou não. Tudo isso fica fora da main thread.
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

    fun irPara(mes: YearMonth) {
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
