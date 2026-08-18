package com.scholze.saldo.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp

/**
 * The Material 3 Expressive ramp (canvas "B · sistema"), on the platform sans —
 * which on Android is Roboto, M3's own face, so the app carries no font asset.
 * The HIG's negative tracking survives only on display sizes; M3 does not tighten
 * body text.
 */
@Immutable
data class SaldoTypography(
    val largeTitle: TextStyle,
    val navTitle: TextStyle,
    val body: TextStyle,
    val row: TextStyle,
    val subhead: TextStyle,
    val footnote: TextStyle,
    val caption: TextStyle,
    val sectionHeader: TextStyle,
)

private val trim = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None,
)

val saldoTypography = SaldoTypography(
    largeTitle = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 38.sp,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = (-1.1).sp,
        lineHeightStyle = trim,
    ),
    navTitle = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 30.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.6).sp,
    ),
    body = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 15.sp,
        fontWeight = FontWeight.Medium,
    ),
    row = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 15.sp,
        fontWeight = FontWeight.Medium,
    ),
    subhead = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
    ),
    footnote = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    caption = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
    ),
    // Deixa de ser caixa-alta destacada: no M3 o cabeçalho de seção é um título de
    // card em caixa baixa. As três strings que o usavam perderam as maiúsculas.
    sectionHeader = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 15.sp,
        fontWeight = FontWeight.Bold,
    ),
)
