package com.scholze.saldo.ui.totais.charts

import kotlin.math.abs

/** A geometria dos gráficos, sem Compose: testável na JVM, os composables só desenham. */
object ChartMath {

    /**
     * Frações de largura da barra segmentada. Uma fatia > 0 nunca fica abaixo de [piso] (senão
     * some da tela); as demais encolhem proporcionalmente para o total continuar 1. Zeros ficam 0.
     */
    fun larguras(shares: List<Float>, piso: Float = 0.02f): List<Float> {
        if (shares.isEmpty()) return emptyList()
        val total = shares.sum()
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

    /** Posições 0..1 de uma linha (0 = mínimo, 1 = máximo); série constante → 0.5. */
    fun linha(valores: List<Long>): List<Float> {
        val min = valores.minOrNull() ?: return emptyList()
        val max = valores.max()
        return if (max == min) valores.map { 0.5f } else valores.map { (it - min).toFloat() / (max - min) }
    }
}
