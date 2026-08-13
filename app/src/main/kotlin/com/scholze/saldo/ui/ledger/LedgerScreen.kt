package com.scholze.saldo.ui.ledger

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.scholze.saldo.model.DiaSaldo
import com.scholze.saldo.model.Categoria
import com.scholze.saldo.model.FiltroLedger
import com.scholze.saldo.model.Mes
import com.scholze.saldo.model.filtrado
import com.scholze.saldo.model.formatarAssinado
import com.scholze.saldo.model.formatarAssinadoComSimbolo
import com.scholze.saldo.model.formatarComSimbolo
import com.scholze.saldo.model.formatarValor
import com.scholze.saldo.model.mesDeExemplo
import com.scholze.saldo.ui.components.HairlineDivider
import com.scholze.saldo.ui.components.SaldoGlyph
import com.scholze.saldo.ui.components.SaldoIcon
import com.scholze.saldo.ui.components.SegmentedControl
import com.scholze.saldo.ui.theme.SaldoTheme
import com.scholze.saldo.ui.theme.tabular
import java.math.BigDecimal

private val DAY_COLUMN = 34.dp
private val SALDO_COLUMN = 118.dp

/**
 * Option 1a — hero + dense day×saldo grid.
 *
 * The saldo column is heat-tinted: the higher the projected balance relative to
 * the rest of the visible month, the stronger the green.
 */
@Composable
fun LedgerScreen(
    mes: Mes,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val colors = SaldoTheme.colors
    var filtro by rememberSaveable { mutableStateOf(FiltroLedger.TODAS) }
    val visivel = remember(mes, filtro) { mes.filtrado(filtro) }
    val faixa = remember(visivel) { visivel.dias.saldoRange() }

    Column(modifier.fillMaxSize().background(colors.background)) {
        MonthNavBar(mes)

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = contentPadding,
        ) {
            item { BalanceHero(mes) }

            item {
                Box(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 6.dp)) {
                    SegmentedControl(
                        options = FiltroLedger.entries.map { it.rotulo },
                        selectedIndex = filtro.ordinal,
                        onSelect = { filtro = FiltroLedger.entries[it] },
                    )
                }
            }

            item { ColumnHeader() }

            item { HairlineDivider() }

            items(visivel.dias, key = { it.dia }) { dia ->
                DayRow(dia = dia, faixa = faixa)
                HairlineDivider()
            }
        }
    }
}

@Composable
private fun MonthNavBar(mes: Mes) {
    val colors = SaldoTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .background(colors.navBar)
            .padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SaldoGlyph(SaldoIcon.CHEVRON_LEFT, colors.tint, size = 18.dp, strokeWidth = 2.2.dp)
            Text(mes.anterior, style = SaldoTheme.type.body, color = colors.tint)
        }
        Text(
            mes.titulo,
            Modifier.weight(1f),
            style = SaldoTheme.type.navTitle,
            color = colors.label,
            textAlign = TextAlign.Center,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(mes.proximo, style = SaldoTheme.type.body, color = colors.tint)
            SaldoGlyph(SaldoIcon.CHEVRON_RIGHT, colors.tint, size = 18.dp, strokeWidth = 2.2.dp)
        }
    }
    HairlineDivider()
}

@Composable
private fun BalanceHero(mes: Mes) {
    val colors = SaldoTheme.colors
    Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 14.dp)) {
        Text(mes.projetadoEm, style = SaldoTheme.type.footnote, color = colors.secondaryLabel)
        Text(
            mes.saldoProjetado.formatarComSimbolo(),
            Modifier.padding(top = 2.dp),
            style = SaldoTheme.type.largeTitle.tabular,
            color = colors.label,
        )
        Row(
            Modifier.padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                mes.deltaNoMes.formatarAssinadoComSimbolo(),
                style = SaldoTheme.type.subhead.tabular,
                color = colors.positive,
            )
            Text("no mês", style = SaldoTheme.type.subhead, color = colors.secondaryLabel)
        }
    }
}

@Composable
private fun ColumnHeader() {
    val colors = SaldoTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 6.dp),
    ) {
        Header("DIA", Modifier.width(DAY_COLUMN))
        Header("MOVIMENTAÇÕES", Modifier.weight(1f))
        Header("SALDO", textAlign = TextAlign.End)
    }
}

@Composable
private fun Header(text: String, modifier: Modifier = Modifier, textAlign: TextAlign? = null) {
    Text(
        text,
        modifier,
        style = SaldoTheme.type.sectionHeader,
        color = SaldoTheme.colors.secondaryLabel,
        textAlign = textAlign,
    )
}

@Composable
private fun DayRow(dia: DiaSaldo, faixa: ClosedRange<BigDecimal>) {
    val colors = SaldoTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .background(colors.surface)
            .defaultMinSize(minHeight = 52.dp)
            // Lets the saldo cell stretch to whatever height the
            // movimentações column ends up needing.
            .height(IntrinsicSize.Min),
    ) {
        Text(
            dia.dia.toString().padStart(2, '0'),
            Modifier
                .width(DAY_COLUMN)
                .padding(start = 16.dp, top = 10.dp),
            style = SaldoTheme.type.row.tabular,
            color = colors.secondaryLabel,
        )

        Column(
            Modifier
                .weight(1f)
                .padding(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            if (dia.movimentacoes.isEmpty()) {
                Text(
                    "sem movimentações",
                    style = SaldoTheme.type.row,
                    color = colors.secondaryLabel,
                )
            } else {
                dia.movimentacoes.forEach { mov ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        Box(
                            Modifier
                                .size(7.dp)
                                .background(
                                    when (mov.categoria) {
                                        Categoria.VARIAVEL -> colors.categoryVariable
                                        Categoria.FIXA -> colors.categoryFixed
                                    },
                                    CircleShape,
                                ),
                        )
                        Text(mov.descricao, style = SaldoTheme.type.row, color = colors.label)
                        Text(
                            mov.valor.formatarAssinado(),
                            Modifier.weight(1f),
                            style = SaldoTheme.type.row.tabular,
                            color = colors.label,
                            textAlign = TextAlign.End,
                        )
                    }
                }
            }
        }

        Box(
            Modifier
                .padding(start = 14.dp)
                .width(SALDO_COLUMN)
                .fillMaxHeight()
                .background(heatTint(dia.saldo, faixa))
                .padding(end = 16.dp),
            contentAlignment = Alignment.CenterEnd,
        ) {
            Text(
                dia.saldo.formatarValor(),
                style = SaldoTheme.type.row.tabular,
                color = colors.balance,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun heatTint(saldo: BigDecimal, faixa: ClosedRange<BigDecimal>): Color {
    val colors = SaldoTheme.colors
    val min = faixa.start
    val max = faixa.endInclusive
    if (max <= min) return colors.balanceTint2
    val ratio = (saldo - min).toDouble() / (max - min).toDouble()
    return when {
        ratio < 0.34 -> colors.balanceTint1
        ratio < 0.67 -> colors.balanceTint2
        else -> colors.balanceTint3
    }
}

private fun List<DiaSaldo>.saldoRange(): ClosedRange<BigDecimal> {
    if (isEmpty()) return BigDecimal.ZERO..BigDecimal.ZERO
    val saldos = map { it.saldo }
    return saldos.min()..saldos.max()
}

@Preview(showBackground = true, heightDp = 900)
@Composable
private fun LedgerLightPreview() {
    SaldoTheme(darkTheme = false) { LedgerScreen(mesDeExemplo) }
}

@Preview(showBackground = true, heightDp = 900)
@Composable
private fun LedgerDarkPreview() {
    SaldoTheme(darkTheme = true) { LedgerScreen(mesDeExemplo) }
}
