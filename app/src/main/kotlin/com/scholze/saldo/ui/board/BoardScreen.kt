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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
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
import com.scholze.saldo.ui.components.IconeRedondo
import com.scholze.saldo.ui.components.SaldoIcon
import com.scholze.saldo.ui.components.SaldoTopBar
import com.scholze.saldo.ui.ledger.BalanceHero
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
private val diaLongo = DateTimeFormatter.ofPattern("d 'de' MMMM", ptBr)

/** A grade inteira, para os testes acharem de uma vez. */
const val TAG_BOARD_GRADE = "board:grade"

/** A régua no rodapé — os testes leem o "dia típico" por aqui. */
const val TAG_BOARD_LEGENDA = "board:legenda"

/** Uma célula. Há 365 delas na tela; o dia é o que as distingue. */
fun tagCelula(data: LocalDate): String = "board:celula:${data.toEpochDay()}"

/** A calha do mês, à esquerda da grade. */
private val CALHA = 22.dp

/** A partir desta escala de fonte o número do dia não cabe na célula e some. */
private const val ESCALA_SEM_NUMERO = 1.3f

private val DIAS_SEMANA = listOf("s", "t", "q", "q", "s", "s", "d")

/** Qual das duas vistas da aba `saldos` está no ar. O app sempre abre em [BOARD]. */
enum class VistaSaldos { BOARD, LISTA }

/**
 * O board: os últimos 12 meses em sete colunas de dia da semana, semanas empilhadas,
 * rolando na vertical.
 *
 * Em pé e não deitado como o do GitHub por uma razão prática: a raiz da aba `saldos` já
 * gasta o arrasto horizontal trocando de mês, e uma grade que rolasse para o lado
 * brigaria com esse gesto todo dia. Em pé ela também cabe num telefone sem espremer a
 * célula abaixo do que se lê.
 */
@Composable
fun BoardScreen(
    state: BoardUiState,
    onDiaClick: (LocalDate) -> Unit,
    onVerLista: () -> Unit,
    onTogglePrivacidade: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SaldoTheme.colors
    val board = state.board
    val listState = rememberLazyListState()
    val semanas = remember(board) { board?.let { semanasDe(it.dias) }.orEmpty() }

    // A grade abre no fim: hoje é a última linha, e é ela que interessa ao abrir o app.
    LaunchedEffect(semanas.size) {
        if (semanas.isNotEmpty()) listState.scrollToItem(semanas.size - 1)
    }

    Column(modifier.fillMaxSize().background(colors.background)) {
        SaldoTopBar(
            titulo = "seus dias",
            onAnterior = {},
            onProximo = {},
            mostrarSetas = false,
            acoes = {
                IconeRedondo(SaldoIcon.SALDOS, "ver como lista", onVerLista)
                IconeRedondo(
                    if (LocalPrivacy.current.oculto) SaldoIcon.OLHO_RISCADO else SaldoIcon.OLHO,
                    "alternar privacidade",
                    onTogglePrivacidade,
                )
            },
        )

        if (board == null || state.mes == null) {
            Box(Modifier.fillMaxSize())
            return@Column
        }

        // Só a grade rola. O hero, o cabeçalho de colunas e a régua ficam parados: a
        // grade abre no fim, e num LazyColumn único isso empurraria os três para fora da
        // tela — o saldo projetado sumia justamente na abertura do app.
        BalanceHero(state.mes, onTogglePrivacidade)
        CabecalhoColunas()
        LazyColumn(Modifier.weight(1f).testTag(TAG_BOARD_GRADE), state = listState) {
            itemsIndexed(
                semanas,
                key = { _, semana -> semana.filterNotNull().first().data.toEpochDay() },
            ) { i, semana ->
                LinhaSemana(semana, state.hoje, primeiraLinha = i == 0, onDiaClick = onDiaClick)
            }
        }
        Legenda(board.unidadeCentavos)
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
    primeiraLinha: Boolean,
    onDiaClick: (LocalDate) -> Unit,
) {
    // O rótulo do mês aparece na semana que contém um dia 1 — e na primeira linha da
    // grade, que quase nunca contém um e ficaria sem nome nenhum.
    val marco = semana.filterNotNull().firstOrNull { it.data.dayOfMonth == 1 }
        ?: semana.filterNotNull().firstOrNull().takeIf { primeiraLinha }
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
            else Celula(dia, hoje, Modifier.weight(1f), onDiaClick)
        }
    }
}

@Composable
private fun Celula(dia: DiaBoard, hoje: LocalDate, modifier: Modifier, onDiaClick: (LocalDate) -> Unit) {
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
            .then(if (ehHoje) Modifier.border(2.dp, colors.label, RoundedCornerShape(11.dp)) else Modifier)
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
