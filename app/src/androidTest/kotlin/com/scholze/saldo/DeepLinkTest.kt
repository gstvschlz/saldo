package com.scholze.saldo

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.scholze.saldo.ui.nav.Destino
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Um Intent com [Destino] (widget, lembrete) abre o app já no lugar certo. */
@RunWith(AndroidJUnit4::class)
class DeepLinkTest {

    @get:Rule(order = 0)
    val estadoLimpo = EstadoLimpo()

    @get:Rule(order = 1)
    val rule = createEmptyComposeRule()

    @Test
    fun destinoNovaMovimentacaoAbreASheet() {
        val app = ApplicationProvider.getApplicationContext<SaldoApplication>()
        // Já com onboarding feito: antes dele o destino é ignorado (o teclado toma a tela).
        runBlocking { app.container.settings.definirSaldoInicial(100_000_00, LocalDate.now()) }

        ActivityScenario.launch<MainActivity>(MainActivity.intent(app, Destino.NovaMovimentacao)).use {
            rule.waitUntil(5_000) {
                rule.onAllNodesWithText("nova movimentação").fetchSemanticsNodes().isNotEmpty()
            }
            rule.onNodeWithText("nova movimentação").assertIsDisplayed()
        }
    }
}
