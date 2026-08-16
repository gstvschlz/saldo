package com.scholze.saldo.ui.money

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

    /**
     * `abs(Long.MIN_VALUE)` é o próprio `Long.MIN_VALUE`: a magnitude não cabe num Long. O
     * teclado limita a entrada muito antes disso, mas um export/JSON importado ou uma soma
     * de agregados não passa pelo teclado — o formato tem de continuar honesto na borda.
     */
    @Test
    fun magnitudeDoMinimoNaoViraLixo() {
        assertEquals("92.233.720.368.547.758,08", Long.MIN_VALUE.centavosValor())
        assertEquals("−R$ 92.233.720.368.547.758,08", Long.MIN_VALUE.centavosComSimbolo())
    }

    @Test
    fun assinadoComSimbolo() {
        assertEquals("−R$ 238,50", (-23850L).centavosAssinadoComSimbolo())
        assertEquals("+R$ 238,50", 23850L.centavosAssinadoComSimbolo())
    }
}
