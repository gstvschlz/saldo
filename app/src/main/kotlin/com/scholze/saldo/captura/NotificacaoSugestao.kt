package com.scholze.saldo.captura

import android.Manifest
import android.app.Notification
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

    fun mostrar(context: Context, deteccao: Deteccao, rotulo: String, jaLancado: Boolean) {
        // Checagem inline (não via helper) para o lint enxergar a guarda de MissingPermission.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return
        NotificationManagerCompat.from(context)
            .notify(idDe(deteccao.id), construir(context, deteccao, rotulo, jaLancado))
    }

    fun cancelar(context: Context, deteccaoId: Long) {
        NotificationManagerCompat.from(context).cancel(idDe(deteccaoId))
    }

    /** Separado de [mostrar] para os testes lerem título e texto sem depender do sistema. */
    fun construir(context: Context, deteccao: Deteccao, rotulo: String, jaLancado: Boolean): Notification {
        val id = idDe(deteccao.id)
        val valor = "R$ " + deteccao.centavos.centavosValor()
        val texto = if (jaLancado) "já lançado hoje · lançar mesmo assim?" else "lançar como saída de hoje?"

        // Abrir a sheet já preenchida é a saída para tudo que a decisão de um toque não cobre:
        // trocar o sinal, mudar para cartão, pôr tag, corrigir a descrição.
        val abrir = PendingIntent.getActivity(
            context, id,
            MainActivity.intent(
                context,
                Destino.NovaMovimentacao(saida = true, centavos = deteccao.centavos, descricao = rotulo),
            ),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val publica = NotificationCompat.Builder(context, CANAL)
            .setSmallIcon(R.drawable.ic_notificacao)
            .setContentTitle("sugestão de lançamento")
            .build()

        return NotificationCompat.Builder(context, CANAL)
            .setSmallIcon(R.drawable.ic_notificacao)
            .setContentTitle("$rotulo · $valor")
            .setContentText(texto)
            .setStyle(NotificationCompat.BigTextStyle().bigText(texto))
            .setContentIntent(abrir)
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publica)
            .addAction(0, "lançar", acao(context, AcoesSugestao.ACAO_LANCAR, deteccao, rotulo, id * 2))
            .addAction(0, "ignorar", acao(context, AcoesSugestao.ACAO_IGNORAR, deteccao, rotulo, id * 2 + 1))
            .build()
    }

    /**
     * O rótulo do app viaja como extra em vez de ser consultado no receiver: ele roda noutro
     * momento e não tem por que enxergar o pacote que emitiu a notificação original.
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
