package com.scholze.saldo.lembretes

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.scholze.saldo.domain.LembretesConfig
import com.scholze.saldo.domain.Slot
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Dois trabalhos únicos, um por [Slot], cada um agendado para a PRÓXIMA ocorrência da sua hora e
 * reagendado pelo próprio worker ao terminar. Inexato de propósito (WorkManager, sem alarme
 * exato nem receiver de boot — o WorkManager sobrevive ao reboot sozinho): um lembrete alguns
 * minutos atrasado sob Doze é aceitável.
 */
class LembretesScheduler(private val context: Context) {

    /** A cada mudança de toggle/hora ([ExistingWorkPolicy.REPLACE]) e no arranque do app ([ExistingWorkPolicy.KEEP]). */
    fun agendar(
        config: LembretesConfig,
        politica: ExistingWorkPolicy = ExistingWorkPolicy.REPLACE,
        agora: LocalDateTime = LocalDateTime.now(),
    ) {
        for (slot in Slot.entries) {
            val (ligado, hora) = config.doSlot(slot)
            agendarSlot(slot, ligado, hora, politica, agora)
        }
    }

    /**
     * O worker chama ao terminar: a ocorrência de hoje acabou de rodar, então a próxima é amanhã.
     * APPEND_OR_REPLACE, não REPLACE: isto roda de DENTRO do próprio unique work, então REPLACE
     * cancelaria o trabalho em execução (ele mesmo) antes de inserir o novo.
     */
    fun reagendar(slot: Slot, config: LembretesConfig, agora: LocalDateTime = LocalDateTime.now()) {
        val (ligado, hora) = config.doSlot(slot)
        agendarSlot(slot, ligado, hora, ExistingWorkPolicy.APPEND_OR_REPLACE, agora)
    }

    /** O "apagar dados": nenhum slot volta a disparar até alguém religar um lembrete. */
    fun cancelarTudo() {
        val wm = WorkManager.getInstance(context)
        Slot.entries.forEach { wm.cancelUniqueWork(nome(it)) }
    }

    private fun agendarSlot(slot: Slot, ligado: Boolean, hora: LocalTime, politica: ExistingWorkPolicy, agora: LocalDateTime) {
        val wm = WorkManager.getInstance(context)
        if (!ligado) {
            wm.cancelUniqueWork(nome(slot))
            return
        }
        val pedido = OneTimeWorkRequestBuilder<LembretesWorker>()
            .setInitialDelay(proximaOcorrencia(agora, hora))
            .setInputData(workDataOf(LembretesWorker.CHAVE_SLOT to slot.name))
            .build()
        wm.enqueueUniqueWork(nome(slot), politica, pedido)
    }

    companion object {
        fun nome(slot: Slot): String = "lembretes-" + slot.name.lowercase()

        /**
         * Quanto falta até a próxima [hora]: hoje, se ainda não passou; senão amanhã. Exatamente
         * na hora conta como agora. Relógio de parede ([LocalDateTime], não instante/UTC): num
         * dia de troca de horário de verão o disparo pode errar por até uma hora — aceito, o
         * Brasil não tem horário de verão desde 2019 e o agendamento já é inexato de propósito.
         */
        fun proximaOcorrencia(agora: LocalDateTime, hora: LocalTime): Duration {
            val hoje = agora.toLocalDate().atTime(hora)
            val proxima = if (hoje.isBefore(agora)) hoje.plusDays(1) else hoje
            return Duration.between(agora, proxima)
        }
    }
}

private fun LembretesConfig.doSlot(slot: Slot): Pair<Boolean, LocalTime> = when (slot) {
    Slot.INFORMATIVOS -> algumInformativo to horaInformativos
    Slot.NUDGE -> registrarGastos to horaNudge
}
