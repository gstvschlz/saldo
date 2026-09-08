package com.scholze.saldo.domain

import java.time.LocalDate
import java.time.YearMonth

object FaturaCalculator {

    /** Day F of the cycle's month, clamped to month length. */
    fun fechamentoDoCiclo(ciclo: YearMonth, config: CartaoConfig): LocalDate =
        ciclo.atDay(minOf(config.fechamentoDia, ciclo.lengthOfMonth()))

    /**
     * O vencimento do ciclo: dia V no mês do ciclo quando ele cai DEPOIS do fechamento, senão dia
     * V do mês seguinte.
     *
     * A comparação é entre as datas **já clamped**, não entre os dias crus. Fecha 30 / vence 31 em
     * fevereiro: os dois dias clampam para 28, e comparar 31 > 30 daria uma fatura que fecha e vence
     * no mesmo dia — o único mês do ano sem um dia de carência. Comparando as datas, fevereiro passa
     * a vencer em março, como todos os outros meses com essa configuração.
     */
    fun vencimentoDoCiclo(ciclo: YearMonth, config: CartaoConfig): LocalDate {
        val noCiclo = ciclo.atDay(minOf(config.vencimentoDia, ciclo.lengthOfMonth()))
        if (noCiclo > fechamentoDoCiclo(ciclo, config)) return noCiclo
        val seguinte = ciclo.plusMonths(1)
        return seguinte.atDay(minOf(config.vencimentoDia, seguinte.lengthOfMonth()))
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
