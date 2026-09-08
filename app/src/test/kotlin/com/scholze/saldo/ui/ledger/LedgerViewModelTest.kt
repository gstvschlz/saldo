package com.scholze.saldo.ui.ledger

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.scholze.saldo.RepositorioFixo
import com.scholze.saldo.data.SaldoRepository
import com.scholze.saldo.domain.CartaoConfig
import com.scholze.saldo.domain.FiltroLedger
import com.scholze.saldo.domain.LedgerInput
import com.scholze.saldo.domain.Movimentacao
import com.scholze.saldo.domain.Natureza
import com.scholze.saldo.ui.components.MENSAGEM_ERRO_LEITURA
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LedgerViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val hoje = LocalDate.parse("2026-09-07")
    private val input = LedgerInput(
        saldoInicialCentavos = 0, saldoInicialData = LocalDate.parse("2026-01-01"),
        movimentacoes = listOf(
            Movimentacao(id = 1, descricao = "uber", valorCentavos = -23_90, data = LocalDate.parse("2026-09-01"), natureza = Natureza.DIARIO),
            Movimentacao(id = 2, descricao = "uber", valorCentavos = -31_00, data = LocalDate.parse("2026-04-03"), natureza = Natureza.DIARIO),
        ),
        recorrencias = emptyList(), mesesMaterializados = emptySet(), cartao = CartaoConfig(), hoje = hoje,
    )

    // `state` e `resultados` são StateFlow em `viewModelScope` (Task 9); um teste que os
    // coleta (`.first { }`) mantém aquele `WhileSubscribed` vivo além do teste — em produção
    // `onCleared()` cancela `viewModelScope`, aqui ninguém chama isso. Sem cancelar, ele
    // sobrevive ao fim do teste e tenta retomar depois que `resetMain()` já invalidou o
    // dispatcher, e a exceção assíncrona é atribuída ao próximo teste da suíte inteira
    // (`UncaughtExceptionsBeforeTest`) — não a este arquivo.
    private val criados = mutableListOf<LedgerViewModel>()
    private fun vm(saved: SavedStateHandle = SavedStateHandle()) = vmCom(RepositorioFixo(input), saved)

    private fun vmCom(repo: SaldoRepository, saved: SavedStateHandle = SavedStateHandle()) =
        LedgerViewModel(repo, saved).also { criados += it }

    @Before fun setMain() = Dispatchers.setMain(dispatcher)

    @After fun resetMainDispatcher() {
        // `cancel()` só INICIA o cancelamento; drenar o scheduler antes do
        // `resetMain()` deixa as corrotinas terminarem de morrer enquanto o Main
        // ainda existe. Sem isto a exceção assíncrona cai num teste de outra classe.
        criados.forEach { it.viewModelScope.cancel() }
        dispatcher.scheduler.advanceUntilIdle()
        Dispatchers.resetMain()
    }

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
    fun mesFiltroTagEBuscaSobrevivemNoSavedState() {
        val saved = SavedStateHandle()
        val vm = vm(saved)
        vm.irPara(YearMonth.of(2026, 4))
        vm.definirFiltro(FiltroLedger.FIXAS)
        vm.definirTagFiltroId(9L)
        vm.abrirBusca()
        vm.definirBusca("ub")

        val outro = vm(saved)
        assertEquals(YearMonth.of(2026, 4), outro.mesAtualAgora)
        assertEquals(FiltroLedger.FIXAS, outro.filtroAgora)
        assertEquals(9L, outro.tagFiltroIdAgora)
        assertEquals("ub", outro.busca.value)
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
}
