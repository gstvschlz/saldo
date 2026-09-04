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
import com.scholze.saldo.domain.FiltroLedger
import com.scholze.saldo.domain.MesLedger
import com.scholze.saldo.domain.ProjectionEngine
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * [board] e [mes] são `null` só enquanto o primeiro `LedgerInput` não chegou do banco.
 *
 * [mes] é o mês corrente inteiro, e existe por um motivo só: o hero do board é o MESMO
 * saldo projetado que o ledger mostra, e o número não pode mudar quando a vista troca.
 * Recalculá-lo aqui, em vez de espiar o `LedgerViewModel`, mantém as duas vistas
 * independentes — e a conta é a mesma função pura nos dois lados.
 */
data class BoardUiState(val board: Board?, val mes: MesLedger?, val hoje: LocalDate)

class BoardViewModel(repo: SaldoRepository) : ViewModel() {

    val state: StateFlow<BoardUiState> =
        repo.ledger
            .map { input ->
                BoardUiState(
                    board = BoardEngine.board(input),
                    mes = ProjectionEngine.mes(input, YearMonth.from(input.hoje), FiltroLedger.TODAS),
                    hoje = input.hoje,
                )
            }
            // 365 dias de agrupamento mais a projeção do mês: fora da main thread.
            .flowOn(Dispatchers.Default)
            // Mesma razão do LedgerViewModel: uma exceção do banco cancelaria o StateFlow
            // e a tela congelaria para sempre, sem nem um crash que explicasse.
            .catch { Log.e(TAG, "fluxo do board falhou", it) }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                BoardUiState(board = null, mes = null, hoje = LocalDate.now()),
            )

    companion object {
        private const val TAG = "saldo"

        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { BoardViewModel(container.repository) }
        }
    }
}
