package com.scholze.saldo.ui.entry

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.scholze.saldo.AppContainer
import com.scholze.saldo.data.SaldoRepository
import com.scholze.saldo.domain.EscopoEdicao
import com.scholze.saldo.domain.EscopoExclusao
import com.scholze.saldo.domain.FiltroLedger
import com.scholze.saldo.domain.Movimentacao
import com.scholze.saldo.domain.Natureza
import com.scholze.saldo.domain.ProjectionEngine
import com.scholze.saldo.domain.RepetirOpcao
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

private const val TAG = "saldo"
private val CORES_TAG = listOf(0xFFA6486BL, 0xFFB95A2EL, 0xFF2A7A86L, 0xFF4B4BC4L, 0xFF14663AL)

data class EntryUiState(
    val editandoId: Long? = null,
    val recorrenciaId: Long? = null,
    val saida: Boolean = true,
    val natureza: Natureza = Natureza.DIARIO,
    val centavos: Long = 0,
    val descricao: String = "",
    val data: LocalDate = LocalDate.now(),
    val repetir: RepetirOpcao = RepetirOpcao.Nao,
    val tagsSelecionadas: List<Tag> = emptyList(),
    val todasTags: List<Tag> = emptyList(),
    val saldoResultanteCentavos: Long? = null,
) {
    val podeSalvar: Boolean get() = centavos > 0 && descricao.isNotBlank()
    val valorAssinado: Long get() = if (saida) -centavos else centavos
}

/**
 * Form state for the nova/editar movimentação sheet.
 *
 * Two flows, on purpose. [form] is what the user typed and is the ONLY thing the write path
 * reads — it is never stale, never throttled, and exists whether or not anyone is collecting.
 * [state] is that same form decorated with what only the repository knows (tags, and the
 * "saldo do dia" footer); being a `WhileSubscribed` `StateFlow`, it can be a frame behind or,
 * with no subscriber, back at its initial value — fine for rendering, wrong for saving.
 *
 * Because the form is ViewModel-scoped it survives rotation; only the sheet's own open/closed
 * flag needs `rememberSaveable`.
 */
class EntryViewModel(private val repo: SaldoRepository) : ViewModel() {

    private val form = MutableStateFlow(EntryUiState())

    /** A linha COMO ESTÁ no ledger, para o rodapé descontar o que ela já contribui. */
    private data class Original(val valorCentavos: Long, val data: LocalDate, val natureza: Natureza)

    private val original = MutableStateFlow<Original?>(null)

    private val _erros = MutableSharedFlow<String>(extraBufferCapacity = 1)
    private val _exclusoes = MutableSharedFlow<Movimentacao>(extraBufferCapacity = 1)

    /** Falha de escrita que a shell mostra no snackbar — a sheet NÃO fecha. */
    val erros: SharedFlow<String> = _erros

    /** Snapshot da linha apagada pela sheet, para o mesmo "desfazer" do swipe. */
    val exclusoes: SharedFlow<Movimentacao> = _exclusoes

    val state: StateFlow<EntryUiState> =
        combine(form, original, repo.ledger, repo.tags) { f, orig, input, tags ->
            val mesLedger = ProjectionEngine.mes(input, YearMonth.from(f.data), FiltroLedger.TODAS)
            val saldoDoDia = mesLedger.dias.getOrNull(f.data.dayOfMonth - 1)?.saldoCentavos
            f.copy(
                todasTags = tags,
                saldoResultanteCentavos = saldoDoDia?.let {
                    saldoResultante(
                        saldoDoDia = it,
                        natureza = f.natureza,
                        valorAssinado = f.valorAssinado,
                        naturezaOriginal = orig?.natureza,
                        valorOriginal = orig?.valorCentavos ?: 0L,
                        dataForm = f.data,
                        dataOriginal = orig?.data,
                    )
                },
            )
        }
            // A projeção do mês inteiro roda fora da main thread.
            .flowOn(Dispatchers.Default)
            // Uma falha vinda do banco não pode matar o StateFlow do formulário: registrar e
            // parar de emitir preserva o último estado, em vez de congelar a sheet vazia.
            .catch { Log.e(TAG, "fluxo do formulário falhou", it) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EntryUiState())

    fun iniciarNova(hoje: LocalDate) {
        original.value = null
        form.value = EntryUiState(data = hoje)
    }

    fun iniciarEdicao(mov: Movimentacao) {
        original.value = Original(mov.valorCentavos, mov.data, mov.natureza)
        form.value = EntryUiState(
            editandoId = mov.id,
            recorrenciaId = mov.recorrenciaId,
            saida = mov.valorCentavos < 0,
            natureza = mov.natureza,
            centavos = kotlin.math.abs(mov.valorCentavos),
            descricao = mov.descricao,
            data = mov.data,
            repetir = if (mov.recorrenciaId != null) RepetirOpcao.TodoMes(mov.data.dayOfMonth) else RepetirOpcao.Nao,
            tagsSelecionadas = mov.tags,
        )
    }

    fun definirSaida(v: Boolean) = form.atualizar { copy(saida = v) }
    fun definirNatureza(n: Natureza) = form.atualizar { copy(natureza = n) }
    fun definirCentavos(v: Long) = form.atualizar { copy(centavos = v) }
    fun definirDescricao(v: String) = form.atualizar { copy(descricao = v) }
    fun definirData(v: LocalDate) = form.atualizar { copy(data = v) }
    fun definirRepetir(r: RepetirOpcao) = form.atualizar { copy(repetir = r) }

    fun alternarTag(tag: Tag) = form.atualizar {
        copy(
            tagsSelecionadas =
                if (tagsSelecionadas.any { it.id == tag.id }) tagsSelecionadas.filterNot { it.id == tag.id }
                else tagsSelecionadas + tag,
        )
    }

    fun criarTagInline(nome: String, onCriada: (Tag) -> Unit) {
        viewModelScope.launch {
            try {
                // A cor só rotaciona sobre as tags já existentes; se o state ainda não emitiu,
                // a primeira cor é uma escolha tão boa quanto qualquer outra.
                val cor = CORES_TAG[state.value.todasTags.size % CORES_TAG.size]
                val id = repo.criarTag(nome, cor)
                onCriada(Tag(id = id, nome = nome, cor = cor))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "criarTag falhou", e)
            }
        }
    }

    /** [escopo] só é consultado quando se edita uma instância de recorrência. */
    fun salvar(escopo: EscopoEdicao, onDone: () -> Unit) {
        val f = form.value
        if (!f.podeSalvar) return
        escrever("salvar", "não foi possível salvar", onDone) {
            val mov = Movimentacao(
                id = f.editandoId ?: 0,
                descricao = f.descricao.trim(),
                valorCentavos = f.valorAssinado,
                data = f.data,
                natureza = f.natureza,
                recorrenciaId = f.recorrenciaId,
                tags = f.tagsSelecionadas,
            )
            if (f.editandoId == null) repo.criar(mov, f.repetir) else repo.editar(mov, escopo)
        }
    }

    fun excluir(onDone: () -> Unit) {
        val f = form.value
        val id = f.editandoId ?: return
        // As tags entram no snapshot: é ele que o "desfazer" reinsere, e sem elas a linha
        // voltaria pelada.
        val mov = Movimentacao(
            id = id, descricao = f.descricao, valorCentavos = f.valorAssinado,
            data = f.data, natureza = f.natureza, recorrenciaId = f.recorrenciaId,
            tags = f.tagsSelecionadas,
        )
        escrever("excluir", "não foi possível excluir", onDone) {
            _exclusoes.emit(repo.excluir(mov))
        }
    }

    fun excluirRecorrencia(escopo: EscopoExclusao, onDone: () -> Unit) {
        val f = form.value
        val recId = f.recorrenciaId ?: return
        escrever("excluirRecorrencia", "não foi possível excluir", onDone) {
            repo.excluirRecorrencia(recId, YearMonth.from(f.data), escopo)
        }
    }

    /**
     * Toda escrita da sheet passa por aqui.
     *
     * O ponto é o `return@launch`: antes, uma falha era registrada e o [onDone] rodava
     * assim mesmo — a sheet fechava, o usuário via o formulário sumir e concluía que
     * gravou. Agora a sheet fica aberta com o que ele digitou e o erro vira snackbar.
     */
    private fun escrever(qual: String, mensagem: String, onDone: () -> Unit, bloco: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                bloco()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "$qual falhou", e)
                _erros.emit(mensagem)
                return@launch
            }
            onDone()
        }
    }

    private inline fun MutableStateFlow<EntryUiState>.atualizar(bloco: EntryUiState.() -> EntryUiState) {
        value = value.bloco()
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { EntryViewModel(container.repository) }
        }
    }
}
