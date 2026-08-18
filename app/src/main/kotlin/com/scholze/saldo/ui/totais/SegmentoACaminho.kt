package com.scholze.saldo.ui.totais

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.scholze.saldo.domain.ACaminho
import com.scholze.saldo.domain.ItemDia
import com.scholze.saldo.domain.ItemFuturo
import com.scholze.saldo.domain.ResumoRecorrencias
import com.scholze.saldo.ui.components.InsetGroup
import com.scholze.saldo.ui.components.InsetRow
import com.scholze.saldo.ui.components.SaldoGlyph
import com.scholze.saldo.ui.components.SaldoIcon
import com.scholze.saldo.ui.privacy.FormatoMoney
import com.scholze.saldo.ui.privacy.MoneyText
import com.scholze.saldo.ui.theme.SaldoTheme
import java.time.YearMonth
import java.time.format.DateTimeFormatter

private val diaMesCurto = DateTimeFormatter.ofPattern("d MMM", ptBr)

/** "1 fixa" / "3 fixas" — a contagem aparece aqui e na tela de recorrências. */
internal fun fixas(n: Int): String = if (n == 1) "1 fixa" else "$n fixas"

/**
 * Segmento "a caminho": o que ainda passa pela coluna de saldo depois de hoje até o fim do mês
 * visto, e o atalho para as recorrências. Tocar numa linha abre o dia dela no ledger.
 *
 * O atalho fica FORA do card da lista de propósito: ele vale também num mês encerrado, onde não
 * existe nada a caminho para listar.
 *
 * Sem cabeçalho de seção: o chip selecionado logo acima já diz "a caminho", e repetir a palavra
 * a poucos dp de distância seria ruído — além de deixar dois nós com o mesmo texto na árvore,
 * o que faria `onNodeWithText("a caminho")` (que é como o teste troca de segmento) falhar.
 */
@Composable
fun SegmentoACaminho(
    a: ACaminho,
    resumo: ResumoRecorrencias?,
    mes: YearMonth,
    onIrParaDia: (YearMonth, Int) -> Unit,
    onAbrirRecorrencias: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SaldoTheme.colors

    Column(modifier, verticalArrangement = Arrangement.spacedBy(18.dp)) {
        if (a.mesEncerrado) {
            InsetGroup {
                InsetRow(label = "mês encerrado", value = "nada a caminho")
            }
        } else {
            // Card tonal, como o herói do mês e o do ledger: o total que ainda sai é a
            // leitura principal do segmento, não mais uma linha de lista.
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp))
                    .background(colors.primaryContainer)
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                // Rótulo, valor e prazo em linhas separadas: com o valor revelado, a frase
                // "ainda saem R$ … até 31 jul" numa linha só estoura a largura numa tela pequena.
                Text(
                    "ainda saem",
                    style = SaldoTheme.type.footnote,
                    color = colors.onPrimaryContainer.copy(alpha = 0.72f),
                )
                MoneyText(
                    centavos = a.saemCentavos,
                    style = SaldoTheme.type.navTitle,
                    color = colors.onPrimaryContainer,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "até " + mes.atEndOfMonth().format(diaMesCurto).removeSuffix("."),
                        style = SaldoTheme.type.caption,
                        color = colors.onPrimaryContainer.copy(alpha = 0.72f),
                    )
                    if (a.entramCentavos > 0) {
                        Text(
                            " · entram ",
                            style = SaldoTheme.type.caption,
                            color = colors.onPrimaryContainer.copy(alpha = 0.72f),
                        )
                        MoneyText(
                            centavos = a.entramCentavos,
                            style = SaldoTheme.type.caption,
                            color = colors.onPrimaryContainer,
                        )
                    }
                }
            }

            InsetGroup {
                if (a.itens.isEmpty()) {
                    InsetRow(label = "nada agendado até o fim do mês")
                } else {
                    a.itens.forEach { f ->
                        LinhaFuturo(f) { onIrParaDia(mes, f.data.dayOfMonth) }
                    }
                }
            }
        }

        if (resumo != null) {
            InsetGroup {
                InsetRow(
                    label = "recorrências",
                    onClick = onAbrirRecorrencias,
                    trailing = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                fixas(resumo.ativas.size) + " ·",
                                style = SaldoTheme.type.footnote,
                                color = colors.secondaryLabel,
                            )
                            MoneyText(
                                centavos = resumo.saemMes,
                                style = SaldoTheme.type.footnote,
                                color = colors.secondaryLabel,
                            )
                            Text("/mês", style = SaldoTheme.type.footnote, color = colors.secondaryLabel)
                            SaldoGlyph(SaldoIcon.CHEVRON_RIGHT, colors.secondaryLabel, size = 14.dp, strokeWidth = 1.8.dp)
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun LinhaFuturo(f: ItemFuturo, onClick: () -> Unit) {
    val colors = SaldoTheme.colors
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            f.data.dayOfMonth.toString().padStart(2, '0'),
            Modifier.width(20.dp),
            style = SaldoTheme.type.footnote, color = colors.secondaryLabel,
        )
        Text(f.item.descricao, Modifier.weight(1f), style = SaldoTheme.type.body, color = colors.label)
        // Fixa (recorrência ou fatura): o mesmo glifo do ledger, mesma leitura.
        if (f.item.recorrente) {
            SaldoGlyph(SaldoIcon.RECORRENTE, colors.secondaryLabel, size = 12.dp, strokeWidth = 1.6.dp)
        }
        MoneyText(
            centavos = f.item.valorCentavos,
            style = SaldoTheme.type.body,
            color = if (f.item is ItemDia.FaturaDia) colors.secondaryLabel else colors.label,
            formato = FormatoMoney.ASSINADO,
        )
    }
}
