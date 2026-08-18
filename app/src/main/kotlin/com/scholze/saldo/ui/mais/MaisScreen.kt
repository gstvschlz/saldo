package com.scholze.saldo.ui.mais

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.scholze.saldo.data.Tema
import com.scholze.saldo.domain.CartaoConfig
import com.scholze.saldo.domain.LembretesConfig
import com.scholze.saldo.ui.components.InsetGroup
import com.scholze.saldo.ui.components.InsetRow
import com.scholze.saldo.ui.entry.AmountKeypadScreen
import com.scholze.saldo.ui.privacy.MoneyText
import com.scholze.saldo.ui.theme.SaldoTheme

private fun rotulo(t: Tema) = when (t) {
    Tema.SISTEMA -> "sistema"
    Tema.CLARO -> "claro"
    Tema.ESCURO -> "escuro"
}

private fun resumo(l: LembretesConfig): String = when (l.ativos) {
    0 -> "desligados"
    1 -> "1 ativo"
    else -> "${l.ativos} ativos"
}

@Composable
fun MaisScreen(vm: MaisViewModel, onExportar: () -> Unit, modifier: Modifier = Modifier) {
    val colors = SaldoTheme.colors
    val settings by vm.settings.collectAsState()
    val s = settings ?: return

    var editandoSaldo by rememberSaveable { mutableStateOf(false) }
    var editandoCartao by remember { mutableStateOf(false) }
    var escolhendoTema by remember { mutableStateOf(false) }
    var abrindoLembretes by rememberSaveable { mutableStateOf(false) }

    if (abrindoLembretes) {
        LembretesScreen(
            config = s.lembretes,
            onDefinir = vm::definirLembretes,
            onVoltar = { abrindoLembretes = false },
            modifier = modifier,
        )
        return
    }

    if (editandoSaldo) {
        // O teclado ocupa a aba inteira e não tem "cancelar" próprio: sem isto, voltar
        // atrás só seria possível salvando (ou saindo do app pelo back do sistema).
        BackHandler { editandoSaldo = false }
        AmountKeypadScreen(
            onContinue = { vm.definirSaldoInicial(it); editandoSaldo = false },
            initialCentavos = s.saldoInicialCentavos ?: 0,
            titulo = "qual seu saldo hoje?",
            textoBotao = "salvar",
            modifier = modifier,
        )
        return
    }

    Column(modifier.fillMaxSize().background(colors.background)) {
        // Titulo grande e a ESQUERDA, como no ledger, em totais e em recorrencias: centrado
        // era a assinatura do HIG, e a 30sp do ramo novo ela ficava ainda mais evidente.
        Text(
            "mais",
            Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 14.dp),
            style = SaldoTheme.type.navTitle, color = colors.label,
        )

        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 32.dp)),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            InsetGroup {
                InsetRow(
                    label = "saldo inicial",
                    onClick = { editandoSaldo = true },
                    trailing = {
                        MoneyText(
                            centavos = s.saldoInicialCentavos ?: 0,
                            style = SaldoTheme.type.body, color = colors.secondaryLabel,
                        )
                    },
                )
                InsetRow(
                    label = "cartão",
                    value = "${s.cartao.nome} · fecha ${s.cartao.fechamentoDia} · vence ${s.cartao.vencimentoDia}",
                    onClick = { editandoCartao = true },
                )
            }

            InsetGroup {
                InsetRow(
                    label = "começar oculto",
                    trailing = { Switch(checked = s.comecarOculto, onCheckedChange = { vm.definirComecarOculto(it) }) },
                )
                InsetRow(
                    label = "mostrar valores no widget",
                    trailing = { Switch(checked = s.widgetMostrarValores, onCheckedChange = { vm.definirWidgetMostrarValores(it) }) },
                )
                Text(
                    "o widget mostra o saldo projetado na tela inicial; desligado, mostra R$ •••••",
                    Modifier.padding(start = 16.dp, end = 16.dp, bottom = 10.dp),
                    style = SaldoTheme.type.caption, color = colors.secondaryLabel,
                )
                InsetRow(label = "tema", value = rotulo(s.tema), onClick = { escolhendoTema = true })
            }

            InsetGroup {
                InsetRow(label = "lembretes", value = resumo(s.lembretes), onClick = { abrindoLembretes = true })
            }

            InsetGroup {
                InsetRow(label = "exportar dados", onClick = onExportar)
            }

            InsetGroup {
                InsetRow(label = "sobre", value = "saldo · 100% local")
            }
        }
    }

    if (editandoCartao) {
        var nome by remember { mutableStateOf(s.cartao.nome) }
        var fechamento by remember { mutableStateOf(s.cartao.fechamentoDia.toString()) }
        var vencimento by remember { mutableStateOf(s.cartao.vencimentoDia.toString()) }
        AlertDialog(
            onDismissRequest = { editandoCartao = false },
            title = { Text("cartão") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = nome, onValueChange = { nome = it },
                        label = { Text("nome") }, singleLine = true,
                    )
                    OutlinedTextField(
                        value = fechamento, onValueChange = { fechamento = it.filter(Char::isDigit).take(2) },
                        label = { Text("dia do fechamento") }, singleLine = true,
                    )
                    OutlinedTextField(
                        value = vencimento, onValueChange = { vencimento = it.filter(Char::isDigit).take(2) },
                        label = { Text("dia do vencimento") }, singleLine = true,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val f = fechamento.toIntOrNull()?.coerceIn(1, 31)
                    val v = vencimento.toIntOrNull()?.coerceIn(1, 31)
                    if (f != null && v != null && nome.isNotBlank()) {
                        vm.definirCartao(CartaoConfig(nome = nome.trim(), fechamentoDia = f, vencimentoDia = v))
                        editandoCartao = false
                    }
                }) { Text("salvar") }
            },
            dismissButton = { TextButton(onClick = { editandoCartao = false }) { Text("cancelar") } },
        )
    }

    if (escolhendoTema) {
        AlertDialog(
            onDismissRequest = { escolhendoTema = false },
            title = { Text("tema") },
            text = {
                Column {
                    Tema.entries.forEach { t ->
                        val atual = t == s.tema
                        TextButton(onClick = { vm.definirTema(t); escolhendoTema = false }) {
                            Text(
                                rotulo(t),
                                // Uma lista de três botões idênticos não diz qual está
                                // valendo; o atual vem em semibold e na cor de label.
                                style = SaldoTheme.type.body.copy(
                                    fontWeight = if (atual) FontWeight.SemiBold else FontWeight.Normal,
                                ),
                                color = if (atual) colors.label else colors.tint,
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { escolhendoTema = false }) { Text("fechar") } },
        )
    }
}
