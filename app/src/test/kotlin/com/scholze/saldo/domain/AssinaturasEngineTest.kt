package com.scholze.saldo.domain

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AssinaturasEngineTest {

    private val hoje = LocalDate.parse("2026-09-08")

    private var proximoId = 0L

    private fun mov(
        data: String,
        centavos: Long = -39_90,
        descricao: String = "netflix",
        rec: Long? = null,
    ) = Movimentacao(
        id = ++proximoId, descricao = descricao, valorCentavos = centavos,
        data = LocalDate.parse(data), natureza = Natureza.DIARIO, recorrenciaId = rec,
    )

    private fun candidatas(movs: List<Movimentacao>, dispensadas: Set<String> = emptySet()) =
        AssinaturasEngine.candidatas(
            LedgerInput(
                saldoInicialCentavos = 100_000_00,
                saldoInicialData = LocalDate.parse("2026-01-01"),
                movimentacoes = movs,
                recorrencias = emptyList(),
                mesesMaterializados = emptySet(),
                cartao = CartaoConfig(),
                hoje = hoje,
            ),
            hoje,
            dispensadas,
        )

    private val tresMeses = listOf(mov("2026-06-10"), mov("2026-07-10"), mov("2026-08-10"))

    @Test
    fun tresMesesSeguidosComOMesmoValorEcandidata() {
        val c = candidatas(tresMeses).single()
        assertEquals("netflix", c.chave)
        assertEquals("netflix", c.descricao)
        assertEquals(3, c.meses)
        assertEquals(-39_90L, c.valorCentavos)
        assertEquals(10, c.diaDoMes)
        assertNull(c.valorAnteriorCentavos)
        assertEquals(LocalDate.parse("2026-08-10"), c.ocorrenciaMaisRecente.data)
    }

    @Test fun doisMesesNaoBastam() =
        assertEquals(emptyList<Assinatura>(), candidatas(listOf(mov("2026-07-10"), mov("2026-08-10"))))

    /** jun, ago, set: nenhuma sequência de três meses seguidos. */
    @Test fun tresMesesComBuracoNoMeioNaoE() =
        assertEquals(
            emptyList<Assinatura>(),
            candidatas(listOf(mov("2026-06-10"), mov("2026-08-10"), mov("2026-09-05"))),
        )

    /** Duas cobranças no mesmo mês quebram o padrão de assinatura — e derrubam a candidata. */
    @Test fun duasOcorrenciasNumDosMesesDerruba() =
        assertEquals(
            emptyList<Assinatura>(),
            candidatas(listOf(mov("2026-06-10"), mov("2026-07-10"), mov("2026-07-22"), mov("2026-08-10"))),
        )

    /** A sequência pode terminar no mês corrente — a cobrança deste mês já caiu. */
    @Test
    fun sequenciaQueTerminaNoMesCorrenteEcandidata() {
        val c = candidatas(listOf(mov("2026-07-05"), mov("2026-08-05"), mov("2026-09-05"))).single()
        assertEquals(3, c.meses)
        assertEquals(LocalDate.parse("2026-09-05"), c.ocorrenciaMaisRecente.data)
    }

    /**
     * mar, abr, mai vistos de setembro: três meses seguidos, mas a última cobrança foi há quatro
     * meses. Isso não é uma assinatura a cadastrar, é uma que o usuário cancelou — e "tornar
     * mensal" criaria uma recorrência de algo que não existe mais.
     */
    @Test fun tresMesesQueTerminamHaQuatroMesesNaoE() =
        assertEquals(
            emptyList<Assinatura>(),
            candidatas(listOf(mov("2026-03-10"), mov("2026-04-10"), mov("2026-05-10"))),
        )

    /** A borda: julho, visto de setembro, já é um mês tarde demais. */
    @Test fun sequenciaQueParaDoisMesesAtrasNaoE() =
        assertEquals(
            emptyList<Assinatura>(),
            candidatas(listOf(mov("2026-05-10"), mov("2026-06-10"), mov("2026-07-10"))),
        )

    /**
     * A sequência que conta é a que chega até agora, não a maior da janela: quatro meses seguidos
     * que pararam em junho não emprestam o seu tamanho às duas cobranças recentes.
     */
    @Test fun aSequenciaQueContaEaQueChegaAteAgora() =
        assertEquals(
            emptyList<Assinatura>(),
            candidatas(
                listOf(
                    mov("2026-03-10"), mov("2026-04-10"), mov("2026-05-10"), mov("2026-06-10"),
                    mov("2026-08-10"), mov("2026-09-05"),
                ),
            ),
        )

    /** Assinatura reajusta: 8% acima da mediana ainda é a mesma coisa. */
    @Test
    fun variacaoDeOitoPorCentoAindaEcandidata() {
        val c = candidatas(listOf(mov("2026-06-10"), mov("2026-07-10"), mov("2026-08-10", -43_09))).single()
        assertEquals(-43_09L, c.valorCentavos)
        assertEquals(-39_90L, c.valorAnteriorCentavos)
    }

    @Test fun variacaoDeTrintaPorCentoNaoE() =
        assertEquals(
            emptyList<Assinatura>(),
            candidatas(listOf(mov("2026-06-10"), mov("2026-07-10"), mov("2026-08-10", -51_87))),
        )

    @Test
    fun diasProximosSaoUmaAssinatura() {
        val c = candidatas(listOf(mov("2026-06-03"), mov("2026-07-05"), mov("2026-08-04"))).single()
        assertEquals(4, c.diaDoMes)   // a mediana de 3, 4 e 5
    }

    @Test fun diasEspalhadosNaoSao() =
        assertEquals(
            emptyList<Assinatura>(),
            candidatas(listOf(mov("2026-06-03"), mov("2026-07-12"), mov("2026-08-27"))),
        )

    @Test fun aQueJaTemRecorrenciaNuncaEntra() =
        assertEquals(
            emptyList<Assinatura>(),
            candidatas(listOf(mov("2026-06-10", rec = 1), mov("2026-07-10", rec = 1), mov("2026-08-10", rec = 1))),
        )

    @Test fun entradaNuncaEntra() =
        assertEquals(
            emptyList<Assinatura>(),
            candidatas(listOf(mov("2026-06-10", 39_90), mov("2026-07-10", 39_90), mov("2026-08-10", 39_90))),
        )

    @Test fun descricaoVaziaNuncaEntra() =
        assertEquals(
            emptyList<Assinatura>(),
            candidatas(
                listOf(
                    mov("2026-06-10", descricao = "  "),
                    mov("2026-07-10", descricao = ""),
                    mov("2026-08-10", descricao = ""),
                ),
            ),
        )

    @Test fun dispensadaNaoVolta() =
        assertEquals(emptyList<Assinatura>(), candidatas(tresMeses, dispensadas = setOf("netflix")))

    /** Meio por cento é arredondamento, não reajuste: a segunda linha não aparece. */
    @Test
    fun umaMudancaAbaixoDeUmPorCentoNaoViraSubiuDe() {
        val c = candidatas(listOf(mov("2026-06-10"), mov("2026-07-10"), mov("2026-08-10", -40_10))).single()
        assertNull(c.valorAnteriorCentavos)
    }

    /** A seção é um empurrão, não uma caixa de entrada: as três de maior valor, e mais nada. */
    @Test
    fun ateTresEAsDeMaiorValor() {
        val movs = listOf(20_00L, 50_00L, 10_00L, 90_00L).flatMapIndexed { i, v ->
            listOf(
                mov("2026-06-10", -v, "assinatura$i"),
                mov("2026-07-10", -v, "assinatura$i"),
                mov("2026-08-10", -v, "assinatura$i"),
            )
        }
        assertEquals(listOf("assinatura3", "assinatura1", "assinatura0"), candidatas(movs).map { it.chave })
    }

    /** A janela é os seis meses fechados mais o corrente: janeiro e fevereiro, de setembro, ficam fora. */
    @Test fun foraDaJanelaNaoConta() =
        assertEquals(
            emptyList<Assinatura>(),
            candidatas(listOf(mov("2026-01-10"), mov("2026-02-10"), mov("2026-03-10"))),
        )

    /** A chave passa por `Busca.normalizar`: "Café" e "cafe" são a mesma assinatura. */
    @Test
    fun aChaveIgnoraAcentoECaixa() {
        val c = candidatas(
            listOf(
                mov("2026-06-10", descricao = "Café"),
                mov("2026-07-10", descricao = "CAFE"),
                mov("2026-08-10", descricao = "cafe"),
            ),
        ).single()
        assertEquals("cafe", c.chave)
        assertEquals("cafe", c.descricao)   // como veio na ÚLTIMA ocorrência
    }
}
