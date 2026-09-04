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
import androidx.glance.layout.size
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.semantics.testTag
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.scholze.saldo.domain.BoardEngine
import com.scholze.saldo.ui.nav.Destino
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

const val TAG_BOARD_WIDGET_MES = "widget:board:mes"

private val ptBrBoard = Locale.forLanguageTag("pt-BR")
private val mesBoardWidget = DateTimeFormatter.ofPattern("MMMM", ptBrBoard)

/** O que o widget do board desenha. Sem Context e sem repositório, para ser testado na JVM. */
sealed interface BoardWidgetEstado {
    data object SemOnboarding : BoardWidgetEstado
    data object Falha : BoardWidgetEstado

    /**
     * [semanas] são linhas de sete, de segunda a domingo, com `null` nas pontas que não são
     * do mês. Cada célula é o NÍVEL (−3..3) daquele dia, nunca o valor: o widget não tem
     * máscara de privacidade porque não há o que mascarar — cor não é número.
     */
    data class Pronto(
        val mes: YearMonth,
        val semanas: List<List<Int?>>,
    ) : BoardWidgetEstado
}

/**
 * O board na tela inicial: o mês corrente até hoje, em quadradinhos.
 *
 * É o único widget sem máscara e o único que não mostra dinheiro nenhum. Uma célula rosa diz
 * "saiu mais do que entrou naquele dia" sem dizer quanto — que é exatamente o que se quer ver
 * de relance na home do telefone, e exatamente o que não constrange quando alguém olha por
 * cima do ombro.
 */
class BoardWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val estado = when (val carga = carregarWidget(context)) {
            Carga.SemOnboarding -> BoardWidgetEstado.SemOnboarding
            Carga.Falha -> BoardWidgetEstado.Falha
            is Carga.Pronto -> {
                val board = BoardEngine.board(carga.input, carga.mes)
                BoardWidgetEstado.Pronto(
                    mes = carga.mes,
                    semanas = semanasDoWidget(board.dias.map { dia ->
                        if (dia.dentroDaJanela) dia.nivel else null
                    }, board.inicio),
                )
            }
        }
        provideContent { BoardWidgetContent(estado) }
    }
}

/**
 * Quebra os níveis em semanas de segunda a domingo, completando as pontas com `null`.
 *
 * Um dia fora da janela (antes do saldo inicial) já chega como `null` e some junto com o
 * preenchimento — as duas ausências se desenham igual, e a diferença entre elas não cabe num
 * widget.
 */
internal fun semanasDoWidget(niveis: List<Int?>, inicio: LocalDate): List<List<Int?>> {
    if (niveis.isEmpty()) return emptyList()
    val antes = inicio.dayOfWeek.value - 1
    val fim = inicio.plusDays((niveis.size - 1).toLong())
    val depois = 7 - fim.dayOfWeek.value
    return (List<Int?>(antes) { null } + niveis + List<Int?>(depois) { null }).chunked(7)
}

class BoardWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = BoardWidget()
}

@Composable
fun BoardWidgetContent(estado: BoardWidgetEstado) {
    val mes = (estado as? BoardWidgetEstado.Pronto)?.mes ?: mesDeHoje()
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(CoresWidget.fundo)
            .cornerRadius(16.dp)
            .clickable(abrirWidget(Destino.Saldos(mes, LocalDate.now().dayOfMonth)))
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        when (estado) {
            BoardWidgetEstado.SemOnboarding -> AvisoBoard("toque para começar")
            BoardWidgetEstado.Falha -> AvisoBoard("não foi possível carregar")
            is BoardWidgetEstado.Pronto -> {
                Text(
                    estado.mes.format(mesBoardWidget),
                    modifier = GlanceModifier.semantics { testTag = TAG_BOARD_WIDGET_MES },
                    style = TextStyle(color = CoresWidget.secundario, fontSize = 12.sp),
                    maxLines = 1,
                )
                Box(GlanceModifier.padding(top = 6.dp)) {
                    Column(modifier = GlanceModifier.fillMaxWidth()) {
                        estado.semanas.forEach { semana ->
                            Row(
                                modifier = GlanceModifier.fillMaxWidth().padding(bottom = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                semana.forEach { nivel ->
                                    Box(GlanceModifier.padding(end = 3.dp)) { Celula(nivel) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Uma célula de 14dp. `null` — dia de outro mês ou anterior ao saldo inicial — vira um vão
 * transparente do mesmo tamanho, para as colunas continuarem alinhadas.
 */
@Composable
private fun Celula(nivel: Int?) {
    if (nivel == null) {
        Box(GlanceModifier.size(14.dp)) {}
        return
    }
    Box(
        GlanceModifier
            .size(14.dp)
            .cornerRadius(4.dp)
            .background(CoresWidget.tomDoBoard(nivel))
            .semantics { contentDescription = descricaoDoNivel(nivel) },
    ) {}
}

/** O que o leitor de tela ouve. Sem valor: o widget não mostra número nenhum. */
internal fun descricaoDoNivel(nivel: Int): String = when {
    nivel < 0 -> "dia em que saiu mais"
    nivel > 0 -> "dia em que entrou mais"
    else -> "dia sem movimentação"
}

@Composable
private fun AvisoBoard(texto: String) {
    Text(texto, style = TextStyle(color = CoresWidget.label, fontSize = 15.sp, fontWeight = FontWeight.Medium))
}
