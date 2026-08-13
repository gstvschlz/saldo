package com.scholze.saldo.domain

import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Test

class FaturaCalculatorTest {
    private val config = CartaoConfig(nome = "nubank", fechamentoDia = 28, vencimentoDia = 5)

    private fun compra(dia: String, centavos: Long) = Movimentacao(
        descricao = "compra", valorCentavos = centavos,
        data = LocalDate.parse(dia), natureza = Natureza.CARTAO,
    )

    @Test
    fun compraAntesDoFechamentoEntraNoCicloDoMes() {
        assertEquals(YearMonth.of(2026, 7), FaturaCalculator.cicloDaCompra(LocalDate.parse("2026-07-10"), config))
    }

    @Test
    fun compraNoDiaDoFechamentoEntraNoCicloQueFechaNaqueleDia() {
        assertEquals(YearMonth.of(2026, 7), FaturaCalculator.cicloDaCompra(LocalDate.parse("2026-07-28"), config))
    }

    @Test
    fun compraDepoisDoFechamentoEntraNoCicloSeguinte() {
        assertEquals(YearMonth.of(2026, 8), FaturaCalculator.cicloDaCompra(LocalDate.parse("2026-07-29"), config))
    }

    @Test
    fun vencimentoMenorQueFechamentoCaiNoMesSeguinte() {
        // fecha dia 28, vence dia 5 => ciclo jul vence 5/ago
        assertEquals(LocalDate.parse("2026-08-05"), FaturaCalculator.vencimentoDoCiclo(YearMonth.of(2026, 7), config))
    }

    @Test
    fun vencimentoMaiorQueFechamentoCaiNoMesmoMes() {
        val c = CartaoConfig(fechamentoDia = 3, vencimentoDia = 10)
        assertEquals(LocalDate.parse("2026-07-10"), FaturaCalculator.vencimentoDoCiclo(YearMonth.of(2026, 7), c))
    }

    @Test
    fun fechamentoDia31ClampaEmMesCurto() {
        val c = CartaoConfig(fechamentoDia = 31, vencimentoDia = 7)
        assertEquals(LocalDate.parse("2026-02-28"), FaturaCalculator.fechamentoDoCiclo(YearMonth.of(2026, 2), c))
    }

    @Test
    fun agrupaComprasEmFaturasComTotais() {
        val faturas = FaturaCalculator.faturas(
            listOf(
                compra("2026-07-10", -100_00), // ciclo jul
                compra("2026-07-28", -50_00),  // ciclo jul (dia do fechamento)
                compra("2026-07-30", -30_00),  // ciclo ago
            ),
            config,
        )
        assertEquals(2, faturas.size)
        val jul = faturas.first { it.ciclo == YearMonth.of(2026, 7) }
        assertEquals(-150_00L, jul.totalCentavos)
        assertEquals(LocalDate.parse("2026-08-05"), jul.vencimento)
        assertEquals(2, jul.compras.size)
    }

    @Test
    fun ignoraMovimentacoesQueNaoSaoCartao() {
        val faturas = FaturaCalculator.faturas(
            listOf(
                Movimentacao(descricao = "mercado", valorCentavos = -10_00, data = LocalDate.parse("2026-07-10"), natureza = Natureza.DIARIO),
            ),
            config,
        )
        assertEquals(0, faturas.size)
    }
}
