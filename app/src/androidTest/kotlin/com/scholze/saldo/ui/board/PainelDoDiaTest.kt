package com.scholze.saldo.ui.board

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.scholze.saldo.domain.CartaoConfig
import com.scholze.saldo.domain.Fatura
import com.scholze.saldo.domain.FiltroLedger
import com.scholze.saldo.domain.ItemDia
import com.scholze.saldo.domain.LedgerInput
import com.scholze.saldo.domain.MesLedger
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
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * O painel do dia: o cabeçalho com a badge e o saldo, e um cartão por lançamento.
 *
 * Monta [DayRow] direto, sem tela em volta — era um teste da lista (`LedgerDayGridTest`), e a
 * lista deixou de existir em 2026-09-10. O que ele guarda continua sendo o mesmo, mais o
 * arranjo novo: descrição e valor em linhas separadas.
 */
@RunWith(AndroidJUnit4::class)
class PainelDoDiaTest {

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

    private val mes: MesLedger = ProjectionEngine.mes(input, mesAlvo, FiltroLedger.TODAS)

    /** [dia] é o dia do mês; 3 tem o aluguel, 5 a fatura, 6 o mercado (e é hoje). */
    private fun montar(
        dia: Int = 6,
        oculto: Boolean = false,
        entrada: MesLedger = mes,
        onItemClick: (Movimentacao) -> Unit = {},
        onFaturaClick: (Fatura) -> Unit = {},
    ) {
        rule.setContent {
            SaldoTheme(darkTheme = false) {
                CompositionLocalProvider(LocalPrivacy provides PrivacyState(ocultoInicial = oculto)) {
                    DayRow(
                        dia = entrada.dias[dia - 1],
                        faixa = entrada.faixaSaldos(),
                        hoje = hoje,
                        onItemClick = onItemClick,
                        onExcluir = {},
                        onFaturaClick = onFaturaClick,
                    )
                }
            }
        }
    }

    @Test
    fun oCartaoMostraADescricaoEOCabecalhoOSaldo() {
        montar()
        rule.onNodeWithText("mercado").assertIsDisplayed()
        // 5.000,00 − 2.400,00 (03) − 300,00 (fatura, 05) − 189,90 (06)
        rule.onNodeWithTag(tagSaldoDoDia(6)).assertTextEquals("2.110,10")
    }

    @Test
    fun oBadgeMostraODiaEODiaDaSemana() {
        montar()
        rule.onNodeWithText("06").assertIsDisplayed()
        // 06/07/2026 é uma segunda.
        rule.onNodeWithText("seg").assertExists()
    }

    /**
     * O ponto do cartão: o valor tem uma LINHA só para ele, embaixo da descrição.
     *
     * Era o defeito que o usuário viu — descrição e valor dividiam a mesma linha, e com uma
     * descrição longa o `R$ …` era espremido contra a borda. Aqui a prova é geométrica: o topo
     * do valor fica abaixo do fim da descrição, então eles não disputam largura nenhuma.
     */
    @Test
    fun oValorFicaEmbaixoDaDescricaoENaoAoLado() {
        montar()
        val descricao = rule.onNodeWithText("mercado").getUnclippedBoundsInRoot()
        val valor = rule.onNodeWithText("−R$ 189,90").getUnclippedBoundsInRoot()
        assertTrue(
            "o valor deveria começar abaixo do fim da descrição (desc.bottom=${descricao.bottom}, valor.top=${valor.top})",
            valor.top >= descricao.bottom,
        )
    }

    @Test
    fun oSaldoDoDiaMascaraQuandoOculto() {
        montar(oculto = true)
        rule.onNodeWithTag(tagSaldoDoDia(6)).assertTextEquals(MASCARA_PRIVACIDADE)
    }

    @Test
    fun aFaturaAvisaOnFaturaClickENaoOnItemClick() {
        var cliques = 0
        var faturas = 0
        montar(dia = 5, onItemClick = { cliques++ }, onFaturaClick = { faturas++ })

        // A fatura é clicável (abre o diálogo de compras) mas não é uma movimentação de
        // verdade: não há editor para uma linha que não existe no banco.
        rule.onNodeWithText("fatura cartão")
            .assertIsDisplayed()
            .assert(SemanticsMatcher.keyIsDefined(SemanticsActions.OnClick))
            .performClick()
        assertEquals(0, cliques)
        assertEquals(1, faturas)
    }

    @Test
    fun umaMovimentacaoDeVerdadeAbreOEditor() {
        var cliques = 0
        montar(dia = 3, onItemClick = { cliques++ })
        rule.onNodeWithText("aluguel")
            .assert(SemanticsMatcher.keyIsDefined(SemanticsActions.OnClick))
            .performClick()
        assertEquals(1, cliques)
    }

    @Test
    fun umDiaSemLancamentoDizQueEstaVazio() {
        montar(dia = 1)
        rule.onNodeWithText("sem movimentações").assertIsDisplayed()
    }

    // ---- o lembrete de etiquetar ----

    private val comida = Tag(id = 9, nome = "comida", cor = 0xFFB63C62L)

    /**
     * [DayRow] com o aviso ligado. `aberto` decide se as fileiras de etiqueta aparecem.
     *
     * `useUnmergedTree` em toda busca por tag dentro do aviso: ele é um bloco com
     * `semantics(mergeDescendants = true)`, e isso tira os `testTag` dos filhos da árvore
     * mesclada — inclusive das asserções de ausência, que ficariam verdes sem provar nada.
     */
    private fun montarComLembrete(
        dia: Int = 6,
        aberto: Boolean = false,
        entrada: MesLedger = mes,
        onAlternar: () -> Unit = {},
        onEtiquetar: (Movimentacao, Tag) -> Unit = { _, _ -> },
    ) {
        rule.setContent {
            SaldoTheme(darkTheme = false) {
                CompositionLocalProvider(LocalPrivacy provides PrivacyState(ocultoInicial = false)) {
                    DayRow(
                        dia = entrada.dias[dia - 1],
                        faixa = entrada.faixaSaldos(),
                        hoje = hoje,
                        onItemClick = {},
                        onExcluir = {},
                        onFaturaClick = {},
                        lembrete = LembreteDeTags(
                            aberto = aberto,
                            etiquetas = listOf(comida),
                            onAlternar = onAlternar,
                            onEtiquetar = onEtiquetar,
                            onMais = {},
                        ),
                    )
                }
            }
        }
    }

    @Test
    fun oAvisoContaOsLancamentosSemTagDoDia() {
        montarComLembrete()
        rule.onNodeWithTag(TAG_AVISO_SEM_TAG).assertIsDisplayed()
        rule.onNodeWithText("1 sem tag · etiquetar").assertIsDisplayed()
    }

    /** Sem trabalho, sem aviso: um aviso permanente é ruído nos dias já etiquetados. */
    @Test
    fun semLancamentoSemTagNaoHaAviso() {
        val etiquetado = mes.copy(
            dias = mes.dias.map { d ->
                d.copy(
                    itens = d.itens.map { item ->
                        if (item is ItemDia.Mov) ItemDia.Mov(item.mov.copy(tags = listOf(comida))) else item
                    },
                )
            },
        )
        montarComLembrete(entrada = etiquetado)
        rule.onAllNodesWithTag(TAG_AVISO_SEM_TAG, useUnmergedTree = true).assertCountEquals(0)
    }

    /** Fechado, o aviso não mostra etiqueta nenhuma. */
    @Test
    fun comOAvisoFechadoNaoHaFileira() {
        montarComLembrete(aberto = false)
        rule.onAllNodesWithContentDescription("etiquetar como comida").assertCountEquals(0)
    }

    @Test
    fun comOAvisoAbertoAFileiraAparece() {
        montarComLembrete(aberto = true)
        rule.onNodeWithContentDescription("etiquetar como comida").assertIsDisplayed()
    }

    @Test
    fun tocarNoAvisoPedeParaAlternar() {
        var alternou = 0
        montarComLembrete(onAlternar = { alternou++ })
        rule.onNodeWithTag(TAG_AVISO_SEM_TAG).performClick()
        assertEquals(1, alternou)
    }

    @Test
    fun umToqueNoChipEtiquetaALinha() {
        var etiquetada: Pair<Long, String>? = null
        montarComLembrete(aberto = true, onEtiquetar = { mov, tag -> etiquetada = mov.id to tag.nome })
        rule.onNodeWithContentDescription("etiquetar como comida").performClick()
        assertEquals(2L to "comida", etiquetada)
    }
}
