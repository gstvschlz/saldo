package com.scholze.saldo.domain

import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * O cenário-base é o exemplo fechado do spec, dia 19 de setembro de 2026:
 *
 * ```
 *   renda             4.500,00   salário dia 5
 * − fixas             1.400,00   aluguel 1.200 + academia 155 + Netflix 45 (cartão)
 * − reserva             900,00   max(20% de 4.500 ; guardado 900)
 * − avulso até 18     1.151,20
 * ─────────────────────────────
 *   sobra             1.048,80  ÷ 12 dias = R$ 87,40
 * ```
 */
class TetoEngineTest {

    private val hoje = LocalDate.of(2026, 9, 19)
    private val ancora = LocalDate.of(2025, 1, 1)
    private val setembro = YearMonth.of(2026, 9)

    private fun mov(dia: Int, centavos: Long, natureza: Natureza = Natureza.DIARIO, mes: Int = 9) =
        Movimentacao(
            id = 1,
            descricao = "x",
            valorCentavos = centavos,
            data = LocalDate.of(2026, mes, dia),
            natureza = natureza,
        )

    private fun rec(id: Long, centavos: Long, dia: Int, natureza: Natureza = Natureza.DIARIO) =
        Recorrencia(
            id = id,
            descricao = "r$id",
            valorCentavos = centavos,
            natureza = natureza,
            diaDoMes = dia,
            inicio = YearMonth.of(2025, 1),
        )

    private val salario = rec(1, 450_000, dia = 5)
    private val aluguel = rec(2, -120_000, dia = 10)
    private val academia = rec(3, -15_500, dia = 8)
    private val netflix = rec(4, -4_500, dia = 15, natureza = Natureza.CARTAO)
    private fun poupanca(centavos: Long = -90_000) = rec(5, centavos, dia = 6, natureza = Natureza.ECONOMIA)

    /** R$ 1.151,20 em três saídas avulsas, todas antes de hoje. */
    private val avulsasAteOntem = listOf(mov(2, -50_000), mov(7, -40_000), mov(14, -25_120))

    private fun input(
        movs: List<Movimentacao> = avulsasAteOntem,
        recs: List<Recorrencia> = listOf(salario, aluguel, academia, netflix, poupanca()),
        dia: LocalDate = hoje,
    ) = LedgerInput(
        saldoInicialCentavos = 100_000,
        saldoInicialData = ancora,
        movimentacoes = movs,
        recorrencias = recs,
        mesesMaterializados = emptySet(),
        cartao = CartaoConfig(fechamentoDia = 28, vencimentoDia = 5),
        hoje = dia,
    )

    private fun teto(
        movs: List<Movimentacao> = avulsasAteOntem,
        recs: List<Recorrencia> = listOf(salario, aluguel, academia, netflix, poupanca()),
        dia: LocalDate = hoje,
        meta: Int = 20,
    ) = TetoEngine.teto(input(movs, recs, dia), meta)

    // ---- o exemplo fechado ----

    @Test
    fun `o exemplo fechado do spec da 87,40 por dia`() {
        val t = teto()!!
        assertEquals(setembro, t.mes)
        assertEquals(104_880L, t.sobraDoMesCentavos)
        assertEquals(12, t.diasRestantes)
        assertEquals(8_740L, t.tetoCentavos)
        assertEquals(8_740L, t.restaCentavos)
    }

    @Test
    fun `a renda conta o salario que ainda nao caiu`() {
        // Dia 2, antes do salário do dia 5: o teto já enxerga os R$ 4.500 inteiros. Sem isso o
        // começo do mês seria sempre um mês sem renda.
        val t = teto(movs = emptyList(), dia = LocalDate.of(2026, 9, 2))!!
        assertEquals(29, t.diasRestantes)
        // 4.500 − 1.400 − 900 = 2.200 ÷ 29
        assertEquals(220_000L / 29, t.tetoCentavos)
    }

    // ---- sem renda ----

    @Test
    fun `mes sem entrada nenhuma nao tem teto`() {
        assertNull(teto(recs = listOf(aluguel, academia)))
    }

    @Test
    fun `mes sem movimento nenhum nao tem teto`() {
        assertNull(teto(movs = emptyList(), recs = emptyList()))
    }

    // ---- a reserva ----

    @Test
    fun `a meta e o piso quando se guardou menos que ela`() {
        // Guardou R$ 300, meta pede R$ 900: a reserva segura o lugar dos R$ 600 que faltam, então
        // o teto é o mesmo do cenário-base.
        val t = teto(recs = listOf(salario, aluguel, academia, netflix, poupanca(-30_000)))!!
        assertEquals(8_740L, t.tetoCentavos)
    }

    @Test
    fun `guardar acima da meta aperta o teto`() {
        // R$ 1.500 guardados contra uma meta de R$ 900: manda o real, porque o dinheiro saiu.
        // 4.500 − 1.400 − 1.500 − 1.151,20 = 448,80 ÷ 12
        val t = teto(recs = listOf(salario, aluguel, academia, netflix, poupanca(-150_000)))!!
        assertEquals(3_740L, t.tetoCentavos)
    }

    @Test
    fun `recorrencia de economia nao e cobrada duas vezes`() {
        // A poupança de R$ 900 é reserva, não fixa. Se ela caísse nas duas peneiras a sobra seria
        // R$ 148,80 e o teto R$ 12,40.
        assertEquals(8_740L, teto()!!.tetoCentavos)
    }

    @Test
    fun `economia avulsa empurra a reserva e nao conta como gasto`() {
        // R$ 200 avulsos para a poupança, somados aos R$ 900 recorrentes: reserva de R$ 1.100.
        // 4.500 − 1.400 − 1.100 − 1.151,20 = 848,80 ÷ 12. Contada TAMBÉM como gasto daria 5.406.
        val t = teto(movs = avulsasAteOntem + mov(10, -20_000, Natureza.ECONOMIA))!!
        assertEquals(84_880L / 12, t.tetoCentavos)
        assertEquals(7_073L, t.tetoCentavos)
    }

    @Test
    fun `sem meta a reserva e so o que se guardou de fato`() {
        // meta 0 e R$ 300 guardados: 4.500 − 1.400 − 300 − 1.151,20 = 1.648,80 ÷ 12
        val t = teto(recs = listOf(salario, aluguel, academia, netflix, poupanca(-30_000)), meta = 0)!!
        assertEquals(13_740L, t.tetoCentavos)
    }

    @Test
    fun `mes materializado da o mesmo teto que o virtual`() {
        // Com o mês materializado a expansão não roda: as recorrências viram linhas reais no
        // banco, com `recorrenciaId` preenchido. As fixas e a economia têm de continuar caindo
        // nas mesmas peneiras — é o caminho que o app usa em todo mês já aberto.
        val materializadas = listOf(
            mov(5, 450_000).copy(recorrenciaId = 1),
            mov(10, -120_000).copy(recorrenciaId = 2),
            mov(8, -15_500).copy(recorrenciaId = 3),
            mov(15, -4_500, Natureza.CARTAO).copy(recorrenciaId = 4),
            mov(6, -90_000, Natureza.ECONOMIA).copy(recorrenciaId = 5),
        )
        val t = TetoEngine.teto(
            LedgerInput(
                saldoInicialCentavos = 100_000,
                saldoInicialData = ancora,
                movimentacoes = avulsasAteOntem + materializadas,
                recorrencias = listOf(salario, aluguel, academia, netflix, poupanca()),
                mesesMaterializados = setOf(setembro),
                cartao = CartaoConfig(fechamentoDia = 28, vencimentoDia = 5),
                hoje = hoje,
            ),
            20,
        )!!
        assertEquals(8_740L, t.tetoCentavos)
    }

    // ---- o cartão ----

    @Test
    fun `compra avulsa no cartao pesa no dia da compra`() {
        // R$ 100 no cartão dia 16: sai da sobra agora, não no vencimento da fatura.
        val t = teto(movs = avulsasAteOntem + mov(16, -10_000, Natureza.CARTAO))!!
        assertEquals(94_880L, t.sobraDoMesCentavos)
        assertEquals(7_906L, t.tetoCentavos)
    }

    @Test
    fun `compra no cartao do mes passado nao volta pela fatura`() {
        // Compra em 20 de agosto: a fatura vence em 5 de setembro, mas o teto de setembro não a
        // cobra — ela já pesou no teto de agosto, no dia em que foi feita.
        val t = teto(movs = avulsasAteOntem + mov(20, -30_000, Natureza.CARTAO, mes = 8))!!
        assertEquals(8_740L, t.tetoCentavos)
    }

    @Test
    fun `recorrencia no cartao e fixa, nao gasto do dia`() {
        // A Netflix já está nas fixas do cenário-base. Sem ela a sobra cresce R$ 45.
        val semNetflix = teto(recs = listOf(salario, aluguel, academia, poupanca()))!!
        assertEquals(104_880L + 4_500L, semNetflix.sobraDoMesCentavos)
    }

    // ---- a conta regressiva do dia ----

    @Test
    fun `o gasto de hoje desce do resta e nao do teto`() {
        val t = teto(movs = avulsasAteOntem + mov(19, -30_000))!!
        assertEquals(8_740L, t.tetoCentavos)
        assertEquals(30_000L, t.gastoDeHojeCentavos)
        assertEquals(-21_260L, t.restaCentavos)
        assertEquals(74_880L, t.sobraDoMesCentavos)
        assertTrue(t.estourouODia)
        assertFalse(t.estourouOMes)
    }

    @Test
    fun `o furo de hoje so se dilui amanha`() {
        val gastou = avulsasAteOntem + mov(19, -10_200)
        val hojeAssim = teto(movs = gastou)!!
        val amanha = teto(movs = gastou, dia = LocalDate.of(2026, 9, 20))!!

        assertEquals(8_740L, hojeAssim.tetoCentavos)
        // 1.048,80 − 102,00 = 946,80 ÷ 11 dias
        assertEquals(11, amanha.diasRestantes)
        assertEquals(8_607L, amanha.tetoCentavos)
        assertEquals(0L, amanha.gastoDeHojeCentavos)
    }

    @Test
    fun `no ultimo dia do mes o teto e tudo que sobra`() {
        val t = teto(dia = LocalDate.of(2026, 9, 30))!!
        assertEquals(1, t.diasRestantes)
        assertEquals(104_880L, t.tetoCentavos)
    }

    // ---- o mês estourado ----

    @Test
    fun `mes estourado da teto negativo`() {
        val t = teto(movs = listOf(mov(3, -450_000)))!!
        // 4.500 − 1.400 − 900 − 4.500 = −2.300 ÷ 12
        assertEquals(-230_000L, t.sobraDoMesCentavos)
        assertEquals(-19_166L, t.tetoCentavos)
        assertTrue(t.estourouOMes)
    }

    @Test
    fun `o mesmo buraco fica mais negativo a cada dia`() {
        val estourado = listOf(mov(3, -450_000))
        val dia19 = teto(movs = estourado)!!.tetoCentavos
        val dia25 = teto(movs = estourado, dia = LocalDate.of(2026, 9, 25))!!.tetoCentavos

        assertTrue(dia19 < 0 && dia25 < 0)
        assertTrue("mesmo buraco, menos dias para diluir", dia25 < dia19)
    }
}
