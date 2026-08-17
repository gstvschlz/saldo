package com.scholze.saldo.ui.totais.charts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChartMathTest {

    @Test
    fun largurasAplicamPisoERenormalizam() {
        val l = ChartMath.larguras(listOf(0.5f, 0.49f, 0.01f))
        assertEquals(0.02f, l[2], 1e-6f)          // a fatia de 1 % sobe para o piso
        assertEquals(1f, l.sum(), 1e-4f)           // e as outras encolhem para fechar em 1
        assertTrue(l[0] > l[1])
    }

    @Test
    fun largurasSemFatiasPequenasSaoAsProprias() {
        val l = ChartMath.larguras(listOf(0.6f, 0.4f))
        assertEquals(0.6f, l[0], 1e-6f)
        assertEquals(0.4f, l[1], 1e-6f)
    }

    @Test
    fun largurasZeroFicamZero() {
        assertEquals(listOf(0f, 0f), ChartMath.larguras(listOf(0f, 0f)))
        val l = ChartMath.larguras(listOf(0.7f, 0f, 0.3f))
        assertEquals(0f, l[1], 0f)
        assertEquals(1f, l.sum(), 1e-4f)
    }

    @Test
    fun alturasRelativasAoMaiorValorAbsoluto() {
        assertEquals(listOf(1f, 0.5f, 0f), ChartMath.alturas(listOf(200L, 100L, 0L)))
        assertEquals(listOf(1f, 0.5f), ChartMath.alturas(listOf(-200L, 100L)))
        assertEquals(listOf(0f, 0f), ChartMath.alturas(listOf(0L, 0L)))
    }

    @Test
    fun linhaNormalizaEntreMinimoEMaximo() {
        assertEquals(listOf(0f, 0.5f, 1f), ChartMath.linha(listOf(0L, 50L, 100L)))
        assertEquals(listOf(0.5f, 0.5f), ChartMath.linha(listOf(7L, 7L)))
        assertTrue(ChartMath.linha(emptyList()).isEmpty())
    }
}
