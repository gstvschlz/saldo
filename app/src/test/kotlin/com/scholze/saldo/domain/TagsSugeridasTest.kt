package com.scholze.saldo.domain

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class TagsSugeridasTest {

    private val hoje = LocalDate.parse("2026-09-08")

    private fun tag(id: Long, nome: String) = Tag(id = id, nome = nome, cor = 0xFFB63C62L)

    private fun mov(data: String, tags: List<Tag>) = Movimentacao(
        id = 1, descricao = "m", valorCentavos = -10_00,
        data = LocalDate.parse(data), natureza = Natureza.DIARIO, tags = tags,
    )

    private val comida = tag(1, "comida")
    private val transporte = tag(2, "transporte")
    private val assinaturas = tag(3, "assinaturas")

    @Test
    fun asMaisUsadasNos90DiasVemPrimeiro() {
        val movs = listOf(
            mov("2026-09-01", listOf(transporte)),
            mov("2026-09-02", listOf(transporte)),
            mov("2026-09-03", listOf(comida)),
        )
        assertEquals(
            listOf("transporte", "comida", "assinaturas"),
            TagsSugeridas.paraFila(listOf(comida, transporte, assinaturas), movs, hoje).map { it.nome },
        )
    }

    /** Empate — inclusive o empate em zero — vai pela ordem alfabética. */
    @Test
    fun oEmpateVaiPorOrdemAlfabetica() =
        assertEquals(
            listOf("assinaturas", "comida", "transporte"),
            TagsSugeridas.paraFila(listOf(transporte, comida, assinaturas), emptyList(), hoje).map { it.nome },
        )

    /** Um uso de cinco meses atrás não conta: a fileira é sobre o hábito de agora. */
    @Test
    fun usoAntigoNaoConta() =
        assertEquals(
            listOf("assinaturas", "comida", "transporte"),
            TagsSugeridas.paraFila(
                listOf(comida, transporte, assinaturas),
                listOf(mov("2026-04-01", listOf(transporte))),
                hoje,
            ).map { it.nome },
        )

    /** Uma data no futuro (uma agendada) não é hábito nenhum. */
    @Test
    fun usoFuturoNaoConta() =
        assertEquals(
            listOf("assinaturas", "comida", "transporte"),
            TagsSugeridas.paraFila(
                listOf(comida, transporte, assinaturas),
                listOf(mov("2026-12-01", listOf(transporte))),
                hoje,
            ).map { it.nome },
        )

    @Test
    fun cortaEmSeis() =
        assertEquals(6, TagsSugeridas.paraFila((1L..9L).map { tag(it, "tag$it") }, emptyList(), hoje).size)
}
