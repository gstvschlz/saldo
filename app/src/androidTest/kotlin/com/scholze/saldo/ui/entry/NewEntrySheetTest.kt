package com.scholze.saldo.ui.entry

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.scholze.saldo.SaldoApplication
import com.scholze.saldo.domain.Movimentacao
import com.scholze.saldo.domain.Natureza
import com.scholze.saldo.ui.theme.SaldoTheme
import java.time.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NewEntrySheetTest {

    @get:Rule val rule = createComposeRule()

    private fun vm() = EntryViewModel(ApplicationProvider.getApplicationContext<SaldoApplication>().container.repository)

    private val instancia = Movimentacao(
        id = 6, descricao = "luz", valorCentavos = -120_00, data = LocalDate.parse("2026-02-28"),
        natureza = Natureza.DIARIO, recorrenciaId = 3,
    )

    /**
     * `state` só reflete o que `iniciarEdicao`/`iniciarNova` puseram em `form` depois que o
     * `combine` com `repo.ledger`/`repo.tags` — o Room de verdade — emitir pela primeira vez;
     * até lá fica no `EntryUiState()` default. Esperar o texto aparecer, em vez de assumir que
     * o primeiro frame composto já é o real, evita flakiness contra esse primeiro emit.
     */
    private fun esperarTexto(texto: String) =
        rule.waitUntil(5_000) { rule.onAllNodesWithText(texto).fetchSemanticsNodes().isNotEmpty() }

    /** Desambigua um texto que também aparece atrás do diálogo (a sheet continua composta). */
    private fun noDialogo(texto: String) = rule.onNode(hasText(texto) and hasAnyAncestor(isDialog()))

    @Test
    fun naEdicaoRepetirEUmControleEMostraODiaDoTemplate() {
        val vm = vm()
        vm.iniciarEdicao(instancia, diaDoTemplate = 31)
        rule.setContent { SaldoTheme { NewEntrySheet(vm = vm, onFechar = {}) } }
        esperarTexto("todo mês no dia 31")
        rule.onNode(hasText("todo mês no dia 31") and hasClickAction()).assertIsDisplayed()
        rule.onNodeWithText("todo mês no dia 31").performClick()
        rule.onNodeWithText("parar de repetir a partir deste mês").assertIsDisplayed()
        // "cancelar" aparece na nav bar da sheet e no dismissButton do diálogo: o do diálogo é
        // o que tem um `AlertDialog` como ancestral.
        noDialogo("cancelar").performClick()
    }

    /**
     * Reabrir "todo mês" numa instância clampeada (fevereiro, dia 28) tem de oferecer o dia do
     * TEMPLATE (31), não o da linha — senão reconfirmar a opção já marcada encolheria a série
     * inteira para o dia 28.
     */
    @Test
    fun reabrirTodoMesOfereceODiaDoTemplateNaoODaLinhaClampeada() {
        val vm = vm()
        vm.iniciarEdicao(instancia, diaDoTemplate = 31) // instancia.data = 2026-02-28, clampeada
        rule.setContent { SaldoTheme { NewEntrySheet(vm = vm, onFechar = {}) } }
        esperarTexto("todo mês no dia 31")
        rule.onNode(hasText("todo mês no dia 31") and hasClickAction()).performClick()
        noDialogo("todo mês no dia 31").assertIsDisplayed()
    }

    @Test
    fun aAvulsaOfereceTodoMesNaEdicao() {
        val vm = vm()
        vm.iniciarEdicao(instancia.copy(recorrenciaId = null, data = LocalDate.parse("2026-09-10")))
        rule.setContent { SaldoTheme { NewEntrySheet(vm = vm, onFechar = {}) } }
        esperarTexto("não repete")
        rule.onNodeWithText("não repete").performClick()
        rule.onNodeWithText("todo mês no dia 10").assertIsDisplayed()
    }

    @Test
    fun oDialogoDeTagsTemCancelar() {
        val vm = vm()
        vm.iniciarNova(LocalDate.now())
        rule.setContent { SaldoTheme { NewEntrySheet(vm = vm, onFechar = {}) } }
        rule.onNodeWithText("tags").performClick()
        noDialogo("cancelar").assertIsDisplayed()
    }
}
