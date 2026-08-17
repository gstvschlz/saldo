package com.scholze.saldo.ui.ledger

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.graphics.Color
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
import com.scholze.saldo.ui.components.HairlineDivider
import com.scholze.saldo.ui.components.SaldoGlyph
import com.scholze.saldo.ui.components.SaldoIcon
import com.scholze.saldo.ui.components.SegmentedControl
import com.scholze.saldo.ui.privacy.FormatoMoney
import com.scholze.saldo.ui.privacy.LocalPrivacy
import com.scholze.saldo.ui.privacy.MoneyText
import com.scholze.saldo.ui.theme.SaldoTheme
import com.scholze.saldo.ui.theme.tabular
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlinx.coroutines.launch

private val DAY_COLUMN = 34.dp
private val SALDO_COLUMN = 118.dp
private val ptBr = Locale.forLanguageTag("pt-BR")
private val tituloMes = DateTimeFormatter.ofPattern("MMMM yyyy", ptBr)
private val mesCurto = DateTimeFormatter.ofPattern("MMM", ptBr)
private val diaCurto = DateTimeFormatter.ofPattern("d MMM", ptBr)

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

    // Índice do item de hoje na LazyColumn: 3 headers antes dos dias, 4 quando o chip de
    // tag entra entre o segmented control e o cabeçalho de colunas. Num mês sem
    // movimentação alguma os dias nem viram itens — a pill não teria destino.
    val cabecalhos = if (state.tagFiltro != null) 4 else 3
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
            MonthNavBar(state, onMesAnterior, onProximoMes, onTogglePrivacidade)

            if (mes == null) {
                Box(Modifier.fillMaxSize())
            } else {
                LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = contentPadding) {
                    item(key = "hero") { BalanceHero(mes, onTogglePrivacidade) }
                    item(key = "filtro") {
                        Box(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 6.dp)) {
                            SegmentedControl(
                                options = FiltroLedger.entries.map { it.rotulo },
                                selectedIndex = state.filtro.ordinal,
                                onSelect = { onFiltro(FiltroLedger.entries[it]) },
                            )
                        }
                    }
                    state.tagFiltro?.let { tag ->
                        item(key = "tagchip") {
                            Row(
                                Modifier
                                    .padding(start = 16.dp, bottom = 6.dp)
                                    .clip(RoundedCornerShape(13.dp))
                                    .background(colors.segmentedTrack)
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
                    item(key = "header") { ColumnHeader(); HairlineDivider() }

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
                            HairlineDivider()
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
                Text("hoje", style = SaldoTheme.type.footnote.copy(fontWeight = FontWeight.SemiBold), color = Color.White)
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

@Composable
private fun MonthNavBar(
    state: LedgerUiState,
    onMesAnterior: () -> Unit,
    onProximoMes: () -> Unit,
    onTogglePrivacidade: () -> Unit,
) {
    val colors = SaldoTheme.colors
    val privacidade = LocalPrivacy.current
    Row(
        Modifier
            .fillMaxWidth()
            .background(colors.navBar)
            .padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier.clickable(onClick = onMesAnterior),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SaldoGlyph(SaldoIcon.CHEVRON_LEFT, colors.tint, size = 18.dp, strokeWidth = 2.2.dp)
            Text(
                state.mesAtual.minusMonths(1).format(mesCurto).removeSuffix("."),
                style = SaldoTheme.type.body, color = colors.tint,
            )
        }
        Text(
            state.mesAtual.format(tituloMes),
            Modifier.weight(1f),
            style = SaldoTheme.type.navTitle, color = colors.label, textAlign = TextAlign.Center,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Olho da privacidade: alternar máscara em todas as telas.
            Box(
                Modifier
                    .clip(CircleShape)
                    .clickable(onClick = onTogglePrivacidade)
                    .padding(6.dp),
            ) {
                SaldoGlyph(
                    if (privacidade.oculto) SaldoIcon.OLHO_RISCADO else SaldoIcon.OLHO,
                    colors.tint, size = 20.dp,
                )
            }
            Row(
                Modifier.clickable(onClick = onProximoMes),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    state.mesAtual.plusMonths(1).format(mesCurto).removeSuffix("."),
                    style = SaldoTheme.type.body, color = colors.tint,
                )
                SaldoGlyph(SaldoIcon.CHEVRON_RIGHT, colors.tint, size = 18.dp, strokeWidth = 2.2.dp)
            }
        }
    }
    HairlineDivider()
}

@Composable
private fun BalanceHero(mes: MesLedger, onTogglePrivacidade: () -> Unit) {
    val colors = SaldoTheme.colors
    Column(
        Modifier
            .clickable(onClick = onTogglePrivacidade)   // tocar no hero também alterna
            .padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 14.dp)
            .fillMaxWidth(),
    ) {
        Text(
            "saldo projetado · " + mes.projetadoEm.format(diaCurto).removeSuffix("."),
            style = SaldoTheme.type.footnote, color = colors.secondaryLabel,
        )
        // Contagem até o valor novo em vez de troca seca — de mês para mês, e quando uma
        // movimentação entra ou sai. Mascarado o número nem aparece, então a animação
        // simplesmente não se vê; o alvo continua sendo o valor real.
        val animado by animateFloatAsState(
            targetValue = mes.saldoProjetadoCentavos.toFloat(),
            animationSpec = tween(durationMillis = 450),
            label = "saldoCountUp",
        )
        MoneyText(
            centavos = animado.toLong(),
            modifier = Modifier.padding(top = 2.dp).testTag(TAG_SALDO_PROJETADO),
            style = SaldoTheme.type.largeTitle, color = colors.label,
        )
        Row(
            Modifier.padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MoneyText(
                centavos = mes.deltaNoMesCentavos,
                style = SaldoTheme.type.subhead,
                color = if (mes.deltaNoMesCentavos >= 0) colors.positive else colors.categoryVariable,
                formato = FormatoMoney.ASSINADO_COM_SIMBOLO,
            )
            Text("no mês", style = SaldoTheme.type.subhead, color = colors.secondaryLabel)
        }
        if (mes.estimativaCentavos > 0) {
            Row(Modifier.padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("inclui estimativa de", style = SaldoTheme.type.footnote, color = colors.secondaryLabel)
                MoneyText(
                    centavos = mes.estimativaCentavos,
                    style = SaldoTheme.type.footnote, color = colors.secondaryLabel,
                )
                Text("em diários", style = SaldoTheme.type.footnote, color = colors.secondaryLabel)
            }
        }
    }
}

@Composable
private fun ColumnHeader() {
    val colors = SaldoTheme.colors
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 6.dp)) {
        Text("DIA", Modifier.width(DAY_COLUMN), style = SaldoTheme.type.sectionHeader, color = colors.secondaryLabel)
        Text("MOVIMENTAÇÕES", Modifier.weight(1f), style = SaldoTheme.type.sectionHeader, color = colors.secondaryLabel)
        Text("SALDO", style = SaldoTheme.type.sectionHeader, color = colors.secondaryLabel, textAlign = TextAlign.End)
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
    // `surface` opaca primeiro, `segmentedTrack` (translúcida) por cima — a mesma pilha que
    // cada linha de movimentação pinta lá dentro. Compor a tinta sobre bases diferentes
    // (aqui sobre `background`, lá sobre `surface`) deixaria a linha de hoje com um bloco
    // mais claro atrás de cada item do que nas colunas de dia e de saldo que a ladeiam.
    Row(
        Modifier
            .fillMaxWidth()
            .background(colors.surface)
            .then(if (ehHoje) Modifier.background(colors.segmentedTrack) else Modifier)
            .defaultMinSize(minHeight = 52.dp)
            .height(IntrinsicSize.Min),
    ) {
        Text(
            dia.data.dayOfMonth.toString().padStart(2, '0'),
            Modifier.width(DAY_COLUMN).padding(start = 16.dp, top = 10.dp),
            // Tabular: a coluna de dias é uma coluna de números e tem de alinhar.
            style = SaldoTheme.type.row.tabular.let { if (ehHoje) it.copy(fontWeight = FontWeight.SemiBold) else it },
            color = if (ehHoje) colors.tint else colors.secondaryLabel,
        )

        Column(Modifier.weight(1f).padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (dia.itens.isEmpty()) {
                Text("sem movimentações", style = SaldoTheme.type.row, color = colors.secondaryLabel)
            } else {
                dia.itens.forEach { item ->
                    // Exaustivo na interface selada: cada ramo sabe exatamente com que tipo
                    // de item está lidando, sem cast nenhum (nem seguro nem inseguro).
                    when (item) {
                        is ItemDia.Mov -> key(item.mov.id, item.descricao) {
                            // A chave prende o `rememberSwipeToDismissBoxState` ao item, não à
                            // posição: sem ela, apagar o primeiro de dois itens do mesmo dia faria
                            // o segundo herdar o slot do primeiro — e aparecer arrastado para a
                            // esquerda, com o painel vermelho atrás, enquanto a animação volta.
                            val mov = item.mov
                            val dismissState = rememberSwipeToDismissBoxState()
                            // Reagir à TRANSIÇÃO de currentValue, não a um confirmValueChange.
                            // O anchoredDraggable chama aquele callback mais de uma vez no mesmo
                            // gesto: um swipe produzia DOIS snackbars, e o "desfazer" do segundo
                            // reinseria a linha de novo, agora duplicada. Um LaunchedEffect com
                            // chave no valor dispara uma vez por transição — e ainda deixa de
                            // usar uma API que a material3 já marcou como deprecada.
                            LaunchedEffect(dismissState.currentValue) {
                                if (dismissState.currentValue == SwipeToDismissBoxValue.EndToStart) {
                                    onExcluir(mov)
                                    // A linha some porque o dado sumiu; se a exclusão for recusada
                                    // (ocorrência virtual), o reset devolve a linha ao lugar.
                                    dismissState.reset()
                                }
                            }
                            SwipeToDismissBox(
                                state = dismissState,
                                enableDismissFromStartToEnd = false,
                                backgroundContent = {
                                    Box(
                                        Modifier.fillMaxSize().background(colors.categoryVariable),
                                        contentAlignment = Alignment.CenterEnd,
                                    ) {
                                        Text(
                                            "excluir",
                                            Modifier.padding(end = 16.dp),
                                            style = SaldoTheme.type.footnote.copy(fontWeight = FontWeight.SemiBold),
                                            color = Color.White,
                                        )
                                    }
                                },
                            ) {
                                // `segmentedTrack` é translúcida (é uma tinta, não uma cor
                                // sólida) — pintar `surface` opaca por baixo primeiro é
                                // obrigatório aqui: o SwipeToDismissBox mantém o
                                // `backgroundContent` ("excluir", vermelho) sempre desenhado
                                // atrás do conteúdo em primeiro plano, então sem a base opaca
                                // o vermelho vazaria através da linha de hoje mesmo parada.
                                Box(
                                    Modifier
                                        .background(colors.surface)
                                        .then(if (ehHoje) Modifier.background(colors.segmentedTrack) else Modifier),
                                ) {
                                    Row(
                                        Modifier.clickable { onItemClick(mov) },
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                                    ) {
                                        if (item.recorrente) {
                                            SaldoGlyph(SaldoIcon.RECORRENTE, colors.secondaryLabel, size = 11.dp, strokeWidth = 1.3.dp)
                                        } else {
                                            Box(
                                                Modifier.size(7.dp).background(
                                                    when (mov.natureza) {
                                                        Natureza.ECONOMIA -> colors.categoryFixed
                                                        else -> colors.categoryVariable
                                                    },
                                                    CircleShape,
                                                ),
                                            )
                                        }
                                        Text(item.descricao, style = SaldoTheme.type.row, color = colors.label)
                                        MoneyText(
                                            centavos = item.valorCentavos,
                                            modifier = Modifier.weight(1f),
                                            style = SaldoTheme.type.row, color = colors.label,
                                            formato = FormatoMoney.ASSINADO, textAlign = TextAlign.End,
                                        )
                                    }
                                }
                            }
                        }

                        is ItemDia.FaturaDia -> {
                            // A fatura é um total calculado, não uma movimentação de verdade:
                            // toca para abrir a lista de compras (Step 1b), mas não passa por
                            // onItemClick — não há editor para uma linha que não existe no banco.
                            Row(
                                Modifier.clickable { onFaturaClick(item.fatura) },
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(7.dp),
                            ) {
                                // FaturaDia.recorrente é sempre true, então este ramo nunca cai
                                // no branch da bolinha colorida — só o glifo de recorrência.
                                SaldoGlyph(SaldoIcon.RECORRENTE, colors.secondaryLabel, size = 11.dp, strokeWidth = 1.3.dp)
                                Text(item.descricao, style = SaldoTheme.type.row, color = colors.label)
                                MoneyText(
                                    centavos = item.valorCentavos,
                                    modifier = Modifier.weight(1f),
                                    style = SaldoTheme.type.row, color = colors.label,
                                    formato = FormatoMoney.ASSINADO, textAlign = TextAlign.End,
                                )
                            }
                        }
                    }
                }
            }
        }

        Box(
            Modifier
                .padding(start = 14.dp)
                .width(SALDO_COLUMN)
                .fillMaxHeight()
                .background(heatTint(dia.saldoCentavos, faixa)),
            contentAlignment = Alignment.CenterEnd,
        ) {
            MoneyText(
                centavos = dia.saldoCentavos,
                modifier = Modifier.padding(end = 16.dp).testTag(tagSaldoDoDia(dia.data.dayOfMonth)),
                style = SaldoTheme.type.row, color = colors.balance,
                formato = FormatoMoney.VALOR, fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun heatTint(saldo: Long, faixa: ClosedRange<Long>): Color {
    val colors = SaldoTheme.colors
    if (faixa.endInclusive <= faixa.start) return colors.balanceTint2
    val ratio = (saldo - faixa.start).toDouble() / (faixa.endInclusive - faixa.start).toDouble()
    return when {
        ratio < 0.34 -> colors.balanceTint1
        ratio < 0.67 -> colors.balanceTint2
        else -> colors.balanceTint3
    }
}
