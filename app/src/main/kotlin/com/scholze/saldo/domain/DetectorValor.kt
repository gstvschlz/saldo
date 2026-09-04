package com.scholze.saldo.domain

/**
 * O valor em reais dentro de um texto de notificação.
 *
 * **O primeiro valor é o valor.** Um aviso de banco costuma trazer dois — "compra aprovada
 * de R$ 32,90 · saldo R$ 1.204,00" — e o primeiro é a transação. Pegar o maior acertaria a
 * compra grande e erraria todas as pequenas.
 *
 * Dólar é ignorado de propósito: sem rede não há cotação, e uma cotação velha mentiria no
 * saldo. `US$` não casa com nenhuma das marcas daqui.
 */
object DetectorValor {

    /**
     * As duas marcas de real que aparecem na barra: `R$` e `BRL`.
     *
     * `BRL` existe porque a carteira do cartão anuncia "VMT*CAROLINA BRL 16.90" enquanto o
     * app do mesmo banco anuncia "Compra de R$ 16,90" — o mesmo gasto, escrito de dois
     * jeitos. Ela vem antes ou depois do número, colada nele ou não; a borda de palavra fica
     * só do lado de fora, para "COBRL" não virar dinheiro e "BRL16,90" ainda virar.
     *
     * O número é capturado inteiro — dígitos, pontos e vírgulas — e desmontado em
     * [emCentavos]: decidir milhar contra decimal dentro do próprio regex foi o que fazia
     * "R$ 16.90" virar R$ 16,00 calado.
     */
    private val REGEX = Regex(
        """(?:(?:R\$|\bBRL)\s*(\d[\d.,]*)|(\d[\d.,]*)\s*BRL\b)""",
        RegexOption.IGNORE_CASE,
    )

    /** `null` quando não há valor, quando o valor é zero, ou quando não cabe num `Long`. */
    fun primeiroValorEmCentavos(texto: String): Long? {
        val m = REGEX.find(texto) ?: return null
        val token = m.groupValues[1].ifEmpty { m.groupValues[2] }
        return emCentavos(token)
    }

    /**
     * Desmonta "1.234,56", "1,234.56", "16.90", "1.234" ou "16" em centavos.
     *
     * A regra é uma só, e vale igual para ponto e para vírgula: **o último separador é
     * decimal se tiver exatamente dois dígitos depois dele**; qualquer outro é separador de
     * milhar. É o que distingue "16.90" — dezesseis e noventa, como a carteira escreve — de
     * "1.234" — mil duzentos e trinta e quatro, como o banco escreve — sem precisar saber
     * qual dos dois mandou a notificação.
     */
    private fun emCentavos(token: String): Long? {
        val limpo = token.trimEnd('.', ',')
        if (limpo.isEmpty()) return null

        val corte = limpo.indexOfLast { it == '.' || it == ',' }
        val decimal = corte >= 0 && limpo.length - corte - 1 == 2

        val parteInteira = if (decimal) limpo.substring(0, corte) else limpo
        val parteDecimal = if (decimal) limpo.substring(corte + 1) else "0"

        val reais = parteInteira.filter { it.isDigit() }.toLongOrNull() ?: return null
        val centavos = parteDecimal.toLongOrNull() ?: return null
        // O teto do Long é ~92 quatrilhões de centavos; qualquer coisa perto disso é lixo de
        // parse, não dinheiro. Multiplicar sem checar daria um número negativo em silêncio.
        if (reais > Long.MAX_VALUE / 100 - 1) return null
        return (reais * 100 + centavos).takeIf { it > 0 }
    }
}
