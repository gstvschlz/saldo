package com.scholze.saldo.domain

/**
 * O valor em reais dentro de um texto de notificação.
 *
 * **O primeiro valor é o valor.** Um aviso de banco costuma trazer dois — "compra aprovada
 * de R$ 32,90 · saldo R$ 1.204,00" — e o primeiro é a transação. Pegar o maior acertaria a
 * compra grande e erraria todas as pequenas.
 *
 * Dólar é ignorado de propósito: sem rede não há cotação, e uma cotação velha mentiria no
 * saldo. `US$` nem chega a casar com o padrão, que exige o `R$`.
 */
object DetectorValor {

    /**
     * `R$`, espaço opcional, o número e centavos opcionais.
     *
     * A alternativa de milhar exige **pelo menos um** grupo `.ddd` (`+`, não `*`) e por isso
     * só ganha quando há separador de verdade. Com `*` ela venceria também em "R$ 1234,56" —
     * casaria só o "123" e o valor viraria R$ 1,23 em vez de R$ 1.234,56, calada.
     */
    private val REGEX = Regex(
        """R\$\s*(\d{1,3}(?:\.\d{3})+|\d+)(?:,(\d{2}))?""",
        RegexOption.IGNORE_CASE,
    )

    /** `null` quando não há valor, quando o valor é zero, ou quando não cabe num `Long`. */
    fun primeiroValorEmCentavos(texto: String): Long? {
        val m = REGEX.find(texto) ?: return null
        val reais = m.groupValues[1].replace(".", "").toLongOrNull() ?: return null
        val centavos = m.groupValues[2].ifEmpty { "0" }.toLong()
        // O teto do Long é ~92 quatrilhões de centavos; qualquer coisa perto disso é lixo de
        // parse, não dinheiro. Multiplicar sem checar daria um número negativo em silêncio.
        if (reais > Long.MAX_VALUE / 100 - 1) return null
        return (reais * 100 + centavos).takeIf { it > 0 }
    }
}
