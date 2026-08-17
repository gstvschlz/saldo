package com.scholze.saldo.ui.totais.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.scholze.saldo.domain.PontoMes
import com.scholze.saldo.ui.theme.SaldoTheme
import java.time.DayOfWeek
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

private val ptBr = Locale.forLanguageTag("pt-BR")
private val mesCurto = DateTimeFormatter.ofPattern("MMM", ptBr)

/** A barra 100 % de "para onde foi": uma Row com pesos vindos de [ChartMath.larguras]. */
@Composable
fun SegmentedBar(shares: List<Float>, cores: List<Color>, modifier: Modifier = Modifier, altura: Dp = 12.dp) {
    val larguras = remember(shares) { ChartMath.larguras(shares) }
    Row(modifier.fillMaxWidth().height(altura).clip(RoundedCornerShape(altura / 2))) {
        larguras.forEachIndexed { i, w ->
            if (w > 0f) Box(Modifier.fillMaxHeight().weight(w).background(cores[i]))
        }
    }
}

/**
 * Tendência: por mês, barra de saídas e barra de entradas (mesma escala) e a linha do sobrou por
 * cima (escala própria). Toque numa coluna ou no rótulo → [onMes].
 */
@Composable
fun TrendChart(
    pontos: List<PontoMes>,
    mesDestacado: YearMonth,
    onMes: (YearMonth) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SaldoTheme.colors
    val n = pontos.size
    // Saídas e entradas dividem a escala: alturas calculadas sobre as duas séries juntas.
    val alturas = remember(pontos) { ChartMath.alturas(pontos.map { it.saidas } + pontos.map { it.entradas }) }
    val linha = remember(pontos) { ChartMath.linha(pontos.map { it.sobrou }) }
    val corSaidas = colors.categoryVariable
    val corEntradas = colors.balance
    val corLinha = colors.tint
    val corFundoPonto = colors.surface

    Column(modifier) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(120.dp)
                .pointerInput(pontos) {
                    detectTapGestures { o ->
                        if (n > 0) onMes(pontos[(o.x / size.width * n).toInt().coerceIn(0, n - 1)].mes)
                    }
                },
        ) {
            if (n == 0) return@Canvas
            val colW = size.width / n
            val barW = colW * 0.28f
            val gap = colW * 0.06f
            val h = size.height
            val raio = CornerRadius(3.dp.toPx())
            pontos.forEachIndexed { i, _ ->
                val x0 = i * colW + (colW - (2 * barW + gap)) / 2
                val hs = alturas[i] * h
                val he = alturas[n + i] * h
                if (hs > 0f) drawRoundRect(corSaidas, topLeft = Offset(x0, h - hs), size = Size(barW, hs), cornerRadius = raio)
                if (he > 0f) drawRoundRect(corEntradas, topLeft = Offset(x0 + barW + gap, h - he), size = Size(barW, he), cornerRadius = raio)
            }
            // A linha do sobrou fica entre 5 % e 95 % da altura, para os pontos não colarem nas bordas.
            val pts = linha.mapIndexed { i, y -> Offset(i * colW + colW / 2, h * 0.05f + h * 0.9f * (1f - y)) }
            for (i in 0 until pts.size - 1) {
                drawLine(corLinha, pts[i], pts[i + 1], strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
            }
            pts.forEach {
                drawCircle(corLinha, radius = 3.5.dp.toPx(), center = it)
                drawCircle(corFundoPonto, radius = 1.5.dp.toPx(), center = it)
            }
        }
        Row(Modifier.fillMaxWidth()) {
            pontos.forEach { p ->
                val destacado = p.mes == mesDestacado
                Text(
                    p.mes.format(mesCurto).removeSuffix("."),
                    Modifier.weight(1f).clickable { onMes(p.mes) },
                    style = SaldoTheme.type.footnote.copy(fontWeight = if (destacado) FontWeight.SemiBold else FontWeight.Normal),
                    color = if (destacado) colors.label else colors.secondaryLabel,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** Sete barrinhas seg…dom; [destaque] (o dia mais caro) na cor de saída, o resto neutro. */
@Composable
fun WeekdayBars(porDia: Map<DayOfWeek, Long>, destaque: DayOfWeek?, modifier: Modifier = Modifier) {
    val colors = SaldoTheme.colors
    val dias = DayOfWeek.entries
    val alturas = remember(porDia) { ChartMath.alturas(dias.map { porDia[it] ?: 0L }) }
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
        dias.forEachIndexed { i, d ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(maxOf(2.dp, 28.dp * alturas[i]))
                        .clip(RoundedCornerShape(2.dp))
                        .background(if (d == destaque) colors.categoryVariable else colors.separator),
                )
                Text(rotulo(d), style = SaldoTheme.type.caption, color = colors.secondaryLabel)
            }
        }
    }
}

private fun rotulo(d: DayOfWeek): String = when (d) {
    DayOfWeek.MONDAY -> "s"
    DayOfWeek.TUESDAY -> "t"
    DayOfWeek.WEDNESDAY -> "q"
    DayOfWeek.THURSDAY -> "q"
    DayOfWeek.FRIDAY -> "s"
    DayOfWeek.SATURDAY -> "s"
    DayOfWeek.SUNDAY -> "d"
}

/** A reserva acumulada mês a mês: uma linha com o ponto final marcado. */
@Composable
fun ReservaLine(valores: List<Long>, modifier: Modifier = Modifier) {
    val cor = SaldoTheme.colors.balance
    val ys = remember(valores) { ChartMath.linha(valores) }
    Canvas(modifier.fillMaxWidth().height(56.dp)) {
        if (ys.size < 2) return@Canvas
        val passo = size.width / (ys.size - 1)
        val pts = ys.mapIndexed { i, y -> Offset(i * passo, size.height * 0.1f + size.height * 0.8f * (1f - y)) }
        for (i in 0 until pts.size - 1) {
            drawLine(cor, pts[i], pts[i + 1], strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
        }
        drawCircle(cor, radius = 4.dp.toPx(), center = pts.last())
    }
}
