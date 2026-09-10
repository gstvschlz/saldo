package com.scholze.saldo.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.scholze.saldo.ui.nav.SaldoTab
import com.scholze.saldo.ui.nav.SaldoTabBar
import com.scholze.saldo.ui.theme.SaldoTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * O que um leitor de tela e um dedo encontram na barra de navegação.
 *
 * A parte de a11y que não depende de dado nenhum: nomes, papéis e tamanho de alvo. As frases dos
 * gráficos são puras e vivem em `DescricoesTest`, na JVM.
 */
@RunWith(AndroidJUnit4::class)
class AcessibilidadeTest {

    @get:Rule val rule = createComposeRule()

    private fun montarBarra(selecionada: SaldoTab = SaldoTab.SALDOS, escalaFonte: Float = 1f) {
        rule.setContent {
            val densidade = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(densidade.density, escalaFonte)) {
                SaldoTheme {
                    SaldoTabBar(selected = selecionada, onSelect = {}, onAdd = {})
                }
            }
        }
    }

    /** O botão mais usado do app era um glifo desenhado, sem nome nenhum para o TalkBack. */
    @Test
    fun oMaisTemNome() {
        montarBarra()
        rule.onNodeWithContentDescription("nova movimentação").assertIsDisplayed()
    }

    /** O alvo do `+` não pode encolher com o botão tendo descido para dentro da barra. */
    @Test
    fun oMaisTemAlvoDeSobra() {
        montarBarra()
        rule.onNodeWithContentDescription("nova movimentação").assertHeightIsAtLeast(48.dp)
    }

    /**
     * Com `clickable` as quatro abas liam "botão" e nenhuma dizia estar escolhida. `selectable`
     * com `Role.Tab` é o que dá as duas informações.
     */
    @Test
    fun aAbaEscolhidaSeAnuncia() {
        montarBarra(selecionada = SaldoTab.TOTAIS)
        rule.onNodeWithText("totais").assertIsSelected()
    }

    @Test
    fun asOutrasAbasNaoSeDizemEscolhidas() {
        montarBarra(selecionada = SaldoTab.TOTAIS)
        rule.onNodeWithText("saldos").assert(SemanticsMatcher.expectValue(
            androidx.compose.ui.semantics.SemanticsProperties.Selected, false,
        ))
    }

    /** Fonte grande não pode tirar a aba do lugar de alvo tocável. */
    @Test
    fun comFonteGrandeAsAbasContinuamTocaveis() {
        montarBarra(escalaFonte = 2f)
        rule.onNodeWithText("saldos").assertIsDisplayed()
    }
}
