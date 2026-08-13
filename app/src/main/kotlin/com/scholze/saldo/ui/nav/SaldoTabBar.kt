package com.scholze.saldo.ui.nav

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scholze.saldo.ui.components.HairlineDivider
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
 * Option 1k — four tabs with a prominent center add button.
 *
 * The add control sits in the tab bar's center slot but is a button, not a
 * tab: it opens the nova-movimentação sheet and never becomes "selected".
 */
@Composable
fun SaldoTabBar(
    selected: SaldoTab,
    onSelect: (SaldoTab) -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SaldoTheme.colors
    Column(modifier.fillMaxWidth().background(colors.navBar)) {
        HairlineDivider()
        Row(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(52.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TabItem(SaldoTab.SALDOS, selected, onSelect, Modifier.weight(1f))
            TabItem(SaldoTab.TOTAIS, selected, onSelect, Modifier.weight(1f))

            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                AddButton(onAdd)
            }

            TabItem(SaldoTab.TAGS, selected, onSelect, Modifier.weight(1f))
            TabItem(SaldoTab.MAIS, selected, onSelect, Modifier.weight(1f))
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
    val tint = if (active) colors.tint else colors.secondaryLabel

    Column(
        modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onSelect(tab) }
            .padding(top = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        SaldoGlyph(tab.icon, tint, size = 24.dp, strokeWidth = if (active) 2.2.dp else 1.9.dp)
        Text(
            tab.rotulo,
            style = SaldoTheme.type.caption.copy(
                fontSize = 10.sp,
                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
            ),
            color = tint,
        )
    }
}

@Composable
private fun AddButton(onAdd: () -> Unit) {
    val colors = SaldoTheme.colors
    Box(
        Modifier
            .size(46.dp)
            .clip(CircleShape)
            .background(colors.tint)
            .clickable(onClick = onAdd),
        contentAlignment = Alignment.Center,
    ) {
        SaldoGlyph(SaldoIcon.PLUS, Color.White, size = 26.dp, strokeWidth = 2.4.dp)
    }
}
