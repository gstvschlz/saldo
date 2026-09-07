package com.scholze.saldo.ui.ledger

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
import com.scholze.saldo.domain.Busca
import com.scholze.saldo.domain.FiltroLedger
import com.scholze.saldo.domain.MesLedger
import com.scholze.saldo.domain.Movimentacao
import com.scholze.saldo.domain.ProjectionEngine
import com.scholze.saldo.domain.Recorrencia
import com.scholze.saldo.domain.Tag
import com.scholze.saldo.ui.toLongChave
import com.scholze.saldo.ui.toYearMonth
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
import kotlinx.coroutines.flow.first
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
    /** Os resultados da busca, mais recentes primeiro; `null` = busca fechada ou em branco. */
    val resultados: List<Movimentacao>? = null,
    /** Os templates, para a shell achar o dia da recorrência de uma linha ao abrir a sheet. */
    val recorrencias: List<Recorrencia> = emptyList(),
)

/** Dia para o qual o ledger deve rolar assim que [mes] estiver na tela — pedido por um deep link. */
data class AlvoLedger(val mes: YearMonth, val dia: Int)

class LedgerViewModel(
    private val repo: SaldoRepository,
    private val savedState: SavedStateHandle = SavedStateHandle(),
) : ViewModel() {

    // Mês, filtro, tag e busca no SavedStateHandle: sobrevivem à morte do processo. Sem isto,
    // voltar ao app depois de um tempo mostrava a subtela da tag sem tag — e sem o × para sair.
    private val mesAtual = MutableStateFlow(savedState.get<Long>(KEY_MES)?.toYearMonth() ?: YearMonth.now())
    private val filtro = MutableStateFlow(
        savedState.get<String>(KEY_FILTRO)?.let { FiltroLedger.valueOf(it) } ?: FiltroLedger.TODAS,
    )
    // Guarda o id, não a Tag: renomear ou apagar a etiqueta na aba tags tem de chegar
    // aqui, e um snapshot da Tag deixaria o chip preso ao nome antigo (ou o ledger preso
    // a uma etiqueta que já não existe, filtrando tudo para fora sem saída visível).
    private val tagFiltroId = MutableStateFlow<Long?>(savedState.get<Long>(KEY_TAG))

    /** O texto da busca; `null` = busca fechada, "" = aberta sem nada digitado. */
    private val _busca = MutableStateFlow<String?>(savedState.get<String>(KEY_BUSCA))
    val busca: StateFlow<String?> = _busca

    private val _eventoExclusao = MutableSharedFlow<Movimentacao>(extraBufferCapacity = 1)

    /** Snapshot da linha apagada, para o "desfazer" do snackbar. */
    val eventoExclusao: SharedFlow<Movimentacao> = _eventoExclusao

    private val _alvo = MutableStateFlow<AlvoLedger?>(null)

    /** Consumido pela tela (`limparAlvo`) depois de rolar; separado de [state] para não engordar o combine. */
    val alvo: StateFlow<AlvoLedger?> = _alvo

    /** Leituras síncronas para a shell e para os testes do saved state. */
    val mesAtualAgora: YearMonth get() = mesAtual.value
    val filtroAgora: FiltroLedger get() = filtro.value
    val tagFiltroIdAgora: Long? get() = tagFiltroId.value

    /** Os cinco controles do usuário, combinados uma vez: o `combine` de seis fluxos perde os tipos. */
    private data class Controles(val mes: YearMonth, val filtro: FiltroLedger, val tagId: Long?, val busca: String?)

    private val controles = combine(mesAtual, filtro, tagFiltroId, _busca) { m, f, t, b -> Controles(m, f, t, b) }

    val state: StateFlow<LedgerUiState> =
        combine(repo.ledger, repo.tags, controles) { input, tags, c ->
            val tag = c.tagId?.let { id -> tags.firstOrNull { it.id == id } }
            LedgerUiState(
                mes = ProjectionEngine.mes(input, c.mes, c.filtro, tagId = tag?.id),
                mesAtual = c.mes,
                filtro = c.filtro,
                hoje = input.hoje,
                tagFiltro = tag,
                resultados = c.busca?.takeIf { it.isNotBlank() }?.let { q ->
                    Busca.filtrar(ProjectionEngine.movimentacoesAte(input, YearMonth.from(input.hoje)), q, input.hoje)
                },
                recorrencias = input.recorrencias,
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
        viewModelScope.launch { mesAtual.collect { savedState[KEY_MES] = it.toLongChave() } }
        viewModelScope.launch { filtro.collect { savedState[KEY_FILTRO] = it.name } }
        viewModelScope.launch { tagFiltroId.collect { savedState[KEY_TAG] = it } }
        viewModelScope.launch { _busca.collect { savedState[KEY_BUSCA] = it } }
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

    fun definirTagFiltro(tag: Tag?) = definirTagFiltroId(tag?.id)
    fun definirTagFiltroId(id: Long?) { tagFiltroId.value = id }

    fun abrirBusca() { if (_busca.value == null) _busca.value = "" }
    fun fecharBusca() { _busca.value = null }
    fun definirBusca(texto: String) { _busca.value = texto }

    /**
     * Abre um resultado da busca. Uma ocorrência virtual (`id == 0`) precisa do mês
     * materializado antes: [SaldoRepository.abrirMes] e a releitura do ledger trazem a linha
     * com id, que é o que a sheet precisa para gravar — o mesmo caminho de
     * `RecorrenciasViewModel.abrirOcorrencia`.
     */
    fun abrirResultado(mov: Movimentacao, onPronta: (Movimentacao) -> Unit) {
        if (mov.id != 0L) { onPronta(mov); return }
        val m = YearMonth.from(mov.data)
        viewModelScope.launch {
            try {
                repo.abrirMes(m)
                val linha = repo.ledger.first().movimentacoes.firstOrNull {
                    it.recorrenciaId == mov.recorrenciaId && it.data == mov.data
                }
                if (linha != null && linha.id != 0L) onPronta(linha)
                else Log.w(TAG, "resultado virtual sem linha depois de abrir $m")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "abrirResultado falhou", e)
            }
        }
    }

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
        private const val KEY_MES = "ledger.mes"
        private const val KEY_FILTRO = "ledger.filtro"
        private const val KEY_TAG = "ledger.tag"
        private const val KEY_BUSCA = "ledger.busca"

        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { LedgerViewModel(container.repository, createSavedStateHandle()) }
        }
    }
}
