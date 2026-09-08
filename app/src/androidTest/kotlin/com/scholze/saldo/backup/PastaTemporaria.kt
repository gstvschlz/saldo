package com.scholze.saldo.backup

import java.io.File
import java.io.IOException

/**
 * Uma [PastaBackup] de arquivos de verdade num diretório temporário.
 *
 * É o que permite testar o worker inteiro sem uma árvore SAF escolhida à mão. [falharAoCriar] e
 * [lancarSecurity] simulam os dois caminhos ruins que o worker trata de formas diferentes.
 */
class PastaTemporaria(private val raiz: File) : PastaBackup {

    var falharAoCriar = false
    var lancarSecurity = false

    override suspend fun listar(): List<String> {
        if (lancarSecurity) throw SecurityException("permissão revogada")
        return raiz.listFiles()?.map { it.name }.orEmpty()
    }

    override suspend fun criar(nome: String, conteudo: String) {
        if (lancarSecurity) throw SecurityException("permissão revogada")
        if (falharAoCriar) throw IOException("disco cheio")
        File(raiz, nome).writeText(conteudo)
    }

    override suspend fun renomear(de: String, para: String) {
        if (lancarSecurity) throw SecurityException("permissão revogada")
        val origem = File(raiz, de)
        if (!origem.exists()) throw IOException("$de não existe")
        File(raiz, para).delete()
        if (!origem.renameTo(File(raiz, para))) throw IOException("não deu para renomear $de")
    }

    override suspend fun apagar(nome: String) {
        if (lancarSecurity) throw SecurityException("permissão revogada")
        File(raiz, nome).delete()
    }
}
