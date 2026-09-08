package com.scholze.saldo.ui.mais

import android.Manifest
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.isOn
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onChildren
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onParent
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
        val vm = MaisViewModel(
            settingsStore = app.container.settings,
            repository = app.container.repository,
            scheduler = app.container.lembretesScheduler,
            backupScheduler = app.container.backupScheduler,
            leitor = app.container.leitorDeArquivo,
            limparNotificacoes = {},
            limparSugestoes = {},
        )
        rule.setContent {
            SaldoTheme {
                val settings by vm.settings.collectAsState()
                settings?.let { s ->
                    LembretesScreen(config = s.lembretes, onDefinir = vm::definirLembretes, onVoltar = {})
                }
            }
        }
        rule.onNodeWithText("fatura vence amanhã").assertIsDisplayed()

        // "fatura vence amanhã", "recorrência hoje" e "fechamento do mês" dividem o mesmo
        // InsetGroup (mesmo pai semântico, por causa do clip do card) — isToggleable() sozinho
        // pega os três switches do card. O switch de cada InsetRow é o irmão logo após o rótulo.
        val rotulo = rule.onNodeWithText("fatura vence amanhã")
        val idRotulo = rotulo.fetchSemanticsNode().id
        val irmaos = rotulo.onParent().onChildren()
        val indiceRotulo = irmaos.fetchSemanticsNodes().indexOfFirst { it.id == idRotulo }
        irmaos[indiceRotulo + 1].assert(isToggleable()).performClick()

        rule.waitUntil(5_000) { rule.onAllNodes(isOn()).fetchSemanticsNodes().size == 1 }
    }
}
