package com.scholze.saldo.domain

import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RecurrenceExpanderTest {
    private val salario = Recorrencia(
        id = 7, descricao = "salário", valorCentavos = 8_240_00,
        natureza = Natureza.DIARIO, diaDoMes = 15, inicio = YearMonth.of(2026, 1),
    )

    @Test
    fun expandeNoDiaCerto() {
        val occ = RecurrenceExpander.ocorrenciaNoMes(salario, YearMonth.of(2026, 7))!!
        assertEquals(LocalDate.parse("2026-07-15"), occ.data)
        assertEquals(7L, occ.recorrenciaId)
        assertEquals(8_240_00L, occ.valorCentavos)
        assertEquals(0L, occ.id)
    }

    @Test
    fun dia31ClampaParaFimDoMesCurto() {
        val t = salario.copy(diaDoMes = 31)
        assertEquals(LocalDate.parse("2026-02-28"), RecurrenceExpander.ocorrenciaNoMes(t, YearMonth.of(2026, 2))!!.data)
    }

    /** `inicio` é inclusivo: o primeiro mês da recorrência já expande. */
    @Test
    fun expandeNoProprioMesDeInicio() {
        assertEquals(
            LocalDate.parse("2026-01-15"),
            RecurrenceExpander.ocorrenciaNoMes(salario, YearMonth.of(2026, 1))!!.data,
        )
    }

    @Test
    fun foraDoIntervaloNaoExpande() {
        assertNull(RecurrenceExpander.ocorrenciaNoMes(salario, YearMonth.of(2025, 12)))
        val comFim = salario.copy(fim = YearMonth.of(2026, 6))
        assertNull(RecurrenceExpander.ocorrenciaNoMes(comFim, YearMonth.of(2026, 7)))
        // fim inclusive:
        assertEquals(
            LocalDate.parse("2026-06-15"),
            RecurrenceExpander.ocorrenciaNoMes(comFim, YearMonth.of(2026, 6))!!.data,
        )
    }

    @Test
    fun inativaNaoExpande() {
        assertNull(RecurrenceExpander.ocorrenciaNoMes(salario.copy(ativa = false), YearMonth.of(2026, 7)))
    }

    @Test
    fun expandeListaOrdenadaPorDia() {
        val aluguel = salario.copy(id = 8, descricao = "aluguel", valorCentavos = -2_400_00, diaDoMes = 3)
        val occs = RecurrenceExpander.ocorrenciasNoMes(listOf(salario, aluguel), YearMonth.of(2026, 7))
        assertEquals(listOf("aluguel", "salário"), occs.map { it.descricao })
    }
}
