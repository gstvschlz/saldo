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
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.RowScope
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import com.scholze.saldo.domain.Board
import com.scholze.saldo.domain.BoardEngine
import com.scholze.saldo.ui.nav.Destino
import java.time.LocalDate
import java.time.YearMonth

/** Um quadradinho do mês. */
sealed interface CelulaMes {
    /** Fora do mês (as pontas da primeira e da última semana) ou antes do saldo inicial. */
    data object Fora : CelulaMes
    /** Um dia que ainda não chegou: só o contorno. */
    data object Futuro : CelulaMes
    /** O NÍVEL do dia (−3..3), nunca o valor: cor não é número. */
    data class Dia(val nivel: Int, val hoje: Boolean) : CelulaMes
}

sealed interface BoardWidgetEstado {
    data object SemOnboarding : BoardWidgetEstado
    data object Falha : BoardWidgetEstado

    /** [dias] tem um item por dia do mês, na ordem; [semanas] é a mesma coisa em linhas de 7. */
    data class Pronto(val mes: YearMonth, val hoje: Int, val dias: List<CelulaMes>, val semanas: List<List<CelulaMes>>) : BoardWidgetEstado {
        val verdes: Int get() = dias.count { it is CelulaMes.Dia && it.nivel > 0 }
        val rosas: Int get() = dias.count { it is CelulaMes.Dia && it.nivel < 0 }

        /** Quantos dias seguidos, terminando hoje, entrou mais do que saiu. */
        val sequencia: Int get() = dias.take(hoje).takeLastWhile { it is CelulaMes.Dia && it.nivel > 0 }.size
    }
}

/**
 * O mês corrente inteiro, de segunda a domingo. O board só vai até hoje; os dias que faltam
 * entram como [CelulaMes.Futuro] para a grade ter sempre o tamanho do mês.
 */
internal fun mesDoWidget(board: Board, mes: YearMonth, hoje: LocalDate): BoardWidgetEstado.Pronto {
    val porDia = board.dias.associateBy { it.data }
    val dias = (1..mes.lengthOfMonth()).map { d ->
        val data = mes.atDay(d)
        val dia = porDia[data]
        when {
            data > hoje -> CelulaMes.Futuro
            dia == null || !dia.dentroDaJanela -> CelulaMes.Fora
            else -> CelulaMes.Dia(dia.nivel, data == hoje)
        }
    }
    val antes = mes.atDay(1).dayOfWeek.value - 1
    val depois = (7 - (antes + dias.size) % 7) % 7
    val semanas = (List(antes) { CelulaMes.Fora } + dias + List(depois) { CelulaMes.Fora }).chunked(7)
    return BoardWidgetEstado.Pronto(mes, hoje.dayOfMonth, dias, semanas)
}

/** "mês": o mês até hoje em quadradinhos que enchem a caixa, e quantos dias ficaram no verde. */
class BoardWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val estado = when (val carga = carregarWidget(context)) {
            Carga.SemOnboarding -> BoardWidgetEstado.SemOnboarding
            Carga.Falha -> BoardWidgetEstado.Falha
            is Carga.Pronto -> mesDoWidget(BoardEngine.board(carga.input, carga.mes), carga.mes, carga.input.hoje)
        }
        provideContent { BoardWidgetContent(estado) }
    }
}

class BoardWidgetReceiver : SaldoWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = BoardWidget()
}

@Composable
fun BoardWidgetContent(estado: BoardWidgetEstado) {
    val formato = formatoDe(LocalSize.current)
    val pronto = estado as? BoardWidgetEstado.Pronto
    val mes = pronto?.mes ?: mesDeHoje()
    val descricao = pronto?.let { "${it.mes.format(nomeDoMes)} até hoje: ${it.verdes} dias no verde, ${it.rosas} no rosa" } ?: "o mês"
    Moldura(Destino.Saldos(mes, LocalDate.now().dayOfMonth), formato, descricao) {
        when (estado) {
            BoardWidgetEstado.SemOnboarding -> Aviso("toque para começar")
            BoardWidgetEstado.Falha -> Aviso("não foi possível carregar")
            is BoardWidgetEstado.Pronto -> CorpoMes(estado, formato)
        }
    }
}

@Composable
private fun androidx.glance.layout.ColumnScope.CorpoMes(e: BoardWidgetEstado.Pronto, formato: Formato) {
    val contagem = "${e.verdes} no verde · ${e.rosas} no rosa"
    val nome = e.mes.format(nomeDoMes)
    val raio = when (formato) {
        Formato.MINI -> 3.dp
        Formato.GRANDE -> 7.dp
        else -> 5.dp
    }
    val vao = when (formato) {
        Formato.MINI -> 1.dp
        Formato.GRANDE -> 2.5.dp
        else -> 1.5.dp
    }
    when (formato) {
        Formato.MINI -> Grade(e.semanas, raio, vao)
        Formato.LINHA -> {
            Cabecalho(nome, if (LocalSize.current.width >= 200.dp) contagem else null)
            Vao()
            Row(GlanceModifier.fillMaxWidth().defaultWeight()) {
                e.dias.take(e.hoje).takeLast(14).forEach { Celula(it, raio, 2.dp) }
            }
        }
        else -> {
            Cabecalho(nome, if (formato == Formato.QUADRADO) "dia ${e.hoje}" else contagem)
            Vao()
            if (formato == Formato.GRANDE) {
                Row(GlanceModifier.fillMaxWidth().padding(bottom = 4.dp)) {
                    listOf("S", "T", "Q", "Q", "S", "S", "D").forEach {
                        Text(
                            it,
                            modifier = GlanceModifier.defaultWeight(),
                            style = TextStyle(color = CoresWidget.secundario, fontSize = 10.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center),
                        )
                    }
                }
            }
            Box(GlanceModifier.fillMaxWidth().defaultWeight()) { Grade(e.semanas, raio, vao) }
            if (formato == Formato.GRANDE) {
                Vao()
                Meta("${e.sequencia} dias seguidos no verde · o mês até hoje")
            }
        }
    }
}

/** Linhas e colunas de peso igual: a grade enche a caixa em qualquer tamanho. */
@Composable
private fun Grade(semanas: List<List<CelulaMes>>, raio: Dp, vao: Dp) {
    Column(GlanceModifier.fillMaxSize()) {
        semanas.forEach { semana ->
            Row(GlanceModifier.fillMaxWidth().defaultWeight()) {
                semana.forEach { Celula(it, raio, vao) }
            }
        }
    }
}

@Composable
private fun RowScope.Celula(c: CelulaMes, raio: Dp, vao: Dp) {
    val fora = GlanceModifier.defaultWeight().fillMaxHeight().padding(vao)
    when (c) {
        CelulaMes.Fora -> Box(fora) {}
        CelulaMes.Futuro -> Box(fora) {
            Contornada(GlanceModifier.fillMaxSize(), CoresWidget.tomDoBoard(0), CoresWidget.fundo, 1.5.dp, raio)
        }
        is CelulaMes.Dia -> Box(fora) {
            if (c.hoje) {
                Contornada(GlanceModifier.fillMaxSize(), CoresWidget.label, CoresWidget.tomDoBoard(c.nivel), 2.dp, raio)
            } else {
                Box(GlanceModifier.fillMaxSize().background(CoresWidget.tomDoBoard(c.nivel)).cornerRadius(raio)) {}
            }
        }
    }
}
