package com.scholze.saldo.lembretes

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import androidx.work.ListenableWorker
import androidx.work.WorkManager
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.workDataOf
import com.scholze.saldo.EstadoLimpo
import com.scholze.saldo.SaldoApplication
import com.scholze.saldo.domain.LembretesConfig
import com.scholze.saldo.domain.Movimentacao
import com.scholze.saldo.domain.Natureza
import com.scholze.saldo.domain.RepetirOpcao
import com.scholze.saldo.domain.Slot
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A rodada de verdade, sobre o container do app: o cenário determinístico é o nudge (não depende
 * do dia do mês nem do cartão). As regras de fatura/recorrência/fechamento vivem nos testes JVM
 * do LembretesEngine.
 */
@RunWith(AndroidJUnit4::class)
class LembretesWorkerTest {

    @get:Rule(order = 0)
    val estadoLimpo = EstadoLimpo()

    @get:Rule(order = 1)
    val permissao: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)

    private val app = ApplicationProvider.getApplicationContext<SaldoApplication>()
    private val nm: NotificationManager get() = app.getSystemService(NotificationManager::class.java)

    @Before
    fun preparar() {
        nm.cancelAll()
        Notificacoes.criarCanal(app)
        runBlocking {
            app.container.settings.definirSaldoInicial(100_000_00, LocalDate.now())
            app.container.settings.definirLembretes(LembretesConfig(registrarGastos = true))
        }
    }

    @After
    fun limpar() {
        nm.cancelAll()
        WorkManager.getInstance(app).cancelAllWork()
    }

    private fun rodar(slot: Slot): ListenableWorker.Result = runBlocking {
        TestListenableWorkerBuilder<LembretesWorker>(app, inputData = workDataOf(LembretesWorker.CHAVE_SLOT to slot.name))
            .build()
            .doWork()
    }

    private fun titulos(): List<String?> {
        // O NotificationManager pode levar um instante para listar o que acabou de ser postado.
        repeat(20) {
            val ativos = nm.activeNotifications
            if (ativos.isNotEmpty()) return ativos.map { it.notification.extras.getString(Notification.EXTRA_TITLE) }
            Thread.sleep(100)
        }
        return emptyList()
    }

    @Test
    fun nudgeAvisaQuandoNadaFoiLancadoHoje() {
        assertEquals(ListenableWorker.Result.success(), rodar(Slot.NUDGE))
        assertEquals(listOf("registrar os gastos de hoje?"), titulos())
    }

    @Test
    fun nudgeCalaDepoisDeUmLancamentoHoje() {
        runBlocking {
            app.container.repository.criar(
                Movimentacao(descricao = "café", valorCentavos = -8_50, data = LocalDate.now(), natureza = Natureza.DIARIO),
                RepetirOpcao.Nao,
            )
        }
        assertEquals(ListenableWorker.Result.success(), rodar(Slot.NUDGE))
        assertEquals(emptyList<String?>(), titulos())
    }
}
