package com.scholze.saldo.ui.entry

import com.scholze.saldo.domain.Natureza
import java.time.LocalDate

/**
 * Rodapé da sheet: em quanto o saldo do dia [dataForm] fica se esta movimentação for salva.
 *
 * [saldoDoDia] é o saldo corrente que o `ProjectionEngine` já calculou para [dataForm] — ou
 * seja, o ledger COMO ESTÁ, ainda com a linha original onde ela estava. O ajuste é, então,
 * "somar o novo, descontar o velho", com duas ressalvas que o cálculo ingênuo erra:
 *
 * - **Cartão não entra na coluna de saldo.** Uma compra no cartão pesa na fatura, num dia
 *   futuro; ela não muda o saldo do dia da compra. Logo o novo valor contribui com 0 quando
 *   [natureza] é [Natureza.CARTAO], e o valor antigo não é descontado quando
 *   [naturezaOriginal] era CARTAO (ele nunca esteve nessa coluna para começar).
 * - **Mover para trás não desconta.** Se a edição joga a movimentação para uma data ANTERIOR
 *   à original, o saldo corrente em [dataForm] ainda não acumulou a linha original (ela vem
 *   depois) — não há o que descontar. Só se desconta quando [dataForm] >= [dataOriginal].
 *
 * Numa movimentação nova, [dataOriginal] e [naturezaOriginal] são `null` e [valorOriginal] 0.
 */
fun saldoResultante(
    saldoDoDia: Long,
    natureza: Natureza,
    valorAssinado: Long,
    naturezaOriginal: Natureza?,
    valorOriginal: Long,
    dataForm: LocalDate,
    dataOriginal: LocalDate?,
): Long {
    val entra = if (natureza == Natureza.CARTAO) 0L else valorAssinado
    val sai =
        if (naturezaOriginal == Natureza.CARTAO || dataOriginal == null || dataForm < dataOriginal) 0L
        else valorOriginal
    return saldoDoDia + entra - sai
}
