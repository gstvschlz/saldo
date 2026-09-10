package com.scholze.saldo.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
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
import com.scholze.saldo.domain.InsightsEngine
import com.scholze.saldo.ui.nav.Destino
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

const val TAG_POUPANCA_TAXA = "widget:poupanca:taxa"

private val ptBrPoupanca = Locale.forLanguageTag("pt-BR")
private val mesPoupanca = DateTimeFormatter.ofPattern("MMM", ptBrPoupanca)

/** O que o widget da poupança desenha. Sem Context e sem repositório, para ser testado na JVM. */
sealed interface PoupancaWidgetEstado {
    data object SemOnboarding : PoupancaWidgetEstado
    data object Falha : PoupancaWidgetEstado

    /**
     * [meses] vem do mais antigo ao mais recente; o último é o mês corrente e é o destacado.
     * A taxa é percentual, não dinheiro — por isso este widget não tem máscara: 12 % não
     * diz quanto se ganha nem quanto se gasta.
     */
    data class Pronto(val meses: List<Ponto>) : PoupancaWidgetEstado {
        /** [taxa] `null` = mês sem entrada, que é diferente de taxa zero. */
        data class Ponto(val mes: YearMonth, val taxa: Int?)
    }
}

/**
 * "poupança": a taxa de cada um dos últimos seis meses, em barra.
 *
 * Sem máscara de propósito, como o board: uma porcentagem não entrega saldo nem salário, e o
 * ponto do widget é justamente poder olhar de relance na tela inicial.
 */
class PoupancaWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val estado = when (val carga = carregarWidget(context)) {
            Carga.SemOnboarding -> PoupancaWidgetEstado.SemOnboarding
            Carga.Falha -> PoupancaWidgetEstado.Falha
            is Carga.Pronto -> PoupancaWidgetEstado.Pronto(
                InsightsEngine.tendencia(carga.input, carga.mes).map {
                    PoupancaWidgetEstado.Pronto.Ponto(it.mes, it.taxaPoupanca)
                },
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
    val mes = (estado as? PoupancaWidgetEstado.Pronto)?.meses?.lastOrNull()?.mes ?: mesDeHoje()
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(CoresWidget.fundo)
            .cornerRadius(16.dp)
            .clickable(abrirWidget(Destino.Totais(mes)))
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        when (estado) {
            PoupancaWidgetEstado.SemOnboarding -> AvisoPoupanca("toque para começar")
            PoupancaWidgetEstado.Falha -> AvisoPoupanca("não foi possível carregar")
            is PoupancaWidgetEstado.Pronto -> CorpoPoupanca(estado)
        }
    }
}

@Composable
private fun CorpoPoupanca(estado: PoupancaWidgetEstado.Pronto) {
    val atual = estado.meses.lastOrNull()
    Row(verticalAlignment = Alignment.CenterVertically, modifier = GlanceModifier.fillMaxWidth()) {
        Text(
            "taxa de poupança",
            modifier = GlanceModifier.defaultWeight(),
            style = TextStyle(color = CoresWidget.secundario, fontSize = 12.sp),
            maxLines = 1,
        )
        Text(
            atual?.taxa?.let { it.toString() + "%" } ?: "—",
            modifier = GlanceModifier.semantics { testTag = TAG_POUPANCA_TAXA },
            style = TextStyle(color = CoresWidget.label, fontSize = 18.sp, fontWeight = FontWeight.Bold),
            maxLines = 1,
        )
    }

    val maior = estado.meses.mapNotNull { it.taxa }.maxOrNull() ?: 0
    Box(GlanceModifier.padding(top = 8.dp)) {
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            estado.meses.forEach { ponto ->
                Column(
                    modifier = GlanceModifier.defaultWeight(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // O Glance não empilha altura fracionária: a barra é uma pilha de blocos
                    // de 4dp, e o número de blocos é a proporção.
                    Column(GlanceModifier.padding(bottom = 3.dp)) {
                        repeat(blocosDaTaxa(ponto.taxa, maior)) {
                            Box(
                                GlanceModifier
                                    .height(4.dp)
                                    .padding(bottom = 1.dp)
                                    .cornerRadius(1.dp)
                                    .background(
                                        if (ponto.mes == atual?.mes) CoresWidget.tint else CoresWidget.trilha,
                                    ),
                            ) { Text(" ", style = TextStyle(fontSize = 4.sp)) }
                        }
                    }
                    Text(
                        ponto.mes.format(mesPoupanca).removeSuffix("."),
                        style = TextStyle(color = CoresWidget.secundario, fontSize = 10.sp),
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/**
 * Quantos blocos de 4dp a barra de [taxa] tem, contra a [maior] taxa da janela.
 *
 * Uma taxa que existe nunca fica sem bloco nenhum — senão "0 %" e "mês sem entrada" ficariam
 * idênticos —, e um mês sem taxa não desenha barra.
 */
internal fun blocosDaTaxa(taxa: Int?, maior: Int, blocos: Int = 6): Int {
    if (taxa == null) return 0
    if (maior <= 0) return 1
    return ((taxa.toFloat() / maior) * blocos).toInt().coerceIn(1, blocos)
}

@Composable
private fun AvisoPoupanca(texto: String) {
    Text(texto, style = TextStyle(color = CoresWidget.label, fontSize = 15.sp, fontWeight = FontWeight.Medium))
}
