package com.scholze.saldo.backup

import android.content.Context
import android.util.Log
import androidx.core.net.toUri
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.scholze.saldo.BuildConfig
import com.scholze.saldo.SaldoApplication
import com.scholze.saldo.data.Dump
import com.scholze.saldo.data.Exporters
import java.time.LocalDate
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.first

/**
 * Uma gravação do backup automático, na ordem: lê ledger e ajustes UMA vez → monta o JSON (o mesmo
 * `Exporters.json` do "exportar dados") → grava `saldo-AAAA-MM-DD.json.parcial` → renomeia →
 * rotaciona → grava o estado → reagenda.
 *
 * O `.parcial` existe porque uma escrita interrompida deixa um arquivo que PARECE válido, e um
 * backup pela metade é pior que backup nenhum. Só o rename — atômico do ponto de vista de quem
 * lê — publica o arquivo do dia.
 *
 * Três finais, três significados:
 * - **sucesso**: grava o último sucesso, limpa o erro e reagenda para a próxima ocorrência.
 * - **erro de I/O**: grava o motivo na linha da tela e devolve `retry` SEM reagendar — o retry do
 *   próprio WorkManager (backoff exponencial) mantém o unique work vivo, e reagendar aqui inseriria
 *   um segundo pedido no mesmo nome.
 * - **`SecurityException`** (permissão revogada, pasta apagada): desliga o backup, troca a linha por
 *   "escolha a pasta de novo" e NÃO reagenda. Um agendamento que não pode gravar não deve tentar
 *   para sempre em silêncio. Vale para a rodada manual também: se a pasta morreu, morreu para as duas.
 *
 * A rodada do botão "agora" ([CHAVE_MANUAL]) faz tudo isso menos reagendar — ver o comentário no
 * ponto onde ela é lida.
 */
class BackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as SaldoApplication).container
        // `lerBackup()`, e NÃO o flow `settings`: aquele tem um `.catch` que cai nos defaults num
        // disco ilegível, de propósito, para não travar a primeira composição. Por ele, "não deu
        // para ler" chegaria aqui como `pastaUri = null` — indistinguível de "o usuário não
        // escolheu pasta" —, o worker sairia em silêncio sem reagendar e o trabalho único morreria
        // até o próximo arranque do app. Ilegível é infraestrutura: `retry`, como no LembretesWorker.
        val config = try {
            container.settings.lerBackup()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "backup: ajustes ilegíveis", e)
            return Result.retry()
        }
        // Desligado (ou nunca ligado): não é erro, é a configuração do usuário.
        val uri = config.pastaUri ?: return Result.success()
        val pasta = container.pastaBackup(uri.toUri())
        val hoje = LocalDate.now()
        val parcial = NomeBackup.parcial(hoje)
        val definitivo = NomeBackup.de(hoje)

        try {
            // Só agora o resto: se não há pasta, nem o ledger precisa ser lido.
            val texto = Exporters.json(
                Dump.de(
                    input = container.repository.ledger.first(),
                    tags = container.repository.tags.first(),
                    settings = container.settings.settings.first(),
                    app = BuildConfig.VERSION_NAME,
                ),
            )
            pasta.criar(parcial, texto)
            pasta.renomear(parcial, definitivo)
            NomeBackup.aApagar(pasta.listar()).forEach { pasta.apagar(it) }
            container.settings.registrarBackupOk(hoje)
        } catch (e: CancellationException) {
            throw e
        } catch (e: SecurityException) {
            Log.e(TAG, "backup: a pasta não é mais acessível", e)
            container.settings.desligarBackup("escolha a pasta de novo", hoje)
            return Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "backup falhou", e)
            // O `.parcial` que sobrou não engana ninguém (não casa com o padrão), mas também não
            // precisa ficar sujando a pasta do usuário.
            tentar("apagar o parcial") { pasta.apagar(parcial) }
            tentar("registrar o erro") { container.settings.registrarBackupErro(e.message ?: "não deu para gravar", hoje) }
            return Result.retry()
        }

        // A rodada do botão "agora" não reagenda: ela roda sob outro trabalho único
        // (`BackupScheduler.NOME_AGORA`), então o APPEND_OR_REPLACE do `reagendar` — que só
        // substitui a cadeia quando é chamado de DENTRO dela — empilharia mais um nó na cadeia do
        // agendado a cada toque. A cadência automática mantém o ritmo dela sozinha.
        // E se o próprio WorkManager o parou, também não reinsere — o cuidado do LembretesWorker.
        val manual = inputData.getBoolean(CHAVE_MANUAL, false)
        if (!manual && !isStopped) tentar("reagendar") { container.backupScheduler.reagendar(config) }
        return Result.success()
    }

    /**
     * Os passos que não decidem o resultado do worker — a faxina do `.parcial`, a linha da tela, o
     * reagendamento —, cada um capaz de falhar sozinho sem levar os outros junto. Cancelamento passa
     * direto: `runCatching` o engoliria, e um worker parado deve parar.
     */
    private suspend fun tentar(oQue: String, bloco: suspend () -> Unit) {
        try {
            bloco()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "backup: $oQue falhou", e)
        }
    }

    companion object {
        /**
         * Marca a rodada do botão "agora". Ela grava, renomeia, rotaciona e registra o sucesso como
         * qualquer outra — só não mexe no agendamento.
         */
        const val CHAVE_MANUAL = "manual"

        private const val TAG = "saldo"
    }
}
