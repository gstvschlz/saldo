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
) {
    /**
     * Até onde [efetivas] vale: o último mês já materializado, ou o mês de [hoje] mais doze — o
     * que for mais tarde. Cobre tudo que as telas pedem (o board olha treze meses; o ledger e
     * totais andam de mês em mês); quem passar disso — totais não tem limite para o futuro —
     * ganha uma expansão nova, calculada na hora.
     */
    val tetoExpansao: YearMonth
        get() = maxOf(
            mesesMaterializados.maxOrNull() ?: YearMonth.from(hoje),
            YearMonth.from(hoje).plusMonths(12),
        )

    /**
     * As movimentações efetivas — linhas materializadas mais as ocorrências virtuais das
     * recorrências —, do saldo inicial até [tetoExpansao], sem faturas.
     *
     * `by lazy`: uma emissão do ledger expande as recorrências UMA vez, e `mes`, `totais`,
     * `faturasAte`, `movimentacoesDoMes` e `movimentacoesAte` recortam esta lista por data em vez
     * de re-expandir cada uma por conta própria. `TotaisViewModel` sozinho fazia sete expansões
     * inteiras por emissão.
     */
    val efetivas: List<Movimentacao> by lazy { ProjectionEngine.expandir(this, tetoExpansao) }
}

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
     * Gasto avulso ainda esperado **dentro** do mês — de hoje até o fim dele no mês corrente, do
     * dia 1 ao fim num mês futuro —, e zero num mês que já terminou.
     *
     * No mês corrente é exatamente o que [saldoProjetadoCentavos] descontou. Num mês futuro a
     * projeção desconta mais do que isto, porque também paga os dias entre hoje e o dia 1 daquele
     * mês; ver `ProjectionEngine.estimativaAcumuladaAte`.
     */
    val estimativaCentavos: Long,
    val deltaNoMesCentavos: Long,
    val projetadoEm: LocalDate,
    /** Quanto do que entrou no mês foi para economia, em %; `null` num mês sem entrada. */
    val taxaGuardada: Int? = null,
    /**
     * Quantas linhas do mês estão sem etiqueta e já aconteceram — o número do chip `sem tag`.
     *
     * Conta exatamente o conjunto que a lista mostra sob [FiltroLedger.SEM_TAG]: linha real
     * (`id != 0`), sem etiqueta, `data <= hoje`, e que não seja compra no cartão. Uma compra de
     * cartão nunca aparece como linha no ledger — ela entra no total da fatura, no dia do
     * vencimento —, então contá-la faria o chip prometer um trabalho que a fila não sabe entregar.
     *
     * É do mês inteiro, não do que o filtro corrente mostra: o número do chip não pode mudar
     * conforme o chip que está aceso.
     */
    val semTag: Int = 0,
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
    fun mes(input: LedgerInput, mes: YearMonth, filtro: FiltroLedger, tagId: Long? = null): MesLedger =
        mes(input, mes, filtro, tagId, efetivas(input, mes))

    /**
     * A leitura "guardou N%": as saídas de natureza economia do mês sobre tudo que entrou.
     * Arredonda; sem entrada não há proporção (nulo, não zero); guardar mais do que entrou
     * — de saldo antigo — passa de cem, porque é verdade. É a única conta desta razão: a
     * tendência e o hero leem daqui e não podem discordar por um por cento.
     */
    fun taxaGuardada(entradasCentavos: Long, economiaCentavos: Long): Int? {
        if (entradasCentavos <= 0) return null
        return Math.round(economiaCentavos * 100.0 / entradasCentavos).toInt()
    }

    /** [efetivas] tem de ser exatamente `efetivas(input, mes)` — [totais] a reaproveita em vez de expandir de novo. */
    private fun mes(
        input: LedgerInput,
        mes: YearMonth,
        filtro: FiltroLedger,
        tagId: Long?,
        efetivas: List<Movimentacao>,
    ): MesLedger {
        val faturas = FaturaCalculator.faturas(efetivas, input.cartao)
        val fimMes = mes.atEndOfMonth()
        val fimAnterior = mes.minusMonths(1).atEndOfMonth()

        // A partição do mês inteiro, uma vez só: `movsDoMes` e a taxa guardada (abaixo)
        // reaproveitam em vez de varrer `efetivas` de novo cada um por conta própria.
        val doMes = efetivas.filter { YearMonth.from(it.data) == mes }

        // Nem `diários` nem `sem tag` mostram faturas: a primeira porque só quer as avulsas do dia
        // a dia, a segunda porque uma fatura é o agregado de um ciclo e não carrega etiqueta — ela
        // apareceria na fila sem nada que o usuário pudesse fazer com ela.
        val semFaturas = filtro == FiltroLedger.DIARIOS || filtro == FiltroLedger.SEM_TAG || tagId != null

        // A contagem da fila, uma vez, a partir da partição que já existe. `natureza != CARTAO` é
        // o mesmo corte que `movsDoMes` faz logo abaixo: o número do chip e o tamanho da lista têm
        // de ser a mesma coisa. O preço é uma compra de cartão sem etiqueta ficar fora da fila para
        // sempre — aceitável porque a captura de notificação, que é a fonte do buraco, grava DIARIO.
        val quantasSemTag = doMes.count {
            it.natureza != Natureza.CARTAO &&
                passaFiltro(it, FiltroLedger.SEM_TAG, tagId = null, hoje = input.hoje)
        }

        // Itens do mês segundo o filtro.
        val movsDoMes = doMes.asSequence()
            .filter { it.natureza != Natureza.CARTAO }
            .filter { passaFiltro(it, filtro, tagId, input.hoje) }
            .toList()
        val faturasDoMes =
            if (semFaturas) emptyList()
            else faturas.filter { YearMonth.from(it.vencimento) == mes }

        // Coluna de saldo corre sobre o conjunto filtrado.
        var corrente = input.saldoInicialCentavos +
            efetivas.filter {
                it.natureza != Natureza.CARTAO && it.data <= fimAnterior &&
                    passaFiltro(it, filtro, tagId, input.hoje)
            }.sumOf { it.valorCentavos } +
            (if (semFaturas) 0L else faturas.filter { it.vencimento <= fimAnterior }.sumOf { it.totalCentavos })

        // Agrupados uma vez, em vez de varrer o mês inteiro a cada dia; `groupBy` preserva a
        // ordem de `efetivas` (por data, estável), então a ordem dentro do dia não muda.
        val movsPorDia = movsDoMes.groupBy { it.data }
        val faturasPorDia = faturasDoMes.groupBy { it.vencimento }
        val dias = (1..mes.lengthOfMonth()).map { dia ->
            val data = mes.atDay(dia)
            val itens: List<ItemDia> =
                movsPorDia[data].orEmpty().map { ItemDia.Mov(it) } +
                    faturasPorDia[data].orEmpty().map { ItemDia.FaturaDia(it, input.cartao.nome) }
            corrente += itens.sumOf { it.valorCentavos }
            DiaRow(data, itens, corrente)
        }

        // Projeção (sempre sobre o conjunto completo, não o filtrado).
        val estimativa = estimativaDoMes(input, mes)
        val projetado = projetadoDoMes(input, efetivas, faturas, mes)
        val projetadoAnterior = projetadoDoMes(input, efetivas, faturas, mes.minusMonths(1))

        // Sempre sobre o mês inteiro (sem filtro): a pill do hero não muda com os chips.
        val entradas = doMes.filter { it.natureza != Natureza.CARTAO && it.valorCentavos > 0 }.sumOf { it.valorCentavos }
        val economia = -doMes.filter { it.natureza == Natureza.ECONOMIA && it.valorCentavos < 0 }.sumOf { it.valorCentavos }

        return MesLedger(
            mes = mes,
            dias = dias,
            saldoProjetadoCentavos = projetado,
            estimativaCentavos = estimativa,
            deltaNoMesCentavos = projetado - projetadoAnterior,
            projetadoEm = fimMes,
            taxaGuardada = taxaGuardada(entradas, economia),
            semTag = quantasSemTag,
        )
    }

    // Parameter deliberately NOT named `mes` — it would shadow the mes() function
    // and the call below would fail to resolve.
    fun totais(input: LedgerInput, mesAlvo: YearMonth): TotaisMes {
        // faturaAtual é sobre o ciclo aberto em `hoje`, não sobre mesAlvo — precisa de
        // efetivas expandidas até cobrir o mês do ciclo aberto, mesmo que mesAlvo seja passado.
        val cicloAberto = FaturaCalculator.cicloDaCompra(input.hoje, input.cartao)
        val mesCicloAberto = YearMonth.from(FaturaCalculator.fechamentoDoCiclo(cicloAberto, input.cartao))
        // Uma expansão só, até o mais tardio dos dois meses. O recorte até mesAlvo é um filtro
        // por data sobre ela: `efetivas(input, m)` é a lista completa cortada em fim(m), então
        // filtrar a lista maior dá exatamente a mesma lista, na mesma ordem.
        val efetivasAteCiclo = efetivas(input, maxOf(mesAlvo, mesCicloAberto))
        val fimMesAlvo = mesAlvo.atEndOfMonth()
        val efetivasAteMesAlvo = efetivasAteCiclo.filter { it.data <= fimMesAlvo }
        val doMes = efetivasAteMesAlvo.filter { YearMonth.from(it.data) == mesAlvo }
        val faturasParaAtual = FaturaCalculator.faturas(efetivasAteCiclo, input.cartao)
        val ledger = mes(input, mesAlvo, FiltroLedger.TODAS, tagId = null, efetivas = efetivasAteMesAlvo)

        return TotaisMes(
            entradasCentavos = doMes.filter { it.natureza != Natureza.CARTAO && it.valorCentavos > 0 }
                .sumOf { it.valorCentavos },
            saidasPorNatureza = Natureza.entries.associateWith { n ->
                -doMes.filter { it.natureza == n && it.valorCentavos < 0 }.sumOf { it.valorCentavos }
            },
            sobrouCentavos = ledger.deltaNoMesCentavos,
            economiaBucketCentavos = -efetivasAteMesAlvo
                .filter { it.natureza == Natureza.ECONOMIA }
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

    /**
     * Todas as faturas cujas compras cabem até o fim de [ateMes] — linhas materializadas e
     * expansões virtuais, como [mes] usa por dentro. É o que o `LembretesEngine` precisa para
     * achar "a fatura que vence amanhã" sem refazer a expansão por conta própria.
     */
    fun faturasAte(input: LedgerInput, ateMes: YearMonth): List<Fatura> =
        FaturaCalculator.faturas(efetivas(input, ateMes), input.cartao)

    /**
     * As movimentações efetivas de [mes] — linhas materializadas e expansões virtuais, sem
     * faturas. É a lista sobre a qual "para onde foi o dinheiro" conta: uma compra no cartão
     * conta no dia em que foi feita, não no vencimento da fatura.
     */
    fun movimentacoesDoMes(input: LedgerInput, mes: YearMonth): List<Movimentacao> =
        efetivas(input, mes).filter { YearMonth.from(it.data) == mes }

    /**
     * As movimentações efetivas do saldo inicial até o fim de [ateMes] — linhas
     * materializadas e expansões virtuais, sem faturas. É a mesma lista que
     * [movimentacoesDoMes] recorta num mês; o board precisa de treze meses de uma vez, e
     * chamar aquela treze vezes reexpandiria as recorrências treze vezes.
     */
    fun movimentacoesAte(input: LedgerInput, ateMes: YearMonth): List<Movimentacao> =
        efetivas(input, ateMes)

    // ---- internals ----

    /**
     * Linhas materializadas + expansões virtuais, do saldo inicial até o fim de [ateMes], sem
     * faturas. Pública só porque é o corpo de [LedgerInput.efetivas] — todo mundo mais lê a lista
     * pronta de lá, via [efetivas].
     */
    fun expandir(input: LedgerInput, ateMes: YearMonth): List<Movimentacao> {
        val fim = ateMes.atEndOfMonth()
        val reais = input.movimentacoes.filter { dentroDaAncora(it, input) && it.data <= fim }
        val virtuais = buildList {
            // Começa um mês ANTES da âncora: uma compra de cartão anterior ao saldo inicial cuja
            // fatura vence depois dele conta (ver `dentroDaAncora`), e uma recorrência de cartão
            // daquele mês seria perdida se a varredura começasse no mês da âncora.
            var m = YearMonth.from(input.saldoInicialData).minusMonths(1)
            while (m <= ateMes) {
                if (m !in input.mesesMaterializados) {
                    addAll(
                        RecurrenceExpander.ocorrenciasNoMes(input.recorrencias, m)
                            .filter { dentroDaAncora(it, input) },
                    )
                }
                m = m.plusMonths(1)
            }
        }
        return (reais + virtuais).sortedBy { it.data }
    }

    /**
     * A linha conta a partir do saldo inicial?
     *
     * `DIARIO` e `ECONOMIA`, pela data da movimentação — dinheiro que já saiu da conta antes da
     * âncora está embutido nela. `CARTAO`, pelo VENCIMENTO da fatura em que a compra cai: o saldo
     * inicial é "quanto tenho hoje" e a fatura aberta ainda não foi paga, então as compras dela
     * pesam no vencimento, tenham sido feitas antes ou depois da âncora.
     */
    private fun dentroDaAncora(mov: Movimentacao, input: LedgerInput): Boolean =
        if (mov.natureza == Natureza.CARTAO) {
            FaturaCalculator.vencimentoDoCiclo(
                FaturaCalculator.cicloDaCompra(mov.data, input.cartao),
                input.cartao,
            ) >= input.saldoInicialData
        } else {
            mov.data >= input.saldoInicialData
        }

    /** O recorte por data de [LedgerInput.efetivas]; além do teto, uma expansão nova. */
    private fun efetivas(input: LedgerInput, ateMes: YearMonth): List<Movimentacao> {
        if (ateMes > input.tetoExpansao) return expandir(input, ateMes)
        val fim = ateMes.atEndOfMonth()
        return input.efetivas.filter { it.data <= fim }
    }

    private fun passaFiltro(mov: Movimentacao, filtro: FiltroLedger, tagId: Long?, hoje: LocalDate): Boolean {
        if (tagId != null && mov.tags.none { it.id == tagId }) return false
        return when (filtro) {
            FiltroLedger.TODAS -> true
            FiltroLedger.DIARIOS -> mov.recorrenciaId == null && mov.natureza == Natureza.DIARIO
            FiltroLedger.FIXAS -> mov.recorrenciaId != null
            // `id != 0`: uma ocorrência virtual — a expansão de uma recorrência num mês ainda não
            // materializado — não tem linha no banco para receber etiqueta, e materializar o mês
            // inteiro só para etiquetar criaria linhas que ninguém pediu.
            // `data <= hoje`: o futuro entra na fila quando virar presente.
            FiltroLedger.SEM_TAG -> mov.id != 0L && mov.tags.isEmpty() && mov.data <= hoje
        }
    }

    private fun saldoReal(input: LedgerInput, efetivas: List<Movimentacao>, faturas: List<Fatura>, ate: LocalDate): Long =
        input.saldoInicialCentavos +
            efetivas.filter { it.natureza != Natureza.CARTAO && it.data <= ate }.sumOf { it.valorCentavos } +
            faturas.filter { it.vencimento <= ate }.sumOf { it.totalCentavos }

    /**
     * O que se MOSTRA: gasto avulso ainda esperado **dentro** de [mes] — zero num mês que já
     * terminou. A janela começa em `max(hoje, dia 1 do mês)`, então no mês corrente vai de hoje ao
     * fim dele e num mês futuro é o mês inteiro. Antes começava sempre em `hoje`, e a estimativa
     * de dezembro vista de setembro cobrava cem dias de gasto contra um mês de 31.
     *
     * É o [MesLedger.estimativaCentavos] e nada mais. Quem projeta saldo desconta
     * [estimativaAcumuladaAte], que é outra janela de propósito.
     */
    private fun estimativaDoMes(input: LedgerInput, mes: YearMonth): Long {
        val fimMes = mes.atEndOfMonth()
        if (fimMes <= input.hoje) return 0L
        return mediaDiaria(input) * ChronoUnit.DAYS.between(maxOf(input.hoje, mes.atDay(1)), fimMes)
    }

    /**
     * O que se DESCONTA: gasto avulso esperado de hoje até o fim de [mes] — a janela inteira,
     * atravessando os meses do caminho.
     *
     * [projetadoDoMes] parte do saldo real de HOJE e soma tudo que está agendado no intervalo
     * `(hoje, fim de mes]`; a estimativa que ele tira tem de cobrir esse mesmo intervalo. Usar
     * [estimativaDoMes] aqui fazia a projeção de dezembro vista de setembro esquecer setenta e
     * dois dias de gasto e ficar otimista em mais de sete mil reais.
     */
    private fun estimativaAcumuladaAte(input: LedgerInput, mes: YearMonth): Long {
        val fimMes = mes.atEndOfMonth()
        if (fimMes <= input.hoje) return 0L
        return mediaDiaria(input) * ChronoUnit.DAYS.between(input.hoje, fimMes)
    }

    /**
     * Saldo projetado ao fim de [mes]: saldo real se o mês já terminou, senão saldo real de hoje
     * + agendadas/faturas futuras dentro do intervalo `(hoje, fim de mes]` − a estimativa do
     * mesmo intervalo ([estimativaAcumuladaAte], não a do mês).
     */
    private fun projetadoDoMes(input: LedgerInput, efetivas: List<Movimentacao>, faturas: List<Fatura>, mes: YearMonth): Long {
        val fimMes = mes.atEndOfMonth()
        if (fimMes <= input.hoje) return saldoReal(input, efetivas, faturas, fimMes)
        val estimativa = estimativaAcumuladaAte(input, mes)
        val agendadas = efetivas
            .filter { it.natureza != Natureza.CARTAO && it.data > input.hoje && it.data <= fimMes }
            .sumOf { it.valorCentavos }
        val faturasFuturas = faturas
            .filter { it.vencimento > input.hoje && it.vencimento <= fimMes }
            .sumOf { it.totalCentavos }
        return saldoReal(input, efetivas, faturas, input.hoje) + agendadas + faturasFuturas - estimativa
    }

    /** Σ|one-off DIARIO saídas| in the 30 days ending today (never before saldoInicialData), ÷ 30. */
    fun mediaDiaria(input: LedgerInput): Long {
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
