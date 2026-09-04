package com.scholze.saldo.domain

import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BoardEngineTest {

    // Dia 20: a janela é o mês corrente até hoje, e as três semanas antes dele são o
    // espaço em que a mediana, o vencimento e a saturação do salário cabem.
    private val hoje = LocalDate.of(2026, 9, 20)

    private fun mov(dia: LocalDate, centavos: Long, natureza: Natureza = Natureza.DIARIO) =
        Movimentacao(id = 1, descricao = "x", valorCentavos = centavos, data = dia, natureza = natureza)

    private fun input(
        movs: List<Movimentacao>,
        recorrencias: List<Recorrencia> = emptyList(),
        inicial: LocalDate = LocalDate.of(2025, 1, 1),
        cartao: CartaoConfig = CartaoConfig(fechamentoDia = 28, vencimentoDia = 5),
    ) = LedgerInput(
        saldoInicialCentavos = 100_000,
        saldoInicialData = inicial,
        movimentacoes = movs,
        recorrencias = recorrencias,
        mesesMaterializados = emptySet(),
        cartao = cartao,
        hoje = hoje,
    )

    private fun Board.dia(d: LocalDate) = dias.first { it.data == d }

    // ---- janela ----

    @Test
    fun `janela vai do dia 1 do mes corrente ate hoje`() {
        val b = BoardEngine.board(input(emptyList()))
        assertEquals(LocalDate.of(2026, 9, 1), b.inicio)
        assertEquals(hoje, b.fim)
        assertEquals(20, b.dias.size)
        assertEquals(b.inicio, b.dias.first().data)
        assertEquals(b.fim, b.dias.last().data)
    }

    @Test
    fun `a grade nao passa de hoje nem alcanca o mes passado`() {
        val b = BoardEngine.board(
            input(
                listOf(
                    mov(LocalDate.of(2026, 8, 31), -9_000),
                    mov(LocalDate.of(2026, 9, 25), -9_000),
                ),
            ),
        )
        assertTrue(b.dias.none { it.data < LocalDate.of(2026, 9, 1) })
        assertTrue(b.dias.none { it.data > hoje })
    }

    @Test
    fun `o que ainda vai vencer neste mes nao entra na grade`() {
        // Recorrência do dia 25: existe, o ledger a projeta, mas ainda não aconteceu.
        val rec = Recorrencia(
            id = 1, descricao = "internet", valorCentavos = -12_000,
            natureza = Natureza.DIARIO, diaDoMes = 25, inicio = YearMonth.of(2026, 1),
        )
        val b = BoardEngine.board(input(emptyList(), recorrencias = listOf(rec)))
        assertEquals(hoje, b.dias.last().data)
        assertEquals(0, b.unidadeCentavos)
    }

    // ---- a janela de um mês escolhido ----

    @Test
    fun `mes passado aparece inteiro`() {
        val b = BoardEngine.board(input(emptyList()), YearMonth.of(2026, 8))
        assertEquals(LocalDate.of(2026, 8, 1), b.inicio)
        assertEquals(LocalDate.of(2026, 8, 31), b.fim)
        assertEquals(31, b.dias.size)
    }

    @Test
    fun `fevereiro de ano bissexto tem 29 dias`() {
        val b = BoardEngine.board(input(emptyList()), YearMonth.of(2024, 2))
        assertEquals(29, b.dias.size)
    }

    /** A tela não deixa navegar até lá; o motor não pode estourar se ela deixar. */
    @Test
    fun `mes futuro devolve grade vazia`() {
        val b = BoardEngine.board(input(emptyList()), YearMonth.of(2026, 10))
        assertTrue(b.dias.isEmpty())
        assertEquals(0, b.unidadeCentavos)
    }

    @Test
    fun `sem mes escolhido a janela e a do mes de hoje`() {
        assertEquals(BoardEngine.board(input(emptyList()), YearMonth.of(2026, 9)).dias.size,
            BoardEngine.board(input(emptyList())).dias.size)
    }

    /** Cada mês soma o seu: o gasto de agosto não vaza para a grade de setembro. */
    @Test
    fun `o valor do dia e do mes pedido`() {
        val emAgosto = LocalDate.of(2026, 8, 14)
        val entrada = input(listOf(mov(emAgosto, -7_000), mov(LocalDate.of(2026, 9, 2), -1_000)))

        val agosto = BoardEngine.board(entrada, YearMonth.of(2026, 8))
        assertEquals(-7_000, agosto.dia(emAgosto).valorCentavos)
        assertTrue(agosto.dias.none { it.data.month == java.time.Month.SEPTEMBER })

        val setembro = BoardEngine.board(entrada, YearMonth.of(2026, 9))
        assertEquals(-1_000, setembro.dia(LocalDate.of(2026, 9, 2)).valorCentavos)
        assertTrue(setembro.dias.none { it.data.month == java.time.Month.AUGUST })
    }

    @Test
    fun `dias sao contiguos e sem buraco`() {
        val b = BoardEngine.board(input(listOf(mov(hoje.minusDays(3), -5_000))))
        b.dias.zipWithNext { a, c -> assertEquals(a.data.plusDays(1), c.data) }
    }

    @Test
    fun `dia anterior ao saldo inicial fica fora da janela`() {
        val inicial = LocalDate.of(2026, 9, 5)
        val b = BoardEngine.board(input(emptyList(), inicial = inicial))
        assertFalse(b.dia(LocalDate.of(2026, 9, 4)).dentroDaJanela)
        assertTrue(b.dia(inicial).dentroDaJanela)
    }

    // ---- valor do dia ----

    @Test
    fun `valor do dia soma entradas e saidas do mesmo dia`() {
        val d = hoje.minusDays(2)
        val b = BoardEngine.board(input(listOf(mov(d, -3_000), mov(d, 10_000))))
        assertEquals(7_000, b.dia(d).valorCentavos)
    }

    @Test
    fun `compra no cartao conta no dia da compra`() {
        val compra = LocalDate.of(2026, 9, 12)
        val b = BoardEngine.board(input(listOf(mov(compra, -20_000, Natureza.CARTAO))))
        assertEquals(-20_000, b.dia(compra).valorCentavos)
    }

    @Test
    fun `fatura nao vira valor no dia do vencimento`() {
        // Compra em 12/ago: ciclo ago (fecha 28/ago), vence 5/set.
        val compra = LocalDate.of(2026, 8, 12)
        val b = BoardEngine.board(input(listOf(mov(compra, -20_000, Natureza.CARTAO))))
        assertEquals(0, b.dia(LocalDate.of(2026, 9, 5)).valorCentavos)
    }

    @Test
    fun `ocorrencia virtual de recorrencia entra na soma do dia`() {
        val rec = Recorrencia(
            id = 1, descricao = "aluguel", valorCentavos = -150_000,
            natureza = Natureza.DIARIO, diaDoMes = 10, inicio = YearMonth.of(2026, 1),
        )
        val b = BoardEngine.board(input(emptyList(), recorrencias = listOf(rec)))
        assertEquals(-150_000, b.dia(LocalDate.of(2026, 9, 10)).valorCentavos)
    }

    // ---- anel de fatura ----

    /**
     * A compra que formou a fatura é do mês passado e não está mais na grade — o anel tem
     * de sobreviver a isso, e é por ele que as faturas continuam vindo do histórico
     * inteiro em vez de sair só das compras do mês.
     */
    @Test
    fun `vencimento da fatura marca o dia mesmo com a compra fora da grade`() {
        val compra = LocalDate.of(2026, 8, 12)
        val b = BoardEngine.board(input(listOf(mov(compra, -20_000, Natureza.CARTAO))))
        assertTrue(b.dias.none { it.data == compra })
        assertTrue(b.dia(LocalDate.of(2026, 9, 5)).venceFatura)
    }

    @Test
    fun `o dia da compra nao marca vencimento`() {
        val compra = LocalDate.of(2026, 9, 12)
        val b = BoardEngine.board(input(listOf(mov(compra, -20_000, Natureza.CARTAO))))
        assertFalse(b.dia(compra).venceFatura)
    }

    @Test
    fun `sem compra no cartao nenhum dia marca vencimento`() {
        val b = BoardEngine.board(input(listOf(mov(hoje.minusDays(1), -5_000))))
        assertTrue(b.dias.none { it.venceFatura })
    }

    // ---- unidade e niveis ----

    @Test
    fun `unidade e a mediana dos dias com movimento`() {
        val b = BoardEngine.board(
            input(
                listOf(
                    mov(hoje.minusDays(1), -1_000),
                    mov(hoje.minusDays(2), -3_000),
                    mov(hoje.minusDays(3), -10_000),
                ),
            ),
        )
        assertEquals(3_000, b.unidadeCentavos)
    }

    @Test
    fun `dias zerados nao entram na mediana`() {
        val b = BoardEngine.board(input(listOf(mov(hoje.minusDays(1), -8_000))))
        assertEquals(8_000, b.unidadeCentavos)
    }

    @Test
    fun `sem nenhum movimento nao ha dia tipico`() {
        val b = BoardEngine.board(input(emptyList()))
        assertEquals(0, b.unidadeCentavos)
        assertTrue(b.dias.all { it.nivel == 0 })
    }

    @Test
    fun `faixas caem em meia e uma vez e meia a unidade`() {
        val u = 10_000L
        assertEquals(0, BoardEngine.nivelDe(0, u))
        assertEquals(-1, BoardEngine.nivelDe(-4_999, u))
        assertEquals(-2, BoardEngine.nivelDe(-5_000, u))
        assertEquals(-2, BoardEngine.nivelDe(-14_999, u))
        assertEquals(-3, BoardEngine.nivelDe(-15_000, u))
        assertEquals(1, BoardEngine.nivelDe(4_999, u))
        assertEquals(2, BoardEngine.nivelDe(5_000, u))
        assertEquals(3, BoardEngine.nivelDe(15_000, u))
    }

    @Test
    fun `salario satura sem mexer no lado rosa`() {
        val movs = (1..10).map { mov(hoje.minusDays(it.toLong()), -5_000) } +
            mov(hoje.minusDays(11), 740_000)
        val b = BoardEngine.board(input(movs))
        assertEquals(5_000, b.unidadeCentavos)
        assertEquals(3, b.dia(hoje.minusDays(11)).nivel)
        assertEquals(-2, b.dia(hoje.minusDays(1)).nivel)
    }

    @Test
    fun `unidade zero deixa todo dia neutro`() {
        assertEquals(0, BoardEngine.nivelDe(-9_999, 0))
    }
}
