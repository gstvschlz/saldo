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

    val material = if (darkTheme) {
        darkColorScheme(
            primary = colors.tint,
            background = colors.background,
            surface = colors.surface,
            onBackground = colors.label,
            onSurface = colors.label,
        )
    } else {
        lightColorScheme(
            primary = colors.tint,
            background = colors.background,
            surface = colors.surface,
            onBackground = colors.label,
            onSurface = colors.label,
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
