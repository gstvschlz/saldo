package com.scholze.saldo.ui.entry

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scholze.saldo.model.formatarCentavos
import com.scholze.saldo.model.formatarComSimbolo
import com.scholze.saldo.ui.components.FilledActionButton
import com.scholze.saldo.ui.components.HairlineDivider
import com.scholze.saldo.ui.components.InsetGroup
import com.scholze.saldo.ui.components.InsetRow
import com.scholze.saldo.ui.components.SaldoGlyph
import com.scholze.saldo.ui.components.SaldoIcon
import com.scholze.saldo.ui.components.SegmentedControl
import com.scholze.saldo.ui.theme.SaldoTheme
import com.scholze.saldo.ui.theme.tabular
import java.math.BigDecimal

private val TIPOS = listOf("entrada", "saída")
private val NATUREZAS = listOf("diário", "economia", "cartão")

/** Option 1d — HIG sheet: segmented type over a grouped-inset form. */
@Composable
fun NewEntrySheet(
    onCancel: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
    valor: BigDecimal = BigDecimal("238.50"),
    saldoResultante: BigDecimal = BigDecimal("112655.36"),
) {
    val colors = SaldoTheme.colors
    var tipo by rememberSaveable { mutableIntStateOf(1) }
    var natureza by rememberSaveable { mutableIntStateOf(0) }
    val descricao by rememberSaveable { mutableStateOf("mercado") }
    var centavos by rememberSaveable { mutableLongStateOf(valor.movePointRight(2).toLong()) }
    var editandoValor by rememberSaveable { mutableStateOf(false) }

    // Tapping the amount hands off to the 1g decimal pad.
    if (editandoValor) {
        AmountKeypadScreen(
            onContinue = {
                centavos = it
                editandoValor = false
            },
            initialCentavos = centavos,
            modifier = modifier,
        )
        return
    }

    Column(
        modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        SheetNavBar(onCancel = onCancel, onSave = onSave)

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            SegmentedControl(
                options = TIPOS,
                selectedIndex = tipo,
                onSelect = { tipo = it },
                modifier = Modifier.padding(top = 16.dp),
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NATUREZAS.forEachIndexed { index, rotulo ->
                    NaturezaChip(
                        text = rotulo,
                        selected = index == natureza,
                        onClick = { natureza = index },
                    )
                }
            }

            AmountBlock(
                centavos = centavos,
                legenda = if (tipo == 1) {
                    "gasto variável · sai do saldo hoje"
                } else {
                    "entrada · entra no saldo hoje"
                },
                onClick = { editandoValor = true },
            )

            InsetGroup {
                InsetRow(label = "descrição", value = descricao)
                HairlineDivider(startIndent = 16.dp)
                InsetRow(label = "data", value = "hoje, 20 jul")
                HairlineDivider(startIndent = 16.dp)
                InsetRow(label = "repetir", value = "não repete")
                HairlineDivider(startIndent = 16.dp)
                InsetRow(
                    label = "tags",
                    trailing = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            TagPill("comida")
                            Box(
                                Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(colors.segmentedTrack),
                                contentAlignment = Alignment.Center,
                            ) {
                                SaldoGlyph(SaldoIcon.PLUS, colors.tint, size = 16.dp, strokeWidth = 1.6.dp)
                            }
                        }
                    },
                )
            }

            FilledActionButton(
                text = "adicionar ${NATUREZAS[natureza]}",
                onClick = onSave,
            )

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                Text(
                    "saldo de hoje ficará em ",
                    style = SaldoTheme.type.footnote,
                    color = colors.secondaryLabel,
                )
                Text(
                    saldoResultante.formatarComSimbolo(),
                    style = SaldoTheme.type.footnote.tabular.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.balance,
                )
            }
        }
    }
}

@Composable
private fun SheetNavBar(onCancel: () -> Unit, onSave: () -> Unit) {
    val colors = SaldoTheme.colors
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .background(colors.navBar)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "cancelar",
                Modifier.clickable(onClick = onCancel),
                style = SaldoTheme.type.body,
                color = colors.tint,
            )
            Text(
                "nova movimentação",
                Modifier.weight(1f),
                style = SaldoTheme.type.navTitle,
                color = colors.label,
                textAlign = TextAlign.Center,
            )
            Text(
                "salvar",
                Modifier.clickable(onClick = onSave),
                style = SaldoTheme.type.body.copy(fontWeight = FontWeight.SemiBold),
                color = colors.tint,
            )
        }
        HairlineDivider()
    }
}

@Composable
private fun AmountBlock(centavos: Long, legenda: String, onClick: () -> Unit) {
    val colors = SaldoTheme.colors
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                "R$",
                Modifier.padding(bottom = 7.dp),
                style = SaldoTheme.type.navTitle,
                color = colors.secondaryLabel,
            )
            Text(
                centavos.formatarCentavos(),
                style = SaldoTheme.type.largeTitle.tabular.copy(fontSize = 44.sp),
                color = colors.label,
            )
        }
        Text(
            legenda,
            Modifier.padding(top = 6.dp),
            style = SaldoTheme.type.footnote,
            color = colors.secondaryLabel,
        )
    }
}

@Composable
private fun NaturezaChip(text: String, selected: Boolean, onClick: () -> Unit) {
    val colors = SaldoTheme.colors
    Box(
        Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) colors.tint else colors.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
    ) {
        Text(
            text,
            style = SaldoTheme.type.footnote.copy(
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            ),
            color = if (selected) androidx.compose.ui.graphics.Color.White else colors.label,
        )
    }
}

@Composable
private fun TagPill(text: String) {
    val colors = SaldoTheme.colors
    Box(
        Modifier
            .clip(RoundedCornerShape(11.dp))
            .background(colors.segmentedTrack)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(text, style = SaldoTheme.type.footnote, color = colors.label)
    }
}

@Preview(heightDp = 880)
@Composable
private fun NewEntryLightPreview() {
    SaldoTheme(darkTheme = false) { NewEntrySheet(onCancel = {}, onSave = {}) }
}

@Preview(heightDp = 880)
@Composable
private fun NewEntryDarkPreview() {
    SaldoTheme(darkTheme = true) { NewEntrySheet(onCancel = {}, onSave = {}) }
}
