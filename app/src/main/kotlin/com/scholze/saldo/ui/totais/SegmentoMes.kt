package com.scholze.saldo.ui.totais

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.scholze.saldo.domain.Fatia
import com.scholze.saldo.domain.GrupoGasto
import com.scholze.saldo.domain.Movimentacao
import com.scholze.saldo.domain.Padroes
import com.scholze.saldo.domain.ParaOndeFoi
import com.scholze.saldo.domain.TagsNoTempo
import com.scholze.saldo.domain.Tag
import com.scholze.saldo.ui.components.DescricaoTexto
import com.scholze.saldo.ui.components.InsetGroup
import com.scholze.saldo.ui.components.InsetRow
import com.scholze.saldo.ui.privacy.FormatoMoney
import com.scholze.saldo.ui.privacy.MoneyText
import com.scholze.saldo.ui.theme.SaldoColors
import com.scholze.saldo.ui.theme.SaldoTheme
import com.scholze.saldo.ui.totais.charts.Descricoes
import com.scholze.saldo.ui.totais.charts.SegmentedBar
import com.scholze.saldo.ui.totais.charts.TagsStack
import com.scholze.saldo.ui.totais.charts.WeekdayBars
import java.time.DayOfWeek

/**
 * As três seções de insight do segmento "mês": para onde foi, maiores gastos, padrões.
 *
 * Emite três seções irmãs direto no Column do chamador (que já espaça os itens), não uma raiz
 * só — [modifier] cai na primeira seção, "para onde foi", que é sempre renderizada.
 */
@Composable
fun SegmentoMesInsights(
    p: ParaOndeFoi,
    onVerTag: (Tag) -> Unit,
    onAbrirMovimentacao: (Movimentacao) -> Unit,
    modifier: Modifier = Modifier,
    noTempo: TagsNoTempo? = null,
) {
    val colors = SaldoTheme.colors

    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("para onde foi", Modifier.weight(1f), style = SaldoTheme.type.sectionHeader, color = colors.label)
            if (p.saidasCentavos > 0) {
                Text("saídas", style = SaldoTheme.type.footnote, color = colors.secondaryLabel)
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
                    // Porcentagem, não valor: é o que a barra desenha, e é o que sobrevive à
                    // privacidade — a proporção não diz quanto se gastou.
                    leitura = Descricoes.paraOndeFoi(
                        p.barra.map { it.grupo.nome },
                        p.barra.map { it.centavos },
                    ),
                )
                p.fatias.forEach { f ->
                    LinhaFatia(f, onClick = (f.grupo as? GrupoGasto.DeTag)?.let { g -> { onVerTag(g.tag) } })
                }
                // A barra acima é a composição DESTE mês; as colunas são a mesma pergunta
                // ao longo de seis, que é onde uma categoria crescendo aparece.
                if (noTempo != null && noTempo.grupos.isNotEmpty()) {
                    Text(
                        "nos últimos ${noTempo.meses.size} meses",
                        Modifier.padding(start = 16.dp, top = 14.dp),
                        style = SaldoTheme.type.caption,
                        color = colors.secondaryLabel,
                    )
                    TagsStack(
                        serie = noTempo,
                        cores = noTempo.grupos.map { corDe(it, colors) },
                        modifier = Modifier.padding(horizontal = 16.dp).padding(top = 6.dp, bottom = 12.dp),
                    )
                } else if (noTempo != null) {
                    Text(
                        "sem tags neste período",
                        Modifier.padding(start = 16.dp, top = 14.dp, bottom = 12.dp),
                        style = SaldoTheme.type.caption,
                        color = colors.secondaryLabel,
                    )
                }
            }
        }
    }

    if (p.maioresGastos.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("maiores gastos", style = SaldoTheme.type.sectionHeader, color = colors.label)
            InsetGroup {
                p.maioresGastos.forEachIndexed { i, mov ->
                    LinhaMaiorGasto(mov, onClick = { onAbrirMovimentacao(mov) })
                }
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("padrões", style = SaldoTheme.type.sectionHeader, color = colors.label)
        InsetGroup {
            PadroesRows(p.padroes)
        }
    }
}

@Composable
private fun LinhaFatia(f: Fatia, onClick: (() -> Unit)?) {
    val colors = SaldoTheme.colors
    val base = Modifier.fillMaxWidth().defaultMinSize(minHeight = 44.dp)
    Row(
        (if (onClick != null) base.clickable(onClick = onClick) else base).padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(8.dp).background(corDe(f.grupo, colors), CircleShape))
        Text(nomeDe(f.grupo), Modifier.weight(1f), style = SaldoTheme.type.body, color = colors.label)
        MoneyText(centavos = -f.centavos, style = SaldoTheme.type.body, color = colors.label, formato = FormatoMoney.ASSINADO)
        // Variação contra o mês anterior: gastar mais é vinho, menos é verde, "novo" e "=" neutros.
        // `deltaPercent` não tem teto (InsightsEngine.delta não limita %) — largura MÍNIMA em vez de
        // fixa, uma linha só e alinhado à direita, senão um "+4400%" ou "novo" em fonte grande quebra
        // e deixa essa fatia mais alta que as vizinhas.
        val delta = f.deltaPercent
        Text(
            when {
                delta == null -> "novo"
                delta == 0 -> "="
                delta > 0 -> "+$delta%"
                else -> "−${-delta}%"
            },
            Modifier.widthIn(min = 44.dp),
            maxLines = 1,
            textAlign = TextAlign.End,
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
private fun LinhaMaiorGasto(mov: Movimentacao, onClick: () -> Unit) {
    val colors = SaldoTheme.colors
    Row(
        Modifier.fillMaxWidth().defaultMinSize(minHeight = 44.dp).clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            mov.data.dayOfMonth.toString().padStart(2, '0'),
            style = SaldoTheme.type.footnote, color = colors.secondaryLabel,
        )
        DescricaoTexto(mov.descricao, Modifier.weight(1f))
        MoneyText(centavos = mov.valorCentavos, style = SaldoTheme.type.body, color = colors.label, formato = FormatoMoney.ASSINADO)
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
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("${nomeDia(dia)} é o dia mais caro", Modifier.weight(1f), style = SaldoTheme.type.body, color = colors.label)
                Text("média", style = SaldoTheme.type.footnote, color = colors.secondaryLabel)
                MoneyText(centavos = pd.porDiaDaSemana[dia] ?: 0L, style = SaldoTheme.type.footnote, color = colors.secondaryLabel)
            }
        }
        WeekdayBars(pd.porDiaDaSemana, dia)
    }
    InsetRow(
        label = "avulsas por dia este mês",
        trailing = {
            val v = pd.avulsasPorDiaMes
            if (v == null) Text("—", style = SaldoTheme.type.body, color = colors.secondaryLabel)
            else MoneyText(centavos = v, style = SaldoTheme.type.body, color = colors.secondaryLabel)
        },
    )
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

private fun nomeDe(g: GrupoGasto): String = g.nome

private fun nomeDia(d: DayOfWeek): String = when (d) {
    DayOfWeek.MONDAY -> "segunda"
    DayOfWeek.TUESDAY -> "terça"
    DayOfWeek.WEDNESDAY -> "quarta"
    DayOfWeek.THURSDAY -> "quinta"
    DayOfWeek.FRIDAY -> "sexta"
    DayOfWeek.SATURDAY -> "sábado"
    DayOfWeek.SUNDAY -> "domingo"
}
