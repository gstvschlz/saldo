package com.scholze.saldo.backup

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RotacaoTest {

    private val nove = (1..9).map { "saldo-2026-09-%02d.json".format(it) }
    private val estranhos = listOf("planilha.xlsx", "saldo-antigo.json")

    @Test
    fun oNomeDoDiaEODoParcial() {
        assertEquals("saldo-2026-09-08.json", NomeBackup.de(LocalDate.parse("2026-09-08")))
        assertEquals("saldo-2026-09-08.json.parcial", NomeBackup.parcial(LocalDate.parse("2026-09-08")))
    }

    @Test
    fun soOPadraoExatoConta() {
        assertTrue(NomeBackup.ehDoPadrao("saldo-2026-09-08.json"))
        assertFalse(NomeBackup.ehDoPadrao("saldo-2026-09-08.json.parcial"))
        assertFalse(NomeBackup.ehDoPadrao("saldo-antigo.json"))
        assertFalse(NomeBackup.ehDoPadrao("Saldo-2026-09-08.json"))
        assertFalse(NomeBackup.ehDoPadrao("planilha.xlsx"))
    }

    @Test
    fun sobramOsSeteMaisRecentesEOsEstranhosNaoSaoTocados() {
        // ordem embaralhada de propósito: a rotação não pode depender de a pasta vir ordenada
        val naPasta = (nove + estranhos).shuffled(kotlin.random.Random(7))
        val aApagar = NomeBackup.aApagar(naPasta)
        assertEquals(listOf("saldo-2026-09-01.json", "saldo-2026-09-02.json"), aApagar.sorted())
        estranhos.forEach { assertFalse("$it não é nosso", it in aApagar) }
    }

    @Test
    fun comSeteOuMenosNadaEApagado() {
        assertEquals(emptyList<String>(), NomeBackup.aApagar(nove.take(7) + estranhos))
        assertEquals(emptyList<String>(), NomeBackup.aApagar(estranhos))
        assertEquals(emptyList<String>(), NomeBackup.aApagar(emptyList()))
    }

    /** A ordem é a do NOME, que em ISO é a do calendário — e atravessa a virada do ano. */
    @Test
    fun aOrdemAtravessaOAno() {
        val nomes = listOf("2025-12-30", "2025-12-31", "2026-01-01").map { "saldo-$it.json" }
        assertEquals(listOf("saldo-2025-12-30.json"), NomeBackup.aApagar(nomes, manter = 2))
    }
}
