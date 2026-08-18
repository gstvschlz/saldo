package com.scholze.saldo.ui.tags

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.scholze.saldo.domain.Tag
import com.scholze.saldo.ui.components.InsetGroup
import com.scholze.saldo.ui.privacy.FormatoMoney
import com.scholze.saldo.ui.privacy.MoneyText
import com.scholze.saldo.ui.theme.SaldoTheme

private val CORES = listOf(0xFFA6486BL, 0xFFB95A2EL, 0xFF2A7A86L, 0xFF4B4BC4L, 0xFF14663AL, 0xFFE58A5AL)

@Composable
fun TagsScreen(vm: TagsViewModel, onTagClick: (Tag) -> Unit, modifier: Modifier = Modifier) {
    val colors = SaldoTheme.colors
    val state by vm.state.collectAsState()
    var criando by remember { mutableStateOf(false) }
    var renomeando by remember { mutableStateOf<Tag?>(null) }
    var excluindo by remember { mutableStateOf<Tag?>(null) }

    Column(modifier.fillMaxSize().background(colors.background)) {
        Box(Modifier.fillMaxWidth().background(colors.navBar).padding(vertical = 12.dp)) {
            Text(
                "tags", Modifier.fillMaxWidth(),
                style = SaldoTheme.type.navTitle, color = colors.label, textAlign = TextAlign.Center,
            )
        }

        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 32.dp)),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            InsetGroup {
                state.tags.forEachIndexed { i, (tag, total) ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onTagClick(tag) }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Box(Modifier.size(10.dp).background(Color(tag.cor), CircleShape))
                        Text(tag.nome, Modifier.weight(1f), style = SaldoTheme.type.body, color = colors.label)
                        if (total > 0) {
                            MoneyText(
                                centavos = -total,
                                style = SaldoTheme.type.body, color = colors.secondaryLabel,
                                formato = FormatoMoney.ASSINADO,
                            )
                        }
                        Text(
                            "editar", Modifier.clickable { renomeando = tag },
                            style = SaldoTheme.type.footnote, color = colors.tint,
                        )
                        Text(
                            "excluir", Modifier.clickable { excluindo = tag },
                            style = SaldoTheme.type.footnote, color = colors.categoryVariable,
                        )
                    }
                }
                Text(
                    "nova tag",
                    Modifier.fillMaxWidth().clickable { criando = true }.padding(horizontal = 16.dp, vertical = 12.dp),
                    style = SaldoTheme.type.body, color = colors.tint,
                )
            }
            Text(
                "toque numa tag para ver só ela no ledger",
                style = SaldoTheme.type.footnote, color = colors.secondaryLabel,
            )
        }
    }

    if (criando) {
        var nome by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { criando = false },
            title = { Text("nova tag") },
            text = {
                OutlinedTextField(
                    value = nome, onValueChange = { nome = it }, singleLine = true,
                    placeholder = { Text("nome") },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    // A cor sai da paleta em rodízio pelo tamanho da lista — sem seletor,
                    // que o design não pede, e sem duas tags seguidas iguais.
                    if (nome.isNotBlank()) vm.criar(nome.trim(), CORES[state.tags.size % CORES.size])
                    criando = false
                }) { Text("criar") }
            },
            dismissButton = { TextButton(onClick = { criando = false }) { Text("cancelar") } },
        )
    }

    renomeando?.let { tag ->
        var nome by remember(tag) { mutableStateOf(tag.nome) }
        AlertDialog(
            onDismissRequest = { renomeando = null },
            title = { Text("renomear tag") },
            text = { OutlinedTextField(value = nome, onValueChange = { nome = it }, singleLine = true) },
            confirmButton = {
                TextButton(onClick = {
                    if (nome.isNotBlank()) vm.renomear(tag, nome.trim())
                    renomeando = null
                }) { Text("salvar") }
            },
            dismissButton = { TextButton(onClick = { renomeando = null }) { Text("cancelar") } },
        )
    }

    excluindo?.let { tag ->
        AlertDialog(
            onDismissRequest = { excluindo = null },
            title = { Text("excluir \"${tag.nome}\"?") },
            // Verdade garantida pelo schema: os cross-refs saem por ON DELETE CASCADE,
            // as linhas de movimentação não.
            text = { Text("as movimentações continuam; só perdem essa tag.") },
            confirmButton = { TextButton(onClick = { vm.excluir(tag); excluindo = null }) { Text("excluir") } },
            dismissButton = { TextButton(onClick = { excluindo = null }) { Text("cancelar") } },
        )
    }
}
