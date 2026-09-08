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
import com.scholze.saldo.domain.PaletaTags
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

data class EntryUiState(
    val editandoId: Long? = null,
    val recorrenciaId: Long? = null,
    val saida: Boolean = true,
    val natureza: Natureza = Natureza.DIARIO,
    val centavos: Long = 0,
    val descricao: String = "",
    val data: LocalDate = LocalDate.now(),
    val repetir: RepetirOpcao = RepetirOpcao.Nao,
    /** O que a linha era ao abrir a edição; é a diferença para [repetir] que decide a escrita. */
    val repetirOriginal: RepetirOpcao = RepetirOpcao.Nao,
    val tagsSelecionadas: List<Tag> = emptyList(),
    val todasTags: List<Tag> = emptyList(),
    val saldoResultanteCentavos: Long? = null,
) {
    /**
     * Só o valor prende o salvar. A descrição é opcional: exigi-la no caminho rápido — o
     * widget, o botão "lançar" de uma sugestão — só rendia "x" e "asdf" no lugar de nada.
     */
    val podeSalvar: Boolean get() = centavos > 0
    val valorAssinado: Long get() = if (saida) -centavos else centavos

    /** Editando uma instância que CONTINUA mensal: só aí "só este mês / daqui em diante" faz sentido. */
    val precisaEscopo: Boolean
        get() = editandoId != null && recorrenciaId != null && repetir is RepetirOpcao.TodoMes
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

    /** A linha COMO ESTÁ no ledger: o rodapé desconta o que ela já contribui, e o "desfazer" a reinsere. */
    private val original = MutableStateFlow<Movimentacao?>(null)

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
                        cartao = input.cartao,
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

    /**
     * [centavos] e [descricao] chegam pré-preenchidos quando a sheet foi aberta por uma
     * sugestão de notificação; nos outros casos são os zeros de sempre.
     */
    fun iniciarNova(hoje: LocalDate, centavos: Long = 0, descricao: String = "") {
        original.value = null
        form.value = EntryUiState(data = hoje, centavos = centavos, descricao = descricao)
    }

    /** O formulário como está — a shell decide o escopo por ele, e os testes o leem. */
    val formAgora: EntryUiState get() = form.value

    /**
     * [diaDoTemplate] é o dia da recorrência, quando a linha é uma instância: a data da linha
     * pode estar clamped (dia 31 num mês de 30) e ler o dia dela devolveria o dia errado ao
     * template na primeira edição de fevereiro.
     */
    fun iniciarEdicao(mov: Movimentacao, diaDoTemplate: Int? = null) {
        original.value = mov
        val repetir = if (mov.recorrenciaId != null) RepetirOpcao.TodoMes(diaDoTemplate ?: mov.data.dayOfMonth) else RepetirOpcao.Nao
        form.value = EntryUiState(
            editandoId = mov.id,
            recorrenciaId = mov.recorrenciaId,
            saida = mov.valorCentavos < 0,
            natureza = mov.natureza,
            centavos = kotlin.math.abs(mov.valorCentavos),
            descricao = mov.descricao,
            data = mov.data,
            repetir = repetir,
            repetirOriginal = repetir,
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
                val cor = PaletaTags.proxima(state.value.todasTags.map { it.cor })
                val id = repo.criarTag(nome, cor)
                onCriada(Tag(id = id, nome = nome, cor = cor))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "criarTag falhou", e)
            }
        }
    }

    /** [escopo] só é consultado quando se edita uma instância que continua mensal. */
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
            when {
                f.editandoId == null -> repo.criar(mov, f.repetir)
                // `converterEmRecorrencia`/`encerrarRecorrencia` gravam os campos da linha
                // sozinhos, na mesma transação — não há um `editar` separado para chamar antes.
                // Uma avulsa que vira mensal leva os valores novos para o template.
                f.repetirOriginal is RepetirOpcao.Nao && f.repetir is RepetirOpcao.TodoMes -> {
                    repo.converterEmRecorrencia(mov, f.repetir.dia)
                }
                f.repetirOriginal is RepetirOpcao.TodoMes && f.repetir is RepetirOpcao.Nao -> {
                    // O mês da SÉRIE é o de quando a sheet abriu (`original`), não `f.data`: o
                    // usuário pode ter movido a data no mesmo formulário antes de desligar o
                    // "repetir", e a série tem de acabar no mês de origem, não no de destino.
                    repo.encerrarRecorrencia(mov, YearMonth.from(original.value!!.data))
                }
                // O dia do TEMPLATE vem do formulário, não de `mov.data`: numa instância
                // clamped (31 → 28 em fevereiro) derivar o dia da data achataria a série.
                else -> repo.editar(mov, escopo, diaDoMes = (f.repetir as? RepetirOpcao.TodoMes)?.dia)
            }
        }
    }

    /**
     * Exclui a linha que a sheet abriu — o `original`, não o formulário.
     *
     * O formulário pode já ter sido mexido (o usuário trocou o valor e depois desistiu de tudo), e o
     * "desfazer" reinsere exatamente o que este método emitir: sair daqui com o rascunho faria a
     * linha voltar com um valor que nunca esteve no ledger.
     */
    fun excluir(onDone: () -> Unit) {
        val mov = original.value ?: return
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
