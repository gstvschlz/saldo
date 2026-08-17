package com.scholze.saldo.domain

import java.time.DayOfWeek
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/** Uma fatia de "para onde foi": uma etiqueta, o resto agrupado, ou o que não tem etiqueta. */
sealed interface GrupoGasto {
    data class DeTag(val tag: Tag) : GrupoGasto
    data object Outras : GrupoGasto
    data object SemTag : GrupoGasto
}

/**
 * [share] é fração de `ParaOndeFoi.saidasCentavos` (0..1). [deltaPercent] compara com o mês
 * anterior: `null` = o mês anterior não teve nada nessa fatia ("novo"); `0` = variou menos de 1 %.
 * Só tem esse significado em `ParaOndeFoi.fatias` — em `ParaOndeFoi.barra` toda fatia carrega
 * `deltaPercent = null` sempre (não calculado ali, e não deve ser lido como "novo").
 */
data class Fatia(val grupo: GrupoGasto, val centavos: Long, val share: Float, val deltaPercent: Int?)

/**
 * [porDiaDaSemana]: média de avulsas DIARIO por dia da semana nas últimas 12 semanas.
 * [diaMaisCaro] `null` = menos de 14 dias de dados ("ainda sem padrão"), OU nenhuma saída avulsa
 * na janela (todo dia da semana soma zero — sem isso, `maxBy` apontaria SEGUNDA por ser a
 * primeira entrada do enum, um falso "dia mais caro" para um mês sem gasto avulso nenhum).
 * [avulsasPorDiaMes] `null` = mês futuro, ou o ledger não cobre nenhum dia do mês até o limite
 * (mês inteiro antes de `saldoInicialData`) — nos dois casos, nenhum dia decorrido.
 */
data class Padroes(
    val porDiaDaSemana: Map<DayOfWeek, Long>,
    val diaMaisCaro: DayOfWeek?,
    val avulsasPorDiaMes: Long?,
    val mediaDiaria30: Long,
)

/**
 * [fatias]: toda etiqueta com saída no mês (a movimentação conta em CADA tag dela) + sem tag;
 * [barra]: top 4 + outras + sem tag pela PRIMEIRA tag, para somar 100 %. `barra` não carrega
 * delta (todo item tem `Fatia.deltaPercent = null`) — a UI só renderiza deltas a partir de
 * [fatias].
 */
data class ParaOndeFoi(
    val saidasCentavos: Long,
    val fatias: List<Fatia>,
    val barra: List<Fatia>,
    val maioresGastos: List<Movimentacao>,
    val padroes: Padroes,
)

/**
 * As leituras da aba totais que não são o saldo em si: para onde foi, tendência, a caminho e
 * recorrências. Puro e determinístico como o [ProjectionEngine], que ele reaproveita — nenhuma
 * conta de saldo é refeita aqui.
 */
object InsightsEngine {

    fun paraOndeFoi(input: LedgerInput, mes: YearMonth): ParaOndeFoi {
        val saidas = ProjectionEngine.movimentacoesDoMes(input, mes).filter { it.valorCentavos < 0 }
        val saidasAnterior = ProjectionEngine.movimentacoesDoMes(input, mes.minusMonths(1)).filter { it.valorCentavos < 0 }
        val total = -saidas.sumOf { it.valorCentavos }

        val porTag = somaPorTag(saidas)
        val porTagAnterior = somaPorTag(saidasAnterior)
        val semTag = -saidas.filter { it.tags.isEmpty() }.sumOf { it.valorCentavos }
        val semTagAnterior = -saidasAnterior.filter { it.tags.isEmpty() }.sumOf { it.valorCentavos }

        val fatias = buildList {
            porTag.entries.sortedByDescending { it.value }.forEach { (tag, centavos) ->
                add(Fatia(GrupoGasto.DeTag(tag), centavos, share(centavos, total), delta(centavos, porTagAnterior[tag] ?: 0L)))
            }
            if (semTag > 0) add(Fatia(GrupoGasto.SemTag, semTag, share(semTag, total), delta(semTag, semTagAnterior)))
        }

        // Barra: cada movimentação uma vez só (primeira tag), então as fatias fecham em 100 %.
        val porPrimeiraTag = saidas.filter { it.tags.isNotEmpty() }
            .groupBy { it.tags.first() }
            .mapValues { (_, movs) -> -movs.sumOf { it.valorCentavos } }
            .entries.sortedByDescending { it.value }
        val barra = buildList {
            porPrimeiraTag.take(4).forEach { (tag, centavos) -> add(Fatia(GrupoGasto.DeTag(tag), centavos, share(centavos, total), null)) }
            val outras = porPrimeiraTag.drop(4).sumOf { it.value }
            if (outras > 0) add(Fatia(GrupoGasto.Outras, outras, share(outras, total), null))
            if (semTag > 0) add(Fatia(GrupoGasto.SemTag, semTag, share(semTag, total), null))
        }

        return ParaOndeFoi(
            saidasCentavos = total,
            fatias = fatias,
            barra = barra,
            maioresGastos = saidas.sortedBy { it.valorCentavos }.take(5),
            padroes = padroes(input, mes),
        )
    }

    // ---- padrões ----

    private const val JANELA_DIAS = 84L
    private const val MINIMO_DIAS_PADRAO = 14L

    private fun padroes(input: LedgerInput, mes: YearMonth): Padroes {
        val fim = input.hoje
        val inicio = maxOf(input.saldoInicialData, fim.minusDays(JANELA_DIAS - 1))
        val dias = ChronoUnit.DAYS.between(inicio, fim) + 1
        val avulsas = input.movimentacoes.filter { avulsaDiaria(it) }

        val porDia = DayOfWeek.entries.associateWith { dow ->
            val ocorrencias = (0 until dias).count { inicio.plusDays(it).dayOfWeek == dow }
            if (ocorrencias == 0) 0L
            else -avulsas.filter { it.data >= inicio && it.data <= fim && it.data.dayOfWeek == dow }
                .sumOf { it.valorCentavos } / ocorrencias
        }
        val diaMaisCaro = if (dias < MINIMO_DIAS_PADRAO || porDia.values.all { it == 0L }) null
        else porDia.maxBy { it.value }.key

        val mesAtual = YearMonth.from(input.hoje)
        // Fim da contagem: hoje se mes é o atual, fim do mês se mes já passou; mês futuro não tem dia decorrido.
        val fimContagem = when {
            mes > mesAtual -> null
            mes == mesAtual -> input.hoje
            else -> mes.atEndOfMonth()
        }
        // Início da contagem: só os dias que o ledger cobre — o mês pode ter começado antes de
        // saldoInicialData (ex.: ledger criado no meio do mês corrente), e contar desde o dia 1
        // do mês nesse caso subestima a média (menos gasto dividido por mais dias do que existiram).
        val inicioContagem = maxOf(mes.atDay(1), input.saldoInicialData)
        val diasDecorridos = if (fimContagem == null) 0L else ChronoUnit.DAYS.between(inicioContagem, fimContagem) + 1
        val avulsasPorDiaMes = if (diasDecorridos <= 0) null
        else -avulsas.filter { YearMonth.from(it.data) == mes && it.data >= input.saldoInicialData && it.data <= input.hoje }
            .sumOf { it.valorCentavos } / diasDecorridos

        return Padroes(porDia, diaMaisCaro, avulsasPorDiaMes, ProjectionEngine.mediaDiaria(input))
    }

    private fun avulsaDiaria(m: Movimentacao) =
        m.recorrenciaId == null && m.natureza == Natureza.DIARIO && m.valorCentavos < 0

    // ---- helpers ----

    private fun somaPorTag(saidas: List<Movimentacao>): Map<Tag, Long> =
        saidas.flatMap { m -> m.tags.map { it to -m.valorCentavos } }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, v) -> v.sum() }

    private fun share(centavos: Long, total: Long): Float = if (total == 0L) 0f else centavos.toFloat() / total

    private fun delta(atual: Long, anterior: Long): Int? {
        if (anterior == 0L) return null
        val percent = (atual - anterior) * 100.0 / anterior
        // Variação abaixo de 1% em módulo é ruído para quem lê — mostra "=" (0), não arredonda pra ±1%.
        return if (Math.abs(percent) < 1.0) 0 else Math.round(percent).toInt()
    }
}
