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
import com.scholze.saldo.domain.TagsSugeridas
import com.scholze.saldo.domain.Teto
import com.scholze.saldo.domain.TetoEngine
import com.scholze.saldo.ui.components.MENSAGEM_ERRO_LEITURA
import com.scholze.saldo.ui.fluxoComErro
import com.scholze.saldo.ui.toLongChave
import com.scholze.saldo.ui.toYearMonth
import java.time.LocalDate
import java.time.YearMonth
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
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
    /** Os templates, para a shell achar o dia da recorrência de uma linha ao abrir a sheet. */
    val recorrencias: List<Recorrencia> = emptyList(),
    /** Mensagem de falha de leitura; `null` = está tudo bem. Ver `fluxoComErro`. */
    val erro: String? = null,
    /**
     * Até seis etiquetas para a fileira de chips da fila — as mais usadas nos últimos 90 dias
     * primeiro, depois alfabética (ver [com.scholze.saldo.domain.TagsSugeridas]). A tela só as
     * desenha sob [FiltroLedger.SEM_TAG]; aqui elas estão sempre calculadas, porque o custo é uma
     * passada sobre as linhas do ledger e o `combine` já roda no `Dispatchers.Default`.
     */
    val tagsSugeridas: List<Tag> = emptyList(),
    /**
     * O teto do dia; `null` fora do mês corrente e num mês sem entrada nenhuma. O hero some com
     * a linha nos dois casos — "hoje" não existe em setembro visto de outubro, e sem renda não há
     * o que dividir (ver [com.scholze.saldo.domain.TetoEngine.teto]).
     */
    val teto: Teto? = null,
)

/** Dia para o qual o ledger deve rolar assim que [mes] estiver na tela — pedido por um deep link. */
data class AlvoLedger(val mes: YearMonth, val dia: Int)

/** O que o snackbar de "etiquetado" precisa: a linha, para desfazer, e o nome, para a frase. */
data class EtiquetaAplicada(val movId: Long, val tagNome: String)

class LedgerViewModel(
    private val repo: SaldoRepository,
    private val savedState: SavedStateHandle = SavedStateHandle(),
    /**
     * A meta de guardar, que é a reserva mínima do teto do dia. Um `Flow<Int>` e não o
     * `SettingsStore` inteiro, como em `TotaisViewModel`: assinar o `Settings` completo faria
     * toda troca de tema recalcular a projeção do mês.
     */
    private val metaGuardar: Flow<Int> = flowOf(0),
) : ViewModel() {

    // Mês, filtro, tag e busca no SavedStateHandle: sobrevivem à morte do processo. Sem isto,
    // voltar ao app depois de um tempo mostrava a subtela da tag sem tag — e sem o × para sair.
    private val mesAtual = MutableStateFlow(savedState.get<Long>(KEY_MES)?.toYearMonth() ?: YearMonth.now())
    private val filtro = MutableStateFlow(
        savedState.get<String>(KEY_FILTRO)?.let { salvo -> FiltroLedger.entries.firstOrNull { it.name == salvo } }
            ?: FiltroLedger.TODAS,
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

    private val _eventoEtiqueta = MutableSharedFlow<EtiquetaAplicada>(extraBufferCapacity = 1)

    /** Emitido a cada toque na fileira de chips da fila; a shell mostra o snackbar com "desfazer". */
    val eventoEtiqueta: SharedFlow<EtiquetaAplicada> = _eventoEtiqueta

    private val _alvo = MutableStateFlow<AlvoLedger?>(null)

    /** Consumido pela tela (`limparAlvo`) depois de rolar; separado de [state] para não engordar o combine. */
    val alvo: StateFlow<AlvoLedger?> = _alvo

    /** Leituras síncronas para a shell e para os testes do saved state. */
    val mesAtualAgora: YearMonth get() = mesAtual.value
    val filtroAgora: FiltroLedger get() = filtro.value
    val tagFiltroIdAgora: Long? get() = tagFiltroId.value

    /** Os controles do mês, combinados uma vez: o `combine` de vários fluxos perde os tipos. */
    private data class Controles(val mes: YearMonth, val filtro: FiltroLedger, val tagId: Long?)

    private val controles = combine(mesAtual, filtro, tagFiltroId) { m, f, t -> Controles(m, f, t) }

    private val tentativas = MutableStateFlow(0)

    /** O botão "tentar de novo" da tela: reassina o fluxo do banco. */
    fun tentarDeNovo() {
        tentativas.value++
    }

    private val inicial = LedgerUiState(
        mes = null, mesAtual = mesAtual.value, filtro = filtro.value, hoje = LocalDate.now(),
    )

    val state: StateFlow<LedgerUiState> = fluxoComErro(
        tentativas = tentativas,
        inicial = inicial,
        marcarErro = { it.copy(erro = MENSAGEM_ERRO_LEITURA) },
        limparErro = { it.copy(erro = null) },
        rotulo = "fluxo do ledger",
    ) {
        combine(repo.ledger, repo.tags, controles, metaGuardar) { input, tags, c, meta ->
            val tag = c.tagId?.let { id -> tags.firstOrNull { it.id == id } }
            LedgerUiState(
                mes = ProjectionEngine.mes(input, c.mes, c.filtro, tagId = tag?.id),
                mesAtual = c.mes,
                filtro = c.filtro,
                hoje = input.hoje,
                tagFiltro = tag,
                recorrencias = input.recorrencias,
                tagsSugeridas = TagsSugeridas.paraFila(tags, input.movimentacoes, input.hoje),
                teto = if (c.mes == YearMonth.from(input.hoje)) TetoEngine.teto(input, meta) else null,
            )
        }
            // A projeção do mês inteiro roda fora da main thread.
            .flowOn(Dispatchers.Default)
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), inicial)

    /**
     * Os resultados da busca, à parte do [state]: `state` combina mês, filtro e tag, que não
     * mudam a cada tecla — misturar a busca ali forçaria `ProjectionEngine.mes` (a projeção do
     * mês inteiro) a rodar de novo a cada caractere digitado. Aqui só as movimentações
     * efetivas são recalculadas, e só quando `repo.ledger` ou o texto mudam.
     */
    val resultados: StateFlow<List<Movimentacao>?> =
        combine(repo.ledger, _busca) { input, q ->
            q?.takeIf { it.isNotBlank() }?.let {
                Busca.filtrar(ProjectionEngine.movimentacoesAte(input, YearMonth.from(input.hoje)), it, input.hoje)
            }
        }
            .flowOn(Dispatchers.Default)
            // Continua sendo um `.catch` seco, ao contrário do [state]: uma busca que falha não
            // congela a tela (o mês continua lá, vindo do outro fluxo) e não há botão só dela
            // para reassinar — fechar e reabrir a busca já refaz a leitura.
            .catch { Log.e(TAG, "busca do ledger falhou", it) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        abrir(mesAtual.value)
    }

    fun mesAnterior() = irPara(mesAtual.value.minusMonths(1))
    fun proximoMes() = irPara(mesAtual.value.plusMonths(1))

    /** Navega para [mes]; com [dia], o ledger rola até ele quando o mês chegar (deep link). */
    fun irPara(mes: YearMonth, dia: Int? = null) {
        mesAtual.value = mes
        savedState[KEY_MES] = mes.toLongChave()
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

    fun definirFiltro(f: FiltroLedger) {
        // `sem tag` e o filtro de etiqueta são mutuamente exclusivos: a interseção é sempre vazia
        // (uma linha sem etiqueta nenhuma nunca tem AQUELA etiqueta), e deixar os dois ligados
        // daria uma lista vazia sem nada na tela explicando por quê.
        //
        // Desliga o outro ANTES de ligar este: `filtro` e `tagFiltroId` são dois StateFlow, e o
        // `combine` roda no Default — escrever na outra ordem abriria uma janela em que ele podia
        // publicar exatamente o par proibido, e a tela piscaria vazia antes de se corrigir.
        if (f == FiltroLedger.SEM_TAG) definirTagFiltroId(null)
        filtro.value = f
        savedState[KEY_FILTRO] = f.name
    }

    fun definirTagFiltro(tag: Tag?) = definirTagFiltroId(tag?.id)

    fun definirTagFiltroId(id: Long?) {
        // O outro lado da exclusividade — e só de `sem tag` para `todas`: entrar numa etiqueta com
        // `diários` ou `fixas` ligados continua sendo uma interseção legítima, e ninguém pediu
        // para tirá-la.
        //
        // Não há recursão infinita: este ramo chama `definirFiltro(TODAS)`, que só limpa a tag
        // quando o filtro é SEM_TAG; e `definirFiltro(SEM_TAG)` chama este com `null`, que para no
        // `id != null`.
        if (id != null && filtro.value == FiltroLedger.SEM_TAG) definirFiltro(FiltroLedger.TODAS)
        tagFiltroId.value = id
        savedState[KEY_TAG] = id
    }

    fun abrirBusca() {
        if (_busca.value == null) {
            _busca.value = ""
            savedState[KEY_BUSCA] = ""
        }
    }
    fun fecharBusca() {
        _busca.value = null
        savedState[KEY_BUSCA] = null
    }
    fun definirBusca(texto: String) {
        _busca.value = texto
        savedState[KEY_BUSCA] = texto
    }

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

    /**
     * Um toque na fileira de chips: a linha ganha a etiqueta e sai da fila.
     *
     * SUBSTITUI os vínculos, não acrescenta. Na fila toda linha tem zero etiquetas, então as duas
     * coisas dão o mesmo resultado — e substituir é o que o "desfazer" sabe reverter com uma lista
     * vazia, sem precisar carregar um snapshot do que havia antes.
     */
    fun etiquetar(movId: Long, tag: Tag) {
        viewModelScope.launch {
            try {
                repo.definirTags(movId, listOf(tag.id))
                _eventoEtiqueta.emit(EtiquetaAplicada(movId, tag.nome))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "etiquetar falhou", e)
            }
        }
    }

    /**
     * O "desfazer" do snackbar: a linha volta para a fila, sem etiqueta nenhuma.
     *
     * Sem ele, um toque errado tiraria a linha da única tela em que ela era fácil de achar — e a
     * fila existe justamente porque não havia caminho nenhum até essas linhas.
     */
    fun desfazerEtiqueta(movId: Long) {
        viewModelScope.launch {
            try {
                repo.definirTags(movId, emptyList())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "desfazer etiqueta falhou", e)
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
            initializer {
                LedgerViewModel(
                    container.repository,
                    createSavedStateHandle(),
                    container.settings.settings.map { it.metaGuardarPercent }.distinctUntilChanged(),
                )
            }
        }
    }
}
