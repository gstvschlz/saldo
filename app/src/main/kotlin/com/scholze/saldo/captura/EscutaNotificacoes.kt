package com.scholze.saldo.captura

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.scholze.saldo.SaldoApplication
import com.scholze.saldo.domain.Deteccao
import com.scholze.saldo.domain.DetectorValor
import com.scholze.saldo.domain.ProjectionEngine
import com.scholze.saldo.domain.Sugestao
import com.scholze.saldo.domain.SugestaoEngine
import com.scholze.saldo.data.db.toDomain
import com.scholze.saldo.data.db.toEntity
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.abs
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * A casca. Lê a notificação, pergunta ao [SugestaoEngine] e obedece — nenhuma regra de
 * produto mora aqui, pela mesma razão que nenhuma mora no `LembretesWorker`.
 *
 * **Sobre o texto:** ligado o acesso, o Android entrega a este serviço o texto de toda
 * notificação do aparelho, e não há como pedir menos. O que o saldo controla é o que faz com
 * o que chega: o texto vive na variável local [textoDe] e morre no fim do método. O único
 * vestígio de um app não marcado é o nome do pacote entrando na lista de vistos — que é o que
 * faz ele aparecer na tela esperando a sua marcação.
 */
class EscutaNotificacoes : NotificationListenerService() {

    private val container get() = (applicationContext as SaldoApplication).container

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        // A própria sugestão do saldo tem "R$" no título: sem esta guarda o app se lê e
        // realimenta a si mesmo para sempre.
        if (sbn.packageName == packageName) return
        // O sumário de um grupo repete o texto dos filhos; contá-lo duplicaria a compra.
        if (sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return

        val texto = textoDe(sbn.notification)
        if (texto.isBlank()) return
        val centavos = DetectorValor.primeiroValorEmCentavos(texto) ?: return

        val pacote = sbn.packageName
        val chave = sbn.key
        val agora = System.currentTimeMillis()
        // onNotificationPosted roda na main thread do serviço e não pode tocar em Room.
        container.scope.launch {
            try {
                avaliar(pacote, chave, centavos, agora)
            } catch (e: Exception) {
                Log.e(TAG, "avaliar notificação falhou", e)
            }
        }
    }

    private suspend fun avaliar(pacote: String, chave: String, centavos: Long, agora: Long) {
        val config = container.settings.lerCaptura()
        if (!config.ligada) return
        if (pacote !in config.marcados) {
            container.settings.registrarAppVisto(pacote)
            return
        }

        val dao = container.database.deteccaoDao()
        dao.limpar(agora - VALIDADE_MILLIS)
        val recentes = dao.desde(agora - SugestaoEngine.JANELA_MILLIS).map { it.toDomain() }

        val hoje = LocalDate.now()
        val valoresDeHoje = ProjectionEngine
            .movimentacoesDoMes(container.repository.ledger.first(), YearMonth.from(hoje))
            .filter { it.data == hoje }
            .map { abs(it.valorCentavos) }

        val candidata = Deteccao(pacote = pacote, chave = chave, centavos = centavos, emMillis = agora)
        val rotulo = rotuloDe(pacote)

        when (val sugestao = SugestaoEngine.avaliar(candidata, config, recentes, valoresDeHoje)) {
            Sugestao.Ignorar -> Unit
            // Mesmo id de notificação: substitui a que já está na barra em vez de empilhar.
            is Sugestao.Repetida ->
                NotificacaoSugestao.mostrar(this, sugestao.existente, rotulo, jaLancado = false)
            is Sugestao.Nova -> {
                val id = dao.insert(candidata.toEntity())
                NotificacaoSugestao.mostrar(this, candidata.copy(id = id), rotulo, jaLancado = false)
            }
            is Sugestao.JaLancado -> {
                val id = dao.insert(candidata.toEntity())
                NotificacaoSugestao.mostrar(this, candidata.copy(id = id), rotulo, jaLancado = true)
            }
        }
    }

    /** Título, texto e o texto grande, juntos — o valor pode estar em qualquer um deles. */
    private fun textoDe(notificacao: Notification): String {
        val extras = notificacao.extras ?: return ""
        return listOf(
            Notification.EXTRA_TITLE,
            Notification.EXTRA_TEXT,
            Notification.EXTRA_BIG_TEXT,
        ).mapNotNull { extras.getCharSequence(it)?.toString() }
            .filter { it.isNotBlank() }
            .joinToString(" · ")
    }

    /** O nome que o usuário lê. Consulta por pacote, que não exige `QUERY_ALL_PACKAGES`. */
    private fun rotuloDe(pacote: String): String = runCatching {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(pacote, 0)).toString()
    }.getOrDefault(pacote)

    companion object {
        private const val TAG = "saldo"

        /** Detecção some depois de 24 h: ela existe para deduplicar, não para virar histórico. */
        private const val VALIDADE_MILLIS = 24 * 60 * 60 * 1000L
    }
}
