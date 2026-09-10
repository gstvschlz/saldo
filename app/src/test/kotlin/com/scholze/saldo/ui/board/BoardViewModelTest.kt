package com.scholze.saldo.ui.board

import androidx.lifecycle.viewModelScope
import androidx.lifecycle.SavedStateHandle
import com.scholze.saldo.RepositorioFixo
import com.scholze.saldo.domain.CartaoConfig
import com.scholze.saldo.domain.LedgerInput
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BoardViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val input = LedgerInput(
        saldoInicialCentavos = 0, saldoInicialData = LocalDate.parse("2026-01-01"),
        movimentacoes = emptyList(), recorrencias = emptyList(), mesesMaterializados = emptySet(),
        cartao = CartaoConfig(), hoje = LocalDate.parse("2026-09-07"),
    )

    @Before fun setMain() = Dispatchers.setMain(dispatcher)
    // Os ViewModels criados aqui lançam corrotinas no `viewModelScope` (salvar, excluir,
    // sincronizar). Em produção `onCleared()` cancela o escopo; no teste ninguém chama, e
    // uma corrotina que retoma depois do `resetMain()` estoura num teste de OUTRA classe.
    // `cancel()` só inicia o cancelamento, por isso o scheduler é drenado antes do reset.
    private val criados = mutableListOf<BoardViewModel>()

    @After fun resetMainDispatcher() {
        criados.forEach { it.viewModelScope.cancel() }
        dispatcher.scheduler.advanceUntilIdle()
        Dispatchers.resetMain()
    }

    @Test
    fun oMesEODiaAbertoSobrevivemNoSavedState() {
        val saved = SavedStateHandle()
        val vm = BoardViewModel(RepositorioFixo(input), saved, calculo = dispatcher).also { criados += it }
        vm.irPara(YearMonth.of(2026, 5), dia = 12)              // grava direto no handle (Task 9)

        val outro = BoardViewModel(RepositorioFixo(input), saved, calculo = dispatcher).also { criados += it }   // "processo novo", mesmo handle
        assertEquals(YearMonth.of(2026, 5), outro.mesAtualAgora)
        assertEquals(LocalDate.parse("2026-05-12"), outro.diaAbertoAgora)
    }

    @Test
    fun fecharODiaTambemSobrevive() {
        val saved = SavedStateHandle()
        val vm = BoardViewModel(RepositorioFixo(input), saved, calculo = dispatcher).also { criados += it }
        vm.alternarDia(LocalDate.now())                         // hoje estava aberto: fecha
        val outro = BoardViewModel(RepositorioFixo(input), saved, calculo = dispatcher).also { criados += it }
        assertEquals(null, outro.diaAbertoAgora)
    }

    @Test
    fun semSavedStateAbreNoMesCorrenteComHojeAberto() {
        val vm = BoardViewModel(RepositorioFixo(input), SavedStateHandle(), calculo = dispatcher).also { criados += it }
        assertEquals(YearMonth.now(), vm.mesAtualAgora)
        assertEquals(LocalDate.now(), vm.diaAbertoAgora)
    }
}
