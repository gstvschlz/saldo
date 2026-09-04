package com.scholze.saldo

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToKey
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.scholze.saldo.ui.nav.TAG_ADD
import java.time.LocalDate
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
        // O app abre no board desde a board-1; este fluxo é sobre o ledger.
        rule.onNodeWithContentDescription("ver como lista").performClick()

        // revela valores (hero toggle)
        rule.onAllNodesWithText("saldo projetado", substring = true).onFirst().performClick()

        // adiciona uma movimentação (mesma sequência do EntryFlowTest)
        rule.onNodeWithTag(TAG_ADD).performClick()
        rule.onNodeWithText("toque para escrever").performClick()
        rule.onNode(hasSetTextAction()).performTextInput("uber")
        rule.onNodeWithText("0,00").performClick()               // abre o teclado
        "2740".forEach { rule.onNodeWithText(it.toString()).performClick() }
        rule.onNodeWithText("continuar").performClick()
        rule.onNodeWithText("adicionar diário").performClick()

        // A linha de hoje raramente cabe na primeira dobra (mesmo problema do
        // EntryFlowTest): espera a lista deixar de estar vazia e rola pela CHAVE do
        // item (o epochDay do dia), não pelo texto — não depende do dia do mês em que
        // o teste roda.
        rule.waitUntil(5_000) {
            rule.onAllNodesWithText("toque em + para adicionar").fetchSemanticsNodes().isEmpty()
        }
        rule.onNode(hasScrollToIndexAction()).performScrollToKey(LocalDate.now().toEpochDay())
        rule.waitUntil(5_000) { rule.onAllNodesWithText("uber").fetchSemanticsNodes().isNotEmpty() }

        // swipe para excluir
        rule.onAllNodesWithText("uber").onFirst().performTouchInput { swipeLeft() }
        rule.waitUntil(5_000) { rule.onAllNodesWithText("movimentação excluída").fetchSemanticsNodes().isNotEmpty() }

        // desfazer restaura
        rule.onNodeWithText("desfazer").performClick()

        // "uber" era a ÚNICA movimentação do mês: excluí-la troca a grade inteira pelo
        // placeholder "sem movimentações neste mês" (item único), e restaurá-la troca de
        // volta — nessa virada de tipo de conteúdo a LazyColumn não preserva a posição de
        // rolagem antiga, então rola de novo pela mesma chave antes de procurar a linha.
        rule.waitUntil(5_000) {
            rule.onAllNodesWithText("toque em + para adicionar").fetchSemanticsNodes().isEmpty()
        }
        rule.onNode(hasScrollToIndexAction()).performScrollToKey(LocalDate.now().toEpochDay())
        rule.waitUntil(5_000) { rule.onAllNodesWithText("uber").fetchSemanticsNodes().isNotEmpty() }
    }
}
