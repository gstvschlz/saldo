package com.scholze.saldo.domain

import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RitmoEngineTest {

    private val hoje = LocalDate.of(2026, 9, 4)

    private fun mov(dia: LocalDate, centavos: Long, natureza: Natureza = Natureza.DIARIO) =
        Movimentacao(id = 1, descricao = "x", valorCentavos = centavos, data = dia, natureza = natureza)

    private fun input(
        movs: List<Movimentacao>,
        inicial: LocalDate = LocalDate.of(2025, 1, 1),
    ) = LedgerInput(
        saldoInicialCentavos = 100_000,
        saldoInicialData = inicial,
        movimentacoes = movs,
        recorrencias = emptyList(),
        mesesMaterializados = emptySet(),
        cartao = CartaoConfig(fechamentoDia = 28, vencimentoDia = 5),
        hoje = hoje,
    )

    private fun setembro(movs: List<Movimentacao>) = RitmoEngine.ritmo(input(movs), YearMonth.of(2026, 9))

    // ---- o acumulado ----

    @Test
    fun `acumulado tem um ponto por dia decorrido e para em hoje`() {
        val r = setembro(emptyList())
        assertEquals(4, r.acumulado.size)
    }

    @Test
    fun `acumulado soma as saidas dia a dia`() {
        val r = setembro(
            listOf(
                mov(LocalDate.of(2026, 9, 1), -1_000),
                mov(LocalDate.of(2026, 9, 3), -2_500),
            ),
        )
        assertEquals(listOf(1_000L, 1_000L, 3_500L, 3_500L), r.acumulado)
        assertEquals(3_500L, r.gastoAteAgora)
    }

    @Test
    fun `entrada nao devolve ritmo`() {
        val r = setembro(
            listOf(
                mov(LocalDate.of(2026, 9, 1), -1_000),
                mov(LocalDate.of(2026, 9, 2), 740_000),
            ),
        )
        assertEquals(listOf(1_000L, 1_000L, 1_000L, 1_000L), r.acumulado)
    }

    @Test
    fun `acumulado nunca desce`() {
        val r = setembro((1..4).map { mov(LocalDate.of(2026, 9, it), -1_00) })
        r.acumulado.zipWithNext { a, b -> assertTrue("$a > $b", b >= a) }
    }

    @Test
    fun `compra no cartao conta no dia da compra`() {
        val r = setembro(listOf(mov(LocalDate.of(2026, 9, 2), -20_000, Natureza.CARTAO)))
        assertEquals(listOf(0L, 20_000L, 20_000L, 20_000L), r.acumulado)
    }

    @Test
    fun `mes passado vai ate o fim do mes`() {
        val r = RitmoEngine.ritmo(input(emptyList()), YearMonth.of(2026, 8))
        assertEquals(31, r.acumulado.size)
    }

    // ---- a referência ----

    @Test
    fun `referencia e a media dos meses anteriores no mesmo dia`() {
        // ago: 100 no dia 1. jul: 300 no dia 1. jun: 200 no dia 1. Média no dia 1 = 200.
        val r = setembro(
            listOf(
                mov(LocalDate.of(2026, 8, 1), -100),
                mov(LocalDate.of(2026, 7, 1), -300),
                mov(LocalDate.of(2026, 6, 1), -200),
            ),
        )
        assertEquals(3, r.mesesComparados)
        assertEquals(200L, r.referencia.first())
        assertEquals(4, r.referencia.size)
    }

    @Test
    fun `referencia tem o mesmo tamanho do acumulado`() {
        val r = setembro(listOf(mov(LocalDate.of(2026, 8, 15), -5_000)))
        assertEquals(r.acumulado.size, r.referencia.size)
    }

    @Test
    fun `so os tres meses anteriores entram`() {
        val r = setembro(
            listOf(
                mov(LocalDate.of(2026, 8, 1), -100),
                mov(LocalDate.of(2026, 7, 1), -100),
                mov(LocalDate.of(2026, 6, 1), -100),
                // Maio é o quarto mês para trás: fica de fora.
                mov(LocalDate.of(2026, 5, 1), -900_000),
            ),
        )
        assertEquals(3, r.mesesComparados)
        assertEquals(100L, r.referencia.first())
    }

    @Test
    fun `mes anterior sem gasto nenhum nao entra na media`() {
        val r = setembro(listOf(mov(LocalDate.of(2026, 8, 1), -600)))
        assertEquals(1, r.mesesComparados)
        assertEquals(600L, r.referencia.first())
    }

    @Test
    fun `sem mes anterior a referencia fica vazia`() {
        val r = setembro(emptyList())
        assertEquals(0, r.mesesComparados)
        assertTrue(r.referencia.isEmpty())
        assertNull(r.desvioPercentual)
    }

    /** Um mês anterior ao saldo inicial não é um mês barato: é um mês sem registro. */
    @Test
    fun `mes anterior ao saldo inicial nao entra`() {
        val r = RitmoEngine.ritmo(
            input(listOf(mov(LocalDate.of(2026, 8, 1), -600)), inicial = LocalDate.of(2026, 9, 1)),
            YearMonth.of(2026, 9),
        )
        assertEquals(0, r.mesesComparados)
    }

    /** Um mês mais curto vale pelo total com que fechou, não por zero. */
    @Test
    fun `mes mais curto se estica no ultimo valor`() {
        val emMarco = RitmoEngine.ritmo(
            LedgerInput(
                saldoInicialCentavos = 100_000,
                saldoInicialData = LocalDate.of(2026, 1, 1),
                movimentacoes = listOf(mov(LocalDate.of(2026, 2, 28), -5_000)),
                recorrencias = emptyList(),
                mesesMaterializados = emptySet(),
                cartao = CartaoConfig(fechamentoDia = 28, vencimentoDia = 5),
                hoje = LocalDate.of(2026, 3, 31),
            ),
            YearMonth.of(2026, 3),
        )
        // Fevereiro de 2026 tem 28 dias; nos dias 29, 30 e 31 a referência repete o total.
        assertEquals(31, emMarco.referencia.size)
        assertEquals(5_000L, emMarco.referencia[27])
        assertEquals(5_000L, emMarco.referencia[30])
    }

    // ---- o desvio ----

    /**
     * O caso que a captura pegou: existem meses anteriores, mas até ESTE dia do mês eles
     * estavam zerados. Não há porcentagem, e ainda assim há comparação — dizer "sem mês
     * anterior" aqui é mentira para quem tem seis meses de histórico.
     */
    @Test
    fun `costume zerado neste ponto do mes ainda e comparacao`() {
        val r = setembro(
            listOf(
                mov(LocalDate.of(2026, 9, 1), -5_000),
                // Agosto só gastou no dia 20: no dia 4, o costume dele era zero.
                mov(LocalDate.of(2026, 8, 20), -90_000),
            ),
        )
        assertTrue(r.temComparacao)
        assertEquals(0L, r.referenciaAteAgora)
        assertNull(r.desvioPercentual)
    }

    @Test
    fun `sem mes anterior nao ha comparacao nenhuma`() {
        val r = setembro(emptyList())
        assertFalse(r.temComparacao)
        assertNull(r.desvioPercentual)
    }

    @Test
    fun `desvio positivo quer dizer gastando mais que o costume`() {
        val r = setembro(
            listOf(
                mov(LocalDate.of(2026, 9, 1), -1_500),
                mov(LocalDate.of(2026, 8, 1), -1_000),
            ),
        )
        assertEquals(50, r.desvioPercentual)
    }

    @Test
    fun `desvio negativo quer dizer gastando menos`() {
        val r = setembro(
            listOf(
                mov(LocalDate.of(2026, 9, 1), -500),
                mov(LocalDate.of(2026, 8, 1), -1_000),
            ),
        )
        assertEquals(-50, r.desvioPercentual)
    }
}
