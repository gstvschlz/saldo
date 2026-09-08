package com.scholze.saldo

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.scholze.saldo.ui.mais.TAG_BOTAO_APAGAR
import com.scholze.saldo.ui.mais.TAG_CONFIRMACAO_APAGAR
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Apagar dados leva o app de volta ao teclado do onboarding, sem reiniciar nada. */
@RunWith(AndroidJUnit4::class)
class ApagarDadosTest {

    @get:Rule(order = 0)
    val estadoLimpo = EstadoLimpo()

    @get:Rule(order = 1)
    val rule = createEmptyComposeRule()

    @Test
    fun apagarVoltaAoOnboarding() {
        val app = ApplicationProvider.getApplicationContext<SaldoApplication>()
        runBlocking { app.container.settings.definirSaldoInicial(100_000_00, LocalDate.now()) }

        ActivityScenario.launch(MainActivity::class.java).use {
            rule.waitUntil(5_000) { rule.onAllNodesWithText("mais").fetchSemanticsNodes().isNotEmpty() }
            rule.onNodeWithText("mais").performClick()
            rule.onNodeWithText("apagar dados").performScrollTo().performClick()
            rule.onNodeWithTag(TAG_CONFIRMACAO_APAGAR).performTextInput("apagar")
            rule.onNodeWithTag(TAG_BOTAO_APAGAR).performClick()

            rule.waitUntil(5_000) {
                rule.onAllNodesWithText("qual seu saldo hoje?").fetchSemanticsNodes().isNotEmpty()
            }
            rule.onNodeWithText("qual seu saldo hoje?").assertIsDisplayed()
        }
    }
}
