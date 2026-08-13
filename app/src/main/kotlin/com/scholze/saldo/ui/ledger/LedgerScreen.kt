package com.scholze.saldo.ui.ledger

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.scholze.saldo.domain.DiaRow
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
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.launch

private val DAY_COLUMN = 34.dp
private val SALDO_COLUMN = 118.dp
private val ptBr = Locale.forLanguageTag("pt-BR")
private val tituloMes = DateTimeFormatter.ofPattern("MMMM yyyy", ptBr)
private val mesCurto = DateTimeFormatter.ofPattern("MMM", ptBr)
private val diaCurto = DateTimeFormatter.ofPattern("d MMM", ptBr)

/** O hero, para os testes: há vários nós de dinheiro mascarados na tela. */
const val TAG_SALDO_PROJETADO = "ledger:saldoProjetado"

@Composable
fun LedgerScreen(
    state: LedgerUiState,
    onMesAnterior: () -> Unit,
    onProximoMes: () -> Unit,
    onFiltro: (FiltroLedger) -> Unit,
    onItemClick: (Movimentacao) -> Unit,
    onTogglePrivacidade: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val colors = SaldoTheme.colors
    val mes = state.mes
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // Índice do item de hoje na LazyColumn (3 headers antes dos dias). Num mês sem
    // movimentação alguma os dias nem viram itens — a pill não teria destino.
    val indiceHoje = remember(mes, state.hoje) {
        mes?.takeIf { m -> m.dias.any { it.itens.isNotEmpty() } }
            ?.dias?.indexOfFirst { it.data == state.hoje }?.takeIf { it >= 0 }?.plus(3)
    }
    val mostraPillHoje by remember(indiceHoje) {
        derivedStateOf {
            indiceHoje != null &&
                (listState.firstVisibleItemIndex > indiceHoje ||
                    listState.firstVisibleItemIndex + listState.layoutInfo.visibleItemsInfo.size <= indiceHoje)
        }
    }

    Box(
        modifier
            .fillMaxSize()
            .background(colors.background)
            .pointerInput(state.mesAtual) {
                var total = 0f
                detectHorizontalDragGestures(
                    onDragStart = { total = 0f },
                    onDragEnd = {
                        if (total > 120f) onMesAnterior() else if (total < -120f) onProximoMes()
                    },
                ) { _, dragAmount -> total += dragAmount }
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
                    item(key = "header") { ColumnHeader(); HairlineDivider() }

                    if (mes.dias.all { it.itens.isEmpty() }) {
                        item(key = "vazio") { EmptyMonth() }
                    } else {
                        itemsIndexed(mes.dias, key = { _, d -> d.data.toEpochDay() }) { _, dia ->
                            DayRow(
                                dia = dia,
                                faixa = mes.faixaSaldos(),
                                hoje = state.hoje,
                                onItemClick = onItemClick,
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
                    if (privacidade.oculto) SaldoIcon.MAIS else SaldoIcon.TOTAIS,
                    colors.tint, size = 18.dp,
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
        MoneyText(
            centavos = mes.saldoProjetadoCentavos,
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
private fun EmptyMonth() {
    val colors = SaldoTheme.colors
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("sem movimentações neste mês", style = SaldoTheme.type.row, color = colors.secondaryLabel)
        Text("toque em + para adicionar", style = SaldoTheme.type.footnote, color = colors.secondaryLabel)
    }
}

@Composable
private fun DayRow(
    dia: DiaRow,
    faixa: ClosedRange<Long>,
    hoje: LocalDate,
    onItemClick: (Movimentacao) -> Unit,
) {
    val colors = SaldoTheme.colors
    val ehHoje = dia.data == hoje
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (ehHoje) colors.segmentedTrack else colors.surface)
            .defaultMinSize(minHeight = 52.dp)
            .height(IntrinsicSize.Min),
    ) {
        Text(
            dia.data.dayOfMonth.toString().padStart(2, '0'),
            Modifier.width(DAY_COLUMN).padding(start = 16.dp, top = 10.dp),
            style = SaldoTheme.type.row.let { if (ehHoje) it.copy(fontWeight = FontWeight.SemiBold) else it },
            color = if (ehHoje) colors.tint else colors.secondaryLabel,
        )

        Column(Modifier.weight(1f).padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (dia.itens.isEmpty()) {
                Text("sem movimentações", style = SaldoTheme.type.row, color = colors.secondaryLabel)
            } else {
                dia.itens.forEach { item ->
                    val mov = (item as? ItemDia.Mov)?.mov
                    Row(
                        Modifier.clickable(enabled = mov != null) { mov?.let(onItemClick) },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        if (item.recorrente) {
                            SaldoGlyph(SaldoIcon.RECORRENTE, colors.secondaryLabel, size = 11.dp, strokeWidth = 1.3.dp)
                        } else {
                            Box(
                                Modifier.size(7.dp).background(
                                    when ((item as ItemDia.Mov).mov.natureza) {
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
                modifier = Modifier.padding(end = 16.dp),
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
