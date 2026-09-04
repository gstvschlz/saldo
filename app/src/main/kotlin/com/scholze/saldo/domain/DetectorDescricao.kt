package com.scholze.saldo.domain

/**
 * O nome do gasto dentro de um texto de notificação — o estabelecimento, não o app.
 *
 * Sem isto, o lançamento nascia chamado "Cartão XP": o nome de quem avisou, e não o de onde
 * o dinheiro foi. O valor o [DetectorValor] já achava; o que faltava era ler o resto da
 * frase, que é onde está a única informação que o usuário reconheceria depois no ledger.
 *
 * Duas regras, nesta ordem, e as duas com o valor já removido do texto:
 *
 * 1. **Depois de uma preposição de lugar** — "compra em VMT*CAROLINA", "pagamento no
 *    MERCADO SÃO JOSÉ", "pix para João Silva". É como quase todo banco escreve, e é a regra
 *    que acerta nome com minúscula.
 * 2. **A maior sequência de MAIÚSCULAS** — "VMT*CAROLINA BRL 16.90", que é como a carteira
 *    do cartão escreve, sem preposição nenhuma. Palavras de transação ficam de fora por
 *    lista: "COMPRA" e "APROVADA" são maiúsculas e não são estabelecimento.
 *
 * Devolve `null` quando nada convence — e aí quem chama fica com o nome do app, que é o
 * comportamento antigo. Um palpite ruim é pior do que nenhum: ele vira o nome de uma linha
 * do ledger.
 */
object DetectorDescricao {

    /** Depois destas, o que vem é o estabelecimento. */
    private val PREPOSICOES = setOf("em", "no", "na", "para", "pra")

    /**
     * Aqui a leitura acaba: o que vem depois descreve a FORMA ou a HORA do pagamento, não o
     * lugar. "…em VMT*CAROLINA via cartão digital" — o nome termina no "via".
     *
     * `de`, `do` e `da` NÃO estão aqui de propósito: eles são conectivo dentro de nome
     * ("MERCADO DA ESQUINA", "BAR DO ZÉ"), e cortar neles decepava metade dos nomes.
     */
    private val PARADAS = setOf(
        "via", "com", "pelo", "pela", "usando", "no", "na", "em",
        "cartão", "cartao", "crédito", "credito", "débito", "debito", "conta", "pix",
        "hoje", "ontem", "às", "as", "dia",
    )

    /** Maiúsculas que aparecem em aviso de banco e não são nome de lugar nenhum. */
    private val NAO_SAO_NOME = setOf(
        "COMPRA", "COMPRAS", "APROVADA", "APROVADO", "APROVADAS", "NEGADA", "PAGAMENTO",
        "PAGO", "DEBITO", "DÉBITO", "CREDITO", "CRÉDITO", "CARTAO", "CARTÃO", "PIX",
        "TRANSFERENCIA", "TRANSFERÊNCIA", "SALDO", "FATURA", "BRL", "RS", "R$", "TED", "DOC",
    )

    /** Uma descrição de ledger é curta; o resto da frase não cabe numa linha de lista. */
    private const val MAXIMO = 40

    /** O valor sai do texto antes de tudo: ele não é nome de lugar. */
    private val VALOR = Regex("""(?:R\$|\bBRL)\s*[\d.,]+|[\d.,]+\s*BRL\b""", RegexOption.IGNORE_CASE)

    fun descricao(texto: String): String? {
        val limpo = VALOR.replace(texto, " ").replace(Regex("\\s+"), " ").trim()
        if (limpo.isEmpty()) return null
        val tokens = limpo.split(" ").filter { it.isNotBlank() }
        return depoisDePreposicao(tokens) ?: maiorSequenciaDeMaiusculas(tokens)
    }

    private fun depoisDePreposicao(tokens: List<String>): String? {
        val inicio = tokens.indexOfFirst { it.lowercase().trimEnd(*PONTUACAO) in PREPOSICOES }
        if (inicio < 0) return null

        val nome = mutableListOf<String>()
        for (token in tokens.drop(inicio + 1)) {
            val limpo = token.trimEnd(*PONTUACAO)
            if (limpo.isEmpty()) break
            // A parada só vale depois do primeiro token: "em de" não existe, mas "no MERCADO
            // DA ESQUINA" tem um "DA" no meio que não pode cortar o nome.
            if (nome.isNotEmpty() && limpo.lowercase() in PARADAS) break
            nome += limpo
            // Pontuação fecha a frase: o nome acabou ali.
            if (token != limpo) break
        }
        return aceitavel(nome.joinToString(" "))
    }

    private fun maiorSequenciaDeMaiusculas(tokens: List<String>): String? {
        var melhor = emptyList<String>()
        var atual = mutableListOf<String>()
        for (token in tokens) {
            val limpo = token.trimEnd(*PONTUACAO)
            if (pareceNome(limpo)) {
                atual += limpo
            } else {
                if (atual.size > melhor.size) melhor = atual
                atual = mutableListOf()
            }
        }
        if (atual.size > melhor.size) melhor = atual
        return aceitavel(melhor.joinToString(" "))
    }

    /** Maiúscula de verdade (ou com `*`, que é marca de adquirente) e não palavra de banco. */
    private fun pareceNome(token: String): Boolean {
        if (token.length < 2) return false
        if (token.uppercase() in NAO_SAO_NOME) return false
        if (token.none { it.isLetter() }) return false
        return token == token.uppercase()
    }

    /** Corta no tamanho de uma linha de ledger e recusa o que não tem letra nenhuma. */
    private fun aceitavel(nome: String): String? {
        val limpo = nome.trim().trimEnd(*PONTUACAO).trim()
        if (limpo.isEmpty() || limpo.none { it.isLetter() }) return null
        return if (limpo.length <= MAXIMO) limpo else limpo.take(MAXIMO).trimEnd()
    }

    private val PONTUACAO = charArrayOf('.', ',', ';', ':', '!', '?', ')', '(', '"', '\'')
}
