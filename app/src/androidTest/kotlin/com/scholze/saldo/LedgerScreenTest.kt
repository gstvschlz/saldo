package com.scholze.saldo

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.scholze.saldo.ui.ledger.TAG_SALDO_PROJETADO
import com.scholze.saldo.ui.privacy.MASCARA_PRIVACIDADE
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Cold-start flow on a fresh install: onboarding keypad -> ledger masked by default.
 *
 * A reinstall keeps app data, and the test itself writes the saldo inicial, so the
 * fresh state is made rather than assumed: [estadoLimpo] runs before the activity rule
 * launches [MainActivity].
 *
 * O reset é feito pelas instâncias vivas do container, não apagando arquivos — ver o
 * KDoc de [EstadoLimpo]. Apagar `filesDir/datastore` e `saldo.db`, como esta classe
 * fazia, só funciona enquanto ninguém tiver lido: o container do `SaldoApplication`
 * (DataStore singleton + conexão do Room) sobrevive de `@Test` para `@Test` dentro do
 * mesmo processo e continuaria servindo o cache antigo.
 */
@RunWith(AndroidJUnit4::class)
class LedgerScreenTest {

    @get:Rule(order = 0)
    val estadoLimpo = EstadoLimpo()

    @get:Rule(order = 1)
    val rule = createAndroidComposeRule<MainActivity>()

    @Test
    fun onboardingDepoisLedgerOculto() {
        rule.onNodeWithText("qual seu saldo hoje?").assertIsDisplayed()
        rule.onNodeWithText("1").performClick()
        rule.onNodeWithText("0").performClick()
        rule.onNodeWithText("0").performClick()
        rule.onNodeWithText("0").performClick()
        rule.onNodeWithText("0").performClick()   // 1 + quatro zeros => R$ 100,00
        rule.onNodeWithText("começar").performClick()
        rule.waitUntil(5_000) {
            rule.onAllNodesWithText("saldo projetado", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        // O app abre no board desde a board-1; este fluxo é sobre o ledger.
        rule.onNodeWithContentDescription("ver como lista").performClick()
        // começa oculto por padrão. O hero inteiro é clicável, então o nó do valor só
        // existe na árvore não-mesclada.
        rule.onNodeWithTag(TAG_SALDO_PROJETADO, useUnmergedTree = true)
            .assertTextEquals(MASCARA_PRIVACIDADE)
    }
}
