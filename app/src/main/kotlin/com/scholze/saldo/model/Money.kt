package com.scholze.saldo.model

import java.math.BigDecimal
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

private val ptBr: Locale = Locale.forLanguageTag("pt-BR")

private val grouped: DecimalFormat
    get() = DecimalFormat("#,##0.00", DecimalFormatSymbols(ptBr))

/** U+2212 MINUS SIGN, as used in the design rather than a hyphen. */
private const val MINUS = "−"

/** "120.461,84" */
fun BigDecimal.formatarValor(): String = grouped.format(this.abs())

/** "R$ 120.461,84" */
fun BigDecimal.formatarComSimbolo(): String = "R$ " + formatarValor()

/** "+R$ 6.506,38" / "−R$ 189,90" */
fun BigDecimal.formatarAssinadoComSimbolo(): String =
    sinal() + formatarComSimbolo()

/** "+8.240,00" / "−189,90", the form used inside the ledger rows. */
fun BigDecimal.formatarAssinado(): String = sinal() + formatarValor()

private fun BigDecimal.sinal(): String = when (signum()) {
    -1 -> MINUS
    else -> "+"
}

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

/** -23850 -> "−238,50"; 23850 -> "+238,50". */
fun Long.centavosAssinado(): String = (if (this < 0) MINUS else "+") + centavosValor()
