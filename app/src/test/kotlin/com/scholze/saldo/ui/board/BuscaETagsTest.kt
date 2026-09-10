package com.scholze.saldo.ui.board

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.scholze.saldo.RepositorioFixo
import com.scholze.saldo.data.SaldoRepository
import com.scholze.saldo.domain.CartaoConfig
import com.scholze.saldo.domain.LedgerInput
import com.scholze.saldo.domain.Movimentacao
import com.scholze.saldo.domain.Natureza
import com.scholze.saldo.domain.Tag
import com.scholze.saldo.ui.components.MENSAGEM_ERRO_LEITURA
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * A busca, o filtro de etiqueta e o etiquetar — tudo o que veio da lista para o board quando ela
 * foi apagada, em 2026-09-10. O que ficou para trás foi a exclusividade entre o filtro `sem tag`
 * e o de etiqueta: sem chips de filtro na grade, aquele par proibido deixou de poder existir.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BuscaETagsTest {

    private val dispatcher = StandardTestDispatcher()
    private val hoje = LocalDate.parse("2026-09-07")
    private val comida = Tag(id = 5, nome = "comida", cor = 0xFFB63C62L)
    private val input = LedgerInput(
        saldoInicialCentavos = 0, saldoInicialData = LocalDate.parse("2026-01-01"),
        movimentacoes = listOf(
            Movimentacao(id = 1, descricao = "uber", valorCentavos = -23_90, data = LocalDate.parse("2026-09-01"), natureza = Natureza.DIARIO),
            Movimentacao(id = 2, descricao = "uber", valorCentavos = -31_00, data = LocalDate.parse("2026-04-03"), natureza = Natureza.DIARIO),
        ),
        recorrencias = emptyList(), mesesMaterializados = emptySet(), cartao = CartaoConfig(), hoje = hoje,
    )

    // `state` e `resultados` são StateFlow em `viewModelScope`; um teste que os coleta
    // (`.first { }`) mantém aquele `WhileSubscribed` vivo além do teste — em produção
    // `onCleared()` cancela `viewModelScope`, aqui ninguém chama isso. Sem cancelar, ele
    // sobrevive ao fim do teste e tenta retomar depois que `resetMain()` já invalidou o
    // dispatcher, e a exceção assíncrona é atribuída ao próximo teste da suíte inteira
    // (`UncaughtExceptionsBeforeTest`) — não a este arquivo.
    private val criados = mutableListOf<BoardViewModel>()

    private fun vm(saved: SavedStateHandle = SavedStateHandle()) = vmCom(RepositorioFixo(input), saved)

    private fun vmCom(repo: SaldoRepository, saved: SavedStateHandle = SavedStateHandle()) =
        BoardViewModel(repo, saved).also { criados += it }

    @Before fun setMain() = Dispatchers.setMain(dispatcher)

    @After fun resetMainDispatcher() {
        // `cancel()` só INICIA o cancelamento; drenar o scheduler antes do `resetMain()` deixa
        // as corrotinas terminarem de morrer enquanto o Main ainda existe.
        criados.forEach { it.viewModelScope.cancel() }
        dispatcher.scheduler.advanceUntilIdle()
        Dispatchers.resetMain()
    }

    // ---- a busca, agora na grade ----

    @Test
    fun aBuscaAchaEmTodosOsMeses() = runTest(dispatcher) {
        val vm = vm()
        vm.abrirBusca()
        vm.definirBusca("uber")
        val resultados = vm.resultados.first { it != null }
        assertEquals(listOf(1L, 2L), resultados!!.map { it.id })
    }

    @Test
    fun buscaFechadaNaoTemResultados() = runTest(dispatcher) {
        val vm = vm()
        assertNull(vm.resultados.value)
    }

    @Test
    fun mesTagEBuscaSobrevivemNoSavedState() {
        val saved = SavedStateHandle()
        val vm = vm(saved)
        vm.irPara(YearMonth.of(2026, 4))
        vm.definirTagFiltroId(9L)
        vm.abrirBusca()
        vm.definirBusca("ub")

        val outro = vm(saved)
        assertEquals(YearMonth.of(2026, 4), outro.mesAtualAgora)
        assertEquals(9L, outro.tagFiltroIdAgora)
        assertEquals("ub", outro.busca.value)
    }

    // ---- o filtro de etiqueta na grade ----

    /** As duas linhas de setembro; só uma carrega a etiqueta. */
    private val comUmaEtiquetada = input.copy(
        movimentacoes = listOf(
            Movimentacao(id = 1, descricao = "mercado", valorCentavos = -50_00, data = LocalDate.parse("2026-09-01"), natureza = Natureza.DIARIO, tags = listOf(comida)),
            Movimentacao(id = 3, descricao = "gasolina", valorCentavos = -80_00, data = LocalDate.parse("2026-09-02"), natureza = Natureza.DIARIO),
            Movimentacao(id = 4, descricao = "salário", valorCentavos = 500_00, data = LocalDate.parse("2026-09-05"), natureza = Natureza.DIARIO),
        ),
    )

    @Test
    fun aEtiquetaFiltraAGradeEOPainelDoDia() = runTest(dispatcher) {
        val vm = vmCom(RepositorioFixo(comUmaEtiquetada, listOf(comida)))
        vm.definirTagFiltro(comida)
        val estado = vm.state.first { it.mes != null && it.tagFiltro != null }

        // A célula do dia 2 (gasolina, sem etiqueta) fica zerada; a do dia 1 mantém o valor.
        val porDia = estado.board!!.dias.associate { it.data to it.valorCentavos }
        assertEquals(-50_00L, porDia[LocalDate.parse("2026-09-01")])
        assertEquals(0L, porDia[LocalDate.parse("2026-09-02")])

        // E o painel do dia 2 não tem lançamento nenhum para mostrar.
        val dia2 = estado.mes!!.dias[1]
        assertEquals(emptyList<Long>(), dia2.itens.map { it.valorCentavos })
    }

    /**
     * O hero responde "quanto vai sobrar no mês", e essa conta não muda porque a tela está
     * filtrada — senão o número do topo mentiria a cada toque numa etiqueta.
     */
    @Test
    fun oSaldoProjetadoNaoMudaComAEtiqueta() = runTest(dispatcher) {
        val semFiltro = vmCom(RepositorioFixo(comUmaEtiquetada, listOf(comida)))
        val antes = semFiltro.state.first { it.mes != null }.mes!!.saldoProjetadoCentavos

        val comFiltro = vmCom(RepositorioFixo(comUmaEtiquetada, listOf(comida)))
        comFiltro.definirTagFiltro(comida)
        val depois = comFiltro.state.first { it.mes != null && it.tagFiltro != null }.mes!!.saldoProjetadoCentavos

        assertEquals(antes, depois)
    }

    /** Apagar a etiqueta na aba tags não pode deixar a grade presa a um id que já não existe. */
    @Test
    fun etiquetaInexistenteNaoFiltraNada() = runTest(dispatcher) {
        val vm = vmCom(RepositorioFixo(comUmaEtiquetada, listOf(comida)))
        vm.definirTagFiltroId(999L)
        val estado = vm.state.first { it.mes != null }
        assertNull(estado.tagFiltro)
        assertTrue(estado.mes!!.dias.any { it.itens.isNotEmpty() })
    }

    // ---- erro de leitura e tentar de novo (dados-1) ----

    /** Falha na PRIMEIRA assinatura e responde nas seguintes: é como se prova que o botão reassina. */
    private class RepositorioQueFalhaUmaVez(private val input: LedgerInput) : SaldoRepository by RepositorioFixo(input) {
        var assinaturas = 0
            private set

        override val ledger: Flow<LedgerInput> = flow {
            assinaturas++
            if (assinaturas == 1) throw IllegalStateException("disco ruim")
            emit(input)
        }
    }

    @Test
    fun umaFalhaDeLeituraViraErroNoEstado() = runTest(dispatcher) {
        val vm = vmCom(RepositorioQueFalhaUmaVez(input))
        assertEquals(MENSAGEM_ERRO_LEITURA, vm.state.first { it.erro != null }.erro)
    }

    @Test
    fun tentarDeNovoReassinaOFluxo() = runTest(dispatcher) {
        val repo = RepositorioQueFalhaUmaVez(input)
        val vm = vmCom(repo)
        val coleta = backgroundScope.launch { vm.state.collect { } }   // mantém o WhileSubscribed vivo
        vm.state.first { it.erro != null }

        vm.tentarDeNovo()

        // Não basta a mensagem sumir: o dado tem de CHEGAR, e só a segunda assinatura o traz.
        val depois = vm.state.first { it.erro == null && it.mes != null }
        assertNull(depois.erro)
        assertEquals(2, repo.assinaturas)
        coleta.cancel()
    }

    // ---- etiquetar pela fileira (arrumacao-1) ----

    /** Anota o que `definirTags` recebeu; todo o resto vem do repositório fixo. */
    private class RepositorioQueAnotaTags(input: LedgerInput, tags: List<Tag>) :
        SaldoRepository by RepositorioFixo(input, tags) {
        val chamadas = mutableListOf<Pair<Long, List<Long>>>()
        override suspend fun definirTags(movId: Long, tagIds: List<Long>) {
            chamadas += movId to tagIds
        }
    }

    @Test
    fun etiquetarGravaAListaCertaEAvisaOSnackbar() = runTest(dispatcher) {
        val repo = RepositorioQueAnotaTags(input, listOf(comida))
        val vm = vmCom(repo)
        // O fluxo não tem replay: quem não está inscrito na hora do emit perde o evento. Um
        // `async` que já suspendeu no `first()` é a inscrição mais barata que não depende de
        // ordem de fila — `runCurrent` só precisa levá-lo até lá.
        val evento = async { vm.eventoEtiqueta.first() }
        runCurrent()

        vm.etiquetar(1L, comida)
        advanceUntilIdle()

        assertEquals(listOf(1L to listOf(5L)), repo.chamadas)
        assertEquals(EtiquetaAplicada(1L, "comida"), evento.await())
    }

    @Test
    fun oDesfazerDevolveAListaVazia() = runTest(dispatcher) {
        val repo = RepositorioQueAnotaTags(input, listOf(comida))
        val vm = vmCom(repo)
        vm.desfazerEtiqueta(1L)
        advanceUntilIdle()
        assertEquals(listOf(1L to emptyList<Long>()), repo.chamadas)
    }

    /** A fileira de chips sai daqui: as mais usadas nos 90 dias primeiro. */
    @Test
    fun oEstadoTrazAsEtiquetasSugeridas() = runTest(dispatcher) {
        val aluguel = Tag(id = 6, nome = "aluguel", cor = 0xFF9A5A00L)
        // Só a linha de 01/09 está dentro dos 90 dias de 07/09; a de abril não conta.
        val comUso = input.copy(movimentacoes = input.movimentacoes.map { it.copy(tags = listOf(comida)) })
        val vm = vmCom(RepositorioFixo(comUso, listOf(aluguel, comida)))
        val estado = vm.state.first { it.mes != null }
        assertEquals(listOf("comida", "aluguel"), estado.tagsSugeridas.map { it.nome })
    }
}
