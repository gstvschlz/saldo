package com.scholze.saldo.ui.tags

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.scholze.saldo.domain.PaletaTags
import com.scholze.saldo.domain.Tag
import com.scholze.saldo.ui.components.ErroDeLeitura
import com.scholze.saldo.ui.components.IconeRedondo
import com.scholze.saldo.ui.components.InsetGroup
import com.scholze.saldo.ui.components.SaldoIcon
import com.scholze.saldo.ui.privacy.FormatoMoney
import com.scholze.saldo.ui.privacy.MoneyText
import com.scholze.saldo.ui.theme.SaldoTheme

@Composable
fun TagsScreen(vm: TagsViewModel, onTagClick: (Tag) -> Unit, modifier: Modifier = Modifier) {
    val colors = SaldoTheme.colors
    val state by vm.state.collectAsState()
    var criando by rememberSaveable { mutableStateOf(false) }
    // `Tag` não é `Saveable`: guarda-se o id e resolve-se contra `state.tags` na hora de desenhar.
    var renomeandoId by rememberSaveable { mutableStateOf<Long?>(null) }
    var excluindoId by rememberSaveable { mutableStateOf<Long?>(null) }
    val renomeando = state.tags.firstOrNull { it.first.id == renomeandoId }?.first
    val excluindo = state.tags.firstOrNull { it.first.id == excluindoId }?.first

    Column(modifier.fillMaxSize().background(colors.background)) {
        // Titulo grande e a ESQUERDA, como no ledger, em totais e em recorrencias: centrado
        // era a assinatura do HIG, e a 30sp do ramo novo ela ficava ainda mais evidente.
        Text(
            "tags",
            Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 14.dp),
            style = SaldoTheme.type.navTitle, color = colors.label,
        )

        val erro = state.erro
        if (erro != null) {
            ErroDeLeitura(erro, vm::tentarDeNovo)
            return@Column
        }

        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 32.dp)),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            if (state.tags.isEmpty()) {
                Text(
                    "uma tag é uma etiqueta: mercado, casa, lazer. toque numa tag para ver só ela.",
                    style = SaldoTheme.type.footnote, color = colors.secondaryLabel,
                )
            }
            InsetGroup {
                state.tags.forEachIndexed { i, (tag, total) ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onTagClick(tag) }
                            .padding(horizontal = 16.dp, vertical = 4.dp),
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
                        IconeRedondo(SaldoIcon.LAPIS, "editar ${tag.nome}", onClick = { renomeandoId = tag.id })
                        IconeRedondo(SaldoIcon.LIXEIRA, "excluir ${tag.nome}", onClick = { excluindoId = tag.id })
                    }
                }
                Text(
                    "nova tag",
                    Modifier.fillMaxWidth().clickable { criando = true }.padding(horizontal = 16.dp, vertical = 12.dp),
                    style = SaldoTheme.type.body, color = colors.tint,
                )
            }
            if (state.tags.isNotEmpty()) {
                Text(
                    "toque numa tag para ver só ela no ledger",
                    style = SaldoTheme.type.footnote, color = colors.secondaryLabel,
                )
            }
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
                    if (nome.isNotBlank()) vm.criar(nome.trim(), PaletaTags.proxima(state.tags.map { it.first.cor }))
                    criando = false
                }) { Text("criar") }
            },
            dismissButton = { TextButton(onClick = { criando = false }) { Text("cancelar") } },
        )
    }

    renomeando?.let { tag ->
        var nome by remember(tag) { mutableStateOf(tag.nome) }
        var cor by remember(tag) { mutableStateOf(tag.cor) }
        AlertDialog(
            onDismissRequest = { renomeandoId = null },
            title = { Text("editar tag") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(value = nome, onValueChange = { nome = it }, singleLine = true)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        PaletaTags.cores.forEachIndexed { i, c ->
                            Box(
                                Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(Color(c))
                                    .then(if (c == cor) Modifier.border(3.dp, colors.label, CircleShape) else Modifier)
                                    .clickable { cor = c }
                                    .semantics { contentDescription = "cor ${i + 1}" },
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (nome.isNotBlank() && nome.trim() != tag.nome) vm.renomear(tag, nome.trim())
                    if (cor != tag.cor) vm.recolorir(tag, cor)
                    renomeandoId = null
                }) { Text("salvar") }
            },
            dismissButton = { TextButton(onClick = { renomeandoId = null }) { Text("cancelar") } },
        )
    }

    excluindo?.let { tag ->
        AlertDialog(
            onDismissRequest = { excluindoId = null },
            title = { Text("excluir \"${tag.nome}\"?") },
            // Verdade garantida pelo schema: os cross-refs saem por ON DELETE CASCADE,
            // as linhas de movimentação não.
            text = { Text("as movimentações continuam; só perdem essa tag.") },
            confirmButton = { TextButton(onClick = { vm.excluir(tag); excluindoId = null }) { Text("excluir") } },
            dismissButton = { TextButton(onClick = { excluindoId = null }) { Text("cancelar") } },
        )
    }
}
