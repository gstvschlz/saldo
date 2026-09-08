package com.scholze.saldo.backup

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.Configuration
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.scholze.saldo.domain.BackupConfig
import com.scholze.saldo.domain.Cadencia
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * `BackupScheduler` contra um `WorkManager` de teste de verdade: o que fica enfileirado e o que é
 * cancelado. A conta da próxima ocorrência já tem cobertura pura em `BackupSchedulerTest` (JVM).
 */
@RunWith(AndroidJUnit4::class)
class BackupSchedulerWorkTest {
    private val ctx = ApplicationProvider.getApplicationContext<Context>()

    @Before
    fun init() {
        WorkManagerTestInitHelper.initializeTestWorkManager(
            ctx, Configuration.Builder().setExecutor(SynchronousExecutor()).build(),
        )
    }

    @After
    fun limpar() {
        WorkManager.getInstance(ctx).cancelAllWork()
    }

    private fun infos() = WorkManager.getInstance(ctx).getWorkInfosForUniqueWork(BackupScheduler.NOME).get()

    /** Sem pasta escolhida não há o que gravar: nada é agendado. */
    @Test
    fun semPastaNadaEAgendado() {
        BackupScheduler(ctx).agendar(BackupConfig(pastaUri = null, cadencia = Cadencia.DIARIO))
        assertTrue(infos().none { !it.state.isFinished })
    }

    @Test
    fun comPastaAgendaOTrabalhoUnico() {
        BackupScheduler(ctx).agendar(BackupConfig(pastaUri = "content://tree/quintal"))
        assertEquals(listOf(WorkInfo.State.ENQUEUED), infos().map { it.state })
    }

    /** Tirar a pasta cancela o agendamento que existia. */
    @Test
    fun tirarAPastaCancela() {
        val s = BackupScheduler(ctx)
        s.agendar(BackupConfig(pastaUri = "content://tree/quintal"))
        s.agendar(BackupConfig(pastaUri = null))
        assertTrue(infos().none { !it.state.isFinished })
    }

    @Test
    fun oNomeDoTrabalhoEODaSpec() = assertEquals("backup-automatico", BackupScheduler.NOME)
}
