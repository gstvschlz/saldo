package com.scholze.saldo.ui.ledger

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.scholze.saldo.AppContainer
import com.scholze.saldo.data.SaldoRepository
import com.scholze.saldo.domain.FiltroLedger
import com.scholze.saldo.domain.MesLedger
import com.scholze.saldo.domain.Movimentacao
import com.scholze.saldo.domain.ProjectionEngine
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class LedgerUiState(
    /** `null` enquanto o primeiro `LedgerInput` não chegou do banco. */
    val mes: MesLedger?,
    val mesAtual: YearMonth,
    val filtro: FiltroLedger,
    val hoje: LocalDate,
)

class LedgerViewModel(private val repo: SaldoRepository) : ViewModel() {

    private val mesAtual = MutableStateFlow(YearMonth.now())
    private val filtro = MutableStateFlow(FiltroLedger.TODAS)
    private val _eventoExclusao = MutableSharedFlow<Movimentacao>(extraBufferCapacity = 1)

    /** Snapshot da linha apagada, para o "desfazer" do snackbar. */
    val eventoExclusao: SharedFlow<Movimentacao> = _eventoExclusao

    val state: StateFlow<LedgerUiState> =
        combine(repo.ledger, mesAtual, filtro) { input, mes, f ->
            LedgerUiState(
                mes = ProjectionEngine.mes(input, mes, f),
                mesAtual = mes,
                filtro = f,
                hoje = input.hoje,
            )
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            LedgerUiState(mes = null, mesAtual = mesAtual.value, filtro = filtro.value, hoje = LocalDate.now()),
        )

    init {
        viewModelScope.launch { repo.abrirMes(mesAtual.value) }
    }

    fun mesAnterior() = irPara(mesAtual.value.minusMonths(1))
    fun proximoMes() = irPara(mesAtual.value.plusMonths(1))

    private fun irPara(mes: YearMonth) {
        mesAtual.value = mes
        viewModelScope.launch { repo.abrirMes(mes) }
    }

    fun definirFiltro(f: FiltroLedger) { filtro.value = f }

    fun excluir(mov: Movimentacao) {
        viewModelScope.launch { _eventoExclusao.emit(repo.excluir(mov)) }
    }

    fun desfazerExclusao(snapshot: Movimentacao) {
        viewModelScope.launch { repo.restaurar(snapshot) }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { LedgerViewModel(container.repository) }
        }
    }
}
