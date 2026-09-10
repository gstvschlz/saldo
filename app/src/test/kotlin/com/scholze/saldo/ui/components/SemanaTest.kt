package com.scholze.saldo.ui.components

import java.time.DayOfWeek
import org.junit.Assert.assertEquals
import org.junit.Test

/** Sete rótulos, segunda primeiro, e o acento onde ele existe. */
class SemanaTest {

    @Test
    fun saoSeteRotulosDeSegundaADomingo() =
        assertEquals(listOf("seg", "ter", "qua", "qui", "sex", "sáb", "dom"), Semana.CURTOS)

    @Test
    fun cadaDiaCaiNoSeuRotulo() {
        assertEquals("seg", Semana.curto(DayOfWeek.MONDAY))
        assertEquals("qui", Semana.curto(DayOfWeek.THURSDAY))
        assertEquals("dom", Semana.curto(DayOfWeek.SUNDAY))
    }

    /** `sáb` com acento: o rótulo é uma palavra curta, não três letras quaisquer. */
    @Test
    fun sabadoTemAcento() = assertEquals("sáb", Semana.curto(DayOfWeek.SATURDAY))

    /** Terça e quinta deixaram de ser a mesma letra — era o ponto de trocar `s t q q s s d`. */
    @Test
    fun osSeteRotulosSaoDistintos() = assertEquals(7, Semana.CURTOS.toSet().size)
}
