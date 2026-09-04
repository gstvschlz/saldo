package com.scholze.saldo.widget

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.testing.unit.assertHasText
import androidx.glance.testing.unit.hasContentDescription
import androidx.glance.testing.unit.hasTestTag
import androidx.glance.testing.unit.hasText
import com.scholze.saldo.ui.privacy.MASCARA_PRIVACIDADE
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Os três widgets de gráfico, na JVM: cada conteúdo recebe um estado puro, sem Context nem
 * repositório.
 *
 * O que estes testes NÃO cobrem é cor — o harness do Glance assere semântica (texto, testTag,
 * contentDescription). A defesa contra erro de cor é o `CoresWidget` num lugar só.
 */
class WidgetsGraficosTest {

    private val set = YearMonth.of(2026, 9)

    private val GRANDE = DpSize(250.dp, 110.dp)
    private val FAIXA = DpSize(250.dp, 60.dp)

    // ---------------------------------------------------------------- board

    private val board = BoardWidgetEstado.Pronto(
        mes = set,
        // Setembro de 2026 começa numa terça: a primeira semana tem um vão na segunda.
        semanas = listOf(
            listOf(null, -2, 1, 0, -3, null, null),
            listOf(-1, 0, 2, null, null, null, null),
        ),
    )

    @Test
    fun boardMostraOMes() = runGlanceAppWidgetUnitTest {
        setAppWidgetSize(GRANDE)
        provideComposable { BoardWidgetContent(board) }
        onNode(hasTestTag(TAG_BOARD_WIDGET_MES)).assertHasText("setembro")
    }

    /** O widget do board não mostra número nenhum — é o único sem nada a mascarar. */
    @Test
    fun boardNaoMostraValorNenhum() = runGlanceAppWidgetUnitTest {
        setAppWidgetSize(GRANDE)
        provideComposable { BoardWidgetContent(board) }
        onNode(hasText(MASCARA_PRIVACIDADE)).assertDoesNotExist()
    }

    @Test
    fun boardAnunciaOSentidoDeCadaDia() = runGlanceAppWidgetUnitTest {
        setAppWidgetSize(GRANDE)
        provideComposable { BoardWidgetContent(board) }
        // O fixture tem três dias negativos, dois positivos e dois parados.
        onAllNodes(hasContentDescription("dia em que saiu mais")).assertCountEquals(3)
        onAllNodes(hasContentDescription("dia em que entrou mais")).assertCountEquals(2)
        onAllNodes(hasContentDescription("dia sem movimentação")).assertCountEquals(2)
    }

    @Test
    fun boardSemOnboardingConvida() = runGlanceAppWidgetUnitTest {
        setAppWidgetSize(GRANDE)
        provideComposable { BoardWidgetContent(BoardWidgetEstado.SemOnboarding) }
        onNode(hasText("toque para começar")).assertExists()
    }

    @Test
    fun boardComFalhaDizIsso() = runGlanceAppWidgetUnitTest {
        setAppWidgetSize(GRANDE)
        provideComposable { BoardWidgetContent(BoardWidgetEstado.Falha) }
        onNode(hasText("não foi possível carregar")).assertExists()
    }

    // ---- a quebra em semanas ----

    @Test
    fun `a primeira semana ganha vao ate o dia 1`() {
        // 2026-09-01 é uma terça: um vão na segunda.
        val semanas = semanasDoWidget(List(7) { 0 }, LocalDate.of(2026, 9, 1))
        assertEquals(null, semanas.first().first())
        assertEquals(0, semanas.first()[1])
    }

    @Test
    fun `toda semana tem sete colunas`() {
        val semanas = semanasDoWidget(List(20) { 0 }, LocalDate.of(2026, 9, 1))
        assertTrue(semanas.all { it.size == 7 })
    }

    @Test
    fun `sem dia nenhum nao ha semana`() {
        assertTrue(semanasDoWidget(emptyList(), LocalDate.of(2026, 9, 1)).isEmpty())
    }

    // ---------------------------------------------------------------- ritmo

    private val ritmo = RitmoWidgetEstado.Pronto(
        mes = set,
        diasDecorridos = 12,
        gastoCentavos = 1_240_00,
        desvioPercentual = 24,
        temComparacao = true,
        gastouAlgo = true,
        fracao = 1f,
        fracaoCostume = 0.8f,
        mostrarValores = true,
    )

    @Test
    fun ritmoMostraOValorEODesvio() = runGlanceAppWidgetUnitTest {
        setAppWidgetSize(FAIXA)
        provideComposable { RitmoWidgetContent(ritmo) }
        onNode(hasTestTag(TAG_RITMO_TOTAL)).assertHasText("R$ 1.240,00")
        onNode(hasTestTag(TAG_RITMO_DESVIO)).assertHasText("+24% vs costume")
        onNode(hasText("saiu em 12 dias")).assertExists()
    }

    @Test
    fun ritmoMascaraOValor() = runGlanceAppWidgetUnitTest {
        setAppWidgetSize(FAIXA)
        provideComposable { RitmoWidgetContent(ritmo.copy(mostrarValores = false)) }
        onNode(hasTestTag(TAG_RITMO_TOTAL)).assertHasText(MASCARA_PRIVACIDADE)
        // O desvio é percentual e não é dinheiro: continua visível.
        onNode(hasTestTag(TAG_RITMO_DESVIO)).assertHasText("+24% vs costume")
    }

    @Test
    fun ritmoSemMesAnteriorDizQueNaoHaComparacao() = runGlanceAppWidgetUnitTest {
        setAppWidgetSize(FAIXA)
        provideComposable {
            RitmoWidgetContent(ritmo.copy(desvioPercentual = null, temComparacao = false))
        }
        onNode(hasTestTag(TAG_RITMO_DESVIO)).assertHasText("sem comparação")
    }

    /** Com histórico mas costume zerado neste ponto do mês, ainda dá para dizer o lado. */
    @Test
    fun ritmoComCostumeZeradoDizQueEstaAcima() = runGlanceAppWidgetUnitTest {
        setAppWidgetSize(FAIXA)
        provideComposable { RitmoWidgetContent(ritmo.copy(desvioPercentual = null)) }
        onNode(hasTestTag(TAG_RITMO_DESVIO)).assertHasText("acima do costume")
    }

    @Test
    fun `o texto do desvio muda de sinal`() {
        assertEquals("+24% vs costume", textoDoDesvio(24, temComparacao = true, gastouAlgo = true))
        assertEquals("-24% vs costume", textoDoDesvio(-24, temComparacao = true, gastouAlgo = true))
        assertEquals("no costume", textoDoDesvio(0, temComparacao = true, gastouAlgo = true))
    }

    @Test
    fun `sem comparacao e costume zerado dizem coisas diferentes`() {
        assertEquals("sem comparação", textoDoDesvio(null, temComparacao = false, gastouAlgo = true))
        assertEquals("acima do costume", textoDoDesvio(null, temComparacao = true, gastouAlgo = true))
        assertEquals("no costume", textoDoDesvio(null, temComparacao = true, gastouAlgo = false))
    }

    @Test
    fun `a barra cheia ocupa tudo e a vazia nada`() {
        assertEquals(PESO_TOTAL, pesoCheio(1f))
        assertEquals(0, pesoCheio(0f))
        assertEquals(PESO_TOTAL / 2, pesoCheio(0.5f))
    }

    @Test
    fun `fracao fora da faixa nao estoura a barra`() {
        assertEquals(PESO_TOTAL, pesoCheio(3f))
        assertEquals(0, pesoCheio(-1f))
    }

    // ---------------------------------------------------------------- poupança

    private val poupanca = PoupancaWidgetEstado.Pronto(
        (5 downTo 0).map { k ->
            PoupancaWidgetEstado.Pronto.Ponto(set.minusMonths(k.toLong()), 12 - k)
        },
    )

    @Test
    fun poupancaMostraATaxaDoMesEOsRotulos() = runGlanceAppWidgetUnitTest {
        setAppWidgetSize(GRANDE)
        provideComposable { PoupancaWidgetContent(poupanca) }
        onNode(hasTestTag(TAG_POUPANCA_TAXA)).assertHasText("12%")
        onNode(hasText("set")).assertExists()
        onNode(hasText("abr")).assertExists()
    }

    @Test
    fun poupancaSemTaxaNoMesMostraTraco() = runGlanceAppWidgetUnitTest {
        setAppWidgetSize(GRANDE)
        val semTaxa = poupanca.copy(
            meses = poupanca.meses.dropLast(1) + poupanca.meses.last().copy(taxa = null),
        )
        provideComposable { PoupancaWidgetContent(semTaxa) }
        onNode(hasTestTag(TAG_POUPANCA_TAXA)).assertHasText("—")
    }

    @Test
    fun `mes sem taxa nao desenha barra e taxa zero desenha uma`() {
        assertEquals(0, blocosDaTaxa(null, 12))
        assertEquals(1, blocosDaTaxa(0, 12))
    }

    @Test
    fun `a maior taxa enche a barra`() {
        assertEquals(6, blocosDaTaxa(12, 12))
        assertEquals(3, blocosDaTaxa(6, 12))
    }

    /** Todas as taxas zeradas: nenhuma é "a maior", e todas ficam no piso de um bloco. */
    @Test
    fun `sem taxa maior todas ficam no piso`() {
        assertEquals(1, blocosDaTaxa(0, 0))
    }
}
