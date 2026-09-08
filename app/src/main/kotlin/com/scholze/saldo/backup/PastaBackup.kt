package com.scholze.saldo.backup

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import java.io.IOException
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * A pasta onde o backup automático grava — quatro operações e nada mais.
 *
 * É uma interface, e não o `DocumentsContract` direto no worker, porque senão o worker só seria
 * testável num aparelho com uma árvore SAF de verdade escolhida à mão: o tipo de teste que ninguém
 * roda. Com ela, `BackupWorkerTest` usa uma pasta de arquivos temporários e exercita o caminho
 * inteiro — inclusive a falha de escrita e a permissão revogada.
 */
interface PastaBackup {
    /** Os nomes dos arquivos da pasta (só o nome, sem caminho). */
    suspend fun listar(): List<String>

    /** Cria (ou sobrescreve) [nome] com [conteudo]. */
    suspend fun criar(nome: String, conteudo: String)

    suspend fun renomear(de: String, para: String)

    /** Apaga [nome]; um nome que não existe é no-op. */
    suspend fun apagar(nome: String)
}

/**
 * Os nomes dos arquivos do backup e a regra de rotação.
 *
 * Um arquivo por dia em que o backup rodar, datado, e a rotação apaga o que passar de [QUANTOS] —
 * **e só o que casa com o padrão exato**. Um nome fixo sobrescrito seria mais simples e deixaria um
 * export corrompido comer o único arquivo bom; apagar qualquer coisa que esteja na pasta seria o
 * app se achando dono de uma pasta que é do usuário.
 *
 * Os `.parcial` não entram na rotação: eles são um estado intermediário de um segundo, e o do dia é
 * sobrescrito pela rodada seguinte.
 */
object NomeBackup {

    /** Grava-se aqui e só depois renomeia: um backup pela metade que PARECE válido é pior que nenhum. */
    const val SUFIXO_PARCIAL = ".parcial"

    const val QUANTOS = 7

    private val padrao = Regex("""^saldo-\d{4}-\d{2}-\d{2}\.json$""")

    fun de(data: LocalDate): String = "saldo-$data.json"

    fun parcial(data: LocalDate): String = de(data) + SUFIXO_PARCIAL

    fun ehDoPadrao(nome: String): Boolean = padrao.matches(nome)

    /**
     * O que a rotação apaga: os arquivos do padrão além dos [manter] mais recentes.
     * A ordem é a do NOME — em ISO-8601 ela é a do calendário, inclusive na virada do ano.
     */
    fun aApagar(nomes: List<String>, manter: Int = QUANTOS): List<String> =
        nomes.filter(::ehDoPadrao).sortedDescending().drop(manter)
}

/**
 * A pasta de verdade, sobre a árvore que o usuário escolheu com `OpenDocumentTree` e cuja permissão
 * o app persistiu.
 *
 * Sem teste automatizado, de propósito: tudo que decide o quê gravar e o quê apagar está em
 * [NomeBackup] e no `BackupWorker`. Aqui só há chamadas do `DocumentsContract`.
 *
 * Toda operação lança `SecurityException` se a permissão foi revogada ou a pasta apagada — e é
 * exatamente isso que o worker usa para desligar o backup em vez de tentar para sempre em silêncio.
 */
class PastaSaf(private val context: Context, private val arvore: Uri) : PastaBackup {

    private val documentoRaiz: Uri
        get() = DocumentsContract.buildDocumentUriUsingTree(arvore, DocumentsContract.getTreeDocumentId(arvore))

    private val filhos: Uri
        get() = DocumentsContract.buildChildDocumentsUriUsingTree(arvore, DocumentsContract.getTreeDocumentId(arvore))

    override suspend fun listar(): List<String> = withContext(Dispatchers.IO) {
        nomesEUris().map { it.first }
    }

    override suspend fun criar(nome: String, conteudo: String) = withContext(Dispatchers.IO) {
        // `createDocument` com um nome que já existe cria "nome (1)" em vez de sobrescrever, e a
        // pasta encheria de sobras de tentativas interrompidas.
        apagarSincrono(nome)
        val doc = DocumentsContract.createDocument(context.contentResolver, documentoRaiz, "application/json", nome)
            ?: throw IOException("não deu para criar $nome")
        context.contentResolver.openOutputStream(doc, "wt")?.use { it.write(conteudo.toByteArray()) }
            ?: throw IOException("sem stream de escrita para $nome")
    }

    override suspend fun renomear(de: String, para: String) = withContext(Dispatchers.IO) {
        apagarSincrono(para)
        val uri = nomesEUris().firstOrNull { it.first == de }?.second ?: throw IOException("$de não existe")
        DocumentsContract.renameDocument(context.contentResolver, uri, para) ?: throw IOException("não deu para renomear $de")
        Unit
    }

    override suspend fun apagar(nome: String) = withContext(Dispatchers.IO) { apagarSincrono(nome) }

    private fun apagarSincrono(nome: String) {
        nomesEUris().firstOrNull { it.first == nome }?.let {
            DocumentsContract.deleteDocument(context.contentResolver, it.second)
        }
    }

    private fun nomesEUris(): List<Pair<String, Uri>> {
        val colunas = arrayOf(
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
        )
        return context.contentResolver.query(filhos, colunas, null, null, null)?.use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(c.getString(0) to DocumentsContract.buildDocumentUriUsingTree(arvore, c.getString(1)))
                }
            }
        } ?: emptyList()
    }
}
