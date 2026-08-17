package com.scholze.saldo.lembretes

import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Test

class LembretesSchedulerTest {
    private val nove = LocalTime.of(9, 0)

    @Test
    fun antesDaHoraEHojeMesmo() =
        assertEquals(Duration.ofMinutes(30), LembretesScheduler.proximaOcorrencia(LocalDateTime.parse("2026-07-20T08:30"), nove))

    @Test
    fun depoisDaHoraEAmanha() =
        assertEquals(Duration.ofHours(23), LembretesScheduler.proximaOcorrencia(LocalDateTime.parse("2026-07-20T10:00"), nove))

    /** O worker se reagenda logo depois de rodar: um segundo depois da hora já é amanhã. */
    @Test
    fun umSegundoDepoisDaHoraEAmanha() =
        assertEquals(
            Duration.ofDays(1).minusSeconds(1),
            LembretesScheduler.proximaOcorrencia(LocalDateTime.parse("2026-07-20T09:00:01"), nove),
        )

    @Test
    fun naHoraExataEAgora() =
        assertEquals(Duration.ZERO, LembretesScheduler.proximaOcorrencia(LocalDateTime.parse("2026-07-20T09:00"), nove))
}
