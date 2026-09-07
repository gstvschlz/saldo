package com.scholze.saldo.widget

import android.content.Context
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import com.scholze.saldo.domain.FiltroLedger
import com.scholze.saldo.domain.ProjectionEngine
import java.time.YearMonth

/**
 * Lê o mesmo repositório do app, projeta o mês corrente e entrega um [WidgetEstado] ao conteúdo.
 * Um snapshot (`first()`), não uma coleta: quem atualiza o widget quando algo muda é o
 * [WidgetRefresher] (processo vivo), o `updatePeriodMillis` do provider (6 h) e o worker dos
 * lembretes (09:00).
 */
class SaldoWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Responsive(setOf(COMPACTO, LARGO, QUADRADO))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val estado = when (val carga = carregarWidget(context)) {
            Carga.SemOnboarding -> WidgetEstado.SemOnboarding
            Carga.Falha -> WidgetEstado.Falha
            is Carga.Pronto -> {
                val mes = ProjectionEngine.mes(carga.input, carga.mes, FiltroLedger.TODAS)
                WidgetEstado.Pronto(
                    projetadoEm = mes.projetadoEm,
                    saldoProjetadoCentavos = mes.saldoProjetadoCentavos,
                    deltaNoMesCentavos = mes.deltaNoMesCentavos,
                    mostrarValores = carga.mostrarValores,
                    taxaGuardada = mes.taxaGuardada,
                )
            }
        }
        provideContent { SaldoWidgetContent(estado) }
    }

    companion object {
        val COMPACTO = DpSize(110.dp, 50.dp)
        val LARGO = DpSize(250.dp, 50.dp)

        /**
         * 2×2. Aqui o layout MUDA de eixo: em COMPACTO/LARGO tudo cabe numa linha com o botão
         * ao lado, e com altura de verdade o número vira o herói, com o botão embaixo.
         */
        val QUADRADO = DpSize(150.dp, 110.dp)
    }
}

class SaldoWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SaldoWidget()
}
