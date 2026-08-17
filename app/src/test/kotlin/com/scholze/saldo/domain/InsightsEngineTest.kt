package com.scholze.saldo.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InsightsEngineTest {
    private val jun = YearMonth.of(2026, 6)
    private val jul = YearMonth.of(2026, 7)
    private val ago = YearMonth.of(2026, 8)
    private val cartao = CartaoConfig(nome = "nubank", fechamentoDia = 28, vencimentoDia = 5)
    private val comida = Tag(1, "comida", 0xFFA6486B)
    private val moradia = Tag(2, "moradia", 0xFFB95A2E)
    private val transporte = Tag(3, "transporte", 0xFF2A7A86)

    private fun mov(
        dia: String,
        centavos: Long,
        natureza: Natureza = Natureza.DIARIO,
        rec: Long? = null,
        tags: List<Tag> = emptyList(),
        descricao: String = "m",
    ) = Movimentacao(
        descricao = descricao, valorCentavos = centavos, data = LocalDate.parse(dia), natureza = natureza,
        recorrenciaId = rec, tags = tags,
    )

    private fun input(
        movs: List<Movimentacao> = emptyList(),
        recs: List<Recorrencia> = emptyList(),
        materializados: Set<YearMonth> = setOf(jul),
        hoje: String = "2026-07-20",
        saldoInicialData: String = "2026-07-01",
    ) = LedgerInput(
        saldoInicialCentavos = 100_000_00,
        saldoInicialData = LocalDate.parse(saldoInicialData),
        movimentacoes = movs,
        recorrencias = recs,
        mesesMaterializados = materializados,
        cartao = cartao,
        hoje = LocalDate.parse(hoje),
    )

    private fun Fatia.nome() = when (val g = grupo) {
        is GrupoGasto.DeTag -> g.tag.nome
        GrupoGasto.Outras -> "outras"
        GrupoGasto.SemTag -> "sem tag"
    }

    // ---- para onde foi: fatias ----

    @Test
    fun fatiasPorTagOrdenadasComSemTagNoFim() {
        val movs = listOf(
            mov("2026-07-02", -100_00, tags = listOf(comida)),
            mov("2026-07-03", -50_00, tags = listOf(comida)),
            mov("2026-07-04", -30_00, tags = listOf(moradia)),
            mov("2026-07-05", -20_00),
        )
        val p = InsightsEngine.paraOndeFoi(input(movs), jul)
        assertEquals(200_00L, p.saidasCentavos)
        assertEquals(listOf("comida", "moradia", "sem tag"), p.fatias.map { it.nome() })
        assertEquals(listOf(150_00L, 30_00L, 20_00L), p.fatias.map { it.centavos })
        assertEquals(0.75f, p.fatias[0].share, 0.001f)
        // Julho é o primeiro mês com dados: não há mês anterior para comparar.
        assertTrue(p.fatias.all { it.deltaPercent == null })
    }

    @Test
    fun deltaContraOMesAnterior() {
        val movs = listOf(
            mov("2026-06-10", -100_00, tags = listOf(comida)),
            mov("2026-06-11", -30_00, tags = listOf(moradia)),
            mov("2026-06-12", -80_00, tags = listOf(transporte)),
            mov("2026-07-10", -130_00, tags = listOf(comida)),
            mov("2026-07-11", -30_00, tags = listOf(moradia)),
        )
        val p = InsightsEngine.paraOndeFoi(input(movs, materializados = setOf(jun, jul), saldoInicialData = "2026-06-01"), jul)
        assertEquals(30, p.fatias.first { it.nome() == "comida" }.deltaPercent)
        assertEquals(0, p.fatias.first { it.nome() == "moradia" }.deltaPercent)
        // transporte não teve saída em julho: não entra na lista.
        assertTrue(p.fatias.none { it.nome() == "transporte" })
    }

    /** A lista conta a movimentação em cada tag; a barra a atribui só à primeira, para somar 100 %. */
    @Test
    fun movimentacaoComDuasTagsContaNasDuasNaListaMasUmaVezNaBarra() {
        val movs = listOf(mov("2026-07-02", -100_00, tags = listOf(comida, moradia)))
        val p = InsightsEngine.paraOndeFoi(input(movs), jul)
        assertEquals(listOf(100_00L, 100_00L), p.fatias.map { it.centavos })
        assertEquals(listOf("comida"), p.barra.map { it.nome() })
        assertEquals(1f, p.barra.sumOf { it.share.toDouble() }.toFloat(), 0.001f)
    }

    @Test
    fun barraTemTop4MaisOutrasESemTag() {
        val tags = (1..6).map { Tag(it.toLong(), "t$it", 1L) }
        val movs = tags.mapIndexed { i, t -> mov("2026-07-0${i + 1}", -(60_00L - i * 10_00L), tags = listOf(t)) } +
            mov("2026-07-09", -5_00)
        val p = InsightsEngine.paraOndeFoi(input(movs), jul)
        assertEquals(listOf("t1", "t2", "t3", "t4", "outras", "sem tag"), p.barra.map { it.nome() })
        assertEquals(30_00L, p.barra[4].centavos)                     // t5 (20) + t6 (10)
        assertEquals(1f, p.barra.sumOf { it.share.toDouble() }.toFloat(), 0.001f)
    }

    @Test
    fun semSaidasNoMes() {
        val p = InsightsEngine.paraOndeFoi(input(listOf(mov("2026-07-02", 100_00))), jul)
        assertEquals(0L, p.saidasCentavos)
        assertTrue(p.fatias.isEmpty())
        assertTrue(p.barra.isEmpty())
        assertTrue(p.maioresGastos.isEmpty())
    }

    // ---- maiores gastos ----

    @Test
    fun maioresGastosSaoOsCincoMaioresEmOrdem() {
        val movs = listOf(
            mov("2026-07-01", -10_00, descricao = "a"), mov("2026-07-02", -60_00, descricao = "b"),
            mov("2026-07-03", -30_00, descricao = "c"), mov("2026-07-04", -250_00, Natureza.CARTAO, descricao = "d"),
            mov("2026-07-05", -40_00, descricao = "e"), mov("2026-07-06", -20_00, descricao = "f"),
            mov("2026-07-07", 500_00, descricao = "entrada"),
        )
        val p = InsightsEngine.paraOndeFoi(input(movs), jul)
        assertEquals(listOf("d", "b", "e", "c", "f"), p.maioresGastos.map { it.descricao })
    }

    // ---- padrões ----

    /** 2026-07-20 é segunda; os sábados 4, 11 e 18 de julho carregam R$ 100,00 cada. */
    @Test
    fun diaMaisCaroPelaMediaPorDiaDaSemana() {
        val movs = listOf(
            mov("2026-07-04", -100_00), mov("2026-07-11", -100_00), mov("2026-07-18", -100_00),
            mov("2026-07-06", -50_00),
            mov("2026-07-13", -999_00, rec = 1L),                       // recorrência: fora dos padrões
            mov("2026-07-15", -999_00, natureza = Natureza.ECONOMIA),   // economia: fora
        )
        val p = InsightsEngine.paraOndeFoi(input(movs, materializados = setOf(jul)), jul).padroes
        assertEquals(DayOfWeek.SATURDAY, p.diaMaisCaro)
        assertEquals(100_00L, p.porDiaDaSemana[DayOfWeek.SATURDAY])   // 300 / 3 sábados na janela 1..20 jul
        assertEquals(16_66L, p.porDiaDaSemana[DayOfWeek.MONDAY])     // 50 / 3 segundas (6, 13, 20)
        assertEquals(0L, p.porDiaDaSemana[DayOfWeek.SUNDAY])
    }

    @Test
    fun semDadosSuficientesNaoHaPadrao() {
        val p = InsightsEngine.paraOndeFoi(input(listOf(mov("2026-07-18", -100_00)), saldoInicialData = "2026-07-15"), jul).padroes
        assertNull(p.diaMaisCaro)
    }

    @Test
    fun avulsasPorDiaDoMesCorrenteEPassadoEFuturo() {
        val movs = listOf(
            mov("2026-06-05", -300_00),
            mov("2026-07-02", -100_00), mov("2026-07-10", -50_00), mov("2026-07-15", -50_00),
            mov("2026-07-12", -999_00, rec = 1L),
        )
        val i = input(movs, materializados = setOf(jun, jul), saldoInicialData = "2026-06-01")
        assertEquals(10_00L, InsightsEngine.paraOndeFoi(i, jul).padroes.avulsasPorDiaMes)     // 200 / 20 dias
        assertEquals(10_00L, InsightsEngine.paraOndeFoi(i, jun).padroes.avulsasPorDiaMes)     // 300 / 30 dias
        assertNull(InsightsEngine.paraOndeFoi(i, ago).padroes.avulsasPorDiaMes)
        assertEquals(ProjectionEngine.mediaDiaria(i), InsightsEngine.paraOndeFoi(i, jul).padroes.mediaDiaria30)
    }
}
