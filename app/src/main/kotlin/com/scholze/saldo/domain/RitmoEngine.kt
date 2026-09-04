package com.scholze.saldo.domain

import java.time.YearMonth

/**
 * O ritmo de gasto de um mês: quanto já saiu até cada dia, contra a mesma altura dos meses
 * anteriores.
 *
 * [acumulado] é monotônico e tem um ponto por dia decorrido — index 0 é o dia 1. No mês
 * corrente ele para em hoje, pela mesma razão do board: só fato consumado.
 *
 * [referencia] tem exatamente o mesmo tamanho e responde "no dia 4, quanto eu costumava ter
 * gasto?". Vazia quando não há mês anterior com que comparar, e aí a tela mostra só uma
 * linha em vez de inventar uma média de nada.
 */
data class Ritmo(
    val mes: YearMonth,
    val acumulado: List<Long>,
    val referencia: List<Long>,
    val mesesComparados: Int,
) {
    val gastoAteAgora: Long get() = acumulado.lastOrNull() ?: 0L

    val referenciaAteAgora: Long get() = referencia.lastOrNull() ?: 0L

    /**
     * Existe mês anterior com que comparar.
     *
     * É diferente de [desvioPercentual] ser `null`: no dia 3 de um mês, o costume dos meses
     * anteriores no dia 3 pode ser legitimamente zero — ninguém tinha gasto nada ainda —, e
     * aí não há porcentagem possível mas há comparação. Tratar os dois casos como um só
     * fazia a tela dizer "sem mês anterior" para quem tem seis meses de histórico.
     */
    val temComparacao: Boolean get() = mesesComparados > 0

    /**
     * Quanto por cento acima (positivo) ou abaixo (negativo) do costume, hoje.
     *
     * `null` nos dois casos em que a divisão não existe: sem mês anterior, e com o costume
     * zerado neste ponto do mês. Quem mostra distingue os dois por [temComparacao].
     */
    val desvioPercentual: Int?
        get() {
            val base = referenciaAteAgora
            if (!temComparacao || base <= 0L) return null
            return (((gastoAteAgora - base) * 100) / base).toInt()
        }
}

/**
 * O motor do ritmo.
 *
 * Conta **só o que saiu**, e conta a compra no cartão no dia da compra — as duas escolhas do
 * "para onde foi", pela mesma razão: a pergunta aqui é sobre comportamento, não sobre quando
 * o banco cobrou. Uma entrada no meio do mês não "devolve" ritmo, então salário não faz a
 * linha descer.
 */
object RitmoEngine {

    /** Três meses de costume. Um só seria anedota; seis arrastariam mudança de vida antiga. */
    const val MESES_DE_REFERENCIA = 3

    fun ritmo(input: LedgerInput, mes: YearMonth): Ritmo {
        val acumulado = acumuladoDe(input, mes)

        val anteriores = (1..MESES_DE_REFERENCIA)
            .map { mes.minusMonths(it.toLong()) }
            // Um mês anterior ao saldo inicial não é um mês barato: é um mês sem registro.
            .filter { it >= YearMonth.from(input.saldoInicialData) }
            .map { acumuladoDe(input, it) }
            .filter { it.isNotEmpty() && it.last() > 0L }

        val referencia = if (anteriores.isEmpty()) {
            emptyList()
        } else {
            acumulado.indices.map { i ->
                // Um mês mais curto não tem dia 31: o costume dele naquele ponto é o total
                // com que ele fechou, e não zero.
                anteriores.map { serie -> serie[minOf(i, serie.lastIndex)] }.average().toLong()
            }
        }

        return Ritmo(mes, acumulado, referencia, anteriores.size)
    }

    /** O acumulado de saídas de [mes], parando em hoje quando [mes] é o corrente. */
    private fun acumuladoDe(input: LedgerInput, mes: YearMonth): List<Long> {
        val ultimo = minOf(mes.atEndOfMonth(), input.hoje)
        if (ultimo < mes.atDay(1)) return emptyList()

        val porDia = ProjectionEngine.movimentacoesDoMes(input, mes)
            .filter { it.valorCentavos < 0 && it.data <= ultimo }
            .groupBy { it.data.dayOfMonth }
            .mapValues { (_, movs) -> movs.sumOf { -it.valorCentavos } }

        var soma = 0L
        return (1..ultimo.dayOfMonth).map { dia ->
            soma += porDia[dia] ?: 0L
            soma
        }
    }
}
