package com.scholze.saldo.domain

import java.time.LocalDate
import java.time.YearMonth

object FaturaCalculator {

    /** Day F of the cycle's month, clamped to month length. */
    fun fechamentoDoCiclo(ciclo: YearMonth, config: CartaoConfig): LocalDate =
        ciclo.atDay(minOf(config.fechamentoDia, ciclo.lengthOfMonth()))

    /** Day V in the cycle month when V > F, otherwise day V of the next month. */
    fun vencimentoDoCiclo(ciclo: YearMonth, config: CartaoConfig): LocalDate {
        val mes = if (config.vencimentoDia > config.fechamentoDia) ciclo else ciclo.plusMonths(1)
        return mes.atDay(minOf(config.vencimentoDia, mes.lengthOfMonth()))
    }

    /** Purchases on the closing day belong to the cycle closing that day. */
    fun cicloDaCompra(dataCompra: LocalDate, config: CartaoConfig): YearMonth {
        val mes = YearMonth.from(dataCompra)
        return if (dataCompra <= fechamentoDoCiclo(mes, config)) mes else mes.plusMonths(1)
    }

    fun faturas(compras: List<Movimentacao>, config: CartaoConfig): List<Fatura> =
        compras.asSequence()
            .filter { it.natureza == Natureza.CARTAO }
            .groupBy { cicloDaCompra(it.data, config) }
            .map { (ciclo, doCiclo) ->
                Fatura(
                    ciclo = ciclo,
                    vencimento = vencimentoDoCiclo(ciclo, config),
                    totalCentavos = doCiclo.sumOf { it.valorCentavos },
                    compras = doCiclo.sortedBy { it.data },
                )
            }
            .sortedBy { it.ciclo }
}
