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
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
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
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import com.scholze.saldo.domain.InsightsEngine
import com.scholze.saldo.ui.nav.Destino
import java.time.YearMonth

sealed interface PoupancaWidgetEstado {
    data object SemOnboarding : PoupancaWidgetEstado
    data object Falha : PoupancaWidgetEstado

    /**
     * [meses] vai do mais antigo ao corrente, que é o último. Taxa `null` = mês sem entrada, que
     * é diferente de taxa zero. [meta] `0` = sem meta.
     */
    data class Pronto(val meses: List<Ponto>, val meta: Int) : PoupancaWidgetEstado {
        data class Ponto(val mes: YearMonth, val taxa: Int?)

        val mes: YearMonth get() = meses.last().mes
        val taxa: Int? get() = meses.last().taxa

        /** Quanto do anel se enche: contra a meta, ou contra 100% quando não há meta. */
        val fracao: Float get() = ((taxa ?: 0).coerceAtLeast(0).toFloat() / (if (meta > 0) meta else 100)).coerceIn(0f, 1f)

        val legenda: String get() = when {
            taxa == null -> "sem entrada no mês"
            meta <= 0 -> "sem meta"
            taxa!! >= meta -> "meta batida"
            else -> "faltam ${meta - taxa!!} pontos"
        }

        val bateram: Int get() = if (meta <= 0) 0 else meses.count { (it.taxa ?: Int.MIN_VALUE) >= meta }
    }
}

/**
 * "guardou": quanto da renda do mês foi guardado, contra a meta, e os últimos seis meses. Só
 * porcentagem — 17% não diz quanto se ganha nem quanto se gasta.
 */
class PoupancaWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val estado = when (val carga = carregarWidget(context)) {
            Carga.SemOnboarding -> PoupancaWidgetEstado.SemOnboarding
            Carga.Falha -> PoupancaWidgetEstado.Falha
            is Carga.Pronto -> PoupancaWidgetEstado.Pronto(
                InsightsEngine.tendencia(carga.input, carga.mes).map { PoupancaWidgetEstado.Pronto.Ponto(it.mes, it.taxaPoupanca) },
                carga.metaGuardarPercent,
            )
        }
        provideContent { PoupancaWidgetContent(estado) }
    }
}

class PoupancaWidgetReceiver : SaldoWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = PoupancaWidget()
}

@Composable
fun PoupancaWidgetContent(estado: PoupancaWidgetEstado) {
    val formato = formatoDe(LocalSize.current)
    val pronto = estado as? PoupancaWidgetEstado.Pronto
    val descricao = pronto?.let { p ->
        "guardou ${p.taxa?.let(::porcento) ?: "nada"} da renda" + if (p.meta > 0) ", meta de ${p.meta}%" else ""
    } ?: "guardou"
    Moldura(Destino.Totais(pronto?.mes ?: mesDeHoje()), formato, descricao) {
        when (estado) {
            PoupancaWidgetEstado.SemOnboarding -> Aviso("toque para começar")
            PoupancaWidgetEstado.Falha -> Aviso("não foi possível carregar")
            is PoupancaWidgetEstado.Pronto -> CorpoGuardou(estado, formato)
        }
    }
}

@Composable
private fun androidx.glance.layout.ColumnScope.CorpoGuardou(e: PoupancaWidgetEstado.Pronto, formato: Formato) {
    val tamanho = LocalSize.current
    val m = margemDe(formato)
    val w = tamanho.width - m * 2
    val h = tamanho.height - m * 2
    val taxa = e.taxa?.let(::porcento) ?: "—"
    val meta = if (e.meta > 0) "meta ${e.meta}%" else "sem meta"
    when (formato) {
        Formato.MINI -> Box(GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) { Anel(e, minOf(w, h), taxa) }
        Formato.LINHA -> Row(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            Column {
                Meta("guardou")
                Numero(taxa, 26.sp, CoresWidget.tint)
            }
            Spacer(GlanceModifier.width(14.dp))
            Column(GlanceModifier.defaultWeight()) {
                val trilho = w - 14.dp - 64.dp
                Box(GlanceModifier.fillMaxWidth().height(12.dp).background(CoresWidget.tomDoBoard(0)).cornerRadius(6.dp)) {
                    if (e.fracao > 0f) {
                        Box(GlanceModifier.width(trilho * e.fracao).height(12.dp).background(CoresWidget.tint).cornerRadius(6.dp)) {}
                    }
                }
                Vao(6.dp)
                Meta(if (tamanho.width >= 200.dp) "$meta · ${e.legenda}" else meta)
            }
        }
        Formato.QUADRADO -> {
            Cabecalho("guardou", meta)
            Vao()
            Box(GlanceModifier.fillMaxWidth().defaultWeight(), contentAlignment = Alignment.Center) {
                Anel(e, minOf(w, h - 16.dp - 8.dp - 8.dp - 16.dp), taxa)
            }
            Vao()
            Meta(e.legenda)
        }
        Formato.LARGO, Formato.GRANDE -> {
            val grande = formato == Formato.GRANDE
            Cabecalho("guardou em ${e.mes.format(nomeDoMes)}", meta)
            Vao()
            val corpo = h - 24.dp - if (grande) 24.dp else 0.dp
            val lado = minOf(corpo, w * (if (grande) 0.46f else 0.42f))
            Row(GlanceModifier.fillMaxWidth().defaultWeight(), verticalAlignment = Alignment.CenterVertically) {
                Anel(e, lado, taxa)
                Spacer(GlanceModifier.width(16.dp))
                Barras(e, corpo - if (grande) 30.dp else 18.dp, grande)
            }
            if (grande) {
                Vao()
                Meta(if (e.meta > 0) "${e.bateram} dos últimos ${e.meses.size} meses bateram a meta · ${e.legenda}" else e.legenda)
            }
        }
    }
}

/** O anel de [lado]: a trilha inteira por baixo, o arco da fração por cima, a taxa no meio. */
@Composable
private fun Anel(e: PoupancaWidgetEstado.Pronto, lado: Dp, taxa: String) {
    if (lado < 24.dp) return
    val context = LocalContext.current
    val espessura = maxOf(7.dp, lado * 0.1f)
    Box(GlanceModifier.size(lado), contentAlignment = Alignment.Center) {
        Pintado(arco(context, lado, espessura, 1f), CoresWidget.tomDoBoard(0))
        Pintado(arco(context, lado, espessura, e.fracao), CoresWidget.tint)
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Numero(taxa, maxOf(15f, lado.value * 0.26f).sp)
            if (lado >= 100.dp) Meta("da renda")
        }
    }
}

@Composable
private fun androidx.glance.layout.RowScope.Barras(e: PoupancaWidgetEstado.Pronto, altura: Dp, comTaxa: Boolean) {
    val escala = maxOf(30, e.meta + 5, e.meses.maxOf { it.taxa ?: 0 })
    Column(GlanceModifier.defaultWeight().fillMaxHeight()) {
        Box(GlanceModifier.fillMaxWidth().defaultWeight(), contentAlignment = Alignment.BottomStart) {
            Row(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.Bottom) {
                e.meses.forEachIndexed { i, p ->
                    val corrente = i == e.meses.lastIndex
                    Box(GlanceModifier.defaultWeight().fillMaxHeight().padding(horizontal = 3.dp), contentAlignment = Alignment.BottomCenter) {
                        val alto = altura * ((p.taxa ?: 0).coerceAtLeast(0).toFloat() / escala)
                        if (alto > 0.dp) {
                            Box(
                                GlanceModifier.fillMaxWidth().height(alto).cornerRadius(4.dp)
                                    .background(if (corrente) CoresWidget.tint else CoresWidget.suave),
                            ) {}
                        }
                    }
                }
            }
            if (e.meta > 0) {
                Box(GlanceModifier.fillMaxWidth().padding(bottom = altura * (e.meta.toFloat() / escala))) {
                    Box(GlanceModifier.fillMaxWidth().height(1.5.dp).background(CoresWidget.trilha)) {}
                }
            }
        }
        Vao(4.dp)
        Row(GlanceModifier.fillMaxWidth()) {
            e.meses.forEachIndexed { i, p ->
                val corrente = i == e.meses.lastIndex
                val estilo = TextStyle(
                    color = if (corrente) CoresWidget.label else CoresWidget.secundario,
                    fontSize = 10.5.sp,
                    fontWeight = if (corrente) FontWeight.Medium else FontWeight.Normal,
                    textAlign = TextAlign.Center,
                )
                Column(GlanceModifier.defaultWeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(p.mes.format(mesCurto).trimEnd('.'), style = estilo, maxLines = 1)
                    if (comTaxa) Text(p.taxa?.let(::porcento) ?: "—", style = estilo, maxLines = 1)
                }
            }
        }
    }
}
