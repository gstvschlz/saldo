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
    /** Hora dos informativos: fatura vence amanhã, recorrência hoje, fechamento do mês. */
    val horaInformativos: LocalTime = LocalTime.of(9, 0),
    /** Hora do "registrar os gastos de hoje?". */
    val horaNudge: LocalTime = LocalTime.of(20, 0),
) {
    val algumInformativo: Boolean get() = faturaAmanha || recorrenciaHoje || fechamentoMes
    val algum: Boolean get() = algumInformativo || registrarGastos
}
