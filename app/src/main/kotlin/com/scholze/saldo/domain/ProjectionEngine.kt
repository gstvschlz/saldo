package com.scholze.saldo.domain

import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

data class LedgerInput(
    val saldoInicialCentavos: Long,
    val saldoInicialData: LocalDate,
    val movimentacoes: List<Movimentacao>,
    val recorrencias: List<Recorrencia>,
    val mesesMaterializados: Set<YearMonth>,
    val cartao: CartaoConfig,
    val hoje: LocalDate,
)

sealed interface ItemDia {
    val descricao: String
    val valorCentavos: Long
    val recorrente: Boolean

    data class Mov(val mov: Movimentacao) : ItemDia {
        override val descricao get() = mov.descricao
        override val valorCentavos get() = mov.valorCentavos
        override val recorrente get() = mov.recorrenciaId != null
    }

    data class FaturaDia(val fatura: Fatura, val nomeCartao: String) : ItemDia {
        override val descricao get() = "fatura $nomeCartao"
        override val valorCentavos get() = fatura.totalCentavos
        override val recorrente get() = true
    }
}

data class DiaRow(val data: LocalDate, val itens: List<ItemDia>, val saldoCentavos: Long)

data class MesLedger(
    val mes: YearMonth,
    val dias: List<DiaRow>,
    val saldoProjetadoCentavos: Long,
    val estimativaCentavos: Long,
    val deltaNoMesCentavos: Long,
    val projetadoEm: LocalDate,
)

data class TotaisMes(
    val entradasCentavos: Long,
    val saidasPorNatureza: Map<Natureza, Long>,
    val sobrouCentavos: Long,
    val economiaBucketCentavos: Long,
    val faturaAtual: Fatura?,
    val fechamentoFaturaAtual: LocalDate,
    val topTags: List<Pair<Tag, Long>>,
)

object ProjectionEngine {

    fun mes(input: LedgerInput, mes: YearMonth, filtro: FiltroLedger): MesLedger {
        val efetivas = efetivas(input, mes)
        val faturas = FaturaCalculator.faturas(efetivas, input.cartao)
        val fimMes = mes.atEndOfMonth()
        val fimAnterior = mes.minusMonths(1).atEndOfMonth()

        // Itens do mês segundo o filtro.
        val movsDoMes = efetivas.asSequence()
            .filter { it.natureza != Natureza.CARTAO }
            .filter { YearMonth.from(it.data) == mes }
            .filter { passaFiltro(it, filtro) }
            .toList()
        val faturasDoMes =
            if (filtro == FiltroLedger.DIARIOS) emptyList()
            else faturas.filter { YearMonth.from(it.vencimento) == mes }

        // Coluna de saldo corre sobre o conjunto filtrado.
        var corrente = input.saldoInicialCentavos +
            efetivas.filter { it.natureza != Natureza.CARTAO && it.data <= fimAnterior && passaFiltro(it, filtro) }
                .sumOf { it.valorCentavos } +
            (if (filtro == FiltroLedger.DIARIOS) 0L
             else faturas.filter { it.vencimento <= fimAnterior }.sumOf { it.totalCentavos })

        val dias = (1..mes.lengthOfMonth()).map { dia ->
            val data = mes.atDay(dia)
            val itens: List<ItemDia> =
                movsDoMes.filter { it.data == data }.map { ItemDia.Mov(it) } +
                    faturasDoMes.filter { it.vencimento == data }
                        .map { ItemDia.FaturaDia(it, input.cartao.nome) }
            corrente += itens.sumOf { it.valorCentavos }
            DiaRow(data, itens, corrente)
        }

        // Projeção (sempre sobre o conjunto completo, não o filtrado).
        val estimativa: Long
        val projetado: Long
        if (fimMes <= input.hoje) {
            estimativa = 0
            projetado = saldoReal(input, efetivas, faturas, fimMes)
        } else {
            val media = mediaDiaria(input)
            val diasRestantes = ChronoUnit.DAYS.between(input.hoje, fimMes)
            estimativa = media * diasRestantes
            val agendadas = efetivas
                .filter { it.natureza != Natureza.CARTAO && it.data > input.hoje && it.data <= fimMes }
                .sumOf { it.valorCentavos }
            val faturasFuturas = faturas
                .filter { it.vencimento > input.hoje && it.vencimento <= fimMes }
                .sumOf { it.totalCentavos }
            projetado = saldoReal(input, efetivas, faturas, input.hoje) + agendadas + faturasFuturas - estimativa
        }

        return MesLedger(
            mes = mes,
            dias = dias,
            saldoProjetadoCentavos = projetado,
            estimativaCentavos = estimativa,
            deltaNoMesCentavos = projetado - saldoReal(input, efetivas, faturas, fimAnterior),
            projetadoEm = fimMes,
        )
    }

    // Parameter deliberately NOT named `mes` — it would shadow the mes() function
    // and the call below would fail to resolve.
    fun totais(input: LedgerInput, mesAlvo: YearMonth): TotaisMes {
        val efetivas = efetivas(input, mesAlvo)
        val faturas = FaturaCalculator.faturas(efetivas, input.cartao)
        val doMes = efetivas.filter { YearMonth.from(it.data) == mesAlvo }

        val cicloAberto = FaturaCalculator.cicloDaCompra(input.hoje, input.cartao)
        val ledger = mes(input, mesAlvo, FiltroLedger.TODAS)

        return TotaisMes(
            entradasCentavos = doMes.filter { it.natureza != Natureza.CARTAO && it.valorCentavos > 0 }
                .sumOf { it.valorCentavos },
            saidasPorNatureza = Natureza.entries.associateWith { n ->
                -doMes.filter { it.natureza == n && it.valorCentavos < 0 }.sumOf { it.valorCentavos }
            },
            sobrouCentavos = ledger.deltaNoMesCentavos,
            economiaBucketCentavos = -efetivas
                .filter { it.natureza == Natureza.ECONOMIA && it.data <= mesAlvo.atEndOfMonth() }
                .sumOf { it.valorCentavos },
            faturaAtual = faturas.firstOrNull { it.ciclo == cicloAberto },
            fechamentoFaturaAtual = FaturaCalculator.fechamentoDoCiclo(cicloAberto, input.cartao),
            topTags = doMes.asSequence()
                .filter { it.valorCentavos < 0 }
                .flatMap { m -> m.tags.map { it to -m.valorCentavos } }
                .groupBy({ it.first }, { it.second })
                .map { (tag, valores) -> tag to valores.sum() }
                .sortedByDescending { it.second }
                .take(5),
        )
    }

    // ---- internals ----

    /** Materialized rows + virtual expansions, from saldoInicialData through end of [ateMes]. */
    private fun efetivas(input: LedgerInput, ateMes: YearMonth): List<Movimentacao> {
        val fim = ateMes.atEndOfMonth()
        val reais = input.movimentacoes.filter { it.data >= input.saldoInicialData && it.data <= fim }
        val virtuais = buildList {
            var m = YearMonth.from(input.saldoInicialData)
            while (m <= ateMes) {
                if (m !in input.mesesMaterializados) {
                    addAll(RecurrenceExpander.ocorrenciasNoMes(input.recorrencias, m)
                        .filter { it.data >= input.saldoInicialData })
                }
                m = m.plusMonths(1)
            }
        }
        return (reais + virtuais).sortedBy { it.data }
    }

    private fun passaFiltro(mov: Movimentacao, filtro: FiltroLedger): Boolean = when (filtro) {
        FiltroLedger.TODAS -> true
        FiltroLedger.DIARIOS -> mov.recorrenciaId == null && mov.natureza == Natureza.DIARIO
        FiltroLedger.FIXAS -> mov.recorrenciaId != null
    }

    private fun saldoReal(input: LedgerInput, efetivas: List<Movimentacao>, faturas: List<Fatura>, ate: LocalDate): Long =
        input.saldoInicialCentavos +
            efetivas.filter { it.natureza != Natureza.CARTAO && it.data <= ate }.sumOf { it.valorCentavos } +
            faturas.filter { it.vencimento <= ate }.sumOf { it.totalCentavos }

    /** Σ|one-off DIARIO saídas| in the 30 days ending today, ÷ 30. */
    private fun mediaDiaria(input: LedgerInput): Long {
        val inicioJanela = input.hoje.minusDays(29)
        val total = input.movimentacoes
            .filter {
                it.recorrenciaId == null && it.natureza == Natureza.DIARIO &&
                    it.valorCentavos < 0 && it.data >= inicioJanela && it.data <= input.hoje
            }
            .sumOf { -it.valorCentavos }
        return total / 30
    }
}
