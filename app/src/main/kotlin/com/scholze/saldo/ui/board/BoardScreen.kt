package com.scholze.saldo.ui.board

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.scholze.saldo.domain.DiaBoard
import com.scholze.saldo.domain.Fatura
import com.scholze.saldo.domain.Movimentacao
import com.scholze.saldo.ui.components.Carregando
import com.scholze.saldo.ui.components.ErroDeLeitura
import com.scholze.saldo.ui.components.IconeRedondo
import com.scholze.saldo.ui.components.arrastoDeMes
import com.scholze.saldo.ui.components.SaldoIcon
import com.scholze.saldo.ui.components.SaldoTopBar
import com.scholze.saldo.ui.ledger.BalanceHero
import com.scholze.saldo.ui.ledger.DayRow
import com.scholze.saldo.ui.ledger.DialogoFatura
import com.scholze.saldo.ui.ledger.faixaSaldos
import com.scholze.saldo.ui.money.centavosComSimbolo
import com.scholze.saldo.ui.privacy.FormatoMoney
import com.scholze.saldo.ui.privacy.LocalPrivacy
import com.scholze.saldo.ui.privacy.MASCARA_PRIVACIDADE
import com.scholze.saldo.ui.privacy.MoneyText
import com.scholze.saldo.ui.theme.SaldoTheme
import com.scholze.saldo.ui.theme.textoSobreBoard
import com.scholze.saldo.ui.theme.tomDoBoard
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

private val ptBr = Locale.forLanguageTag("pt-BR")
private val mesCurto = DateTimeFormatter.ofPattern("MMM", ptBr)
private val tituloMes = DateTimeFormatter.ofPattern("MMMM yyyy", ptBr)
private val diaLongo = DateTimeFormatter.ofPattern("d 'de' MMMM", ptBr)

/**
 * O corpo rolável do board — grade e painel do dia. Os testes acham a tela por ele e rolam
 * dentro dele; com fonte 2× a grade sozinha já passa da altura de um telefone.
 */
const val TAG_BOARD_GRADE = "board:grade"

/** A régua no rodapé — os testes leem o "dia típico" por aqui. */
const val TAG_BOARD_LEGENDA = "board:legenda"

/** O convite do mês sem nada — no lugar da régua, que não teria o que explicar. */
const val TAG_BOARD_VAZIO = "board:vazio"

/** Uma célula. Há uma por dia do mês; o dia é o que as distingue. */
fun tagCelula(data: LocalDate): String = "board:celula:${data.toEpochDay()}"

/** A calha do mês, à esquerda da grade. */
private val CALHA = 22.dp

/** A partir desta escala de fonte o número do dia não cabe na célula e some. */
private const val ESCALA_SEM_NUMERO = 1.3f

private val DIAS_SEMANA = listOf("s", "t", "q", "q", "s", "s", "d")

/**
 * O board: um mês em sete colunas de dia da semana, semanas empilhadas, e embaixo os
 * lançamentos do dia que estiver aberto.
 *
 * É a única vista da aba `saldos` desde que o ledger deixou de ser tela: a lista do mês
 * inteiro virou o painel de um dia só, que é o que se olha noventa por cento das vezes.
 *
 * Em pé e não deitada como a do GitHub por uma razão prática: a aba gasta o arrasto
 * horizontal trocando de mês, e uma grade que rolasse para o lado brigaria com esse gesto
 * todo dia. Em pé ela também cabe num telefone sem espremer a célula abaixo do que se lê.
 *
 * O hero e o cabeçalho de colunas ficam FORA da rolagem. A grade e o painel rolam juntos —
 * com fonte 2× e um dia de muitos lançamentos eles passam da tela, e foi o hero sumindo na
 * abertura que a board-1 já pagou uma vez para aprender.
 */
@Composable
fun BoardScreen(
    state: BoardUiState,
    onDiaClick: (LocalDate) -> Unit,
    onMesAnterior: () -> Unit,
    onProximoMes: () -> Unit,
    onVerLista: () -> Unit,
    onItemClick: (Movimentacao) -> Unit,
    onExcluir: (Movimentacao) -> Unit,
    onTogglePrivacidade: () -> Unit,
    onVerGuardado: () -> Unit,
    onTentar: () -> Unit = {},
    /** A meta de guardar, em %; `0` = sem meta. O mesmo hero da lista, então a mesma pill. */
    metaGuardarPercent: Int = 0,
    modifier: Modifier = Modifier,
) {
    val colors = SaldoTheme.colors
    val board = state.board
    val semanas = remember(board) { board?.let { semanasDe(it.dias) }.orEmpty() }
    // O diálogo da fatura é só leitura — não edita nada — então fechar na rotação é aceitável.
    var faturaAberta by remember { mutableStateOf<Fatura?>(null) }

    Box(
        modifier
            .fillMaxSize()
            .background(colors.background)
            .arrastoDeMes(state.mesAtual, onMesAnterior, onProximoMes),
    ) {
        Column(Modifier.fillMaxSize()) {
            SaldoTopBar(
                titulo = state.mesAtual.format(tituloMes),
                onAnterior = onMesAnterior,
                onProximo = onProximoMes,
                podeAvancar = state.podeAvancar,
                acoes = {
                    IconeRedondo(SaldoIcon.LISTA, "ver como lista", onVerLista)
                    IconeRedondo(
                        if (LocalPrivacy.current.oculto) SaldoIcon.OLHO_RISCADO else SaldoIcon.OLHO,
                        "alternar privacidade",
                        onTogglePrivacidade,
                    )
                },
            )

            val mes = state.mes
            val erro = state.erro
            if (erro != null) {
                ErroDeLeitura(erro, onTentar)
                return@Column
            }
            if (board == null || mes == null) {
                Carregando()
                return@Column
            }

            BalanceHero(mes, onTogglePrivacidade, onVerGuardado, metaGuardarPercent)
            CabecalhoColunas()

            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .testTag(TAG_BOARD_GRADE),
            ) {
                semanas.forEach { semana ->
                    LinhaSemana(semana, state.hoje, state.diaAberto, onDiaClick)
                }

                // O painel do dia entra acima da régua, e não no lugar dela: no começo do
                // mês a grade tem quatro células e a tela ficava com meio telefone vazio
                // embaixo — e a régua é o que explica a cor de todas elas.
                state.linhaDoDia?.let { linha ->
                    DayRow(
                        dia = linha,
                        faixa = mes.faixaSaldos(),
                        hoje = state.hoje,
                        onItemClick = onItemClick,
                        onExcluir = onExcluir,
                        onFaturaClick = { faturaAberta = it },
                    )
                }
                // Mês sem uma movimentação sequer: a régua explicaria a cor de células que
                // não têm cor. No lugar dela, o convite — some com o primeiro lançamento.
                if (mes.dias.all { it.itens.isEmpty() }) BoardVazio() else Legenda(board.unidadeCentavos)
            }
        }

        faturaAberta?.let { DialogoFatura(it) { faturaAberta = null } }
    }
}

/**
 * Quebra a lista contígua de dias em semanas de segunda a domingo, completando as duas
 * pontas com `null` para o primeiro dia cair na coluna certa. Sem completar a última, as
 * células do fim esticariam para preencher a linha.
 */
internal fun semanasDe(dias: List<DiaBoard>): List<List<DiaBoard?>> {
    if (dias.isEmpty()) return emptyList()
    val antes = dias.first().data.dayOfWeek.value - DayOfWeek.MONDAY.value
    val depois = DayOfWeek.SUNDAY.value - dias.last().data.dayOfWeek.value
    val completa: List<DiaBoard?> = List(antes) { null } + dias + List(depois) { null }
    return completa.chunked(7)
}

/**
 * A frase que o leitor de tela ouve. O valor respeita a privacidade — a cor não entrega
 * número nenhum, mas a descrição entregaria.
 */
internal fun descricaoDe(dia: DiaBoard, oculto: Boolean): String {
    val data = dia.data.format(diaLongo).removeSuffix(".")
    val valor = if (oculto) MASCARA_PRIVACIDADE else abs(dia.valorCentavos).centavosComSimbolo()
    val corpo = when {
        !dia.dentroDaJanela -> "sem registro"
        dia.valorCentavos == 0L -> "sem movimentação"
        dia.valorCentavos < 0 -> "saiu $valor"
        else -> "entrou $valor"
    }
    return if (dia.venceFatura) "$data, $corpo, fatura vence" else "$data, $corpo"
}

@Composable
private fun CabecalhoColunas() {
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Spacer(Modifier.width(CALHA))
        DIAS_SEMANA.forEach { d -> LegendaTexto(d, Modifier.weight(1f)) }
    }
}

@Composable
private fun LinhaSemana(
    semana: List<DiaBoard?>,
    hoje: LocalDate,
    diaAberto: LocalDate?,
    onDiaClick: (LocalDate) -> Unit,
) {
    // A grade é de um mês só, e o dia 1 cai sempre na primeira linha: o rótulo nomeia o
    // mês uma vez, no alto da calha. É o único lugar da tela que o diz — o hero mostra a
    // data da projeção, não o mês.
    val marco = semana.filterNotNull().firstOrNull { it.data.dayOfMonth == 1 }
    val rotulo = marco?.data?.format(mesCurto)?.removeSuffix(".").orEmpty()

    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(CALHA)) {
            if (rotulo.isNotEmpty()) LegendaTexto(rotulo)
        }
        semana.forEach { dia ->
            if (dia == null) Spacer(Modifier.weight(1f).aspectRatio(1f))
            else Celula(dia, hoje, dia.data == diaAberto, Modifier.weight(1f), onDiaClick)
        }
    }
}

@Composable
private fun Celula(
    dia: DiaBoard,
    hoje: LocalDate,
    aberto: Boolean,
    modifier: Modifier,
    onDiaClick: (LocalDate) -> Unit,
) {
    val colors = SaldoTheme.colors
    val oculto = LocalPrivacy.current.oculto
    val forma = RoundedCornerShape(8.dp)
    val ehHoje = dia.data == hoje
    val mostraNumero = LocalDensity.current.fontScale < ESCALA_SEM_NUMERO

    val fundo = if (!dia.dentroDaJanela) colors.surface else colors.tomDoBoard(dia.nivel)
    val tinta = if (!dia.dentroDaJanela) colors.separator else colors.textoSobreBoard(dia.nivel)
    // O anel some se for da cor do próprio fundo, e o tom −3 É essa cor. Nesse caso ele
    // vira o fundo da tela, que contrasta com qualquer célula pintada.
    val anel = if (dia.nivel <= -3) colors.background else colors.boardNeg3

    Box(
        modifier
            .aspectRatio(1f)
            .then(
                when {
                    // O dia aberto ganha o tint: é a ligação visível entre a célula tocada
                    // e o painel que apareceu embaixo.
                    aberto -> Modifier.border(2.dp, colors.tint, RoundedCornerShape(11.dp))
                    ehHoje -> Modifier.border(2.dp, colors.label, RoundedCornerShape(11.dp))
                    else -> Modifier
                },
            )
            .padding(2.dp),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .clip(forma)
                .background(fundo)
                .then(if (dia.venceFatura) Modifier.border(2.dp, anel, forma) else Modifier)
                .clickable(enabled = dia.dentroDaJanela) { onDiaClick(dia.data) }
                .testTag(tagCelula(dia.data))
                .semantics { contentDescription = descricaoDe(dia, oculto) },
            contentAlignment = Alignment.Center,
        ) {
            if (mostraNumero && dia.dentroDaJanela) {
                LegendaTexto(
                    dia.data.dayOfMonth.toString(),
                    cor = tinta,
                    peso = if (ehHoje) FontWeight.Bold else FontWeight.Normal,
                )
            }
        }
    }
}

@Composable
private fun BoardVazio() {
    val colors = SaldoTheme.colors
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 24.dp).testTag(TAG_BOARD_VAZIO),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("nada lançado neste mês", style = SaldoTheme.type.row, color = colors.secondaryLabel)
        Text("toque em + para lançar o primeiro", style = SaldoTheme.type.footnote, color = colors.secondaryLabel)
    }
}

@Composable
private fun Legenda(unidadeCentavos: Long) {
    val colors = SaldoTheme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp)
            .testTag(TAG_BOARD_LEGENDA),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            LegendaTexto("saiu")
            listOf(-3, -2, -1, 0, 1, 2, 3).forEach { n ->
                Box(
                    Modifier
                        .size(12.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(colors.tomDoBoard(n)),
                )
            }
            LegendaTexto("entrou")
        }
        if (unidadeCentavos > 0) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                LegendaTexto("um dia típico =")
                MoneyText(
                    centavos = unidadeCentavos,
                    style = SaldoTheme.type.caption,
                    color = colors.secondaryLabel,
                    formato = FormatoMoney.COM_SIMBOLO,
                )
            }
        } else {
            LegendaTexto("ainda sem um dia típico")
        }
    }
}

/** O texto miúdo desta tela: cabeçalho de coluna, calha, número do dia e régua. */
@Composable
private fun LegendaTexto(
    texto: String,
    modifier: Modifier = Modifier,
    cor: Color = SaldoTheme.colors.secondaryLabel,
    peso: FontWeight = FontWeight.Normal,
) {
    Text(
        text = texto,
        modifier = modifier,
        style = SaldoTheme.type.caption,
        color = cor,
        fontWeight = peso,
        textAlign = TextAlign.Center,
    )
}
