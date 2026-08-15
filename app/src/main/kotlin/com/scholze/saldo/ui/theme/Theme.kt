package com.scholze.saldo.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle

val LocalSaldoColors: ProvidableCompositionLocal<SaldoColors> =
    staticCompositionLocalOf { LightSaldoColors }

val LocalSaldoTypography: ProvidableCompositionLocal<SaldoTypography> =
    staticCompositionLocalOf { saldoTypography }

/**
 * The app theme. Material 3 is kept underneath only so that plumbing which
 * expects it (ripples, text defaults, window insets) keeps working; every
 * visible color and type choice comes from [SaldoTheme.colors] / [SaldoTheme.type].
 */
@Composable
fun SaldoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkSaldoColors else LightSaldoColors

    // `surface` alone is not enough: AlertDialog paints itself with
    // `surfaceContainerHigh`, and OutlinedTextField borders come from `outline` — left
    // unmapped they fall back to Material's baseline lavender and land a Material dialog
    // in the middle of an otherwise HIG-coloured app. Every container role is pinned to
    // the app surface so the mapping cannot leak again.
    val material = if (darkTheme) {
        darkColorScheme(
            primary = colors.tint,
            // O polegar do Switch usa `onPrimary`; sem mapear, no escuro ele saía roxo.
            onPrimary = Color.White,
            background = colors.background,
            surface = colors.surface,
            surfaceContainerLowest = colors.surface,
            surfaceContainerLow = colors.surface,
            surfaceContainer = colors.surface,
            surfaceContainerHigh = colors.surface,
            surfaceContainerHighest = colors.surface,
            onBackground = colors.label,
            onSurface = colors.label,
            onSurfaceVariant = colors.secondaryLabel,
            outline = colors.separator,
        )
    } else {
        lightColorScheme(
            primary = colors.tint,
            // O polegar do Switch usa `onPrimary`; sem mapear, no escuro ele saía roxo.
            onPrimary = Color.White,
            background = colors.background,
            surface = colors.surface,
            surfaceContainerLowest = colors.surface,
            surfaceContainerLow = colors.surface,
            surfaceContainer = colors.surface,
            surfaceContainerHigh = colors.surface,
            surfaceContainerHighest = colors.surface,
            onBackground = colors.label,
            onSurface = colors.label,
            onSurfaceVariant = colors.secondaryLabel,
            outline = colors.separator,
        )
    }

    CompositionLocalProvider(
        LocalSaldoColors provides colors,
        LocalSaldoTypography provides saldoTypography,
        LocalContentColor provides colors.label,
    ) {
        MaterialTheme(
            colorScheme = material,
            typography = MaterialTheme.typography,
            content = content,
        )
    }
}

object SaldoTheme {
    val colors: SaldoColors
        @Composable @ReadOnlyComposable get() = LocalSaldoColors.current

    val type: SaldoTypography
        @Composable @ReadOnlyComposable get() = LocalSaldoTypography.current
}

/** Tabular figures, so columns of money line up. */
val TextStyle.tabular: TextStyle
    get() = copy(
        fontFeatureSettings = "tnum",
    )
