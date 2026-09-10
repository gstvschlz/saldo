package com.scholze.saldo.ui.board

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import com.scholze.saldo.domain.Tag
import com.scholze.saldo.ui.components.BuscaTopBar
import com.scholze.saldo.ui.components.Carregando
import com.scholze.saldo.ui.components.ErroDeLeitura
import com.scholze.saldo.ui.components.IconeRedondo
import com.scholze.saldo.ui.components.SaldoIcon
import com.scholze.saldo.ui.components.SaldoTopBar
import com.scholze.saldo.ui.components.Semana
import com.scholze.saldo.ui.components.arrastoDeMes
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

/** O chip "tag: mercado ×" que aparece quando a grade está filtrada por uma etiqueta. */
const val TAG_CHIP_TAG = "board:chipTag"

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

/**
 * Era `s t q q s s d`: quatro letras repetidas em sete colunas, que não distinguem terça de
 * quinta nem sábado de segunda. Agora vem de [Semana], a mesma lista que o `WeekdayBars` de
 * totais usa — as duas telas escreviam a própria.
 */
private val DIAS_SEMANA = Semana.CURTOS

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
    onItemClick: (Movimentacao) -> Unit,
    onExcluir: (Movimentacao) -> Unit,
    onTogglePrivacidade: () -> Unit,
    onVerGuardado: () -> Unit,
    /** Tira o filtro de etiqueta — o `×` do chip. */
    onLimparTag: () -> Unit = {},
    /** Um toque num chip da fileira do aviso "sem tag". */
    onEtiquetar: (Movimentacao, Tag) -> Unit = { _, _ -> },
    /** O `+` da fileira: abre a sheet, onde escolher várias etiquetas já existe. */
    onMaisEtiquetas: (Movimentacao) -> Unit = {},
    onTentar: () -> Unit = {},
    /** A meta de guardar, em %; `0` = sem meta. */
    metaGuardarPercent: Int = 0,
    /** O texto da busca; `null` = busca fechada, "" = aberta e ainda sem nada digitado. */
    busca: String? = null,
    resultados: List<Movimentacao>? = null,
    onAbrirBusca: () -> Unit = {},
    onFecharBusca: () -> Unit = {},
    onBusca: (String) -> Unit = {},
    onAbrirResultado: (Movimentacao) -> Unit = onItemClick,
    modifier: Modifier = Modifier,
) {
    val colors = SaldoTheme.colors
    val board = state.board
    val semanas = remember(board) { board?.let { semanasDe(it.dias) }.orEmpty() }
    // O diálogo da fatura é só leitura — não edita nada — então fechar na rotação é aceitável.
    var faturaAberta by remember { mutableStateOf<Fatura?>(null) }

    // O aviso de etiquetar é um interruptor por DIA: trocar de dia aberto o fecha, senão o dia
    // seguinte já chegaria com as fileiras abertas por uma decisão tomada sobre outro dia.
    var etiquetando by remember(state.diaAberto) { mutableStateOf(false) }

    Box(
        modifier
            .fillMaxSize()
            .background(colors.background)
            // O arrasto de mês desliga com a busca aberta: um gesto horizontal sobre os
            // resultados não tem "mês" para trocar, e brigaria com o scroll da lista.
            .then(
                if (busca == null) Modifier.arrastoDeMes(state.mesAtual, onMesAnterior, onProximoMes)
                else Modifier,
            ),
    ) {
        Column(Modifier.fillMaxSize()) {
            SaldoTopBar(
                titulo = state.mesAtual.format(tituloMes),
                onAnterior = onMesAnterior,
                onProximo = onProximoMes,
                podeAvancar = state.podeAvancar,
                busca = busca?.let { BuscaTopBar(it, onBusca, onFecharBusca) },
                acoes = {
                    // A lupa ocupa o lugar que era do `≡`. A vista de lista saiu inteira a
                    // pedido do usuário, e a busca — que só existia lá dentro — teria saído
                    // junto: procurar um lançamento de três meses atrás é a única coisa que
                    // a grade não sabe fazer sozinha.
                    IconeRedondo(SaldoIcon.LUPA, "buscar", onAbrirBusca)
                    // A descrição diz o que o toque FAZ, não o que o botão é: "alternar
                    // privacidade" era a mesma frase ligado e desligado, então o TalkBack nunca
                    // dizia em que estado a tela estava.
                    IconeRedondo(
                        if (LocalPrivacy.current.oculto) SaldoIcon.OLHO_RISCADO else SaldoIcon.OLHO,
                        if (LocalPrivacy.current.oculto) "mostrar valores" else "ocultar valores",
                        onTogglePrivacidade,
                    )
                },
            )

            // Busca com texto: os resultados tomam o lugar do mês, hero e grade inclusive.
            if (busca != null && busca.isNotBlank()) {
                ResultadosBusca(
                    consulta = busca,
                    resultados = resultados.orEmpty(),
                    hoje = state.hoje,
                    onItemClick = onAbrirResultado,
                    onExcluir = onExcluir,
                    contentPadding = PaddingValues(bottom = 24.dp),
                )
                return@Column
            }

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

            BalanceHero(mes, onTogglePrivacidade, onVerGuardado, metaGuardarPercent, state.teto)
            state.tagFiltro?.let { tag -> ChipDaTag(tag, onLimparTag) }
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
                        lembrete = LembreteDeTags(
                            aberto = etiquetando,
                            etiquetas = state.tagsSugeridas,
                            onAlternar = { etiquetando = !etiquetando },
                            onEtiquetar = onEtiquetar,
                            onMais = onMaisEtiquetas,
                        ),
                    )
                }
                // Mês sem uma movimentação sequer: a régua explicaria a cor de células que
                // não têm cor. No lugar dela, o convite — some com o primeiro lançamento.
                if (mes.dias.all { it.itens.isEmpty() }) BoardVazio(state.tagFiltro != null)
                else Legenda(board.unidadeCentavos)
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

/**
 * A grade sem nada para mostrar.
 *
 * Sob uma etiqueta a frase é OUTRA: o mês pode estar cheiíssimo e ainda assim nenhuma linha
 * carregar aquela etiqueta, e aí "toque em + para lançar o primeiro" é conselho errado — o que
 * falta não é lançamento, é o × que devolve o mês inteiro.
 */
@Composable
private fun BoardVazio(comTagFiltro: Boolean) {
    val colors = SaldoTheme.colors
    val (titulo, ajuda) =
        if (comTagFiltro) "nenhuma movimentação com essa tag" to "toque no × acima para ver o mês inteiro"
        else "nada lançado neste mês" to "toque em + para lançar o primeiro"
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 24.dp).testTag(TAG_BOARD_VAZIO),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(titulo, style = SaldoTheme.type.row, color = colors.secondaryLabel)
        Text(ajuda, style = SaldoTheme.type.footnote, color = colors.secondaryLabel)
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

/** A etiqueta que a grade está mostrando, e o `×` que devolve o mês inteiro. */
@Composable
private fun ChipDaTag(tag: Tag, onLimpar: () -> Unit) {
    val colors = SaldoTheme.colors
    Row(
        Modifier
            .padding(start = 16.dp, top = 10.dp)
            .clip(RoundedCornerShape(percent = 50))
            .background(colors.secondaryContainer)
            .clickable(onClick = onLimpar)
            .testTag(TAG_CHIP_TAG)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("tag: ${tag.nome}", style = SaldoTheme.type.footnote, color = colors.label)
        Text("\u00d7", style = SaldoTheme.type.footnote, color = colors.secondaryLabel)
    }
}
