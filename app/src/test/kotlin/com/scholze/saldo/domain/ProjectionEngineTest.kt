package com.scholze.saldo.domain

import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
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

    /** No mês corrente o delta é projetado(M) − saldoReal(fim de M−1) — aqui 95.700 − 100.000. */
    @Test
    fun deltaDoMesAtualEhProjetadoMenosSaldoRealDoMesAnterior() {
        val movs = listOf(mov("2026-07-10", -3_000_00), mov("2026-07-25", -200_00))
        val m = ProjectionEngine.mes(input(movs), jul, FiltroLedger.TODAS)
        assertEquals(95_700_00L - 100_000_00L, m.deltaNoMesCentavos)
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

    private val comida = Tag(id = 1, nome = "comida", cor = 1)

    @Test
    fun filtroPorTag() {
        val movs = listOf(
            mov("2026-07-10", -100_00, tags = listOf(comida)),
            mov("2026-07-10", -900_00),
        )
        val m = ProjectionEngine.mes(input(movs), jul, FiltroLedger.TODAS, tagId = 1)
        assertEquals(1, m.dias[9].itens.size)
        assertEquals(-100_00L, m.dias[9].itens[0].valorCentavos)
        assertEquals(100_000_00L - 100_00, m.dias[9].saldoCentavos)
    }

    @Test
    fun filtroPorTagTambemPodaOSaldoDeArrasteEAsFaturas() {
        val movs = listOf(
            // Junho: só a etiquetada entra na semente do saldo de julho.
            mov("2026-06-10", -100_00, tags = listOf(comida)),
            mov("2026-06-11", -700_00),
            // Uma compra no cartão etiquetada: a fatura é um total agregado, não uma
            // linha da tag, e some junto com as outras quando o filtro está ligado.
            mov("2026-06-15", -300_00, natureza = Natureza.CARTAO, tags = listOf(comida)),
        )
        val m = ProjectionEngine.mes(
            input(movs, materializados = setOf(YearMonth.of(2026, 6), jul))
                .copy(saldoInicialData = LocalDate.parse("2026-06-01")),
            jul, FiltroLedger.TODAS, tagId = 1,
        )
        assertEquals(100_000_00L - 100_00, m.dias[0].saldoCentavos)
        // A fatura de 05/jul (ciclo de junho) não aparece em nenhum dia.
        assertEquals(emptyList<ItemDia>(), m.dias.flatMap { it.itens })
    }

    @Test
    fun projecaoIgnoraOFiltroDeTag() {
        // O hero é o saldo do mês inteiro; filtrar a lista não pode mexer nele.
        val movs = listOf(
            mov("2026-07-10", -100_00, tags = listOf(comida)),
            mov("2026-07-10", -900_00),
        )
        val semTag = ProjectionEngine.mes(input(movs), jul, FiltroLedger.TODAS)
        val comTag = ProjectionEngine.mes(input(movs), jul, FiltroLedger.TODAS, tagId = 1)
        assertEquals(semTag.saldoProjetadoCentavos, comTag.saldoProjetadoCentavos)
        assertEquals(semTag.estimativaCentavos, comTag.estimativaCentavos)
    }

    @Test
    fun `taxaGuardada e nula sem entrada`() {
        assertNull(ProjectionEngine.taxaGuardada(0, 200_00))
        assertNull(ProjectionEngine.taxaGuardada(-10, 200_00))
    }

    @Test
    fun `taxaGuardada arredonda e passa de cem`() {
        assertEquals(20, ProjectionEngine.taxaGuardada(1_000_00, 200_00))
        assertEquals(21, ProjectionEngine.taxaGuardada(1_000_00, 205_00))   // 20,5 → 21
        assertEquals(120, ProjectionEngine.taxaGuardada(1_000_00, 1_200_00))
        assertEquals(0, ProjectionEngine.taxaGuardada(1_000_00, 0))
    }

    @Test
    fun `mes carrega a taxa guardada do proprio mes`() {
        val set = YearMonth.of(2026, 9)
        val input = LedgerInput(
            saldoInicialCentavos = 0, saldoInicialData = LocalDate.parse("2026-01-01"),
            movimentacoes = listOf(
                Movimentacao(id = 1, descricao = "salário", valorCentavos = 5_000_00, data = LocalDate.parse("2026-09-05"), natureza = Natureza.DIARIO),
                Movimentacao(id = 2, descricao = "cdb", valorCentavos = -1_000_00, data = LocalDate.parse("2026-09-06"), natureza = Natureza.ECONOMIA),
                Movimentacao(id = 3, descricao = "mercado", valorCentavos = -300_00, data = LocalDate.parse("2026-09-07"), natureza = Natureza.DIARIO),
                Movimentacao(id = 4, descricao = "cdb", valorCentavos = -500_00, data = LocalDate.parse("2026-08-06"), natureza = Natureza.ECONOMIA),
            ),
            recorrencias = emptyList(), mesesMaterializados = emptySet(), cartao = CartaoConfig(),
            hoje = LocalDate.parse("2026-09-07"),
        )
        assertEquals(20, ProjectionEngine.mes(input, set, FiltroLedger.TODAS).taxaGuardada)
        // agosto: economia sem entrada → nulo, não 0
        assertNull(ProjectionEngine.mes(input, YearMonth.of(2026, 8), FiltroLedger.TODAS).taxaGuardada)
    }

    // ---- âncora, estimativa e expansão única (dados-1) ----

    /**
     * Uma lista que conta quantas vezes alguém pediu o iterador. `RecurrenceExpander.ocorrenciasNoMes`
     * faz `templates.mapNotNull { ... }`, então cada mês expandido é exatamente uma iteração — é o
     * contador do expansor sem furar a produção com um hook de teste.
     */
    private class ListaContada<T>(private val base: List<T>) : List<T> by base {
        var iteracoes = 0
            private set

        override fun iterator(): Iterator<T> {
            iteracoes++
            return base.iterator()
        }
    }

    private val aluguel = Recorrencia(
        id = 1, descricao = "aluguel", valorCentavos = -1_690_00,
        natureza = Natureza.DIARIO, diaDoMes = 10, inicio = YearMonth.of(2026, 7),
    )

    @Test
    fun compraNoCartaoAntesDaAncoraComFaturaVencendoDepoisConta() {
        // fecha 28 / vence 5: a compra de 20/06 cai no ciclo de junho, que vence em 05/07 — depois
        // da âncora (01/07). A fatura ainda não foi paga, então ela pesa.
        val input = input(listOf(mov("2026-06-20", -100_00, natureza = Natureza.CARTAO)))
        val faturas = ProjectionEngine.faturasAte(input, jul)
        assertEquals(listOf(YearMonth.of(2026, 6)), faturas.map { it.ciclo })
        assertEquals(-100_00L, faturas.single().totalCentavos)
        assertEquals(LocalDate.parse("2026-07-05"), faturas.single().vencimento)
        // e ela desce o saldo no dia do vencimento
        assertEquals(99_900_00L, ProjectionEngine.mes(input, jul, FiltroLedger.TODAS).dias[4].saldoCentavos)
    }

    @Test
    fun compraNoCartaoComFaturaVencidaAntesDaAncoraNaoConta() {
        // 20/05 cai no ciclo de maio, que vence em 05/06 — antes da âncora, portanto já paga.
        val input = input(listOf(mov("2026-05-20", -100_00, natureza = Natureza.CARTAO)))
        assertEquals(emptyList<YearMonth>(), ProjectionEngine.faturasAte(input, jul).map { it.ciclo })
    }

    @Test
    fun diarioAntesDaAncoraNaoConta() {
        val input = input(listOf(mov("2026-06-20", -100_00)))
        assertEquals(100_000_00L, ProjectionEngine.mes(input, jul, FiltroLedger.TODAS).dias[30].saldoCentavos)
    }

    /** 3.000,00 em avulsas DIARIO nos 30 dias até 20/09 → média de 100,00/dia; nada agendado depois de hoje. */
    private val emSetembro = LedgerInput(
        saldoInicialCentavos = 100_000_00,
        saldoInicialData = LocalDate.parse("2026-09-01"),
        movimentacoes = listOf(mov("2026-09-05", -1_500_00), mov("2026-09-15", -1_500_00)),
        recorrencias = emptyList(),
        mesesMaterializados = setOf(YearMonth.of(2026, 9)),
        cartao = cartao,
        hoje = LocalDate.parse("2026-09-20"),
    )

    @Test
    fun estimativaDeUmMesFuturoCobreSoAqueleMes() {
        // dezembro visto de setembro: 1 a 31 de dezembro são 30 dias de janela, não os 102 de hoje até lá
        assertEquals(
            30 * 100_00L,
            ProjectionEngine.mes(emSetembro, YearMonth.of(2026, 12), FiltroLedger.TODAS).estimativaCentavos,
        )
        // e o mês corrente continua contando de hoje até o fim dele
        assertEquals(
            10 * 100_00L,
            ProjectionEngine.mes(emSetembro, YearMonth.of(2026, 9), FiltroLedger.TODAS).estimativaCentavos,
        )
    }

    /**
     * O que se mostra e o que se desconta são duas contas. A projeção de dezembro parte do saldo
     * de hoje, então tem de pagar os 102 dias até 31/12 — os 30 do mês exibido deixariam de fora
     * o resto de setembro, outubro e novembro inteiros.
     */
    @Test
    fun projecaoDeUmMesFuturoDescontaAJanelaInteiraDesdeHoje() {
        val m = ProjectionEngine.mes(emSetembro, YearMonth.of(2026, 12), FiltroLedger.TODAS)
        assertEquals(97_000_00L - 102 * 100_00L, m.saldoProjetadoCentavos)
        assertEquals(30 * 100_00L, m.estimativaCentavos)
    }

    @Test
    fun efetivasEExpandidaUmaVezPorLedgerInput() {
        val recs = ListaContada(listOf(aluguel))
        val input = input(recs = recs, materializados = emptySet())

        assertSame(input.efetivas, input.efetivas)      // o `by lazy` guarda o resultado

        ProjectionEngine.mes(input, jul, FiltroLedger.TODAS)
        val depoisDoPrimeiro = recs.iteracoes
        assertTrue("o expansor tem de rodar ao menos uma vez", depoisDoPrimeiro > 0)

        ProjectionEngine.totais(input, jul)
        ProjectionEngine.movimentacoesDoMes(input, jul)
        ProjectionEngine.faturasAte(input, jul)
        ProjectionEngine.movimentacoesAte(input, jul)
        assertEquals("ninguém pode re-expandir o mesmo LedgerInput", depoisDoPrimeiro, recs.iteracoes)
    }

    /** Navegar em totais além do teto ainda projeta: a lista guardada é curta, e aí o motor expande de novo. */
    @Test
    fun umMesAlemDoTetoAindaEExpandido() {
        val input = input(recs = listOf(aluguel), materializados = emptySet())
        assertEquals(YearMonth.of(2027, 7), input.tetoExpansao)
        val longe = YearMonth.from(input.hoje).plusMonths(24)
        assertEquals(1, ProjectionEngine.mes(input, longe, FiltroLedger.TODAS).dias[9].itens.size)
    }
}
