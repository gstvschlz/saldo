package com.scholze.saldo.ui.entry

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** O que prende o "salvar" da sheet: o valor, e só ele. */
class EntryUiStateTest {

    @Test
    fun `salva sem descricao nenhuma`() {
        assertTrue(EntryUiState(centavos = 1_990, descricao = "").podeSalvar)
    }

    @Test
    fun `salva com descricao so de espacos`() {
        assertTrue(EntryUiState(centavos = 1_990, descricao = "   ").podeSalvar)
    }

    @Test
    fun `sem valor nao salva, com ou sem descricao`() {
        assertFalse(EntryUiState(centavos = 0, descricao = "mercado").podeSalvar)
        assertFalse(EntryUiState(centavos = 0, descricao = "").podeSalvar)
    }
}
