package com.scholze.saldo.ui.board

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
import com.scholze.saldo.domain.Board
import com.scholze.saldo.domain.BoardEngine
import com.scholze.saldo.domain.DiaRow
import com.scholze.saldo.domain.FiltroLedger
import com.scholze.saldo.domain.MesLedger
import com.scholze.saldo.domain.ProjectionEngine
import com.scholze.saldo.domain.Teto
import com.scholze.saldo.domain.TetoEngine
import com.scholze.saldo.ui.components.MENSAGEM_ERRO_LEITURA
import com.scholze.saldo.ui.fluxoComErro
import com.scholze.saldo.ui.toLongChave
import com.scholze.saldo.ui.toYearMonth
import java.time.LocalDate
import java.time.YearMonth
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * [board] e [mes] são `null` só enquanto o primeiro `LedgerInput` não chegou do banco.
 *
 * [mes] é o mês inteiro, e existe por dois motivos: o hero mostra o MESMO saldo projetado
 * que o ledger mostrava — o número não pode mudar por causa da vista —, e é dele que sai o
 * painel do dia, sem uma segunda projeção.
 *
 * [diaAberto] é o dia cujos lançamentos aparecem embaixo da grade. `null` = nenhum, e aí o
 * rodapé mostra a régua do dia típico.
 */
data class BoardUiState(
    val board: Board?,
    val mes: MesLedger?,
    val hoje: LocalDate,
    val mesAtual: YearMonth = YearMonth.now(),
    val diaAberto: LocalDate? = null,
    /** Mensagem de falha de leitura; `null` = está tudo bem. Ver `fluxoComErro`. */
    val erro: String? = null,
    /**
     * O teto do dia; `null` fora do mês corrente e num mês sem entrada nenhuma. O hero some com
     * a linha nos dois casos — "hoje" não existe em setembro visto de outubro, e sem renda não há
     * o que dividir (ver [com.scholze.saldo.domain.TetoEngine.teto]).
     */
    val teto: Teto? = null,
) {
    /** A linha do dia aberto, com os lançamentos dele; `null` quando não há dia aberto. */
    val linhaDoDia: DiaRow?
        get() {
            val d = diaAberto ?: return null
            if (mes == null || YearMonth.from(d) != mes.mes) return null
            return mes.dias.getOrNull(d.dayOfMonth - 1)
        }

    /** A seta `›` para no mês corrente: um mês futuro seria uma grade de "ainda não aconteceu". */
    val podeAvancar: Boolean get() = mesAtual < YearMonth.from(hoje)
}

class BoardViewModel(
    private val repo: SaldoRepository,
    private val savedState: SavedStateHandle = SavedStateHandle(),
    /**
     * A meta de guardar, que é a reserva mínima do teto do dia. Um `Flow<Int>` e não o
     * `SettingsStore` inteiro, como em `TotaisViewModel`: assinar o `Settings` completo faria
     * toda troca de tema recalcular o board inteiro.
     */
    private val metaGuardar: Flow<Int> = flowOf(0),
) : ViewModel() {

    // Mês e dia aberto no SavedStateHandle: sobrevivem à morte do processo, e não só à
    // rotação. `YearMonth`/`LocalDate` como Long (o handle só aceita o que vai num Bundle).
    private val mesAtual = MutableStateFlow(savedState.get<Long>(KEY_MES)?.toYearMonth() ?: YearMonth.now())

    // O app abre respondendo "o que eu gastei hoje": no mês corrente, hoje já vem aberto.
    // `contains` distingue "nunca gravado" (primeira abertura) de "gravado como nenhum".
    private val diaAberto = MutableStateFlow<LocalDate?>(
        if (savedState.contains(KEY_DIA)) savedState.get<Long>(KEY_DIA)?.let { LocalDate.ofEpochDay(it) }
        else LocalDate.now(),
    )

    /** Leitura síncrona: o `+` da barra lança no dia aberto, e os testes conferem o saved state. */
    val mesAtualAgora: YearMonth get() = mesAtual.value
    val diaAbertoAgora: LocalDate? get() = diaAberto.value

    private val tentativas = MutableStateFlow(0)

    /** O botão "tentar de novo" da tela: reassina o fluxo do banco. */
    fun tentarDeNovo() {
        tentativas.value++
    }

    private val inicial = BoardUiState(
        board = null, mes = null, hoje = LocalDate.now(), diaAberto = LocalDate.now(),
    )

    val state: StateFlow<BoardUiState> = fluxoComErro(
        tentativas = tentativas,
        inicial = inicial,
        marcarErro = { it.copy(erro = MENSAGEM_ERRO_LEITURA) },
        limparErro = { it.copy(erro = null) },
        rotulo = "fluxo do board",
    ) {
        combine(repo.ledger, mesAtual, diaAberto, metaGuardar) { input, mes, dia, meta ->
            BoardUiState(
                board = BoardEngine.board(input, mes),
                mes = ProjectionEngine.mes(input, mes, FiltroLedger.TODAS),
                hoje = input.hoje,
                mesAtual = mes,
                diaAberto = dia,
                teto = if (mes == YearMonth.from(input.hoje)) TetoEngine.teto(input, meta) else null,
            )
        }
            // O agrupamento por dia mais a projeção do mês: fora da main thread.
            .flowOn(Dispatchers.Default)
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), inicial)

    init {
        abrir(mesAtual.value)
    }

    fun mesAnterior() = irPara(mesAtual.value.minusMonths(1))

    /** Sem efeito no mês corrente: não há para onde avançar. */
    fun proximoMes() {
        val alvo = mesAtual.value.plusMonths(1)
        if (alvo <= YearMonth.now()) irPara(alvo)
    }

    /**
     * Navega para [mes]. Com [dia] — o caminho do deep link e do "ir para o dia" de totais —
     * aquele dia já chega aberto no painel; sem ele, abre hoje se o mês for o corrente.
     */
    fun irPara(mes: YearMonth, dia: Int? = null) {
        mesAtual.value = mes
        savedState[KEY_MES] = mes.toLongChave()
        diaAberto.value = when {
            dia != null -> mes.atDay(dia.coerceIn(1, mes.lengthOfMonth()))
            mes == YearMonth.now() -> LocalDate.now()
            else -> null
        }
        savedState[KEY_DIA] = diaAberto.value?.toEpochDay()
        abrir(mes)
    }

    /**
     * Traz o board de volta a [mes] sem perder o dia aberto — ao contrário de [irPara], que
     * sempre reaplica a regra do dia (reabre hoje ou fecha o painel). Usado por quem só quer
     * "garantir que o board mostra este mês" (sair da lista, o botão Voltar), não navegar
     * para lá: se o mês já é este, não há nada a fazer.
     */
    fun sincronizarMes(mes: YearMonth) {
        if (mesAtual.value == mes) return
        irPara(mes)
    }

    /** Tocar no dia já aberto fecha o painel — é o mesmo toque desfazendo o que fez. */
    fun alternarDia(data: LocalDate) {
        diaAberto.value = if (diaAberto.value == data) null else data
        savedState[KEY_DIA] = diaAberto.value?.toEpochDay()
    }

    /**
     * Materializa as recorrências do mês, como o ledger fazia ao trocar de mês. A projeção
     * já mostra as ocorrências virtuais sem isto; o que isto compra é poder editá-las.
     */
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
        private const val KEY_MES = "board.mes"
        private const val KEY_DIA = "board.dia"

        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                BoardViewModel(
                    container.repository,
                    createSavedStateHandle(),
                    container.settings.settings.map { it.metaGuardarPercent }.distinctUntilChanged(),
                )
            }
        }
    }
}
