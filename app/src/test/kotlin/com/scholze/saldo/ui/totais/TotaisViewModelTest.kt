package com.scholze.saldo.ui.totais

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.scholze.saldo.RepositorioFixo
import com.scholze.saldo.domain.CartaoConfig
import com.scholze.saldo.domain.LedgerInput
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
    // Um teste que COLETA o `state` mantém o `WhileSubscribed` do `viewModelScope` vivo além
    // do fim do teste: em produção `onCleared()` o cancela, aqui ninguém chama. Sem cancelar à
    // mão, ele tenta retomar depois que `resetMain()` invalidou o dispatcher, e a exceção
    // assíncrona é atribuída ao PRÓXIMO teste da suíte. Mesmo cuidado do LedgerViewModelTest.
    private val criados = mutableListOf<TotaisViewModel>()

    @After fun resetMainDispatcher() {
        // `cancel()` só INICIA o cancelamento; drenar o scheduler antes do
        // `resetMain()` deixa as corrotinas terminarem de morrer enquanto o Main
        // ainda existe. Sem isto a exceção assíncrona cai num teste de outra classe.
        criados.forEach { it.viewModelScope.cancel() }
        dispatcher.scheduler.advanceUntilIdle()
        Dispatchers.resetMain()
    }

    @Test
    fun oMesVistoSobreviveNoSavedState() = runTest(dispatcher) {
        val saved = SavedStateHandle()
        val vm = TotaisViewModel(RepositorioFixo(input), saved, calculo = dispatcher).also { criados += it }
        vm.irPara(YearMonth.of(2026, 12))
        advanceUntilIdle()
        val outro = TotaisViewModel(RepositorioFixo(input), saved, calculo = dispatcher).also { criados += it }
        assertEquals(YearMonth.of(2026, 12), outro.mesAtualAgora)
    }

    @Test
    fun oSegmentoSobreviveNoSavedState() {
        val saved = SavedStateHandle()
        val vm = TotaisViewModel(RepositorioFixo(input), saved, calculo = dispatcher).also { criados += it }
        vm.selecionarSegmento(SegmentoTotais.TENDENCIA)
        val outro = TotaisViewModel(RepositorioFixo(input), saved, calculo = dispatcher).also { criados += it }
        assertEquals(SegmentoTotais.TENDENCIA, outro.segmento.value)
    }

    // ---- só o segmento aberto (dados-1) ----

    @Test
    fun oSegmentoMesNaoCalculaTendenciaNemACaminho() = runTest(dispatcher) {
        val vm = TotaisViewModel(RepositorioFixo(input), SavedStateHandle(), calculo = dispatcher).also { criados += it }
        val estado = vm.state.first { it.totais != null }
        assertNotNull(estado.insights)
        assertNotNull(estado.ritmo)
        assertNull(estado.tendencia)
        assertNull(estado.aCaminho)
    }

    @Test
    fun escolherTendenciaCalculaTendenciaESoEla() = runTest(dispatcher) {
        val vm = TotaisViewModel(RepositorioFixo(input), SavedStateHandle(), calculo = dispatcher).also { criados += it }
        vm.selecionarSegmento(SegmentoTotais.TENDENCIA)
        advanceUntilIdle()
        val estado = vm.state.first { it.tendencia != null }
        assertNotNull(estado.tagsNoTempo)
        assertNull(estado.insights)
        assertNull(estado.aCaminho)
    }

    @Test
    fun escolherACaminhoCalculaACaminhoEAsRecorrencias() = runTest(dispatcher) {
        val vm = TotaisViewModel(RepositorioFixo(input), SavedStateHandle(), calculo = dispatcher).also { criados += it }
        vm.selecionarSegmento(SegmentoTotais.A_CAMINHO)
        advanceUntilIdle()
        val estado = vm.state.first { it.aCaminho != null }
        assertNotNull(estado.recorrencias)
        assertNull(estado.tendencia)
        assertNull(estado.insights)
    }

    /** `totais` e a estimativa são do cabeçalho e valem para os três. */
    @Test
    fun oCabecalhoValeParaTodosOsSegmentos() = runTest(dispatcher) {
        val vm = TotaisViewModel(RepositorioFixo(input), SavedStateHandle(), calculo = dispatcher).also { criados += it }
        SegmentoTotais.entries.forEach { seg ->
            vm.selecionarSegmento(seg)
            advanceUntilIdle()
            assertNotNull("$seg sem totais", vm.state.first { it.totais != null }.totais)
        }
    }

    // ---- a meta de guardar (arrumacao-1) ----

    /** A meta chega por um fluxo só dela e vai ao estado, que é de onde a régua do gráfico lê. */
    @Test
    fun aMetaDosAjustesChegaAoEstado() = runTest(dispatcher) {
        val vm = TotaisViewModel(RepositorioFixo(input), SavedStateHandle(), flowOf(35), calculo = dispatcher)
            .also { criados += it }
        assertEquals(35, vm.state.first { it.totais != null }.metaGuardarPercent)
    }

    /**
     * Sem o terceiro parâmetro — o default que deixa os testes acima construírem o ViewModel com
     * dois argumentos — não há meta nenhuma, e o gráfico não desenha régua.
     */
    @Test
    fun semOFluxoDaMetaOEstadoFicaSemMeta() = runTest(dispatcher) {
        val vm = TotaisViewModel(RepositorioFixo(input), SavedStateHandle(), calculo = dispatcher).also { criados += it }
        assertEquals(0, vm.state.first { it.totais != null }.metaGuardarPercent)
    }
}
