package com.scholze.saldo.domain

import java.time.LocalDate

/**
 * As etiquetas que a fila de "sem tag" oferece embaixo de cada linha.
 *
 * As mais usadas nos últimos [DIAS] dias primeiro, o resto em ordem alfabética, e no máximo
 * [LIMITE] — cabe numa fileira e é uma decisão, não um catálogo. Quem quer outra etiqueta (ou uma
 * que ainda não existe) toca no `+` e cai na sheet, onde escolher várias e criar na hora já existe.
 *
 * Ordem alfabética como segundo critério, e não "ordem de criação": num app novo ninguém tem uso
 * nenhum nos 90 dias, e aí a fileira inteira seria um empate — alfabética é a única ordem que o
 * usuário consegue prever.
 */
object TagsSugeridas {

    /** Quantas cabem na fileira. */
    const val LIMITE = 6

    /** O passado que conta como "uso recente". */
    private const val DIAS = 90L

    fun paraFila(tags: List<Tag>, movimentacoes: List<Movimentacao>, hoje: LocalDate): List<Tag> {
        val desde = hoje.minusDays(DIAS)
        val usos = movimentacoes
            .asSequence()
            .filter { it.data >= desde && it.data <= hoje }
            .flatMap { it.tags.asSequence() }
            .groupingBy { it.id }
            .eachCount()
        return tags
            .sortedWith(compareByDescending<Tag> { usos[it.id] ?: 0 }.thenBy { it.nome })
            .take(LIMITE)
    }
}
