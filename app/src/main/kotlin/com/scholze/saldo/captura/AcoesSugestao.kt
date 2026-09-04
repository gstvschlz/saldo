package com.scholze.saldo.captura

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.scholze.saldo.SaldoApplication
import com.scholze.saldo.domain.Movimentacao
import com.scholze.saldo.domain.Natureza
import com.scholze.saldo.domain.RepetirOpcao
import java.time.LocalDate
import kotlinx.coroutines.launch

/**
 * Os dois botões da sugestão, resolvidos sem abrir o app.
 *
 * **"lançar" grava uma saída `DIARIO` de hoje**, sem heurística de sinal nem de conta ×
 * cartão — foi decisão explícita, com o risco conhecido: uma compra no cartão lançada assim
 * sai do saldo hoje e sai de novo quando a fatura vencer. Quem quiser o cartão abre a sheet
 * pelo corpo da notificação e troca lá, que é um toque a mais.
 *
 * "ignorar" só marca a detecção como resolvida; é isso que impede a mesma compra de voltar a
 * sugerir quando o app repostar o aviso dentro da janela.
 */
class AcoesSugestao : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val deteccaoId = intent.getLongExtra(EXTRA_DETECCAO, -1L).takeIf { it >= 0 } ?: return
        val acao = intent.action ?: return
        val centavos = intent.getLongExtra(EXTRA_CENTAVOS, 0L)
        val rotulo = intent.getStringExtra(EXTRA_ROTULO).orEmpty().ifBlank { "lançamento" }
        val container = (context.applicationContext as SaldoApplication).container

        // A notificação some já: quem tocou não deve esperar o banco para ver que funcionou.
        NotificacaoSugestao.cancelar(context, deteccaoId)

        val pendente = goAsync()
        container.scope.launch {
            try {
                if (acao == ACAO_LANCAR && centavos > 0) {
                    container.repository.criar(
                        Movimentacao(
                            descricao = rotulo,
                            valorCentavos = -centavos,
                            data = LocalDate.now(),
                            natureza = Natureza.DIARIO,
                        ),
                        RepetirOpcao.Nao,
                    )
                }
                container.database.deteccaoDao().resolver(deteccaoId)
            } catch (e: Exception) {
                Log.e("saldo", "ação da sugestão falhou", e)
            } finally {
                pendente.finish()
            }
        }
    }

    companion object {
        const val ACAO_LANCAR = "com.scholze.saldo.SUGESTAO_LANCAR"
        const val ACAO_IGNORAR = "com.scholze.saldo.SUGESTAO_IGNORAR"
        const val EXTRA_DETECCAO = "deteccao"
        const val EXTRA_CENTAVOS = "centavos"
        const val EXTRA_ROTULO = "rotulo"
    }
}
