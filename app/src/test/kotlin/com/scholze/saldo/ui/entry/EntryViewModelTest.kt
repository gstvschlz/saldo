package com.scholze.saldo.ui.entry

import com.scholze.saldo.RepositorioFixo
import com.scholze.saldo.data.SaldoRepository
import com.scholze.saldo.domain.CartaoConfig
import com.scholze.saldo.domain.EscopoEdicao
import com.scholze.saldo.domain.LedgerInput
import com.scholze.saldo.domain.Movimentacao
import com.scholze.saldo.domain.Natureza
import com.scholze.saldo.domain.RepetirOpcao
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
        override suspend fun editar(mov: Movimentacao, escopo: EscopoEdicao) { chamadas += "editar:$escopo" }
        override suspend fun converterEmRecorrencia(mov: Movimentacao, diaDoMes: Int) { chamadas += "converter:$diaDoMes" }
        override suspend fun encerrarRecorrencia(mov: Movimentacao) { chamadas += "encerrar" }
        override suspend fun criar(mov: Movimentacao, repetir: RepetirOpcao) { chamadas += "criar" }
    }

    private val avulsa = Movimentacao(id = 5, descricao = "luz", valorCentavos = -120_00, data = LocalDate.parse("2026-09-10"), natureza = Natureza.DIARIO)
    private val instancia = avulsa.copy(id = 6, recorrenciaId = 3)

    @Before fun setMain() = Dispatchers.setMain(dispatcher)
    @After fun resetMainDispatcher() = Dispatchers.resetMain()

    @Test
    fun avulsaQueViraMensalConverte() = runTest(dispatcher) {
        val repo = RepoEspiao(input)
        val vm = EntryViewModel(repo)
        vm.iniciarEdicao(avulsa)
        vm.definirRepetir(RepetirOpcao.TodoMes(10))
        vm.salvar(EscopoEdicao.SO_ESTE_MES) {}
        advanceUntilIdle()
        assertEquals(listOf("editar:SO_ESTE_MES", "converter:10"), repo.chamadas)
    }

    @Test
    fun mensalQueParaEncerra() = runTest(dispatcher) {
        val repo = RepoEspiao(input)
        val vm = EntryViewModel(repo)
        vm.iniciarEdicao(instancia, diaDoTemplate = 10)
        vm.definirRepetir(RepetirOpcao.Nao)
        vm.salvar(EscopoEdicao.SO_ESTE_MES) {}
        advanceUntilIdle()
        assertEquals(listOf("editar:SO_ESTE_MES", "encerrar"), repo.chamadas)
    }

    @Test
    fun mensalQueContinuaMensalSoEdita() = runTest(dispatcher) {
        val repo = RepoEspiao(input)
        val vm = EntryViewModel(repo)
        vm.iniciarEdicao(instancia, diaDoTemplate = 10)
        vm.definirCentavos(130_00)
        vm.salvar(EscopoEdicao.DAQUI_EM_DIANTE) {}
        advanceUntilIdle()
        assertEquals(listOf("editar:DAQUI_EM_DIANTE"), repo.chamadas)
    }

    @Test
    fun oDiaMostradoEODoTemplateNaoODaData() {
        val vm = EntryViewModel(RepoEspiao(input))
        vm.iniciarEdicao(instancia.copy(data = LocalDate.parse("2026-02-28")), diaDoTemplate = 31)
        assertEquals(RepetirOpcao.TodoMes(31), vm.formAgora.repetir)
        assertEquals(RepetirOpcao.TodoMes(31), vm.formAgora.repetirOriginal)
    }

    @Test
    fun soPedeEscopoQuandoContinuaMensal() {
        val vm = EntryViewModel(RepoEspiao(input))
        vm.iniciarEdicao(instancia, diaDoTemplate = 10)
        assertEquals(true, vm.formAgora.precisaEscopo)
        vm.definirRepetir(RepetirOpcao.Nao)
        assertEquals(false, vm.formAgora.precisaEscopo)
    }
}
