package com.scholze.saldo.ui.tags

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.scholze.saldo.AppContainer
import com.scholze.saldo.data.SaldoRepository
import com.scholze.saldo.domain.Tag
import java.time.YearMonth
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Cada tag com o total de |saídas| que ela acumulou no mês corrente. */
data class TagsUiState(val tags: List<Pair<Tag, Long>> = emptyList())

class TagsViewModel(private val repo: SaldoRepository) : ViewModel() {

    val state: StateFlow<TagsUiState> = combine(repo.ledger, repo.tags) { input, tags ->
        val mes = YearMonth.from(input.hoje)
        val totais = input.movimentacoes
            .filter { YearMonth.from(it.data) == mes && it.valorCentavos < 0 }
            .flatMap { m -> m.tags.map { it.id to -m.valorCentavos } }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, v) -> v.sum() }
        TagsUiState(tags.map { it to (totais[it.id] ?: 0L) })
    }
        .catch { Log.e(TAG, "fluxo de tags falhou", it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TagsUiState())

    fun criar(nome: String, cor: Long) = escrever("criarTag") { repo.criarTag(nome, cor) }
    fun renomear(tag: Tag, nome: String) = escrever("renomearTag") { repo.renomearTag(tag.id, nome) }
    fun excluir(tag: Tag) = escrever("excluirTag") { repo.excluirTag(tag.id) }

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
