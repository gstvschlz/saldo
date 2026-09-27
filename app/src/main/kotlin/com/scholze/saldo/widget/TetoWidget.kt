package com.scholze.saldo.widget

import android.content.Context
import androidx.compose.runtime.Composable
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
import androidx.glance.layout.width
import androidx.glance.unit.ColorProvider
import com.scholze.saldo.domain.LedgerInput
import com.scholze.saldo.domain.Teto
import com.scholze.saldo.domain.TetoEngine
import com.scholze.saldo.ui.nav.Destino
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.roundToInt

/** Quantos dias o "hoje" olha para trás na sequência. */
internal const val DIAS_DA_SEQUENCIA = 14

sealed interface TetoWidgetEstado {
    data object SemOnboarding : TetoWidgetEstado
    data object Falha : TetoWidgetEstado

    /** O mês não teve entrada nenhuma: não há renda para dividir. Ver [TetoEngine.teto]. */
    data object SemRenda : TetoWidgetEstado

    /**
     * [cabe] é quanto do teto de hoje ainda cabe, em 0..100 — nunca o valor. [dias] são os últimos
     * [DIAS_DA_SEQUENCIA] dias, hoje por último: `true` dentro do teto, `false` estourou, `null`
     * sem teto naquele dia (antes do saldo inicial ou num mês sem renda).
     */
    data class Pronto(
        val mes: YearMonth,
        val cabe: Int,
        val estourouODia: Boolean,
        val estourouOMes: Boolean,
        val dias: List<Boolean?>,
    ) : TetoWidgetEstado {
        val sequencia: Int get() = dias.takeLastWhile { it == true }.size

        val numero: String get() = if (estourouOMes) "—" else "$cabe%"

        val legenda: String get() = when {
            estourouOMes -> "o mês já estourou"
            estourouODia -> "o dia estourou o teto"
            else -> "do teto ainda cabe no dia"
        }
    }
}

/** Quanto do teto de hoje ainda cabe, em 0..100. */
internal fun cabeHoje(t: Teto): Int = when {
    t.tetoCentavos <= 0 || t.estourouODia -> 0
    else -> (t.restaCentavos * 100f / t.tetoCentavos).roundToInt().coerceIn(0, 100)
}

/**
 * Os últimos [dias] dias contra o teto de cada um: o motor roda de novo com `hoje` naquele dia,
 * que é exatamente o teto que aquele dia teve (fixado à meia-noite dele).
 */
internal fun historicoDoTeto(input: LedgerInput, meta: Int, dias: Int = DIAS_DA_SEQUENCIA): List<Boolean?> =
    (dias - 1 downTo 0).map { atras ->
        val dia: LocalDate = input.hoje.minusDays(atras.toLong())
        if (dia < input.saldoInicialData) {
            null
        } else {
            TetoEngine.teto(input.copy(hoje = dia), meta)?.let { !it.estourouODia && !it.estourouOMes }
        }
    }

/** "hoje": quanto do teto do dia ainda cabe, e há quantos dias o teto vem sendo respeitado. */
class TetoWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val estado = when (val carga = carregarWidget(context)) {
            Carga.SemOnboarding -> TetoWidgetEstado.SemOnboarding
            Carga.Falha -> TetoWidgetEstado.Falha
            is Carga.Pronto -> TetoEngine.teto(carga.input, carga.metaGuardarPercent)?.let { t ->
                TetoWidgetEstado.Pronto(
                    mes = t.mes,
                    cabe = cabeHoje(t),
                    estourouODia = t.estourouODia,
                    estourouOMes = t.estourouOMes,
                    dias = historicoDoTeto(carga.input, carga.metaGuardarPercent),
                )
            } ?: TetoWidgetEstado.SemRenda
        }
        provideContent { TetoWidgetContent(estado) }
    }
}

class TetoWidgetReceiver : SaldoWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TetoWidget()
}

@Composable
fun TetoWidgetContent(estado: TetoWidgetEstado) {
    val formato = formatoDe(LocalSize.current)
    val pronto = estado as? TetoWidgetEstado.Pronto
    val descricao = pronto?.let { "hoje: ${it.numero.replace("—", "")} ${it.legenda}".replace("  ", " ") } ?: "hoje"
    Moldura(Destino.Saldos(pronto?.mes ?: mesDeHoje(), LocalDate.now().dayOfMonth), formato, descricao) {
        when (estado) {
            TetoWidgetEstado.SemOnboarding -> Aviso("toque para começar")
            TetoWidgetEstado.Falha -> Aviso("não foi possível carregar")
            TetoWidgetEstado.SemRenda -> Aviso("sem entrada no mês")
            is TetoWidgetEstado.Pronto -> CorpoHoje(estado, formato)
        }
    }
}

@Composable
private fun androidx.glance.layout.ColumnScope.CorpoHoje(e: TetoWidgetEstado.Pronto, formato: Formato) {
    val cor = if (e.estourouODia || e.estourouOMes) CoresWidget.negativo else CoresWidget.tint
    when (formato) {
        Formato.MINI -> Column(GlanceModifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalAlignment = Alignment.CenterVertically) {
            Numero(e.numero, 24.sp, cor)
            Vao(6.dp)
            Meta("cabe hoje")
        }
        Formato.LINHA -> Row(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            Column {
                Meta("hoje ainda cabe")
                Numero(e.numero, 26.sp, cor)
            }
            Spacer(GlanceModifier.width(14.dp))
            Segmentos(e, 28.dp, GlanceModifier.defaultWeight())
        }
        else -> {
            val grande = formato == Formato.GRANDE
            Cabecalho("hoje", if (formato == Formato.QUADRADO) "teto do dia" else "${e.sequencia} dias seguidos no teto")
            Vao(6.dp)
            Row(verticalAlignment = Alignment.Bottom) {
                Numero(e.numero, if (grande) 44.sp else 32.sp, cor)
                if (formato != Formato.QUADRADO) {
                    Spacer(GlanceModifier.width(8.dp))
                    Meta(e.legenda)
                }
            }
            if (formato == Formato.QUADRADO) Meta(e.legenda)
            if (formato == Formato.GRANDE) {
                Vao()
                Segmentos(e, 20.dp, GlanceModifier.fillMaxWidth())
                Vao(10.dp)
                Meta("últimos $DIAS_DA_SEQUENCIA dias dentro do teto")
                Vao(6.dp)
                Column(GlanceModifier.fillMaxWidth().defaultWeight()) {
                    e.dias.chunked(7).forEach { semana ->
                        Row(GlanceModifier.fillMaxWidth().defaultWeight()) {
                            semana.forEach { d -> Dia(d, GlanceModifier.defaultWeight().fillMaxHeight().padding(2.dp), 5.dp) }
                        }
                    }
                }
            } else {
                Spacer(GlanceModifier.defaultWeight())
                Segmentos(e, 14.dp, GlanceModifier.fillMaxWidth())
                Vao()
                if (formato == Formato.LARGO) {
                    Row(GlanceModifier.fillMaxWidth().height(12.dp)) {
                        e.dias.forEach { Dia(it, GlanceModifier.defaultWeight().fillMaxHeight().padding(horizontal = 1.5.dp), 3.dp) }
                    }
                } else {
                    Meta("${e.sequencia} dias seguidos no teto")
                }
            }
        }
    }
}

/** Dez fatias do teto de hoje: as que já foram, em rosa claro; as que ainda cabem, em verde. */
@Composable
private fun Segmentos(e: TetoWidgetEstado.Pronto, altura: Dp, modifier: GlanceModifier) {
    val cheios = (e.cabe + 5) / 10
    Row(modifier.height(altura)) {
        repeat(10) { i ->
            val cor: ColorProvider = if (i >= 10 - cheios) CoresWidget.tint else CoresWidget.tomDoBoard(-1)
            Box(GlanceModifier.defaultWeight().fillMaxHeight().padding(horizontal = 1.5.dp)) {
                Box(GlanceModifier.fillMaxSize().background(cor).cornerRadius(4.dp)) {}
            }
        }
    }
}

@Composable
private fun Dia(dentro: Boolean?, modifier: GlanceModifier, raio: Dp) {
    val cor = when (dentro) {
        true -> CoresWidget.tomDoBoard(2)
        false -> CoresWidget.tomDoBoard(-2)
        null -> CoresWidget.tomDoBoard(0)
    }
    Box(modifier) { Box(GlanceModifier.fillMaxSize().background(cor).cornerRadius(raio)) {} }
}
