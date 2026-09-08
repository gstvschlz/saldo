package com.scholze.saldo.backup

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.scholze.saldo.domain.BackupConfig
import com.scholze.saldo.domain.Cadencia
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Um trabalho único, agendado para a próxima ocorrência da cadência e reagendado pelo próprio
 * worker ao terminar — o mesmo molde do `LembretesScheduler`. Inexato de propósito (WorkManager,
 * sem alarme exato nem receiver de boot: o WorkManager sobrevive ao reboot sozinho); um backup que
 * roda às 03:40 em vez de 03:00 sob Doze está ótimo.
 */
class BackupScheduler(private val context: Context) {

    /** A cada mudança de pasta/cadência ([ExistingWorkPolicy.REPLACE]) e no arranque ([ExistingWorkPolicy.KEEP]). */
    fun agendar(
        config: BackupConfig,
        politica: ExistingWorkPolicy = ExistingWorkPolicy.REPLACE,
        agora: LocalDateTime = LocalDateTime.now(),
    ) {
        val wm = WorkManager.getInstance(context)
        // Sem pasta escolhida não há o que gravar: nada fica enfileirado, e o que existia sai.
        if (!config.ligado) {
            wm.cancelUniqueWork(NOME)
            return
        }
        val pedido = OneTimeWorkRequestBuilder<BackupWorker>()
            .setInitialDelay(proximaOcorrencia(agora, config.cadencia))
            .build()
        wm.enqueueUniqueWork(NOME, politica, pedido)
    }

    /**
     * O worker chama ao terminar. APPEND_OR_REPLACE, não REPLACE: isto roda de DENTRO do próprio
     * unique work, e REPLACE cancelaria o trabalho em execução — ele mesmo — antes de inserir o novo.
     */
    fun reagendar(config: BackupConfig, agora: LocalDateTime = LocalDateTime.now()) =
        agendar(config, ExistingWorkPolicy.APPEND_OR_REPLACE, agora)

    fun cancelar() {
        WorkManager.getInstance(context).cancelUniqueWork(NOME)
    }

    /**
     * O botão "agora" da tela: grava já.
     *
     * Trabalho único SEPARADO do agendado, de propósito. No mesmo nome, `REPLACE` mataria o
     * agendamento das 03:00 e `APPEND` faria o "agora" esperar até lá — nas duas leituras o botão
     * mentiria. `KEEP` aqui só evita dois disparos de um toque duplo.
     *
     * E vai marcado como manual: sendo um trabalho de OUTRO nome, o [reagendar] do fim da rodada
     * não substituiria a cadeia de [NOME] — empilharia um nó nela a cada toque. Uma rodada extra
     * não muda o ritmo da cadência.
     */
    fun agora() {
        WorkManager.getInstance(context).enqueueUniqueWork(
            NOME_AGORA,
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<BackupWorker>()
                .setInputData(workDataOf(BackupWorker.CHAVE_MANUAL to true))
                .build(),
        )
    }

    companion object {
        const val NOME = "backup-automatico"
        const val NOME_AGORA = "backup-agora"

        /** 03:00: o aparelho está parado, carregando, e ninguém está usando o app. */
        val HORA: LocalTime = LocalTime.of(3, 0)

        /**
         * Quanto falta até a próxima gravação: o próximo 03:00 (hoje, se ainda não passou; senão
         * amanhã), adiantado pela cadência.
         *
         * Ligar o backup semanal às 10:00 significa esperar uma semana pelo primeiro arquivo — é
         * exatamente para isso que a tela tem o botão "agora", e por isso a regra pode ser esta,
         * uniforme e fácil de conferir, em vez de um "quando calha" cheio de casos.
         *
         * Relógio de parede ([LocalDateTime], não instante/UTC): num dia de troca de horário de
         * verão o disparo pode errar por até uma hora — aceito, como nos lembretes.
         */
        fun proximaOcorrencia(agora: LocalDateTime, cadencia: Cadencia): Duration {
            val hojeNaHora = agora.toLocalDate().atTime(HORA)
            val proximoTresDaManha = if (hojeNaHora.isBefore(agora)) hojeNaHora.plusDays(1) else hojeNaHora
            val proxima = when (cadencia) {
                Cadencia.DIARIO -> proximoTresDaManha
                Cadencia.SEMANAL -> proximoTresDaManha.plusWeeks(1)
                // `plusMonths` do java.time clampa sozinho: 31/01 + 1 mês = 28/02.
                Cadencia.MENSAL -> proximoTresDaManha.plusMonths(1)
            }
            return Duration.between(agora, proxima)
        }
    }
}
