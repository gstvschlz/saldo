package com.scholze.saldo

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.scholze.saldo.ui.ledger.TAG_SALDO_PROJETADO
import com.scholze.saldo.ui.privacy.MASCARA_PRIVACIDADE
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.runner.RunWith

/**
 * Cold-start flow on a fresh install: onboarding keypad -> ledger masked by default.
 *
 * A reinstall keeps app data, and the test itself writes the saldo inicial, so the
 * fresh state is made rather than assumed: [estadoLimpo] runs before the activity
 * rule launches [MainActivity] and nothing has read the DataStore/DB by then.
 */
@RunWith(AndroidJUnit4::class)
class LedgerScreenTest {

    @get:Rule(order = 0)
    val estadoLimpo = object : ExternalResource() {
        override fun before() {
            val ctx = ApplicationProvider.getApplicationContext<Context>()
            File(ctx.filesDir, "datastore").deleteRecursively()
            ctx.deleteDatabase("saldo.db")
        }
    }

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
        // começa oculto por padrão. O hero inteiro é clicável, então o nó do valor só
        // existe na árvore não-mesclada.
        rule.onNodeWithTag(TAG_SALDO_PROJETADO, useUnmergedTree = true)
            .assertTextEquals(MASCARA_PRIVACIDADE)
    }
}
