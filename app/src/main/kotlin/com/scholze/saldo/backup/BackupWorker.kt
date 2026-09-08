package com.scholze.saldo.backup

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/**
 * Esqueleto: só o suficiente para o [BackupScheduler] ter o que enfileirar. O corpo — monta o
 * JSON, grava o `.parcial`, renomeia, rotaciona, grava o estado e reagenda — vem na próxima task.
 */
class BackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = Result.success()
}
