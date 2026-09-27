package com.scholze.saldo.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalSize
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.scholze.saldo.domain.GrupoGasto
import com.scholze.saldo.domain.InsightsEngine
import com.scholze.saldo.ui.nav.Destino
import com.scholze.saldo.ui.totais.charts.ChartMath
import java.time.YearMonth
import kotlin.math.roundToInt
import androidx.glance.unit.ColorProvider as ProvedorDeCor

sealed interface ParaOndeFoiEstado {
    data object SemOnboarding : ParaOndeFoiEstado
    data object Falha : ParaOndeFoiEstado

    /** [fatias] vazio = nada saiu ainda neste mês. */
    data class Pronto(val mes: YearMonth, val fatias: List<Segmento>) : ParaOndeFoiEstado

    /** [cor] ARGB; `0L` = "outras", `-1L` = "sem tag". [percent] é o rótulo, [fracao] o tamanho. */
    data class Segmento(val nome: String, val cor: Long, val percent: Int, val fracao: Float)
}

/**
 * O treemap em no máximo três blocos, porque o Glance só empilha linhas e colunas: a maior fatia
 * é uma coluna inteira à esquerda; o resto divide a direita em duas linhas — as duas seguintes em
 * cima e as outras embaixo (com três fatias, uma em cima e uma embaixo).
 */
internal data class Treemap(val esquerda: Int, val cima: List<Int>, val baixo: List<Int>)

internal fun treemapDe(n: Int): Treemap = when {
    n <= 1 -> Treemap(0, emptyList(), emptyList())
    n == 2 -> Treemap(0, listOf(1), emptyList())
    n == 3 -> Treemap(0, listOf(1), listOf(2))
    else -> Treemap(0, listOf(1, 2), (3 until n).toList())
}

/** "para onde foi": as saídas do mês por tag, em porcentagem, num treemap que enche a caixa. */
class ParaOndeFoiWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val estado = when (val carga = carregarWidget(context)) {
            Carga.SemOnboarding -> ParaOndeFoiEstado.SemOnboarding
            Carga.Falha -> ParaOndeFoiEstado.Falha
            is Carga.Pronto -> {
                val p = InsightsEngine.paraOndeFoi(carga.input, carga.mes)
                // A MESMA matemática da aba totais: piso de 2 % e renormalização.
                val larguras = ChartMath.larguras(p.barra.map { it.share })
                ParaOndeFoiEstado.Pronto(
                    mes = carga.mes,
                    fatias = if (p.saidasCentavos <= 0) emptyList() else p.barra.mapIndexed { i, f ->
                        ParaOndeFoiEstado.Segmento(f.grupo.nome, corDoGrupo(f.grupo), (f.share * 100).roundToInt(), larguras.getOrElse(i) { 0f })
                    },
                )
            }
        }
        provideContent { ParaOndeFoiWidgetContent(estado) }
    }
}

class ParaOndeFoiWidgetReceiver : SaldoWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ParaOndeFoiWidget()
}

private fun corDoGrupo(g: GrupoGasto): Long = when (g) {
    is GrupoGasto.DeTag -> g.tag.cor
    GrupoGasto.Outras -> 0L
    GrupoGasto.SemTag -> -1L
}

private val tintaEscura = Color(0xFF191D18)

private fun fundoDe(cor: Long): ProvedorDeCor = when (cor) {
    0L -> CoresWidget.outras
    -1L -> CoresWidget.trilha
    else -> ColorProvider(day = Color(cor), night = Color(cor))
}

/** O texto sobre o bloco: escuro nas cores claras, branco nas escuras. */
private fun tintaDe(cor: Long): ProvedorDeCor = when (cor) {
    0L -> ColorProvider(day = Color.White, night = tintaEscura)
    -1L -> ColorProvider(day = tintaEscura, night = Color.White)
    else -> {
        val c = Color(cor)
        val luz = 0.2126f * c.red + 0.7152f * c.green + 0.0722f * c.blue
        if (luz > 0.33f) ColorProvider(day = tintaEscura, night = tintaEscura) else ColorProvider(day = Color.White, night = Color.White)
    }
}

@Composable
fun ParaOndeFoiWidgetContent(estado: ParaOndeFoiEstado) {
    val formato = formatoDe(LocalSize.current)
    val pronto = estado as? ParaOndeFoiEstado.Pronto
    val descricao = pronto?.fatias?.take(3)?.joinToString(prefix = "para onde foi: ") { "${it.nome} ${it.percent}%" } ?: "para onde foi"
    Moldura(Destino.Totais(pronto?.mes ?: mesDeHoje()), formato, descricao) {
        when (estado) {
            ParaOndeFoiEstado.SemOnboarding -> Aviso("toque para começar")
            ParaOndeFoiEstado.Falha -> Aviso("não foi possível carregar")
            is ParaOndeFoiEstado.Pronto -> if (estado.fatias.isEmpty()) Aviso("nada saiu ainda") else CorpoParaOndeFoi(estado, formato)
        }
    }
}

@Composable
private fun androidx.glance.layout.ColumnScope.CorpoParaOndeFoi(e: ParaOndeFoiEstado.Pronto, formato: Formato) {
    val tamanho = LocalSize.current
    val m = margemDe(formato)
    val w = tamanho.width - m * 2
    val h = tamanho.height - m * 2
    when (formato) {
        Formato.MINI -> Mapa(e.fatias, w, h, 2.dp, rotulos = false)
        Formato.LINHA -> {
            val quantas = if (tamanho.width >= 280.dp) 3 else if (tamanho.width >= 200.dp) 2 else 1
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("para onde foi", modifier = GlanceModifier.defaultWeight(), style = TextStyle(color = CoresWidget.label, fontSize = 13.sp, fontWeight = FontWeight.Medium), maxLines = 1)
                e.fatias.take(quantas).forEach {
                    Spacer(GlanceModifier.width(10.dp))
                    Box(GlanceModifier.size(8.dp).background(fundoDe(it.cor)).cornerRadius(2.dp)) {}
                    Spacer(GlanceModifier.width(4.dp))
                    Meta("${it.nome} ${it.percent}%")
                }
            }
            Vao()
            Row(GlanceModifier.fillMaxWidth().defaultWeight().cornerRadius(10.dp)) {
                e.fatias.forEach {
                    Box(GlanceModifier.width(w * it.fracao).fillMaxHeight().background(fundoDe(it.cor))) {}
                }
            }
        }
        else -> {
            Cabecalho("para onde foi", if (formato == Formato.QUADRADO) null else e.mes.format(nomeDoMes))
            Vao()
            Mapa(e.fatias, w, h - 24.dp, 3.dp, rotulos = true)
        }
    }
}

@Composable
private fun Mapa(fatias: List<ParaOndeFoiEstado.Segmento>, w: Dp, h: Dp, vao: Dp, rotulos: Boolean) {
    val t = treemapDe(fatias.size)
    val a = fatias[t.esquerda]
    val cima = t.cima.map { fatias[it] }
    val baixo = t.baixo.map { fatias[it] }
    val resto = (cima + baixo).sumOf { it.fracao.toDouble() }.toFloat()
    if (resto <= 0f) {
        Bloco(a, w, h, rotulos)
        return
    }
    val wa = (w - vao) * (a.fracao / (a.fracao + resto))
    val wr = w - vao - wa
    val sCima = cima.sumOf { it.fracao.toDouble() }.toFloat()
    val sBaixo = baixo.sumOf { it.fracao.toDouble() }.toFloat()
    val hCima = if (baixo.isEmpty()) h else (h - vao) * (sCima / (sCima + sBaixo))
    Row(GlanceModifier.fillMaxSize()) {
        Bloco(a, wa, h, rotulos)
        Spacer(GlanceModifier.width(vao))
        Column(GlanceModifier.fillMaxHeight().width(wr)) {
            Linha(cima, wr, hCima, sCima, vao, rotulos)
            if (baixo.isNotEmpty()) {
                Spacer(GlanceModifier.height(vao))
                Linha(baixo, wr, h - vao - hCima, sBaixo, vao, rotulos)
            }
        }
    }
}

@Composable
private fun Linha(fatias: List<ParaOndeFoiEstado.Segmento>, w: Dp, h: Dp, soma: Float, vao: Dp, rotulos: Boolean) {
    val util = w - vao * (fatias.size - 1)
    Row(GlanceModifier.fillMaxWidth().height(h)) {
        fatias.forEachIndexed { i, f ->
            if (i > 0) Spacer(GlanceModifier.width(vao))
            Bloco(f, util * (f.fracao / soma), h, rotulos)
        }
    }
}

@Composable
private fun Bloco(f: ParaOndeFoiEstado.Segmento, w: Dp, h: Dp, rotulos: Boolean) {
    val tinta = tintaDe(f.cor)
    Box(
        GlanceModifier.width(w).height(h).background(fundoDe(f.cor)).cornerRadius(if (rotulos) 8.dp else 4.dp)
            .padding(horizontal = if (rotulos) 8.dp else 0.dp, vertical = if (rotulos) 6.dp else 0.dp),
        contentAlignment = Alignment.BottomStart,
    ) {
        if (rotulos && w >= 32.dp && h >= 24.dp) {
            Column {
                if (w >= 58.dp && h >= 40.dp) {
                    Text(f.nome, style = TextStyle(color = tinta, fontSize = 11.5.sp, fontWeight = FontWeight.Medium), maxLines = 1)
                }
                Text("${f.percent}%", style = TextStyle(color = tinta, fontSize = 15.sp, fontWeight = FontWeight.Bold), maxLines = 1)
            }
        }
    }
}
