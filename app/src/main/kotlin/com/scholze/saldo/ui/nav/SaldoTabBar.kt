package com.scholze.saldo.ui.nav

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.scholze.saldo.ui.components.SaldoGlyph
import com.scholze.saldo.ui.components.SaldoIcon
import com.scholze.saldo.ui.theme.SaldoTheme

enum class SaldoTab(val rotulo: String, val icon: SaldoIcon) {
    SALDOS("saldos", SaldoIcon.SALDOS),
    TOTAIS("totais", SaldoIcon.TOTAIS),
    TAGS("tags", SaldoIcon.TAGS),
    MAIS("mais", SaldoIcon.MAIS),
}

/**
 * The M3 navigation bar — four destinations, the active one carrying a pill
 * indicator behind its icon, with the add FAB docked over the bar's top edge.
 *
 * The information architecture is exactly what it was: four tabs and a centre add
 * button that is a button, not a destination, and never becomes "selected".
 *
 * Altura total = [ALTURA_FAIXA_FAB] + [ALTURA_BARRA] acima do inset de navegação.
 * Quem desenhar por cima da barra (o SnackbarHost) precisa desse número.
 */
@Composable
fun SaldoTabBar(
    selected: SaldoTab,
    onSelect: (SaldoTab) -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SaldoTheme.colors
    // Box e não Column: o FAB transborda a própria faixa e precisa ser desenhado DEPOIS
    // da barra, senão o `background(navBar)` da Row passa por cima e corta o botão ao
    // meio — que foi exatamente o que aconteceu na primeira versão desta tela.
    Box(modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth()) {
            Spacer(Modifier.height(ALTURA_FAIXA_FAB))
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(colors.navBar)
                    .navigationBarsPadding()
                    .height(ALTURA_BARRA)
                    .padding(bottom = 14.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                TabItem(SaldoTab.SALDOS, selected, onSelect, Modifier.weight(1f))
                TabItem(SaldoTab.TOTAIS, selected, onSelect, Modifier.weight(1f))
                // O vão do FAB.
                Box(Modifier.width(72.dp))
                TabItem(SaldoTab.TAGS, selected, onSelect, Modifier.weight(1f))
                TabItem(SaldoTab.MAIS, selected, onSelect, Modifier.weight(1f))
            }
        }
        // O FAB desce até projetar exatamente [SALIENCIA_FAB] acima da borda da barra.
        // Centrado na faixa ele nasce com o centro em ALTURA_FAIXA_FAB/2; o offset leva esse
        // centro até `ALTURA_FAIXA_FAB - SALIENCIA_FAB + LADO_FAB/2`, e a diferença entre os
        // dois é a conta abaixo. `offset` em vez de padding negativo porque só o desenho
        // desce: o alvo de toque acompanha o deslocamento.
        Box(
            Modifier.fillMaxWidth().height(ALTURA_FAIXA_FAB).align(Alignment.TopCenter),
            contentAlignment = Alignment.Center,
        ) {
            AddButton(onAdd, Modifier.offset(y = ALTURA_FAIXA_FAB / 2 - SALIENCIA_FAB + LADO_FAB / 2))
        }
    }
}

@Composable
private fun TabItem(
    tab: SaldoTab,
    selected: SaldoTab,
    onSelect: (SaldoTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SaldoTheme.colors
    val active = tab == selected

    Column(
        modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onSelect(tab) },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            Modifier
                .size(width = 64.dp, height = 32.dp)
                .clip(RoundedCornerShape(percent = 50))
                .background(if (active) colors.primaryContainer else Color.Transparent),
            contentAlignment = Alignment.Center,
        ) {
            SaldoGlyph(
                tab.icon,
                if (active) colors.onPrimaryContainer else colors.secondaryLabel,
                size = 22.dp,
                strokeWidth = if (active) 2.4.dp else 2.dp,
            )
        }
        Text(
            tab.rotulo,
            style = SaldoTheme.type.caption.copy(
                fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
            ),
            color = if (active) colors.label else colors.secondaryLabel,
        )
    }
}

/** O `+` central, para os testes: é um glifo desenhado, sem nó de texto para procurar. */
const val TAG_ADD = "tab-add"

/** O lado do `+`. */
private val LADO_FAB = 64.dp

/**
 * Quanto do `+` fica ACIMA da borda de cima da barra.
 *
 * Era 32dp — metade do botão, isto é, o FAB centrado na própria borda —, e nessa altura ele
 * lia como um objeto solto boiando sobre a barra, saliente demais ao lado dos outros quatro
 * ícones. Com 22dp ele entra 42dp dentro da barra: continua sendo o botão que se destaca,
 * sem parecer que caiu ali de outra tela.
 */
val SALIENCIA_FAB = 22.dp

/**
 * A faixa que o FAB ocupa acima da barra: o que ele projeta para fora, mais 12dp de respiro
 * entre ele e o conteúdo da tela.
 *
 * Não é constante à toa: quem desenha por cima da barra (o SnackbarHost) soma esta faixa com
 * [ALTURA_BARRA], então mexer na saliência acerta o snackbar sozinho.
 */
val ALTURA_FAIXA_FAB = SALIENCIA_FAB + 12.dp

/** A barra em si, do topo até o inset de navegação. */
val ALTURA_BARRA = 84.dp

@Composable
private fun AddButton(onAdd: () -> Unit, modifier: Modifier = Modifier) {
    val colors = SaldoTheme.colors
    Box(
        modifier
            // requiredSize, não size: a faixa que hospeda o FAB é mais baixa que ele, e
            // `size` se deixa espremer pela restrição máxima do pai — o botão saía com a
            // altura da faixa. `requiredSize` ignora a restrição, que é o que faz o FAB
            // TRANSBORDAR para dentro da barra em vez de caber nela.
            .requiredSize(LADO_FAB)
            .shadow(6.dp, RoundedCornerShape(22.dp))
            .clip(RoundedCornerShape(22.dp))
            .background(colors.tint)
            .clickable(onClick = onAdd)
            .testTag(TAG_ADD),
        contentAlignment = Alignment.Center,
    ) {
        // Branco fixo aqui daria 1,68:1 no escuro, onde o tint é verde claro: `onPrimary`
        // é o papel feito para isso, e mantém o hex numa única casa (Theme.kt).
        SaldoGlyph(
            SaldoIcon.PLUS,
            MaterialTheme.colorScheme.onPrimary,
            size = 28.dp,
            strokeWidth = 2.6.dp,
        )
    }
}
