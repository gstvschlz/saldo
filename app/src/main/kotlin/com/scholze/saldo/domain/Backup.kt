package com.scholze.saldo.domain

import java.time.LocalDate

/** De quanto em quanto tempo o backup automático grava. Às 03:00, ver `BackupScheduler.HORA`. */
enum class Cadencia(val rotulo: String) {
    DIARIO("diário"),
    SEMANAL("semanal"),
    MENSAL("mensal"),
}

/**
 * O backup automático como o usuário o configurou, mais o que a última tentativa deixou.
 *
 * [pastaUri] é a árvore escolhida com `OpenDocumentTree`, guardada como texto — a permissão é
 * persistida à parte, pelo sistema. É ela que liga o recurso: sem pasta não há o que gravar, e nada
 * é agendado.
 *
 * [ultimoErro] só existe quando a última tentativa falhou; um sucesso o limpa. É o que a linha da
 * tela mostra — a escolha do dono foi essa, e não uma notificação de falha. [ultimoErroEm] anda
 * junto porque "falhou" sem data não diz se foi ontem ou em março.
 */
data class BackupConfig(
    val pastaUri: String? = null,
    val cadencia: Cadencia = Cadencia.SEMANAL,
    val ultimoSucesso: LocalDate? = null,
    val ultimoErro: String? = null,
    val ultimoErroEm: LocalDate? = null,
) {
    val ligado: Boolean get() = pastaUri != null
}
