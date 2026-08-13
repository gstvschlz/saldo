package com.scholze.saldo.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp

/**
 * The HIG type scale used by the canvas. SF Pro is not redistributable, so the
 * platform sans-serif stands in for it; the sizes, weights and (negative)
 * tracking are the design's.
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
        fontSize = 34.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.37.sp,
        lineHeightStyle = trim,
    ),
    navTitle = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 17.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.4).sp,
    ),
    body = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 17.sp,
        letterSpacing = (-0.4).sp,
    ),
    row = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 15.sp,
        letterSpacing = (-0.24).sp,
    ),
    subhead = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 15.sp,
        letterSpacing = (-0.24).sp,
    ),
    footnote = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 13.sp,
        letterSpacing = (-0.08).sp,
    ),
    caption = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 12.sp,
    ),
    sectionHeader = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 12.sp,
        letterSpacing = 0.72.sp,
    ),
)
