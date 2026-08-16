package com.scholze.saldo.model

/** U+2212 MINUS SIGN, as used in the design rather than a hyphen. */
private const val MINUS = "−"

/** 23850 -> "238,50". Used while the keypad is accumulating centavos. */
fun Long.formatarCentavos(): String {
    val reais = this / 100
    val cents = this % 100
    val agrupado = reais.toString()
        .reversed()
        .chunked(3)
        .joinToString(".")
        .reversed()
    return "$agrupado,${cents.toString().padStart(2, '0')}"
}

/** 23850 -> "238,50" — magnitude only. */
fun Long.centavosValor(): String = kotlin.math.abs(this).formatarCentavos()

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
