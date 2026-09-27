package com.scholze.saldo.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.action.clickable
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ColumnScope
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.scholze.saldo.ui.nav.Destino
import java.time.format.DateTimeFormatter
import java.util.Locale

internal val ptBrWidget: Locale = Locale.forLanguageTag("pt-BR")
internal val nomeDoMes: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM", ptBrWidget)
internal val mesCurto: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM", ptBrWidget)

/**
 * Os cinco layouts de todo widget. Todos usam `SizeMode.Exact`: o launcher entrega o tamanho de
 * verdade, o formato sai dele, e o que é medido (barras, treemap, anel) é medido contra a caixa
 * real — com `Responsive` a conta era feita contra o balde e sobrava espaço vazio.
 *
 * Os cortes são em dp e não em células porque a célula muda de launcher para launcher: uma linha
 * fica abaixo de 110dp em todos, duas colunas abaixo de 200dp, duas linhas abaixo de 230dp.
 */
internal enum class Formato { MINI, LINHA, QUADRADO, LARGO, GRANDE }

internal fun formatoDe(tamanho: DpSize): Formato = when {
    tamanho.width < 110.dp && tamanho.height < 110.dp -> Formato.MINI
    tamanho.height < 110.dp -> Formato.LINHA
    tamanho.width < 200.dp -> Formato.QUADRADO
    tamanho.height < 230.dp -> Formato.LARGO
    else -> Formato.GRANDE
}

internal fun margemDe(formato: Formato): Dp = when (formato) {
    Formato.MINI -> 8.dp
    Formato.LINHA -> 12.dp
    else -> 14.dp
}

/** O cartão de todo widget: fundo, canto, margem, o toque que abre o app e a frase do TalkBack. */
@Composable
internal fun Moldura(
    destino: Destino,
    formato: Formato,
    descricao: String,
    margem: Dp = margemDe(formato),
    conteudo: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(CoresWidget.fundo)
            .cornerRadius(24.dp)
            .clickable(abrirWidget(destino))
            .padding(margem)
            .semantics { contentDescription = descricao },
        content = conteudo,
    )
}

@Composable
internal fun Cabecalho(titulo: String, direita: String? = null) {
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            titulo,
            modifier = GlanceModifier.defaultWeight(),
            style = TextStyle(color = CoresWidget.label, fontSize = 13.sp, fontWeight = FontWeight.Medium),
            maxLines = 1,
        )
        if (direita != null) Meta(direita)
    }
}

@Composable
internal fun Meta(texto: String, modifier: GlanceModifier = GlanceModifier, cor: ColorProvider = CoresWidget.secundario) {
    Text(texto, modifier = modifier, style = TextStyle(color = cor, fontSize = 12.sp), maxLines = 1)
}

@Composable
internal fun Numero(texto: String, tamanho: TextUnit, cor: ColorProvider = CoresWidget.label, modifier: GlanceModifier = GlanceModifier) {
    Text(texto, modifier = modifier, style = TextStyle(color = cor, fontSize = tamanho, fontWeight = FontWeight.Bold), maxLines = 1)
}

@Composable
internal fun Vao(altura: Dp = 8.dp) = Spacer(GlanceModifier.height(altura))

/** Antes do onboarding, ou numa falha: o widget inteiro vira um convite a abrir o app. */
@Composable
internal fun Aviso(texto: String) {
    Box(GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(texto, style = TextStyle(color = CoresWidget.label, fontSize = 14.sp, fontWeight = FontWeight.Medium))
    }
}

/**
 * Uma caixa de cor com borda: a borda é o fundo de fora e o miolo o de dentro. O Glance não tem
 * `border`, e é assim que se desenha o anel de hoje no mês e o contorno dos dias que faltam.
 */
@Composable
internal fun Contornada(modifier: GlanceModifier, borda: ColorProvider, miolo: ColorProvider, espessura: Dp, raio: Dp) {
    Box(modifier.background(borda).cornerRadius(raio).padding(espessura)) {
        Box(GlanceModifier.fillMaxSize().background(miolo).cornerRadius(maxOf(0.dp, raio - espessura))) {}
    }
}

/** Fração → "17%"; o sinal de menos tipográfico, não o hífen. */
internal fun porcento(valor: Int): String = if (valor < 0) "−${-valor}%" else "$valor%"
