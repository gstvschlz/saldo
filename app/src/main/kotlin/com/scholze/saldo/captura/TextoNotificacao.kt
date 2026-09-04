package com.scholze.saldo.captura

import android.app.Notification
import android.os.Bundle
import androidx.core.os.BundleCompat

/**
 * Todo o texto de uma notificação, e não só o título.
 *
 * O valor pode estar em qualquer campo, e cada app escolhe um: o título ("Cartão XP · R$
 * 16,90"), o corpo, o texto grande do `BigTextStyle`, o subtítulo, ou — o caso que mais
 * escapava — as **linhas do `InboxStyle`**, que é como banco lista várias transações numa
 * notificação só. Nenhuma dessas é mais "a certa" que as outras, então lê-se todas e o
 * [com.scholze.saldo.domain.DetectorValor] procura no conjunto.
 *
 * As partes são juntadas com " · " e não concatenadas cruas: sem separador, o fim de um
 * campo colaria no começo do outro e inventaria número onde não havia ("...16" + "90..." =
 * "1690").
 *
 * **Sobre o texto:** ele vive nesta função e na avaliação que a chama, e não vai para o
 * banco — a `Deteccao` guarda pacote, valor e hora, mais nada. O único pedaço que sobrevive
 * é o nome do estabelecimento, e só quando o usuário toca em "lançar".
 */
internal fun textoDaNotificacao(extras: Bundle?): String {
    if (extras == null) return ""

    val simples = CAMPOS_DE_TEXTO.mapNotNull { extras.getCharSequence(it)?.toString() }

    // InboxStyle: cada transação numa linha.
    val linhas = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
        ?.mapNotNull { it?.toString() }
        .orEmpty()

    // MessagingStyle: um Bundle por mensagem, com o texto na chave "text". Vem de outro app,
    // então qualquer surpresa na estrutura vira lista vazia em vez de derrubar o serviço.
    val mensagens = runCatching {
        BundleCompat.getParcelableArray(extras, Notification.EXTRA_MESSAGES, Bundle::class.java)
            ?.mapNotNull { (it as? Bundle)?.getCharSequence("text")?.toString() }
            .orEmpty()
    }.getOrDefault(emptyList())

    return (simples + linhas + mensagens)
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        // Um app que repete o corpo no texto grande é a regra, não a exceção: sem isto, o
        // mesmo valor apareceria duas vezes no texto juntado.
        .distinct()
        .joinToString(" · ")
}

/**
 * Os campos de texto simples de uma notificação, na ordem em que a tela os mostra.
 *
 * `EXTRA_TITLE_BIG` e `EXTRA_CONVERSATION_TITLE` entram porque um `BigTextStyle` com título
 * próprio troca o título na expansão — e é justamente a versão expandida que costuma trazer
 * o valor por extenso.
 */
private val CAMPOS_DE_TEXTO = listOf(
    Notification.EXTRA_TITLE,
    Notification.EXTRA_TITLE_BIG,
    Notification.EXTRA_TEXT,
    Notification.EXTRA_BIG_TEXT,
    Notification.EXTRA_SUB_TEXT,
    Notification.EXTRA_SUMMARY_TEXT,
    Notification.EXTRA_INFO_TEXT,
    Notification.EXTRA_CONVERSATION_TITLE,
)
