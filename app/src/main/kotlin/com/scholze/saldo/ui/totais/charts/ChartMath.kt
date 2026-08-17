package com.scholze.saldo.ui.totais.charts

import kotlin.math.abs

/** A geometria dos gráficos, sem Compose: testável na JVM, os composables só desenham. */
object ChartMath {

    /**
     * Frações de largura da barra segmentada. Uma fatia > 0 nunca fica abaixo de [piso] (senão
     * some da tela); as demais encolhem proporcionalmente para o total continuar 1. Zeros ficam 0.
     */
    fun larguras(shares: List<Float>, piso: Float = 0.02f): List<Float> {
        val total = shares.sum()
        // Lista vazia cai aqui direto: total = 0f (sum de vazio), então retorna emptyList() sem
        // precisar de um caso especial em separado.
        if (total <= 0f) return shares.map { 0f }
        val normal = shares.map { it / total }
        val pequenas = normal.count { it > 0f && it < piso }
        if (pequenas == 0) return normal
        val restante = normal.filter { it >= piso }.sum()
        val escala = if (restante > 0f) (1f - pequenas * piso) / restante else 0f
        return normal.map {
            when {
                it <= 0f -> 0f
                it < piso -> piso
                else -> it * escala
            }
        }
    }

    /** Alturas 0..1 relativas ao maior valor absoluto; tudo zero → tudo zero. */
    fun alturas(valores: List<Long>): List<Float> {
        val max = valores.maxOfOrNull { abs(it) } ?: 0L
        return if (max == 0L) valores.map { 0f } else valores.map { abs(it).toFloat() / max }
    }

    // Limites (mínimo, máximo) sempre incluindo o zero — usado por linha() e linhaZero() para as
    // duas lerem a mesma escala. Só chamar com `valores` não vazio.
    private fun limites(valores: List<Long>): Pair<Long, Long> =
        minOf(0L, valores.min()) to maxOf(0L, valores.max())

    /**
     * Posições 0..1 de uma linha (0 = mínimo, 1 = máximo), com o intervalo sempre incluindo o
     * zero — assim a posição também carrega o SINAL do valor (um mês positivo sobe, um negativo
     * desce), não só a variação entre os pontos. Sem isso, uma série [-500, +500] e outra
     * [+100, +900] desenhavam a mesma forma: as duas normalizavam só pela própria variação (min-max
     * dos dados), nunca em relação ao zero. Série sem variação (depois de incluir o zero) → 0.5.
     */
    fun linha(valores: List<Long>): List<Float> {
        if (valores.isEmpty()) return emptyList()
        val (min, max) = limites(valores)
        return if (max == min) valores.map { 0.5f } else valores.map { (it - min).toFloat() / (max - min) }
    }

    /**
     * Posição 0..1 do zero na mesma escala de [linha] — onde desenhar a régua muda de sinal.
     * Só existe quando a série realmente mistura valor abaixo e acima de zero; caso contrário a
     * base ficaria colada numa borda (ou fora do trecho útil), sem ler como referência nenhuma.
     */
    fun linhaZero(valores: List<Long>): Float? {
        if (valores.isEmpty() || valores.none { it < 0 } || valores.none { it > 0 }) return null
        val (min, max) = limites(valores)
        if (max == min) return null
        return (0L - min).toFloat() / (max - min)
    }
}
