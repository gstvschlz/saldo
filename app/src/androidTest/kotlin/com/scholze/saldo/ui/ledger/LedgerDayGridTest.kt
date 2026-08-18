package com.scholze.saldo.ui.ledger

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.scholze.saldo.domain.CartaoConfig
import com.scholze.saldo.domain.FiltroLedger
import com.scholze.saldo.domain.LedgerInput
import com.scholze.saldo.domain.Movimentacao
import com.scholze.saldo.domain.Natureza
import com.scholze.saldo.domain.ProjectionEngine
import com.scholze.saldo.domain.Tag
import com.scholze.saldo.ui.privacy.LocalPrivacy
import com.scholze.saldo.ui.privacy.MASCARA_PRIVACIDADE
import com.scholze.saldo.ui.privacy.PrivacyState
import com.scholze.saldo.ui.theme.SaldoTheme
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A grade de dias, sobre estado fabricado — sem activity, sem banco.
 *
 * O fluxo ponta-a-ponta ([com.scholze.saldo.EntryFlowTest]) só consegue produzir um mês
 * com uma linha; aqui o mês é montado à mão, com uma instância de recorrência e uma
 * compra no cartão que vira linha de fatura, que é o que exercita a grade de verdade.
 */
@RunWith(AndroidJUnit4::class)
class LedgerDayGridTest {

    @get:Rule val rule = createComposeRule()

    private val hoje = LocalDate.parse("2026-07-06")
    private val mesAlvo = YearMonth.of(2026, 7)

    // Fechamento 28 / vencimento 5: a compra de 15 jun fecha no ciclo de junho e vence
    // em 05 jul — é assim que a fatura cai DENTRO do mês exibido.
    private val input = LedgerInput(
        saldoInicialCentavos = 5_000_00,
        saldoInicialData = LocalDate.parse("2026-06-01"),
        movimentacoes = listOf(
            Movimentacao(
                id = 1, descricao = "aluguel", valorCentavos = -2_400_00,
                data = LocalDate.parse("2026-07-03"), natureza = Natureza.DIARIO, recorrenciaId = 7,
            ),
            Movimentacao(
                id = 2, descricao = "mercado", valorCentavos = -189_90,
                data = hoje, natureza = Natureza.DIARIO,
            ),
            Movimentacao(
                id = 3, descricao = "fone", valorCentavos = -300_00,
                data = LocalDate.parse("2026-06-15"), natureza = Natureza.CARTAO,
            ),
        ),
        // Meses marcados: nada de expansão virtual por cima das linhas acima.
        recorrencias = emptyList(),
        mesesMaterializados = setOf(YearMonth.of(2026, 6), mesAlvo),
        cartao = CartaoConfig(),
        hoje = hoje,
    )

    private val estado = LedgerUiState(
        mes = ProjectionEngine.mes(input, mesAlvo, FiltroLedger.TODAS),
        mesAtual = mesAlvo,
        filtro = FiltroLedger.TODAS,
        hoje = hoje,
    )

    private fun montar(
        oculto: Boolean = false,
        onItemClick: (Movimentacao) -> Unit = {},
        state: LedgerUiState = estado,
        onLimparTag: () -> Unit = {},
    ) {
        rule.setContent {
            SaldoTheme(darkTheme = false) {
                CompositionLocalProvider(LocalPrivacy provides PrivacyState(ocultoInicial = oculto)) {
                    LedgerScreen(
                        state = state,
                        onMesAnterior = {},
                        onProximoMes = {},
                        onFiltro = {},
                        onItemClick = onItemClick,
                        onExcluir = {},
                        onTogglePrivacidade = {},
                        onLimparTag = onLimparTag,
                    )
                }
            }
        }
    }

    @Test
    fun diaMostraDescricaoDoItemESaldoNaColuna() {
        montar()
        rule.onNodeWithText("aluguel").assertIsDisplayed()
        // 5.000,00 − 2.400,00 (03) − 300,00 (fatura, 05) − 189,90 (06)
        rule.onNodeWithTag(tagSaldoDoDia(6)).assertTextEquals("2.110,10")
    }

    @Test
    fun numeroDoDiaDeHojeAparece() {
        montar()
        rule.onNodeWithText("06").assertIsDisplayed()
    }

    @Test
    fun linhaDeFaturaAbreDialogMasNaoInvocaOnItemClick() {
        var cliques = 0
        montar(onItemClick = { cliques++ })

        // A fatura agora É clicável (Step 1b: abre o diálogo de compras)...
        rule.onNodeWithText("fatura cartão")
            .assertIsDisplayed()
            .assert(SemanticsMatcher.keyIsDefined(SemanticsActions.OnClick))
            .performClick()

        // ...mas não é uma movimentação de verdade: onItemClick não dispara, e o diálogo
        // com a compra ("fone", a única no ciclo) abre no lugar do editor. A linha da
        // compra é um só nó de texto ("15 jun  fone"), daí o substring.
        assertEquals(0, cliques)
        rule.onNodeWithText("fone", substring = true).assertIsDisplayed()
        rule.onNodeWithText("ok").performClick()

        // ...ao contrário de uma movimentação de verdade, que abre o editor via onItemClick.
        rule.onNodeWithText("aluguel").assert(SemanticsMatcher.keyIsDefined(SemanticsActions.OnClick)).performClick()
        assertEquals(1, cliques)
    }

    @Test
    fun colunaDeSaldoDoDiaMascaraQuandoOculto() {
        montar(oculto = true)
        rule.onNodeWithTag(tagSaldoDoDia(6)).assertTextEquals(MASCARA_PRIVACIDADE)
    }

    @Test
    fun chipDeTagApareceEDispensa() {
        val comida = Tag(id = 1, nome = "comida", cor = 0xFFA6486B)
        var limpou = 0
        montar(state = estado.copy(tagFiltro = comida), onLimparTag = { limpou++ })

        rule.onNodeWithText("tag: comida").assertIsDisplayed().performClick()
        assertEquals(1, limpou)
    }

    @Test
    fun mesVazioSobFiltroDeTagNaoMandaTocarNoMais() {
        val comida = Tag(id = 1, nome = "comida", cor = 0xFFA6486B)
        // O mesmo mês, projetado por uma tag que nenhuma linha carrega: fica vazio, mas
        // "toque em + para adicionar" seria conselho errado — o mês tem movimentações.
        val vazioPorTag = estado.copy(
            mes = ProjectionEngine.mes(input, mesAlvo, FiltroLedger.TODAS, tagId = comida.id),
            tagFiltro = comida,
        )
        montar(state = vazioPorTag)
        rule.onNodeWithText("nenhuma movimentação com essa tag").assertIsDisplayed()
        rule.onNodeWithText("toque no × acima para ver o mês inteiro").assertIsDisplayed()
    }

    /**
     * A coluna morreu, a escala de calor nao: os dois valores continuam legiveis e
     * distintos na pill. O teste bate no que da para observar — a cor de fundo de um
     * Box nao e exposta na arvore de semantica.
     */
    @Test
    fun aPillCarregaOSaldoDeCadaDia() {
        montar()
        rule.onNodeWithTag(tagSaldoDoDia(3)).assertTextEquals("2.600,00")
        rule.onNodeWithTag(tagSaldoDoDia(6)).assertTextEquals("2.110,10")
    }

    /** O dia da semana entrou junto com o badge — 06/07/2026 e uma segunda. */
    @Test
    fun oBadgeDoDiaMostraODiaDaSemana() {
        montar()
        rule.onNodeWithText("seg").assertExists()
    }
}
