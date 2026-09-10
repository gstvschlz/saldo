package com.scholze.saldo.widget

import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.testing.unit.assertHasText
import androidx.glance.testing.unit.hasTestTag
import androidx.glance.testing.unit.hasText
import com.scholze.saldo.data.db.toAnoMes
import com.scholze.saldo.ui.board.SEM_ENTRADA
import com.scholze.saldo.ui.nav.Destino
import com.scholze.saldo.ui.privacy.MASCARA_PRIVACIDADE
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Test

/** Roda na JVM: o conteúdo do widget não lê Context, só um [WidgetEstado]. */
class SaldoWidgetContentTest {
    private val pronto = WidgetEstado.Pronto(
        projetadoEm = LocalDate.parse("2026-08-31"),
        saldoProjetadoCentavos = 4_738_72,
        deltaNoMesCentavos = -61_28,
        mostrarValores = false,
        taxaGuardada = 20,
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

    @Test
    fun falhaConvidaAAbrirDeNovo() = runGlanceAppWidgetUnitTest {
        setAppWidgetSize(SaldoWidget.LARGO)
        provideComposable { SaldoWidgetContent(WidgetEstado.Falha) }
        onNode(hasText("não foi possível carregar")).assertExists()
        onNode(hasText("toque para abrir")).assertExists()
    }

    @Test
    fun largoReveladoDizQuantoGuardou() = runGlanceAppWidgetUnitTest {
        setAppWidgetSize(SaldoWidget.LARGO)
        provideComposable { SaldoWidgetContent(pronto.copy(mostrarValores = true)) }
        onNode(hasTestTag(TAG_WIDGET_GUARDADO)).assertHasText("guardou 20%")
    }

    /** A porcentagem não é dinheiro: mascarar o saldo não a esconde mais (2026-09-10). */
    @Test
    fun largoMascaradoAindaDizQuantoGuardou() = runGlanceAppWidgetUnitTest {
        setAppWidgetSize(SaldoWidget.LARGO)
        provideComposable { SaldoWidgetContent(pronto) }
        onNode(hasTestTag(TAG_WIDGET_GUARDADO)).assertHasText("guardou 20%")
    }

    /** Sem entrada no mês a linha não some: ela diz por que não há porcentagem. */
    @Test
    fun semTaxaALinhaDizOMotivo() = runGlanceAppWidgetUnitTest {
        setAppWidgetSize(SaldoWidget.LARGO)
        provideComposable { SaldoWidgetContent(pronto.copy(mostrarValores = true, taxaGuardada = null)) }
        onNode(hasTestTag(TAG_WIDGET_GUARDADO)).assertHasText(SEM_ENTRADA)
    }

    @Test
    fun compactoNaoTemALinha() = runGlanceAppWidgetUnitTest {
        setAppWidgetSize(SaldoWidget.COMPACTO)
        provideComposable { SaldoWidgetContent(pronto.copy(mostrarValores = true)) }
        onNode(hasTestTag(TAG_WIDGET_GUARDADO)).assertDoesNotExist()
    }

    @Test
    fun quadradoTambemDizQuantoGuardou() = runGlanceAppWidgetUnitTest {
        setAppWidgetSize(SaldoWidget.QUADRADO)
        provideComposable { SaldoWidgetContent(pronto.copy(mostrarValores = true)) }
        onNode(hasTestTag(TAG_WIDGET_GUARDADO)).assertHasText("guardou 20%")
    }

    // ---- a meta no widget (arrumacao-1) ----

    /** A regra inteira num predicado: o composable só escolhe entre duas cores a partir dele. */
    @Test
    fun comMetaBatidaACorMuda() =
        assertEquals(
            true,
            pronto.copy(mostrarValores = true, taxaGuardada = 22, metaGuardarPercent = 20).metaBatida,
        )

    /**
     * A cor não depende mais da máscara. Enquanto o texto era `••%`, deixar a cor mudar contaria
     * pela cor o que o texto escondia; sem máscara no texto, a trava perdeu a razão de existir.
     */
    @Test
    fun comMetaBatidaEValoresMascaradosACorTambemMuda() =
        assertEquals(
            true,
            pronto.copy(mostrarValores = false, taxaGuardada = 22, metaGuardarPercent = 20).metaBatida,
        )

    @Test
    fun semMetaACorNuncaMuda() =
        assertEquals(
            false,
            pronto.copy(mostrarValores = true, taxaGuardada = 99, metaGuardarPercent = 0).metaBatida,
        )

    @Test
    fun abaixoDaMetaACorNaoMuda() =
        assertEquals(
            false,
            pronto.copy(mostrarValores = true, taxaGuardada = 19, metaGuardarPercent = 20).metaBatida,
        )

    /** Exatamente na meta já é bater: `>=`, não `>`. */
    @Test
    fun exatamenteNaMetaJaBateu() =
        assertEquals(
            true,
            pronto.copy(mostrarValores = true, taxaGuardada = 20, metaGuardarPercent = 20).metaBatida,
        )

    @Test
    fun semTaxaACorNaoMuda() =
        assertEquals(
            false,
            pronto.copy(mostrarValores = true, taxaGuardada = null, metaGuardarPercent = 20).metaBatida,
        )

    /** E o texto continua o mesmo, batendo a meta ou não. */
    @Test
    fun aMetaNaoMudaOTextoMascarado() = runGlanceAppWidgetUnitTest {
        setAppWidgetSize(SaldoWidget.LARGO)
        provideComposable { SaldoWidgetContent(pronto.copy(taxaGuardada = 22, metaGuardarPercent = 20)) }
        onNode(hasTestTag(TAG_WIDGET_GUARDADO)).assertHasText("guardou 22%")
    }

    @Test
    fun aMetaNaoMudaOTextoRevelado() = runGlanceAppWidgetUnitTest {
        setAppWidgetSize(SaldoWidget.LARGO)
        provideComposable {
            SaldoWidgetContent(pronto.copy(mostrarValores = true, taxaGuardada = 22, metaGuardarPercent = 20))
        }
        onNode(hasTestTag(TAG_WIDGET_GUARDADO)).assertHasText("guardou 22%")
    }

    /** Pina os extras que o widget manda contra o que `MainActivity`/`Destino.de` esperam ler. */
    @Test
    fun paraParametrosLevaOsExtrasQueMainActivityEspera() {
        val doSaldos = Destino.Saldos(YearMonth.of(2026, 8), 5).paraParametros().asMap().mapKeys { it.key.name }
        assertEquals(
            mapOf("destino" to "saldos", "anoMes" to YearMonth.of(2026, 8).toAnoMes(), "dia" to 5),
            doSaldos,
        )

        val daNova = Destino.NovaMovimentacao().paraParametros().asMap().mapKeys { it.key.name }
        assertEquals(mapOf("destino" to "nova"), daNova)
    }
}
