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
 * The app theme. Material 3 is the real scheme now, not a thing to be contained:
 * [SaldoTheme.colors] and the `MaterialTheme.colorScheme` are two views of the same
 * pinned green seed, so Material's own surfaces land where the design wants them.
 */
@Composable
fun SaldoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkSaldoColors else LightSaldoColors

    // Antes, cada papel de container do Material era preso na mesma `surface` chapada
    // para o Material não vazar lavanda por baixo do HIG. Agora os papéis VALEM: o
    // esquema é um M3 de verdade, e diálogo, snackbar, switch e campo de texto herdam
    // dele em vez de precisarem ser domados um a um.
    //
    // Os papéis "inverse" são a exceção que continua explícita: `lightColorScheme` /
    // `darkColorScheme` NÃO os derivam de `primary`, então sem mapeá-los o Snackbar
    // volta a se pintar com o lavanda de fábrica — foi exatamente o bug que o mapa
    // antigo corrigia. Aqui eles são os cinzas esverdeados do próprio tema, e
    // `inversePrimary` é o tint do esquema oposto, que é o que lê sobre eles.
    val material = if (darkTheme) {
        darkColorScheme(
            primary = colors.tint,
            onPrimary = Color(0xFF003919),
            primaryContainer = colors.primaryContainer,
            onPrimaryContainer = colors.onPrimaryContainer,
            secondaryContainer = colors.secondaryContainer,
            onSecondaryContainer = colors.onPrimaryContainer,
            background = colors.background,
            onBackground = colors.label,
            surface = colors.background,
            onSurface = colors.label,
            surfaceContainerLowest = Color(0xFF0B0F0B),
            surfaceContainerLow = colors.surface,
            surfaceContainer = colors.navBar,
            surfaceContainerHigh = Color(0xFF262C26),
            surfaceContainerHighest = Color(0xFF313830),
            onSurfaceVariant = colors.secondaryLabel,
            outline = Color(0xFF8A938A),
            outlineVariant = colors.separator,
            inverseSurface = Color(0xFFE0E4DC),
            inverseOnSurface = Color(0xFF2D322C),
            inversePrimary = Color(0xFF2F6A45),
        )
    } else {
        lightColorScheme(
            primary = colors.tint,
            onPrimary = Color.White,
            primaryContainer = colors.primaryContainer,
            onPrimaryContainer = colors.onPrimaryContainer,
            secondaryContainer = colors.secondaryContainer,
            onSecondaryContainer = colors.onPrimaryContainer,
            background = colors.background,
            onBackground = colors.label,
            surface = colors.background,
            onSurface = colors.label,
            surfaceContainerLowest = Color(0xFFFFFFFF),
            surfaceContainerLow = colors.surface,
            surfaceContainer = colors.navBar,
            surfaceContainerHigh = Color(0xFFE6E9E2),
            surfaceContainerHighest = Color(0xFFE0E4DB),
            onSurfaceVariant = colors.secondaryLabel,
            outline = Color(0xFF717970),
            outlineVariant = colors.separator,
            inverseSurface = Color(0xFF2D322C),
            inverseOnSurface = Color(0xFFEFF2EB),
            inversePrimary = Color(0xFF99D5AC),
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
