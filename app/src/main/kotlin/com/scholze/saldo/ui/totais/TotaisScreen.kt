package com.scholze.saldo.ui.totais

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.scholze.saldo.domain.Movimentacao
import com.scholze.saldo.domain.Ritmo
import com.scholze.saldo.domain.Natureza
import com.scholze.saldo.domain.Tag
import com.scholze.saldo.ui.components.FiltroChips
import com.scholze.saldo.ui.components.InsetGroup
import com.scholze.saldo.ui.components.InsetRow
import com.scholze.saldo.ui.components.SaldoTopBar
import com.scholze.saldo.ui.privacy.FormatoMoney
import com.scholze.saldo.ui.privacy.MoneyText
import com.scholze.saldo.ui.theme.SaldoTheme
import com.scholze.saldo.ui.totais.charts.RitmoChart
import java.time.YearMonth
import java.time.format.DateTimeFormatter

// `ptBr`, `mesCurto` e `rotuloCurto()` moraram aqui; agora vêm de Formatos.kt (mesmo pacote,
// sem import) — reutilizados por SegmentoTendencia.kt e, futuramente, pelas Tasks 6 e 7.
private val diaMes = DateTimeFormatter.ofPattern("d MMM", ptBr)

const val TAG_SEGMENTO_TOTAIS = "totais:segmento"

@Composable
fun TotaisScreen(
    vm: TotaisViewModel,
    onVerTag: (Tag) -> Unit,
    onAbrirMovimentacao: (Movimentacao) -> Unit,
    onIrParaDia: (YearMonth, Int) -> Unit,
    onAbrirRecorrencias: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by vm.state.collectAsState()
    val segmento by vm.segmento.collectAsState()
    TotaisContent(
        state, vm::mesAnterior, vm::proximoMes,
        onVerTag = onVerTag, onAbrirMovimentacao = onAbrirMovimentacao, onIrParaMes = vm::irPara,
        onIrParaDia = onIrParaDia, onAbrirRecorrencias = onAbrirRecorrencias,
        segmento = segmento, onSegmento = vm::selecionarSegmento,
        modifier = modifier,
    )
}

@Composable
fun TotaisContent(
    state: TotaisUiState,
    onMesAnterior: () -> Unit,
    onProximoMes: () -> Unit,
    modifier: Modifier = Modifier,
    onVerTag: (Tag) -> Unit = {},
    onAbrirMovimentacao: (Movimentacao) -> Unit = {},
    onIrParaMes: (YearMonth) -> Unit = {},
    onIrParaDia: (YearMonth, Int) -> Unit = { _, _ -> },
    onAbrirRecorrencias: () -> Unit = {},
    segmento: SegmentoTotais,
    onSegmento: (SegmentoTotais) -> Unit,
) {
    val colors = SaldoTheme.colors
    val t = state.totais

    Column(modifier.fillMaxSize().background(colors.background)) {
        SaldoTopBar(
            titulo = "totais · " + state.mesAtual.rotuloCurto(),
            onAnterior = onMesAnterior,
            onProximo = onProximoMes,
        )

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
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp))
                    .background(colors.primaryContainer)
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    if (t.sobrouCentavos >= 0) "sobrou dinheiro" else "faltou dinheiro",
                    style = SaldoTheme.type.footnote, color = colors.onPrimaryContainer.copy(alpha = 0.72f),
                )
                MoneyText(
                    centavos = t.sobrouCentavos,
                    style = SaldoTheme.type.navTitle,
                    color = colors.onPrimaryContainer,
                    formato = FormatoMoney.ASSINADO_COM_SIMBOLO,
                )
            }

            FiltroChips(
                opcoes = SegmentoTotais.entries.map { it.rotulo },
                selecionado = segmento.ordinal,
                onSelect = { onSegmento(SegmentoTotais.entries[it]) },
                modifier = Modifier.testTag(TAG_SEGMENTO_TOTAIS),
            )

            when (segmento) {
                SegmentoTotais.MES -> {
                    InsetGroup {
                        LinhaValor("entradas", t.entradasCentavos, colors.positive)
                        // `saidasPorNatureza` guarda magnitudes positivas; a linha mostra saída.
                        LinhaValor("saídas diários", -(t.saidasPorNatureza[Natureza.DIARIO] ?: 0))
                        LinhaValor("saídas economia", -(t.saidasPorNatureza[Natureza.ECONOMIA] ?: 0))
                        LinhaValor("compras no cartão", -(t.saidasPorNatureza[Natureza.CARTAO] ?: 0))
                        if (state.estimativaCentavos > 0) {
                            LinhaValor("estimativa restante", -state.estimativaCentavos)
                        }
                    }

                    InsetGroup {
                        LinhaValor("reserva acumulada", t.economiaBucketCentavos, colors.balance)
                    }

                    state.ritmo?.let { BlocoRitmo(it) }

                    InsetGroup {
                        val fatura = t.faturaAtual
                        if (fatura == null) {
                            InsetRow(label = "fatura atual", value = "sem compras no ciclo")
                        } else {
                            LinhaValor("fatura atual", fatura.totalCentavos)
                            InsetRow(
                                label = "fecha em",
                                value = t.fechamentoFaturaAtual.format(diaMes).removeSuffix("."),
                            )
                        }
                    }

                    state.insights?.let {
                        SegmentoMesInsights(it, onVerTag, onAbrirMovimentacao, noTempo = state.tagsNoTempo)
                    }
                }

                SegmentoTotais.TENDENCIA -> state.tendencia?.let { pontos ->
                    SegmentoTendencia(
                        pontos, state.mesAtual,
                        onMes = { mes ->
                            onIrParaMes(mes)
                            onSegmento(SegmentoTotais.MES)
                        },
                    )
                }

                SegmentoTotais.A_CAMINHO -> state.aCaminho?.let { a ->
                    SegmentoACaminho(a, state.recorrencias, state.mesAtual, onIrParaDia, onAbrirRecorrencias)
                }
            }
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

/**
 * "Estou indo rápido demais?" — o acumulado de saídas do mês contra o costume dos meses
 * anteriores na mesma altura do mês.
 *
 * O número que responde a pergunta é o desvio; o gráfico está ali para mostrar ONDE a
 * diferença apareceu. Sem mês anterior com que comparar, o bloco diz isso em vez de
 * desenhar uma referência inventada.
 */
/** Gastando mais do que o costume — com ou sem porcentagem para expressar quanto. */
private fun acimaDoCostume(ritmo: Ritmo): Boolean {
    if (!ritmo.temComparacao) return false
    val desvio = ritmo.desvioPercentual ?: return ritmo.gastoAteAgora > 0
    return desvio > 0
}

@Composable
private fun BlocoRitmo(ritmo: Ritmo) {
    val colors = SaldoTheme.colors
    if (ritmo.acumulado.size < 2) return

    InsetGroup {
        Column(
            Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "ritmo do mês",
                    Modifier.weight(1f),
                    style = SaldoTheme.type.body,
                    color = colors.label,
                )
                val desvio = ritmo.desvioPercentual
                Text(
                    text = when {
                        !ritmo.temComparacao -> "sem mês anterior"
                        // Há comparação, mas o costume neste ponto do mês era zero: não há
                        // porcentagem, e mesmo assim dá para dizer de que lado se está.
                        desvio == null -> if (ritmo.gastoAteAgora > 0) "acima do costume" else "no costume"
                        desvio > 0 -> "$desvio% acima do costume"
                        desvio < 0 -> "${-desvio}% abaixo do costume"
                        else -> "no costume"
                    },
                    style = SaldoTheme.type.footnote,
                    // Gastar mais que o costume não é erro, é informação: o tom forte fica
                    // para o lado que pesa, e o resto é secundário.
                    color = if (acimaDoCostume(ritmo)) colors.categoryVariable else colors.secondaryLabel,
                )
            }

            RitmoChart(ritmo.acumulado, ritmo.referencia)

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("saiu até agora", style = SaldoTheme.type.footnote, color = colors.secondaryLabel)
                MoneyText(
                    centavos = ritmo.gastoAteAgora,
                    modifier = Modifier.weight(1f).padding(start = 6.dp),
                    style = SaldoTheme.type.footnote,
                    color = colors.label,
                    fontWeight = FontWeight.SemiBold,
                )
                if (ritmo.referencia.isNotEmpty()) {
                    Text("costume", style = SaldoTheme.type.footnote, color = colors.secondaryLabel)
                    MoneyText(
                        centavos = ritmo.referenciaAteAgora,
                        modifier = Modifier.padding(start = 6.dp),
                        style = SaldoTheme.type.footnote,
                        color = colors.secondaryLabel,
                    )
                }
            }
        }
    }
}
