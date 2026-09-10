package com.scholze.saldo.domain

import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LembretesEngineTest {
    private val jul = YearMonth.of(2026, 7)
    private val ago = YearMonth.of(2026, 8)
    /** Fecha 28, vence 5 do mês seguinte: uma compra de julho vence em 5 de agosto. */
    private val cartao = CartaoConfig(nome = "nubank", fechamentoDia = 28, vencimentoDia = 5)
    private val zona = ZoneId.of("America/Sao_Paulo")
    private val tudo = LembretesConfig(
        faturaAmanha = true, recorrenciaHoje = true, registrarGastos = true, fechamentoMes = true,
        etiquetarHoje = true,
    )

    private fun mov(
        dia: String,
        centavos: Long,
        natureza: Natureza = Natureza.DIARIO,
        rec: Long? = null,
        criadaEm: Long = 0,
        id: Long = 0,
        tags: List<Tag> = emptyList(),
    ) = Movimentacao(
        id = id, descricao = "m", valorCentavos = centavos, data = LocalDate.parse(dia), natureza = natureza,
        recorrenciaId = rec, criadaEm = criadaEm, tags = tags,
    )

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

    private fun criadaEm(dia: String): Long = LocalDate.parse(dia).atTime(10, 0).atZone(zona).toInstant().toEpochMilli()

    private fun avaliar(input: LedgerInput, slot: Slot, config: LembretesConfig = tudo) =
        LembretesEngine.avaliar(input, config, slot, zona)

    // ---- fatura vence amanhã ----

    @Test
    fun faturaQueVenceAmanhaAvisaComOTotal() {
        val i = input(movs = listOf(mov("2026-07-10", -250_00, Natureza.CARTAO)), materializados = setOf(jul, ago), hoje = "2026-08-04")
        val l = avaliar(i, Slot.INFORMATIVOS).single() as Lembrete.FaturaAmanha
        assertEquals(LocalDate.parse("2026-08-05"), l.fatura.vencimento)
        assertEquals(-250_00L, l.fatura.totalCentavos)
        assertEquals("nubank", l.nomeCartao)
    }

    @Test
    fun faturaSemComprasNaoAvisa() {
        val i = input(materializados = setOf(jul, ago), hoje = "2026-08-04")
        assertEquals(emptyList<Lembrete>(), avaliar(i, Slot.INFORMATIVOS))
    }

    @Test
    fun faturaDoisDiasAntesNaoAvisa() {
        val i = input(movs = listOf(mov("2026-07-10", -250_00, Natureza.CARTAO)), materializados = setOf(jul, ago), hoje = "2026-08-03")
        assertEquals(emptyList<Lembrete>(), avaliar(i, Slot.INFORMATIVOS))
    }

    /** No dia do vencimento a fatura é uma linha fixa do dia — entra em "recorrências hoje", não em "amanhã". */
    @Test
    fun faturaQueVenceHojeEntraNasRecorrenciasDeHoje() {
        val i = input(movs = listOf(mov("2026-07-10", -250_00, Natureza.CARTAO)), materializados = setOf(jul, ago), hoje = "2026-08-05")
        val l = avaliar(i, Slot.INFORMATIVOS).single() as Lembrete.RecorrenciasHoje
        assertEquals(listOf("fatura nubank"), l.itens.map { it.descricao })
        assertEquals(-250_00L, l.itens.single().valorCentavos)
    }

    // ---- recorrência hoje ----

    @Test
    fun recorrenciasDeHojeListamSoAsFixas() {
        val aluguel = Recorrencia(id = 1, descricao = "aluguel", valorCentavos = -2_400_00, natureza = Natureza.DIARIO, diaDoMes = 20, inicio = YearMonth.of(2026, 1))
        // jul não materializado: o template expande virtualmente no dia 20; a avulsa de hoje fica de fora.
        val i = input(movs = listOf(mov("2026-07-20", -30_00)), recs = listOf(aluguel), materializados = emptySet())
        val l = avaliar(i, Slot.INFORMATIVOS).single() as Lembrete.RecorrenciasHoje
        assertEquals(listOf("aluguel"), l.itens.map { it.descricao })
    }

    @Test
    fun semFixasHojeNaoAvisa() {
        assertEquals(emptyList<Lembrete>(), avaliar(input(movs = listOf(mov("2026-07-20", -30_00))), Slot.INFORMATIVOS))
    }

    // ---- fechamento do mês ----

    @Test
    fun fechamentoSoNoDia1ComOsNumerosDoMesAnterior() {
        val movs = listOf(mov("2026-07-10", -3_000_00), mov("2026-07-15", 8_240_00))
        val l = avaliar(input(movs, materializados = setOf(jul, ago), hoje = "2026-08-01"), Slot.INFORMATIVOS).single() as Lembrete.FechamentoMes
        assertEquals(jul, l.mes)
        assertEquals(5_240_00L, l.sobrouCentavos)      // saldoReal(fim jul) − saldoReal(fim jun)
        assertEquals(8_240_00L, l.entradasCentavos)
        assertEquals(3_000_00L, l.saidasCentavos)
    }

    @Test
    fun fechamentoNoDia2NaoAvisa() {
        val movs = listOf(mov("2026-07-10", -3_000_00), mov("2026-07-15", 8_240_00))
        assertEquals(emptyList<Lembrete>(), avaliar(input(movs, materializados = setOf(jul, ago), hoje = "2026-08-02"), Slot.INFORMATIVOS))
    }

    /** Um mês sem nenhuma movimentação não tem o que fechar — nada de "sobrou R$ 0,00". */
    @Test
    fun fechamentoDeMesVazioNaoAvisa() {
        assertEquals(emptyList<Lembrete>(), avaliar(input(materializados = setOf(jul, ago), hoje = "2026-08-01"), Slot.INFORMATIVOS))
    }

    // ---- registrar gastos (nudge) ----

    @Test
    fun nudgeQuandoNadaFoiCriadoHoje() {
        val i = input(movs = listOf(mov("2026-07-20", -30_00, criadaEm = criadaEm("2026-07-19"))))
        assertEquals(listOf(Lembrete.RegistrarGastos), avaliar(i, Slot.NUDGE))
    }

    /** Conta a data de CRIAÇÃO, não a da movimentação: uma despesa de ontem lançada hoje é "lançou hoje". */
    @Test
    fun nudgeCalaQuandoUmAvulsoFoiCriadoHoje() {
        val i = input(movs = listOf(mov("2026-07-19", -30_00, criadaEm = criadaEm("2026-07-20"))))
        assertEquals(emptyList<Lembrete>(), avaliar(i, Slot.NUDGE))
    }

    /** Abrir o mês materializa recorrências com `criadaEm` de hoje — isso não é lançar nada. */
    @Test
    fun nudgeIgnoraRecorrenciaMaterializadaHoje() {
        val i = input(movs = listOf(mov("2026-07-20", -2_400_00, rec = 1L, criadaEm = criadaEm("2026-07-20"))))
        assertEquals(listOf(Lembrete.RegistrarGastos), avaliar(i, Slot.NUDGE))
    }

    /**
     * 23:30 em São Paulo já é madrugada do dia seguinte em UTC — se o engine comparasse em UTC
     * (em vez da [zona] recebida), isto contaria como "não criado hoje" e o nudge disparava errado.
     */
    @Test
    fun nudgeUsaOFusoParaDecidirODia() {
        val criadaAs2330 = LocalDate.parse("2026-07-20").atTime(23, 30).atZone(zona).toInstant().toEpochMilli()
        val i = input(movs = listOf(mov("2026-07-20", -30_00, criadaEm = criadaAs2330)), hoje = "2026-07-20")
        assertEquals(emptyList<Lembrete>(), avaliar(i, Slot.NUDGE))
    }

    // ---- etiquetar os de hoje ----

    private val comida = Tag(id = 9, nome = "comida", cor = 0xFFB63C62L)

    @Test
    fun avisaQuantosLancamentosDeHojeEstaoSemTag() {
        val i = input(
            movs = listOf(
                mov("2026-07-20", -30_00, id = 1),
                mov("2026-07-20", -12_00, id = 2),
                mov("2026-07-20", -50_00, id = 3, tags = listOf(comida)),
            ),
        )
        val l = avaliar(i, Slot.NUDGE).filterIsInstance<Lembrete.EtiquetarHoje>().single()
        assertEquals(2, l.quantos)
        assertEquals(LocalDate.parse("2026-07-20"), l.dia)
    }

    /** Zero não vira notificação: "hoje: nada a etiquetar" é ruído. */
    @Test
    fun tudoEtiquetadoHojeNaoAvisa() {
        val i = input(movs = listOf(mov("2026-07-20", -30_00, id = 1, tags = listOf(comida))))
        assertTrue(avaliar(i, Slot.NUDGE).none { it is Lembrete.EtiquetarHoje })
    }

    /** Só HOJE: a fila do mês inteiro é assunto da tela, não desta notificação. */
    @Test
    fun lancamentoDeOntemSemTagNaoEntra() {
        val i = input(movs = listOf(mov("2026-07-19", -30_00, id = 1)))
        assertTrue(avaliar(i, Slot.NUDGE).none { it is Lembrete.EtiquetarHoje })
    }

    /** Ocorrência virtual (`id == 0`) não tem linha no banco para receber etiqueta. */
    @Test
    fun ocorrenciaVirtualNaoEntra() {
        val i = input(movs = listOf(mov("2026-07-20", -2_400_00, rec = 1L)))
        assertTrue(avaliar(i, Slot.NUDGE).none { it is Lembrete.EtiquetarHoje })
    }

    /** Compra de cartão não aparece como linha do dia: ela entra no total da fatura. */
    @Test
    fun compraDeCartaoNaoEntra() {
        val i = input(movs = listOf(mov("2026-07-20", -80_00, Natureza.CARTAO, id = 1)))
        assertTrue(avaliar(i, Slot.NUDGE).none { it is Lembrete.EtiquetarHoje })
    }

    @Test
    fun desligadoNaoAvisa() {
        val i = input(movs = listOf(mov("2026-07-20", -30_00, id = 1)))
        val so = LembretesConfig(registrarGastos = true)
        assertTrue(avaliar(i, Slot.NUDGE, so).none { it is Lembrete.EtiquetarHoje })
    }

    /**
     * Os dois do fim do dia não se contradizem: "registrar gastos" só sai quando NADA foi criado
     * hoje, "etiquetar" só quando alguma coisa foi — e ficou sem etiqueta.
     */
    @Test
    fun registrarEEtiquetarNuncaSaemJuntos() {
        val criouHoje = input(movs = listOf(mov("2026-07-20", -30_00, id = 1, criadaEm = criadaEm("2026-07-20"))))
        val lembretes = avaliar(criouHoje, Slot.NUDGE)
        assertTrue(lembretes.none { it is Lembrete.RegistrarGastos })
        assertTrue(lembretes.any { it is Lembrete.EtiquetarHoje })
    }

    // ---- slots e toggles ----

    @Test
    fun slotsNaoSeMisturam() {
        val i = input(movs = listOf(mov("2026-07-10", -250_00, Natureza.CARTAO, criadaEm = criadaEm("2026-07-10"))), materializados = setOf(jul, ago), hoje = "2026-08-04")
        val informativos = avaliar(i, Slot.INFORMATIVOS)
        val nudge = avaliar(i, Slot.NUDGE)
        assertTrue(informativos.single() is Lembrete.FaturaAmanha)
        assertEquals(listOf(Lembrete.RegistrarGastos), nudge)
    }

    @Test
    fun togglesDesligadosCalamTudo() {
        val i = input(movs = listOf(mov("2026-07-10", -250_00, Natureza.CARTAO)), materializados = setOf(jul, ago), hoje = "2026-08-04")
        assertEquals(emptyList<Lembrete>(), avaliar(i, Slot.INFORMATIVOS, LembretesConfig()))
        assertEquals(emptyList<Lembrete>(), avaliar(i, Slot.NUDGE, LembretesConfig()))
    }

    /**
     * Fatura amanhã + recorrência hoje no mesmo cenário: cada toggle liga só a própria regra, e
     * com as duas ligadas a ordem é fatura antes de recorrência (a ordem do `buildList` do engine).
     */
    @Test
    fun cadaToggleLigaSoASuaRegra() {
        val aluguel = Recorrencia(id = 1, descricao = "aluguel", valorCentavos = -2_400_00, natureza = Natureza.DIARIO, diaDoMes = 4, inicio = YearMonth.of(2026, 1))
        // ago já está materializado (abaixo), então a instância de hoje precisa ser uma linha real.
        val i = input(
            movs = listOf(mov("2026-07-10", -250_00, Natureza.CARTAO), mov("2026-08-04", -2_400_00, rec = 1L)),
            recs = listOf(aluguel),
            materializados = setOf(jul, ago),
            hoje = "2026-08-04",
        )

        val soFatura = avaliar(i, Slot.INFORMATIVOS, LembretesConfig(faturaAmanha = true))
        assertTrue(soFatura.single() is Lembrete.FaturaAmanha)

        val soRecorrencia = avaliar(i, Slot.INFORMATIVOS, LembretesConfig(recorrenciaHoje = true))
        assertTrue(soRecorrencia.single() is Lembrete.RecorrenciasHoje)

        val ambos = avaliar(i, Slot.INFORMATIVOS, LembretesConfig(faturaAmanha = true, recorrenciaHoje = true))
        assertEquals(2, ambos.size)
        assertTrue(ambos[0] is Lembrete.FaturaAmanha)
        assertTrue(ambos[1] is Lembrete.RecorrenciasHoje)
    }
}
