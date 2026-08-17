package com.scholze.saldo.lembretes

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.Configuration
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.scholze.saldo.domain.LembretesConfig
import com.scholze.saldo.domain.Slot
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * `LembretesScheduler` contra um `WorkManager` de teste de verdade: quais slots ficam
 * agendados/cancelados por [LembretesScheduler.agendar]. A matemática de [LembretesScheduler.proximaOcorrencia]
 * já tem cobertura pura em `LembretesSchedulerTest` (JVM); aqui é só o que passa pelo WorkManager.
 */
@RunWith(AndroidJUnit4::class)
class LembretesSchedulerWorkTest {
    private val ctx = ApplicationProvider.getApplicationContext<Context>()

    @Before
    fun init() {
        WorkManagerTestInitHelper.initializeTestWorkManager(ctx, Configuration.Builder().setExecutor(SynchronousExecutor()).build())
    }

    @After
    fun limpar() {
        WorkManager.getInstance(ctx).cancelAllWork()
    }

    private fun infos(slot: Slot) = WorkManager.getInstance(ctx).getWorkInfosForUniqueWork(LembretesScheduler.nome(slot)).get()

    @Test
    fun agendaSoOsSlotsComToggleLigado() {
        LembretesScheduler(ctx).agendar(LembretesConfig(faturaAmanha = true))
        assertEquals(listOf(WorkInfo.State.ENQUEUED), infos(Slot.INFORMATIVOS).map { it.state })
        assertTrue(infos(Slot.NUDGE).none { !it.state.isFinished })
    }

    @Test
    fun desligarTudoCancelaOsDois() {
        val s = LembretesScheduler(ctx)
        s.agendar(LembretesConfig(faturaAmanha = true, registrarGastos = true))
        s.agendar(LembretesConfig())
        assertTrue(infos(Slot.INFORMATIVOS).none { !it.state.isFinished })
        assertTrue(infos(Slot.NUDGE).none { !it.state.isFinished })
    }

    @Test
    fun nomesDosTrabalhosSaoOsDaSpec() {
        assertEquals("lembretes-informativos", LembretesScheduler.nome(Slot.INFORMATIVOS))
        assertEquals("lembretes-nudge", LembretesScheduler.nome(Slot.NUDGE))
    }
}
