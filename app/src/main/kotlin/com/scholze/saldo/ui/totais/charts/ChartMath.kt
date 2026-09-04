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

    /**
     * Um valor > 0 nunca ocupa menos que [piso] do espaço disponível ([cheio]) — do contrário vira
     * um traço subpixel que lê como "sem dado" (um dia sem gasto e um dia com R$ 1 não podem
     * parecer iguais). Zero não desenha nada. O piso é limitado por [cheio]: numa barra ou canvas
     * baixo demais (ex. `altura = 1.dp` em [WeekdayBars]/[TrendChart]), um piso fixo de 2dp
     * desenharia FORA da área disponível — aqui ele encolhe pra caber. Usada por WeekdayBars (dp,
     * via a sobrecarga em Charts.kt) e TrendChart (px do Canvas, direto).
     */
    fun pisoSeNaoZero(fracao: Float, cheio: Float, piso: Float): Float {
        if (fracao <= 0f) return 0f
        val pisoLimitado = minOf(piso, cheio)
        return maxOf(pisoLimitado, fracao * cheio)
    }

    // Limites (mínimo, máximo) só da variação real dos dados — usado por linha() (a reserva
    // acumulada não tem por que se ancorar em zero; ver o KDoc dela). Só chamar com não vazio.
    private fun limitesDados(valores: List<Long>): Pair<Long, Long> =
        valores.min() to valores.max()

    // Limites (mínimo, máximo) sempre incluindo o zero — usado por linhaComSinal() e linhaZero()
    // para as duas lerem a mesma escala. Só chamar com `valores` não vazio.
    private fun limitesComZero(valores: List<Long>): Pair<Long, Long> =
        minOf(0L, valores.min()) to maxOf(0L, valores.max())

    /**
     * Posições 0..1 de uma linha (0 = mínimo, 1 = máximo) pela variação REAL da série, sem forçar
     * o zero a entrar na escala. Para [ReservaLine]: a reserva acumulada (`PontoMes.reservaAcumulada`)
     * é positiva e cresce pouco mês a mês perto do próprio tamanho — ex.: uma reserva que sobe de
     * 420 mil pra 530 mil (variações mensais de ~3 % a ~7 % do total) precisa ocupar o canvas
     * inteiro pra essa variação aparecer. Ancorar em zero comprimiria a curva nos últimos 20 % do
     * espaço (420 mil já é 79 % do caminho até 530 mil) — o sparkline existe justamente pra mostrar
     * essa variação pequena, não pra provar que a reserva é positiva. Série sem variação → 0.5.
     */
    fun linha(valores: List<Long>): List<Float> {
        if (valores.isEmpty()) return emptyList()
        val (min, max) = limitesDados(valores)
        return if (max == min) valores.map { 0.5f } else valores.map { (it - min).toFloat() / (max - min) }
    }

    /**
     * Duas séries na MESMA escala, ancoradas no zero: 0 = nada, 1 = o maior ponto das duas.
     *
     * Normalizar cada uma pela própria variação, como [linha] faz, desenharia o mês e o
     * costume com a MESMA forma — e é justamente a diferença de altura entre os dois que o
     * gráfico do ritmo existe para mostrar. Ancorar no zero também é o certo aqui porque um
     * acumulado começa em zero de verdade, não num mínimo qualquer.
     */
    fun linhasNaMesmaEscala(a: List<Long>, b: List<Long>): Pair<List<Float>, List<Float>> {
        val max = maxOf(a.maxOrNull() ?: 0L, b.maxOrNull() ?: 0L)
        if (max <= 0L) return a.map { 0f } to b.map { 0f }
        return a.map { it.toFloat() / max } to b.map { it.toFloat() / max }
    }

    /**
     * Como [linha], mas com o intervalo sempre incluindo o zero — assim a posição também carrega o
     * SINAL do valor (um mês positivo sobe, um negativo desce), não só a variação entre os pontos.
     * Sem isso, uma série [-500, +500] e outra [+100, +900] desenhavam a mesma forma: as duas
     * normalizavam só pela própria variação (min-max dos dados), nunca em relação ao zero. Usada
     * pela linha do "sobrou" em [TrendChart], onde estar acima ou abaixo de zero é a informação
     * principal — ao contrário da reserva acumulada ([linha]), que é sempre positiva e não tem essa
     * fronteira pra marcar. Série sem variação (depois de incluir o zero) → 0.5.
     */
    fun linhaComSinal(valores: List<Long>): List<Float> {
        if (valores.isEmpty()) return emptyList()
        val (min, max) = limitesComZero(valores)
        return if (max == min) valores.map { 0.5f } else valores.map { (it - min).toFloat() / (max - min) }
    }

    /**
     * Posição 0..1 do zero na mesma escala de [linhaComSinal] — onde desenhar a régua muda de
     * sinal. Só existe quando a série realmente mistura valor abaixo e acima de zero; caso
     * contrário a base ficaria colada numa borda (ou fora do trecho útil), sem ler como referência
     * nenhuma.
     */
    fun linhaZero(valores: List<Long>): Float? {
        if (valores.isEmpty() || valores.none { it < 0 } || valores.none { it > 0 }) return null
        val (min, max) = limitesComZero(valores)
        return (0L - min).toFloat() / (max - min)
    }
}
