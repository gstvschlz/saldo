package com.scholze.saldo.ui.entry

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
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

    @Test
    fun naEdicaoRepetirEUmControleEMostraODiaDoTemplate() {
        val vm = vm()
        vm.iniciarEdicao(instancia, diaDoTemplate = 31)
        rule.setContent { SaldoTheme { NewEntrySheet(vm = vm, onFechar = {}) } }
        rule.onNode(hasText("todo mês no dia 31") and hasClickAction()).assertIsDisplayed()
        rule.onNodeWithText("todo mês no dia 31").performClick()
        rule.onNodeWithText("parar de repetir a partir deste mês").assertIsDisplayed()
        // "cancelar" aparece na nav bar da sheet e no dismissButton do diálogo: o último é o do diálogo.
        rule.onAllNodesWithText("cancelar").onLast().performClick()
    }

    @Test
    fun aAvulsaOfereceTodoMesNaEdicao() {
        val vm = vm()
        vm.iniciarEdicao(instancia.copy(recorrenciaId = null, data = LocalDate.parse("2026-09-10")))
        rule.setContent { SaldoTheme { NewEntrySheet(vm = vm, onFechar = {}) } }
        rule.onNodeWithText("não repete").performClick()
        rule.onNodeWithText("todo mês no dia 10").assertIsDisplayed()
    }

    @Test
    fun oDialogoDeTagsTemCancelar() {
        val vm = vm()
        vm.iniciarNova(LocalDate.now())
        rule.setContent { SaldoTheme { NewEntrySheet(vm = vm, onFechar = {}) } }
        rule.onNodeWithText("tags").performClick()
        rule.onAllNodesWithText("cancelar").onLast().assertIsDisplayed()
    }
}
