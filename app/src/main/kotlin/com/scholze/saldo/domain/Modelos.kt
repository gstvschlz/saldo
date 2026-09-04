package com.scholze.saldo.domain

import java.time.LocalDate
import java.time.YearMonth

enum class Natureza { DIARIO, ECONOMIA, CARTAO }

enum class FiltroLedger(val rotulo: String) { TODAS("todas"), DIARIOS("diários"), FIXAS("fixas") }

data class Tag(val id: Long = 0, val nome: String, val cor: Long)

/**
 * O que se lê no lugar de uma descrição em branco.
 *
 * A descrição é opcional — pelo widget, ou aceitando uma sugestão de notificação, o normal
 * é registrar o valor e seguir a vida —, mas nenhuma tela pode mostrar um dinheiro ao lado
 * de um espaço vazio. Os exports guardam o vazio como está; isto é só leitura.
 */
const val SEM_DESCRICAO = "sem descrição"

/** A descrição como ela se lê: o texto, ou [SEM_DESCRICAO] quando ficou em branco. */
fun String.descricaoVisivel(): String = ifBlank { SEM_DESCRICAO }

data class Movimentacao(
    val id: Long = 0,
    val descricao: String,
    val valorCentavos: Long,          // signed; negative = saída
    val data: LocalDate,
    val natureza: Natureza,
    val recorrenciaId: Long? = null,
    val editadaManualmente: Boolean = false,
    val tags: List<Tag> = emptyList(),
    /**
     * Epoch millis de quando a linha foi criada; `0` = desconhecido (fixtures de teste, ocorrências
     * virtuais). É o sinal honesto de "o usuário lançou algo hoje" — a `data` não serve: uma
     * recorrência materializada hoje tem `data` de hoje sem ninguém ter lançado nada, e uma
     * despesa de ontem lançada hoje tem `data` de ontem.
     */
    val criadaEm: Long = 0,
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
