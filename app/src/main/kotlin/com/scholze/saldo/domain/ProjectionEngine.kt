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
    /**
     * Gasto avulso ainda esperado no intervalo `(hoje, fim de mes]` — não inclui hoje, e é
     * zero num mês que já terminou. Já vem descontado de [saldoProjetadoCentavos]; quem
     * mostra os dois lado a lado está mostrando o total e uma de suas parcelas.
     */
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

    /**
     * [tagId] estreita a lista (e a coluna de saldo) a uma etiqueta só. As faturas somem
     * junto: uma fatura é o total agregado de um ciclo, não uma linha que carrega tags.
     * A projeção do hero — [MesLedger.saldoProjetadoCentavos], [MesLedger.estimativaCentavos],
     * [MesLedger.deltaNoMesCentavos] — segue sobre o mês inteiro, sem filtro nenhum.
     */
    fun mes(input: LedgerInput, mes: YearMonth, filtro: FiltroLedger, tagId: Long? = null): MesLedger {
        val efetivas = efetivas(input, mes)
        val faturas = FaturaCalculator.faturas(efetivas, input.cartao)
        val fimMes = mes.atEndOfMonth()
        val fimAnterior = mes.minusMonths(1).atEndOfMonth()

        // Itens do mês segundo o filtro.
        val movsDoMes = efetivas.asSequence()
            .filter { it.natureza != Natureza.CARTAO }
            .filter { YearMonth.from(it.data) == mes }
            .filter { passaFiltro(it, filtro, tagId) }
            .toList()
        val faturasDoMes =
            if (filtro == FiltroLedger.DIARIOS || tagId != null) emptyList()
            else faturas.filter { YearMonth.from(it.vencimento) == mes }

        // Coluna de saldo corre sobre o conjunto filtrado.
        var corrente = input.saldoInicialCentavos +
            efetivas.filter { it.natureza != Natureza.CARTAO && it.data <= fimAnterior && passaFiltro(it, filtro, tagId) }
                .sumOf { it.valorCentavos } +
            (if (filtro == FiltroLedger.DIARIOS || tagId != null) 0L
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
        val estimativa = estimativaDoMes(input, mes)
        val projetado = projetadoDoMes(input, efetivas, faturas, mes)
        val projetadoAnterior = projetadoDoMes(input, efetivas, faturas, mes.minusMonths(1))

        return MesLedger(
            mes = mes,
            dias = dias,
            saldoProjetadoCentavos = projetado,
            estimativaCentavos = estimativa,
            deltaNoMesCentavos = projetado - projetadoAnterior,
            projetadoEm = fimMes,
        )
    }

    // Parameter deliberately NOT named `mes` — it would shadow the mes() function
    // and the call below would fail to resolve.
    fun totais(input: LedgerInput, mesAlvo: YearMonth): TotaisMes {
        val efetivasAteMesAlvo = efetivas(input, mesAlvo)
        val doMes = efetivasAteMesAlvo.filter { YearMonth.from(it.data) == mesAlvo }

        // faturaAtual é sobre o ciclo aberto em `hoje`, não sobre mesAlvo — precisa de
        // efetivas expandidas até cobrir o mês do ciclo aberto, mesmo que mesAlvo seja passado.
        val cicloAberto = FaturaCalculator.cicloDaCompra(input.hoje, input.cartao)
        val mesCicloAberto = YearMonth.from(FaturaCalculator.fechamentoDoCiclo(cicloAberto, input.cartao))
        val faturasParaAtual = FaturaCalculator.faturas(efetivas(input, maxOf(mesAlvo, mesCicloAberto)), input.cartao)
        val ledger = mes(input, mesAlvo, FiltroLedger.TODAS)

        return TotaisMes(
            entradasCentavos = doMes.filter { it.natureza != Natureza.CARTAO && it.valorCentavos > 0 }
                .sumOf { it.valorCentavos },
            saidasPorNatureza = Natureza.entries.associateWith { n ->
                -doMes.filter { it.natureza == n && it.valorCentavos < 0 }.sumOf { it.valorCentavos }
            },
            sobrouCentavos = ledger.deltaNoMesCentavos,
            economiaBucketCentavos = -efetivasAteMesAlvo
                .filter { it.natureza == Natureza.ECONOMIA && it.data <= mesAlvo.atEndOfMonth() }
                .sumOf { it.valorCentavos },
            faturaAtual = faturasParaAtual.firstOrNull { it.ciclo == cicloAberto },
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

    private fun passaFiltro(mov: Movimentacao, filtro: FiltroLedger, tagId: Long? = null): Boolean {
        if (tagId != null && mov.tags.none { it.id == tagId }) return false
        return when (filtro) {
            FiltroLedger.TODAS -> true
            FiltroLedger.DIARIOS -> mov.recorrenciaId == null && mov.natureza == Natureza.DIARIO
            FiltroLedger.FIXAS -> mov.recorrenciaId != null
        }
    }

    private fun saldoReal(input: LedgerInput, efetivas: List<Movimentacao>, faturas: List<Fatura>, ate: LocalDate): Long =
        input.saldoInicialCentavos +
            efetivas.filter { it.natureza != Natureza.CARTAO && it.data <= ate }.sumOf { it.valorCentavos } +
            faturas.filter { it.vencimento <= ate }.sumOf { it.totalCentavos }

    /**
     * Saldo projetado ao fim de [mes]: saldo real se o mês já terminou (`estimativa` 0),
     * senão saldo real de hoje + agendadas/faturas futuras dentro do mês − estimativa.
     */
    /**
     * Gasto avulso ainda esperado no intervalo `(hoje, fim de mes]` — zero num mês que já
     * terminou. Fonte única: [mes] a publica como `estimativaCentavos` e [projetadoDoMes]
     * a desconta, e as duas contas têm de continuar sendo a mesma.
     */
    private fun estimativaDoMes(input: LedgerInput, mes: YearMonth): Long {
        val fimMes = mes.atEndOfMonth()
        return if (fimMes <= input.hoje) 0L
        else mediaDiaria(input) * ChronoUnit.DAYS.between(input.hoje, fimMes)
    }

    private fun projetadoDoMes(input: LedgerInput, efetivas: List<Movimentacao>, faturas: List<Fatura>, mes: YearMonth): Long {
        val fimMes = mes.atEndOfMonth()
        if (fimMes <= input.hoje) return saldoReal(input, efetivas, faturas, fimMes)
        val estimativa = estimativaDoMes(input, mes)
        val agendadas = efetivas
            .filter { it.natureza != Natureza.CARTAO && it.data > input.hoje && it.data <= fimMes }
            .sumOf { it.valorCentavos }
        val faturasFuturas = faturas
            .filter { it.vencimento > input.hoje && it.vencimento <= fimMes }
            .sumOf { it.totalCentavos }
        return saldoReal(input, efetivas, faturas, input.hoje) + agendadas + faturasFuturas - estimativa
    }

    /** Σ|one-off DIARIO saídas| in the 30 days ending today (never before saldoInicialData), ÷ 30. */
    private fun mediaDiaria(input: LedgerInput): Long {
        val inicioJanela = input.hoje.minusDays(29)
        val total = input.movimentacoes
            .filter {
                it.recorrenciaId == null && it.natureza == Natureza.DIARIO &&
                    it.valorCentavos < 0 && it.data >= inicioJanela && it.data <= input.hoje &&
                    it.data >= input.saldoInicialData
            }
            .sumOf { -it.valorCentavos }
        return total / 30
    }
}
