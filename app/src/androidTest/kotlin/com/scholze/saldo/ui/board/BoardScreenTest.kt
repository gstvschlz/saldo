package com.scholze.saldo.ui.board

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
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

    // A grade é do dia 1 até hoje; hoje no dia 20 dá mês suficiente para caber tudo
    // o que estes testes olham.
    private val hoje = LocalDate.parse("2026-09-20")

    /** 04/set: dois gastos no mesmo dia, 152,30 no total. */
    private val diaDoGasto = LocalDate.parse("2026-09-04")

    // 12/ago: compra no cartão — ciclo ago fecha 28/ago e vence 05/set, dentro da grade.
    private val input = LedgerInput(
        saldoInicialCentavos = 500_000,
        saldoInicialData = LocalDate.parse("2026-01-01"),
        movimentacoes = listOf(
            Movimentacao(
                id = 1, descricao = "mercado", valorCentavos = -128_40,
                data = diaDoGasto, natureza = Natureza.DIARIO,
            ),
            Movimentacao(
                id = 2, descricao = "uber", valorCentavos = -23_90,
                data = diaDoGasto, natureza = Natureza.DIARIO,
            ),
            Movimentacao(
                id = 3, descricao = "salário", valorCentavos = 740_000,
                data = LocalDate.parse("2026-09-01"), natureza = Natureza.DIARIO,
            ),
            Movimentacao(
                id = 4, descricao = "fone", valorCentavos = -30_000,
                data = LocalDate.parse("2026-08-12"), natureza = Natureza.CARTAO,
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
    fun aGradeCobreDoDia1AteHoje() {
        assertEquals(20, BoardEngine.board(input).dias.size)
        montar()
        celula(LocalDate.parse("2026-09-01")).assertExists()
        celula(hoje).assertExists()
    }

    /** O mês passado saiu da grade: a compra de 12/ago não tem célula nenhuma. */
    @Test
    fun oMesPassadoNaoTemCelula() {
        montar()
        rule.onAllNodesWithTag(tagCelula(LocalDate.parse("2026-08-12")))
            .fetchSemanticsNodes()
            .let { assertEquals(0, it.size) }
    }

    /** O hero fica fora da rolagem: a grade abre em hoje e ele não pode sumir junto. */
    @Test
    fun oHeroFicaVisivelComAGradeAbertaEmHoje() {
        montar()
        rule.onNodeWithText("saldo projetado", substring = true).assertIsDisplayed()
    }

    @Test
    fun celulaDeGastoAnunciaOValorQueSaiu() {
        montar()
        celula(diaDoGasto).assertContentDescriptionEquals("4 de setembro, saiu R$ 152,30")
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
        celula(LocalDate.parse("2026-09-05"))
            .assertContentDescriptionEquals("5 de setembro, sem movimentação, fatura vence")
    }

    @Test
    fun privacidadeMascaraOValorMasNaoApagaAGrade() {
        montar(oculto = true)
        celula(diaDoGasto).assertContentDescriptionEquals("4 de setembro, saiu R$ •••••")
        celula(LocalDate.parse("2026-09-03")).assertExists()
    }

    @Test
    fun aLegendaMostraODiaTipico() {
        montar()
        // A régua não rola com a grade: está sempre na tela.
        rule.onNodeWithTag(TAG_BOARD_LEGENDA).assertExists()
        rule.onNodeWithText("um dia típico =").assertIsDisplayed()
    }

    @Test
    fun semNenhumMovimentoALegendaDizQueAindaNaoHaDiaTipico() {
        montar(input.copy(movimentacoes = emptyList()))
        rule.onNodeWithText("ainda sem um dia típico").assertIsDisplayed()
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
        celula(diaDoGasto).assertContentDescriptionEquals("4 de setembro, saiu R$ 152,30")
    }

    @Test
    fun diaAnteriorAoSaldoInicialNaoTemRegistro() {
        // Saldo inicial no dia 3: o dia 2 está na grade, mas não há registro dele.
        montar(input.copy(saldoInicialData = LocalDate.parse("2026-09-03")))
        celula(LocalDate.parse("2026-09-02")).assertContentDescriptionEquals("2 de setembro, sem registro")
    }
}
