package com.scholze.saldo.ui.mais

import android.Manifest
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.isOn
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.scholze.saldo.EstadoLimpo
import com.scholze.saldo.SaldoApplication
import com.scholze.saldo.ui.theme.SaldoTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Ligar um lembrete grava a config (e o switch renderiza a partir do que foi gravado). */
@RunWith(AndroidJUnit4::class)
class LembretesScreenTest {

    @get:Rule(order = 0)
    val estadoLimpo = EstadoLimpo()

    // Com a permissão já concedida a tela grava direto, sem o diálogo do sistema no meio do teste.
    @get:Rule(order = 1)
    val permissao: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)

    @get:Rule(order = 2)
    val rule = createComposeRule()

    @Test
    fun ligarUmLembreteGravaAConfig() {
        val app = ApplicationProvider.getApplicationContext<SaldoApplication>()
        val vm = MaisViewModel(app.container.settings, app.container.lembretesScheduler)
        rule.setContent {
            SaldoTheme {
                val settings by vm.settings.collectAsState()
                settings?.let { s ->
                    LembretesScreen(config = s.lembretes, onDefinir = vm::definirLembretes, onVoltar = {})
                }
            }
        }
        rule.onNodeWithText("fatura vence amanhã").assertIsDisplayed()
        rule.onAllNodes(isToggleable()).onFirst().performClick()   // o primeiro switch é "fatura vence amanhã"
        rule.waitUntil(5_000) { rule.onAllNodes(isOn()).fetchSemanticsNodes().size == 1 }
    }
}
