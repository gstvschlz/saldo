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

    // ---- carência de fevereiro (dados-1) ----

    /** fecha 30 / vence 31: em fevereiro os dois clampam para 28 e a fatura venceria no mesmo dia. */
    @Test
    fun fevereiroComFechamento30EVencimento31VenceEmMarco() {
        val config = CartaoConfig(nome = "c", fechamentoDia = 30, vencimentoDia = 31)
        val fev = YearMonth.of(2026, 2)
        assertEquals(LocalDate.parse("2026-02-28"), FaturaCalculator.fechamentoDoCiclo(fev, config))
        assertEquals(LocalDate.parse("2026-03-31"), FaturaCalculator.vencimentoDoCiclo(fev, config))
    }

    /** O mês seguinte com a MESMA config continua como sempre foi: 30 → 31, no próprio ciclo. */
    @Test
    fun marcoComFechamento30EVencimento31ContinuaNoProprioCiclo() {
        val config = CartaoConfig(nome = "c", fechamentoDia = 30, vencimentoDia = 31)
        val mar = YearMonth.of(2026, 3)
        assertEquals(LocalDate.parse("2026-03-30"), FaturaCalculator.fechamentoDoCiclo(mar, config))
        assertEquals(LocalDate.parse("2026-03-31"), FaturaCalculator.vencimentoDoCiclo(mar, config))
    }

    /** Vencimento e fechamento no MESMO dia depois do clamp já é "não venceu ainda": vai para o mês seguinte. */
    @Test
    fun vencimentoIgualAoFechamentoDepoisDoClampVaiParaOMesSeguinte() {
        val config = CartaoConfig(nome = "c", fechamentoDia = 28, vencimentoDia = 28)
        assertEquals(
            LocalDate.parse("2026-08-28"),
            FaturaCalculator.vencimentoDoCiclo(YearMonth.of(2026, 7), config),
        )
    }

    /** E o caso comum não pode regredir: fecha 28 / vence 5 sempre cai no mês seguinte. */
    @Test
    fun fechamento28Vencimento5ContinuaNoMesSeguinte() {
        val config = CartaoConfig(nome = "c", fechamentoDia = 28, vencimentoDia = 5)
        assertEquals(
            LocalDate.parse("2026-08-05"),
            FaturaCalculator.vencimentoDoCiclo(YearMonth.of(2026, 7), config),
        )
    }
}
