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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

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
 * The form itself lives in [form]; [state] decorates it with everything that has
 * to come from the repository (tags, and the "saldo do dia" footer projected with
 * the value being typed). Because the form is ViewModel-scoped it survives
 * rotation — only the sheet's own open/closed flag is `rememberSaveable`.
 */
class EntryViewModel(private val repo: SaldoRepository) : ViewModel() {

    private val form = MutableStateFlow(EntryUiState())

    /** Valor já contabilizado no ledger pela linha em edição — descontado no rodapé. */
    private var valorOriginal: Long = 0

    val state: StateFlow<EntryUiState> =
        combine(form, repo.ledger, repo.tags) { f, input, tags ->
            val mesLedger = ProjectionEngine.mes(input, YearMonth.from(f.data), FiltroLedger.TODAS)
            val saldoDoDia = mesLedger.dias.getOrNull(f.data.dayOfMonth - 1)?.saldoCentavos
            f.copy(
                todasTags = tags,
                saldoResultanteCentavos = saldoDoDia?.plus(f.valorAssinado - valorOriginal),
            )
        }
            // Uma falha vinda do banco não pode matar o StateFlow do formulário: registrar e
            // parar de emitir preserva o último estado, em vez de congelar a sheet vazia.
            .catch { Log.e(TAG, "fluxo do formulário falhou", it) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EntryUiState())

    fun iniciarNova(hoje: LocalDate) {
        valorOriginal = 0
        form.value = EntryUiState(data = hoje)
    }

    fun iniciarEdicao(mov: Movimentacao) {
        valorOriginal = mov.valorCentavos
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
            runCatching {
                val cor = CORES_TAG[state.value.todasTags.size % CORES_TAG.size]
                val id = repo.criarTag(nome, cor)
                onCriada(Tag(id = id, nome = nome, cor = cor))
            }.onFailure { Log.e(TAG, "criarTag falhou", it) }
        }
    }

    /** [escopo] só é consultado quando se edita uma instância de recorrência. */
    fun salvar(escopo: EscopoEdicao, onDone: () -> Unit) {
        val f = state.value
        if (!f.podeSalvar) return
        viewModelScope.launch {
            runCatching {
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
            }.onFailure { Log.e(TAG, "salvar falhou", it) }
            onDone()
        }
    }

    fun excluir(onDone: () -> Unit) {
        val f = state.value
        val id = f.editandoId ?: return
        viewModelScope.launch {
            runCatching {
                repo.excluir(
                    Movimentacao(
                        id = id, descricao = f.descricao, valorCentavos = f.valorAssinado,
                        data = f.data, natureza = f.natureza, recorrenciaId = f.recorrenciaId,
                    ),
                )
            }.onFailure { Log.e(TAG, "excluir falhou", it) }
            onDone()
        }
    }

    fun excluirRecorrencia(escopo: EscopoExclusao, onDone: () -> Unit) {
        val f = state.value
        val recId = f.recorrenciaId ?: return
        viewModelScope.launch {
            runCatching { repo.excluirRecorrencia(recId, YearMonth.from(f.data), escopo) }
                .onFailure { Log.e(TAG, "excluirRecorrencia falhou", it) }
            onDone()
        }
    }

    private inline fun MutableStateFlow<EntryUiState>.atualizar(bloco: EntryUiState.() -> EntryUiState) {
        value = value.bloco()
    }

    companion object {
        private const val TAG = "saldo"
        private val CORES_TAG = listOf(0xFFA6486BL, 0xFFB95A2EL, 0xFF2A7A86L, 0xFF4B4BC4L, 0xFF14663AL)

        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { EntryViewModel(container.repository) }
        }
    }
}
