package com.scholze.saldo

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Instrumented test, runs on an Android device or emulator. */
@RunWith(AndroidJUnit4::class)
class LedgerScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun mostraOMesEOSaldoProjetado() {
        composeRule.onNodeWithText("julho 2026").assertIsDisplayed()
        composeRule.onNodeWithText("R$ 120.461,84").assertIsDisplayed()
    }

    @Test
    fun abreOSheetDeNovaMovimentacao() {
        composeRule.onNodeWithText("saldo projetado · 31 jul").assertIsDisplayed()
    }
}
