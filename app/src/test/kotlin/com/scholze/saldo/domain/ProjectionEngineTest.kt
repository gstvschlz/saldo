package com.scholze.saldo.domain

import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProjectionEngineTest {
    private val jul = YearMonth.of(2026, 7)
    private val cartao = CartaoConfig(nome = "nubank", fechamentoDia = 28, vencimentoDia = 5)

    private fun mov(dia: String, centavos: Long, natureza: Natureza = Natureza.DIARIO, rec: Long? = null, tags: List<Tag> = emptyList()) =
        Movimentacao(descricao = "m", valorCentavos = centavos, data = LocalDate.parse(dia), natureza = natureza, recorrenciaId = rec, tags = tags)

    private fun input(
        movs: List<Movimentacao> = emptyList(),
        recs: List<Recorrencia> = emptyList(),
        materializados: Set<YearMonth> = setOf(jul),
        hoje: String = "2026-07-20",
    ) = LedgerInput(
        saldoInicialCentavos = 100_000_00,
        saldoInicialData = LocalDate.parse("2026-07-01"),
        movimentacoes = movs,
        recorrencias = recs,
        mesesMaterializados = materializados,
        cartao = cartao,
        hoje = LocalDate.parse(hoje),
    )

    @Test
    fun saldoCorrePorDia() {
        val m = ProjectionEngine.mes(input(listOf(mov("2026-07-10", -10_00), mov("2026-07-12", 50_00))), jul, FiltroLedger.TODAS)
        assertEquals(31, m.dias.size)
        assertEquals(100_000_00L, m.dias[8].saldoCentavos)         // dia 9: ainda saldo inicial
        assertEquals(99_990_00L, m.dias[9].saldoCentavos)          // dia 10
        assertEquals(100_040_00L, m.dias[11].saldoCentavos)        // dia 12
        assertEquals(100_040_00L, m.dias[30].saldoCentavos)        // fim do mês
    }

    @Test
    fun mesPassadoEhSaldoRealSemEstimativa() {
        val m = ProjectionEngine.mes(
            input(listOf(mov("2026-06-10", -10_00)), materializados = setOf(YearMonth.of(2026, 6)), hoje = "2026-07-20")
                .copy(saldoInicialData = LocalDate.parse("2026-06-01")),
            YearMonth.of(2026, 6), FiltroLedger.TODAS,
        )
        assertEquals(0L, m.estimativaCentavos)
        assertEquals(99_990_00L, m.saldoProjetadoCentavos)
    }

    @Test
    fun estimativaUsaSomenteAvulsasDiario30Dias() {
        // 3.000,00 em avulsas DIARIO nos últimos 30 dias -> média 100,00/dia; 11 dias restantes em jul
        val movs = listOf(
            mov("2026-07-05", -1_500_00),
            mov("2026-07-15", -1_500_00),
            mov("2026-07-10", -999_00, natureza = Natureza.ECONOMIA),   // fora da média
            mov("2026-07-11", -888_00, rec = 1L),                        // recorrente: fora da média
        )
        val m = ProjectionEngine.mes(input(movs), jul, FiltroLedger.TODAS)
        assertEquals(100_00L * 11, m.estimativaCentavos)
    }

    @Test
    fun projetadoSomaAgendadasEDescontaEstimativa() {
        val movs = listOf(
            mov("2026-07-10", -3_000_00),                 // passada (média 100/dia)
            mov("2026-07-25", -200_00),                   // agendada futura
        )
        val m = ProjectionEngine.mes(input(movs), jul, FiltroLedger.TODAS)
        // real hoje = 97.000; +(-200) agendada; -1.100 estimativa
        assertEquals(97_000_00L - 200_00 - 1_100_00, m.saldoProjetadoCentavos)
        assertEquals(1_100_00L, m.estimativaCentavos)
    }

    @Test
    fun mesVirtualExpandeTemplates() {
        val rec = Recorrencia(id = 1, descricao = "salário", valorCentavos = 8_240_00, natureza = Natureza.DIARIO, diaDoMes = 15, inicio = YearMonth.of(2026, 1))
        val ago = YearMonth.of(2026, 8)
        val m = ProjectionEngine.mes(input(recs = listOf(rec), materializados = setOf(jul)), ago, FiltroLedger.TODAS)
        val dia15 = m.dias[14]
        assertEquals(1, dia15.itens.size)
        assertEquals(true, dia15.itens[0].recorrente)
    }

    @Test
    fun mesMaterializadoNaoDuplicaTemplates() {
        val rec = Recorrencia(id = 1, descricao = "salário", valorCentavos = 8_240_00, natureza = Natureza.DIARIO, diaDoMes = 15, inicio = YearMonth.of(2026, 1))
        val instancia = mov("2026-07-15", 8_240_00, rec = 1L)
        val m = ProjectionEngine.mes(input(listOf(instancia), listOf(rec)), jul, FiltroLedger.TODAS)
        assertEquals(1, m.dias[14].itens.size)
    }

    @Test
    fun faturaApareceNoVencimentoENaoAsCompras() {
        val movs = listOf(mov("2026-07-10", -150_00, natureza = Natureza.CARTAO))
        val ago = YearMonth.of(2026, 8)
        val mJul = ProjectionEngine.mes(input(movs), jul, FiltroLedger.TODAS)
        assertEquals(0, mJul.dias[9].itens.size)                    // compra não aparece dia 10
        val mAgo = ProjectionEngine.mes(input(movs, materializados = setOf(jul, ago)), ago, FiltroLedger.TODAS)
        val dia5 = mAgo.dias[4].itens
        assertEquals(1, dia5.size)
        assertEquals(-150_00L, dia5[0].valorCentavos)               // fatura em 5/ago
    }

    @Test
    fun filtroRecalculaColunaSaldo() {
        val movs = listOf(
            mov("2026-07-10", -100_00),                              // avulsa DIARIO
            mov("2026-07-10", -2_400_00, rec = 2L),                  // fixa
        )
        val diarios = ProjectionEngine.mes(input(movs), jul, FiltroLedger.DIARIOS)
        assertEquals(99_900_00L, diarios.dias[9].saldoCentavos)
        val fixas = ProjectionEngine.mes(input(movs), jul, FiltroLedger.FIXAS)
        assertEquals(97_600_00L, fixas.dias[9].saldoCentavos)
    }

    @Test
    fun totaisDoMes() {
        val comida = Tag(id = 1, nome = "comida", cor = 0xFFA6486BL)
        val movs = listOf(
            mov("2026-07-15", 8_240_00),
            mov("2026-07-10", -1_000_00, tags = listOf(comida)),
            mov("2026-07-11", -1_500_00, natureza = Natureza.ECONOMIA),
            mov("2026-07-12", -300_00, natureza = Natureza.CARTAO),
        )
        val t = ProjectionEngine.totais(input(movs), jul)
        assertEquals(8_240_00L, t.entradasCentavos)
        assertEquals(1_000_00L, t.saidasPorNatureza[Natureza.DIARIO])
        assertEquals(1_500_00L, t.saidasPorNatureza[Natureza.ECONOMIA])
        assertEquals(300_00L, t.saidasPorNatureza[Natureza.CARTAO])
        assertEquals(1_500_00L, t.economiaBucketCentavos)
        assertEquals(-300_00L, t.faturaAtual!!.totalCentavos)       // ciclo aberto em 20/jul
        assertEquals(LocalDate.parse("2026-07-28"), t.fechamentoFaturaAtual)
        assertEquals(comida to 1_000_00L, t.topTags.first())
    }

    @Test
    fun faturaAtualNulaSemCompras() {
        assertNull(ProjectionEngine.totais(input(), jul).faturaAtual)
    }

    @Test
    fun mediaIgnoraLancamentosAnterioresAoSaldoInicial() {
        // saldoInicialData é 2026-07-01; este avulso está dentro da janela de 30 dias mas antes do piso.
        val movs = listOf(mov("2026-06-25", -3_000_00))
        val m = ProjectionEngine.mes(input(movs), jul, FiltroLedger.TODAS)
        assertEquals(0L, m.estimativaCentavos)
    }

    @Test
    fun faturaAtualCompletaAoVerMesPassado() {
        val movs = listOf(
            mov("2026-07-05", -100_00, natureza = Natureza.CARTAO),
            mov("2026-07-10", -50_00, natureza = Natureza.CARTAO),
        )
        val jun = YearMonth.of(2026, 6)
        val t = ProjectionEngine.totais(
            input(movs, materializados = setOf(jun, jul)).copy(saldoInicialData = LocalDate.parse("2026-06-01")),
            jun,
        )
        assertEquals(-150_00L, t.faturaAtual!!.totalCentavos)
    }

    @Test
    fun deltaDoMesFuturoCobraSoOMesFuturo() {
        // média 100,00/dia (3.000,00 em avulsas DIARIO na janela); nenhuma agendada em ago.
        val movs = listOf(mov("2026-07-10", -3_000_00))
        val ago = YearMonth.of(2026, 8)
        val m = ProjectionEngine.mes(input(movs), ago, FiltroLedger.TODAS)
        assertEquals(-(100_00L * 31), m.deltaNoMesCentavos)
    }
}
