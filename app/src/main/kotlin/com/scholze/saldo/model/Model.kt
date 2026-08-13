package com.scholze.saldo.model

import androidx.compose.runtime.Immutable
import java.math.BigDecimal

/** How a movimentação behaves against the projected balance. */
enum class Categoria { VARIAVEL, FIXA }

@Immutable
data class Movimentacao(
    val descricao: String,
    /** Negative for saída, positive for entrada. */
    val valor: BigDecimal,
    val categoria: Categoria,
)

/** One day of the ledger, with the balance projected to the end of that day. */
@Immutable
data class DiaSaldo(
    val dia: Int,
    val movimentacoes: List<Movimentacao>,
    val saldo: BigDecimal,
)

@Immutable
data class Mes(
    val titulo: String,
    val anterior: String,
    val proximo: String,
    /** "saldo projetado · 31 jul" */
    val projetadoEm: String,
    val saldoProjetado: BigDecimal,
    val deltaNoMes: BigDecimal,
    val dias: List<DiaSaldo>,
)

/** The three filters in the segmented control on the ledger. */
enum class FiltroLedger(val rotulo: String) {
    TODAS("todas"),
    DIARIOS("diários"),
    FIXAS("fixas"),
}

fun Mes.filtrado(filtro: FiltroLedger): Mes = when (filtro) {
    FiltroLedger.TODAS -> this
    FiltroLedger.DIARIOS -> filtrarPor(Categoria.VARIAVEL)
    FiltroLedger.FIXAS -> filtrarPor(Categoria.FIXA)
}

private fun Mes.filtrarPor(categoria: Categoria): Mes = copy(
    dias = dias.map { dia ->
        dia.copy(movimentacoes = dia.movimentacoes.filter { it.categoria == categoria })
    },
)
