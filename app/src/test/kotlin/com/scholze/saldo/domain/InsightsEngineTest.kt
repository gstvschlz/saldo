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

    @Test
    fun deltaNegativoQuandoGastoDiminui() {
        val movs = listOf(
            mov("2026-06-10", -200_00, tags = listOf(comida)),
            mov("2026-07-10", -150_00, tags = listOf(comida)),
        )
        val p = InsightsEngine.paraOndeFoi(input(movs, materializados = setOf(jun, jul), saldoInicialData = "2026-06-01"), jul)
        // (150 - 200) / 200 * 100 = -25 %.
        assertEquals(-25, p.fatias.first { it.nome() == "comida" }.deltaPercent)
    }

    @Test
    fun deltaMenorQueUmPorCentoVira0() {
        val movs = listOf(
            mov("2026-06-10", -200_00, tags = listOf(comida)),   // 20 000 centavos
            mov("2026-07-10", -201_20, tags = listOf(comida)),   // 20 120 centavos
        )
        val p = InsightsEngine.paraOndeFoi(input(movs, materializados = setOf(jun, jul), saldoInicialData = "2026-06-01"), jul)
        // (20120 - 20000) / 20000 * 100 = 0,6 %: abaixo de 1 %, mostra "=" (0), não arredonda pra +1 %.
        assertEquals(0, p.fatias.first { it.nome() == "comida" }.deltaPercent)
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
    fun diaMaisCaroNuloQuandoNenhumGastoAvulsoNaJanela() {
        val movs = listOf(
            mov("2026-07-05", 500_00, descricao = "salario"),          // entrada: não é gasto
            mov("2026-07-10", -300_00, natureza = Natureza.ECONOMIA),  // economia: fora do padrão
            mov("2026-07-12", -999_00, rec = 1L),                      // recorrência: fora do padrão
        )
        val p = InsightsEngine.paraOndeFoi(input(movs), jul).padroes
        // Janela com >= 14 dias (1 a 20 de julho), mas nenhuma saída avulsa nela: sem o guard,
        // maxBy apontaria SEGUNDA por ser a primeira entrada do enum, um falso "dia mais caro".
        assertTrue(p.porDiaDaSemana.values.all { it == 0L })
        assertNull(p.diaMaisCaro)
    }

    @Test
    fun semDadosSuficientesNaoHaPadrao() {
        val p = InsightsEngine.paraOndeFoi(input(listOf(mov("2026-07-18", -100_00)), saldoInicialData = "2026-07-15"), jul).padroes
        assertNull(p.diaMaisCaro)
        // porDiaDaSemana continua populado mesmo sem padrão: só diaMaisCaro exige os 14 dias.
        assertEquals(100_00L, p.porDiaDaSemana[DayOfWeek.SATURDAY])   // única saída avulsa da janela, em 18/07 (sábado)
        // saldoInicialData (15/07) é depois do dia 1 do mês: a média conta só os dias que o
        // ledger cobre (15 a 20/07 = 6 dias), não os 20 dias corridos do mês.
        assertEquals(16_66L, p.avulsasPorDiaMes)   // 100_00 / 6 dias
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
        // Literal, não só a fiação: janela de 30 dias termina em 20/07, começa 21/06 — só as
        // avulsas de julho entram (02, 10, 15 = 200_00; 05/06 fica antes da janela). 200_00 / 30 = 666.
        assertEquals(666L, InsightsEngine.paraOndeFoi(i, jul).padroes.mediaDiaria30)
    }

    @Test
    fun recorrenciaNaoMaterializadaEntraNasFatiasMasNaoEAvulsa() {
        val internet = Recorrencia(
            id = 9, descricao = "internet", valorCentavos = -80_00, natureza = Natureza.DIARIO,
            diaDoMes = 10, inicio = jun, tags = listOf(moradia),
        )
        // Avulsa real, para o denominador de avulsasPorDiaMes não zerar por falta de dado.
        val movs = listOf(mov("2026-06-05", -20_00, tags = listOf(comida)))
        val i = input(movs, recs = listOf(internet), materializados = setOf(jul), saldoInicialData = "2026-06-01")
        val p = InsightsEngine.paraOndeFoi(i, jun)
        // jun não está em materializados: a recorrência vem da expansão virtual de movimentacoesDoMes.
        assertEquals(100_00L, p.saidasCentavos)                                          // 80 (virtual) + 20 (avulsa)
        assertEquals(80_00L, p.fatias.first { it.nome() == "moradia" }.centavos)
        assertEquals(20_00L, p.fatias.first { it.nome() == "comida" }.centavos)
        // A recorrência tem recorrenciaId != null: não conta como avulsa, mesmo vindo de expansão virtual.
        assertEquals(66L, p.padroes.avulsasPorDiaMes)                                    // só os 20_00 avulsos / 30 dias de junho
    }

    // ---- tendência ----

    @Test
    fun tendenciaTemSeisPontosTerminandoNoMes() {
        val t = InsightsEngine.tendencia(input(), jul)
        assertEquals(6, t.size)
        assertEquals(YearMonth.of(2026, 2), t.first().mes)
        assertEquals(jul, t.last().mes)
        // Meses antes do saldo inicial (1/jul) são zero, sem taxa.
        assertTrue(t.dropLast(1).all { it.entradas == 0L && it.saidas == 0L && it.sobrou == 0L && it.reservaAcumulada == 0L && it.taxaPoupanca == null })
    }

    @Test
    fun tendenciaSobrouReservaETaxa() {
        val mai = YearMonth.of(2026, 5)
        val movs = listOf(
            mov("2026-05-05", 1_000_00), mov("2026-05-10", -200_00, Natureza.ECONOMIA),
            mov("2026-06-05", 1_000_00), mov("2026-06-10", -300_00, Natureza.ECONOMIA),
            mov("2026-07-05", 1_000_00),
        )
        // hoje já em agosto: os três meses estão fechados, sobrou = saldoReal(fim) − saldoReal(fim anterior).
        val i = input(movs, materializados = setOf(mai, jun, jul), hoje = "2026-08-01", saldoInicialData = "2026-05-01")
        val t = InsightsEngine.tendencia(i, jul)
        val pMai = t.first { it.mes == mai }
        assertEquals(1_000_00L, pMai.entradas)
        assertEquals(200_00L, pMai.saidas)
        assertEquals(800_00L, pMai.sobrou)
        assertEquals(200_00L, pMai.reservaAcumulada)
        assertEquals(20, pMai.taxaPoupanca)
        val pJun = t.first { it.mes == jun }
        assertEquals(700_00L, pJun.sobrou)
        assertEquals(500_00L, pJun.reservaAcumulada)
        assertEquals(30, pJun.taxaPoupanca)
        val pJul = t.last()
        assertEquals(1_000_00L, pJul.sobrou)
        assertEquals(500_00L, pJul.reservaAcumulada)
        assertEquals(0, pJul.taxaPoupanca)
    }

    @Test
    fun taxaNulaSemEntradas() {
        val i = input(listOf(mov("2026-07-10", -100_00, Natureza.ECONOMIA)))
        assertNull(InsightsEngine.tendencia(i, jul).last().taxaPoupanca)
    }

    @Test
    fun taxaTruncaParaBaixo() {
        val movs = listOf(
            mov("2026-07-05", 1_000_00),
            mov("2026-07-10", -666_67, Natureza.ECONOMIA),
        )
        // Uma divisão inteira 66,667 % trunca para 66; arredondar daria 67.
        assertEquals(66, InsightsEngine.tendencia(input(movs), jul).last().taxaPoupanca)
    }

    @Test
    fun tendenciaMesAbertoSobrouDescontaEstimativa() {
        // hoje no meio de julho: julho ainda está aberto, e sobrou nesse ponto já desconta a
        // estimativa do gasto avulso restante até o fim do mês — não é só entradas − saídas
        // realizadas até hoje.
        val movs = listOf(
            mov("2026-07-05", 1_000_00),
            mov("2026-07-08", -200_00),
            mov("2026-07-10", -300_00, Natureza.ECONOMIA),
        )
        val p = InsightsEngine.tendencia(input(movs, hoje = "2026-07-15"), jul).last()
        assertEquals(1_000_00L, p.entradas)
        assertEquals(500_00L, p.saidas)   // 200_00 (DIARIO) + 300_00 (ECONOMIA)
        // mediaDiaria = só a avulsa de 08/07 (200_00) dentro de [saldoInicialData, hoje] / 30 = 666.
        // estimativa = 666 * 16 dias restantes (16 a 31/jul) = 10_656.
        // saldoReal(hoje=15/07) = saldoInicial (100_000_00 = 10_000_000) + 1_000_00 − 200_00 − 300_00
        //   = 10_000_000 + 50_000 = 10_050_000; sem agendadas/faturas futuras.
        // projetado(jul) = 10_050_000 − 10_656 = 10_039_344.
        // projetado(jun): mês fechado (fim 30/06 <= hoje) e sem nenhuma movimentação antes de
        //   saldoInicialData (01/07) => saldoReal = saldoInicial = 10_000_000.
        // sobrou = 10_039_344 − 10_000_000 = 39_344 centavos = R$ 393,44.
        assertEquals(393_44L, p.sobrou)
    }

    /** A compra de cartão soma em `saidas` no mês da compra; ver faturaEntraNoVencimento para o
     *  outro lado — a fatura só chega ao saldo (sobrou/aCaminho) no vencimento, mês seguinte. */
    @Test
    fun saidasContaCompraDeCartaoNaDataDaCompra() {
        val movs = listOf(mov("2026-07-10", -250_00, Natureza.CARTAO))
        assertEquals(250_00L, InsightsEngine.tendencia(input(movs), jul).last().saidas)
    }

    // ---- a caminho ----

    @Test
    fun aCaminhoSoDepoisDeHojeAteOFimDoMes() {
        val aluguel = Recorrencia(id = 1, descricao = "aluguel", valorCentavos = -2_400_00, natureza = Natureza.DIARIO, diaDoMes = 28, inicio = YearMonth.of(2026, 1))
        val movs = listOf(
            mov("2026-07-20", -100_00),                       // hoje: fora
            mov("2026-07-25", -50_00),
            mov("2026-07-31", 200_00),                        // último dia: dentro
            mov("2026-08-01", -30_00),                        // mês seguinte: fora
            mov("2026-07-10", -250_00, Natureza.CARTAO),      // vence 5/ago: fora de julho
        )
        // julho não materializado: o aluguel expande virtualmente no dia 28.
        val a = InsightsEngine.aCaminho(input(movs, recs = listOf(aluguel), materializados = emptySet()), jul)
        assertEquals(false, a.mesEncerrado)
        assertEquals(listOf("2026-07-25", "2026-07-28", "2026-07-31"), a.itens.map { it.data.toString() })
        assertEquals(2_450_00L, a.saemCentavos)
        assertEquals(200_00L, a.entramCentavos)
    }

    @Test
    fun mesPassadoEstaEncerrado() {
        val a = InsightsEngine.aCaminho(input(listOf(mov("2026-07-25", -50_00)), hoje = "2026-08-10"), jul)
        assertTrue(a.mesEncerrado)
        assertTrue(a.itens.isEmpty())
        assertEquals(0L, a.saemCentavos)
    }

    @Test
    fun faturaEntraNoVencimento() {
        val i = input(listOf(mov("2026-07-10", -250_00, Natureza.CARTAO)), materializados = setOf(jul, ago), hoje = "2026-08-01")
        val a = InsightsEngine.aCaminho(i, ago)
        assertEquals(listOf("2026-08-05"), a.itens.map { it.data.toString() })
        assertTrue(a.itens.single().item is ItemDia.FaturaDia)
        assertEquals(250_00L, a.saemCentavos)
    }

    // ---- recorrências ----

    @Test
    fun recorrenciasAtivasEncerradasETotais() {
        val aluguel = Recorrencia(id = 1, descricao = "aluguel", valorCentavos = -2_400_00, natureza = Natureza.DIARIO, diaDoMes = 3, inicio = YearMonth.of(2026, 1))
        val salario = Recorrencia(id = 2, descricao = "salário", valorCentavos = 8_240_00, natureza = Natureza.DIARIO, diaDoMes = 5, inicio = YearMonth.of(2026, 1))
        val netflix = Recorrencia(id = 3, descricao = "netflix", valorCentavos = -50_00, natureza = Natureza.DIARIO, diaDoMes = 10, inicio = YearMonth.of(2026, 1), fim = jun)
        val academia = Recorrencia(id = 4, descricao = "academia", valorCentavos = -120_00, natureza = Natureza.DIARIO, diaDoMes = 1, inicio = YearMonth.of(2026, 9))
        val antiga = Recorrencia(id = 5, descricao = "antiga", valorCentavos = -10_00, natureza = Natureza.DIARIO, diaDoMes = 1, inicio = YearMonth.of(2026, 1), ativa = false)
        val r = InsightsEngine.recorrencias(input(recs = listOf(aluguel, salario, netflix, academia, antiga)), jul)
        assertEquals(listOf("academia", "aluguel", "salário"), r.ativas.map { it.descricao })      // por dia do mês: 1, 3, 5
        assertEquals(listOf("antiga", "netflix"), r.encerradas.map { it.descricao })
        assertEquals(8_240_00L, r.entramMes)
        assertEquals(2_400_00L, r.saemMes)                                                           // academia só começa em setembro
    }

    // ---- para onde foi ao longo do tempo ----

    private fun serie(movs: List<Movimentacao>, meses: Int = 3) =
        InsightsEngine.tagsAoLongoDoTempo(
            input(movs, materializados = setOf(jun, jul, ago), saldoInicialData = "2026-05-01"),
            jul,
            meses = meses,
        )

    @Test
    fun `a serie tem um ponto por mes, na ordem`() {
        val s = serie(emptyList())
        assertEquals(listOf(YearMonth.of(2026, 5), jun, jul), s.meses.map { it.mes })
    }

    @Test
    fun `cada mes segue a mesma ordem de grupos`() {
        val s = serie(
            listOf(
                mov("2026-06-10", -100_00, tags = listOf(comida)),
                mov("2026-07-10", -300_00, tags = listOf(moradia)),
            ),
        )
        // Os dois meses têm valores nas MESMAS posições, mesmo que um deles seja zero ali.
        s.meses.forEach { assertEquals(s.grupos.size, it.valores.size) }
        val iComida = s.grupos.indexOfFirst { it is GrupoGasto.DeTag && it.tag.id == comida.id }
        assertEquals(100_00L, s.meses.first { it.mes == jun }.valores[iComida])
        assertEquals(0L, s.meses.first { it.mes == jul }.valores[iComida])
    }

    @Test
    fun `o total do mes e a soma dos grupos`() {
        val s = serie(
            listOf(
                mov("2026-07-10", -100_00, tags = listOf(comida)),
                mov("2026-07-12", -50_00, tags = listOf(moradia)),
                mov("2026-07-15", -25_00),
            ),
        )
        val julho = s.meses.first { it.mes == jul }
        assertEquals(175_00L, julho.total)
        assertEquals(julho.valores.sum(), julho.total)
    }

    /** Cada movimentação conta uma vez, pela primeira etiqueta — senão a coluna passaria do total. */
    @Test
    fun `movimentacao com duas tags conta so na primeira`() {
        val s = serie(listOf(mov("2026-07-10", -100_00, tags = listOf(comida, moradia))))
        assertEquals(100_00L, s.meses.first { it.mes == jul }.total)
    }

    @Test
    fun `sem tag vira um grupo proprio`() {
        val s = serie(listOf(mov("2026-07-10", -80_00)))
        assertTrue(s.grupos.contains(GrupoGasto.SemTag))
        val i = s.grupos.indexOf(GrupoGasto.SemTag)
        assertEquals(80_00L, s.meses.first { it.mes == jul }.valores[i])
    }

    /** As maiores saem do total da JANELA, não de um mês: senão a cor trocaria de dono. */
    @Test
    fun `as maiores etiquetas saem da janela inteira`() {
        val s = InsightsEngine.tagsAoLongoDoTempo(
            input(
                listOf(
                    // comida é pequena em julho, mas a maior somando os três meses.
                    mov("2026-05-10", -900_00, tags = listOf(comida)),
                    mov("2026-06-10", -900_00, tags = listOf(comida)),
                    mov("2026-07-10", -10_00, tags = listOf(comida)),
                    mov("2026-07-11", -500_00, tags = listOf(moradia)),
                ),
                materializados = setOf(jun, jul, ago),
                saldoInicialData = "2026-05-01",
            ),
            jul,
            meses = 3,
            maioresTags = 1,
        )
        assertEquals(1, s.grupos.count { it is GrupoGasto.DeTag })
        assertTrue((s.grupos.first() as GrupoGasto.DeTag).tag.id == comida.id)
        // moradia não coube nas maiores e virou "outras".
        assertTrue(s.grupos.contains(GrupoGasto.Outras))
    }

    @Test
    fun `sem gasto nenhum a serie existe mas nao tem grupo`() {
        val s = serie(emptyList())
        assertTrue(s.grupos.isEmpty())
        assertTrue(s.meses.all { it.total == 0L })
    }

    /** A etiqueta é agrupada por id: renomear no meio da janela não parte a série em duas. */
    @Test
    fun `etiqueta renomeada continua a mesma serie`() {
        val comidaVelha = Tag(comida.id, "rango", comida.cor)
        val s = serie(
            listOf(
                mov("2026-06-10", -100_00, tags = listOf(comidaVelha)),
                mov("2026-07-10", -200_00, tags = listOf(comida)),
            ),
        )
        assertEquals(1, s.grupos.count { it is GrupoGasto.DeTag })
        val i = s.grupos.indexOfFirst { it is GrupoGasto.DeTag }
        assertEquals(100_00L, s.meses.first { it.mes == jun }.valores[i])
        assertEquals(200_00L, s.meses.first { it.mes == jul }.valores[i])
        // O nome exibido é o da aparição mais recente.
        assertEquals("comida", (s.grupos[i] as GrupoGasto.DeTag).tag.nome)
    }
}
