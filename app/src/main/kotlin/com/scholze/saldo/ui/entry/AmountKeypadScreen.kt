package com.scholze.saldo.ui.entry

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scholze.saldo.ui.money.formatarCentavos
import com.scholze.saldo.ui.components.FilledActionButton
import com.scholze.saldo.ui.components.SaldoGlyph
import com.scholze.saldo.ui.components.SaldoIcon
import com.scholze.saldo.ui.theme.SaldoTheme
import com.scholze.saldo.ui.theme.tabular

/**
 * Option 1g — plain system decimal pad with HIG spacing.
 *
 * Digits accumulate from the right in centavos, the way a till does, so the
 * comma is placed for you rather than typed.
 */
@Composable
fun AmountKeypadScreen(
    onContinue: (centavos: Long) -> Unit,
    modifier: Modifier = Modifier,
    initialCentavos: Long = 0L,
    titulo: String = "valor",
    textoBotao: String = "continuar",
) {
    val colors = SaldoTheme.colors
    var centavos by rememberSaveable { mutableStateOf(initialCentavos) }

    Column(
        modifier
            .fillMaxSize()
            .background(colors.background)
            .navigationBarsPadding()
            .padding(horizontal = 16.dp),
    ) {
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(titulo, style = SaldoTheme.type.footnote, color = colors.secondaryLabel)
            Text(
                "R$ " + centavos.formatarCentavos(),
                Modifier.padding(top = 4.dp),
                style = SaldoTheme.type.largeTitle.tabular.copy(fontSize = 44.sp),
                color = colors.label,
            )
        }

        Keypad(
            onDigit = { d -> centavos = (centavos * 10 + d).coerceAtMost(99_999_999_99L) },
            onBackspace = { centavos /= 10 },
        )

        FilledActionButton(
            text = textoBotao,
            onClick = { onContinue(centavos) },
            enabled = centavos > 0,
            modifier = Modifier.padding(top = 16.dp, bottom = 24.dp),
        )
    }
}

@Composable
private fun Keypad(onDigit: (Long) -> Unit, onBackspace: () -> Unit) {
    val rows = listOf(
        listOf(Key.Digit(1), Key.Digit(2), Key.Digit(3)),
        listOf(Key.Digit(4), Key.Digit(5), Key.Digit(6)),
        listOf(Key.Digit(7), Key.Digit(8), Key.Digit(9)),
        listOf(Key.Comma, Key.Digit(0), Key.Backspace),
    )

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { key ->
                    KeyButton(
                        key = key,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            when (key) {
                                is Key.Digit -> onDigit(key.value.toLong())
                                Key.Backspace -> onBackspace()
                                // The comma is implicit: centavos are always
                                // the last two digits, so it is inert here.
                                Key.Comma -> Unit
                            }
                        },
                    )
                }
            }
        }
    }
}

private sealed interface Key {
    data class Digit(val value: Int) : Key
    data object Comma : Key
    data object Backspace : Key
}

@Composable
private fun KeyButton(key: Key, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = SaldoTheme.colors
    val interaction = remember { MutableInteractionSource() }
    // As teclas não têm ripple (indication = null) nem mudam de fundo: sem o tique no dedo
    // não sobra retorno nenhum de que a tecla foi lida.
    val haptics = LocalHapticFeedback.current

    Box(
        modifier
            .height(62.dp)
            .clickable(interactionSource = interaction, indication = null) {
                haptics.performHapticFeedback(HapticFeedbackType.KeyboardTap)
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        when (key) {
            is Key.Digit -> KeyLabel(key.value.toString())
            Key.Comma -> KeyLabel(",")
            Key.Backspace -> SaldoGlyph(
                SaldoIcon.BACKSPACE,
                colors.label,
                size = 28.dp,
                strokeWidth = 1.7.dp,
            )
        }
    }
}

@Composable
private fun KeyLabel(text: String) {
    Text(
        text,
        style = SaldoTheme.type.largeTitle.copy(
            fontSize = 26.sp,
            fontWeight = FontWeight.Normal,
            letterSpacing = 0.sp,
        ),
        color = SaldoTheme.colors.label,
    )
}

@Preview(heightDp = 880)
@Composable
private fun KeypadLightPreview() {
    SaldoTheme(darkTheme = false) { AmountKeypadScreen(onContinue = {}) }
}

@Preview(heightDp = 880)
@Composable
private fun KeypadDarkPreview() {
    SaldoTheme(darkTheme = true) { AmountKeypadScreen(onContinue = {}) }
}
