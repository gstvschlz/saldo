package com.scholze.saldo.data

import java.time.LocalDate
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DiaAtualTest {

    /**
     * O ledger recebe `hoje` por este fluxo. Ele emite na hora e depois só quando o dia vira —
     * checando o relógio a cada minuto, e não dormindo até a meia-noite: um `delay` longo não
     * conta o tempo em que o aparelho dormiu, e acordaria horas depois da virada.
     */
    @Test
    fun emiteHojeNaHoraEDeNovoSoQuandoODiaVira() = runTest {
        var agora = LocalDate.parse("2026-07-20")
        val emitidos = mutableListOf<LocalDate>()
        val coleta = launch { diaAtual(relogio = { agora }, intervalo = 1.minutes).collect { emitidos += it } }
        runCurrent()
        assertEquals(listOf("2026-07-20"), emitidos.map { it.toString() })

        advanceTimeBy(30.minutes)
        runCurrent()
        assertEquals("mesmo dia: nada de novo", 1, emitidos.size)

        agora = LocalDate.parse("2026-07-21")
        advanceTimeBy(1.minutes)
        runCurrent()
        assertEquals(listOf("2026-07-20", "2026-07-21"), emitidos.map { it.toString() })
        coleta.cancel()
    }
}
