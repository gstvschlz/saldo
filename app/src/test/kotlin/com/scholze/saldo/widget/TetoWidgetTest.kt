package com.scholze.saldo.widget

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.testing.unit.assertHasText
import androidx.glance.testing.unit.hasTestTag
import androidx.glance.testing.unit.hasText
import com.scholze.saldo.ui.privacy.MASCARA_PRIVACIDADE
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * O widget "hoje", na JVM: o conteúdo recebe um estado puro, sem Context nem repositório.
 *
 * Como nos outros, o harness do Glance assere semântica e não cor — a defesa contra erro de cor
 * é o `CoresWidget` num lugar só.
 */
class TetoWidgetTest {

    private val FAIXA = DpSize(250.dp, 60.dp)

    private val pronto = TetoWidgetEstado.Pronto(
        mes = YearMonth.of(2026, 9),
        tetoCentavos = 8_740,
        restaCentavos = 8_740,
        gastouHoje = false,
        diasRestantes = 12,
        estourouODia = false,
        estourouOMes = false,
        mostrarValores = true,
    )

    /** Depois do primeiro gasto do dia: R$ 42 almoçados de um teto de R$ 87,40. */
    private val gastou = pronto.copy(
        restaCentavos = 4_540,
        gastouHoje = true,
    )

    @Test
    fun antesDoPrimeiroGastoAdireitaContaOsDias() = runGlanceAppWidgetUnitTest {
        setAppWidgetSize(FAIXA)
        provideComposable { TetoWidgetContent(pronto) }
        onNode(hasTestTag(TAG_TETO_VALOR)).assertHasText("R$ 87,40")
        onNode(hasTestTag(TAG_TETO_DIREITA)).assertHasText("faltam 12 dias")
        onNode(hasText("hoje")).assertExists()
    }

    @Test
    fun depoisDeGastarAdireitaMostraOQueResta() = runGlanceAppWidgetUnitTest {
        setAppWidgetSize(FAIXA)
        provideComposable { TetoWidgetContent(gastou) }
        onNode(hasTestTag(TAG_TETO_VALOR)).assertHasText("R$ 87,40")
        onNode(hasTestTag(TAG_TETO_DIREITA)).assertHasText("restam R$ 45,40")
    }

    @Test
    fun valorMascaradoEtetoMascarado() = runGlanceAppWidgetUnitTest {
        setAppWidgetSize(FAIXA)
        provideComposable { TetoWidgetContent(gastou.copy(mostrarValores = false)) }
        onNode(hasTestTag(TAG_TETO_VALOR)).assertHasText(MASCARA_PRIVACIDADE)
        // Os dois números são dinheiro: esconder um e mostrar o outro contaria o que o
        // mascaramento esconde, porque o teto se deduz do resta mais o gasto do dia.
        onNode(hasTestTag(TAG_TETO_DIREITA)).assertHasText("restam $MASCARA_PRIVACIDADE")
    }

    @Test
    fun mesSemEntradaConvidaEmVezDeMostrarZero() = runGlanceAppWidgetUnitTest {
        setAppWidgetSize(FAIXA)
        provideComposable { TetoWidgetContent(TetoWidgetEstado.SemRenda) }
        onNode(hasText("sem entrada neste mês")).assertExists()
        onNode(hasTestTag(TAG_TETO_VALOR)).assertDoesNotExist()
    }

    @Test
    fun antesDoOnboardingConvidaAabrirOApp() = runGlanceAppWidgetUnitTest {
        setAppWidgetSize(FAIXA)
        provideComposable { TetoWidgetContent(TetoWidgetEstado.SemOnboarding) }
        onNode(hasText("toque para começar")).assertExists()
    }

    @Test
    fun falhaConvidaEmVezDeMostrarNumeroVelho() = runGlanceAppWidgetUnitTest {
        setAppWidgetSize(FAIXA)
        provideComposable { TetoWidgetContent(TetoWidgetEstado.Falha) }
        onNode(hasText("não foi possível carregar")).assertExists()
    }

    // ---- os textos, sem composição ----

    @Test
    fun `o mes estourado mostra o negativo`() {
        assertEquals("−R$ 27,27", textoDoTeto(pronto.copy(tetoCentavos = -2_727, estourouOMes = true)))
    }

    @Test
    fun `no ultimo dia a direita diz que e o ultimo dia`() {
        assertEquals("último dia do mês", textoDaDireita(pronto.copy(diasRestantes = 1)))
    }

    @Test
    fun `o dia estourado mostra o resta negativo`() {
        assertEquals(
            "restam −R$ 14,60",
            textoDaDireita(gastou.copy(restaCentavos = -1_460, estourouODia = true)),
        )
    }
}
