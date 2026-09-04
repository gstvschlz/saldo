package com.scholze.saldo.domain

/**
 * Uma notificação com valor que o saldo viu.
 *
 * O que NÃO está aqui é o ponto: nem título, nem corpo, nem nome do app. O texto de uma
 * notificação existe em memória durante a avaliação e nunca chega ao disco — o que se guarda
 * é o mínimo para deduplicar e para lembrar do que já foi resolvido.
 *
 * [chave] é a identidade da notificação no Android (`StatusBarNotification.key`).
 */
data class Deteccao(
    val id: Long = 0,
    val pacote: String,
    val chave: String,
    val centavos: Long,
    val emMillis: Long,
    val resolvida: Boolean = false,
)

/**
 * [marcados] são os apps de que o usuário quer que o saldo leia; [vistos] são os que já
 * emitiram alguma notificação com valor e por isso aparecem na tela esperando a marcação.
 *
 * A lista é **descoberta e não enumerada**: listar os apps instalados no Android 11+ exige
 * `QUERY_ALL_PACKAGES`, permissão sensível da Play que contradiz a postura do app. O preço é
 * que o primeiro gasto de cada app novo passa batido — é ele que traz o app para a lista.
 */
data class CapturaConfig(
    val ligada: Boolean = false,
    val marcados: Set<String> = emptySet(),
    val vistos: Set<String> = emptySet(),
)

sealed interface Sugestao {
    /** Notificar do zero. */
    data class Nova(val deteccao: Deteccao) : Sugestao

    /** Notificar, mas avisando que um valor igual já foi lançado hoje. */
    data class JaLancado(val deteccao: Deteccao) : Sugestao

    /** A mesma compra de novo: atualiza a sugestão que já está na barra, não cria outra. */
    data class Repetida(val existente: Deteccao) : Sugestao

    /** Não é da nossa conta. */
    data object Ignorar : Sugestao
}
