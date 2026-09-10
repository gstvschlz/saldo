package com.scholze.saldo.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
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
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.semantics.semantics
import androidx.glance.semantics.testTag
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.scholze.saldo.domain.GrupoGasto
import com.scholze.saldo.domain.InsightsEngine
import com.scholze.saldo.ui.money.centavosAssinado
import com.scholze.saldo.ui.money.centavosComSimbolo
import com.scholze.saldo.ui.nav.Destino
import com.scholze.saldo.ui.privacy.MASCARA_PRIVACIDADE
import com.scholze.saldo.ui.totais.charts.ChartMath
import java.time.YearMonth

const val TAG_PARAONDEFOI_TOTAL = "widget:paraondefoi:total"

/** Quantas fatias cabem na lista de uma célula 4×2. */
private const val FATIAS_VISIVEIS = 4

/** E quantas cabem quando o widget encolhe para 2×2 — o resto entra na barra e só nela. */
private const val FATIAS_COMPACTO = 2

/** O que o widget "para onde foi" desenha. Sem Context e sem repositório: testável na JVM. */
sealed interface ParaOndeFoiEstado {
    data object SemOnboarding : ParaOndeFoiEstado
    data object Falha : ParaOndeFoiEstado
    data class Pronto(
        val mes: YearMonth,
        val saidasCentavos: Long,
        /** Larguras já normalizadas (com piso), na ordem da barra. */
        val barra: List<Segmento>,
        val fatias: List<Segmento>,
        val mostrarValores: Boolean,
    ) : ParaOndeFoiEstado

    /** Uma fatia pronta para desenhar: nome, cor ARGB e o quanto ocupa. */
    data class Segmento(val nome: String, val cor: Long, val centavos: Long, val fracao: Float)
}

/**
 * "para onde foi": as saídas do mês corrente por tag. Lê `InsightsEngine.paraOndeFoi`, que a
 * insights-1 já entregou testado.
 */
class ParaOndeFoiWidget : GlanceAppWidget() {

    // Responsivo e não `Single` também por um motivo que não é o tamanho: a barra mede
    // `LocalSize`, e em `Single` isso é sempre o mínimo declarado — a barra ficava calculada
    // para 250dp em qualquer largura.
    override val sizeMode: SizeMode = SizeMode.Responsive(setOf(PEQUENO, GRANDE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val estado = when (val carga = carregarWidget(context)) {
            Carga.SemOnboarding -> ParaOndeFoiEstado.SemOnboarding
            Carga.Falha -> ParaOndeFoiEstado.Falha
            is Carga.Pronto -> {
                val p = InsightsEngine.paraOndeFoi(carga.input, carga.mes)
                // A MESMA matemática da aba totais: piso de 2 % e renormalização. Só o desenho
                // difere, porque o Glance não tem Canvas — a conta não pode ter duas verdades.
                val larguras = ChartMath.larguras(p.barra.map { it.share })
                ParaOndeFoiEstado.Pronto(
                    mes = carga.mes,
                    saidasCentavos = p.saidasCentavos,
                    barra = p.barra.mapIndexed { i, f ->
                        ParaOndeFoiEstado.Segmento(nomeDoGrupo(f.grupo), corDoGrupo(f.grupo), f.centavos, larguras.getOrElse(i) { 0f })
                    },
                    fatias = p.fatias.take(FATIAS_VISIVEIS).map { f ->
                        ParaOndeFoiEstado.Segmento(nomeDoGrupo(f.grupo), corDoGrupo(f.grupo), f.centavos, f.share)
                    },
                    mostrarValores = carga.mostrarValores,
                )
            }
        }
        provideContent { ParaOndeFoiWidgetContent(estado) }
    }

    companion object {
        /**
         * 2×2. O cabeçalho troca de eixo (rótulo em cima, total embaixo) porque "para onde
         * foi" e o valor não cabem lado a lado nessa largura, e a lista cai para duas fatias.
         */
        val PEQUENO = DpSize(110.dp, 110.dp)

        /** 4×2, o tamanho com que o widget nasce. */
        val GRANDE = DpSize(250.dp, 110.dp)
    }
}

class ParaOndeFoiWidgetReceiver : SaldoWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ParaOndeFoiWidget()
}

/** `0L` = sem cor própria; o desenho cai nos tokens do widget. */
private fun corDoGrupo(g: GrupoGasto): Long = when (g) {
    is GrupoGasto.DeTag -> g.tag.cor
    GrupoGasto.Outras -> 0L
    GrupoGasto.SemTag -> -1L
}

private fun nomeDoGrupo(g: GrupoGasto): String = when (g) {
    is GrupoGasto.DeTag -> g.tag.nome
    GrupoGasto.Outras -> "outras"
    GrupoGasto.SemTag -> "sem tag"
}

// O tipo e `androidx.glance.unit.ColorProvider`; o que se importa e a FABRICA dia/noite,
// de `androidx.glance.color` — os dois tem o mesmo nome, entao o tipo vem qualificado.
private fun provedorDe(cor: Long): androidx.glance.unit.ColorProvider = when (cor) {
    0L -> CoresWidget.outras
    -1L -> CoresWidget.trilha
    else -> ColorProvider(day = Color(cor), night = Color(cor))
}

@Composable
fun ParaOndeFoiWidgetContent(estado: ParaOndeFoiEstado) {
    val mes = (estado as? ParaOndeFoiEstado.Pronto)?.mes ?: mesDeHoje()
    val compacto = LocalSize.current.width < ParaOndeFoiWidget.GRANDE.width
    val margem = if (compacto) 10.dp else 14.dp
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(CoresWidget.fundo)
            .cornerRadius(16.dp)
            .clickable(abrirWidget(Destino.Totais(mes)))
            .padding(horizontal = margem, vertical = if (compacto) 8.dp else 10.dp),
    ) {
        when (estado) {
            ParaOndeFoiEstado.SemOnboarding ->
                Text("toque para começar", style = TextStyle(color = CoresWidget.label, fontSize = 15.sp, fontWeight = FontWeight.Medium))
            ParaOndeFoiEstado.Falha ->
                Text("não foi possível carregar", style = TextStyle(color = CoresWidget.label, fontSize = 15.sp, fontWeight = FontWeight.Medium))
            is ParaOndeFoiEstado.Pronto -> Corpo(estado, compacto, margem)
        }
    }
}

@Composable
private fun Corpo(estado: ParaOndeFoiEstado.Pronto, compacto: Boolean, margem: Dp) {
    val total = if (estado.mostrarValores) estado.saidasCentavos.centavosComSimbolo() else MASCARA_PRIVACIDADE
    val estiloTotal = TextStyle(color = CoresWidget.label, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    val estiloRotulo = TextStyle(color = CoresWidget.secundario, fontSize = if (compacto) 11.sp else 12.sp)

    // Em 2×2 o rótulo e o valor não cabem na mesma linha, então o cabeçalho empilha — a mesma
    // troca de eixo que o widget do saldo faz no quadrado.
    if (compacto) {
        Text("para onde foi", style = estiloRotulo, maxLines = 1)
        Text(
            total,
            modifier = GlanceModifier.semantics { testTag = TAG_PARAONDEFOI_TOTAL },
            style = estiloTotal,
            maxLines = 1,
        )
    } else {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = GlanceModifier.fillMaxWidth()) {
            Text(
                "para onde foi",
                modifier = GlanceModifier.defaultWeight(),
                style = estiloRotulo,
                maxLines = 1,
            )
            Text(
                total,
                modifier = GlanceModifier.semantics { testTag = TAG_PARAONDEFOI_TOTAL },
                style = estiloTotal,
                maxLines = 1,
            )
        }
    }

    if (estado.barra.isEmpty()) {
        Box(GlanceModifier.padding(top = if (compacto) 6.dp else 8.dp)) {
            Text("nenhuma saída neste mês", style = estiloRotulo, maxLines = 1)
        }
        return
    }

    // A barra 100 %. O Glance não tem Canvas E o `defaultWeight()` dele é peso IGUAL, sem
    // fração — então a largura de cada segmento é calculada em dp a partir da largura viva do
    // widget. É por isso que a barra mora aqui e não pode reusar o SegmentedBar do Compose.
    val disponivel = LocalSize.current.width - margem * 2
    val altura = if (compacto) 8.dp else 10.dp
    Row(modifier = GlanceModifier.fillMaxWidth().padding(top = if (compacto) 6.dp else 8.dp)) {
        estado.barra.forEach { seg ->
            val largura = disponivel * seg.fracao
            if (largura > 0.dp) {
                Box(GlanceModifier.width(largura).height(altura).background(provedorDe(seg.cor)).cornerRadius(altura / 2)) {}
            }
        }
    }

    val ponto = if (compacto) 6.dp else 8.dp
    estado.fatias.take(if (compacto) FATIAS_COMPACTO else FATIAS_VISIVEIS).forEach { f ->
        Row(
            modifier = GlanceModifier.fillMaxWidth().padding(top = if (compacto) 4.dp else 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(GlanceModifier.size(ponto).background(provedorDe(f.cor)).cornerRadius(ponto / 2)) {}
            Box(GlanceModifier.padding(horizontal = if (compacto) 4.dp else 6.dp)) {
                Text(
                    f.nome,
                    style = TextStyle(color = CoresWidget.label, fontSize = if (compacto) 11.sp else 13.sp),
                    maxLines = 1,
                )
            }
            Box(GlanceModifier.defaultWeight()) {}
            Text(
                if (estado.mostrarValores) (-f.centavos).centavosAssinado() else MASCARA_PRIVACIDADE,
                style = TextStyle(color = CoresWidget.secundario, fontSize = if (compacto) 11.sp else 13.sp),
                maxLines = 1,
            )
        }
    }
}
