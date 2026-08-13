package com.scholze.saldo.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.scholze.saldo.model.mesDeExemplo
import com.scholze.saldo.ui.entry.NewEntrySheet
import com.scholze.saldo.ui.ledger.LedgerScreen
import com.scholze.saldo.ui.nav.SaldoTab
import com.scholze.saldo.ui.nav.SaldoTabBar
import com.scholze.saldo.ui.theme.SaldoTheme

/**
 * The 1k shell: tabbed content with the nova-movimentação sheet sliding over
 * the top when the center add button is tapped.
 */
@Composable
fun SaldoApp(modifier: Modifier = Modifier) {
    val colors = SaldoTheme.colors
    var tab by rememberSaveable { mutableStateOf(SaldoTab.SALDOS) }
    var sheetAberto by rememberSaveable { mutableStateOf(false) }

    Box(
        modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Box(Modifier.weight(1f)) {
                when (tab) {
                    SaldoTab.SALDOS -> LedgerScreen(
                        mes = mesDeExemplo,
                        contentPadding = PaddingValues(bottom = 24.dp),
                    )
                    SaldoTab.TOTAIS -> Placeholder("totais")
                    SaldoTab.TAGS -> Placeholder("tags")
                    SaldoTab.MAIS -> Placeholder("mais")
                }
            }

            SaldoTabBar(
                selected = tab,
                onSelect = { tab = it },
                onAdd = { sheetAberto = true },
            )
        }

        AnimatedVisibility(
            visible = sheetAberto,
            enter = slideInVertically { it },
            exit = slideOutVertically { it },
        ) {
            NewEntrySheet(
                onCancel = { sheetAberto = false },
                onSave = { sheetAberto = false },
                modifier = Modifier.statusBarsPadding(),
            )
        }
    }
}

/** The tabs beyond saldos are not part of the four screens that were picked. */
@Composable
private fun Placeholder(nome: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            nome,
            style = SaldoTheme.type.body,
            color = SaldoTheme.colors.secondaryLabel,
        )
    }
}

@Preview(heightDp = 900)
@Composable
private fun SaldoAppLightPreview() {
    SaldoTheme(darkTheme = false) { SaldoApp() }
}

@Preview(heightDp = 900)
@Composable
private fun SaldoAppDarkPreview() {
    SaldoTheme(darkTheme = true) { SaldoApp() }
}
