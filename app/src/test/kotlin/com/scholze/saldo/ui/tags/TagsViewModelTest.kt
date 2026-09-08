package com.scholze.saldo.ui.tags

import com.scholze.saldo.RepositorioFixo
import com.scholze.saldo.domain.CartaoConfig
import com.scholze.saldo.domain.LedgerInput
import com.scholze.saldo.domain.Movimentacao
import com.scholze.saldo.domain.Natureza
import com.scholze.saldo.domain.Recorrencia
import com.scholze.saldo.domain.Tag
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TagsViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val mercado = Tag(id = 1, nome = "mercado", cor = 0xFF112233L)

    // setembro é o mês corrente do teste; agosto e setembro nunca foram abertos.
    private val input = LedgerInput(
        saldoInicialCentavos = 100_000_00,
        saldoInicialData = LocalDate.parse("2026-09-01"),
        movimentacoes = listOf(
            // antes da âncora: não conta
            Movimentacao(id = 1, descricao = "agosto", valorCentavos = -500_00,
                data = LocalDate.parse("2026-08-20"), natureza = Natureza.DIARIO, tags = listOf(mercado)),
            // depois da âncora: conta
            Movimentacao(id = 2, descricao = "setembro", valorCentavos = -80_00,
                data = LocalDate.parse("2026-09-03"), natureza = Natureza.DIARIO, tags = listOf(mercado)),
        ),
        recorrencias = listOf(
            // nunca materializada: só existe como ocorrência virtual, e mesmo assim conta
            Recorrencia(id = 9, descricao = "feira", valorCentavos = -120_00, natureza = Natureza.DIARIO,
                diaDoMes = 5, inicio = YearMonth.of(2026, 1), tags = listOf(mercado)),
        ),
        mesesMaterializados = emptySet(),
        cartao = CartaoConfig(),
        hoje = LocalDate.parse("2026-09-20"),
    )

    @Before fun setMain() = Dispatchers.setMain(dispatcher)
    @After fun resetMainDispatcher() = Dispatchers.resetMain()

    @Test
    fun oTotalDaTagIncluiAVirtualEExcluiOAnteriorAAncora() = runTest(dispatcher) {
        val vm = TagsViewModel(RepositorioFixo(input, tags = listOf(mercado)))
        val estado = vm.state.first { it.tags.isNotEmpty() }
        // 80,00 da linha real + 120,00 da ocorrência virtual; os 500,00 de agosto ficam de fora
        assertEquals(listOf(mercado to 200_00L), estado.tags)
    }
}
