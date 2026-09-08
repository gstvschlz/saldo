package com.scholze.saldo.backup

import com.scholze.saldo.domain.Cadencia
import java.time.Duration
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A matemática de [BackupScheduler.proximaOcorrencia], pura e sem WorkManager. O que passa pelo
 * WorkManager (o que fica enfileirado, o que é cancelado) tem cobertura em `BackupSchedulerWorkTest`.
 */
class BackupSchedulerTest {

    private fun proxima(agora: String, cadencia: Cadencia) =
        BackupScheduler.proximaOcorrencia(LocalDateTime.parse(agora), cadencia)

    @Test
    fun asTresDaManhaEAHora() = assertEquals(3, BackupScheduler.HORA.hour)

    @Test
    fun diarioAntesDasTresEHojeMesmo() =
        assertEquals(Duration.ofHours(2), proxima("2026-09-07T01:00", Cadencia.DIARIO))

    /** Depois das 03:00 a próxima é amanhã — a conta atravessa a meia-noite. */
    @Test
    fun diarioDepoisDasTresAtravessaAMeiaNoite() =
        assertEquals(Duration.ofHours(3).plusMinutes(30), proxima("2026-09-07T23:30", Cadencia.DIARIO))

    @Test
    fun naHoraExataEAgora() =
        assertEquals(Duration.ZERO, proxima("2026-09-07T03:00", Cadencia.DIARIO))

    @Test
    fun semanalEOProximoTresDaManhaMaisUmaSemana() =
        // próximo 03:00 = 08/09 03:00; mais uma semana = 15/09 03:00
        assertEquals(Duration.ofDays(7).plusHours(17), proxima("2026-09-07T10:00", Cadencia.SEMANAL))

    /** Mensal a partir do dia 31: fevereiro não tem 31, e o `plusMonths` do java.time clampa. */
    @Test
    fun mensalNoDia31CaiNoUltimoDiaDeFevereiro() =
        // próximo 03:00 = 31/01 03:00; mais um mês = 28/02 03:00
        assertEquals(
            Duration.between(LocalDateTime.parse("2026-01-31T01:00"), LocalDateTime.parse("2026-02-28T03:00")),
            proxima("2026-01-31T01:00", Cadencia.MENSAL),
        )
}
