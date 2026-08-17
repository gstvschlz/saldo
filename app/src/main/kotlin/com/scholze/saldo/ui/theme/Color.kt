package com.scholze.saldo.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * Tokens transcribed from the "Finance app design system" canvas (option set
 * 1a / 1d / 1g / 1k). The palette is Apple HIG system colors, so the names
 * follow Apple's semantic roles rather than Material's.
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
    /** The running-balance figure in the saldo column. */
    val balance: Color,
    /** Heat tints for the saldo column, lightest to strongest. */
    val balanceTint1: Color,
    val balanceTint2: Color,
    val balanceTint3: Color,
    val navBar: Color,
    val segmentedTrack: Color,
    val segmentedThumb: Color,
    /** Category dots. */
    val categoryVariable: Color,
    val categoryFixed: Color,
    /** "para onde foi": as tags além do top 4 agrupadas, e o que não tem tag. */
    val insightOutras: Color,
    val insightSemTag: Color,
    val isDark: Boolean,
)

val LightSaldoColors = SaldoColors(
    background = Color(0xFFF2F2F7),
    surface = Color(0xFFFFFFFF),
    separator = Color(0xFFC6C6C8),
    label = Color(0xFF000000),
    secondaryLabel = Color(0x993C3C43),
    tint = Color(0xFF007AFF),
    positive = Color(0xFF1E8E4A),
    balance = Color(0xFF14663A),
    balanceTint1 = Color(0xFFE9F6EE),
    balanceTint2 = Color(0xFFDDF1E3),
    balanceTint3 = Color(0xFFCFEBD8),
    navBar = Color(0xDBF2F2F7),
    segmentedTrack = Color(0x1F767680),
    segmentedThumb = Color(0xFFFFFFFF),
    categoryVariable = Color(0xFFA6486B),
    categoryFixed = Color(0xFFB95A2E),
    insightOutras = Color(0xFF8E8E93),
    insightSemTag = Color(0xFFC7C7CC),
    isDark = false,
)

val DarkSaldoColors = SaldoColors(
    background = Color(0xFF000000),
    surface = Color(0xFF1C1C1E),
    separator = Color(0xFF38383A),
    label = Color(0xFFFFFFFF),
    secondaryLabel = Color(0x99EBEBF5),
    tint = Color(0xFF0A84FF),
    positive = Color(0xFF30D158),
    balance = Color(0xFF5CD98A),
    balanceTint1 = Color(0xFF12301C),
    balanceTint2 = Color(0xFF173B25),
    balanceTint3 = Color(0xFF1D5030),
    navBar = Color(0xD1141416),
    segmentedTrack = Color(0x3D767680),
    segmentedThumb = Color(0xFF636366),
    categoryVariable = Color(0xFFE07A9E),
    categoryFixed = Color(0xFFE58A5A),
    insightOutras = Color(0xFF8E8E93),
    insightSemTag = Color(0xFF48484A),
    isDark = true,
)
