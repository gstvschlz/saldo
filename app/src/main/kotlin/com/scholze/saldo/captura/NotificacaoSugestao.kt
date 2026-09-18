package com.scholze.saldo.captura

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.scholze.saldo.MainActivity
import com.scholze.saldo.R
import com.scholze.saldo.domain.Deteccao
import com.scholze.saldo.ui.money.centavosValor
import com.scholze.saldo.ui.nav.Destino

/**
 * A sugestão na barra de status: um valor, dois botões e um toque que abre a sheet.
 *
 * Canal próprio, separado do de lembretes, para o usuário poder silenciar um sem o outro —
 * são coisas diferentes: um é o app cutucando no horário, o outro é reação a um gasto real.
 *
 * A versão pública (tela bloqueada) é mais fechada que a dos lembretes e mostra só "sugestão
 * de lançamento", sem valor. Um lembrete fala de dinheiro que já é seu; isto aqui é um dado
 * que acabou de chegar de outro app, e não vai para uma tela que qualquer um vê.
 */
object NotificacaoSugestao {
    const val CANAL = "sugestoes"

    /** Fora da faixa dos lembretes (1001–1004), que ocupam o começo. */
    private const val BASE_ID = 2000

    fun criarCanal(context: Context) {
        NotificationManagerCompat.from(context).createNotificationChannel(
            NotificationChannelCompat.Builder(CANAL, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                .setName("sugestões de lançamento")
                .setDescription("valores vistos nas notificações dos apps que você marcou")
                .build(),
        )
    }

    /** Uma sugestão por detecção: uma repetida renotifica no MESMO id, e por isso substitui. */
    fun idDe(deteccaoId: Long): Int = BASE_ID + (deteccaoId % 100_000L).toInt()

    fun mostrar(
        context: Context,
        deteccao: Deteccao,
        rotulo: String,
        descricao: String,
        jaLancado: Boolean,
    ) {
        // Checagem inline (não via helper) para o lint enxergar a guarda de MissingPermission.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return
        NotificationManagerCompat.from(context)
            .notify(idDe(deteccao.id), construir(context, deteccao, rotulo, descricao, jaLancado))
    }

    fun cancelar(context: Context, deteccaoId: Long) {
        NotificationManagerCompat.from(context).cancel(idDe(deteccaoId))
    }

    /**
     * Tira da barra toda sugestão pendente. Filtra pelo canal em vez de varrer os ids: as sugestões
     * são deste canal e de mais nenhum, e um `cancelAll` levaria os lembretes junto — que é uma
     * decisão de outra tela.
     */
    fun cancelarTodas(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        val nmc = NotificationManagerCompat.from(context)
        nm.activeNotifications
            .filter { it.notification.channelId == CANAL }
            .forEach { nmc.cancel(it.id) }
    }

    /**
     * Separado de [mostrar] para os testes lerem título e texto sem depender do sistema.
     *
     * [rotulo] é o nome do app e fica no título, porque é ele que diz DE ONDE a sugestão
     * veio. [descricao] é o estabelecimento lido do texto, e é o nome com que o lançamento
     * nasce — quando não houve nenhum reconhecível, quem chama já manda o rótulo aqui.
     */
    fun construir(
        context: Context,
        deteccao: Deteccao,
        rotulo: String,
        descricao: String,
        jaLancado: Boolean,
    ): Notification {
        val id = idDe(deteccao.id)
        val valor = "R$ " + deteccao.centavos.centavosValor()
        val pergunta = if (jaLancado) "já lançado hoje · lançar mesmo assim?" else "lançar como saída de hoje?"
        // O estabelecimento vem antes da pergunta: é o que faz reconhecer a compra sem abrir
        // nada. Quando ele é só o nome do app, repeti-lo no corpo seria eco do título.
        val texto = if (descricao == rotulo) pergunta else descricao + " · " + pergunta

        // Abrir a sheet já preenchida é a saída para tudo que a decisão de um toque não cobre:
        // trocar o sinal, mudar para cartão, pôr tag, corrigir a descrição.
        val abrir = PendingIntent.getActivity(
            context, id,
            MainActivity.intent(
                context,
                Destino.NovaMovimentacao(saida = true, centavos = deteccao.centavos, descricao = descricao),
            ),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val publica = NotificationCompat.Builder(context, CANAL)
            .setSmallIcon(R.drawable.ic_notificacao)
            .setColor(ContextCompat.getColor(context, R.color.ic_launcher_background))
            .setContentTitle("sugestão de lançamento")
            .build()

        return NotificationCompat.Builder(context, CANAL)
            .setSmallIcon(R.drawable.ic_notificacao)
            .setColor(ContextCompat.getColor(context, R.color.ic_launcher_background))
            .setContentTitle("$rotulo · $valor")
            .setContentText(texto)
            .setStyle(NotificationCompat.BigTextStyle().bigText(texto))
            .setContentIntent(abrir)
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publica)
            .addAction(0, "lançar", acao(context, AcoesSugestao.ACAO_LANCAR, deteccao, descricao, id * 2))
            .addAction(0, "ignorar", acao(context, AcoesSugestao.ACAO_IGNORAR, deteccao, descricao, id * 2 + 1))
            .build()
    }

    /**
     * A descrição viaja como extra em vez de ser recalculada no receiver: ele roda noutro
     * momento, e a essa altura o texto da notificação original já não existe em lugar nenhum
     * — nem no banco, que guarda só pacote, valor e hora.
     */
    private fun acao(
        context: Context,
        acao: String,
        deteccao: Deteccao,
        rotulo: String,
        requestCode: Int,
    ): PendingIntent = PendingIntent.getBroadcast(
        context,
        requestCode,
        Intent(context, AcoesSugestao::class.java)
            .setAction(acao)
            .putExtra(AcoesSugestao.EXTRA_DETECCAO, deteccao.id)
            .putExtra(AcoesSugestao.EXTRA_CENTAVOS, deteccao.centavos)
            .putExtra(AcoesSugestao.EXTRA_ROTULO, rotulo),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}
