package com.scholze.saldo.ui.totais

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.scholze.saldo.domain.PontoMes
import com.scholze.saldo.ui.components.InsetGroup
import com.scholze.saldo.ui.components.InsetRow
import com.scholze.saldo.ui.privacy.FormatoMoney
import com.scholze.saldo.ui.privacy.MoneyText
import com.scholze.saldo.ui.theme.SaldoTheme
import com.scholze.saldo.ui.totais.charts.ReservaLine
import com.scholze.saldo.ui.totais.charts.TrendChart
import com.scholze.saldo.ui.totais.charts.coresTendencia
import java.time.YearMonth

// `ptBr`/`mesCurto` vêm de Formatos.kt (mesmo pacote, sem import).

/**
 * Segmento "tendência": os 6 meses (entradas, saídas, sobrou) e a poupança (reserva + taxa).
 *
 * Emite duas seções irmãs direto no Column do chamador (que já espaça os itens), não uma raiz
 * só — [modifier] cai na primeira seção, "6 MESES", que é sempre renderizada.
 */
@Composable
fun SegmentoTendencia(
    pontos: List<PontoMes>,
    mesDestacado: YearMonth,
    onMes: (YearMonth) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SaldoTheme.colors
    // Mesmo mapeamento série → cor usado dentro do Canvas de TrendChart — uma fonte só, para a
    // legenda nunca poder descrever cores que o gráfico já não está mais desenhando.
    val cores = coresTendencia(colors)

    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("6 MESES", style = SaldoTheme.type.sectionHeader, color = colors.secondaryLabel)
        InsetGroup {
            TrendChart(pontos, mesDestacado, onMes, Modifier.padding(start = 12.dp, end = 12.dp, top = 14.dp, bottom = 6.dp))
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Legenda(cores.saidas, "saídas")
                Legenda(cores.entradas, "entradas")
                Legenda(cores.sobrou, "sobrou")
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("POUPANÇA", style = SaldoTheme.type.sectionHeader, color = colors.secondaryLabel)
        InsetGroup {
            val atual = pontos.lastOrNull()
            // InsetRow (não uma Row copiada à mão) — o mesmo helper usado no resto do app, com o
            // `trailing` slot passando `formato = ASSINADO` explícito: é o mesmo metric que a linha
            // idêntica do segmento "mês" (LinhaValor, também ASSINADO), então as duas não podem
            // voltar a divergir de formato. De brinde, o `defaultMinSize(44.dp)` de InsetRow cobre
            // o alvo de toque que esta linha não tinha.
            InsetRow(
                label = "reserva acumulada",
                trailing = {
                    MoneyText(
                        centavos = atual?.reservaAcumulada ?: 0L,
                        style = SaldoTheme.type.body,
                        color = colors.balance,
                        formato = FormatoMoney.ASSINADO,
                    )
                },
            )
            ReservaLine(pontos.map { it.reservaAcumulada }, Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp))
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
private fun Legenda(cor: Color, rotulo: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.size(8.dp).background(cor, CircleShape))
        Text(rotulo, style = SaldoTheme.type.caption, color = SaldoTheme.colors.secondaryLabel)
    }
}
