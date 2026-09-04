package com.scholze.saldo.ui.ledger

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChangeIgnoreConsumed
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.scholze.saldo.domain.DiaRow
import com.scholze.saldo.domain.Fatura
import com.scholze.saldo.domain.FiltroLedger
import com.scholze.saldo.domain.ItemDia
import com.scholze.saldo.domain.MesLedger
import com.scholze.saldo.domain.Movimentacao
import com.scholze.saldo.domain.Natureza
import com.scholze.saldo.ui.components.DiaBadge
import com.scholze.saldo.ui.components.FiltroChips
import com.scholze.saldo.ui.components.IconeRedondo
import com.scholze.saldo.ui.components.SaldoGlyph
import com.scholze.saldo.ui.components.SaldoIcon
import com.scholze.saldo.ui.components.SaldoPill
import com.scholze.saldo.ui.components.SaldoTopBar
import com.scholze.saldo.ui.privacy.FormatoMoney
import com.scholze.saldo.ui.privacy.LocalPrivacy
import com.scholze.saldo.ui.privacy.MoneyText
import com.scholze.saldo.ui.theme.SaldoTheme
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlinx.coroutines.launch

private val ptBr = Locale.forLanguageTag("pt-BR")
private val tituloMes = DateTimeFormatter.ofPattern("MMMM yyyy", ptBr)
private val diaCurto = DateTimeFormatter.ofPattern("d MMM", ptBr)
private val diaSemanaCurto = DateTimeFormatter.ofPattern("EEE", ptBr)

/** O hero, para os testes: há vários nós de dinheiro mascarados na tela. */
const val TAG_SALDO_PROJETADO = "ledger:saldoProjetado"

/** A coluna de saldo de um dia — mesma razão do hero: vários nós iguais na tela. */
fun tagSaldoDoDia(dia: Int): String = "ledger:saldoDia:$dia"

@Composable
fun LedgerScreen(
    state: LedgerUiState,
    onMesAnterior: () -> Unit,
    onProximoMes: () -> Unit,
    onFiltro: (FiltroLedger) -> Unit,
    onItemClick: (Movimentacao) -> Unit,
    onExcluir: (Movimentacao) -> Unit,
    onTogglePrivacidade: () -> Unit,
    onVerBoard: () -> Unit,
    onLimparTag: () -> Unit,
    alvo: AlvoLedger? = null,
    onAlvoConsumido: () -> Unit = {},
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val colors = SaldoTheme.colors
    val mes = state.mes
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    // Fatura tocada: abre a lista de compras (Step 1b). Estado de tela, não de ViewModel —
    // é só uma leitura, não muda dado nenhum.
    var faturaAberta by remember { mutableStateOf<Fatura?>(null) }

    // Índice do item de hoje na LazyColumn: 2 headers antes dos dias (hero, chips), 3
    // quando o chip de tag entra. O cabeçalho de colunas sumiu com a grade. Num mês sem
    // movimentação alguma os dias nem viram itens — a pill não teria destino.
    val cabecalhos = if (state.tagFiltro != null) 3 else 2
    val indiceHoje = remember(mes, state.hoje, cabecalhos) {
        mes?.takeIf { m -> m.dias.any { it.itens.isNotEmpty() } }
            ?.dias?.indexOfFirst { it.data == state.hoje }?.takeIf { it >= 0 }?.plus(cabecalhos)
    }
    val mostraPillHoje by remember(indiceHoje) {
        derivedStateOf {
            indiceHoje != null &&
                (listState.firstVisibleItemIndex > indiceHoje ||
                    listState.firstVisibleItemIndex + listState.layoutInfo.visibleItemsInfo.size <= indiceHoje)
        }
    }

    // Chegada por deep link (widget/lembrete) pedindo um dia: rola até ele assim que o mês
    // pedido está na tela — `mes` pode ainda ser o mês anterior por um quadro — e devolve o
    // alvo como consumido. Num mês sem movimentação os dias nem viram itens: só consome.
    LaunchedEffect(alvo, mes) {
        val a = alvo ?: return@LaunchedEffect
        val m = mes ?: return@LaunchedEffect
        if (m.mes != a.mes) return@LaunchedEffect
        if (m.dias.any { it.itens.isNotEmpty() }) listState.scrollToItem(cabecalhos + a.dia - 1)
        onAlvoConsumido()
    }

    Box(
        modifier
            .fillMaxSize()
            .background(colors.background)
            // Navegação de mês por arrasto, cedendo a vez aos filhos.
            //
            // A versão anterior era um `detectHorizontalDragGestures` aqui na raiz, e ele
            // COMPETE com o SwipeToDismissBox de cada linha: num arrasto real (não no
            // swipeLeft() sintético dos testes) a raiz costumava ganhar a corrida do touch
            // slop, engolir o gesto e trocar o mês em vez de excluir a linha.
            //
            // O loop abaixo roda no pass Main, que num nó pai chega DEPOIS dos filhos: se a
            // linha (ou a rolagem da lista) já consumiu movimento, `alheio` fecha a porta e
            // o mês não muda. Só o consumo de MOVIMENTO conta — `clickable` consome o down
            // para marcar o press, e isso não pode valer como "alguém pegou o gesto".
            //
            // Quando é a raiz que assume, ela consome: sem isso o `clickable` do hero, que
            // não tem slop nenhum, dispararia junto e alternaria a privacidade no fim do
            // arrasto.
            .pointerInput(state.mesAtual) {
                val limiar = 120.dp.toPx()
                val slop = viewConfiguration.touchSlop
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    var totalX = 0f
                    var totalY = 0f
                    var meu = false
                    var alheio = false
                    while (true) {
                        val evento = awaitPointerEvent()
                        val mudanca = evento.changes.firstOrNull() ?: break
                        val delta = mudanca.positionChangeIgnoreConsumed()
                        if (!meu && mudanca.isConsumed && delta != Offset.Zero) alheio = true
                        if (!alheio) {
                            totalX += delta.x
                            totalY += delta.y
                            // Predominantemente horizontal, senão uma rolagem na diagonal
                            // sobre a coluna de dias viraria troca de mês.
                            if (!meu && abs(totalX) > slop && abs(totalX) > abs(totalY)) meu = true
                            if (meu) mudanca.consume()
                        }
                        if (!mudanca.pressed) break
                    }
                    if (meu) {
                        if (totalX > limiar) onMesAnterior() else if (totalX < -limiar) onProximoMes()
                    }
                }
            },
    ) {
        Column(Modifier.fillMaxSize()) {
            SaldoTopBar(
                titulo = state.mesAtual.format(tituloMes),
                onAnterior = onMesAnterior,
                onProximo = onProximoMes,
                acoes = {
                    IconeRedondo(SaldoIcon.GRADE, "ver como grade", onVerBoard)
                    IconeRedondo(
                        if (LocalPrivacy.current.oculto) SaldoIcon.OLHO_RISCADO else SaldoIcon.OLHO,
                        "alternar privacidade",
                        onTogglePrivacidade,
                    )
                },
            )

            if (mes == null) {
                Box(Modifier.fillMaxSize())
            } else {
                LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = contentPadding) {
                    item(key = "hero") { BalanceHero(mes, onTogglePrivacidade) }
                    item(key = "filtro") {
                        FiltroChips(
                            opcoes = FiltroLedger.entries.map { it.rotulo },
                            selecionado = state.filtro.ordinal,
                            onSelect = { onFiltro(FiltroLedger.entries[it]) },
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 12.dp),
                        )
                    }
                    state.tagFiltro?.let { tag ->
                        item(key = "tagchip") {
                            Row(
                                Modifier
                                    .padding(start = 16.dp, bottom = 6.dp)
                                    .clip(RoundedCornerShape(percent = 50))
                                    .background(colors.secondaryContainer)
                                    .clickable { onLimparTag() }
                                    .padding(horizontal = 12.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text("tag: ${tag.nome}", style = SaldoTheme.type.footnote, color = colors.label)
                                Text("×", style = SaldoTheme.type.footnote, color = colors.secondaryLabel)
                            }
                        }
                    }
                    if (mes.dias.all { it.itens.isEmpty() }) {
                        item(key = "vazio") { EmptyMonth(comTagFiltro = state.tagFiltro != null) }
                    } else {
                        itemsIndexed(mes.dias, key = { _, d -> d.data.toEpochDay() }) { _, dia ->
                            DayRow(
                                dia = dia,
                                faixa = mes.faixaSaldos(),
                                hoje = state.hoje,
                                onItemClick = onItemClick,
                                onExcluir = onExcluir,
                                onFaturaClick = { faturaAberta = it },
                            )
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = mostraPillHoje,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 18.dp),
        ) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(15.dp))
                    .background(colors.tint)
                    .clickable { indiceHoje?.let { scope.launch { listState.animateScrollToItem(it) } } }
                    .padding(horizontal = 16.dp, vertical = 7.dp),
            ) {
                Text(
                    "hoje",
                    style = SaldoTheme.type.footnote.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }

        faturaAberta?.let { fatura ->
            AlertDialog(
                onDismissRequest = { faturaAberta = null },
                title = { Text("fatura · vence " + fatura.vencimento.format(diaCurto).removeSuffix(".")) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        fatura.compras.forEach { compra ->
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    compra.data.format(diaCurto).removeSuffix(".") + "  " + compra.descricao,
                                    Modifier.weight(1f), style = SaldoTheme.type.row,
                                )
                                MoneyText(centavos = compra.valorCentavos, style = SaldoTheme.type.row, formato = FormatoMoney.ASSINADO)
                            }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { faturaAberta = null }) { Text("ok") } },
            )
        }
    }
}

private fun MesLedger.faixaSaldos(): ClosedRange<Long> {
    if (dias.isEmpty()) return 0L..0L
    val saldos = dias.map { it.saldoCentavos }
    return saldos.min()..saldos.max()
}

/** O card do saldo projetado. `internal` porque o board mostra exatamente o mesmo. */
@Composable
internal fun BalanceHero(mes: MesLedger, onTogglePrivacidade: () -> Unit) {
    val colors = SaldoTheme.colors
    Column(
        Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(colors.primaryContainer)
            .clickable(onClick = onTogglePrivacidade)   // tocar no hero tambem alterna
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            "saldo projetado · " + mes.projetadoEm.format(diaCurto).removeSuffix("."),
            style = SaldoTheme.type.footnote, color = colors.onPrimaryContainer.copy(alpha = 0.72f),
        )
        // Contagem ate o valor novo em vez de troca seca - de mes para mes, e quando uma
        // movimentacao entra ou sai. Mascarado o numero nem aparece, entao a animacao
        // simplesmente nao se ve; o alvo continua sendo o valor real.
        val animado by animateFloatAsState(
            targetValue = mes.saldoProjetadoCentavos.toFloat(),
            animationSpec = tween(durationMillis = 450),
            label = "saldoCountUp",
        )
        MoneyText(
            centavos = animado.toLong(),
            modifier = Modifier.testTag(TAG_SALDO_PROJETADO),
            style = SaldoTheme.type.largeTitle, color = colors.onPrimaryContainer,
        )
        Row(
            Modifier
                .padding(top = 6.dp)
                .clip(RoundedCornerShape(percent = 50))
                .background(colors.onPrimaryContainer.copy(alpha = 0.10f))
                .padding(horizontal = 11.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MoneyText(
                centavos = mes.deltaNoMesCentavos,
                style = SaldoTheme.type.subhead,
                color = colors.onPrimaryContainer,
                formato = FormatoMoney.ASSINADO_COM_SIMBOLO,
            )
            Text("no mês", style = SaldoTheme.type.subhead, color = colors.onPrimaryContainer)
        }
        if (mes.estimativaCentavos > 0) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("inclui estimativa de", style = SaldoTheme.type.caption, color = colors.onPrimaryContainer.copy(alpha = 0.7f))
                MoneyText(
                    centavos = mes.estimativaCentavos,
                    style = SaldoTheme.type.caption, color = colors.onPrimaryContainer.copy(alpha = 0.7f),
                )
                Text("em diários", style = SaldoTheme.type.caption, color = colors.onPrimaryContainer.copy(alpha = 0.7f))
            }
        }
    }
}

@Composable
private fun EmptyMonth(comTagFiltro: Boolean = false) {
    val colors = SaldoTheme.colors
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // Sob filtro de tag o mês pode estar cheio: mandar "toque em +" seria mentira.
        Text(
            if (comTagFiltro) "nenhuma movimentação com essa tag" else "sem movimentações neste mês",
            style = SaldoTheme.type.row, color = colors.secondaryLabel,
        )
        Text(
            if (comTagFiltro) "toque no × acima para ver o mês inteiro" else "toque em + para adicionar",
            style = SaldoTheme.type.footnote, color = colors.secondaryLabel,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DayRow(
    dia: DiaRow,
    faixa: ClosedRange<Long>,
    hoje: LocalDate,
    onItemClick: (Movimentacao) -> Unit,
    onExcluir: (Movimentacao) -> Unit,
    onFaturaClick: (Fatura) -> Unit,
) {
    val colors = SaldoTheme.colors
    val ehHoje = dia.data == hoje
    val fundo = if (ehHoje) colors.secondaryContainer else colors.surface

    Row(
        Modifier
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(fundo)
            .then(if (ehHoje) Modifier.border(2.dp, colors.tint, RoundedCornerShape(20.dp)) else Modifier)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        DiaBadge(
            dia = dia.data.dayOfMonth,
            diaSemana = dia.data.format(diaSemanaCurto).removeSuffix("."),
            destacado = ehHoje,
        )

        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            if (dia.itens.isEmpty()) {
                Text("sem movimentações", style = SaldoTheme.type.row, color = colors.secondaryLabel)
            } else {
                dia.itens.forEach { item ->
                    // Exaustivo na interface selada: cada ramo sabe exatamente com que tipo
                    // de item esta lidando, sem cast nenhum (nem seguro nem inseguro).
                    when (item) {
                        is ItemDia.Mov -> key(item.mov.id, item.descricao) {
                            // A chave prende o `rememberSwipeToDismissBoxState` ao item, nao a
                            // posicao: sem ela, apagar o primeiro de dois itens do mesmo dia faria
                            // o segundo herdar o slot do primeiro - e aparecer arrastado para a
                            // esquerda, com o painel vermelho atras, enquanto a animacao volta.
                            val mov = item.mov
                            val dismissState = rememberSwipeToDismissBoxState()
                            // Reagir a TRANSICAO de currentValue, nao a um confirmValueChange.
                            // O anchoredDraggable chama aquele callback mais de uma vez no mesmo
                            // gesto: um swipe produzia DOIS snackbars, e o "desfazer" do segundo
                            // reinseria a linha de novo, agora duplicada.
                            LaunchedEffect(dismissState.currentValue) {
                                if (dismissState.currentValue == SwipeToDismissBoxValue.EndToStart) {
                                    onExcluir(mov)
                                    // A linha some porque o dado sumiu; se a exclusao for recusada
                                    // (ocorrencia virtual), o reset devolve a linha ao lugar.
                                    dismissState.reset()
                                }
                            }
                            SwipeToDismissBox(
                                state = dismissState,
                                enableDismissFromStartToEnd = false,
                                backgroundContent = {
                                    Box(
                                        Modifier
                                            .fillMaxSize()
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(colors.categoryVariable),
                                        contentAlignment = Alignment.CenterEnd,
                                    ) {
                                        Text(
                                            "excluir",
                                            Modifier.padding(end = 12.dp),
                                            style = SaldoTheme.type.footnote,
                                            // `categoryVariable` inverte de claridade entre os
                                            // esquemas igual ao tint: branco fixo dava 2,46:1 no
                                            // escuro. `inverseOnSurface` tem exatamente a
                                            // polaridade certa - tinta clara no tema claro,
                                            // escura no escuro - e da 5,4:1 nos dois.
                                            color = MaterialTheme.colorScheme.inverseOnSurface,
                                        )
                                    }
                                },
                            ) {
                                // Base opaca: o SwipeToDismissBox mantem o backgroundContent
                                // ("excluir", vermelho) sempre desenhado atras do conteudo, entao
                                // sem ela o vermelho vazaria atraves da linha mesmo parada.
                                Box(Modifier.background(fundo)) {
                                    LinhaMov(
                                        descricao = item.descricao,
                                        centavos = item.valorCentavos,
                                        recorrente = item.recorrente,
                                        natureza = mov.natureza,
                                        onClick = { onItemClick(mov) },
                                    )
                                }
                            }
                        }

                        // A fatura e um total calculado, nao uma movimentacao de verdade: toca
                        // para abrir a lista de compras, mas nao passa por onItemClick - nao ha
                        // editor para uma linha que nao existe no banco. FaturaDia.recorrente e
                        // sempre true, entao nunca cai no branch da bolinha colorida.
                        is ItemDia.FaturaDia -> LinhaMov(
                            descricao = item.descricao,
                            centavos = item.valorCentavos,
                            recorrente = true,
                            natureza = Natureza.CARTAO,
                            onClick = { onFaturaClick(item.fatura) },
                        )
                    }
                }
            }
        }

        SaldoPill(
            centavos = dia.saldoCentavos,
            nivel = nivelDeCalor(dia.saldoCentavos, faixa),
            modifier = Modifier.testTag(tagSaldoDoDia(dia.data.dayOfMonth)),
        )
    }
}

/** Uma movimentação dentro da linha do dia: marcador, descrição, valor. */
@Composable
private fun LinhaMov(
    descricao: String,
    centavos: Long,
    recorrente: Boolean,
    natureza: Natureza,
    onClick: () -> Unit,
) {
    val colors = SaldoTheme.colors
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (recorrente) {
            SaldoGlyph(SaldoIcon.RECORRENTE, colors.secondaryLabel, size = 11.dp, strokeWidth = 1.3.dp)
        } else {
            Box(
                Modifier.size(6.dp).background(
                    when (natureza) {
                        Natureza.ECONOMIA -> colors.categoryFixed
                        else -> colors.categoryVariable
                    },
                    CircleShape,
                ),
            )
        }
        Text(descricao, style = SaldoTheme.type.row, color = colors.label)
        MoneyText(
            centavos = centavos,
            modifier = Modifier.weight(1f),
            style = SaldoTheme.type.row, color = colors.secondaryLabel,
            formato = FormatoMoney.ASSINADO, textAlign = TextAlign.End,
        )
    }
}

/**
 * Which of the three heat buckets a day's balance falls in - 0, 1 or 2.
 *
 * The thresholds are the ones the heat-tinted column used; only what consumes them
 * changed (a pill background instead of a column fill), so a month that read as
 * "thin at the end" still does.
 */
private fun nivelDeCalor(saldo: Long, faixa: ClosedRange<Long>): Int {
    if (faixa.endInclusive <= faixa.start) return 1
    val ratio = (saldo - faixa.start).toDouble() / (faixa.endInclusive - faixa.start).toDouble()
    return when {
        ratio < 0.34 -> 0
        ratio < 0.67 -> 1
        else -> 2
    }
}
