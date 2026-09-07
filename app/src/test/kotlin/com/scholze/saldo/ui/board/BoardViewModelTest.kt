package com.scholze.saldo.ui.board

import androidx.lifecycle.SavedStateHandle
import com.scholze.saldo.RepositorioFixo
import com.scholze.saldo.domain.CartaoConfig
import com.scholze.saldo.domain.LedgerInput
import java.time.LocalDate
import java.time.YearMonth
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
class BoardViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val input = LedgerInput(
        saldoInicialCentavos = 0, saldoInicialData = LocalDate.parse("2026-01-01"),
        movimentacoes = emptyList(), recorrencias = emptyList(), mesesMaterializados = emptySet(),
        cartao = CartaoConfig(), hoje = LocalDate.parse("2026-09-07"),
    )

    @Before fun setMain() = Dispatchers.setMain(dispatcher)
    @After fun resetMainDispatcher() = Dispatchers.resetMain()

    @Test
    fun oMesEODiaAbertoSobrevivemNoSavedState() = runTest(dispatcher) {
        val saved = SavedStateHandle()
        val vm = BoardViewModel(RepositorioFixo(input), saved)
        vm.irPara(YearMonth.of(2026, 5), dia = 12)
        advanceUntilIdle()                                      // os coletores gravam no handle

        val outro = BoardViewModel(RepositorioFixo(input), saved)   // "processo novo", mesmo handle
        assertEquals(YearMonth.of(2026, 5), outro.mesAtualAgora)
        assertEquals(LocalDate.parse("2026-05-12"), outro.diaAbertoAgora)
    }

    @Test
    fun fecharODiaTambemSobrevive() = runTest(dispatcher) {
        val saved = SavedStateHandle()
        val vm = BoardViewModel(RepositorioFixo(input), saved)
        vm.alternarDia(LocalDate.now())                         // hoje estava aberto: fecha
        advanceUntilIdle()
        val outro = BoardViewModel(RepositorioFixo(input), saved)
        assertEquals(null, outro.diaAbertoAgora)
    }

    @Test
    fun semSavedStateAbreNoMesCorrenteComHojeAberto() {
        val vm = BoardViewModel(RepositorioFixo(input), SavedStateHandle())
        assertEquals(YearMonth.now(), vm.mesAtualAgora)
        assertEquals(LocalDate.now(), vm.diaAbertoAgora)
    }
}
