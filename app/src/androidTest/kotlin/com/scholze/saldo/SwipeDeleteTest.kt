package com.scholze.saldo

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.scholze.saldo.ui.entry.TAG_TECLADO
import com.scholze.saldo.ui.nav.TAG_ADD
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Swipe-to-delete on a real row: swiping left fires the "movimentação excluída" snackbar,
 * and "desfazer" restores the row. Same fresh-install shape as [EntryFlowTest] — onboarding,
 * reveal, add one entry through the sheet — via [EstadoLimpo] rather than the brief's manual
 * onboarding-every-run preamble.
 */
@RunWith(AndroidJUnit4::class)
class SwipeDeleteTest {

    @get:Rule(order = 0)
    val estadoLimpo = EstadoLimpo()

    @get:Rule(order = 1)
    val rule = createAndroidComposeRule<MainActivity>()

    @Test
    fun swipeExcluiEDesfazerRestaura() {
        // onboarding
        rule.onNodeWithText("qual seu saldo hoje?").assertIsDisplayed()
        "100000".forEach { rule.onNodeWithText(it.toString()).performClick() }   // R$ 1.000,00
        rule.onNodeWithText("começar").performClick()
        rule.waitUntil(5_000) {
            rule.onAllNodesWithText("saldo projetado", substring = true).fetchSemanticsNodes().isNotEmpty()
        }

        // revela valores (hero toggle)
        rule.onAllNodesWithText("saldo projetado", substring = true).onFirst().performClick()

        // adiciona uma movimentação (mesma sequência do EntryFlowTest)
        rule.onNodeWithTag(TAG_ADD).performClick()
        rule.onNodeWithText("opcional").performClick()
        rule.onNode(hasSetTextAction()).performTextInput("uber")
        rule.onNodeWithText("0,00").performClick()               // abre o teclado
        // Dentro do teclado: o "4" da grade atrás da sheet é outro nó com o mesmo texto.
        "2740".forEach { d ->
            rule.onNode(hasAnyAncestor(hasTestTag(TAG_TECLADO)) and hasText(d.toString()))
                .performClick()
        }
        rule.onNodeWithText("continuar").performClick()
        rule.onNodeWithText("adicionar diário").performClick()

        // O painel de hoje já está aberto embaixo da grade: a linha aparece nele sem rolar.
        rule.waitUntil(5_000) { rule.onAllNodesWithText("uber").fetchSemanticsNodes().isNotEmpty() }

        // swipe para excluir
        rule.onAllNodesWithText("uber").onFirst().performTouchInput { swipeLeft() }
        rule.waitUntil(5_000) { rule.onAllNodesWithText("movimentação excluída").fetchSemanticsNodes().isNotEmpty() }

        // desfazer restaura
        rule.onNodeWithText("desfazer").performClick()

        rule.waitUntil(5_000) { rule.onAllNodesWithText("uber").fetchSemanticsNodes().isNotEmpty() }
    }
}
