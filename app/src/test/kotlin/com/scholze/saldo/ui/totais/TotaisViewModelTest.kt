package com.scholze.saldo.ui.totais

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
class TotaisViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val input = LedgerInput(
        saldoInicialCentavos = 0, saldoInicialData = LocalDate.parse("2026-01-01"),
        movimentacoes = emptyList(), recorrencias = emptyList(), mesesMaterializados = emptySet(),
        cartao = CartaoConfig(), hoje = LocalDate.parse("2026-09-07"),
    )

    @Before fun setMain() = Dispatchers.setMain(dispatcher)
    @After fun resetMainDispatcher() = Dispatchers.resetMain()

    @Test
    fun oMesVistoSobreviveNoSavedState() = runTest(dispatcher) {
        val saved = SavedStateHandle()
        val vm = TotaisViewModel(RepositorioFixo(input), saved)
        vm.irPara(YearMonth.of(2026, 12))
        advanceUntilIdle()
        val outro = TotaisViewModel(RepositorioFixo(input), saved)
        assertEquals(YearMonth.of(2026, 12), outro.mesAtualAgora)
    }

    @Test
    fun oSegmentoSobreviveNoSavedState() {
        val saved = SavedStateHandle()
        val vm = TotaisViewModel(RepositorioFixo(input), saved)
        vm.selecionarSegmento(SegmentoTotais.TENDENCIA)
        val outro = TotaisViewModel(RepositorioFixo(input), saved)
        assertEquals(SegmentoTotais.TENDENCIA, outro.segmento.value)
    }
}
