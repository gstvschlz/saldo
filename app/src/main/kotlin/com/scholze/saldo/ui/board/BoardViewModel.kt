package com.scholze.saldo.ui.board

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
import com.scholze.saldo.domain.Board
import com.scholze.saldo.domain.BoardEngine
import com.scholze.saldo.domain.Busca
import com.scholze.saldo.domain.DiaRow
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
import kotlinx.coroutines.CoroutineDispatcher
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

/**
 * [board] e [mes] são `null` só enquanto o primeiro `LedgerInput` não chegou do banco.
 *
 * [mes] é o mês inteiro, e existe por dois motivos: o hero mostra o saldo projetado do mês —
 * que não muda com a etiqueta escolhida —, e é dele que sai o painel do dia, sem uma segunda
 * projeção.
 *
 * [diaAberto] é o dia cujos lançamentos aparecem embaixo da grade. `null` = nenhum, e aí o
 * rodapé mostra a régua do dia típico.
 */
data class BoardUiState(
    val board: Board?,
    val mes: MesLedger?,
    val hoje: LocalDate,
    val mesAtual: YearMonth = YearMonth.now(),
    val diaAberto: LocalDate? = null,
    /** Mensagem de falha de leitura; `null` = está tudo bem. Ver `fluxoComErro`. */
    val erro: String? = null,
    /**
     * O teto do dia; `null` fora do mês corrente e num mês sem entrada nenhuma. O hero some com
     * a linha nos dois casos — "hoje" não existe em setembro visto de outubro, e sem renda não há
     * o que dividir (ver [com.scholze.saldo.domain.TetoEngine.teto]).
     */
    val teto: Teto? = null,
    /**
     * A etiqueta que a grade está mostrando; `null` = o mês inteiro. É onde a aba `tags` e o
     * "ver tag" de totais aterrissam desde que a vista de lista deixou de existir.
     */
    val tagFiltro: Tag? = null,
    /** As etiquetas oferecidas na fileira de um lançamento sem tag. */
    val tagsSugeridas: List<Tag> = emptyList(),
    /**
     * Os templates de recorrência, para a sheet saber o dia do TEMPLATE ao editar uma ocorrência
     * — a data da linha pode estar clamped (31 vira 28 em fevereiro).
     */
    val recorrencias: List<Recorrencia> = emptyList(),
) {
    /** A linha do dia aberto, com os lançamentos dele; `null` quando não há dia aberto. */
    val linhaDoDia: DiaRow?
        get() {
            val d = diaAberto ?: return null
            if (mes == null || YearMonth.from(d) != mes.mes) return null
            return mes.dias.getOrNull(d.dayOfMonth - 1)
        }

    /** A seta `›` para no mês corrente: um mês futuro seria uma grade de "ainda não aconteceu". */
    val podeAvancar: Boolean get() = mesAtual < YearMonth.from(hoje)
}

/** O que o snackbar de "desfazer" precisa saber depois de um toque na fileira de etiquetas. */
data class EtiquetaAplicada(val movId: Long, val tagNome: String)

/**
 * O ViewModel da aba `saldos` — e o único dela.
 *
 * Até 2026-09-10 a aba tinha duas telas (a grade e uma lista) e dois ViewModels, cada um com o
 * seu mês, sincronizados à mão a cada saída da lista. A lista saiu a pedido do usuário e a busca
 * e o filtro de etiqueta vieram para cá; um mês só, num lugar só, é o que sobrou — e é o que
 * torna impossível a classe de bug que a `sincronizarMes` existia para remendar.
 */
class BoardViewModel(
    private val repo: SaldoRepository,
    private val savedState: SavedStateHandle = SavedStateHandle(),
    /**
     * A meta de guardar, que é a reserva mínima do teto do dia. Um `Flow<Int>` e não o
     * `SettingsStore` inteiro, como em `TotaisViewModel`: assinar o `Settings` completo faria
     * toda troca de tema recalcular o board inteiro.
     */
    private val metaGuardar: Flow<Int> = flowOf(0),
    /**
     * Onde a conta pesada roda. `Default` em produção; o teste passa o seu.
     *
     * Fixar `Dispatchers.Default` aqui dentro deixava um pedaço da cadeia fora do alcance do
     * `TestDispatcher`: `dispatcher.scheduler.advanceUntilIdle()` drena o dispatcher de teste e
     * NADA mais, então uma corrotina deste `flowOn` podia retomar depois do `resetMain()` e ter a
     * exceção cobrada de outro teste, de outra classe. Era a flakiness que a `dados-1` já
     * documentava e que o merge da v0.7.0 fez reaparecer.
     */
    private val calculo: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {

    // Mês, dia aberto, etiqueta e busca no SavedStateHandle: sobrevivem à morte do processo, e
    // não só à rotação. `YearMonth`/`LocalDate` como Long (o handle só aceita o que vai num
    // Bundle). Sem a etiqueta aqui, voltar ao app depois de um tempo mostrava uma grade filtrada
    // sem o chip que explica o filtro — e sem o × para sair dele.
    private val mesAtual = MutableStateFlow(savedState.get<Long>(KEY_MES)?.toYearMonth() ?: YearMonth.now())

    // O app abre respondendo "o que eu gastei hoje": no mês corrente, hoje já vem aberto.
    // `contains` distingue "nunca gravado" (primeira abertura) de "gravado como nenhum".
    private val diaAberto = MutableStateFlow<LocalDate?>(
        if (savedState.contains(KEY_DIA)) savedState.get<Long>(KEY_DIA)?.let { LocalDate.ofEpochDay(it) }
        else LocalDate.now(),
    )

    // Guarda o id, não a Tag: renomear ou apagar a etiqueta na aba tags tem de chegar aqui, e um
    // snapshot da Tag deixaria o chip preso ao nome antigo (ou a grade presa a uma etiqueta que
    // já não existe, filtrando tudo para fora sem saída visível).
    private val tagFiltroId = MutableStateFlow<Long?>(savedState.get<Long>(KEY_TAG))

    /** O texto da busca; `null` = busca fechada, "" = aberta sem nada digitado. */
    private val _busca = MutableStateFlow<String?>(savedState.get<String>(KEY_BUSCA))
    val busca: StateFlow<String?> = _busca

    private val _eventoExclusao = MutableSharedFlow<Movimentacao>(extraBufferCapacity = 1)

    /** Snapshot da linha apagada, para o "desfazer" do snackbar. */
    val eventoExclusao: SharedFlow<Movimentacao> = _eventoExclusao

    private val _eventoEtiqueta = MutableSharedFlow<EtiquetaAplicada>(extraBufferCapacity = 1)

    /** Emitido a cada toque na fileira de chips; a shell mostra o snackbar com "desfazer". */
    val eventoEtiqueta: SharedFlow<EtiquetaAplicada> = _eventoEtiqueta

    /** Leituras síncronas: o `+` da barra lança no dia aberto, e os testes conferem o saved state. */
    val mesAtualAgora: YearMonth get() = mesAtual.value
    val diaAbertoAgora: LocalDate? get() = diaAberto.value
    val tagFiltroIdAgora: Long? get() = tagFiltroId.value

    /** Os controles da grade, combinados uma vez: o `combine` de vários fluxos perde os tipos. */
    private data class Controles(val mes: YearMonth, val dia: LocalDate?, val tagId: Long?)

    private val controles = combine(mesAtual, diaAberto, tagFiltroId) { m, d, t -> Controles(m, d, t) }

    private val tentativas = MutableStateFlow(0)

    /** O botão "tentar de novo" da tela: reassina o fluxo do banco. */
    fun tentarDeNovo() {
        tentativas.value++
    }

    private val inicial = BoardUiState(
        board = null, mes = null, hoje = LocalDate.now(), diaAberto = LocalDate.now(),
    )

    val state: StateFlow<BoardUiState> = fluxoComErro(
        tentativas = tentativas,
        inicial = inicial,
        marcarErro = { it.copy(erro = MENSAGEM_ERRO_LEITURA) },
        limparErro = { it.copy(erro = null) },
        rotulo = "fluxo do board",
    ) {
        combine(repo.ledger, repo.tags, controles, metaGuardar) { input, tags, c, meta ->
            val tag = c.tagId?.let { id -> tags.firstOrNull { it.id == id } }
            BoardUiState(
                board = BoardEngine.board(input, c.mes, tagId = tag?.id),
                mes = ProjectionEngine.mes(input, c.mes, FiltroLedger.TODAS, tagId = tag?.id),
                hoje = input.hoje,
                mesAtual = c.mes,
                diaAberto = c.dia,
                // O teto é sobre o mês inteiro, não sobre a etiqueta: ele responde "quanto o dia
                // comporta", e essa conta não muda porque a tela está filtrada.
                teto = if (c.mes == YearMonth.from(input.hoje)) TetoEngine.teto(input, meta) else null,
                tagFiltro = tag,
                tagsSugeridas = TagsSugeridas.paraFila(tags, input.movimentacoes, input.hoje),
                recorrencias = input.recorrencias,
            )
        }
            // O agrupamento por dia mais a projeção do mês: fora da main thread.
            .flowOn(calculo)
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), inicial)

    /**
     * Os resultados da busca, à parte do [state]: `state` combina mês, dia e etiqueta, que não
     * mudam a cada tecla — misturar a busca ali forçaria `ProjectionEngine.mes` (a projeção do
     * mês inteiro) e o board a rodarem de novo a cada caractere digitado. Aqui só as
     * movimentações efetivas são recalculadas, e só quando `repo.ledger` ou o texto mudam.
     */
    val resultados: StateFlow<List<Movimentacao>?> =
        combine(repo.ledger, _busca) { input, q ->
            q?.takeIf { it.isNotBlank() }?.let {
                Busca.filtrar(ProjectionEngine.movimentacoesAte(input, YearMonth.from(input.hoje)), it, input.hoje)
            }
        }
            .flowOn(calculo)
            // Continua sendo um `.catch` seco, ao contrário do [state]: uma busca que falha não
            // congela a tela (a grade continua lá, vindo do outro fluxo) e não há botão só dela
            // para reassinar — fechar e reabrir a busca já refaz a leitura.
            .catch { Log.e(TAG, "busca falhou", it) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        abrir(mesAtual.value)
    }

    fun mesAnterior() = irPara(mesAtual.value.minusMonths(1))

    /** Sem efeito no mês corrente: não há para onde avançar. */
    fun proximoMes() {
        val alvo = mesAtual.value.plusMonths(1)
        if (alvo <= YearMonth.now()) irPara(alvo)
    }

    /**
     * Navega para [mes]. Com [dia] — o caminho do deep link e do "ir para o dia" de totais —
     * aquele dia já chega aberto no painel; sem ele, abre hoje se o mês for o corrente.
     */
    fun irPara(mes: YearMonth, dia: Int? = null) {
        mesAtual.value = mes
        savedState[KEY_MES] = mes.toLongChave()
        diaAberto.value = when {
            dia != null -> mes.atDay(dia.coerceIn(1, mes.lengthOfMonth()))
            mes == YearMonth.now() -> LocalDate.now()
            else -> null
        }
        savedState[KEY_DIA] = diaAberto.value?.toEpochDay()
        abrir(mes)
    }

    /** Tocar no dia já aberto fecha o painel — é o mesmo toque desfazendo o que fez. */
    fun alternarDia(data: LocalDate) {
        diaAberto.value = if (diaAberto.value == data) null else data
        savedState[KEY_DIA] = diaAberto.value?.toEpochDay()
    }

    /**
     * Filtra a grade por uma etiqueta, ou tira o filtro com `null`.
     *
     * O mês NÃO muda junto: chegar numa etiqueta pela aba tags e cair em janeiro seria perder o
     * lugar. Quem quiser outro mês navega com as setas, agora já filtrado.
     */
    fun definirTagFiltro(tag: Tag?) = definirTagFiltroId(tag?.id)

    fun definirTagFiltroId(id: Long?) {
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

    /**
     * Materializa as recorrências do mês, como o ledger fazia ao trocar de mês. A projeção
     * já mostra as ocorrências virtuais sem isto; o que isto compra é poder editá-las.
     */
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

    companion object {
        private const val TAG = "saldo"
        private const val KEY_MES = "board.mes"
        private const val KEY_DIA = "board.dia"
        private const val KEY_TAG = "board.tag"
        private const val KEY_BUSCA = "board.busca"

        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                BoardViewModel(
                    container.repository,
                    createSavedStateHandle(),
                    container.settings.settings.map { it.metaGuardarPercent }.distinctUntilChanged(),
                )
            }
        }
    }
}
