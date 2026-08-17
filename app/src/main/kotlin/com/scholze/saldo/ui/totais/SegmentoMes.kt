package com.scholze.saldo.ui.totais

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.scholze.saldo.domain.Fatia
import com.scholze.saldo.domain.GrupoGasto
import com.scholze.saldo.domain.Movimentacao
import com.scholze.saldo.domain.Padroes
import com.scholze.saldo.domain.ParaOndeFoi
import com.scholze.saldo.domain.Tag
import com.scholze.saldo.ui.components.HairlineDivider
import com.scholze.saldo.ui.components.InsetGroup
import com.scholze.saldo.ui.components.InsetRow
import com.scholze.saldo.ui.privacy.FormatoMoney
import com.scholze.saldo.ui.privacy.MoneyText
import com.scholze.saldo.ui.theme.SaldoColors
import com.scholze.saldo.ui.theme.SaldoTheme
import com.scholze.saldo.ui.totais.charts.SegmentedBar
import com.scholze.saldo.ui.totais.charts.WeekdayBars
import java.time.DayOfWeek

/** As três seções de insight do segmento "mês": para onde foi, maiores gastos, padrões. */
@Composable
fun SegmentoMesInsights(p: ParaOndeFoi, onVerTag: (Tag) -> Unit, onAbrirMovimentacao: (Movimentacao) -> Unit) {
    val colors = SaldoTheme.colors

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("PARA ONDE FOI", Modifier.weight(1f), style = SaldoTheme.type.sectionHeader, color = colors.secondaryLabel)
            if (p.saidasCentavos > 0) {
                Text("saídas ", style = SaldoTheme.type.footnote, color = colors.secondaryLabel)
                MoneyText(centavos = p.saidasCentavos, style = SaldoTheme.type.footnote, color = colors.secondaryLabel)
            }
        }
        InsetGroup {
            if (p.saidasCentavos == 0L) {
                InsetRow(label = "nenhuma saída este mês")
            } else {
                SegmentedBar(
                    shares = p.barra.map { it.share },
                    cores = p.barra.map { corDe(it.grupo, colors) },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
                p.fatias.forEach { f ->
                    HairlineDivider(startIndent = 16.dp)
                    LinhaFatia(f, onClick = (f.grupo as? GrupoGasto.DeTag)?.let { g -> { onVerTag(g.tag) } })
                }
            }
        }
    }

    if (p.maioresGastos.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("MAIORES GASTOS", style = SaldoTheme.type.sectionHeader, color = colors.secondaryLabel)
            InsetGroup {
                p.maioresGastos.forEachIndexed { i, mov ->
                    if (i > 0) HairlineDivider(startIndent = 16.dp)
                    Row(
                        Modifier.fillMaxWidth().clickable { onAbrirMovimentacao(mov) }.padding(horizontal = 16.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            mov.data.dayOfMonth.toString().padStart(2, '0'),
                            style = SaldoTheme.type.footnote, color = colors.secondaryLabel,
                        )
                        Text(mov.descricao, Modifier.weight(1f), style = SaldoTheme.type.body, color = colors.label)
                        MoneyText(centavos = mov.valorCentavos, style = SaldoTheme.type.body, color = colors.label, formato = FormatoMoney.ASSINADO)
                    }
                }
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("PADRÕES", style = SaldoTheme.type.sectionHeader, color = colors.secondaryLabel)
        InsetGroup {
            PadroesRows(p.padroes)
        }
    }
}

@Composable
private fun LinhaFatia(f: Fatia, onClick: (() -> Unit)?) {
    val colors = SaldoTheme.colors
    val base = Modifier.fillMaxWidth()
    Row(
        (if (onClick != null) base.clickable(onClick = onClick) else base).padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(8.dp).background(corDe(f.grupo, colors), CircleShape))
        Text(nomeDe(f.grupo), Modifier.weight(1f), style = SaldoTheme.type.body, color = colors.label)
        MoneyText(centavos = -f.centavos, style = SaldoTheme.type.body, color = colors.label, formato = FormatoMoney.ASSINADO)
        // Variação contra o mês anterior: gastar mais é vinho, menos é verde, "novo" e "=" neutros.
        val delta = f.deltaPercent
        Text(
            when {
                delta == null -> "novo"
                delta == 0 -> "="
                delta > 0 -> "+$delta%"
                else -> "−${-delta}%"
            },
            Modifier.width(44.dp),
            style = SaldoTheme.type.footnote,
            color = when {
                delta == null || delta == 0 -> colors.secondaryLabel
                delta > 0 -> colors.categoryVariable
                else -> colors.positive
            },
        )
    }
}

@Composable
private fun PadroesRows(pd: Padroes) {
    val colors = SaldoTheme.colors
    Column(Modifier.padding(horizontal = 16.dp, vertical = 11.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val dia = pd.diaMaisCaro
        if (dia == null) {
            Text("ainda sem padrão", style = SaldoTheme.type.body, color = colors.secondaryLabel)
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${nomeDia(dia)} é o dia mais caro", Modifier.weight(1f), style = SaldoTheme.type.body, color = colors.label)
                Text("média ", style = SaldoTheme.type.footnote, color = colors.secondaryLabel)
                MoneyText(centavos = pd.porDiaDaSemana[dia] ?: 0L, style = SaldoTheme.type.footnote, color = colors.secondaryLabel)
            }
        }
        WeekdayBars(pd.porDiaDaSemana, dia)
    }
    HairlineDivider(startIndent = 16.dp)
    InsetRow(
        label = "avulsas por dia este mês",
        trailing = {
            val v = pd.avulsasPorDiaMes
            if (v == null) Text("—", style = SaldoTheme.type.body, color = colors.secondaryLabel)
            else MoneyText(centavos = v, style = SaldoTheme.type.body, color = colors.secondaryLabel)
        },
    )
    HairlineDivider(startIndent = 16.dp)
    InsetRow(
        label = "média 30 dias (a da projeção)",
        trailing = { MoneyText(centavos = pd.mediaDiaria30, style = SaldoTheme.type.body, color = colors.secondaryLabel) },
    )
}

private fun corDe(g: GrupoGasto, colors: SaldoColors): Color = when (g) {
    is GrupoGasto.DeTag -> Color(g.tag.cor)
    GrupoGasto.Outras -> colors.insightOutras
    GrupoGasto.SemTag -> colors.insightSemTag
}

private fun nomeDe(g: GrupoGasto): String = when (g) {
    is GrupoGasto.DeTag -> g.tag.nome
    GrupoGasto.Outras -> "outras"
    GrupoGasto.SemTag -> "sem tag"
}

private fun nomeDia(d: DayOfWeek): String = when (d) {
    DayOfWeek.MONDAY -> "segunda"
    DayOfWeek.TUESDAY -> "terça"
    DayOfWeek.WEDNESDAY -> "quarta"
    DayOfWeek.THURSDAY -> "quinta"
    DayOfWeek.FRIDAY -> "sexta"
    DayOfWeek.SATURDAY -> "sábado"
    DayOfWeek.SUNDAY -> "domingo"
}
