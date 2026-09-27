package com.scholze.saldo.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.width
import com.scholze.saldo.domain.Ritmo
import com.scholze.saldo.domain.RitmoEngine
import com.scholze.saldo.ui.nav.Destino
import java.time.YearMonth

sealed interface RitmoWidgetEstado {
    data object SemOnboarding : RitmoWidgetEstado
    data object Falha : RitmoWidgetEstado

    /**
     * [mes] e [costume] são as duas curvas acumuladas em 0..1, na MESMA escala (a maior das duas
     * vale 1). Sem eixo nem escala, a curva não diz quanto — só como o mês anda contra o costume.
     */
    data class Pronto(
        val mesAno: YearMonth,
        val diasNoMes: Int,
        val mes: List<Float>,
        val costume: List<Float>,
        val desvio: Int?,
        val temComparacao: Boolean,
        val gastouAlgo: Boolean,
    ) : RitmoWidgetEstado {
        val hoje: Int get() = mes.size
        val acima: Boolean get() = temComparacao && (desvio?.let { it > 0 } ?: gastouAlgo)

        /** O número grande: o desvio com sinal, ou um traço quando não há porcentagem. */
        val numero: String get() = desvio?.takeIf { temComparacao }?.let { if (it > 0) "+$it%" else porcento(it) } ?: "—"

        val legenda: String get() = when {
            !temComparacao -> "sem mês anterior"
            desvio == null -> if (gastouAlgo) "acima do costume" else "no costume"
            desvio > 0 -> "acima do costume"
            desvio < 0 -> "abaixo do costume"
            else -> "no costume"
        }
    }
}

internal fun ritmoDoWidget(r: Ritmo): RitmoWidgetEstado.Pronto {
    val escala = maxOf(r.acumulado.maxOrNull() ?: 0L, r.referencia.maxOrNull() ?: 0L, 1L).toFloat()
    return RitmoWidgetEstado.Pronto(
        mesAno = r.mes,
        diasNoMes = r.mes.lengthOfMonth(),
        mes = r.acumulado.map { it / escala },
        costume = r.referencia.map { it / escala },
        desvio = r.desvioPercentual,
        temComparacao = r.temComparacao,
        gastouAlgo = r.gastoAteAgora > 0,
    )
}

/** "ritmo": o mês contra o costume, em porcentagem e numa curva sem escala. */
class RitmoWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val estado = when (val carga = carregarWidget(context)) {
            Carga.SemOnboarding -> RitmoWidgetEstado.SemOnboarding
            Carga.Falha -> RitmoWidgetEstado.Falha
            is Carga.Pronto -> ritmoDoWidget(RitmoEngine.ritmo(carga.input, carga.mes))
        }
        provideContent { RitmoWidgetContent(estado) }
    }
}

class RitmoWidgetReceiver : SaldoWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = RitmoWidget()
}

@Composable
fun RitmoWidgetContent(estado: RitmoWidgetEstado) {
    val formato = formatoDe(LocalSize.current)
    val pronto = estado as? RitmoWidgetEstado.Pronto
    val descricao = pronto?.let { "ritmo do mês: ${it.numero.replace("—", "")} ${it.legenda}".replace("  ", " ") } ?: "ritmo do mês"
    Moldura(Destino.Totais(pronto?.mesAno ?: mesDeHoje()), formato, descricao) {
        when (estado) {
            RitmoWidgetEstado.SemOnboarding -> Aviso("toque para começar")
            RitmoWidgetEstado.Falha -> Aviso("não foi possível carregar")
            is RitmoWidgetEstado.Pronto -> CorpoRitmo(estado, formato)
        }
    }
}

@Composable
private fun androidx.glance.layout.ColumnScope.CorpoRitmo(e: RitmoWidgetEstado.Pronto, formato: Formato) {
    val tamanho = LocalSize.current
    val m = margemDe(formato)
    val w = tamanho.width - m * 2
    val h = tamanho.height - m * 2
    val cor = if (e.acima) CoresWidget.negativo else CoresWidget.tint
    when (formato) {
        Formato.MINI -> Column(GlanceModifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalAlignment = Alignment.CenterVertically) {
            Numero(e.numero, 24.sp, cor)
            Vao(6.dp)
            Meta("ritmo")
        }
        Formato.LINHA -> Row(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            Column {
                Meta("ritmo")
                Numero(e.numero, 20.sp, cor)
                Meta(e.legenda)
            }
            Spacer(GlanceModifier.width(14.dp))
            Box(GlanceModifier.defaultWeight().fillMaxHeight()) { Grafico(e, w * 0.55f, h) }
        }
        else -> {
            val grande = formato == Formato.GRANDE
            Cabecalho("ritmo do mês", if (formato == Formato.QUADRADO) null else "dia ${e.hoje} de ${e.diasNoMes}")
            Vao(6.dp)
            Row(verticalAlignment = Alignment.Bottom) {
                Numero(e.numero, if (grande) 40.sp else 28.sp, cor)
                Spacer(GlanceModifier.width(8.dp))
                Meta(e.legenda)
            }
            Vao()
            val sobra = h - 16.dp - 6.dp - (if (grande) 40.dp else 28.dp) - 8.dp - if (grande) 24.dp else 0.dp
            Box(GlanceModifier.fillMaxWidth().defaultWeight()) { Grafico(e, w, sobra) }
            if (grande) {
                Vao()
                Row(GlanceModifier.fillMaxWidth()) {
                    Meta("dia 1")
                    Meta("— este mês   - - o costume", GlanceModifier.defaultWeight().padding(horizontal = 8.dp))
                    Meta("dia ${e.diasNoMes}")
                }
            }
        }
    }
}

@Composable
private fun Grafico(e: RitmoWidgetEstado.Pronto, largura: Dp, altura: Dp) {
    if (largura < 24.dp || altura < 16.dp) return
    val c = curva(LocalContext.current, largura, altura, e.mes, e.costume, e.diasNoMes)
    Pintado(c.area, CoresWidget.tint)
    c.costume?.let { Pintado(it, CoresWidget.secundario) }
    Pintado(c.linha, if (e.acima) CoresWidget.negativo else CoresWidget.tint)
}
