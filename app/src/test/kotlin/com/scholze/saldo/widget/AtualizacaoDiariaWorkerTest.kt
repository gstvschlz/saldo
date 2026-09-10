package com.scholze.saldo.widget

import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Test

/** A conta do atraso, que é a única parte pura do worker da meia-noite. */
class AtualizacaoDiariaWorkerTest {

    private val cincoDepoisDaMeiaNoite = LocalTime.of(0, 5)

    private fun atraso(agora: String, hora: LocalTime = cincoDepoisDaMeiaNoite) =
        AtualizacaoDiariaWorker.atrasoAte(LocalDateTime.parse(agora), hora)

    @Test
    fun deVinteETresEMeiaFaltamTrintaECincoMinutos() =
        assertEquals(Duration.ofMinutes(35), atraso("2026-09-10T23:30"))

    @Test
    fun deUmMinutoDepoisDaMeiaNoiteFaltamQuatro() =
        assertEquals(Duration.ofMinutes(4), atraso("2026-09-10T00:01"))

    /** Passou da hora: a próxima é amanhã, não daqui a pouco. */
    @Test
    fun dasSeisDaManhaFaltamQuaseUmDia() =
        assertEquals(Duration.ofHours(18).plusMinutes(5), atraso("2026-09-10T06:00"))

    /**
     * Exatamente na hora conta como "já passou": o trabalho desta virada acabou de rodar, e um
     * atraso zero o faria disparar de novo na hora, em laço.
     */
    @Test
    fun exatamenteNaHoraEsperaODiaInteiro() =
        assertEquals(Duration.ofDays(1), atraso("2026-09-10T00:05"))

    /** A virada do mês não é caso especial nenhum — a data faz a conta sozinha. */
    @Test
    fun aViradaDoMesNaoAtrapalha() =
        assertEquals(Duration.ofMinutes(10), atraso("2026-09-30T23:55"))
}
