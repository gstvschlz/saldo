package com.scholze.saldo.widget

import android.content.Context
import android.util.Log
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import com.scholze.saldo.SaldoApplication
import com.scholze.saldo.domain.FiltroLedger
import com.scholze.saldo.domain.ProjectionEngine
import java.time.YearMonth
import kotlinx.coroutines.flow.first

/**
 * Lê o mesmo repositório do app, projeta o mês corrente e entrega um [WidgetEstado] ao conteúdo.
 * Um snapshot (`first()`), não uma coleta: quem atualiza o widget quando algo muda é o
 * [WidgetRefresher] (processo vivo), o `updatePeriodMillis` do provider (6 h) e o worker dos
 * lembretes (09:00).
 */
class SaldoWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Responsive(setOf(COMPACTO, LARGO))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val estado = try {
            carregar(context)
        } catch (e: Exception) {
            Log.e(TAG, "widget: falha ao carregar", e)
            WidgetEstado.Falha
        }
        provideContent { SaldoWidgetContent(estado) }
    }

    private suspend fun carregar(context: Context): WidgetEstado {
        val container = (context.applicationContext as SaldoApplication).container
        val settings = container.settings.settings.first()
        if (settings.saldoInicialCentavos == null) return WidgetEstado.SemOnboarding
        val input = container.repository.ledger.first()
        val mes = ProjectionEngine.mes(input, YearMonth.from(input.hoje), FiltroLedger.TODAS)
        return WidgetEstado.Pronto(
            projetadoEm = mes.projetadoEm,
            saldoProjetadoCentavos = mes.saldoProjetadoCentavos,
            deltaNoMesCentavos = mes.deltaNoMesCentavos,
            mostrarValores = settings.widgetMostrarValores,
        )
    }

    companion object {
        private const val TAG = "saldo"
        val COMPACTO = DpSize(110.dp, 50.dp)
        val LARGO = DpSize(250.dp, 50.dp)
    }
}

class SaldoWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SaldoWidget()
}
