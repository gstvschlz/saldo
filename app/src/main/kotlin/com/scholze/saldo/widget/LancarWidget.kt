package com.scholze.saldo.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.action.clickable
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.semantics.testTag
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.scholze.saldo.ui.nav.Destino

const val TAG_LANCAR_SAIDA = "widget:lancar:saida"
const val TAG_LANCAR_ENTRADA = "widget:lancar:entrada"

/**
 * O widget mais simples dos quatro: dois botões que abrem a sheet **já do lado certo**, poupando
 * o toque que hoje se gasta trocando entrada/saída. Não lê motor nenhum — só precisa saber se o
 * onboarding já aconteceu, porque antes disso não há saldo inicial e a sheet não tem o que fazer.
 */
class LancarWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val pronto = carregarWidget(context) is Carga.Pronto
        provideContent { LancarWidgetContent(pronto) }
    }
}

class LancarWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = LancarWidget()
}

@Composable
fun LancarWidgetContent(pronto: Boolean) {
    Row(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(CoresWidget.fundo)
            .cornerRadius(16.dp)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!pronto) {
            Box(
                modifier = GlanceModifier.fillMaxSize().clickable(abrirWidget(Destino.Saldos(mesDeHoje()))),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "toque para começar",
                    style = TextStyle(color = CoresWidget.label, fontSize = 15.sp, fontWeight = FontWeight.Medium),
                )
            }
            return@Row
        }
        Botao("− saída", TAG_LANCAR_SAIDA, saida = true, modifier = GlanceModifier.defaultWeight())
        Box(GlanceModifier.padding(horizontal = 4.dp)) {}
        Botao("+ entrada", TAG_LANCAR_ENTRADA, saida = false, modifier = GlanceModifier.defaultWeight())
    }
}

@Composable
private fun Botao(rotulo: String, tag: String, saida: Boolean, modifier: GlanceModifier) {
    Box(
        modifier = modifier
            .fillMaxHeight()
            // Saída é o caso comum (é um app de gasto), então ela leva o preenchimento cheio e a
            // entrada fica tonal — a hierarquia diz qual é o botão do dia a dia.
            .background(if (saida) CoresWidget.tint else CoresWidget.container)
            .cornerRadius(12.dp)
            .clickable(abrirWidget(Destino.NovaMovimentacao(saida = saida)))
            .semantics {
                testTag = tag
                contentDescription = if (saida) "nova saída" else "nova entrada"
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            rotulo,
            style = TextStyle(
                color = if (saida) CoresWidget.sobreTint else CoresWidget.sobreContainer,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
            ),
            maxLines = 1,
        )
    }
}
