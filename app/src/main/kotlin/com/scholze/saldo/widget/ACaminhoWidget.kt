package com.scholze.saldo.widget

import android.content.Context
import androidx.compose.runtime.Composable
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
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import com.scholze.saldo.domain.InsightsEngine
import com.scholze.saldo.domain.descricaoVisivel
import com.scholze.saldo.ui.nav.Destino
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as EstiloDeTexto
import java.time.temporal.ChronoUnit

private val diaMes = DateTimeFormatter.ofPattern("d MMM", ptBrWidget)

sealed interface ACaminhoEstado {
    data object SemOnboarding : ACaminhoEstado
    data object Falha : ACaminhoEstado

    /** Os lançamentos de depois de hoje até o fim do mês: dia, descrição e direção. Nunca o valor. */
    data class Pronto(val mes: YearMonth, val hoje: LocalDate, val itens: List<Item>) : ACaminhoEstado {
        val fim: LocalDate get() = mes.atEndOfMonth()

        /** As próximas datas com lançamento, cada uma com os seus — as colunas do 4×2. */
        val datas: List<Pair<LocalDate, List<Item>>> get() = itens.groupBy { it.data }.toList()

        val entram: Int get() = itens.count { it.entra }
        val saem: Int get() = itens.size - entram
        val proximoEm: Long? get() = itens.firstOrNull()?.let { ChronoUnit.DAYS.between(hoje, it.data) }
    }

    data class Item(val data: LocalDate, val descricao: String, val entra: Boolean)
}

/** "a caminho": quantos lançamentos ainda passam pelo saldo até o fim do mês, e quais. */
class ACaminhoWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val estado = when (val carga = carregarWidget(context)) {
            Carga.SemOnboarding -> ACaminhoEstado.SemOnboarding
            Carga.Falha -> ACaminhoEstado.Falha
            is Carga.Pronto -> ACaminhoEstado.Pronto(
                mes = carga.mes,
                hoje = carga.input.hoje,
                itens = InsightsEngine.aCaminho(carga.input, carga.mes).itens.map {
                    ACaminhoEstado.Item(it.data, it.item.descricao.descricaoVisivel(), it.item.valorCentavos > 0)
                },
            )
        }
        provideContent { ACaminhoWidgetContent(estado) }
    }
}

class ACaminhoWidgetReceiver : SaldoWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ACaminhoWidget()
}

@Composable
fun ACaminhoWidgetContent(estado: ACaminhoEstado) {
    val formato = formatoDe(LocalSize.current)
    val pronto = estado as? ACaminhoEstado.Pronto
    val descricao = pronto?.let { "${it.itens.size} lançamentos a caminho até dia ${it.fim.dayOfMonth}" } ?: "a caminho"
    Moldura(Destino.Saldos(pronto?.mes ?: mesDeHoje(), LocalDate.now().dayOfMonth), formato, descricao) {
        when (estado) {
            ACaminhoEstado.SemOnboarding -> Aviso("toque para começar")
            ACaminhoEstado.Falha -> Aviso("não foi possível carregar")
            is ACaminhoEstado.Pronto -> if (estado.itens.isEmpty()) Aviso("nada a caminho este mês") else CorpoACaminho(estado, formato)
        }
    }
}

@Composable
private fun androidx.glance.layout.ColumnScope.CorpoACaminho(e: ACaminhoEstado.Pronto, formato: Formato) {
    val tamanho = LocalSize.current
    val n = e.itens.size
    when (formato) {
        Formato.MINI -> Column(GlanceModifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalAlignment = Alignment.CenterVertically) {
            Numero("$n", 30.sp)
            Vao(6.dp)
            Meta("a caminho")
        }
        Formato.LINHA -> Row(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            Column {
                Meta("a caminho")
                Numero("$n até dia ${e.fim.dayOfMonth}", 17.sp)
                if (tamanho.width < 200.dp) e.itens.first().let { Meta("próximo: ${it.descricao}, ${it.data.dayOfMonth}") }
            }
            if (tamanho.width >= 200.dp) {
                Spacer(GlanceModifier.width(14.dp))
                e.datas.take(4).forEach { (data, itens) ->
                    Column(
                        GlanceModifier.defaultWeight().fillMaxHeight().padding(horizontal = 2.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(GlanceModifier.fillMaxSize().background(CoresWidget.tomDoBoard(0)).cornerRadius(10.dp), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Meta("${data.dayOfMonth}")
                                Row { itens.take(3).forEach { Seta(it.entra, 14) } }
                            }
                        }
                    }
                }
            }
        }
        Formato.QUADRADO -> {
            Cabecalho("a caminho", "até ${e.fim.dayOfMonth}")
            Vao(6.dp)
            Row(verticalAlignment = Alignment.Bottom) {
                Numero("$n", 30.sp)
                Spacer(GlanceModifier.width(8.dp))
                Meta("lançamentos")
            }
            Column(GlanceModifier.fillMaxWidth().defaultWeight(), verticalAlignment = Alignment.CenterVertically) {
                e.itens.take(2).forEach { Linha(it, grande = false) }
            }
            if (n > 2) Meta("+${n - 2} depois")
        }
        Formato.LARGO -> {
            Cabecalho("a caminho", "$n até ${e.fim.format(diaMes)}")
            Vao()
            Row(GlanceModifier.fillMaxWidth().defaultWeight()) {
                e.datas.take(4).forEachIndexed { i, (data, itens) ->
                    if (i > 0) Spacer(GlanceModifier.width(6.dp))
                    Column(GlanceModifier.defaultWeight().fillMaxHeight().background(CoresWidget.tomDoBoard(0)).cornerRadius(12.dp).padding(8.dp)) {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Numero("${data.dayOfMonth}", 17.sp)
                            Spacer(GlanceModifier.width(4.dp))
                            Meta(data.dayOfWeek.getDisplayName(EstiloDeTexto.SHORT, ptBrWidget).trimEnd('.'))
                        }
                        itens.take(2).forEach { item ->
                            Vao(5.dp)
                            Row(
                                GlanceModifier.fillMaxWidth().background(CoresWidget.fundo).cornerRadius(8.dp).padding(horizontal = 6.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Seta(item.entra, 13)
                                Spacer(GlanceModifier.width(4.dp))
                                Text(item.descricao, style = TextStyle(color = CoresWidget.label, fontSize = 11.5.sp, fontWeight = FontWeight.Medium), maxLines = 1)
                            }
                        }
                        if (itens.size > 2) {
                            Vao(4.dp)
                            Meta("+${itens.size - 2}")
                        }
                    }
                }
            }
        }
        Formato.GRANDE -> {
            Cabecalho("a caminho", "até ${e.fim.format(diaMes)}")
            Vao()
            val cabem = ((tamanho.height - margemDe(formato) * 2 - 24.dp - 24.dp) / 46.dp).toInt().coerceAtLeast(1)
            Column(GlanceModifier.fillMaxWidth().defaultWeight()) {
                e.itens.take(cabem).forEach { Linha(it, grande = true) }
                if (n > cabem) Meta("+${n - cabem} depois")
            }
            Vao()
            Meta(
                listOfNotNull(
                    e.proximoEm?.let { if (it <= 1L) "próximo amanhã" else "próximo em $it dias" },
                    "${e.entram} entra${if (e.entram == 1) "" else "m"}, ${e.saem} sai${if (e.saem == 1) "" else "em"}",
                ).joinToString(" · "),
            )
        }
    }
}

@Composable
private fun Linha(it: ACaminhoEstado.Item, grande: Boolean) {
    val lado = if (grande) 36.dp else 26.dp
    Row(GlanceModifier.fillMaxWidth().padding(vertical = if (grande) 5.dp else 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(GlanceModifier.size(lado).background(CoresWidget.tomDoBoard(0)).cornerRadius(if (grande) 10.dp else 8.dp), contentAlignment = Alignment.Center) {
            Text(
                "${it.data.dayOfMonth}",
                style = TextStyle(color = CoresWidget.label, fontSize = if (grande) 15.sp else 12.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center),
            )
        }
        Spacer(GlanceModifier.width(if (grande) 10.dp else 8.dp))
        Column(GlanceModifier.defaultWeight()) {
            Text(it.descricao, style = TextStyle(color = CoresWidget.label, fontSize = 13.sp, fontWeight = FontWeight.Medium), maxLines = 1)
            if (grande) Meta("${it.data.dayOfWeek.getDisplayName(EstiloDeTexto.SHORT, ptBrWidget).trimEnd('.')} · ${if (it.entra) "entra" else "sai"}")
        }
        Seta(it.entra, if (grande) 18 else 14)
    }
}

/** ↑ entra (verde), ↓ sai (rosa): a direção sem o valor. */
@Composable
private fun Seta(entra: Boolean, sp: Int) {
    Text(
        if (entra) "↑" else "↓",
        style = TextStyle(color = if (entra) CoresWidget.positivo else CoresWidget.negativo, fontSize = sp.sp, fontWeight = FontWeight.Bold),
    )
}
