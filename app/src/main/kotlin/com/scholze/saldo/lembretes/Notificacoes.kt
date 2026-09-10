package com.scholze.saldo.lembretes

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.scholze.saldo.MainActivity
import com.scholze.saldo.R
import com.scholze.saldo.domain.Lembrete
import com.scholze.saldo.domain.Slot
import com.scholze.saldo.domain.descricaoVisivel
import com.scholze.saldo.ui.money.centavosAssinado
import com.scholze.saldo.ui.money.centavosValor
import com.scholze.saldo.ui.nav.Destino
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * Canal, ids fixos e um construtor por [Lembrete]. Valores sempre aparecem no texto; a versão
 * pública (tela bloqueada segura) leva só o título — é a decisão "sempre, mas privado na tela
 * de bloqueio". Ids fixos por tipo: uma rodada repetida substitui, não duplica.
 */
object Notificacoes {
    const val CANAL = "lembretes"
    const val ID_FATURA = 1001
    const val ID_RECORRENCIAS = 1002
    const val ID_FECHAMENTO = 1003
    const val ID_REGISTRAR = 1004
    const val ID_ETIQUETAR = 1005

    private val ptBr = Locale.forLanguageTag("pt-BR")
    private val diaDeMes = DateTimeFormatter.ofPattern("d 'de' MMMM", ptBr)

    fun criarCanal(context: Context) {
        NotificationManagerCompat.from(context).createNotificationChannel(
            NotificationChannelCompat.Builder(CANAL, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                .setName("lembretes")
                .setDescription("fatura, recorrências, registrar gastos, etiquetar e fechamento do mês")
                .build(),
        )
    }

    /**
     * Reflete tanto a permissão de runtime (13+) quanto o interruptor de notificações do app nas
     * configurações do sistema — falso em qualquer API se o usuário desligou as notificações do
     * app, e falso no 13+ sem a permissão concedida.
     */
    fun podeNotificar(context: Context): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled()

    /** Os ids que [construir] pode gerar para o [slot] — a lista que [sincronizar] varre para decidir o que cancelar. */
    fun idsDoSlot(slot: Slot): List<Int> = when (slot) {
        Slot.INFORMATIVOS -> listOf(ID_FATURA, ID_RECORRENCIAS, ID_FECHAMENTO)
        Slot.NUDGE -> listOf(ID_REGISTRAR, ID_ETIQUETAR)
    }

    /**
     * Uma rodada completa do [slot]: cancela todo id do slot que NÃO está entre os [lembretes]
     * desta vez — um lembrete que deixou de valer (a fatura foi paga, o gasto de hoje foi
     * lançado) não pode continuar pendurado na barra — e então mostra os que sobraram.
     */
    fun sincronizar(context: Context, slot: Slot, lembretes: List<Lembrete>) {
        val idsDaVez = lembretes.map { construir(context, it).first }.toSet()
        idsDoSlot(slot).filterNot { it in idsDaVez }.forEach { NotificationManagerCompat.from(context).cancel(it) }
        lembretes.forEach { mostrar(context, it) }
    }

    fun mostrar(context: Context, lembrete: Lembrete) {
        // Checagem inline (não via podeNotificar) para o lint enxergar a guarda de MissingPermission.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return
        val (id, notificacao) = construir(context, lembrete)
        NotificationManagerCompat.from(context).notify(id, notificacao)
    }

    /** Separado de [mostrar] para os testes lerem título/texto sem depender do que o sistema exibe. */
    fun construir(context: Context, lembrete: Lembrete): Pair<Int, Notification> = when (lembrete) {
        is Lembrete.FaturaAmanha -> {
            val titulo = "fatura do ${lembrete.nomeCartao} vence amanhã"
            ID_FATURA to notificacao(
                context, ID_FATURA,
                titulo = titulo,
                texto = "R$ " + lembrete.fatura.totalCentavos.centavosValor() +
                    " · vence " + lembrete.fatura.vencimento.format(diaDeMes),
                tituloPublico = titulo,
                destino = Destino.Saldos(YearMonth.from(lembrete.fatura.vencimento), lembrete.fatura.vencimento.dayOfMonth),
            )
        }
        is Lembrete.RecorrenciasHoje -> {
            val linhas = lembrete.itens.map {
                it.descricao.descricaoVisivel() + " " + it.valorCentavos.centavosAssinado()
            }
            ID_RECORRENCIAS to notificacao(
                context, ID_RECORRENCIAS,
                titulo = if (linhas.size == 1) "hoje: ${linhas.single()}" else "hoje: ${linhas.size} movimentações fixas",
                texto = linhas.joinToString(" · "),
                tituloPublico = "movimentações fixas de hoje",
                destino = Destino.Saldos(YearMonth.from(lembrete.dia), lembrete.dia.dayOfMonth),
            )
        }
        is Lembrete.EtiquetarHoje -> {
            val quantos = lembrete.quantos
            val titulo =
                if (quantos == 1) "1 lançamento de hoje está sem tag"
                else "$quantos lançamentos de hoje estão sem tag"
            ID_ETIQUETAR to notificacao(
                context, ID_ETIQUETAR,
                titulo = titulo,
                texto = "toque para etiquetar",
                // Sem valor no texto, então a versão pública é a mesma: uma contagem de linhas
                // não diz quanto se gastou.
                tituloPublico = titulo,
                destino = Destino.Saldos(YearMonth.from(lembrete.dia), lembrete.dia.dayOfMonth),
            )
        }
        Lembrete.RegistrarGastos -> ID_REGISTRAR to notificacao(
            context, ID_REGISTRAR,
            titulo = "registrar os gastos de hoje?",
            texto = "nada anotado hoje — toque para lançar",
            tituloPublico = "registrar os gastos de hoje?",
            destino = Destino.NovaMovimentacao(),
        )
        is Lembrete.FechamentoMes -> {
            val nomeMes = lembrete.mes.month.getDisplayName(TextStyle.FULL, ptBr)
            val verbo = if (lembrete.sobrouCentavos >= 0) "sobrou" else "faltou"
            ID_FECHAMENTO to notificacao(
                context, ID_FECHAMENTO,
                titulo = "$nomeMes fechou: $verbo R$ " + lembrete.sobrouCentavos.centavosValor(),
                texto = "entradas R$ " + lembrete.entradasCentavos.centavosValor() +
                    " · saídas R$ " + lembrete.saidasCentavos.centavosValor(),
                tituloPublico = "$nomeMes fechou",
                destino = Destino.Totais(lembrete.mes),
            )
        }
    }

    private fun notificacao(
        context: Context,
        id: Int,
        titulo: String,
        texto: String,
        tituloPublico: String,
        destino: Destino,
    ): Notification {
        val abrir = PendingIntent.getActivity(
            context, id, MainActivity.intent(context, destino),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val publica = NotificationCompat.Builder(context, CANAL)
            .setSmallIcon(R.drawable.ic_notificacao)
            .setContentTitle(tituloPublico)
            .build()
        return NotificationCompat.Builder(context, CANAL)
            .setSmallIcon(R.drawable.ic_notificacao)
            .setContentTitle(titulo)
            .setContentText(texto)
            .setStyle(NotificationCompat.BigTextStyle().bigText(texto))
            .setContentIntent(abrir)
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publica)
            .build()
    }
}
