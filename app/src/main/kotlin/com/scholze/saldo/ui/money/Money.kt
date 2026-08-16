package com.scholze.saldo.ui.money

/** U+2212 MINUS SIGN, as used in the design rather than a hyphen. */
private const val MINUS = "−"

/** 23850 -> "238,50". Used while the keypad is accumulating centavos. */
fun Long.formatarCentavos(): String = magnitude().formatarCentavos()

/** 23850 -> "238,50" — magnitude only. */
fun Long.centavosValor(): String = magnitude().formatarCentavos()

/**
 * `|this|` como ULong: `abs(Long.MIN_VALUE)` é o próprio `Long.MIN_VALUE` (a magnitude não
 * cabe num Long), e formatá-lo por `abs` imprimia "-92...,-8". Em aritmética módulo 2^64,
 * `0 - MIN_VALUE.toULong()` é exatamente 2^63 — a magnitude certa.
 */
private fun Long.magnitude(): ULong = if (this < 0) 0UL - toULong() else toULong()

private fun ULong.formatarCentavos(): String {
    val reais = this / 100UL
    val cents = this % 100UL
    val agrupado = reais.toString()
        .reversed()
        .chunked(3)
        .joinToString(".")
        .reversed()
    return "$agrupado,${cents.toString().padStart(2, '0')}"
}

/** -23850 -> "−R$ 238,50"; 23850 -> "R$ 238,50". */
fun Long.centavosComSimbolo(): String = (if (this < 0) MINUS else "") + "R$ " + centavosValor()

/**
 * -23850 -> "−238,50"; 23850 -> "+238,50"; 0 -> "0,00".
 *
 * Zero não leva sinal. Este formato aparece em linhas de agregado — "entradas",
 * "saídas economia", o delta do hero — e um "+0,00" numa linha de saída lê como ganho.
 */
fun Long.centavosAssinado(): String = sinal() + centavosValor()

/** -23850 -> "−R$ 238,50"; 23850 -> "+R$ 238,50"; 0 -> "R$ 0,00" — the hero delta form. */
fun Long.centavosAssinadoComSimbolo(): String = sinal() + "R$ " + centavosValor()

private fun Long.sinal(): String = when {
    this < 0 -> MINUS
    this > 0 -> "+"
    else -> ""
}
