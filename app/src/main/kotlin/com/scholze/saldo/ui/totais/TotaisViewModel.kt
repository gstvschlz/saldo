package com.scholze.saldo.ui.totais

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
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
import com.scholze.saldo.ui.components.MENSAGEM_ERRO_LEITURA
import com.scholze.saldo.ui.fluxoComErro
import com.scholze.saldo.ui.toLongChave
import com.scholze.saldo.ui.toYearMonth
import java.time.YearMonth
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Os segmentos da aba totais. */
enum class SegmentoTotais(val rotulo: String) {
    MES("mês"),
    TENDENCIA("tendência"),
    A_CAMINHO("a caminho"),
}

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
    /** Mensagem de falha de leitura; `null` = está tudo bem. Ver `fluxoComErro`. */
    val erro: String? = null,
)

class TotaisViewModel(
    private val repo: SaldoRepository,
    private val savedState: SavedStateHandle = SavedStateHandle(),
) : ViewModel() {

    // Sobrevive à morte do processo, e não só à rotação — mesmo `Long` de chave que o board usa.
    private val mesAtual = MutableStateFlow(savedState.get<Long>(KEY_MES)?.toYearMonth() ?: YearMonth.now())

    /** Leitura síncrona: os testes conferem o saved state sem coletar o Flow. */
    val mesAtualAgora: YearMonth get() = mesAtual.value

    // Sobrevive à morte do processo, como o mês: a pill "guardou N%" do hero precisa que o
    // segmento já esteja em tendência quando a aba totais reabrir.
    private val _segmento = MutableStateFlow(
        savedState.get<String>(KEY_SEGMENTO)?.let { s -> SegmentoTotais.entries.firstOrNull { it.name == s } } ?: SegmentoTotais.MES,
    )

    /** O segmento visto; estado de navegação, como o mês — sobrevive à morte do processo. */
    val segmento: StateFlow<SegmentoTotais> = _segmento

    fun selecionarSegmento(s: SegmentoTotais) {
        _segmento.value = s
        savedState[KEY_SEGMENTO] = s.name
    }

    private val tentativas = MutableStateFlow(0)

    /** O botão "tentar de novo" da tela: reassina o fluxo do banco. */
    fun tentarDeNovo() {
        tentativas.value++
    }

    private val inicial = TotaisUiState(mesAtual.value, null)

    val state: StateFlow<TotaisUiState> = fluxoComErro(
        tentativas = tentativas,
        inicial = inicial,
        marcarErro = { it.copy(erro = MENSAGEM_ERRO_LEITURA) },
        limparErro = { it.copy(erro = null) },
        rotulo = "fluxo de totais",
    ) {
        combine(repo.ledger, mesAtual) { input, mes ->
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
            // `totais` projeta o mês inteiro (e `mes` de novo, para a estimativa); `insights` soma
            // mais duas passadas de `movimentacoesDoMes` (mês atual e anterior) e os padrões;
            // `tendencia` roda mais seis passagens INTEIRAS de ProjectionEngine.totais (uma por mês,
            // cada uma com sua própria expansão e cálculo de fatura) — a cada emissão do ledger,
            // esteja a aba "tendência" aberta ou não. Tudo isso fica fora da main thread.
            .flowOn(Dispatchers.Default)
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), inicial)

    init {
        abrir(mesAtual.value)
    }

    fun mesAnterior() = irPara(mesAtual.value.minusMonths(1))
    fun proximoMes() = irPara(mesAtual.value.plusMonths(1))

    fun irPara(mes: YearMonth) {
        mesAtual.value = mes
        savedState[KEY_MES] = mes.toLongChave()
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
        private const val KEY_MES = "totais.mes"
        private const val KEY_SEGMENTO = "totais.segmento"

        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { TotaisViewModel(container.repository, createSavedStateHandle()) }
        }
    }
}
