package com.scholze.saldo.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import kotlin.math.abs

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
    /**
     * Os sete tons da grade do board: três rosas (saiu mais), o neutro do dia parado e
     * três verdes (entrou mais). Rosa e verde são as duas famílias que o app já usa —
     * `categoryVariable` e `tint` — esticadas em rampa.
     */
    val boardZero: Color,
    val boardNeg1: Color,
    val boardNeg2: Color,
    val boardNeg3: Color,
    val boardPos1: Color,
    val boardPos2: Color,
    val boardPos3: Color,
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
    boardZero = Color(0xFFE7EBE4),
    boardNeg1 = Color(0xFFF0DDE3),
    boardNeg2 = Color(0xFFD7A9B7),
    boardNeg3 = Color(0xFF8C4F63),
    boardPos1 = Color(0xFFCBE7D2),
    boardPos2 = Color(0xFF79C293),
    boardPos3 = Color(0xFF2F6A45),
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
    boardZero = Color(0xFF232A24),
    boardNeg1 = Color(0xFF3A2830),
    boardNeg2 = Color(0xFF6B3C4C),
    boardNeg3 = Color(0xFFC98FA4),
    boardPos1 = Color(0xFF223A2A),
    boardPos2 = Color(0xFF3E7A55),
    boardPos3 = Color(0xFF7FD79B),
    isDark = true,
)

/**
 * O tom da célula para um nível de −3 a 3. Satura nas pontas em vez de estourar: o motor
 * promete a faixa, mas um `nivel` fora dela não pode virar crash de renderização.
 */
fun SaldoColors.tomDoBoard(nivel: Int): Color = when (nivel.coerceIn(-3, 3)) {
    -3 -> boardNeg3
    -2 -> boardNeg2
    -1 -> boardNeg1
    1 -> boardPos1
    2 -> boardPos2
    3 -> boardPos3
    else -> boardZero
}

/**
 * A tinta que se lê em cima de [tomDoBoard]. Só os tons 3 são escuros o bastante (no
 * claro) ou claros o bastante (no escuro) para exigirem o contraste invertido.
 */
fun SaldoColors.textoSobreBoard(nivel: Int): Color =
    if (abs(nivel) >= 3) (if (isDark) background else Color(0xFFFFFFFF)) else secondaryLabel
