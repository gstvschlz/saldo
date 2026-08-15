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
    fun zeroNaoLevaSinal() {
        // A tela de totais mostra "saídas economia" e "entradas" no formato assinado; um
        // "+0,00" numa linha de saída lê como ganho.
        assertEquals("0,00", 0L.centavosAssinado())
        assertEquals("R$ 0,00", 0L.centavosAssinadoComSimbolo())
    }

    @Test
    fun assinadoMarcaOsDoisSentidos() {
        assertEquals("−238,50", (-23850L).centavosAssinado())
        assertEquals("+238,50", 23850L.centavosAssinado())
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
