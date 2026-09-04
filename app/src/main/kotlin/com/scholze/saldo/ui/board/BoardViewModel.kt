package com.scholze.saldo.ui.board

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
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
import java.time.LocalDate
import java.time.YearMonth
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
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

class BoardViewModel(private val repo: SaldoRepository) : ViewModel() {

    private val mesAtual = MutableStateFlow(YearMonth.now())

    // O app abre respondendo "o que eu gastei hoje": no mês corrente, hoje já vem aberto.
    private val diaAberto = MutableStateFlow<LocalDate?>(LocalDate.now())

    val state: StateFlow<BoardUiState> =
        combine(repo.ledger, mesAtual, diaAberto) { input, mes, dia ->
            BoardUiState(
                board = BoardEngine.board(input, mes),
                mes = ProjectionEngine.mes(input, mes, FiltroLedger.TODAS),
                hoje = input.hoje,
                mesAtual = mes,
                diaAberto = dia,
            )
        }
            // O agrupamento por dia mais a projeção do mês: fora da main thread.
            .flowOn(Dispatchers.Default)
            // Mesma razão do LedgerViewModel: uma exceção do banco cancelaria o StateFlow
            // e a tela congelaria para sempre, sem nem um crash que explicasse.
            .catch { Log.e(TAG, "fluxo do board falhou", it) }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                BoardUiState(board = null, mes = null, hoje = LocalDate.now(), diaAberto = LocalDate.now()),
            )

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
        diaAberto.value = when {
            dia != null -> mes.atDay(dia.coerceIn(1, mes.lengthOfMonth()))
            mes == YearMonth.now() -> LocalDate.now()
            else -> null
        }
        abrir(mes)
    }

    /** Tocar no dia já aberto fecha o painel — é o mesmo toque desfazendo o que fez. */
    fun alternarDia(data: LocalDate) {
        diaAberto.value = if (diaAberto.value == data) null else data
    }

    fun fecharDia() {
        diaAberto.value = null
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

        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { BoardViewModel(container.repository) }
        }
    }
}
