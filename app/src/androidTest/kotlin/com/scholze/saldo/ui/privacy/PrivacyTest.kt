package com.scholze.saldo.ui.privacy

import androidx.compose.foundation.clickable
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PrivacyTest {

    @get:Rule val rule = createComposeRule()

    /** Escondeu, girou: continua escondido. Antes, a rotação revelava o que se acabava de esconder. */
    @Test
    fun ocultoSobreviveARestauracaoDeEstado() {
        val restaurador = StateRestorationTester(rule)
        restaurador.setContent {
            val privacidade = rememberPrivacyState(ocultoInicial = false)
            Text(if (privacidade.oculto) "oculto" else "visível", modifier = Modifier.clickable { privacidade.alternar() })
        }
        rule.onNodeWithText("visível").performClick()
        rule.onNodeWithText("oculto").assertIsDisplayed()
        restaurador.emulateSavedInstanceStateRestore()
        rule.onNodeWithText("oculto").assertIsDisplayed()
    }
}
