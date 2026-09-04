package com.scholze.saldo.domain

import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.abs

/**
 * Um dia da grade.
 *
 * [nivel] vai de −3 a 3: negativo saiu mais do que entrou, positivo o contrário, 0 é dia
 * sem movimento. É deliberadamente um número, não uma cor — quem pinta é a tela.
 *
 * [dentroDaJanela] é falso nos dias anteriores ao saldo inicial: não é "nada aconteceu",
 * é "não há registro", e a grade os apaga em vez de mostrá-los como dia parado.
 */
data class DiaBoard(
    val data: LocalDate,
    val valorCentavos: Long,
    val nivel: Int,
    val venceFatura: Boolean,
    val dentroDaJanela: Boolean,
)

/**
 * A grade inteira. [dias] é contíguo de [inicio] a [fim], sem buraco — a tela conta com
 * isso para montar as semanas.
 *
 * [unidadeCentavos] é o "dia típico" que dá escala às cores; `0` significa que ainda não
 * há nenhum dia com movimento, e a legenda deve dizer isso em vez de mostrar R$ 0,00.
 */
data class Board(
    val dias: List<DiaBoard>,
    val unidadeCentavos: Long,
    val inicio: LocalDate,
    val fim: LocalDate,
)

/**
 * O board: o saldo de cada dia dos últimos 12 meses, em sete tons.
 *
 * Duas escolhas separam este motor do [ProjectionEngine], e as duas são deliberadas:
 *
 * 1. **Uma compra no cartão conta no dia da compra**, como no "para onde foi", e não no
 *    vencimento da fatura como no ledger. O board é uma vista de comportamento — o dia
 *    caro é a sexta em que se gastou, não o dia 5 em que o banco cobrou. O vencimento
 *    ainda aparece, mas como marca ([DiaBoard.venceFatura]), sem valor nenhum.
 * 2. **A escala é a mediana**, não o máximo. Um salário é umas trinta vezes um dia
 *    comum; normalizado pelo máximo ele desbotaria o board inteiro. Contra a mediana ele
 *    simplesmente satura no tom 3 e não move mais nada.
 *
 * Consequência aceita da primeira: somar as células **não** reconstrói o saldo projetado
 * do hero. O board responde "quando eu gastei", o hero responde "quanto vai sobrar".
 */
object BoardEngine {

    /** Quantos meses a grade cobre, terminando em `input.hoje`. */
    private const val MESES = 12L

    fun board(input: LedgerInput): Board {
        val fim = input.hoje
        val inicio = fim.minusMonths(MESES).plusDays(1)
        val ateMes = YearMonth.from(fim)

        // Uma expansão só para os treze meses: `movimentacoesDoMes` chamada mês a mês
        // reexpandiria as recorrências treze vezes.
        val porDia = ProjectionEngine.movimentacoesAte(input, ateMes)
            .filter { it.data >= inicio && it.data <= fim }
            .groupBy { it.data }
            .mapValues { (_, movs) -> movs.sumOf { it.valorCentavos } }

        val vencimentos = ProjectionEngine.faturasAte(input, ateMes)
            .map { it.vencimento }
            .filter { it >= inicio && it <= fim }
            .toSet()

        val unidade = mediana(porDia.values.filter { it != 0L }.map { abs(it) })

        val dias = buildList {
            var d = inicio
            while (d <= fim) {
                val valor = porDia[d] ?: 0L
                add(
                    DiaBoard(
                        data = d,
                        valorCentavos = valor,
                        nivel = nivelDe(valor, unidade),
                        venceFatura = d in vencimentos,
                        dentroDaJanela = d >= input.saldoInicialData,
                    ),
                )
                d = d.plusDays(1)
            }
        }
        return Board(dias = dias, unidadeCentavos = unidade, inicio = inicio, fim = fim)
    }

    /**
     * Em que tom [valorCentavos] cai, dada a unidade do dia típico.
     *
     * Os cortes são ½× e 1½× a unidade, e a conta é feita em inteiros — `|v| * 2` contra
     * `unidade` e contra `unidade * 3` — para não existir um `Double` decidindo de que
     * cor um dia é.
     */
    fun nivelDe(valorCentavos: Long, unidadeCentavos: Long): Int {
        if (valorCentavos == 0L || unidadeCentavos <= 0L) return 0
        val escala = abs(valorCentavos) * 2
        val tom = when {
            escala < unidadeCentavos -> 1
            escala < unidadeCentavos * 3 -> 2
            else -> 3
        }
        return if (valorCentavos < 0) -tom else tom
    }

    /** `0` numa lista vazia — é o sinal de "ainda não há dia típico". */
    private fun mediana(valores: List<Long>): Long {
        if (valores.isEmpty()) return 0L
        val s = valores.sorted()
        val meio = s.size / 2
        return if (s.size % 2 == 1) s[meio] else (s[meio - 1] + s[meio]) / 2
    }
}
