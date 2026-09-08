package com.scholze.saldo.ui.tags

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.scholze.saldo.AppContainer
import com.scholze.saldo.data.SaldoRepository
import com.scholze.saldo.domain.ProjectionEngine
import com.scholze.saldo.domain.Tag
import com.scholze.saldo.domain.TagSnapshot
import com.scholze.saldo.ui.components.MENSAGEM_ERRO_LEITURA
import com.scholze.saldo.ui.fluxoComErro
import java.time.YearMonth
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Cada tag com o total de |saídas| que ela acumulou no mês corrente. */
data class TagsUiState(
    val tags: List<Pair<Tag, Long>> = emptyList(),
    /** Mensagem de falha de leitura; `null` = está tudo bem. Ver `fluxoComErro`. */
    val erro: String? = null,
)

class TagsViewModel(private val repo: SaldoRepository) : ViewModel() {

    private val tentativas = MutableStateFlow(0)

    /** O botão "tentar de novo" da tela: reassina o fluxo do banco. */
    fun tentarDeNovo() {
        tentativas.value++
    }

    val state: StateFlow<TagsUiState> = fluxoComErro(
        tentativas = tentativas,
        inicial = TagsUiState(),
        marcarErro = { it.copy(erro = MENSAGEM_ERRO_LEITURA) },
        limparErro = { it.copy(erro = null) },
        rotulo = "fluxo de tags",
    ) {
        combine(repo.ledger, repo.tags) { input, tags ->
            // Pelo motor, não por `input.movimentacoes`: a lista crua do banco não tem as
            // ocorrências virtuais de um mês nunca aberto (a tag ficaria zerada num mês que o resto
            // do app mostra cheio) e tem as linhas anteriores ao saldo inicial, que o resto ignora.
            val totais = ProjectionEngine.movimentacoesDoMes(input, YearMonth.from(input.hoje))
                .filter { it.valorCentavos < 0 }
                .flatMap { m -> m.tags.map { it.id to -m.valorCentavos } }
                .groupBy({ it.first }, { it.second })
                .mapValues { (_, v) -> v.sum() }
            TagsUiState(tags.map { it to (totais[it.id] ?: 0L) })
        }
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TagsUiState())

    private val _exclusoes = MutableSharedFlow<TagSnapshot>(extraBufferCapacity = 1)

    /** A tag apagada, para o "desfazer" do snackbar — o mesmo caminho das movimentações. */
    val exclusoes: SharedFlow<TagSnapshot> = _exclusoes

    fun criar(nome: String, cor: Long) = escrever("criarTag") { repo.criarTag(nome, cor) }
    fun renomear(tag: Tag, nome: String) = escrever("renomearTag") { repo.renomearTag(tag.id, nome) }
    fun recolorir(tag: Tag, cor: Long) = escrever("recolorirTag") { repo.recolorirTag(tag.id, cor) }
    fun excluir(tag: Tag) = escrever("excluirTag") { _exclusoes.emit(repo.excluirTag(tag.id)) }
    fun desfazerExclusao(snapshot: TagSnapshot) = escrever("restaurarTag") { repo.restaurarTag(snapshot) }

    private fun escrever(qual: String, bloco: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                bloco()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "$qual falhou", e)
            }
        }
    }

    companion object {
        private const val TAG = "saldo"

        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { TagsViewModel(container.repository) }
        }
    }
}
