package com.scholze.saldo.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.scholze.saldo.domain.descricaoVisivel
import com.scholze.saldo.ui.theme.SaldoTheme

/** An M3 tonal card: 28dp corners, `surfaceContainerLow`, rows separated by space. */
@Composable
fun InsetGroup(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(SaldoTheme.colors.surface)
            .padding(vertical = 6.dp),
        content = content,
    )
}

/**
 * A descrição de uma movimentação numa lista.
 *
 * Em branco ela vira [SEM_DESCRICAO] em tom secundário: a descrição é opcional, e uma linha
 * sem nome não pode se parecer com uma linha que alguém batizou de "sem descrição".
 */
@Composable
fun DescricaoTexto(
    descricao: String,
    modifier: Modifier = Modifier,
    style: TextStyle = SaldoTheme.type.body,
) {
    val colors = SaldoTheme.colors
    Text(
        text = descricao.descricaoVisivel(),
        modifier = modifier,
        style = style,
        color = if (descricao.isBlank()) colors.secondaryLabel else colors.label,
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
        .defaultMinSize(minHeight = 48.dp)

    Row(
        (if (onClick != null) base.clickable(onClick = onClick) else base)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text = label, style = SaldoTheme.type.body, color = colors.label)
        Box(Modifier.weight(1f))
        if (value != null) {
            Text(
                text = value,
                style = SaldoTheme.type.body.copy(fontWeight = FontWeight.Bold),
                color = valueColor ?: colors.secondaryLabel,
            )
        }
        trailing?.invoke()
    }
}

/** A filled, full-width M3 action button. */
@Composable
fun FilledActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = SaldoTheme.colors
    val forma = RoundedCornerShape(percent = 50)
    // Desabilitado NÃO é `surface`: depois da retokenização `surface` e `background` estão a
    // 1,05:1 um do outro, então o botão sumia — dava um retângulo invisível que o usuário
    // toca e nada acontece. O padrão do M3 é container a 12% e rótulo a 38% do `onSurface`;
    // a borda em `separator` entra por cima disso para o alvo nunca ficar sem contorno.
    Box(
        modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(forma)
            .background(if (enabled) colors.tint else colors.label.copy(alpha = 0.12f))
            .then(if (enabled) Modifier else Modifier.border(1.dp, colors.separator, forma))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = SaldoTheme.type.body.copy(fontWeight = FontWeight.Bold),
            // NÃO é Color.White: no escuro o `tint` do M3 é um verde CLARO (#99D5AC) e
            // branco sobre ele dá 1,68:1. `onPrimary` é o papel feito para isto — branco
            // no claro, #003919 no escuro, 7,8:1. Sob o HIG o tint era escuro nos dois
            // esquemas, e foi por isso que o branco fixo passou despercebido até aqui.
            color = if (enabled) MaterialTheme.colorScheme.onPrimary else colors.label.copy(alpha = 0.38f),
        )
    }
}
