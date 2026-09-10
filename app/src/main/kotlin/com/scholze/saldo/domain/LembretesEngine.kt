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
    /** [quantos] lançamentos de [dia] estão sem etiqueta; sempre >= 1 (zero não vira lembrete). */
    data class EtiquetarHoje(val dia: LocalDate, val quantos: Int) : Lembrete
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
        Slot.NUDGE -> buildList {
            if (config.registrarGastos && nadaCriadoHoje(input, zona)) add(Lembrete.RegistrarGastos)
            if (config.etiquetarHoje) etiquetarHoje(input)?.let(::add)
        }
    }

    /**
     * Os lançamentos de HOJE que ainda não têm etiqueta.
     *
     * O mesmo corte da fila de "sem tag" ([ProjectionEngine.MesLedger.semTag]), estreitado ao dia:
     * linha real (`id != 0`, uma ocorrência virtual não tem onde receber etiqueta) e fora do
     * cartão (uma compra de cartão não aparece como linha, ela entra no total da fatura). Zero
     * não vira lembrete — "hoje: nada a etiquetar" é ruído, e o app não manda notificação vazia.
     *
     * Convive com [Lembrete.RegistrarGastos] no mesmo slot sem se contradizer: aquele só sai
     * quando NADA foi criado hoje, este só quando alguma coisa foi — e ficou sem etiqueta.
     */
    private fun etiquetarHoje(input: LedgerInput): Lembrete.EtiquetarHoje? {
        val quantos = input.movimentacoes.count {
            it.data == input.hoje && it.id != 0L && it.tags.isEmpty() && it.natureza != Natureza.CARTAO
        }
        return if (quantos == 0) null else Lembrete.EtiquetarHoje(input.hoje, quantos)
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
