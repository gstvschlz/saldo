package com.scholze.saldo.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/** Uma fatia de "para onde foi": uma etiqueta, o resto agrupado, ou o que não tem etiqueta. */
sealed interface GrupoGasto {
    data class DeTag(val tag: Tag) : GrupoGasto
    data object Outras : GrupoGasto
    data object SemTag : GrupoGasto

    /**
     * Como esta fatia se chama na tela — e no TalkBack.
     *
     * Mora aqui, e não na tela, porque quem desenha a legenda e quem descreve o gráfico em voz
     * alta têm de chamar a mesma fatia da mesma coisa.
     */
    val nome: String
        get() = when (this) {
            is DeTag -> tag.nome
            Outras -> "outras"
            SemTag -> "sem tag"
        }
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
 * Um mês da série de tags: [valores] segue exatamente a ordem de [TagsNoTempo.grupos], com
 * zero onde aquele grupo não teve saída no mês.
 */
data class MesPorTag(val mes: YearMonth, val valores: List<Long>, val total: Long)

/**
 * "Para onde foi" ao longo do tempo: as mesmas fatias, mês a mês.
 *
 * [grupos] é decidido pela janela INTEIRA, não mês a mês, e é a mesma lista em todos os
 * meses — é isso que deixa a cor de uma etiqueta querer dizer a mesma coisa da primeira
 * coluna à última. Uma etiqueta que só apareceu num mês entra em `outras` se não estiver
 * entre as maiores da janela.
 *
 * Cada movimentação conta UMA vez, pela primeira etiqueta, como na barra do mês: assim as
 * colunas somam o total de saídas daquele mês em vez de mais que ele.
 */
data class TagsNoTempo(val grupos: List<GrupoGasto>, val meses: List<MesPorTag>)

/**
 * Um mês da tendência: totais fechados (ou projetados, para o mês corrente) e a reserva acumulada até ali.
 *
 * [entradas]/[saidas] e [sobrou] vêm de bases de data diferentes e não fecham entre si —
 * `entradas − saidas ≠ sobrou`, por design. [saidas] soma `TotaisMes.saidasPorNatureza` de
 * todas as naturezas na data da própria movimentação: uma compra no CARTAO conta no dia da
 * compra, não no vencimento, e ECONOMIA entra junto (o mesmo valor que [reservaAcumulada]
 * também soma). [sobrou] é `TotaisMes.sobrouCentavos`, o delta de saldo do mês: a fatura só
 * chega ali no vencimento, e num mês aberto (o corrente) já sai descontada a estimativa do
 * gasto avulso restante.
 */
data class PontoMes(
    val mes: YearMonth,
    val entradas: Long,
    val saidas: Long,
    val sobrou: Long,
    val reservaAcumulada: Long,
    /** ECONOMIA do mês ÷ entradas do mês, em %; `null` sem entradas. */
    val taxaPoupanca: Int?,
)

data class ItemFuturo(val data: LocalDate, val item: ItemDia)

/** O que ainda passa pela coluna de saldo depois de hoje até o fim do mês visto. */
data class ACaminho(
    val saemCentavos: Long,
    val entramCentavos: Long,
    val itens: List<ItemFuturo>,
    /** O mês visto já terminou: nada a caminho, por definição. */
    val mesEncerrado: Boolean,
)

/**
 * [ativas]: templates cujo fim não passou (pausados incluídos; inclui os que só começam
 * depois); [entramMes]/[saemMes] somam só os vigentes no mês (`ativo && inicio ≤ mês`).
 */
data class ResumoRecorrencias(
    val ativas: List<Recorrencia>,
    val encerradas: List<Recorrencia>,
    val entramMes: Long,
    val saemMes: Long,
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

    /**
     * As fatias de "para onde foi" repetidas nos últimos [meses] meses até [ateMes].
     *
     * As etiquetas são agrupadas por **id**, e não pelo objeto: renomear ou recolorir uma
     * etiqueta no meio da janela partiria a série em duas se a igualdade fosse a do data
     * class. O nome exibido é o do mês mais recente em que ela apareceu.
     */
    fun tagsAoLongoDoTempo(
        input: LedgerInput,
        ateMes: YearMonth,
        meses: Int = 6,
        maioresTags: Int = 4,
    ): TagsNoTempo {
        val janela = (meses - 1 downTo 0).map { ateMes.minusMonths(it.toLong()) }

        // Por mês: id da primeira etiqueta -> centavos, e o que não tem etiqueta nenhuma.
        val porMes = janela.map { mes ->
            val saidas = ProjectionEngine.movimentacoesDoMes(input, mes).filter { it.valorCentavos < 0 }
            val porTag = saidas.filter { it.tags.isNotEmpty() }
                .groupBy { it.tags.first().id }
                .mapValues { (_, movs) -> -movs.sumOf { it.valorCentavos } }
            val semTag = -saidas.filter { it.tags.isEmpty() }.sumOf { it.valorCentavos }
            Triple(mes, porTag, semTag)
        }

        // O nome e a cor vêm da aparição mais recente: a janela é percorrida em ordem, então
        // o último put é o mais novo.
        val etiquetas = mutableMapOf<Long, Tag>()
        janela.forEach { mes ->
            ProjectionEngine.movimentacoesDoMes(input, mes)
                .filter { it.valorCentavos < 0 }
                .forEach { mov -> mov.tags.firstOrNull()?.let { etiquetas[it.id] = it } }
        }

        val totalPorId = mutableMapOf<Long, Long>()
        porMes.forEach { (_, porTag, _) ->
            porTag.forEach { (id, centavos) -> totalPorId[id] = (totalPorId[id] ?: 0L) + centavos }
        }

        val maiores = totalPorId.entries.sortedByDescending { it.value }.take(maioresTags).map { it.key }
        val temOutras = totalPorId.keys.any { it !in maiores }
        val temSemTag = porMes.any { (_, _, semTag) -> semTag > 0 }

        val grupos = buildList {
            maiores.forEach { id -> etiquetas[id]?.let { add(GrupoGasto.DeTag(it)) } }
            if (temOutras) add(GrupoGasto.Outras)
            if (temSemTag) add(GrupoGasto.SemTag)
        }

        val serie = porMes.map { (mes, porTag, semTag) ->
            val valores = grupos.map { grupo ->
                when (grupo) {
                    is GrupoGasto.DeTag -> porTag[grupo.tag.id] ?: 0L
                    GrupoGasto.Outras -> porTag.filterKeys { it !in maiores }.values.sum()
                    GrupoGasto.SemTag -> semTag
                }
            }
            MesPorTag(mes, valores, valores.sum())
        }

        return TagsNoTempo(grupos, serie)
    }

    // ---- tendência ----

    /** Os [meses] meses até [ateMes], inclusive; cada ponto vem de [ProjectionEngine.totais]. */
    fun tendencia(input: LedgerInput, ateMes: YearMonth, meses: Int = 6): List<PontoMes> =
        (meses - 1 downTo 0).map { ateMes.minusMonths(it.toLong()) }.map { m ->
            val t = ProjectionEngine.totais(input, m)
            val economia = t.saidasPorNatureza[Natureza.ECONOMIA] ?: 0L
            PontoMes(
                mes = m,
                entradas = t.entradasCentavos,
                saidas = t.saidasPorNatureza.values.sum(),
                sobrou = t.sobrouCentavos,
                reservaAcumulada = t.economiaBucketCentavos,
                taxaPoupanca = ProjectionEngine.taxaGuardada(t.entradasCentavos, economia),
            )
        }

    // ---- a caminho ----

    /** Itens datados DEPOIS de hoje até o fim de [mes], tirados das linhas de dia do próprio ledger. */
    fun aCaminho(input: LedgerInput, mes: YearMonth): ACaminho {
        if (!mes.atEndOfMonth().isAfter(input.hoje)) return ACaminho(0L, 0L, emptyList(), mesEncerrado = true)
        val itens = ProjectionEngine.mes(input, mes, FiltroLedger.TODAS).dias
            .filter { it.data > input.hoje }
            .flatMap { dia -> dia.itens.map { ItemFuturo(dia.data, it) } }
        return ACaminho(
            saemCentavos = -itens.filter { it.item.valorCentavos < 0 }.sumOf { it.item.valorCentavos },
            entramCentavos = itens.filter { it.item.valorCentavos > 0 }.sumOf { it.item.valorCentavos },
            itens = itens,
            mesEncerrado = false,
        )
    }

    // ---- recorrências ----

    /**
     * Resumo das recorrências vistas em [mes]. Note a assimetria: [ResumoRecorrencias.ativas] inclui
     * templates com `inicio` DEPOIS de [mes] (uma recorrência que só começa em setembro já é uma
     * despesa fixa ativa, mesmo olhando julho), mas `entramMes`/`saemMes` somam só os VIGENTES
     * (`inicio ≤ mes`) — em julho, aquela recorrência de setembro ainda não moveu dinheiro nenhum.
     */
    fun recorrencias(input: LedgerInput, mes: YearMonth): ResumoRecorrencias {
        // Pausada (`ativa = false`) não é encerrada: continua na lista, com o interruptor, e só
        // sai das somas. Encerrada é a que tem `fim` antes do mês visto.
        val (ativas, encerradas) = input.recorrencias.partition { r -> r.fim?.let { it >= mes } ?: true }
        val vigentes = ativas.filter { it.ativa && it.inicio <= mes }
        return ResumoRecorrencias(
            ativas = ativas.sortedBy { it.diaDoMes },
            encerradas = encerradas.sortedBy { it.diaDoMes },
            entramMes = vigentes.filter { it.valorCentavos > 0 }.sumOf { it.valorCentavos },
            saemMes = -vigentes.filter { it.valorCentavos < 0 }.sumOf { it.valorCentavos },
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
