package com.scholze.saldo.data

import android.content.Context
import android.net.Uri
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Lê o texto de um `Uri` do seletor do sistema.
 *
 * É uma interface porque o restaurar inteiro fica testável sem um seletor de arquivos: o teste
 * injeta um leitor que devolve uma `String` e o resto do caminho — validar, substituir, reagendar —
 * roda como na vida real.
 */
fun interface LeitorDeArquivo {
    suspend fun ler(uri: Uri): String
}

/**
 * O leitor de verdade. Fora da main thread e com teto de tamanho: o `Uri` vem de fora e um arquivo
 * de 2 GB apontado por engano viraria um `OutOfMemoryError` em vez de uma mensagem.
 *
 * O teto é conferido enquanto lê, e não depois: um `readBytes()` inteiro já teria estourado a
 * memória antes de alguém poder medir o tamanho.
 */
class LeitorSaf(
    private val context: Context,
    private val limiteBytes: Long = 16L * 1024 * 1024,
) : LeitorDeArquivo {

    override suspend fun ler(uri: Uri): String = withContext(Dispatchers.IO) {
        val stream = context.contentResolver.openInputStream(uri) ?: error("não deu para abrir o arquivo")
        stream.use { entrada ->
            val saida = ByteArrayOutputStream()
            val pedaco = ByteArray(8 * 1024)
            while (true) {
                val lidos = entrada.read(pedaco)
                if (lidos < 0) break
                saida.write(pedaco, 0, lidos)
                if (saida.size() > limiteBytes) {
                    throw ArquivoInvalido("arquivo grande demais: o saldo lê até ${limiteBytes / 1024 / 1024} MB")
                }
            }
            String(saida.toByteArray(), Charsets.UTF_8)
        }
    }
}
