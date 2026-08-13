package com.scholze.saldo.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.scholze.saldo.ui.theme.SaldoTheme

/**
 * The .5pt hairline Apple uses between rows.
 *
 * Note this cannot use [Dp.Hairline] — that constant is 0.dp, which means
 * "thinnest possible stroke" when drawing but collapses to nothing when used
 * as a height. One physical pixel is the real equivalent.
 */
@Composable
fun HairlineDivider(
    modifier: Modifier = Modifier,
    startIndent: Dp = 0.dp,
    color: Color = SaldoTheme.colors.separator,
) {
    val onePixel = with(LocalDensity.current) { 1.toDp() }
    Box(
        modifier
            .fillMaxWidth()
            .padding(start = startIndent)
            .height(onePixel)
            .background(color),
    )
}

/** The HIG segmented control: a tinted track with a sliding thumb. */
@Composable
fun SegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SaldoTheme.colors
    BoxWithConstraints(
        modifier
            .clip(RoundedCornerShape(9.dp))
            .background(colors.segmentedTrack)
            .padding(2.dp),
    ) {
        val segmentWidth = maxWidth / options.size
        val thumbX by animateDpAsState(
            targetValue = segmentWidth * selectedIndex,
            label = "segmentedThumb",
        )

        Box(
            Modifier
                .offset(x = thumbX)
                .size(width = segmentWidth, height = 28.dp)
                .shadow(3.dp, RoundedCornerShape(7.dp))
                .background(colors.segmentedThumb, RoundedCornerShape(7.dp)),
        )

        Row(Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, label ->
                val selected = index == selectedIndex
                Box(
                    Modifier
                        .weight(1f)
                        .height(28.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { onSelect(index) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label,
                        style = SaldoTheme.type.footnote.copy(
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        ),
                        color = if (selected) colors.label else colors.secondaryLabel,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

/** An iOS grouped-inset container: rounded card, hairline-separated rows. */
@Composable
fun InsetGroup(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(SaldoTheme.colors.surface),
        content = content,
    )
}

/** A label/value row inside an [InsetGroup]. */
@Composable
fun InsetRow(
    label: String,
    modifier: Modifier = Modifier,
    value: String? = null,
    valueColor: Color? = null,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    val colors = SaldoTheme.colors
    val base = modifier
        .fillMaxWidth()
        .defaultMinSize(minHeight = 44.dp)

    Row(
        (if (onClick != null) base.clickable(onClick = onClick) else base)
            .padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text = label, style = SaldoTheme.type.body, color = colors.label)
        Box(Modifier.weight(1f))
        if (value != null) {
            Text(
                text = value,
                style = SaldoTheme.type.body,
                color = valueColor ?: colors.secondaryLabel,
            )
        }
        trailing?.invoke()
    }
}

/** A filled, full-width HIG action button. */
@Composable
fun FilledActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = SaldoTheme.colors
    Box(
        modifier
            .fillMaxWidth()
            .height(50.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (enabled) colors.tint else colors.segmentedTrack)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = SaldoTheme.type.body.copy(fontWeight = FontWeight.SemiBold),
            color = if (enabled) Color.White else colors.secondaryLabel,
        )
    }
}
