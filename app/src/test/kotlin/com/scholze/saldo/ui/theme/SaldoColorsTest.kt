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
}
