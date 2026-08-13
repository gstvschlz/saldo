package com.scholze.saldo.domain

import java.time.LocalDate
import java.time.YearMonth

enum class Natureza { DIARIO, ECONOMIA, CARTAO }

enum class FiltroLedger(val rotulo: String) { TODAS("todas"), DIARIOS("diários"), FIXAS("fixas") }

data class Tag(val id: Long = 0, val nome: String, val cor: Long)

data class Movimentacao(
    val id: Long = 0,
    val descricao: String,
    val valorCentavos: Long,          // signed; negative = saída
    val data: LocalDate,
    val natureza: Natureza,
    val recorrenciaId: Long? = null,
    val editadaManualmente: Boolean = false,
    val tags: List<Tag> = emptyList(),
)

data class Recorrencia(
    val id: Long = 0,
    val descricao: String,
    val valorCentavos: Long,
    val natureza: Natureza,
    val diaDoMes: Int,                // 1..31, clamped at expansion
    val inicio: YearMonth,
    val fim: YearMonth? = null,       // inclusive
    val ativa: Boolean = true,
    val tags: List<Tag> = emptyList(),
)

data class CartaoConfig(val nome: String = "cartão", val fechamentoDia: Int = 28, val vencimentoDia: Int = 5)

data class Fatura(val ciclo: YearMonth, val vencimento: LocalDate, val totalCentavos: Long, val compras: List<Movimentacao>)

sealed interface RepetirOpcao {
    data object Nao : RepetirOpcao
    data class TodoMes(val dia: Int) : RepetirOpcao
}

enum class EscopoEdicao { SO_ESTE_MES, DAQUI_EM_DIANTE }

enum class EscopoExclusao { SO_FUTURAS, TODAS }
