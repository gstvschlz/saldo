package com.scholze.saldo.ui.board

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
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
import com.scholze.saldo.domain.TetoEngine
import com.scholze.saldo.ui.ledger.SEM_ENTRADA
import com.scholze.saldo.ui.ledger.TAG_PILL_GUARDADO
import com.scholze.saldo.ui.ledger.TAG_TETO_HOJE
import com.scholze.saldo.ui.ledger.TAG_TETO_RESTA
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

    private fun estado(
        entrada: LedgerInput = input,
        diaAberto: LocalDate? = null,
        mesVisto: YearMonth,
        meta: Int = 20,
    ) = BoardUiState(
        board = BoardEngine.board(entrada, mesVisto),
        mes = ProjectionEngine.mes(entrada, mesVisto, FiltroLedger.TODAS),
        hoje = entrada.hoje,
        mesAtual = mesVisto,
        diaAberto = diaAberto,
        // A mesma regra do `BoardViewModel`: fora do mês corrente não há "hoje" para ter teto.
        teto = if (mesVisto == YearMonth.from(entrada.hoje)) TetoEngine.teto(entrada, meta) else null,
    )

    private var clicado: LocalDate? = null
    private var mesesAndados = 0
    private var pediuLista = false
    private var pediuGuardado = false
    private var alternouPrivacidade = 0

    private fun montar(
        entrada: LedgerInput = input,
        oculto: Boolean = false,
        escalaFonte: Float = 1f,
        diaAberto: LocalDate? = null,
        mesVisto: YearMonth = YearMonth.from(input.hoje),
        meta: Int = 20,
    ) {
        rule.setContent {
            val densidade = LocalDensity.current
            CompositionLocalProvider(
                LocalPrivacy provides PrivacyState(ocultoInicial = oculto),
                LocalDensity provides Density(densidade.density, escalaFonte),
            ) {
                SaldoTheme {
                    BoardScreen(
                        state = estado(entrada, diaAberto, mesVisto, meta),
                        onDiaClick = { clicado = it },
                        onMesAnterior = { mesesAndados-- },
                        onProximoMes = { mesesAndados++ },
                        onItemClick = {},
                        onExcluir = {},
                        onTogglePrivacidade = { alternouPrivacidade++ },
                        onVerLista = { pediuLista = true },
                        onVerGuardado = { pediuGuardado = true },
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

    // `semNenhumMovimentoALegendaDizQueAindaNaoHaDiaTipico` foi substituído por
    // `semMovimentacaoNoMesAGradeConvidaALancarOPrimeiro` (uso-diario-1, Task 7): um mês
    // sem NENHUMA movimentação agora troca a régua inteira pelo convite — a régua não
    // teria o que explicar sem uma célula colorida sequer.

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

    // ---- o mês na barra e o painel do dia ----

    @Test
    fun aBarraMostraOMesDaGrade() {
        montar()
        rule.onNodeWithText("setembro 2026").assertIsDisplayed()
    }

    @Test
    fun aSetaEsquerdaPedeOMesAnterior() {
        mesesAndados = 0
        montar()
        rule.onNodeWithContentDescription("mês anterior").performClick()
        assertEquals(-1, mesesAndados)
    }

    /** No mês corrente a seta direita está lá, esmaecida, e não faz nada. */
    @Test
    fun noMesCorrenteASetaDireitaEstaDesabilitada() {
        mesesAndados = 0
        montar()
        rule.onNodeWithContentDescription("próximo mês").assertIsNotEnabled()
        rule.onNodeWithContentDescription("próximo mês").performClick()
        assertEquals(0, mesesAndados)
    }

    @Test
    fun numMesPassadoASetaDireitaAvanca() {
        mesesAndados = 0
        montar(mesVisto = YearMonth.of(2026, 8))
        rule.onNodeWithContentDescription("próximo mês").assertIsEnabled()
        rule.onNodeWithContentDescription("próximo mês").performClick()
        assertEquals(1, mesesAndados)
    }

    @Test
    fun verComoListaAvisaQuemMontou() {
        pediuLista = false
        montar()
        rule.onNodeWithContentDescription("ver como lista").performClick()
        assertEquals(true, pediuLista)
    }

    @Test
    fun semMovimentacaoNoMesAGradeConvidaALancarOPrimeiro() {
        montar(entrada = input.copy(movimentacoes = emptyList()))
        rule.onNodeWithTag(TAG_BOARD_GRADE).performScrollToNode(hasTestTag(TAG_BOARD_VAZIO))
        rule.onNodeWithText("toque em + para lançar o primeiro").assertIsDisplayed()
        rule.onAllNodesWithTag(TAG_BOARD_LEGENDA).assertCountEquals(0)
    }

    @Test
    fun comMovimentacaoALegendaVoltaNoLugarDoConvite() {
        montar()
        rule.onNodeWithTag(TAG_BOARD_GRADE).performScrollToNode(hasTestTag(TAG_BOARD_LEGENDA))
        rule.onAllNodesWithTag(TAG_BOARD_VAZIO).assertCountEquals(0)
    }

    @Test
    fun oPainelMostraOsLancamentosDoDiaAberto() {
        montar(diaAberto = diaDoGasto)
        rule.onNodeWithText("mercado").assertIsDisplayed()
        rule.onNodeWithText("uber").assertIsDisplayed()
    }

    /** A régua não some quando um dia abre: é ela que explica a cor de toda a grade. */
    @Test
    fun aReguaFicaMesmoComODiaAberto() {
        montar(diaAberto = diaDoGasto)
        rule.onNodeWithTag(TAG_BOARD_LEGENDA).assertExists()
    }

    @Test
    fun semDiaAbertoORodapeEaRegua() {
        montar()
        rule.onNodeWithTag(TAG_BOARD_LEGENDA).assertExists()
        rule.onAllNodesWithText("mercado").fetchSemanticsNodes().let { assertEquals(0, it.size) }
    }

    @Test
    fun diaAbertoSemLancamentoDizQueNaoTeveMovimentacao() {
        montar(diaAberto = LocalDate.parse("2026-09-03"))
        rule.onNodeWithText("sem movimentações").assertIsDisplayed()
    }

    @Test
    fun diaAnteriorAoSaldoInicialNaoTemRegistro() {
        // Saldo inicial no dia 3: o dia 2 está na grade, mas não há registro dele.
        montar(input.copy(saldoInicialData = LocalDate.parse("2026-09-03")))
        celula(LocalDate.parse("2026-09-02")).assertContentDescriptionEquals("2 de setembro, sem registro")
    }

    // ---- pill "guardou N%" ----

    @Test
    fun oHeroDizQuantoGuardouDoQueEntrou() {
        val comEconomia = input.copy(
            movimentacoes = input.movimentacoes + Movimentacao(
                id = 5, descricao = "cdb", valorCentavos = -1_480_00,
                data = LocalDate.parse("2026-09-02"), natureza = Natureza.ECONOMIA,
            ),
        )
        montar(entrada = comEconomia)
        rule.onNodeWithTag(TAG_PILL_GUARDADO).assertIsDisplayed()
        rule.onNodeWithText("guardou 20%").assertIsDisplayed()
    }

    /**
     * A pill não some mais num mês sem entrada — ela diz o motivo. Antes de 2026-09-10 o hero
     * simplesmente ficava sem ela nos primeiros dias do mês.
     */
    @Test
    fun semEntradaNoMesAPillDizOMotivo() {
        montar(entrada = input.copy(movimentacoes = input.movimentacoes.filter { it.valorCentavos < 0 }))
        rule.onNodeWithTag(TAG_PILL_GUARDADO).assertIsDisplayed()
        rule.onNodeWithText(SEM_ENTRADA).assertIsDisplayed()
    }

    @Test
    fun aPillFicaVisivelComAPrivacidadeLigada() {
        val comEconomia = input.copy(
            movimentacoes = input.movimentacoes + Movimentacao(
                id = 5, descricao = "cdb", valorCentavos = -1_480_00,
                data = LocalDate.parse("2026-09-02"), natureza = Natureza.ECONOMIA,
            ),
        )
        montar(entrada = comEconomia, oculto = true)
        rule.onNodeWithText("guardou 20%").assertIsDisplayed()
    }

    @Test
    fun tocarNaPillPedeATendencia() {
        pediuGuardado = false
        alternouPrivacidade = 0
        montar(entrada = input.copy(movimentacoes = input.movimentacoes + Movimentacao(
            id = 5, descricao = "cdb", valorCentavos = -1_480_00,
            data = LocalDate.parse("2026-09-02"), natureza = Natureza.ECONOMIA,
        )))
        rule.onNodeWithTag(TAG_PILL_GUARDADO).performClick()
        assertEquals(true, pediuGuardado)
        // O clique na pill não deve vazar para o toggle de privacidade do hero por baixo dela.
        assertEquals(0, alternouPrivacidade)
    }

    // ---- o teto do dia ----
    //
    // Toda busca por tag aqui passa por `useUnmergedTree = true`: a linha do teto é UM nó para o
    // TalkBack (`mergeDescendants`), e na árvore mesclada os `testTag` dos filhos somem. Sem isso
    // um `assertCountEquals(0)` fica verde sem provar nada.

    /**
     * O cenário-base: salário de R$ 7.400 no dia 1, meta de 20% (R$ 1.480), R$ 152,30 gastos
     * até o dia 19 e onze dias pela frente — R$ 5.767,70 ÷ 11 = R$ 524,33.
     */
    @Test
    fun oHeroMostraOTetoDoDia() {
        montar()
        rule.onNodeWithTag(TAG_TETO_HOJE, useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithText("R$ 524,33").assertIsDisplayed()
    }

    @Test
    fun antesDoPrimeiroGastoDoDiaNaoHaLinhaDeRestam() {
        montar()
        rule.onAllNodesWithTag(TAG_TETO_RESTA, useUnmergedTree = true).assertCountEquals(0)
    }

    @Test
    fun gastarHojeAbreALinhaDeRestamSemMexerNoTeto() {
        val gastouHoje = input.copy(
            movimentacoes = input.movimentacoes + Movimentacao(
                id = 6, descricao = "almoço", valorCentavos = -100_00,
                data = hoje, natureza = Natureza.DIARIO,
            ),
        )
        montar(entrada = gastouHoje)
        // O teto é fixado à meia-noite: o gasto de hoje não o move.
        rule.onNodeWithText("R$ 524,33").assertIsDisplayed()
        rule.onNodeWithTag(TAG_TETO_RESTA, useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithText("R$ 424,33").assertIsDisplayed()
    }

    @Test
    fun oTetoNaoExisteNoMesAnterior() {
        montar(mesVisto = YearMonth.of(2026, 8))
        rule.onAllNodesWithTag(TAG_TETO_HOJE, useUnmergedTree = true).assertCountEquals(0)
    }

    @Test
    fun mesSemEntradaNaoTemTeto() {
        montar(entrada = input.copy(movimentacoes = input.movimentacoes.filter { it.valorCentavos < 0 }))
        rule.onAllNodesWithTag(TAG_TETO_HOJE, useUnmergedTree = true).assertCountEquals(0)
    }

    @Test
    fun oTetoEDinheiroEsomeComAPrivacidade() {
        montar(oculto = true)
        rule.onAllNodesWithText("R$ 524,33").assertCountEquals(0)
        rule.onNodeWithTag(TAG_TETO_HOJE, useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun semMetaOTetoSoDescontaOQueSaiu() {
        // Sem os R$ 1.480 de reserva sobram R$ 7.247,70 para onze dias.
        montar(meta = 0)
        rule.onNodeWithText("R$ 658,88").assertIsDisplayed()
    }
}
