package com.scholze.saldo.ui.totais

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.scholze.saldo.domain.PontoMes
import com.scholze.saldo.ui.components.HairlineDivider
import com.scholze.saldo.ui.components.InsetGroup
import com.scholze.saldo.ui.components.InsetRow
import com.scholze.saldo.ui.privacy.MoneyText
import com.scholze.saldo.ui.theme.SaldoTheme
import com.scholze.saldo.ui.totais.charts.ReservaLine
import com.scholze.saldo.ui.totais.charts.TrendChart
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

private val ptBr = Locale.forLanguageTag("pt-BR")
private val mesCurto = DateTimeFormatter.ofPattern("MMM", ptBr)

/** Segmento "tendência": os 6 meses (entradas, saídas, sobrou) e a poupança (reserva + taxa). */
@Composable
fun SegmentoTendencia(pontos: List<PontoMes>, mesDestacado: YearMonth, onMes: (YearMonth) -> Unit) {
    val colors = SaldoTheme.colors

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("6 MESES", style = SaldoTheme.type.sectionHeader, color = colors.secondaryLabel)
        InsetGroup {
            TrendChart(pontos, mesDestacado, onMes, Modifier.padding(start = 12.dp, end = 12.dp, top = 14.dp, bottom = 6.dp))
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Legenda(colors.categoryVariable, "saídas")
                Legenda(colors.balance, "entradas")
                Legenda(colors.tint, "sobrou")
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("POUPANÇA", style = SaldoTheme.type.sectionHeader, color = colors.secondaryLabel)
        InsetGroup {
            val atual = pontos.lastOrNull()
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("reserva acumulada", Modifier.weight(1f), style = SaldoTheme.type.body, color = colors.label)
                MoneyText(centavos = atual?.reservaAcumulada ?: 0L, style = SaldoTheme.type.body, color = colors.balance)
            }
            ReservaLine(pontos.map { it.reservaAcumulada }, Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp))
            HairlineDivider(startIndent = 16.dp)
            val anterior = pontos.getOrNull(pontos.size - 2)
            InsetRow(
                label = "taxa de poupança",
                value = when {
                    atual?.taxaPoupanca == null -> "—"
                    anterior?.taxaPoupanca == null -> "${atual.taxaPoupanca}% este mês"
                    else -> "${atual.taxaPoupanca}% este mês (${anterior.mes.format(mesCurto).removeSuffix(".")} ${anterior.taxaPoupanca}%)"
                },
            )
        }
        Text(
            "taxa = economia do mês ÷ entradas do mês",
            Modifier.padding(horizontal = 16.dp),
            style = SaldoTheme.type.caption, color = colors.secondaryLabel,
        )
    }
}

@Composable
private fun Legenda(cor: androidx.compose.ui.graphics.Color, rotulo: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.size(8.dp).background(cor, CircleShape))
        Text(rotulo, style = SaldoTheme.type.caption, color = SaldoTheme.colors.secondaryLabel)
    }
}
