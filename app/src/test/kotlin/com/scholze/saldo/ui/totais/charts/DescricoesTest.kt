package com.scholze.saldo.ui.totais.charts

import com.scholze.saldo.domain.GrupoGasto
import com.scholze.saldo.domain.MesPorTag
import com.scholze.saldo.domain.PontoMes
import com.scholze.saldo.domain.Tag
import com.scholze.saldo.domain.TagsNoTempo
import java.time.DayOfWeek
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** As frases que o TalkBack lê nos gráficos. Puras, então testáveis aqui. */
class DescricoesTest {

    private fun ponto(
        mes: YearMonth,
        entradas: Long = 0,
        saidas: Long = 0,
        sobrou: Long = 0,
        reserva: Long = 0,
        taxa: Int? = null,
    ) = PontoMes(mes, entradas, saidas, sobrou, reserva, taxa)

    private val julho = YearMonth.of(2026, 7)
    private val agosto = YearMonth.of(2026, 8)

    // ---- tendência ----

    @Test
    fun aTendenciaDizEntrouSaiuESobrouDeCadaMes() {
        val frase = Descricoes.tendencia(
            listOf(ponto(julho, entradas = 5_000_00, saidas = 4_200_00, sobrou = 800_00)),
            oculto = false,
        )
        assertEquals("tendência: em julho entrou R$ 5.000,00, saiu R$ 4.200,00, sobrou R$ 800,00", frase)
    }

    @Test
    fun aTendenciaSeparaOsMesesComPontoEVirgula() {
        val frase = Descricoes.tendencia(listOf(ponto(julho), ponto(agosto)), oculto = false)
        assertTrue(frase, frase.contains("em julho") && frase.contains("; em agosto"))
    }

    @Test
    fun tendenciaSemPontosDizSemDados() =
        assertEquals("tendência: sem dados", Descricoes.tendencia(emptyList(), oculto = false))

    // ---- ritmo ----

    @Test
    fun oRitmoComparaComOCostume() {
        assertEquals(
            "ritmo: R$ 1.200,00 até hoje; o costume neste ponto do mês é R$ 1.500,00",
            Descricoes.ritmo(1_200_00, 1_500_00, oculto = false),
        )
    }

    /** Sem histórico não há costume — e inventar uma referência seria pior que não ter. */
    @Test
    fun oRitmoSemReferenciaDizQueNaoHaComparacao() =
        assertEquals("ritmo: R$ 1.200,00 até hoje, sem histórico para comparar", Descricoes.ritmo(1_200_00, null, false))

    // ---- poupança ----

    @Test
    fun aPoupancaDizATaxaDeCadaMesEAMeta() {
        val frase = Descricoes.poupanca(
            listOf(ponto(julho, taxa = 12), ponto(agosto, taxa = null)),
            metaPercent = 20,
        )
        assertEquals("poupança: julho 12%, agosto sem taxa, meta 20%", frase)
    }

    @Test
    fun semMetaAPoupancaNaoAnunciaAlvo() {
        val frase = Descricoes.poupanca(listOf(ponto(julho, taxa = 12)), metaPercent = 0)
        assertEquals("poupança: julho 12%", frase)
    }

    // ---- reserva ----

    @Test
    fun aReservaVaiDoPrimeiroAoUltimo() {
        val frase = Descricoes.reserva(
            listOf(ponto(julho, reserva = 2_000_00), ponto(agosto, reserva = 3_400_00)),
            oculto = false,
        )
        assertEquals("reserva: de R$ 2.000,00 em julho a R$ 3.400,00 em agosto", frase)
    }

    @Test
    fun umMesSoNaoViraUmIntervalo() =
        assertEquals(
            "reserva: R$ 2.000,00 em julho",
            Descricoes.reserva(listOf(ponto(julho, reserva = 2_000_00)), oculto = false),
        )

    // ---- dia da semana ----

    @Test
    fun oDiaDaSemanaListaOsSete() {
        val frase = Descricoes.porDiaDaSemana(
            mapOf(DayOfWeek.MONDAY to 80_00, DayOfWeek.TUESDAY to 120_00),
            oculto = false,
        )
        assertTrue(frase, frase.startsWith("por dia da semana: segunda-feira R$ 80,00, terça-feira R$ 120,00"))
        assertTrue(frase, frase.contains("domingo"))
    }

    @Test
    fun tudoZeradoNoDiaDaSemanaDizSemDados() =
        assertEquals("por dia da semana: sem dados", Descricoes.porDiaDaSemana(emptyMap(), oculto = false))

    // ---- para onde foi ----

    @Test
    fun paraOndeFoiDizPorcentagens() =
        assertEquals(
            "para onde foi: mercado 40%, casa 30%, sem tag 30%",
            Descricoes.paraOndeFoi(listOf("mercado", "casa", "sem tag"), listOf(40L, 30L, 30L)),
        )

    /** Uma fatia zerada não vira "0%" na frase: ela simplesmente não está na barra. */
    @Test
    fun fatiaZeradaNaoEntra() =
        assertEquals(
            "para onde foi: mercado 100%",
            Descricoes.paraOndeFoi(listOf("mercado", "casa"), listOf(50L, 0L)),
        )

    // ---- por tag no tempo ----

    @Test
    fun tagsNoTempoDizOTotalEAsFatias() {
        val serie = TagsNoTempo(
            grupos = listOf(GrupoGasto.DeTag(Tag(1, "mercado", 0)), GrupoGasto.SemTag),
            meses = listOf(MesPorTag(julho, listOf(75_00, 25_00), 100_00)),
        )
        assertEquals("por tag: em julho, R$ 100,00: mercado 75%, sem tag 25%", Descricoes.tagsNoTempo(serie, false))
    }

    // ---- a privacidade ----

    @Test
    fun ocultoNenhumaFraseDeDinheiroTemCifrao() {
        val pontos = listOf(ponto(julho, entradas = 5_000_00, saidas = 4_200_00, sobrou = 800_00, reserva = 2_000_00))
        val frases = listOf(
            Descricoes.tendencia(pontos, oculto = true),
            Descricoes.ritmo(1_200_00, 1_500_00, oculto = true),
            Descricoes.reserva(pontos, oculto = true),
            Descricoes.porDiaDaSemana(mapOf(DayOfWeek.MONDAY to 80_00), oculto = true),
        )
        frases.forEach { assertFalse(it, it.contains("R$")) }
        frases.forEach { assertTrue(it, it.contains("valor oculto")) }
    }

    /** Porcentagem não é dinheiro: a poupança continua legível com a privacidade ligada. */
    @Test
    fun aPoupancaNaoDependeDaPrivacidade() =
        assertEquals("poupança: julho 12%", Descricoes.poupanca(listOf(ponto(julho, taxa = 12)), 0))
}
