package com.scholze.saldo.lembretes

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.scholze.saldo.SaldoApplication
import com.scholze.saldo.domain.LembretesEngine
import com.scholze.saldo.domain.Slot
import com.scholze.saldo.widget.atualizarTodos
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.first

/**
 * A rodada diária de um [Slot]: lê os lembretes (`SettingsStore.lerLembretes`, que NÃO engole
 * `IOException`) + o ledger, pergunta ao [LembretesEngine] o que sai e sincroniza as
 * notificações do slot com o resultado (`Notificacoes.sincronizar` cancela o que sumiu antes de
 * mostrar o que ficou). Duas categorias de falha, dois resultados:
 * - settings ilegíveis (DataStore) é infraestrutura, não lógica de lembrete: `retry`, SEM
 *   reagendar. Reagendar aqui usaria os toggles default (todos desligados) e cancelaria o loop
 *   diário do slot até o próximo cold start — o retry do próprio WorkManager (backoff
 *   exponencial, ≥30s) já mantém o unique work vivo e tenta de novo assim que o settings voltar
 *   a ser legível.
 * - qualquer outra falha (ledger, engine, notificação) é `success` — um lembrete perdido não
 *   vale uma tempestade de retries — e se reagenda para amanhã no `finally`, com os toggles
 *   reais lidos do settings.
 * Se o próprio WorkManager o parou (toggle desligado, `cancelUniqueWork`), não reinsere:
 * `isStopped`.
 */
class LembretesWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val slot = inputData.getString(CHAVE_SLOT)?.let { nome -> Slot.entries.firstOrNull { it.name == nome } }
            ?: return Result.failure()
        val container = (applicationContext as SaldoApplication).container
        val config = try {
            container.settings.lerLembretes()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "lembretes($slot): settings ilegíveis", e)
            return Result.retry()
        }
        try {
            val input = container.repository.ledger.first()
            Notificacoes.sincronizar(applicationContext, slot, LembretesEngine.avaliar(input, config, slot))
            // De graça: os widgets acordam junto com o lembrete, mesmo com o processo morto o
            // resto do tempo. Os OITO, não só o de saldo — era o que esta linha fazia antes.
            atualizarTodos(applicationContext)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "lembretes($slot) falharam", e)
        } finally {
            if (!isStopped) {
                try {
                    container.lembretesScheduler.reagendar(slot, config)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "lembretes($slot): reagendar falhou", e)
                }
            }
        }
        return Result.success()
    }

    companion object {
        const val CHAVE_SLOT = "slot"
        private const val TAG = "saldo"
    }
}
