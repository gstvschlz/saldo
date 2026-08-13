package com.scholze.saldo.model

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pins the exact glyphs the design asks for: U+2212 MINUS SIGN (not a hyphen),
 * and no "+" on the unsigned symbol form.
 */
class MoneyTest {

    @Test
    fun comSimboloUsaMinusSignENaoTemMais() {
        assertEquals("−R$ 238,50", (-23850L).centavosComSimbolo())
        assertEquals("R$ 238,50", 23850L.centavosComSimbolo())
    }

    @Test
    fun assinadoTrataZeroComoPositivo() {
        assertEquals("+0,00", 0L.centavosAssinado())
    }

    @Test
    fun valorEMagnitude() {
        assertEquals("238,50", 23850L.centavosValor())
        assertEquals("238,50", (-23850L).centavosValor())
    }

    @Test
    fun assinadoComSimbolo() {
        assertEquals("−R$ 238,50", (-23850L).centavosAssinadoComSimbolo())
        assertEquals("+R$ 238,50", 23850L.centavosAssinadoComSimbolo())
    }
}
