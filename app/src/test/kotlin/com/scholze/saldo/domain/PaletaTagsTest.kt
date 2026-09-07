package com.scholze.saldo.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class PaletaTagsTest {

    @Test fun `sao seis cores`() = assertEquals(6, PaletaTags.cores.size)

    @Test fun `sem tag nenhuma a primeira cor e a primeira da paleta`() =
        assertEquals(PaletaTags.cores[0], PaletaTags.proxima(emptyList()))

    @Test fun `a proxima e a menos usada`() {
        val c = PaletaTags.cores
        // as cores 0 e 1 já foram usadas; 2 é a primeira livre
        assertEquals(c[2], PaletaTags.proxima(listOf(c[0], c[1])))
    }

    @Test fun `com todas usadas uma vez volta a primeira`() =
        assertEquals(PaletaTags.cores[0], PaletaTags.proxima(PaletaTags.cores))

    @Test fun `desempate pela ordem da paleta`() {
        val c = PaletaTags.cores
        // 0 usada duas vezes, 1..5 uma vez cada: todas as cinco empatam, e a 1 vem antes
        assertEquals(c[1], PaletaTags.proxima(listOf(c[0], c[0]) + c.drop(1)))
    }

    @Test fun `cor fora da paleta nao conta`() =
        assertEquals(PaletaTags.cores[0], PaletaTags.proxima(listOf(0xFF000000L)))
}
