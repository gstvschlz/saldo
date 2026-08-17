package com.scholze.saldo.ui.ledger

import android.util.Log
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
import com.scholze.saldo.domain.Tag
import java.time.LocalDate
import java.time.YearMonth
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class LedgerUiState(
    /** `null` enquanto o primeiro `LedgerInput` não chegou do banco. */
    val mes: MesLedger?,
    val mesAtual: YearMonth,
    val filtro: FiltroLedger,
    val hoje: LocalDate,
    /** Etiqueta escolhida na aba tags; `null` = o mês inteiro. */
    val tagFiltro: Tag? = null,
)

/** Dia para o qual o ledger deve rolar assim que [mes] estiver na tela — pedido por um deep link. */
data class AlvoLedger(val mes: YearMonth, val dia: Int)

class LedgerViewModel(private val repo: SaldoRepository) : ViewModel() {

    private val mesAtual = MutableStateFlow(YearMonth.now())
    private val filtro = MutableStateFlow(FiltroLedger.TODAS)
    // Guarda o id, não a Tag: renomear ou apagar a etiqueta na aba tags tem de chegar
    // aqui, e um snapshot da Tag deixaria o chip preso ao nome antigo (ou o ledger preso
    // a uma etiqueta que já não existe, filtrando tudo para fora sem saída visível).
    private val tagFiltroId = MutableStateFlow<Long?>(null)
    private val _eventoExclusao = MutableSharedFlow<Movimentacao>(extraBufferCapacity = 1)

    /** Snapshot da linha apagada, para o "desfazer" do snackbar. */
    val eventoExclusao: SharedFlow<Movimentacao> = _eventoExclusao

    private val _alvo = MutableStateFlow<AlvoLedger?>(null)

    /** Consumido pela tela (`limparAlvo`) depois de rolar; separado de [state] para não engordar o combine. */
    val alvo: StateFlow<AlvoLedger?> = _alvo

    val state: StateFlow<LedgerUiState> =
        combine(repo.ledger, repo.tags, mesAtual, filtro, tagFiltroId) { input, tags, mes, f, tagId ->
            val tag = tagId?.let { id -> tags.firstOrNull { it.id == id } }
            LedgerUiState(
                mes = ProjectionEngine.mes(input, mes, f, tagId = tag?.id),
                mesAtual = mes,
                filtro = f,
                hoje = input.hoje,
                tagFiltro = tag,
            )
        }
            // A projeção do mês inteiro roda fora da main thread.
            .flowOn(Dispatchers.Default)
            // Uma exceção subindo do banco cancelaria o StateFlow e a tela ficaria
            // congelada para sempre, sem nem um crash que explicasse. Registrar e parar
            // de emitir preserva o último estado renderizado.
            .catch { Log.e(TAG, "fluxo do ledger falhou", it) }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                LedgerUiState(mes = null, mesAtual = mesAtual.value, filtro = filtro.value, hoje = LocalDate.now()),
            )

    init {
        abrir(mesAtual.value)
    }

    fun mesAnterior() = irPara(mesAtual.value.minusMonths(1))
    fun proximoMes() = irPara(mesAtual.value.plusMonths(1))

    /** Navega para [mes]; com [dia], o ledger rola até ele quando o mês chegar (deep link). */
    fun irPara(mes: YearMonth, dia: Int? = null) {
        mesAtual.value = mes
        _alvo.value = dia?.let { AlvoLedger(mes, it) }
        abrir(mes)
    }

    fun limparAlvo() { _alvo.value = null }

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

    fun definirFiltro(f: FiltroLedger) { filtro.value = f }

    fun definirTagFiltro(tag: Tag?) { tagFiltroId.value = tag?.id }

    fun excluir(mov: Movimentacao) {
        viewModelScope.launch {
            try {
                _eventoExclusao.emit(repo.excluir(mov))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "excluir falhou", e)
            }
        }
    }

    fun desfazerExclusao(snapshot: Movimentacao) {
        viewModelScope.launch {
            try {
                repo.restaurar(snapshot)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "restaurar falhou", e)
            }
        }
    }

    companion object {
        private const val TAG = "saldo"

        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { LedgerViewModel(container.repository) }
        }
    }
}
