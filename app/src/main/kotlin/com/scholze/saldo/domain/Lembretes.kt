package com.scholze.saldo.domain

import java.time.LocalTime

/**
 * Quais lembretes estão ligados e a que horas saem. Tudo desligado por padrão: lembrete é opt-in,
 * e ligar o primeiro é o que pede a permissão de notificação (ver `LembretesScreen`).
 */
data class LembretesConfig(
    val faturaAmanha: Boolean = false,
    val recorrenciaHoje: Boolean = false,
    val registrarGastos: Boolean = false,
    val fechamentoMes: Boolean = false,
    /** "3 lançamentos de hoje ainda estão sem tag" — sai no mesmo horário do registrar. */
    val etiquetarHoje: Boolean = false,
    /** Hora dos informativos: fatura vence amanhã, recorrência hoje, fechamento do mês. */
    val horaInformativos: LocalTime = LocalTime.of(9, 0),
    /** Hora do "registrar os gastos de hoje?". */
    val horaNudge: LocalTime = LocalTime.of(20, 0),
) {
    val algumInformativo: Boolean get() = faturaAmanha || recorrenciaHoje || fechamentoMes
    val algum: Boolean get() = algumInformativo || algumDoFimDoDia

    /** Os dois que saem no [horaNudge]: o slot só é agendado se ao menos um estiver ligado. */
    val algumDoFimDoDia: Boolean get() = registrarGastos || etiquetarHoje
    val ativos: Int
        get() = listOf(faturaAmanha, recorrenciaHoje, registrarGastos, fechamentoMes, etiquetarHoje).count { it }
}
