package com.scholze.saldo.widget

import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.testing.unit.assertHasText
import androidx.glance.testing.unit.hasTestTag
import androidx.glance.testing.unit.hasText
import com.scholze.saldo.ui.privacy.MASCARA_PRIVACIDADE
import java.time.LocalDate
import org.junit.Test

/** Roda na JVM: o conteúdo do widget não lê Context, só um [WidgetEstado]. */
class SaldoWidgetContentTest {
    private val pronto = WidgetEstado.Pronto(
        projetadoEm = LocalDate.parse("2026-08-31"),
        saldoProjetadoCentavos = 4_738_72,
        deltaNoMesCentavos = -61_28,
        mostrarValores = false,
    )

    @Test
    fun mascaradoPorPadraoESemDelta() = runGlanceAppWidgetUnitTest {
        setAppWidgetSize(SaldoWidget.LARGO)
        provideComposable { SaldoWidgetContent(pronto) }
        onNode(hasTestTag(TAG_WIDGET_SALDO)).assertHasText(MASCARA_PRIVACIDADE)
        onNode(hasTestTag(TAG_WIDGET_DELTA)).assertDoesNotExist()
    }

    @Test
    fun reveladoMostraLegendaSaldoEDelta() = runGlanceAppWidgetUnitTest {
        setAppWidgetSize(SaldoWidget.LARGO)
        provideComposable { SaldoWidgetContent(pronto.copy(mostrarValores = true)) }
        onNode(hasTestTag(TAG_WIDGET_LEGENDA)).assertHasText("saldo projetado · 31 ago")
        onNode(hasTestTag(TAG_WIDGET_SALDO)).assertHasText("R$ 4.738,72")
        onNode(hasTestTag(TAG_WIDGET_DELTA)).assertHasText("−R$ 61,28 no mês")
    }

    @Test
    fun compactoSoMostraOSaldo() = runGlanceAppWidgetUnitTest {
        setAppWidgetSize(SaldoWidget.COMPACTO)
        provideComposable { SaldoWidgetContent(pronto.copy(mostrarValores = true)) }
        onNode(hasTestTag(TAG_WIDGET_SALDO)).assertHasText("R$ 4.738,72")
        onNode(hasTestTag(TAG_WIDGET_LEGENDA)).assertDoesNotExist()
        onNode(hasTestTag(TAG_WIDGET_DELTA)).assertDoesNotExist()
    }

    @Test
    fun semOnboardingConvidaAComecar() = runGlanceAppWidgetUnitTest {
        setAppWidgetSize(SaldoWidget.LARGO)
        provideComposable { SaldoWidgetContent(WidgetEstado.SemOnboarding) }
        onNode(hasText("toque para começar")).assertExists()
    }
}
