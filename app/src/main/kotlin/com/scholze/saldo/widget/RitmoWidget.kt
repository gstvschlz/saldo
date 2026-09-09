package com.scholze.saldo.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.semantics.semantics
import androidx.glance.semantics.testTag
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.scholze.saldo.domain.RitmoEngine
import com.scholze.saldo.ui.money.centavosComSimbolo
import com.scholze.saldo.ui.nav.Destino
import com.scholze.saldo.ui.privacy.MASCARA_PRIVACIDADE
import java.time.YearMonth

const val TAG_RITMO_TOTAL = "widget:ritmo:total"
const val TAG_RITMO_DESVIO = "widget:ritmo:desvio"

/** O que o widget do ritmo desenha. Sem Context e sem repositório, para ser testado na JVM. */
sealed interface RitmoWidgetEstado {
    data object SemOnboarding : RitmoWidgetEstado
    data object Falha : RitmoWidgetEstado

    /**
     * [fracao] é a altura da barra do mês contra a maior das duas séries (0..1), e
     * [fracaoCostume] a do costume na mesma escala — as duas juntas são a comparação.
     * [desvioPercentual] é `null` quando não há mês anterior com que comparar.
     */
    data class Pronto(
        val mes: YearMonth,
        val diasDecorridos: Int,
        val gastoCentavos: Long,
        val desvioPercentual: Int?,
        /** Há mês anterior com que comparar, mesmo que o costume aqui seja zero. */
        val temComparacao: Boolean,
        val gastouAlgo: Boolean,
        val fracao: Float,
        val fracaoCostume: Float,
        val mostrarValores: Boolean,
    ) : RitmoWidgetEstado
}

/**
 * "ritmo": quanto já saiu no mês, e se isso é muito para a altura do mês em que se está.
 *
 * O widget mais estreito dos oito (4×1) porque a resposta cabe numa linha: um número, um
 * desvio e duas barras — a do mês e a do costume, na mesma escala.
 */
class RitmoWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val estado = when (val carga = carregarWidget(context)) {
            Carga.SemOnboarding -> RitmoWidgetEstado.SemOnboarding
            Carga.Falha -> RitmoWidgetEstado.Falha
            is Carga.Pronto -> {
                val r = RitmoEngine.ritmo(carga.input, carga.mes)
                val maior = maxOf(r.gastoAteAgora, r.referenciaAteAgora)
                RitmoWidgetEstado.Pronto(
                    mes = carga.mes,
                    diasDecorridos = r.acumulado.size,
                    gastoCentavos = r.gastoAteAgora,
                    desvioPercentual = r.desvioPercentual,
                    temComparacao = r.temComparacao,
                    gastouAlgo = r.gastoAteAgora > 0,
                    fracao = if (maior > 0) r.gastoAteAgora.toFloat() / maior else 0f,
                    fracaoCostume = if (maior > 0) r.referenciaAteAgora.toFloat() / maior else 0f,
                    mostrarValores = carga.mostrarValores,
                )
            }
        }
        provideContent { RitmoWidgetContent(estado) }
    }
}

class RitmoWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = RitmoWidget()
}

@Composable
fun RitmoWidgetContent(estado: RitmoWidgetEstado) {
    val mes = (estado as? RitmoWidgetEstado.Pronto)?.mes ?: mesDeHoje()
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(CoresWidget.fundo)
            .cornerRadius(16.dp)
            .clickable(abrirWidget(Destino.Totais(mes)))
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        when (estado) {
            RitmoWidgetEstado.SemOnboarding -> AvisoRitmo("toque para começar")
            RitmoWidgetEstado.Falha -> AvisoRitmo("não foi possível carregar")
            is RitmoWidgetEstado.Pronto -> CorpoRitmo(estado)
        }
    }
}

@Composable
private fun CorpoRitmo(estado: RitmoWidgetEstado.Pronto) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = GlanceModifier.fillMaxWidth()) {
        Text(
            if (estado.mostrarValores) estado.gastoCentavos.centavosComSimbolo() else MASCARA_PRIVACIDADE,
            modifier = GlanceModifier.semantics { testTag = TAG_RITMO_TOTAL },
            style = TextStyle(color = CoresWidget.label, fontSize = 18.sp, fontWeight = FontWeight.Bold),
            maxLines = 1,
        )
        Box(GlanceModifier.padding(start = 6.dp).defaultWeight()) {
            Text(
                "saiu em " + estado.diasDecorridos + " dias",
                style = TextStyle(color = CoresWidget.secundario, fontSize = 12.sp),
                maxLines = 1,
            )
        }
        Text(
            textoDoDesvio(estado.desvioPercentual, estado.temComparacao, estado.gastouAlgo),
            modifier = GlanceModifier.semantics { testTag = TAG_RITMO_DESVIO },
            style = TextStyle(
                color = if (acimaDoCostume(estado)) CoresWidget.negativo else CoresWidget.secundario,
                fontSize = 12.sp,
            ),
            maxLines = 1,
        )
    }
    // Duas trilhas de 4dp: a do mês na cor de saída, a do costume em cinza logo abaixo.
    Box(GlanceModifier.padding(top = 8.dp)) {
        Column(GlanceModifier.fillMaxWidth()) {
            Barra(estado.fracao, CoresWidget.negativo)
            Box(GlanceModifier.padding(top = 3.dp)) { Barra(estado.fracaoCostume, CoresWidget.trilha) }
        }
    }
}

/**
 * Uma barra proporcional feita de caixas com peso — o Glance não tem largura fracionária, e
 * `defaultWeight` divide igual entre os filhos. Fração zero não desenha nada.
 */
@Composable
private fun Barra(fracao: Float, cor: ColorProvider) {
    val cheias = pesoCheio(fracao)
    val vazias = PESO_TOTAL - cheias
    Row(GlanceModifier.fillMaxWidth().height(4.dp)) {
        if (cheias > 0) {
            Box(GlanceModifier.defaultWeight().height(4.dp).cornerRadius(2.dp).background(cor)) {}
        }
        repeat(vazias) { Box(GlanceModifier.defaultWeight().height(4.dp)) {} }
    }
}

/**
 * Em quantas das [PESO_TOTAL] fatias a barra cheia cabe.
 *
 * A resolução é grosseira de propósito: um widget de 4×1 não tem pixel para mais do que
 * doze passos, e o número exato já está escrito ao lado em reais.
 */
internal fun pesoCheio(fracao: Float): Int =
    (fracao.coerceIn(0f, 1f) * PESO_TOTAL).toInt().coerceIn(0, PESO_TOTAL)

internal const val PESO_TOTAL = 12

/**
 * [temComparacao] separa "não há mês anterior" de "o costume neste ponto do mês era zero" —
 * no dia 3, o segundo é comum e não quer dizer que falte histórico.
 */
internal fun textoDoDesvio(desvio: Int?, temComparacao: Boolean, gastouAlgo: Boolean): String = when {
    !temComparacao -> "sem comparação"
    desvio == null -> if (gastouAlgo) "acima do costume" else "no costume"
    desvio > 0 -> "+" + desvio + "% vs costume"
    desvio < 0 -> desvio.toString() + "% vs costume"
    else -> "no costume"
}

/** Gastando mais do que o costume — com ou sem porcentagem para expressar quanto. */
internal fun acimaDoCostume(estado: RitmoWidgetEstado.Pronto): Boolean {
    if (!estado.temComparacao) return false
    return (estado.desvioPercentual ?: return estado.gastouAlgo) > 0
}

@Composable
private fun AvisoRitmo(texto: String) {
    Text(texto, style = TextStyle(color = CoresWidget.label, fontSize = 15.sp, fontWeight = FontWeight.Medium))
}
