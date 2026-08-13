package com.scholze.saldo.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The tab-bar and chrome glyphs are drawn rather than imported, so the app
 * carries no icon-font dependency and the strokes match the canvas SVGs.
 */
enum class SaldoIcon { SALDOS, TOTAIS, TAGS, MAIS, PLUS, CHEVRON_LEFT, CHEVRON_RIGHT, BACKSPACE, RECORRENTE }

@Composable
fun SaldoGlyph(
    icon: SaldoIcon,
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = 26.dp,
    strokeWidth: Dp = 1.9.dp,
) {
    Canvas(modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val sw = strokeWidth.toPx()
        val stroke = Stroke(width = sw, cap = StrokeCap.Round)

        when (icon) {
            // Three stacked bars of decreasing length: a ledger.
            SaldoIcon.SALDOS -> {
                val lengths = listOf(0.68f, 0.52f, 0.80f)
                lengths.forEachIndexed { i, len ->
                    val y = h * (0.28f + i * 0.22f)
                    drawLine(
                        tint,
                        Offset(w * 0.14f, y),
                        Offset(w * (0.14f + len * 0.86f), y),
                        sw,
                        StrokeCap.Round,
                    )
                }
            }
            // A donut with a wedge marked off.
            SaldoIcon.TOTAIS -> {
                val inset = sw / 2 + w * 0.14f
                drawCircle(tint, radius = w / 2 - inset, style = stroke)
                drawLine(
                    tint,
                    Offset(w / 2, h / 2),
                    Offset(w / 2, inset),
                    sw,
                    StrokeCap.Round,
                )
                drawLine(
                    tint,
                    Offset(w / 2, h / 2),
                    Offset(w - inset, h / 2),
                    sw,
                    StrokeCap.Round,
                )
            }
            // A luggage-tag pentagon with an eyelet.
            SaldoIcon.TAGS -> {
                val p = Path().apply {
                    moveTo(w * 0.16f, h * 0.20f)
                    lineTo(w * 0.56f, h * 0.20f)
                    lineTo(w * 0.86f, h * 0.50f)
                    lineTo(w * 0.52f, h * 0.84f)
                    lineTo(w * 0.16f, h * 0.50f)
                    close()
                }
                drawPath(p, tint, style = stroke)
                drawCircle(tint, radius = w * 0.055f, center = Offset(w * 0.33f, h * 0.37f))
            }
            // Ellipsis in a circle.
            SaldoIcon.MAIS -> {
                drawCircle(tint, radius = w / 2 - sw / 2 - w * 0.10f, style = stroke)
                listOf(0.34f, 0.5f, 0.66f).forEach { fx ->
                    drawCircle(tint, radius = w * 0.048f, center = Offset(w * fx, h * 0.5f))
                }
            }
            SaldoIcon.PLUS -> {
                val inset = w * 0.26f
                drawLine(tint, Offset(w / 2, inset), Offset(w / 2, h - inset), sw, StrokeCap.Round)
                drawLine(tint, Offset(inset, h / 2), Offset(w - inset, h / 2), sw, StrokeCap.Round)
            }
            SaldoIcon.CHEVRON_LEFT -> {
                drawLine(tint, Offset(w * 0.62f, h * 0.20f), Offset(w * 0.34f, h * 0.5f), sw, StrokeCap.Round)
                drawLine(tint, Offset(w * 0.34f, h * 0.5f), Offset(w * 0.62f, h * 0.80f), sw, StrokeCap.Round)
            }
            SaldoIcon.CHEVRON_RIGHT -> {
                drawLine(tint, Offset(w * 0.38f, h * 0.20f), Offset(w * 0.66f, h * 0.5f), sw, StrokeCap.Round)
                drawLine(tint, Offset(w * 0.66f, h * 0.5f), Offset(w * 0.38f, h * 0.80f), sw, StrokeCap.Round)
            }
            // Delete-left: a pentagon shell with an x inside.
            SaldoIcon.BACKSPACE -> {
                val body = Path().apply {
                    moveTo(w * 0.34f, h * 0.24f)
                    lineTo(w * 0.90f, h * 0.24f)
                    lineTo(w * 0.90f, h * 0.76f)
                    lineTo(w * 0.34f, h * 0.76f)
                    lineTo(w * 0.10f, h * 0.50f)
                    close()
                }
                drawPath(body, tint, style = Stroke(width = sw))
                val x0 = w * 0.50f
                val x1 = w * 0.74f
                drawLine(tint, Offset(x0, h * 0.40f), Offset(x1, h * 0.60f), sw, StrokeCap.Round)
                drawLine(tint, Offset(x1, h * 0.40f), Offset(x0, h * 0.60f), sw, StrokeCap.Round)
            }
            // Circular arrow: an open arc with an arrowhead — marks recurrence/fatura rows.
            SaldoIcon.RECORRENTE -> {
                val inset = sw / 2 + w * 0.16f
                drawArc(
                    color = tint,
                    startAngle = -60f,
                    sweepAngle = 270f,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = androidx.compose.ui.geometry.Size(w - 2 * inset, h - 2 * inset),
                    style = stroke,
                )
                drawLine(tint, Offset(w * 0.72f, h * 0.12f), Offset(w * 0.84f, h * 0.26f), sw, StrokeCap.Round)
                drawLine(tint, Offset(w * 0.84f, h * 0.26f), Offset(w * 0.68f, h * 0.32f), sw, StrokeCap.Round)
            }
        }
    }
}
