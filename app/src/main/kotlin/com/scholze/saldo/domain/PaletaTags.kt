package com.scholze.saldo.domain

/**
 * As cores de tag. Uma lista só, para a sheet e a aba tags: antes eram duas (cinco e seis
 * cores) e uma tag criada num lugar tirava de um rodízio diferente da criada no outro.
 *
 * [proxima] devolve a cor menos usada entre as seis; empate vai pela ordem da paleta. Uma
 * cor que não está na paleta (importada, ou de uma versão antiga) não conta.
 */
object PaletaTags {
    val cores: List<Long> = listOf(0xFFA6486BL, 0xFFB95A2EL, 0xFF2A7A86L, 0xFF4B4BC4L, 0xFF14663AL, 0xFFE58A5AL)

    fun proxima(usadas: List<Long>): Long {
        val contagem = usadas.filter { it in cores }.groupingBy { it }.eachCount()
        return cores.minBy { contagem[it] ?: 0 }
    }
}
