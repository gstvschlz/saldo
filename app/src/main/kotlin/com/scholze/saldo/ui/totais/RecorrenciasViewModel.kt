package com.scholze.saldo.ui.totais

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.scholze.saldo.AppContainer
import com.scholze.saldo.data.SaldoRepository
import com.scholze.saldo.domain.InsightsEngine
import com.scholze.saldo.domain.Movimentacao
import com.scholze.saldo.domain.Recorrencia
import com.scholze.saldo.domain.ResumoRecorrencias
import com.scholze.saldo.ui.components.MENSAGEM_ERRO_LEITURA
import com.scholze.saldo.ui.fluxoComErro
import java.time.LocalDate
import java.time.YearMonth
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** O resumo do mês visto, e a falha de leitura quando ela acontece. */
data class RecorrenciasUiState(val resumo: ResumoRecorrencias? = null, val erro: String? = null)

/** A tela de recorrências: um resumo do mês visto na aba totais, mais a ponte para o editor. */
class RecorrenciasViewModel(private val repo: SaldoRepository) : ViewModel() {

    private val mes = MutableStateFlow(YearMonth.now())

    private val tentativas = MutableStateFlow(0)

    /** O botão "tentar de novo" da tela: reassina o fluxo do banco. */
    fun tentarDeNovo() {
        tentativas.value++
    }

    val state: StateFlow<RecorrenciasUiState> = fluxoComErro(
        tentativas = tentativas,
        inicial = RecorrenciasUiState(),
        marcarErro = { it.copy(erro = MENSAGEM_ERRO_LEITURA) },
        limparErro = { it.copy(erro = null) },
        rotulo = "fluxo de recorrências",
    ) {
        combine(repo.ledger, mes) { input, m -> RecorrenciasUiState(InsightsEngine.recorrencias(input, m)) }
            .flowOn(Dispatchers.Default)
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RecorrenciasUiState())

    /** O mês visto é o da aba totais; esta tela é só uma leitura dele. */
    fun verMes(m: YearMonth) {
        mes.value = m
    }

    /**
     * Abre no editor a ocorrência de [rec] **no mês visto**.
     *
     * A ocorrência pode ainda ser virtual (`id == 0`) num mês não materializado:
     * [SaldoRepository.abrirMes] materializa e a releitura do ledger traz a linha com id — que é
     * o que `iniciarEdicao` precisa para gravar com `SO_ESTE_MES`. Um template que só começa
     * depois do mês visto não tem ocorrência nenhuma: nada abre (a tela também não deixa tocar
     * nessas linhas).
     */
    fun abrirOcorrencia(rec: Recorrencia, onPronta: (Movimentacao) -> Unit) {
        val m = mes.value
        viewModelScope.launch {
            try {
                repo.abrirMes(m)
                val ocorrencia = repo.ledger.first().movimentacoes.firstOrNull {
                    it.recorrenciaId == rec.id && YearMonth.from(it.data) == m
                }
                if (ocorrencia != null && ocorrencia.id != 0L) {
                    onPronta(ocorrencia)
                } else {
                    Log.w(TAG, "sem ocorrência da recorrência ${rec.id} em $m")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "abrirOcorrencia(${rec.id}, $m) falhou", e)
            }
        }
    }

    /** Pausar/retomar, pelo `ativa` do template — ver `SaldoRepository.pausar` para o que fica e o que some. */
    fun alternarPausa(rec: Recorrencia) {
        viewModelScope.launch {
            try {
                if (rec.ativa) repo.pausar(rec.id, LocalDate.now()) else repo.retomar(rec.id, LocalDate.now())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "alternarPausa(${rec.id}) falhou", e)
            }
        }
    }

    companion object {
        private const val TAG = "saldo"

        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { RecorrenciasViewModel(container.repository) }
        }
    }
}
