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
import androidx.glance.layout.padding
import androidx.glance.semantics.semantics
import androidx.glance.semantics.testTag
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.scholze.saldo.domain.InsightsEngine
import com.scholze.saldo.ui.money.centavosAssinado
import com.scholze.saldo.ui.money.centavosComSimbolo
import com.scholze.saldo.ui.nav.Destino
import com.scholze.saldo.ui.privacy.MASCARA_PRIVACIDADE
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

const val TAG_ACAMINHO_TOTAL = "widget:acaminho:total"
const val TAG_ACAMINHO_PRAZO = "widget:acaminho:prazo"

private val ptBrACaminho = Locale.forLanguageTag("pt-BR")
private val diaMesACaminho = DateTimeFormatter.ofPattern("d MMM", ptBrACaminho)

/** Quantas linhas cabem numa célula 4×2 sem espremer. */
private const val LINHAS_VISIVEIS = 3

/** O que o widget "a caminho" desenha. Sem Context e sem repositório, para ser testado na JVM. */
sealed interface ACaminhoEstado {
    data object SemOnboarding : ACaminhoEstado
    data object Falha : ACaminhoEstado
    data class Pronto(
        val mes: YearMonth,
        val fimDoMes: LocalDate,
        val saemCentavos: Long,
        val mesEncerrado: Boolean,
        val linhas: List<Linha>,
        val restantes: Int,
        val mostrarValores: Boolean,
    ) : ACaminhoEstado

    /** Uma linha datada: dia, descrição e valor com sinal. */
    data class Linha(val dia: Int, val descricao: String, val centavos: Long)
}

/**
 * "a caminho": o que ainda passa pelo saldo depois de hoje até o fim do mês corrente. Lê o
 * `InsightsEngine.aCaminho` que a insights-1 já entregou testado — nenhuma conta nova aqui.
 */
class ACaminhoWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val estado = when (val carga = carregarWidget(context)) {
            Carga.SemOnboarding -> ACaminhoEstado.SemOnboarding
            Carga.Falha -> ACaminhoEstado.Falha
            is Carga.Pronto -> {
                val a = InsightsEngine.aCaminho(carga.input, carga.mes)
                ACaminhoEstado.Pronto(
                    mes = carga.mes,
                    fimDoMes = carga.mes.atEndOfMonth(),
                    saemCentavos = a.saemCentavos,
                    mesEncerrado = a.mesEncerrado,
                    linhas = a.itens.take(LINHAS_VISIVEIS).map {
                        ACaminhoEstado.Linha(it.data.dayOfMonth, it.item.descricao, it.item.valorCentavos)
                    },
                    restantes = (a.itens.size - LINHAS_VISIVEIS).coerceAtLeast(0),
                    mostrarValores = carga.mostrarValores,
                )
            }
        }
        provideContent { ACaminhoWidgetContent(estado) }
    }
}

class ACaminhoWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ACaminhoWidget()
}

@Composable
fun ACaminhoWidgetContent(estado: ACaminhoEstado) {
    val mes = (estado as? ACaminhoEstado.Pronto)?.mes ?: mesDeHoje()
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(CoresWidget.fundo)
            .cornerRadius(16.dp)
            .clickable(abrirWidget(Destino.Totais(mes)))
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        when (estado) {
            ACaminhoEstado.SemOnboarding -> Aviso("toque para começar")
            ACaminhoEstado.Falha -> Aviso("não foi possível carregar")
            is ACaminhoEstado.Pronto -> Corpo(estado)
        }
    }
}

@Composable
private fun Aviso(texto: String) {
    Text(texto, style = TextStyle(color = CoresWidget.label, fontSize = 15.sp, fontWeight = FontWeight.Medium))
}

@Composable
private fun Corpo(estado: ACaminhoEstado.Pronto) {
    if (estado.mesEncerrado) {
        Aviso("mês encerrado")
        return
    }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = GlanceModifier.fillMaxWidth()) {
        Text(
            "ainda saem",
            modifier = GlanceModifier.defaultWeight(),
            style = TextStyle(color = CoresWidget.secundario, fontSize = 12.sp),
            maxLines = 1,
        )
        Text(
            "até " + estado.fimDoMes.format(diaMesACaminho).removeSuffix("."),
            modifier = GlanceModifier.semantics { testTag = TAG_ACAMINHO_PRAZO },
            style = TextStyle(color = CoresWidget.secundario, fontSize = 12.sp),
            maxLines = 1,
        )
    }
    Text(
        if (estado.mostrarValores) estado.saemCentavos.centavosComSimbolo() else MASCARA_PRIVACIDADE,
        modifier = GlanceModifier.semantics { testTag = TAG_ACAMINHO_TOTAL },
        style = TextStyle(color = CoresWidget.label, fontSize = 20.sp, fontWeight = FontWeight.Bold),
        maxLines = 1,
    )
    if (estado.linhas.isEmpty()) {
        Box(GlanceModifier.padding(top = 6.dp)) {
            Text(
                "nada agendado até o fim do mês",
                style = TextStyle(color = CoresWidget.secundario, fontSize = 12.sp),
                maxLines = 1,
            )
        }
        return
    }
    estado.linhas.forEach { linha ->
        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .padding(top = 4.dp)
                // A linha leva ao DIA dela no ledger; o card inteiro leva a totais.
                .clickable(abrirWidget(Destino.Saldos(estado.mes, linha.dia))),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                linha.dia.toString().padStart(2, '0'),
                style = TextStyle(color = CoresWidget.secundario, fontSize = 12.sp),
                maxLines = 1,
            )
            Box(GlanceModifier.padding(horizontal = 5.dp)) {
                Text(
                    linha.descricao,
                    style = TextStyle(color = CoresWidget.label, fontSize = 13.sp),
                    maxLines = 1,
                )
            }
            Box(GlanceModifier.defaultWeight()) {}
            Text(
                if (estado.mostrarValores) linha.centavos.centavosAssinado() else MASCARA_PRIVACIDADE,
                style = TextStyle(
                    color = if (linha.centavos < 0) CoresWidget.negativo else CoresWidget.positivo,
                    fontSize = 13.sp,
                ),
                maxLines = 1,
            )
        }
    }
    if (estado.restantes > 0) {
        Text(
            "+${estado.restantes} depois",
            modifier = GlanceModifier.padding(top = 3.dp),
            style = TextStyle(color = CoresWidget.secundario, fontSize = 11.sp),
            maxLines = 1,
        )
    }
}
