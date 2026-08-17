package com.scholze.saldo.ui.totais

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.scholze.saldo.domain.Movimentacao
import com.scholze.saldo.domain.Natureza
import com.scholze.saldo.domain.Tag
import com.scholze.saldo.ui.components.HairlineDivider
import com.scholze.saldo.ui.components.InsetGroup
import com.scholze.saldo.ui.components.InsetRow
import com.scholze.saldo.ui.components.SaldoGlyph
import com.scholze.saldo.ui.components.SaldoIcon
import com.scholze.saldo.ui.privacy.FormatoMoney
import com.scholze.saldo.ui.privacy.MoneyText
import com.scholze.saldo.ui.theme.SaldoTheme
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

private val ptBr = Locale.forLanguageTag("pt-BR")
private val mesCurto = DateTimeFormatter.ofPattern("MMM", ptBr)
private val diaMes = DateTimeFormatter.ofPattern("d MMM", ptBr)

/**
 * "ago/26" — o mês abreviado em pt-BR sai com ponto ("ago."), que colidiria com a barra
 * do ano. Mesmo `removeSuffix(".")` que o resto do app usa, só que antes de concatenar.
 */
private fun YearMonth.rotuloCurto(): String =
    format(mesCurto).removeSuffix(".") + "/" + (year % 100).toString().padStart(2, '0')

@Composable
fun TotaisScreen(
    vm: TotaisViewModel,
    onVerTag: (Tag) -> Unit,
    onAbrirMovimentacao: (Movimentacao) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by vm.state.collectAsState()
    TotaisContent(state, vm::mesAnterior, vm::proximoMes, onVerTag = onVerTag, onAbrirMovimentacao = onAbrirMovimentacao, modifier = modifier)
}

@Composable
fun TotaisContent(
    state: TotaisUiState,
    onMesAnterior: () -> Unit,
    onProximoMes: () -> Unit,
    modifier: Modifier = Modifier,
    onVerTag: (Tag) -> Unit = {},
    onAbrirMovimentacao: (Movimentacao) -> Unit = {},
) {
    val colors = SaldoTheme.colors
    val t = state.totais

    Column(modifier.fillMaxSize().background(colors.background)) {
        Row(
            Modifier.fillMaxWidth().background(colors.navBar).padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SaldoGlyph(
                SaldoIcon.CHEVRON_LEFT, colors.tint, size = 18.dp, strokeWidth = 2.2.dp,
                modifier = Modifier.clickable(onClick = onMesAnterior),
            )
            Text(
                "totais · " + state.mesAtual.rotuloCurto(),
                Modifier.weight(1f),
                style = SaldoTheme.type.navTitle, color = colors.label, textAlign = TextAlign.Center,
            )
            SaldoGlyph(
                SaldoIcon.CHEVRON_RIGHT, colors.tint, size = 18.dp, strokeWidth = 2.2.dp,
                modifier = Modifier.clickable(onClick = onProximoMes),
            )
        }
        HairlineDivider()

        // `null` só enquanto o primeiro LedgerInput não chegou: tela vazia, sem spinner
        // (a mesma escolha do ledger — o primeiro frame do banco é praticamente imediato).
        if (t == null) {
            Box(Modifier.fillMaxSize())
            return@Column
        }

        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 32.dp)),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Column {
                Text("performance", style = SaldoTheme.type.footnote, color = colors.secondaryLabel)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        if (t.sobrouCentavos >= 0) "sobrou dinheiro" else "faltou dinheiro",
                        style = SaldoTheme.type.body, color = colors.label,
                    )
                    MoneyText(
                        centavos = t.sobrouCentavos,
                        style = SaldoTheme.type.body,
                        color = if (t.sobrouCentavos >= 0) colors.positive else colors.categoryVariable,
                        formato = FormatoMoney.ASSINADO, fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            InsetGroup {
                LinhaValor("entradas", t.entradasCentavos, colors.positive)
                HairlineDivider(startIndent = 16.dp)
                // `saidasPorNatureza` guarda magnitudes positivas; a linha mostra saída.
                LinhaValor("saídas diários", -(t.saidasPorNatureza[Natureza.DIARIO] ?: 0))
                HairlineDivider(startIndent = 16.dp)
                LinhaValor("saídas economia", -(t.saidasPorNatureza[Natureza.ECONOMIA] ?: 0))
                HairlineDivider(startIndent = 16.dp)
                LinhaValor("compras no cartão", -(t.saidasPorNatureza[Natureza.CARTAO] ?: 0))
                if (state.estimativaCentavos > 0) {
                    HairlineDivider(startIndent = 16.dp)
                    LinhaValor("estimativa restante", -state.estimativaCentavos)
                }
            }

            InsetGroup {
                LinhaValor("reserva acumulada", t.economiaBucketCentavos, colors.balance)
            }

            InsetGroup {
                val fatura = t.faturaAtual
                if (fatura == null) {
                    InsetRow(label = "fatura atual", value = "sem compras no ciclo")
                } else {
                    LinhaValor("fatura atual", fatura.totalCentavos)
                    HairlineDivider(startIndent = 16.dp)
                    InsetRow(
                        label = "fecha em",
                        value = t.fechamentoFaturaAtual.format(diaMes).removeSuffix("."),
                    )
                }
            }

            state.insights?.let { SegmentoMesInsights(it, onVerTag, onAbrirMovimentacao) }
        }
    }
}

@Composable
private fun LinhaValor(rotulo: String, centavos: Long, cor: Color? = null) {
    val colors = SaldoTheme.colors
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(rotulo, Modifier.weight(1f), style = SaldoTheme.type.body, color = colors.label)
        MoneyText(
            centavos = centavos,
            style = SaldoTheme.type.body,
            color = cor ?: colors.label,
            formato = FormatoMoney.ASSINADO,
        )
    }
}
