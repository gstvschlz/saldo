package com.scholze.saldo.ui.totais

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.scholze.saldo.EstadoLimpo
import com.scholze.saldo.MainActivity
import com.scholze.saldo.SaldoApplication
import com.scholze.saldo.domain.Movimentacao
import com.scholze.saldo.domain.Natureza
import com.scholze.saldo.domain.RepetirOpcao
import com.scholze.saldo.ui.nav.Destino
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * totais › a caminho › recorrências, pelo container real: o template aparece e tocá-lo abre a
 * ocorrência DO MÊS VISTO no editor (materializando o mês, se preciso).
 */
@RunWith(AndroidJUnit4::class)
class RecorrenciasScreenTest {

    @get:Rule(order = 0)
    val estadoLimpo = EstadoLimpo()

    @get:Rule(order = 1)
    val rule = createEmptyComposeRule()

    @Test
    fun listaAsFixasEAbreAOcorrenciaDoMes() {
        val app = ApplicationProvider.getApplicationContext<SaldoApplication>()
        val hoje = LocalDate.now()
        runBlocking {
            app.container.settings.definirSaldoInicial(100_000_00, hoje)
            // Dia 28 existe em todo mês — o teste roda em qualquer data.
            app.container.repository.criar(
                Movimentacao(
                    descricao = "aluguel", valorCentavos = -2_400_00,
                    data = hoje.withDayOfMonth(28), natureza = Natureza.DIARIO,
                ),
                RepetirOpcao.TodoMes(28),
            )
        }

        ActivityScenario.launch<MainActivity>(MainActivity.intent(app, Destino.Totais(YearMonth.from(hoje)))).use {
            rule.waitUntil(5_000) { rule.onAllNodesWithText("a caminho").fetchSemanticsNodes().isNotEmpty() }
            rule.onNodeWithText("a caminho").performClick()
            rule.onNodeWithText("recorrências").performScrollTo().performClick()

            rule.waitUntil(5_000) { rule.onAllNodesWithText("dia 28").fetchSemanticsNodes().isNotEmpty() }
            rule.onNodeWithText("aluguel").assertIsDisplayed()
            rule.onNodeWithText("1 fixa").assertIsDisplayed()

            rule.onNodeWithText("aluguel").performClick()
            rule.waitUntil(5_000) { rule.onAllNodesWithText("editar movimentação").fetchSemanticsNodes().isNotEmpty() }
            rule.onNodeWithText("editar movimentação").assertIsDisplayed()
        }
    }

    @Test
    fun voltaParaTotais() {
        val app = ApplicationProvider.getApplicationContext<SaldoApplication>()
        runBlocking { app.container.settings.definirSaldoInicial(100_000_00, LocalDate.now()) }

        ActivityScenario.launch<MainActivity>(MainActivity.intent(app, Destino.Totais(YearMonth.now()))).use {
            rule.waitUntil(5_000) { rule.onAllNodesWithText("a caminho").fetchSemanticsNodes().isNotEmpty() }
            rule.onNodeWithText("a caminho").performClick()
            rule.onNodeWithText("recorrências").performScrollTo().performClick()
            rule.waitUntil(5_000) { rule.onAllNodesWithText("nenhuma recorrência").fetchSemanticsNodes().isNotEmpty() }
            rule.onNodeWithText("‹ totais").performClick()
            // O chip "a caminho" é o marco de volta: o cabeçalho de seção "A CAMINHO" que este
            // teste esperava no plano original não existe mais (ver Task 6 — em caixa baixa ele
            // duplicaria o texto do chip e quebraria o performClick que troca de segmento).
            rule.waitUntil(5_000) { rule.onAllNodesWithText("a caminho").fetchSemanticsNodes().isNotEmpty() }
            rule.onNodeWithText("a caminho").assertIsDisplayed()
        }
    }

    @Test
    fun oInterruptorPausaEALinhaDizPausada() {
        val app = ApplicationProvider.getApplicationContext<SaldoApplication>()
        val hoje = LocalDate.now()
        runBlocking {
            app.container.settings.definirSaldoInicial(100_000_00, hoje)
            app.container.repository.criar(
                Movimentacao(descricao = "academia", valorCentavos = -120_00, data = hoje.withDayOfMonth(5), natureza = Natureza.DIARIO),
                RepetirOpcao.TodoMes(5),
            )
        }
        ActivityScenario.launch<MainActivity>(MainActivity.intent(app, Destino.Totais(YearMonth.from(hoje)))).use {
            rule.waitUntil(5_000) { rule.onAllNodesWithText("a caminho").fetchSemanticsNodes().isNotEmpty() }
            rule.onNodeWithText("a caminho").performClick()
            rule.onNodeWithText("recorrências").performScrollTo().performClick()
            rule.waitUntil(5_000) { rule.onAllNodesWithContentDescription("pausar academia").fetchSemanticsNodes().isNotEmpty() }

            rule.onNodeWithContentDescription("pausar academia").performClick()
            rule.waitUntil(5_000) { rule.onAllNodesWithText("pausada").fetchSemanticsNodes().isNotEmpty() }
            rule.onNodeWithContentDescription("retomar academia").assertIsDisplayed()
            // e continua na lista, não em "encerradas"
            rule.onAllNodesWithText("encerradas", substring = true).assertCountEquals(0)
        }
    }

    /**
     * Achado da revisão: `fixas(r.ativas.size)` contava a pausada também — `ativas` inclui
     * templates pausados (ver `InsightsEngine.recorrencias`), então o cabeçalho continuava
     * dizendo "2 fixas" com uma delas parada.
     */
    @Test
    fun umaPausadaNaoContaComoFixaNoCabecalho() {
        val app = ApplicationProvider.getApplicationContext<SaldoApplication>()
        val hoje = LocalDate.now()
        runBlocking {
            app.container.settings.definirSaldoInicial(100_000_00, hoje)
            app.container.repository.criar(
                Movimentacao(descricao = "academia", valorCentavos = -120_00, data = hoje.withDayOfMonth(5), natureza = Natureza.DIARIO),
                RepetirOpcao.TodoMes(5),
            )
            app.container.repository.criar(
                Movimentacao(descricao = "aluguel", valorCentavos = -2_400_00, data = hoje.withDayOfMonth(28), natureza = Natureza.DIARIO),
                RepetirOpcao.TodoMes(28),
            )
        }
        ActivityScenario.launch<MainActivity>(MainActivity.intent(app, Destino.Totais(YearMonth.from(hoje)))).use {
            rule.waitUntil(5_000) { rule.onAllNodesWithText("a caminho").fetchSemanticsNodes().isNotEmpty() }
            rule.onNodeWithText("a caminho").performClick()
            rule.onNodeWithText("recorrências").performScrollTo().performClick()
            rule.waitUntil(5_000) { rule.onAllNodesWithContentDescription("pausar academia").fetchSemanticsNodes().isNotEmpty() }
            rule.onNodeWithText("2 fixas").assertIsDisplayed()

            rule.onNodeWithContentDescription("pausar academia").performClick()
            rule.waitUntil(5_000) { rule.onAllNodesWithText("pausada").fetchSemanticsNodes().isNotEmpty() }
            rule.onNodeWithText("1 fixa").assertIsDisplayed()
        }
    }
}
