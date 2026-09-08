package com.scholze.saldo.ui.entry

import androidx.lifecycle.viewModelScope
import com.scholze.saldo.RepositorioFixo
import com.scholze.saldo.data.SaldoRepository
import com.scholze.saldo.domain.CartaoConfig
import com.scholze.saldo.domain.EscopoEdicao
import com.scholze.saldo.domain.LedgerInput
import com.scholze.saldo.domain.Movimentacao
import com.scholze.saldo.domain.Natureza
import com.scholze.saldo.domain.RepetirOpcao
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EntryViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val input = LedgerInput(
        saldoInicialCentavos = 0, saldoInicialData = LocalDate.parse("2026-01-01"),
        movimentacoes = emptyList(), recorrencias = emptyList(), mesesMaterializados = emptySet(),
        cartao = CartaoConfig(), hoje = LocalDate.parse("2026-09-07"),
    )

    /** Anota qual escrita foi chamada. */
    private class RepoEspiao(input: LedgerInput) : SaldoRepository by RepositorioFixo(input) {
        val chamadas = mutableListOf<String>()
        override suspend fun editar(mov: Movimentacao, escopo: EscopoEdicao, diaDoMes: Int?) {
            chamadas += "editar:$escopo:$diaDoMes"
        }
        override suspend fun excluir(mov: Movimentacao): Movimentacao {
            chamadas += "excluir:${mov.valorCentavos}"
            return mov
        }
        override suspend fun converterEmRecorrencia(mov: Movimentacao, diaDoMes: Int) { chamadas += "converter:$diaDoMes" }
        override suspend fun encerrarRecorrencia(mov: Movimentacao, mesDaSerie: YearMonth) { chamadas += "encerrar:$mesDaSerie" }
        override suspend fun criar(mov: Movimentacao, repetir: RepetirOpcao) { chamadas += "criar" }
    }

    private val avulsa = Movimentacao(id = 5, descricao = "luz", valorCentavos = -120_00, data = LocalDate.parse("2026-09-10"), natureza = Natureza.DIARIO)
    private val instancia = avulsa.copy(id = 6, recorrenciaId = 3)

    @Before fun setMain() = Dispatchers.setMain(dispatcher)
    // Os ViewModels criados aqui lançam corrotinas no `viewModelScope` (salvar, excluir,
    // sincronizar). Em produção `onCleared()` cancela o escopo; no teste ninguém chama, e
    // uma corrotina que retoma depois do `resetMain()` estoura num teste de OUTRA classe.
    // `cancel()` só inicia o cancelamento, por isso o scheduler é drenado antes do reset.
    private val criados = mutableListOf<EntryViewModel>()

    @After fun resetMainDispatcher() {
        criados.forEach { it.viewModelScope.cancel() }
        dispatcher.scheduler.advanceUntilIdle()
        Dispatchers.resetMain()
    }

    @Test
    fun avulsaQueViraMensalConverte() = runTest(dispatcher) {
        val repo = RepoEspiao(input)
        val vm = EntryViewModel(repo).also { criados += it }
        vm.iniciarEdicao(avulsa)
        vm.definirRepetir(RepetirOpcao.TodoMes(10))
        vm.salvar(EscopoEdicao.SO_ESTE_MES) {}
        advanceUntilIdle()
        // Uma única transação: `converterEmRecorrencia` grava os campos da linha sozinha, não
        // há mais um `editar` separado antes dela.
        assertEquals(listOf("converter:10"), repo.chamadas)
    }

    @Test
    fun mensalQueParaEncerra() = runTest(dispatcher) {
        val repo = RepoEspiao(input)
        val vm = EntryViewModel(repo).also { criados += it }
        vm.iniciarEdicao(instancia, diaDoTemplate = 10)
        vm.definirRepetir(RepetirOpcao.Nao)
        vm.salvar(EscopoEdicao.SO_ESTE_MES) {}
        advanceUntilIdle()
        // Idem: uma única transação, e o mês da série é o de `instancia.data` (2026-09-10).
        assertEquals(listOf("encerrar:2026-09"), repo.chamadas)
    }

    /**
     * O usuário moveu a data da instância no mesmo formulário em que desligou "repetir": o mês
     * da série gravado tem de continuar sendo o de ORIGEM (setembro, o snapshot tirado ao abrir
     * a edição), não o de destino (dezembro, o que ficou no campo "data").
     */
    @Test
    fun mensalQueParaComADataMovidaEncerraNoMesOriginal() = runTest(dispatcher) {
        val repo = RepoEspiao(input)
        val vm = EntryViewModel(repo).also { criados += it }
        vm.iniciarEdicao(instancia, diaDoTemplate = 10) // instancia.data = 2026-09-10
        vm.definirData(LocalDate.parse("2026-12-25"))
        vm.definirRepetir(RepetirOpcao.Nao)
        vm.salvar(EscopoEdicao.SO_ESTE_MES) {}
        advanceUntilIdle()
        assertEquals(listOf("encerrar:2026-09"), repo.chamadas)
    }

    @Test
    fun mensalQueContinuaMensalSoEdita() = runTest(dispatcher) {
        val repo = RepoEspiao(input)
        val vm = EntryViewModel(repo).also { criados += it }
        vm.iniciarEdicao(instancia, diaDoTemplate = 10)
        vm.definirCentavos(130_00)
        vm.salvar(EscopoEdicao.DAQUI_EM_DIANTE) {}
        advanceUntilIdle()
        // O dia que vai junto é o do template (10), não o da data da linha.
        assertEquals(listOf("editar:DAQUI_EM_DIANTE:10"), repo.chamadas)
    }

    /**
     * Mudar só o dia do "todo mês" (continua `TodoMes`, nos dois lados) não é conversão nem
     * encerramento — é uma edição normal do template, e o dia novo do formulário é que manda.
     */
    @Test
    fun trocarODiaDoTodoMesRoteiaParaEditar() = runTest(dispatcher) {
        val repo = RepoEspiao(input)
        val vm = EntryViewModel(repo).also { criados += it }
        vm.iniciarEdicao(instancia, diaDoTemplate = 10)
        vm.definirRepetir(RepetirOpcao.TodoMes(15))
        vm.salvar(EscopoEdicao.DAQUI_EM_DIANTE) {}
        advanceUntilIdle()
        assertEquals(listOf("editar:DAQUI_EM_DIANTE:15"), repo.chamadas)
    }

    /**
     * A instância de fevereiro de uma série de dia 31 cai no dia 28 (clamp). Salvar "daqui em
     * diante" sem mexer em nada tem de mandar 31 — o dia do template, que o formulário guarda —
     * e não os 28 da data da linha, que achatariam a série para sempre.
     */
    @Test
    fun oDia31SobreviveASalvarAInstanciaDeFevereiro() = runTest(dispatcher) {
        val repo = RepoEspiao(input)
        val vm = EntryViewModel(repo).also { criados += it }
        vm.iniciarEdicao(instancia.copy(data = LocalDate.parse("2026-02-28")), diaDoTemplate = 31)
        vm.definirCentavos(130_00)
        vm.salvar(EscopoEdicao.DAQUI_EM_DIANTE) {}
        advanceUntilIdle()
        assertEquals(listOf("editar:DAQUI_EM_DIANTE:31"), repo.chamadas)
    }

    /**
     * Mexer no valor e, sem sair da sheet, excluir: o que vai para o "desfazer" é a linha COMO
     * ESTAVA no ledger, não o rascunho do formulário — senão o desfazer reinsere um valor que
     * nunca existiu.
     */
    @Test
    fun excluirMandaALinhaOriginalNaoORascunhoDoFormulario() = runTest(dispatcher) {
        val repo = RepoEspiao(input)
        val vm = EntryViewModel(repo).also { criados += it }
        vm.iniciarEdicao(avulsa)                                // ledger: -120_00
        vm.definirCentavos(999_00)                              // rascunho: -999_00
        val excluidas = mutableListOf<Movimentacao>()
        val coleta = launch { vm.exclusoes.collect { excluidas += it } }
        advanceUntilIdle()                                      // o coletor assina antes da emissão

        vm.excluir {}
        advanceUntilIdle()
        coleta.cancel()

        assertEquals(listOf("excluir:-12000"), repo.chamadas)
        assertEquals(listOf(-120_00L), excluidas.map { it.valorCentavos })
        assertEquals(listOf(avulsa.id), excluidas.map { it.id })
    }

    @Test
    fun oDiaMostradoEODoTemplateNaoODaData() {
        val vm = EntryViewModel(RepoEspiao(input)).also { criados += it }
        vm.iniciarEdicao(instancia.copy(data = LocalDate.parse("2026-02-28")), diaDoTemplate = 31)
        assertEquals(RepetirOpcao.TodoMes(31), vm.formAgora.repetir)
        assertEquals(RepetirOpcao.TodoMes(31), vm.formAgora.repetirOriginal)
    }

    @Test
    fun soPedeEscopoQuandoContinuaMensal() {
        val vm = EntryViewModel(RepoEspiao(input)).also { criados += it }
        vm.iniciarEdicao(instancia, diaDoTemplate = 10)
        assertEquals(true, vm.formAgora.precisaEscopo)
        vm.definirRepetir(RepetirOpcao.Nao)
        assertEquals(false, vm.formAgora.precisaEscopo)
    }
}
