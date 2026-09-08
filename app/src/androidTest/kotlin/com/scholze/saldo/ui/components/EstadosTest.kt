package com.scholze.saldo.ui.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.scholze.saldo.ui.theme.SaldoTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EstadosTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun erroMostraAMensagemEOBotao() {
        var tentativas = 0
        rule.setContent {
            SaldoTheme { ErroDeLeitura(MENSAGEM_ERRO_LEITURA, onTentar = { tentativas++ }) }
        }
        rule.onNodeWithTag(TAG_ERRO_LEITURA).assertIsDisplayed()
        rule.onNodeWithText(MENSAGEM_ERRO_LEITURA).assertIsDisplayed()
        rule.onNodeWithText("tentar de novo").performClick()
        assertEquals(1, tentativas)
    }

    /** Antes dos 300 ms não há spinner: num aparelho rápido o dado chega primeiro. */
    @Test
    fun carregandoSoMostraOIndicadorDepoisDoAtraso() {
        rule.mainClock.autoAdvance = false
        rule.setContent { SaldoTheme { Carregando() } }

        rule.mainClock.advanceTimeBy(100)
        rule.onNodeWithTag(TAG_CARREGANDO).assertDoesNotExist()

        rule.mainClock.advanceTimeBy(400)
        rule.onNodeWithTag(TAG_CARREGANDO).assertIsDisplayed()
    }
}
