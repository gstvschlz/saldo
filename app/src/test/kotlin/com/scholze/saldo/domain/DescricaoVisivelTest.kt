package com.scholze.saldo.domain

import org.junit.Assert.assertEquals
import org.junit.Test

/** Como uma descrição em branco se lê, agora que a descrição é opcional. */
class DescricaoVisivelTest {

    @Test
    fun `descricao em branco vira sem descricao`() {
        assertEquals(SEM_DESCRICAO, "".descricaoVisivel())
    }

    @Test
    fun `descricao so de espacos tambem`() {
        assertEquals(SEM_DESCRICAO, "   ".descricaoVisivel())
    }

    @Test
    fun `descricao escrita passa intacta`() {
        assertEquals("mercado", "mercado".descricaoVisivel())
    }
}
