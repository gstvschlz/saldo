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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.scholze.saldo.domain.PontoMes
import com.scholze.saldo.ui.theme.SaldoColors
import com.scholze.saldo.ui.theme.SaldoTheme
import com.scholze.saldo.ui.totais.mesCurto
import java.time.DayOfWeek
import java.time.YearMonth

/**
 * As cores dos três papéis (saídas, entradas, sobrou) que [TrendChart] desenha e a legenda em
 * SegmentoTendencia.kt descreve — uma fonte só, para as duas nunca poderem descrever cores
 * diferentes das que o gráfico realmente usa.
 */
internal data class CoresTendencia(val saidas: Color, val entradas: Color, val sobrou: Color)

internal fun coresTendencia(colors: SaldoColors): CoresTendencia =
    CoresTendencia(saidas = colors.categoryVariable, entradas = colors.balance, sobrou = colors.tint)

/**
 * Sobrecarga em Dp de [ChartMath.pisoSeNaoZero] (a geometria mora lá, testável na JVM) — usada
 * onde a UI já pensa em dp, como a altura das barrinhas em [WeekdayBars]. [TrendChart] chama
 * [ChartMath.pisoSeNaoZero] direto, porque dentro do Canvas já está tudo em px.
 */
private fun pisoSeNaoZero(fracao: Float, cheio: Dp, piso: Dp): Dp =
    ChartMath.pisoSeNaoZero(fracao, cheio.value, piso.value).dp

/** A barra 100 % de "para onde foi": uma Row com pesos vindos de [ChartMath.larguras]. */
@Composable
fun SegmentedBar(shares: List<Float>, cores: List<Color>, modifier: Modifier = Modifier, altura: Dp = 12.dp) {
    val colors = SaldoTheme.colors
    val larguras = remember(shares) { ChartMath.larguras(shares) }
    Row(modifier.fillMaxWidth().height(altura).clip(RoundedCornerShape(altura / 2))) {
        larguras.forEachIndexed { i, w ->
            // `cores` pode chegar mais curta que `shares` (os dois caminhos de construção de
            // ParaOndeFoi não garantem o mesmo tamanho); getOrElse troca um crash por um neutro.
            if (w > 0f) Box(Modifier.fillMaxHeight().weight(w).background(cores.getOrElse(i) { colors.separator }))
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
    altura: Dp = 120.dp,
) {
    val colors = SaldoTheme.colors
    val n = pontos.size
    // Saídas e entradas dividem a escala: alturas calculadas sobre as duas séries juntas.
    val alturas = remember(pontos) { ChartMath.alturas(pontos.map { it.saidas } + pontos.map { it.entradas }) }
    val linha = remember(pontos) { ChartMath.linhaComSinal(pontos.map { it.sobrou }) }
    val linhaZero = remember(pontos) { ChartMath.linhaZero(pontos.map { it.sobrou }) }
    val cores = coresTendencia(colors)
    val corSaidas = cores.saidas
    val corEntradas = cores.entradas
    val corLinha = cores.sobrou
    val corFundoPonto = colors.surface
    val corBase = colors.separator
    // O gesto (pointerInput) só reinicia quando `pontos` muda — ele sobrevive à recomposição.
    // `onMes` não: se o lambda capturasse o parâmetro direto, o gesto ficaria preso na instância
    // de `onMes` da composição em que nasceu, enquanto o clique no rótulo do mês (fora do
    // pointerInput) sempre chama a instância atual. Dois toques que deveriam ser idênticos
    // acabariam chamando callbacks diferentes. rememberUpdatedState resolve isso.
    val onMesAtual = rememberUpdatedState(onMes)

    Column(modifier) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(altura)
                .pointerInput(pontos) {
                    detectTapGestures { o ->
                        if (n > 0) onMesAtual.value(pontos[(o.x / size.width * n).toInt().coerceIn(0, n - 1)].mes)
                    }
                },
        ) {
            if (n == 0) return@Canvas
            val colW = size.width / n
            val barW = colW * 0.28f
            val gap = colW * 0.06f
            val h = size.height
            val raio = CornerRadius(3.dp.toPx())
            val pisoPx = 2.dp.toPx()
            pontos.forEachIndexed { i, _ ->
                val x0 = i * colW + (colW - (2 * barW + gap)) / 2
                val hs = ChartMath.pisoSeNaoZero(alturas[i], h, pisoPx)
                val he = ChartMath.pisoSeNaoZero(alturas[n + i], h, pisoPx)
                if (hs > 0f) drawRoundRect(corSaidas, topLeft = Offset(x0, h - hs), size = Size(barW, hs), cornerRadius = raio)
                if (he > 0f) drawRoundRect(corEntradas, topLeft = Offset(x0 + barW + gap, h - he), size = Size(barW, he), cornerRadius = raio)
            }
            // A linha do sobrou fica entre 5 % e 95 % da altura, para os pontos não colarem nas bordas.
            val pts = linha.mapIndexed { i, y -> Offset(i * colW + colW / 2, h * 0.05f + h * 0.9f * (1f - y)) }
            // ChartMath.linhaComSinal inclui o zero na escala, então a posição da linha já carrega
            // o sinal (mês positivo sobe, negativo desce). Quando a série realmente mistura os
            // dois, uma régua no zero (sem número, só a linha) ajuda a localizar onde ele fica.
            if (linhaZero != null) {
                val yBase = h * 0.05f + h * 0.9f * (1f - linhaZero)
                drawLine(corBase, Offset(0f, yBase), Offset(size.width, yBase), strokeWidth = 1.dp.toPx())
            }
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
                // Alvo de toque real (48dp), não a caixa justa do texto footnote — mesmo padrão do
                // botão "voltar" em LembretesScreen.kt.
                Box(
                    Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .clickable(role = Role.Button) { onMes(p.mes) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        p.mes.format(mesCurto).removeSuffix("."),
                        style = SaldoTheme.type.footnote.copy(fontWeight = if (destacado) FontWeight.SemiBold else FontWeight.Normal),
                        color = if (destacado) colors.label else colors.secondaryLabel,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

/** Sete barrinhas seg…dom; [destaque] (o dia mais caro) na cor de saída, o resto neutro. */
@Composable
fun WeekdayBars(porDia: Map<DayOfWeek, Long>, destaque: DayOfWeek?, modifier: Modifier = Modifier, altura: Dp = 28.dp) {
    val colors = SaldoTheme.colors
    val dias = DayOfWeek.entries
    val alturas = remember(porDia) { ChartMath.alturas(dias.map { porDia[it] ?: 0L }) }
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
        dias.forEachIndexed { i, d ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(pisoSeNaoZero(alturas[i], altura, 2.dp))
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

/**
 * O ritmo do mês: o acumulado de saídas (linha cheia) contra o costume dos meses anteriores
 * (tracejada).
 *
 * As duas dividem a escala e a origem no zero, senão as formas ficariam iguais e a
 * comparação — que é o ponto — sumiria. A tracejada não aparece quando não há mês anterior
 * com que comparar: uma linha de referência inventada é pior do que nenhuma.
 */
@Composable
fun RitmoChart(
    acumulado: List<Long>,
    referencia: List<Long>,
    modifier: Modifier = Modifier,
    altura: Dp = 72.dp,
) {
    val colors = SaldoTheme.colors
    val (ysMes, ysRef) = remember(acumulado, referencia) {
        ChartMath.linhasNaMesmaEscala(acumulado, referencia)
    }
    Canvas(modifier.fillMaxWidth().height(altura)) {
        if (ysMes.size < 2) return@Canvas
        val passo = size.width / (ysMes.size - 1)
        fun pontos(ys: List<Float>) = ys.mapIndexed { i, y ->
            Offset(i * passo, size.height * 0.1f + size.height * 0.8f * (1f - y))
        }

        if (ysRef.size == ysMes.size) {
            val ref = pontos(ysRef)
            for (i in 0 until ref.size - 1) {
                drawLine(
                    colors.secondaryLabel,
                    ref[i],
                    ref[i + 1],
                    strokeWidth = 1.5.dp.toPx(),
                    cap = StrokeCap.Round,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())),
                )
            }
        }

        val mes = pontos(ysMes)
        for (i in 0 until mes.size - 1) {
            drawLine(colors.balance, mes[i], mes[i + 1], strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
        }
        drawCircle(colors.balance, radius = 4.dp.toPx(), center = mes.last())
    }
}

/** A reserva acumulada mês a mês: uma linha com o ponto final marcado. */
@Composable
fun ReservaLine(valores: List<Long>, modifier: Modifier = Modifier, altura: Dp = 56.dp) {
    val cor = SaldoTheme.colors.balance
    val ys = remember(valores) { ChartMath.linha(valores) }
    Canvas(modifier.fillMaxWidth().height(altura)) {
        if (ys.size < 2) return@Canvas
        val passo = size.width / (ys.size - 1)
        val pts = ys.mapIndexed { i, y -> Offset(i * passo, size.height * 0.1f + size.height * 0.8f * (1f - y)) }
        for (i in 0 until pts.size - 1) {
            drawLine(cor, pts[i], pts[i + 1], strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
        }
        drawCircle(cor, radius = 4.dp.toPx(), center = pts.last())
    }
}
