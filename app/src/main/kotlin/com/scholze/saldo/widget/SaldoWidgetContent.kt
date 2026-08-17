package com.scholze.saldo.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.semantics.testTag
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.scholze.saldo.MainActivity
import com.scholze.saldo.ui.money.centavosAssinadoComSimbolo
import com.scholze.saldo.ui.money.centavosComSimbolo
import com.scholze.saldo.ui.nav.Destino
import com.scholze.saldo.ui.privacy.MASCARA_PRIVACIDADE
import com.scholze.saldo.ui.theme.DarkSaldoColors
import com.scholze.saldo.ui.theme.LightSaldoColors
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

const val TAG_WIDGET_LEGENDA = "widget:legenda"
const val TAG_WIDGET_SALDO = "widget:saldo"
const val TAG_WIDGET_DELTA = "widget:delta"

private val ptBr = Locale.forLanguageTag("pt-BR")
private val diaCurto = DateTimeFormatter.ofPattern("d MMM", ptBr)

/** Cores dia/noite tiradas dos tokens do app — o launcher decide o modo, não o tema escolhido em "mais". */
private object CoresWidget {
    val fundo = ColorProvider(day = LightSaldoColors.surface, night = DarkSaldoColors.surface)
    val label = ColorProvider(day = LightSaldoColors.label, night = DarkSaldoColors.label)
    val secundario = ColorProvider(day = LightSaldoColors.secondaryLabel, night = DarkSaldoColors.secondaryLabel)
    val positivo = ColorProvider(day = LightSaldoColors.positive, night = DarkSaldoColors.positive)
    val negativo = ColorProvider(day = LightSaldoColors.categoryVariable, night = DarkSaldoColors.categoryVariable)
    val tint = ColorProvider(day = LightSaldoColors.tint, night = DarkSaldoColors.tint)
    // Só a fábrica dia/noite é importada (evita o choque de nome com a interface `androidx.glance.unit.ColorProvider`).
    val branco = ColorProvider(day = Color.White, night = Color.White)
}

/**
 * O widget: hero do saldo projetado (mascarado por padrão) e um `+`. Não lê Context nem
 * repositório — recebe tudo em [estado], para poder ser testado na JVM; o único relógio é o mês
 * corrente para os estados sem número. Em COMPACTO só cabem o valor e o botão; em LARGO entram a
 * legenda e o delta.
 */
@Composable
fun SaldoWidgetContent(estado: WidgetEstado) {
    val largo = LocalSize.current.width >= SaldoWidget.LARGO.width
    // O mês a que o número pertence, não o mês corrente — senão virar o mês sem abrir o app deixa
    // o toque no valor abrindo o ledger no mês errado.
    val destinoValor = when (estado) {
        is WidgetEstado.Pronto -> Destino.Saldos(YearMonth.from(estado.projetadoEm))
        else -> Destino.Saldos(YearMonth.now())
    }
    Row(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(CoresWidget.fundo)
            .cornerRadius(16.dp)
            .padding(horizontal = if (largo) 14.dp else 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(GlanceModifier.defaultWeight().clickable(abrir(destinoValor))) {
            when (estado) {
                WidgetEstado.SemOnboarding -> Text(
                    "toque para começar",
                    style = TextStyle(color = CoresWidget.label, fontSize = 15.sp, fontWeight = FontWeight.Medium),
                )
                WidgetEstado.Falha -> {
                    Text("não foi possível carregar", style = TextStyle(color = CoresWidget.secundario, fontSize = 12.sp))
                    Text("toque para abrir", style = TextStyle(color = CoresWidget.label, fontSize = 15.sp, fontWeight = FontWeight.Medium))
                }
                is WidgetEstado.Pronto -> {
                    if (largo) {
                        Text(
                            "saldo projetado · " + estado.projetadoEm.format(diaCurto).removeSuffix("."),
                            modifier = GlanceModifier.semantics { testTag = TAG_WIDGET_LEGENDA },
                            style = TextStyle(color = CoresWidget.secundario, fontSize = 12.sp),
                            maxLines = 1,
                        )
                    }
                    Text(
                        if (estado.mostrarValores) estado.saldoProjetadoCentavos.centavosComSimbolo() else MASCARA_PRIVACIDADE,
                        modifier = GlanceModifier.semantics { testTag = TAG_WIDGET_SALDO },
                        style = TextStyle(color = CoresWidget.label, fontSize = if (largo) 22.sp else 18.sp, fontWeight = FontWeight.Bold),
                        maxLines = 1,
                    )
                    if (largo && estado.mostrarValores) {
                        Text(
                            estado.deltaNoMesCentavos.centavosAssinadoComSimbolo() + " no mês",
                            modifier = GlanceModifier.semantics { testTag = TAG_WIDGET_DELTA },
                            style = TextStyle(
                                color = if (estado.deltaNoMesCentavos < 0) CoresWidget.negativo else CoresWidget.positivo,
                                fontSize = 12.sp,
                            ),
                            maxLines = 1,
                        )
                    }
                }
            }
        }
        if (estado != WidgetEstado.SemOnboarding) {
            // COMPACTO aperta tudo: um valor de 22 sp + este botão de 36 dp + o padding de 14 dp
            // não cabem juntos numa célula 2×1 (a moldura clipa o valor revelado) — em COMPACTO o
            // botão encolhe junto com o valor e o padding da Row.
            val tamanhoBotao = if (largo) 36.dp else 30.dp
            Box(
                modifier = GlanceModifier
                    .size(tamanhoBotao)
                    .background(CoresWidget.tint)
                    .cornerRadius(tamanhoBotao / 2)
                    .clickable(abrir(Destino.NovaMovimentacao))
                    .semantics { contentDescription = "nova movimentação" },
                contentAlignment = Alignment.Center,
            ) {
                Text("+", style = TextStyle(color = CoresWidget.branco, fontSize = if (largo) 22.sp else 18.sp, fontWeight = FontWeight.Bold))
            }
        }
    }
}

private fun abrir(destino: Destino): Action = actionStartActivity<MainActivity>(destino.paraParametros())

/** Os mesmos extras de [Destino.aplicarEm], no formato do Glance — que os converte em extras do Intent. */
internal fun Destino.paraParametros(): ActionParameters {
    val pares: List<ActionParameters.Pair<out Any>> = paraPares().map { (chave, valor) ->
        when (valor) {
            is Int -> ActionParameters.Key<Int>(chave) to valor
            else -> ActionParameters.Key<String>(chave) to valor.toString()
        }
    }
    return actionParametersOf(*pares.toTypedArray())
}
