package com.scholze.saldo.model

import java.math.BigDecimal

private fun brl(v: String) = BigDecimal(v)

private fun variavel(descricao: String, valor: String) =
    Movimentacao(descricao, brl(valor), Categoria.VARIAVEL)

private fun fixa(descricao: String, valor: String) =
    Movimentacao(descricao, brl(valor), Categoria.FIXA)

/**
 * The ledger exactly as drawn in the design canvas (option 1a), so the
 * implementation can be compared against the mockup side by side.
 */
val mesDeExemplo = Mes(
    titulo = "julho 2026",
    anterior = "jun",
    proximo = "ago",
    projetadoEm = "saldo projetado · 31 jul",
    saldoProjetado = brl("120461.84"),
    deltaNoMes = brl("6506.38"),
    dias = listOf(
        DiaSaldo(
            dia = 14,
            movimentacoes = listOf(
                variavel("mercado", "-189.90"),
                variavel("uber", "-27.40"),
            ),
            saldo = brl("112338.16"),
        ),
        DiaSaldo(
            dia = 15,
            movimentacoes = listOf(fixa("salário", "8240.00")),
            saldo = brl("120578.16"),
        ),
        DiaSaldo(
            dia = 16,
            movimentacoes = listOf(
                fixa("aluguel", "-2400.00"),
                fixa("luz", "-187.32"),
            ),
            saldo = brl("117990.84"),
        ),
        DiaSaldo(
            dia = 17,
            movimentacoes = listOf(variavel("almoço", "-48.00")),
            saldo = brl("117942.84"),
        ),
        DiaSaldo(
            dia = 18,
            movimentacoes = listOf(
                fixa("fatura nubank", "-3412.08"),
                variavel("cinema", "-64.00"),
            ),
            saldo = brl("114466.76"),
        ),
        DiaSaldo(
            dia = 19,
            movimentacoes = listOf(fixa("reserva", "-1500.00")),
            saldo = brl("112966.76"),
        ),
        DiaSaldo(
            dia = 20,
            movimentacoes = listOf(
                variavel("farmácia", "-72.90"),
                variavel("mercado", "-238.50"),
            ),
            saldo = brl("112655.36"),
        ),
        DiaSaldo(
            dia = 21,
            movimentacoes = emptyList(),
            saldo = brl("112655.36"),
        ),
    ),
)
