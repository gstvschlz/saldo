package com.scholze.saldo.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * Tokens for the Material 3 Expressive direction (canvas "B · sistema"), on a
 * pinned green seed — no dynamic color. The field names are the ones the app
 * already used under the HIG dress; most kept their name and changed value.
 *
 * `segmentedTrack` e `segmentedThumb` já morreram com o segmented control. `separator`
 * ficou: tem emprego de verdade no M3 como borda do chip não selecionado e como
 * `outlineVariant` do esquema — o que morreu foi o traço de 1px entre linhas.
 */
@Immutable
data class SaldoColors(
    val background: Color,
    val surface: Color,
    val separator: Color,
    val label: Color,
    val secondaryLabel: Color,
    val tint: Color,
    /** Positive deltas, e.g. "+R$ 6.506,38". */
    val positive: Color,
    /** The running-balance figure, now inside the saldo pill. */
    val balance: Color,
    /** Heat tints for the saldo pill, lightest to strongest. */
    val balanceTint1: Color,
    val balanceTint2: Color,
    val balanceTint3: Color,
    val navBar: Color,
    /** The hero card, and the text on it. */
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    /** Day badge and the nav bar's pill indicator. */
    val secondaryContainer: Color,
    /** Category dots. */
    val categoryVariable: Color,
    val categoryFixed: Color,
    /** "para onde foi": as tags além do top 4 agrupadas, e o que não tem tag. */
    val insightOutras: Color,
    val insightSemTag: Color,
    val isDark: Boolean,
)

val LightSaldoColors = SaldoColors(
    background = Color(0xFFF8FAF5),
    surface = Color(0xFFF2F5EE),
    separator = Color(0xFFC1C9BF),
    label = Color(0xFF191D18),
    secondaryLabel = Color(0xFF414941),
    tint = Color(0xFF2F6A45),
    positive = Color(0xFF2F6A45),
    balance = Color(0xFF10281A),
    balanceTint1 = Color(0xFFE2EFE4),
    balanceTint2 = Color(0xFFCBE7D2),
    balanceTint3 = Color(0xFFB4F1C7),
    navBar = Color(0xFFECEFE8),
    primaryContainer = Color(0xFFB4F1C7),
    onPrimaryContainer = Color(0xFF00210F),
    secondaryContainer = Color(0xFFD6E8D8),
    categoryVariable = Color(0xFF8C4F63),
    categoryFixed = Color(0xFF7A5A2E),
    insightOutras = Color(0xFF5C5F66),
    insightSemTag = Color(0xFFB9C0B5),
    isDark = false,
)

val DarkSaldoColors = SaldoColors(
    background = Color(0xFF101410),
    surface = Color(0xFF191F1A),
    separator = Color(0xFF414941),
    label = Color(0xFFE0E4DC),
    secondaryLabel = Color(0xFFBFC9BD),
    tint = Color(0xFF99D5AC),
    positive = Color(0xFF7FD79B),
    balance = Color(0xFFB4F1C7),
    balanceTint1 = Color(0xFF1C2C21),
    balanceTint2 = Color(0xFF243A2B),
    balanceTint3 = Color(0xFF2E4C37),
    navBar = Color(0xFF1D231E),
    primaryContainer = Color(0xFF1E5133),
    onPrimaryContainer = Color(0xFFB4F1C7),
    secondaryContainer = Color(0xFF33463A),
    categoryVariable = Color(0xFFD493A8),
    categoryFixed = Color(0xFFD9BC8A),
    insightOutras = Color(0xFFC0C6CC),
    insightSemTag = Color(0xFF4A524A),
    isDark = true,
)
