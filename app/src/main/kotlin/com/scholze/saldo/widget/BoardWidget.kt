package com.scholze.saldo.widget

import android.content.Context
import androidx.compose.runtime.Composable
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

    override val sizeMode: SizeMode = SizeMode.Responsive(setOf(PEQUENO, GRANDE))

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

    companion object {
        /**
         * 2×2. A grade não perde nenhum dia ao encolher — o que encolhe é o quadradinho, que
         * o conteúdo calcula a partir da largura viva. É o piso: com menos de sete colunas de
         * 8dp não sobra board, sobra ruído.
         */
        val PEQUENO = DpSize(110.dp, 110.dp)

        /** 4×2, o tamanho com que o widget nasce. */
        val GRANDE = DpSize(250.dp, 110.dp)
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

class BoardWidgetReceiver : SaldoWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = BoardWidget()
}

@Composable
fun BoardWidgetContent(estado: BoardWidgetEstado) {
    val mes = (estado as? BoardWidgetEstado.Pronto)?.mes ?: mesDeHoje()
    val largura = LocalSize.current.width
    val compacto = largura < BoardWidget.GRANDE.width
    val margem = if (compacto) 10.dp else 14.dp
    val vao = if (compacto) 2.dp else 3.dp
    // Sete colunas e sete vãos (cada célula carrega o seu à direita) têm de caber na largura
    // viva. O teto de 14dp é o board de sempre: um widget largo ganha ar à direita, não um
    // tabuleiro de damas. O piso de 8dp é o ponto em que um quadradinho ainda é uma cor.
    val lado = ((largura - margem * 2 - vao * 7) / 7).coerceIn(8.dp, 14.dp)
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(CoresWidget.fundo)
            .cornerRadius(16.dp)
            .clickable(abrirWidget(Destino.Saldos(mes, LocalDate.now().dayOfMonth)))
            .padding(horizontal = margem, vertical = if (compacto) 8.dp else 10.dp),
    ) {
        when (estado) {
            BoardWidgetEstado.SemOnboarding -> AvisoBoard("toque para começar")
            BoardWidgetEstado.Falha -> AvisoBoard("não foi possível carregar")
            is BoardWidgetEstado.Pronto -> {
                Text(
                    estado.mes.format(mesBoardWidget),
                    modifier = GlanceModifier.semantics { testTag = TAG_BOARD_WIDGET_MES },
                    style = TextStyle(color = CoresWidget.secundario, fontSize = if (compacto) 11.sp else 12.sp),
                    maxLines = 1,
                )
                Box(GlanceModifier.padding(top = if (compacto) 4.dp else 6.dp)) {
                    Column(modifier = GlanceModifier.fillMaxWidth()) {
                        estado.semanas.forEach { semana ->
                            Row(
                                modifier = GlanceModifier.fillMaxWidth().padding(bottom = vao),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                semana.forEach { nivel ->
                                    Box(GlanceModifier.padding(end = vao)) { Celula(nivel, lado) }
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
 * Uma célula de [lado] dp. `null` — dia de outro mês ou anterior ao saldo inicial — vira um vão
 * transparente do mesmo tamanho, para as colunas continuarem alinhadas.
 */
@Composable
private fun Celula(nivel: Int?, lado: Dp) {
    if (nivel == null) {
        Box(GlanceModifier.size(lado)) {}
        return
    }
    Box(
        GlanceModifier
            .size(lado)
            .cornerRadius(if (lado < 12.dp) 3.dp else 4.dp)
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
