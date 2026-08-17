package com.scholze.saldo.lembretes

import android.content.Context
import android.util.Log
import androidx.glance.appwidget.updateAll
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.scholze.saldo.SaldoApplication
import com.scholze.saldo.domain.LembretesConfig
import com.scholze.saldo.domain.LembretesEngine
import com.scholze.saldo.domain.Slot
import com.scholze.saldo.widget.SaldoWidget
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.first

/**
 * A rodada diária de um [Slot]: lê ledger + settings, pergunta ao [LembretesEngine] o que sai e
 * posta. Termina em `success` mesmo quando algo falha — um lembrete perdido não vale uma
 * tempestade de retries — e se reagenda para amanhã no `finally`, enquanto os toggles do slot
 * continuarem ligados. Se o próprio WorkManager o parou (toggle desligado, `cancelUniqueWork`),
 * não reinsere: `isStopped`.
 */
class LembretesWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val slot = inputData.getString(CHAVE_SLOT)?.let { nome -> Slot.entries.firstOrNull { it.name == nome } }
            ?: return Result.failure()
        val container = (applicationContext as SaldoApplication).container
        val config = try {
            container.settings.settings.first().lembretes
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "lembretes($slot): settings ilegíveis", e)
            LembretesConfig()
        }
        try {
            val input = container.repository.ledger.first()
            LembretesEngine.avaliar(input, config, slot).forEach { Notificacoes.mostrar(applicationContext, it) }
            // De graça: o widget acorda uma vez por dia mesmo com o processo morto o resto do tempo.
            SaldoWidget().updateAll(applicationContext)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "lembretes($slot) falharam", e)
        } finally {
            if (!isStopped) container.lembretesScheduler.reagendar(slot, config)
        }
        return Result.success()
    }

    companion object {
        const val CHAVE_SLOT = "slot"
        private const val TAG = "saldo"
    }
}
