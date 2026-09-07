package com.scholze.saldo.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.scholze.saldo.ui.theme.SaldoTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class M3Test {

    @get:Rule val rule = createComposeRule()

    @Test
    fun chipSelecionadoNaoRedispara() {
        var escolhido = -1
        rule.setContent {
            SaldoTheme(darkTheme = false) {
                FiltroChips(
                    opcoes = listOf("todas", "diários", "fixas"),
                    selecionado = 0,
                    onSelect = { escolhido = it },
                )
            }
        }
        rule.onNodeWithText("fixas").performClick()
        assertEquals(2, escolhido)
    }

    @Test
    fun pillMostraOValorEMascaraJuntoComOResto() {
        rule.setContent {
            SaldoTheme(darkTheme = false) {
                Column { SaldoPill(centavos = 781_245, nivel = 2) }
            }
        }
        rule.onNodeWithText("7.812,45").assertIsDisplayed()
    }

    /**
     * O badge carrega DOIS nós de texto (dia e dia-da-semana) e ambos têm de existir:
     * um badge que perdesse o dia da semana passaria despercebido num screenshot.
     */
    @Test
    fun badgeMostraDiaEDiaDaSemana() {
        rule.setContent {
            SaldoTheme(darkTheme = false) {
                DiaBadge(dia = 8, diaSemana = "sáb", destacado = true)
            }
        }
        rule.onNodeWithText("08").assertIsDisplayed()
        rule.onNodeWithText("sáb").assertIsDisplayed()
    }

    @Test
    fun topBarNavegaNosDoisSentidos() {
        var anterior = 0
        var proximo = 0
        rule.setContent {
            SaldoTheme(darkTheme = false) {
                SaldoTopBar(titulo = "agosto 2026", onAnterior = { anterior++ }, onProximo = { proximo++ })
            }
        }
        rule.onNodeWithText("agosto 2026").assertIsDisplayed()
        rule.onNodeWithContentDescription("mês anterior").performClick()
        rule.onNodeWithContentDescription("próximo mês").performClick()
        assertEquals(1, anterior)
        assertEquals(1, proximo)
    }

    @Test
    fun setaDeAvancarDesabilitadaNaoTemClique() {
        var avancos = 0
        rule.setContent {
            SaldoTheme { SaldoTopBar(titulo = "setembro 2026", onAnterior = {}, onProximo = { avancos++ }, podeAvancar = false) }
        }
        rule.onNodeWithContentDescription("próximo mês").assertIsNotEnabled()
        rule.onNodeWithContentDescription("próximo mês").performClick()
        assertEquals(0, avancos)
    }

    @Test
    fun modoBuscaTrocaABarraPeloCampo() {
        var texto = ""
        var fechou = false
        rule.setContent {
            SaldoTheme {
                SaldoTopBar(
                    titulo = "setembro 2026", onAnterior = {}, onProximo = {},
                    busca = BuscaTopBar(texto = texto, onTexto = { texto = it }, onFechar = { fechou = true }),
                )
            }
        }
        rule.onNodeWithText("setembro 2026").assertDoesNotExist()
        rule.onAllNodesWithContentDescription("mês anterior").assertCountEquals(0)
        rule.onNodeWithTag(TAG_CAMPO_BUSCA).assertIsFocused()
        rule.onNodeWithTag(TAG_CAMPO_BUSCA).performTextInput("uber")
        assertEquals("uber", texto)
        rule.onNodeWithContentDescription("fechar busca").performClick()
        assertEquals(true, fechou)
    }
}
