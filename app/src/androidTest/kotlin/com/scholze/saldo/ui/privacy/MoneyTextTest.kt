package com.scholze.saldo.ui.privacy

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.scholze.saldo.ui.theme.SaldoTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MoneyTextTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun mostraValorQuandoVisivel() {
        rule.setContent {
            SaldoTheme {
                CompositionLocalProvider(LocalPrivacy provides PrivacyState(ocultoInicial = false)) {
                    MoneyText(centavos = -23850, formato = FormatoMoney.COM_SIMBOLO)
                }
            }
        }
        rule.onNodeWithText("−R$ 238,50").assertIsDisplayed()
    }

    @Test
    fun mascaraQuandoOculto() {
        rule.setContent {
            SaldoTheme {
                CompositionLocalProvider(LocalPrivacy provides PrivacyState(ocultoInicial = true)) {
                    MoneyText(centavos = -23850)
                }
            }
        }
        rule.onNodeWithText(MASCARA_PRIVACIDADE).assertIsDisplayed()
    }

    @Test
    fun alternarRevela() {
        val estado = PrivacyState(ocultoInicial = true)
        rule.setContent {
            SaldoTheme {
                CompositionLocalProvider(LocalPrivacy provides estado) {
                    MoneyText(centavos = 100_00, formato = FormatoMoney.ASSINADO)
                }
            }
        }
        rule.onNodeWithText(MASCARA_PRIVACIDADE).assertIsDisplayed()
        rule.runOnUiThread { estado.alternar() }
        rule.onNodeWithText("+100,00").assertIsDisplayed()
    }
}
