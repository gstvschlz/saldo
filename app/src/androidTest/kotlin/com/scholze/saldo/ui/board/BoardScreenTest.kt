package com.scholze.saldo.ui.board

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.scholze.saldo.domain.BoardEngine
import com.scholze.saldo.domain.CartaoConfig
import com.scholze.saldo.domain.FiltroLedger
import com.scholze.saldo.domain.LedgerInput
import com.scholze.saldo.domain.Movimentacao
import com.scholze.saldo.domain.Natureza
import com.scholze.saldo.domain.ProjectionEngine
import com.scholze.saldo.ui.privacy.LocalPrivacy
import com.scholze.saldo.ui.privacy.PrivacyState
import com.scholze.saldo.ui.theme.SaldoTheme
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A grade sobre estado fabricado — sem activity, sem banco.
 *
 * As células vivem numa `LazyColumn`, então só as visíveis existem na árvore: todo
 * acesso a uma célula passa por [celula], que rola até ela antes de procurar.
 */
@RunWith(AndroidJUnit4::class)
class BoardScreenTest {

    @get:Rule val rule = createComposeRule()

    private val hoje = LocalDate.parse("2026-09-04")

    // 04/set: dois gastos no mesmo dia, 152,30 no total.
    // 12/jul: compra no cartão — ciclo jul fecha 28/jul e vence 05/ago.
    private val input = LedgerInput(
        saldoInicialCentavos = 500_000,
        saldoInicialData = LocalDate.parse("2026-01-01"),
        movimentacoes = listOf(
            Movimentacao(id = 1, descricao = "mercado", valorCentavos = -128_40, data = hoje, natureza = Natureza.DIARIO),
            Movimentacao(id = 2, descricao = "uber", valorCentavos = -23_90, data = hoje, natureza = Natureza.DIARIO),
            Movimentacao(
                id = 3, descricao = "salário", valorCentavos = 740_000,
                data = LocalDate.parse("2026-09-01"), natureza = Natureza.DIARIO,
            ),
            Movimentacao(
                id = 4, descricao = "fone", valorCentavos = -30_000,
                data = LocalDate.parse("2026-07-12"), natureza = Natureza.CARTAO,
            ),
        ),
        recorrencias = emptyList(),
        mesesMaterializados = (1..9).map { YearMonth.of(2026, it) }.toSet(),
        cartao = CartaoConfig(fechamentoDia = 28, vencimentoDia = 5),
        hoje = hoje,
    )

    private fun estado(entrada: LedgerInput = input) = BoardUiState(
        board = BoardEngine.board(entrada),
        mes = ProjectionEngine.mes(entrada, YearMonth.from(entrada.hoje), FiltroLedger.TODAS),
        hoje = entrada.hoje,
    )

    private var clicado: LocalDate? = null

    private fun montar(
        entrada: LedgerInput = input,
        oculto: Boolean = false,
        escalaFonte: Float = 1f,
    ) {
        rule.setContent {
            val densidade = LocalDensity.current
            CompositionLocalProvider(
                LocalPrivacy provides PrivacyState(ocultoInicial = oculto),
                LocalDensity provides Density(densidade.density, escalaFonte),
            ) {
                SaldoTheme {
                    BoardScreen(
                        state = estado(entrada),
                        onDiaClick = { clicado = it },
                        onVerLista = {},
                        onTogglePrivacidade = {},
                    )
                }
            }
        }
    }

    /** Rola a grade até o dia pedido e devolve o nó da célula. */
    private fun celula(data: LocalDate): SemanticsNodeInteraction {
        rule.onNodeWithTag(TAG_BOARD_GRADE).performScrollToNode(hasTestTag(tagCelula(data)))
        return rule.onNodeWithTag(tagCelula(data), useUnmergedTree = true)
    }

    @Test
    fun aGradeCobreOsUltimos12Meses() {
        assertEquals(365, BoardEngine.board(input).dias.size)
        montar()
        celula(hoje).assertExists()
        celula(LocalDate.parse("2026-07-12")).assertExists()
    }

    @Test
    fun celulaDeGastoAnunciaOValorQueSaiu() {
        montar()
        celula(hoje).assertContentDescriptionEquals("4 de setembro, saiu R$ 152,30")
    }

    @Test
    fun celulaDeEntradaAnunciaOValorQueEntrou() {
        montar()
        celula(LocalDate.parse("2026-09-01")).assertContentDescriptionEquals("1 de setembro, entrou R$ 7.400,00")
    }

    @Test
    fun diaParadoAnunciaSemMovimentacao() {
        montar()
        celula(LocalDate.parse("2026-09-03")).assertContentDescriptionEquals("3 de setembro, sem movimentação")
    }

    @Test
    fun diaDeVencimentoAnunciaAFatura() {
        montar()
        celula(LocalDate.parse("2026-08-05"))
            .assertContentDescriptionEquals("5 de agosto, sem movimentação, fatura vence")
    }

    @Test
    fun privacidadeMascaraOValorMasNaoApagaAGrade() {
        montar(oculto = true)
        celula(hoje).assertContentDescriptionEquals("4 de setembro, saiu R$ •••••")
        celula(LocalDate.parse("2026-09-03")).assertExists()
    }

    @Test
    fun aLegendaMostraODiaTipico() {
        montar()
        rule.onNodeWithTag(TAG_BOARD_GRADE).performScrollToNode(hasTestTag(TAG_BOARD_LEGENDA))
        rule.onNodeWithText("um dia típico =").assertExists()
    }

    @Test
    fun semNenhumMovimentoALegendaDizQueAindaNaoHaDiaTipico() {
        montar(input.copy(movimentacoes = emptyList()))
        rule.onNodeWithTag(TAG_BOARD_GRADE).performScrollToNode(hasTestTag(TAG_BOARD_LEGENDA))
        rule.onNodeWithText("ainda sem um dia típico").assertExists()
    }

    @Test
    fun toqueNumDiaDevolveADataDaquelaCelula() {
        clicado = null
        montar()
        celula(LocalDate.parse("2026-09-01")).performClick()
        assertEquals(LocalDate.parse("2026-09-01"), clicado)
    }

    @Test
    fun fonteGrandeNaoQuebraAGrade() {
        montar(escalaFonte = 2f)
        // O número do dia some, a célula e o seu anúncio ficam.
        celula(hoje).assertContentDescriptionEquals("4 de setembro, saiu R$ 152,30")
    }

    @Test
    fun diaAnteriorAoSaldoInicialNaoTemRegistro() {
        montar()
        celula(LocalDate.parse("2025-12-31")).assertContentDescriptionEquals("31 de dezembro, sem registro")
    }
}
