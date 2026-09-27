package com.scholze.saldo.widget

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.scholze.saldo.domain.Board
import com.scholze.saldo.domain.DiaBoard
import com.scholze.saldo.domain.Ritmo
import com.scholze.saldo.domain.Teto
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** As partes puras dos sete widgets: o formato pelo tamanho e o que cada um tira dos motores. */
class WidgetsTest {

    private val setembro = YearMonth.of(2026, 9)

    @Test
    fun oFormatoSaiDoTamanhoDeVerdade() {
        assertEquals(Formato.MINI, formatoDe(DpSize(70.dp, 90.dp)))
        assertEquals(Formato.LINHA, formatoDe(DpSize(250.dp, 90.dp)))
        assertEquals(Formato.QUADRADO, formatoDe(DpSize(150.dp, 180.dp)))
        assertEquals(Formato.QUADRADO, formatoDe(DpSize(70.dp, 300.dp)))
        assertEquals(Formato.LARGO, formatoDe(DpSize(300.dp, 200.dp)))
        assertEquals(Formato.GRANDE, formatoDe(DpSize(300.dp, 320.dp)))
    }

    @Test
    fun oMesTemOTamanhoDoMesESemanasDeSegundaADomingo() {
        val hoje = LocalDate.of(2026, 9, 5)
        val niveis = listOf(1, -2, 0, 2, 3)
        val dias = niveis.mapIndexed { i, n -> DiaBoard(setembro.atDay(i + 1), 0, n, false, dentroDaJanela = true) }
        val e = mesDoWidget(Board(dias, 100, setembro.atDay(1), hoje), setembro, hoje)

        assertEquals(30, e.dias.size)
        assertEquals(CelulaMes.Dia(3, hoje = true), e.dias[4])
        assertEquals(CelulaMes.Futuro, e.dias[5])
        // 1º de setembro de 2026 é terça: a primeira semana começa com uma ponta vazia.
        assertEquals(CelulaMes.Fora, e.semanas[0][0])
        assertTrue(e.semanas.all { it.size == 7 })
        assertEquals(3, e.verdes)
        assertEquals(1, e.rosas)
        assertEquals(2, e.sequencia)
    }

    @Test
    fun oAnelDoGuardouEncheContraAMeta() {
        val meses = listOf(PoupancaWidgetEstado.Pronto.Ponto(setembro.minusMonths(1), 25), PoupancaWidgetEstado.Pronto.Ponto(setembro, 15))
        val e = PoupancaWidgetEstado.Pronto(meses, meta = 20)
        assertEquals(0.75f, e.fracao, 0.001f)
        assertEquals("faltam 5 pontos", e.legenda)
        assertEquals(1, e.bateram)
        assertEquals("sem meta", PoupancaWidgetEstado.Pronto(meses, meta = 0).legenda)
        assertEquals(0f, PoupancaWidgetEstado.Pronto(listOf(PoupancaWidgetEstado.Pronto.Ponto(setembro, -10)), 20).fracao)
    }

    @Test
    fun oRitmoNormalizaAsDuasCurvasNaMesmaEscala() {
        val e = ritmoDoWidget(Ritmo(setembro, listOf(100, 200, 300), listOf(200, 300, 400), mesesComparados = 3))
        assertEquals(listOf(0.25f, 0.5f, 0.75f), e.mes)
        assertEquals(1f, e.costume.last())
        assertEquals("−25%", e.numero)
        assertEquals("abaixo do costume", e.legenda)
        assertEquals("sem mês anterior", ritmoDoWidget(Ritmo(setembro, listOf(100), emptyList(), 0)).legenda)
    }

    @Test
    fun oHojeDizQuantoCabeEmPorcentoENuncaOValor() {
        assertEquals(60, cabeHoje(Teto(setembro, tetoCentavos = 100_00, gastoDeHojeCentavos = 40_00, diasRestantes = 5, sobraDoMesCentavos = 0)))
        assertEquals(0, cabeHoje(Teto(setembro, 100_00, 150_00, 5, 0)))
        assertEquals(0, cabeHoje(Teto(setembro, -10_00, 0, 5, 0)))
        val e = TetoWidgetEstado.Pronto(setembro, 60, false, false, listOf(true, false, null, true, true))
        assertEquals(2, e.sequencia)
    }

    @Test
    fun oTreemapPoeAMaiorAEsquerdaEORestoEmDuasLinhas() {
        assertEquals(Treemap(0, emptyList(), emptyList()), treemapDe(1))
        assertEquals(Treemap(0, listOf(1), listOf(2)), treemapDe(3))
        assertEquals(Treemap(0, listOf(1, 2), listOf(3, 4, 5)), treemapDe(6))
    }
}
