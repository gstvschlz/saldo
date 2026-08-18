package com.scholze.saldo.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scholze.saldo.ui.privacy.FormatoMoney
import com.scholze.saldo.ui.privacy.MoneyText
import com.scholze.saldo.ui.theme.SaldoTheme

private val PILL = RoundedCornerShape(percent = 50)

/**
 * M3 filter chips — the replacement for the HIG segmented control.
 *
 * Unlike the segmented control there is no sliding thumb: selection is carried by
 * the fill plus a leading check, which is what makes it read as Material and not as
 * a repainted iOS control.
 */
@Composable
fun FiltroChips(
    opcoes: List<String>,
    selecionado: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SaldoTheme.colors
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        opcoes.forEachIndexed { index, rotulo ->
            val ativo = index == selecionado
            Row(
                Modifier
                    .clip(PILL)
                    .then(
                        if (ativo) Modifier.background(colors.secondaryContainer)
                        else Modifier.border(1.dp, colors.separator, PILL),
                    )
                    .clickable { onSelect(index) }
                    // 32dp de altura + 12dp de padding vertical = 56dp de alvo: acima
                    // do mínimo de 44dp mesmo com o chip visualmente baixo.
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (ativo) {
                    SaldoGlyph(SaldoIcon.CHECK, colors.onPrimaryContainer, size = 15.dp, strokeWidth = 2.6.dp)
                }
                Text(
                    text = rotulo,
                    style = SaldoTheme.type.footnote,
                    color = if (ativo) colors.onPrimaryContainer else colors.secondaryLabel,
                )
            }
        }
    }
}

/**
 * O saldo corrido do dia. A escala de calor sobreviveu à morte da coluna: [nivel]
 * 0/1/2 são os mesmos três baldes de `heatTint`, agora pintando o fundo da pill.
 */
@Composable
fun SaldoPill(centavos: Long, nivel: Int, modifier: Modifier = Modifier) {
    val colors = SaldoTheme.colors
    val fundo = when (nivel) {
        0 -> colors.balanceTint1
        1 -> colors.balanceTint2
        else -> colors.balanceTint3
    }
    Box(
        modifier
            .clip(PILL)
            .background(fundo)
            .padding(horizontal = 12.dp, vertical = 5.dp),
    ) {
        MoneyText(
            centavos = centavos,
            style = SaldoTheme.type.footnote,
            color = colors.balance,
            formato = FormatoMoney.VALOR,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** O disco do dia na linha do ledger: número em cima, dia da semana embaixo. */
@Composable
fun DiaBadge(dia: Int, diaSemana: String, destacado: Boolean, modifier: Modifier = Modifier) {
    val colors = SaldoTheme.colors
    Column(
        modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(if (destacado) colors.tint else colors.secondaryContainer),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            dia.toString().padStart(2, '0'),
            style = SaldoTheme.type.row.copy(fontWeight = FontWeight.Bold),
            // Ver FilledActionButton: branco sobre o `tint` do esquema escuro dá 1,68:1.
            color = if (destacado) MaterialTheme.colorScheme.onPrimary else colors.onPrimaryContainer,
        )
        Text(
            diaSemana,
            style = SaldoTheme.type.caption.copy(fontSize = 9.sp, fontWeight = FontWeight.SemiBold),
            color = if (destacado) colors.primaryContainer else colors.secondaryLabel,
        )
    }
}

/**
 * A barra superior grande do M3: título alinhado à ESQUERDA e em corpo grande, que é
 * a diferença mais visível de todas contra a barra centrada do HIG.
 *
 * [acao] entra entre as duas setas — é onde o olho da privacidade mora no ledger.
 */
@Composable
fun SaldoTopBar(
    titulo: String,
    onAnterior: () -> Unit,
    onProximo: () -> Unit,
    modifier: Modifier = Modifier,
    acao: @Composable (() -> Unit)? = null,
) {
    val colors = SaldoTheme.colors
    Column(modifier.fillMaxWidth().background(colors.background)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconeRedondo(SaldoIcon.CHEVRON_LEFT, "mês anterior", onAnterior)
            Box(Modifier.weight(1f))
            acao?.invoke()
            IconeRedondo(SaldoIcon.CHEVRON_RIGHT, "próximo mês", onProximo)
        }
        Text(
            titulo,
            Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 14.dp),
            style = SaldoTheme.type.navTitle,
            color = colors.label,
        )
    }
}

/** Um alvo redondo de 44dp — o tamanho de toque do M3 para ícone sem rótulo. */
@Composable
fun IconeRedondo(icon: SaldoIcon, descricao: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = SaldoTheme.colors
    Box(
        modifier
            .size(44.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .semantics { contentDescription = descricao },
        contentAlignment = Alignment.Center,
    ) {
        SaldoGlyph(icon, colors.secondaryLabel, size = 22.dp, strokeWidth = 2.dp)
    }
}
