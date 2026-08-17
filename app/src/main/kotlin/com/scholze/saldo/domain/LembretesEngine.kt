package com.scholze.saldo.domain

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/** Os dois horários diários em que os lembretes rodam (ver `LembretesConfig.horaInformativos` / `horaNudge`). */
enum class Slot { INFORMATIVOS, NUDGE }

sealed interface Lembrete {
    data class FaturaAmanha(val fatura: Fatura, val nomeCartao: String) : Lembrete
    /** As linhas fixas de [dia] (hoje): recorrências e uma fatura que vença nesse dia. */
    data class RecorrenciasHoje(val dia: LocalDate, val itens: List<ItemDia>) : Lembrete
    data object RegistrarGastos : Lembrete
    data class FechamentoMes(
        val mes: YearMonth,
        val sobrouCentavos: Long,
        val entradasCentavos: Long,
        val saidasCentavos: Long,
    ) : Lembrete
}

/**
 * Decide quais lembretes saem num [Slot], a partir do mesmo `LedgerInput` que o ledger usa.
 * Puro e determinístico: toda regra de lembrete mora aqui e em nenhum outro lugar. Cada regra
 * é gated pelo seu toggle e só emite quando há algo a dizer — sem "sobrou R$ 0,00" nem
 * "hoje: nada".
 */
object LembretesEngine {

    fun avaliar(
        input: LedgerInput,
        config: LembretesConfig,
        slot: Slot,
        zona: ZoneId = ZoneId.systemDefault(),
    ): List<Lembrete> = when (slot) {
        Slot.INFORMATIVOS -> buildList {
            if (config.faturaAmanha) faturaAmanha(input)?.let(::add)
            if (config.recorrenciaHoje) recorrenciasHoje(input)?.let(::add)
            if (config.fechamentoMes) fechamentoMes(input)?.let(::add)
        }
        Slot.NUDGE ->
            if (config.registrarGastos && nadaCriadoHoje(input, zona)) listOf(Lembrete.RegistrarGastos) else emptyList()
    }

    /** A fatura com vencimento em hoje+1, se tiver compras. */
    private fun faturaAmanha(input: LedgerInput): Lembrete.FaturaAmanha? {
        val amanha = input.hoje.plusDays(1)
        val fatura = ProjectionEngine.faturasAte(input, YearMonth.from(amanha))
            .firstOrNull { it.vencimento == amanha } ?: return null
        if (fatura.totalCentavos == 0L) return null
        return Lembrete.FaturaAmanha(fatura, input.cartao.nome)
    }

    /** As linhas fixas do dia de hoje — recorrências e uma fatura que vença hoje. */
    private fun recorrenciasHoje(input: LedgerInput): Lembrete.RecorrenciasHoje? {
        val ledger = ProjectionEngine.mes(input, YearMonth.from(input.hoje), FiltroLedger.TODAS)
        val itens = ledger.dias.getOrNull(input.hoje.dayOfMonth - 1)?.itens.orEmpty().filter { it.recorrente }
        return if (itens.isEmpty()) null else Lembrete.RecorrenciasHoje(input.hoje, itens)
    }

    /** Só no dia 1, sobre o mês que acabou de fechar; um mês sem movimentação nenhuma não avisa. */
    private fun fechamentoMes(input: LedgerInput): Lembrete.FechamentoMes? {
        if (input.hoje.dayOfMonth != 1) return null
        val mes = YearMonth.from(input.hoje).minusMonths(1)
        val t = ProjectionEngine.totais(input, mes)
        val saidas = t.saidasPorNatureza.values.sum()
        if (t.entradasCentavos == 0L && saidas == 0L) return null
        return Lembrete.FechamentoMes(mes, t.sobrouCentavos, t.entradasCentavos, saidas)
    }

    /**
     * "Nada lançado hoje" = nenhuma avulsa (`recorrenciaId == null`) CRIADA hoje. A data da
     * movimentação não serve (ver `Movimentacao.criadaEm`), e instância de recorrência não conta:
     * abrir o mês materializa linhas com `criadaEm` de hoje sem ninguém ter lançado nada.
     */
    private fun nadaCriadoHoje(input: LedgerInput, zona: ZoneId): Boolean =
        input.movimentacoes.none { m ->
            m.recorrenciaId == null && m.criadaEm != 0L &&
                Instant.ofEpochMilli(m.criadaEm).atZone(zona).toLocalDate() == input.hoje
        }
}
