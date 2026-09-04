package com.scholze.saldo

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.scholze.saldo.ui.board.TAG_BOARD_GRADE
import com.scholze.saldo.ui.nav.Destino
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * As duas vistas da aba `saldos`: o board é onde o app abre, a lista está a um toque, e
 * quem chega por deep link cai na lista porque pediu um dia, não um panorama.
 */
@RunWith(AndroidJUnit4::class)
class VistaSaldosTest {

    @get:Rule(order = 0)
    val estadoLimpo = EstadoLimpo()

    @get:Rule(order = 1)
    val rule = createEmptyComposeRule()

    private val tituloMes =
        DateTimeFormatter.ofPattern("MMMM yyyy", Locale.forLanguageTag("pt-BR"))

    private val mesCorrente: String get() = YearMonth.now().format(tituloMes)

    /** Onboarding já feito: sem saldo inicial o teclado toma a tela e não há vista nenhuma. */
    private fun app(): SaldoApplication {
        val app = ApplicationProvider.getApplicationContext<SaldoApplication>()
        runBlocking { app.container.settings.definirSaldoInicial(100_000_00, LocalDate.now()) }
        return app
    }

    private fun esperarBoard() = rule.waitUntil(5_000) {
        rule.onAllNodesWithTag(TAG_BOARD_GRADE).fetchSemanticsNodes().isNotEmpty()
    }

    private fun esperarLista() = rule.waitUntil(5_000) {
        rule.onAllNodesWithText(mesCorrente).fetchSemanticsNodes().isNotEmpty()
    }

    @Test
    fun oAppAbreNoBoard() {
        app()
        ActivityScenario.launch(MainActivity::class.java).use {
            esperarBoard()
            rule.onNodeWithText("seus dias").assertIsDisplayed()
        }
    }

    @Test
    fun oToggleLevaAListaEVolta() {
        app()
        ActivityScenario.launch(MainActivity::class.java).use {
            esperarBoard()

            rule.onNodeWithContentDescription("ver como lista").performClick()
            esperarLista()
            rule.onNodeWithText(mesCorrente).assertIsDisplayed()

            rule.onNodeWithContentDescription("ver como grade").performClick()
            esperarBoard()
            rule.onNodeWithTag(TAG_BOARD_GRADE).assertExists()
        }
    }

    @Test
    fun deepLinkDeUmDiaCaiNaLista() {
        val app = app()
        val destino = Destino.Saldos(YearMonth.now(), LocalDate.now().dayOfMonth)
        ActivityScenario.launch<MainActivity>(MainActivity.intent(app, destino)).use {
            esperarLista()
            rule.onNodeWithText(mesCorrente).assertIsDisplayed()
            rule.onAllNodesWithTag(TAG_BOARD_GRADE).fetchSemanticsNodes().let {
                assert(it.isEmpty()) { "o deep link de um dia tem de abrir a lista, não a grade" }
            }
        }
    }
}
