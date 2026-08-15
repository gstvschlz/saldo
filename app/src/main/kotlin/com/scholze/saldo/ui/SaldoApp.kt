package com.scholze.saldo.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.scholze.saldo.AppContainer
import com.scholze.saldo.ui.entry.AmountKeypadScreen
import com.scholze.saldo.ui.entry.EntryViewModel
import com.scholze.saldo.ui.entry.NewEntrySheet
import com.scholze.saldo.ui.ledger.LedgerScreen
import com.scholze.saldo.ui.ledger.LedgerViewModel
import com.scholze.saldo.ui.nav.SaldoTab
import com.scholze.saldo.ui.nav.SaldoTabBar
import com.scholze.saldo.ui.privacy.LocalPrivacy
import com.scholze.saldo.ui.theme.SaldoTheme
import java.time.LocalDate
import kotlinx.coroutines.launch

/**
 * The shell: onboarding gate, tabbed content, undo snackbar and the
 * nova-movimentação sheet sliding over the top.
 */
@Composable
fun SaldoApp(container: AppContainer, modifier: Modifier = Modifier) {
    val colors = SaldoTheme.colors
    val settings by container.settings.settings.collectAsState(initial = null)
    val s = settings ?: return   // aguarda o primeiro valor do DataStore

    if (s.saldoInicialCentavos == null) {
        // Onboarding: semear o saldo inicial com o teclado 1g.
        val scope = rememberCoroutineScope()
        AmountKeypadScreen(
            onContinue = { centavos ->
                scope.launch { container.settings.definirSaldoInicial(centavos, LocalDate.now()) }
            },
            titulo = "qual seu saldo hoje?",
            textoBotao = "começar",
            modifier = modifier.statusBarsPadding(),
        )
        return
    }

    val ledgerVm: LedgerViewModel = viewModel(factory = LedgerViewModel.factory(container))
    val entryVm: EntryViewModel = viewModel(factory = EntryViewModel.factory(container))
    val ledgerState by ledgerVm.state.collectAsState()
    val privacidade = LocalPrivacy.current
    val snackbar = remember { SnackbarHostState() }

    var tab by rememberSaveable { mutableStateOf(SaldoTab.SALDOS) }
    // Só o "a sheet está aberta" é saveable; o formulário em si vive no [EntryViewModel]
    // e por isso sobrevive à rotação sem precisar ser serializado.
    var sheetAberto by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        ledgerVm.eventoExclusao.collect { snapshot ->
            val resultado = snackbar.showSnackbar(
                message = "movimentação excluída",
                actionLabel = "desfazer",
            )
            if (resultado == SnackbarResult.ActionPerformed) ledgerVm.desfazerExclusao(snapshot)
        }
    }

    Box(modifier.fillMaxSize().background(colors.background)) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Box(Modifier.weight(1f)) {
                when (tab) {
                    SaldoTab.SALDOS -> LedgerScreen(
                        state = ledgerState,
                        onMesAnterior = ledgerVm::mesAnterior,
                        onProximoMes = ledgerVm::proximoMes,
                        onFiltro = ledgerVm::definirFiltro,
                        // id 0 = ocorrência virtual: o mês ainda não foi materializado (a
                        // `abrirMes` do ViewModel é assíncrona). Editá-la explodiria no save
                        // com SO_ESTE_MES, então a linha simplesmente não abre o editor.
                        onItemClick = { if (it.id != 0L) { entryVm.iniciarEdicao(it); sheetAberto = true } },
                        // Mesma razão: `repo.excluir` recusa id 0 (o delete seria no-op e o
                        // "desfazer" duplicaria a linha). Sem a guarda o swipe só produziria
                        // uma IllegalArgumentException engolida pelo ViewModel.
                        onExcluir = { if (it.id != 0L) ledgerVm.excluir(it) },
                        onTogglePrivacidade = privacidade::alternar,
                        contentPadding = PaddingValues(bottom = 24.dp),
                    )
                    SaldoTab.TOTAIS -> Placeholder("totais")   // Task 12
                    SaldoTab.TAGS -> Placeholder("tags")       // Task 13
                    SaldoTab.MAIS -> Placeholder("mais")       // Task 14
                }
            }
            SaldoTabBar(
                selected = tab,
                onSelect = { tab = it },
                onAdd = { entryVm.iniciarNova(LocalDate.now()); sheetAberto = true },
            )
        }

        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 70.dp))

        AnimatedVisibility(visible = sheetAberto, enter = slideInVertically { it }, exit = slideOutVertically { it }) {
            NewEntrySheet(
                vm = entryVm,
                onFechar = { sheetAberto = false },
                modifier = Modifier.statusBarsPadding(),
            )
        }
    }
}

/** As abas além de saldos chegam nas tasks 12–14. */
@Composable
private fun Placeholder(nome: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(nome, style = SaldoTheme.type.body, color = SaldoTheme.colors.secondaryLabel)
    }
}
