package com.scholze.saldo.widget

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException

/**
 * O widget que amanhece certo.
 *
 * Com os lembretes desligados — o padrão — nenhum worker roda, e de madrugada, com o processo
 * morto, o widget continua mostrando o dia de ontem até o `updatePeriodMillis` de 6 h resolver
 * acordar. Um widget errado por dez minutos é outra coisa de um errado por seis horas.
 *
 * Independente dos lembretes de propósito: quem tem widget na tela não precisa ter lembrete
 * ligado. Inexato por natureza (WorkManager sob Doze, sem alarme exato nem receiver de boot — o
 * WorkManager sobrevive ao reboot sozinho), e é justamente por isso que ele roda às 00:05 e não
 * às 00:00: alguns minutos de folga custam nada e evitam disputar a virada com o sistema.
 */
class AtualizacaoDiariaWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        try {
            // Sai na hora se ninguém tem widget: o trabalho existe para redesenhar telas
            // iniciais, e sem nenhuma ele só acordaria o processo à toa.
            if (!haWidgetNaTela(applicationContext)) return Result.success()
            atualizarTodos(applicationContext)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "atualização diária dos widgets falhou", e)
        }
        return Result.success()
    }

    companion object {
        private const val TAG = "saldo"
        const val NOME = "widgets-meia-noite"

        /** Cinco minutos depois da virada — ver o KDoc da classe. */
        private val HORA = LocalTime.of(0, 5)

        /**
         * Enfileira (ou reenfileira) a rodada diária.
         *
         * [ExistingPeriodicWorkPolicy.UPDATE] e não `KEEP`: o atraso inicial é calculado a partir
         * de agora, e com `KEEP` um trabalho enfileirado meses atrás manteria um horário que já
         * derivou. `UPDATE` reaproveita o mesmo id e recalcula.
         */
        fun agendar(context: Context, agora: LocalDateTime = LocalDateTime.now()) {
            val pedido = PeriodicWorkRequestBuilder<AtualizacaoDiariaWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(atrasoAte(agora, HORA))
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(NOME, ExistingPeriodicWorkPolicy.UPDATE, pedido)
        }

        /** Sem widget nenhum na tela não há o que redesenhar de madrugada. */
        fun cancelar(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(NOME)
        }

        /**
         * Quanto falta até a próxima [hora]: hoje, se ainda não passou; senão amanhã.
         *
         * Exatamente na hora conta como "já passou" — o trabalho desta virada acabou de rodar, e
         * o próximo é o de amanhã. Relógio de parede ([LocalDateTime], não instante/UTC), como o
         * `LembretesScheduler`: num dia de troca de horário de verão o disparo pode errar por até
         * uma hora, e isso é aceito num agendamento que já é inexato.
         */
        fun atrasoAte(agora: LocalDateTime, hora: LocalTime): Duration {
            val hoje = agora.toLocalDate().atTime(hora)
            val proxima = if (hoje > agora) hoje else hoje.plusDays(1)
            return Duration.between(agora, proxima)
        }
    }
}
