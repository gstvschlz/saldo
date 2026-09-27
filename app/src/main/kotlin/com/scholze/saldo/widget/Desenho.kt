package com.scholze.saldo.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import androidx.core.graphics.createBitmap
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.layout.ContentScale
import androidx.glance.layout.fillMaxSize
import androidx.glance.unit.ColorProvider
import kotlin.math.max
import kotlin.math.roundToInt

/*
 * O Glance não tem Canvas: o anel do "guardou" e a curva do "ritmo" são bitmaps. Cada um é
 * desenhado em BRANCO sobre transparente e pintado com `ColorFilter.tint` — é isso que os deixa
 * seguir o claro/escuro do launcher sem redesenhar, como o resto do widget.
 */

private fun px(context: Context, dp: Dp): Int = max(1, (dp.value * context.resources.displayMetrics.density).roundToInt())

private fun tinta(espessura: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = android.graphics.Color.WHITE
    style = Paint.Style.STROKE
    strokeWidth = espessura
    strokeCap = Paint.Cap.ROUND
    strokeJoin = Paint.Join.ROUND
}

/** Um arco de [fracao] da volta, começando no topo; `1f` é o círculo inteiro. */
internal fun arco(context: Context, lado: Dp, espessura: Dp, fracao: Float): Bitmap {
    val l = px(context, lado)
    val e = px(context, espessura).toFloat()
    val bmp = createBitmap(l, l)
    val f = fracao.coerceIn(0f, 1f)
    if (f > 0f) {
        val caixa = RectF(e / 2, e / 2, l - e / 2, l - e / 2)
        val p = tinta(e).apply { if (f >= 1f) strokeCap = Paint.Cap.BUTT }
        Canvas(bmp).drawArc(caixa, -90f, 360f * f, false, p)
    }
    return bmp
}

/** As três camadas da curva do ritmo; cada uma recebe a sua cor na hora de pintar. */
internal class Curva(val area: Bitmap, val costume: Bitmap?, val linha: Bitmap)

/**
 * [mes] e [costume] em 0..1 (já na mesma escala), um ponto por dia decorrido; o eixo x é o mês
 * inteiro, de [diasNoMes] dias, para a curva mostrar também quanto do mês falta.
 */
internal fun curva(context: Context, largura: Dp, altura: Dp, mes: List<Float>, costume: List<Float>, diasNoMes: Int): Curva {
    val w = px(context, largura)
    val h = px(context, altura)
    val d = context.resources.displayMetrics.density
    val folga = 6 * d
    fun x(i: Int) = folga + (w - 2 * folga) * i / max(1, diasNoMes - 1)
    fun y(v: Float) = folga + (h - 2 * folga) * (1f - v.coerceIn(0f, 1f))
    fun caminho(s: List<Float>) = Path().apply { s.forEachIndexed { i, v -> if (i == 0) moveTo(x(i), y(v)) else lineTo(x(i), y(v)) } }

    val area = createBitmap(w, h)
    if (mes.size > 1) {
        val p = caminho(mes).apply { lineTo(x(mes.lastIndex), h.toFloat()); lineTo(x(0), h.toFloat()); close() }
        Canvas(area).drawPath(p, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.argb(40, 255, 255, 255) })
    }
    val linhaCostume = if (costume.size > 1) {
        createBitmap(w, h).also {
            Canvas(it).drawPath(caminho(costume), tinta(1.5f * d).apply { pathEffect = DashPathEffect(floatArrayOf(4 * d, 4 * d), 0f) })
        }
    } else null
    val linha = createBitmap(w, h)
    if (mes.isNotEmpty()) {
        val c = Canvas(linha)
        if (mes.size > 1) c.drawPath(caminho(mes), tinta(2.5f * d))
        c.drawCircle(x(mes.lastIndex), y(mes.last()), 4.5f * d, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE })
    }
    return Curva(area, linhaCostume, linha)
}

/** Um bitmap branco pintado com [cor], esticado na caixa. */
@Composable
internal fun Pintado(bitmap: Bitmap, cor: ColorProvider, modifier: GlanceModifier = GlanceModifier.fillMaxSize()) {
    Image(
        provider = ImageProvider(bitmap),
        contentDescription = null,
        modifier = modifier,
        contentScale = ContentScale.FillBounds,
        colorFilter = ColorFilter.tint(cor),
    )
}
