package com.scholze.saldo.ui.nav

import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.scholze.saldo.ui.theme.SaldoTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SaldoTabBarTest {

    @get:Rule val rule = createComposeRule()

    private fun montar(onSelect: (SaldoTab) -> Unit = {}, onAdd: () -> Unit = {}) {
        rule.setContent {
            SaldoTheme(darkTheme = false) {
                SaldoTabBar(selected = SaldoTab.SALDOS, onSelect = onSelect, onAdd = onAdd)
            }
        }
    }

    @Test
    fun asQuatroAbasEstaoLaEONemUmaVezMais() {
        montar()
        listOf("saldos", "totais", "tags", "mais").forEach {
            rule.onNodeWithText(it).assertExists()
        }
    }

    @Test
    fun tocarNumaAbaSeleciona() {
        var escolhida: SaldoTab? = null
        montar(onSelect = { escolhida = it })
        rule.onNodeWithText("tags").performClick()
        assertEquals(SaldoTab.TAGS, escolhida)
    }

    @Test
    fun oMaisContinuaSendoUmBotao() {
        var adds = 0
        montar(onAdd = { adds++ })
        rule.onNodeWithTag(TAG_ADD).performClick()
        assertEquals(1, adds)
    }

    /** O FAB do M3 é 64dp; um alvo menor que isso significa que o docking quebrou. */
    @Test
    fun oFabTemOTamanhoDoM3() {
        montar()
        rule.onNodeWithTag(TAG_ADD).assertHeightIsAtLeast(56.dp)
    }
}
