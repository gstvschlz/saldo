package com.scholze.saldo.ui.ledger

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.scholze.saldo.RepositorioFixo
import com.scholze.saldo.domain.CartaoConfig
import com.scholze.saldo.domain.FiltroLedger
import com.scholze.saldo.domain.LedgerInput
import com.scholze.saldo.domain.Movimentacao
import com.scholze.saldo.domain.Natureza
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
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
    private fun vm(saved: SavedStateHandle = SavedStateHandle()) =
        LedgerViewModel(RepositorioFixo(input), saved).also { criados += it }

    @Before fun setMain() = Dispatchers.setMain(dispatcher)

    @After fun resetMainDispatcher() {
        criados.forEach { it.viewModelScope.cancel() }
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
}
