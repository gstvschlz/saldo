package com.scholze.saldo

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.scholze.saldo.ui.board.TAG_BOARD_GRADE
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * O botão Voltar do sistema: subtela → aba → saldos → sair. Antes, saía do app de qualquer
 * lugar que não tivesse `BackHandler` próprio — do ledger da tag, de totais, de tags.
 */
@RunWith(AndroidJUnit4::class)
class SaldoAppBackTest {

    @get:Rule(order = 0)
    val estadoLimpo = EstadoLimpo()

    @get:Rule(order = 1)
    val rule = createEmptyComposeRule()

    private fun app() {
        val app = ApplicationProvider.getApplicationContext<SaldoApplication>()
        runBlocking { app.container.settings.definirSaldoInicial(100_000_00, LocalDate.now()) }
    }

    private fun esperarBoard() = rule.waitUntil(5_000) {
        rule.onAllNodesWithTag(TAG_BOARD_GRADE).fetchSemanticsNodes().isNotEmpty()
    }

    private fun esperarTexto(t: String) = rule.waitUntil(5_000) {
        rule.onAllNodesWithText(t, substring = true).fetchSemanticsNodes().isNotEmpty()
    }

    /** Espresso.pressBack lança se a activity fechar; o `runCatching` distingue "saiu" de "ficou". */
    private fun voltar(): Boolean = runCatching { Espresso.pressBack() }.isSuccess

    @Test
    fun deUmaAbaOVoltarVaiParaSaldos() {
        app()
        ActivityScenario.launch(MainActivity::class.java).use {
            esperarBoard()
            rule.onNodeWithText("totais").performClick()
            esperarTexto("totais")
            assertEquals(true, voltar())
            esperarBoard()
            rule.onAllNodesWithTag(TAG_BOARD_GRADE).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun daListaOVoltarVaiParaOBoard() {
        app()
        ActivityScenario.launch(MainActivity::class.java).use {
            esperarBoard()
            rule.onNodeWithContentDescription("ver como lista").performClick()
            esperarTexto("todas")
            assertEquals(true, voltar())
            esperarBoard()
        }
    }

    @Test
    fun deRecorrenciasOVoltarVaiParaTotaisEDepoisParaSaldos() {
        app()
        ActivityScenario.launch(MainActivity::class.java).use {
            esperarBoard()
            rule.onNodeWithText("totais").performClick()
            rule.onNodeWithText("a caminho").performClick()
            rule.onNodeWithText("recorrências", substring = true).performClick()
            esperarTexto("‹ totais")
            assertEquals(true, voltar())
            esperarTexto("a caminho")
            assertEquals(true, voltar())
            esperarBoard()
        }
    }

    @Test
    fun noBoardOVoltarSaiDoApp() {
        app()
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        esperarBoard()
        voltar()
        rule.waitUntil(5_000) { scenario.state == androidx.lifecycle.Lifecycle.State.DESTROYED }
        assertEquals(androidx.lifecycle.Lifecycle.State.DESTROYED, scenario.state)
    }
}
