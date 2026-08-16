package com.scholze.saldo.ui.entry

import com.scholze.saldo.domain.CartaoConfig
import com.scholze.saldo.domain.FaturaCalculator
import com.scholze.saldo.domain.Natureza
import java.time.LocalDate

/**
 * Rodapé da sheet: em quanto o saldo do dia [dataForm] fica se esta movimentação for salva.
 *
 * [saldoDoDia] é o saldo corrente que o `ProjectionEngine` já calculou para [dataForm] — ou
 * seja, o ledger COMO ESTÁ, ainda com a linha original onde ela estava. O ajuste é "somar o
 * novo, descontar o velho", e uma regra só decide se cada um dos dois conta: uma movimentação
 * pesa no saldo de [dataForm] se o dia em que ela entra na coluna de saldo — a própria data,
 * ou, para o cartão, o vencimento da fatura do seu ciclo ([diaNoSaldo]) — não vem depois de
 * [dataForm]. Daí caem os casos que o cálculo ingênuo erra:
 *
 * - **Compra nova no cartão** vence depois de [dataForm] (o ciclo fecha em ou após a compra e
 *   vence depois do fechamento), então contribui com 0 — salvo a borda em que fechamento e
 *   vencimento clampam para o mesmo dia e a fatura vence no dia da compra.
 * - **Original no cartão** só é descontada quando a fatura dela JÁ venceu até [dataForm]: aí o
 *   saldo do dia carrega essa fatura, que a edição vai encolher. Antes do vencimento ela nunca
 *   esteve nesta coluna.
 * - **Mover para trás não desconta.** Se [dataForm] é anterior à data original, o saldo corrente
 *   em [dataForm] ainda não acumulou a linha original.
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
    cartao: CartaoConfig,
): Long {
    val entra = if (diaNoSaldo(dataForm, natureza, cartao) <= dataForm) valorAssinado else 0L
    val sai =
        if (dataOriginal != null && naturezaOriginal != null &&
            diaNoSaldo(dataOriginal, naturezaOriginal, cartao) <= dataForm
        ) valorOriginal else 0L
    return saldoDoDia + entra - sai
}

/** Dia em que uma movimentação passa a pesar na coluna de saldo: a data, ou o vencimento da fatura do cartão. */
private fun diaNoSaldo(data: LocalDate, natureza: Natureza, cartao: CartaoConfig): LocalDate =
    if (natureza == Natureza.CARTAO) {
        FaturaCalculator.vencimentoDoCiclo(FaturaCalculator.cicloDaCompra(data, cartao), cartao)
    } else {
        data
    }
