package com.scholze.saldo.domain

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class BuscaTest {

    private val hoje = LocalDate.parse("2026-09-07")
    private val mercado = Tag(id = 1, nome = "mercado", cor = 1L)

    private fun mov(id: Long, descricao: String, centavos: Long, dia: String, vararg tags: Tag) = Movimentacao(
        id = id, descricao = descricao, valorCentavos = centavos, data = LocalDate.parse(dia),
        natureza = Natureza.DIARIO, tags = tags.toList(),
    )

    private val todas = listOf(
        mov(1, "Pão de Açúcar", -340_00, "2026-04-12", mercado),
        mov(2, "uber", -23_90, "2026-09-01"),
        mov(3, "salário", 7_400_00, "2026-09-05"),
        mov(4, "aluguel", -1_690_00, "2026-09-10"),      // futuro: não entra
        mov(5, "farmácia", -16_90, "2026-08-20"),
    )

    private fun busca(q: String) = Busca.filtrar(todas, q, hoje).map { it.id }

    @Test fun `consulta em branco devolve vazio`() = assertEquals(emptyList<Long>(), busca("   "))

    @Test fun `descricao sem acento casa com acento`() = assertEquals(listOf(1L), busca("pao de acucar"))

    @Test fun `maiusculas nao importam`() = assertEquals(listOf(1L), busca("PÃO"))

    @Test fun `nome da tag casa`() = assertEquals(listOf(1L), busca("merc"))

    @Test fun `valor em reais casa`() = assertEquals(listOf(1L), busca("340"))

    @Test fun `valor com virgula casa`() = assertEquals(listOf(5L), busca("16,90"))

    @Test fun `so digitos casa reais e centavos`() {
        // 1690 lido como reais = 169000 (nada); lido como centavos = 1690 → farmácia
        assertEquals(listOf(5L), busca("1690"))
    }

    @Test fun `o futuro nao entra`() = assertEquals(emptyList<Long>(), busca("aluguel"))

    @Test fun `ordem decrescente por data`() = assertEquals(listOf(3L, 2L, 5L, 1L), busca("r"))

    @Test fun `normalizar tira acento e caixa`() = assertEquals("pao de acucar", Busca.normalizar(" Pão de Açúcar "))
}
