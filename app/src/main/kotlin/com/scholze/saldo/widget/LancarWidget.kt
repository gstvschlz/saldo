package com.scholze.saldo.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalSize
import androidx.glance.action.clickable
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
import androidx.glance.layout.width
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
 * Dois botões que abrem a sheet já do lado certo. Não lê motor nenhum — só precisa saber se o
 * onboarding já aconteceu. No 1×1 vira um `+` só, que abre a sheet no padrão dela.
 */
class LancarWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val pronto = carregarWidget(context) is Carga.Pronto
        provideContent { LancarWidgetContent(pronto) }
    }
}

class LancarWidgetReceiver : SaldoWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = LancarWidget()
}

@Composable
fun LancarWidgetContent(pronto: Boolean) {
    val formato = formatoDe(LocalSize.current)
    Moldura(Destino.Saldos(mesDeHoje()), formato, "lançar", margem = 8.dp) {
        when {
            !pronto -> Aviso("toque para começar")
            formato == Formato.MINI -> Botao("+", null, "lançar", null, 32, GlanceModifier.fillMaxSize())
            formato == Formato.QUADRADO -> Column(GlanceModifier.fillMaxSize()) {
                Botao("− saiu", true, "nova saída", TAG_LANCAR_SAIDA, 16, GlanceModifier.fillMaxWidth().defaultWeight())
                Spacer(GlanceModifier.height(8.dp))
                Botao("+ entrou", false, "nova entrada", TAG_LANCAR_ENTRADA, 16, GlanceModifier.fillMaxWidth().defaultWeight())
            }
            else -> Row(GlanceModifier.fillMaxSize()) {
                val tamanho = if (formato == Formato.GRANDE) 20 else if (LocalSize.current.width < 200.dp) 14 else 16
                Botao("− saiu", true, "nova saída", TAG_LANCAR_SAIDA, tamanho, GlanceModifier.defaultWeight().fillMaxHeight())
                Spacer(GlanceModifier.width(8.dp))
                Botao("+ entrou", false, "nova entrada", TAG_LANCAR_ENTRADA, tamanho, GlanceModifier.defaultWeight().fillMaxHeight())
            }
        }
    }
}

@Composable
private fun Botao(rotulo: String, saida: Boolean?, descricao: String, tag: String?, tamanho: Int, modifier: GlanceModifier) {
    val fundo = if (saida == true) CoresWidget.tomDoBoard(-1) else CoresWidget.tint
    val tinta = if (saida == true) CoresWidget.negativo else CoresWidget.sobreTint
    Box(
        modifier = modifier
            .background(fundo)
            .cornerRadius(18.dp)
            .clickable(abrirWidget(Destino.NovaMovimentacao(saida = saida)))
            .semantics {
                if (tag != null) testTag = tag
                contentDescription = descricao
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(rotulo, style = TextStyle(color = tinta, fontSize = tamanho.sp, fontWeight = FontWeight.Bold), maxLines = 1)
    }
}
