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
import com.scholze.saldo.domain.TetoEngine
import com.scholze.saldo.ui.money.centavosComSimbolo
import com.scholze.saldo.ui.nav.Destino
import com.scholze.saldo.ui.privacy.MASCARA_PRIVACIDADE
import java.time.YearMonth

const val TAG_TETO_VALOR = "widget:teto:valor"
const val TAG_TETO_DIREITA = "widget:teto:direita"

/** O que o widget do teto desenha. Sem Context e sem repositório, para ser testado na JVM. */
sealed interface TetoWidgetEstado {
    data object SemOnboarding : TetoWidgetEstado
    data object Falha : TetoWidgetEstado

    /** O mês não teve entrada nenhuma: não há renda para dividir. Ver [TetoEngine.teto]. */
    data object SemRenda : TetoWidgetEstado

    data class Pronto(
        val mes: YearMonth,
        val tetoCentavos: Long,
        val restaCentavos: Long,
        /** Já houve gasto avulso hoje — é o que decide se a direita fala de dinheiro ou de dias. */
        val gastouHoje: Boolean,
        val diasRestantes: Int,
        val estourouODia: Boolean,
        val estourouOMes: Boolean,
        val mostrarValores: Boolean,
    ) : TetoWidgetEstado
}

/**
 * "hoje": o teto do dia na tela inicial.
 *
 * 4×1 como o do ritmo, e pela mesma razão — a resposta cabe numa linha: quanto o dia comporta e,
 * à direita, quanto ainda cabe nele (ou quantos dias faltam, antes do primeiro gasto).
 *
 * Abre no board, que é onde a mesma linha vive dentro do app.
 */
class TetoWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val estado = when (val carga = carregarWidget(context)) {
            Carga.SemOnboarding -> TetoWidgetEstado.SemOnboarding
            Carga.Falha -> TetoWidgetEstado.Falha
            is Carga.Pronto -> {
                val t = TetoEngine.teto(carga.input, carga.metaGuardarPercent)
                if (t == null) {
                    TetoWidgetEstado.SemRenda
                } else {
                    TetoWidgetEstado.Pronto(
                        mes = carga.mes,
                        tetoCentavos = t.tetoCentavos,
                        restaCentavos = t.restaCentavos,
                        gastouHoje = t.gastoDeHojeCentavos != 0L,
                        diasRestantes = t.diasRestantes,
                        estourouODia = t.estourouODia,
                        estourouOMes = t.estourouOMes,
                        mostrarValores = carga.mostrarValores,
                    )
                }
            }
        }
        provideContent { TetoWidgetContent(estado) }
    }
}

class TetoWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TetoWidget()
}

@Composable
fun TetoWidgetContent(estado: TetoWidgetEstado) {
    val mes = (estado as? TetoWidgetEstado.Pronto)?.mes ?: mesDeHoje()
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(CoresWidget.fundo)
            .cornerRadius(16.dp)
            .clickable(abrirWidget(Destino.Saldos(mes)))
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        when (estado) {
            TetoWidgetEstado.SemOnboarding -> AvisoTeto("toque para começar")
            TetoWidgetEstado.Falha -> AvisoTeto("não foi possível carregar")
            TetoWidgetEstado.SemRenda -> AvisoTeto("sem entrada neste mês")
            is TetoWidgetEstado.Pronto -> CorpoTeto(estado)
        }
    }
}

@Composable
private fun CorpoTeto(estado: TetoWidgetEstado.Pronto) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = GlanceModifier.fillMaxWidth()) {
        Text(
            textoDoTeto(estado),
            modifier = GlanceModifier.semantics { testTag = TAG_TETO_VALOR },
            style = TextStyle(
                // Mês estourado pinta o número, como o desvio do ritmo acima do costume. Mascarado
                // NÃO pinta: a cor contaria o sinal que o `R$ •••••` está escondendo.
                color = if (estado.estourouOMes && estado.mostrarValores) CoresWidget.negativo else CoresWidget.label,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            ),
            maxLines = 1,
        )
        Box(GlanceModifier.padding(start = 6.dp).defaultWeight()) {
            Text(
                "hoje",
                style = TextStyle(color = CoresWidget.secundario, fontSize = 12.sp),
                maxLines = 1,
            )
        }
        Text(
            textoDaDireita(estado),
            modifier = GlanceModifier.semantics { testTag = TAG_TETO_DIREITA },
            style = TextStyle(
                // `gastouHoje` junto: sem gasto a direita conta DIAS, e pintar "faltam 11 dias"
                // de vermelho porque o mês está no vermelho falaria do número errado.
                color = if (estado.gastouHoje && estado.estourouODia && estado.mostrarValores) {
                    CoresWidget.negativo
                } else {
                    CoresWidget.secundario
                },
                fontSize = 12.sp,
            ),
            maxLines = 1,
        )
    }
}

/** O número grande. Valor mascarado é teto mascarado — a mesma regra da meta no widget de saldo. */
internal fun textoDoTeto(estado: TetoWidgetEstado.Pronto): String =
    if (estado.mostrarValores) estado.tetoCentavos.centavosComSimbolo() else MASCARA_PRIVACIDADE

/**
 * A direita: dinheiro depois do primeiro gasto do dia, dias antes dele.
 *
 * Antes de gastar, "restam" repetiria o número grande — então o espaço conta a outra metade da
 * conta, quantos dias esse teto ainda tem de durar.
 */
internal fun textoDaDireita(estado: TetoWidgetEstado.Pronto): String = when {
    !estado.gastouHoje && estado.diasRestantes == 1 -> "último dia do mês"
    !estado.gastouHoje -> "faltam " + estado.diasRestantes + " dias"
    estado.mostrarValores -> "restam " + estado.restaCentavos.centavosComSimbolo()
    else -> "restam " + MASCARA_PRIVACIDADE
}

@Composable
private fun AvisoTeto(texto: String) {
    Text(texto, style = TextStyle(color = CoresWidget.label, fontSize = 15.sp, fontWeight = FontWeight.Medium))
}
