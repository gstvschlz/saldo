package com.scholze.saldo.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the seed, not every hex: a token sheet is a design document, and asserting
 * all 20 values here would just be the file typed twice. What is pinned is what a
 * careless edit would silently break — the three new roles existing in both
 * schemes, the heat ramp keeping its direction, and no HIG blue surviving.
 */
class SaldoColorsTest {

    @Test
    fun aSementeVerdeSubstituiOAzulDoHig() {
        assertNotEquals(Color(0xFF007AFF), LightSaldoColors.tint)
        assertNotEquals(Color(0xFF0A84FF), DarkSaldoColors.tint)
        assertEquals(Color(0xFF2F6A45), LightSaldoColors.tint)
        assertEquals(Color(0xFF99D5AC), DarkSaldoColors.tint)
    }

    @Test
    fun osTresPapeisNovosExistemNosDoisEsquemas() {
        assertEquals(Color(0xFFB4F1C7), LightSaldoColors.primaryContainer)
        assertEquals(Color(0xFF00210F), LightSaldoColors.onPrimaryContainer)
        assertEquals(Color(0xFFD6E8D8), LightSaldoColors.secondaryContainer)
        assertEquals(Color(0xFF1E5133), DarkSaldoColors.primaryContainer)
        assertEquals(Color(0xFFB4F1C7), DarkSaldoColors.onPrimaryContainer)
        assertEquals(Color(0xFF33463A), DarkSaldoColors.secondaryContainer)
    }

    /**
     * Mais saldo, mais verde — nos DOIS esquemas, e nos três degraus.
     *
     * Não se mede isso por luminância: no claro a rampa vai de um verde pálido a um
     * verde saturado, que é mais ESCURO (.923 → .877 → .882), e nem sequer é monótona;
     * no escuro ela clareia (.156 → .209 → .267). O que as duas rampas têm em comum é
     * o verdor — o quanto o canal verde se destaca da média de vermelho e azul. Uma
     * rampa invertida, ou um degrau fora de ordem, quebra aqui.
     */
    @Test
    fun aRampaDeCalorGanhaVerdeNosDoisEsquemas() {
        for (cores in listOf(LightSaldoColors, DarkSaldoColors)) {
            val rampa = listOf(cores.balanceTint1, cores.balanceTint2, cores.balanceTint3)
            rampa.map(::verdor).zipWithNext { menor, maior ->
                assertTrue("rampa fora de ordem em isDark=${cores.isDark}: $menor >= $maior", menor < maior)
            }
        }
    }

    @Test
    fun oEsquemaEscuroNaoEPretoPuro() {
        assertNotEquals(Color(0xFF000000), DarkSaldoColors.background)
        assertEquals(Color(0xFF101410), DarkSaldoColors.background)
    }

    @Test
    fun isDarkContinuaCoerente() {
        assertEquals(false, LightSaldoColors.isDark)
        assertEquals(true, DarkSaldoColors.isDark)
    }

    /** O quanto o verde se destaca do vermelho e do azul. */
    private fun verdor(c: Color): Float = c.green - (c.red + c.blue) / 2f

    @Test
    fun tomDoBoardCobreOsSeteNiveisNosDoisEsquemas() {
        for (cores in listOf(LightSaldoColors, DarkSaldoColors)) {
            val tons = (-3..3).map { cores.tomDoBoard(it) }
            assertEquals("sete níveis, sete tons distintos", 7, tons.toSet().size)
            assertEquals(cores.boardZero, cores.tomDoBoard(0))
            assertEquals(cores.boardNeg3, cores.tomDoBoard(-3))
            assertEquals(cores.boardPos3, cores.tomDoBoard(3))
        }
    }

    /** O motor promete −3..3, mas um nível fora da faixa não pode virar crash. */
    @Test
    fun tomDoBoardSaturaEmVezDeEstourar() {
        assertEquals(LightSaldoColors.boardNeg3, LightSaldoColors.tomDoBoard(-9))
        assertEquals(LightSaldoColors.boardPos3, LightSaldoColors.tomDoBoard(9))
    }

    /** Mesma lógica da rampa de calor: mais gasto, mais rosa — nos dois esquemas. */
    @Test
    fun aRampaRosaGanhaRosaNosDoisEsquemas() {
        for (cores in listOf(LightSaldoColors, DarkSaldoColors)) {
            val rampa = listOf(cores.boardNeg1, cores.boardNeg2, cores.boardNeg3)
            rampa.map(::rosidade).zipWithNext { menor, maior ->
                assertTrue("rampa rosa fora de ordem em isDark=${cores.isDark}", menor < maior)
            }
        }
    }

    /** O quanto o vermelho se destaca do verde. */
    private fun rosidade(c: Color): Float = c.red - c.green

    /**
     * A pill do hero INVERTE quando a meta bate: fundo `onPrimaryContainer`, texto
     * `primaryContainer`. São os dois tokens do próprio cartão, trocados de lado — então o que
     * precisa ser verdade é que o par se lê nos dois sentidos, nos dois temas. Pintar de verde o
     * que já está sobre um cartão verde não mudaria nada; a inversão é diferença de LUMINÂNCIA,
     * que sobrevive ao daltonismo.
     */
    @Test
    fun aPillInvertidaSeLeNosDoisTemas() {
        for (cores in listOf(LightSaldoColors, DarkSaldoColors)) {
            val razao = contraste(cores.onPrimaryContainer, cores.primaryContainer)
            assertTrue("contraste ${"%.2f".format(razao)}:1 em isDark=${cores.isDark}", razao >= 4.5)
        }
    }

    /** Contraste WCAG entre duas cores opacas; simétrico, então serve para os dois sentidos. */
    private fun contraste(a: Color, b: Color): Double {
        val la = luminancia(a)
        val lb = luminancia(b)
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    private fun luminancia(c: Color): Double {
        fun canal(v: Float): Double {
            val d = v.toDouble()
            return if (d <= 0.03928) d / 12.92 else Math.pow((d + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * canal(c.red) + 0.7152 * canal(c.green) + 0.0722 * canal(c.blue)
    }
}
