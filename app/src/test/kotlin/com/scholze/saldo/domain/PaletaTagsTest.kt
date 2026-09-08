package com.scholze.saldo.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class PaletaTagsTest {

    @Test fun `sao seis cores`() = assertEquals(6, PaletaTags.cores.size)

    @Test fun `sem tag nenhuma a primeira cor e a primeira da paleta`() =
        assertEquals(PaletaTags.cores[0], PaletaTags.proxima(emptyList()))

    @Test fun `a proxima e a menos usada`() {
        val c = PaletaTags.cores
        // as cores 0 e 1 já foram usadas; 2 é a primeira livre
        assertEquals(c[2], PaletaTags.proxima(listOf(c[0], c[1])))
    }

    @Test fun `com todas usadas uma vez volta a primeira`() =
        assertEquals(PaletaTags.cores[0], PaletaTags.proxima(PaletaTags.cores))

    @Test fun `desempate pela ordem da paleta`() {
        val c = PaletaTags.cores
        // 0 usada duas vezes, 1..5 uma vez cada: todas as cinco empatam, e a 1 vem antes
        assertEquals(c[1], PaletaTags.proxima(listOf(c[0], c[0]) + c.drop(1)))
    }

    @Test fun `cor fora da paleta nao conta`() =
        assertEquals(PaletaTags.cores[0], PaletaTags.proxima(listOf(0xFF000000L)))

    // ---- paleta A (arrumacao-1) ----

    @Test fun `as seis cores da paleta A`() = assertEquals(
        listOf(0xFFB63C62L, 0xFF9A5A00L, 0xFF719503L, 0xFF079E92L, 0xFF036EAEL, 0xFFA672DCL),
        PaletaTags.cores,
    )

    @Test fun `o mapa antigo para novo tem as seis entradas`() =
        assertEquals(6, PaletaTags.ANTIGAS_PARA_NOVAS.size)

    /** Seis destinos distintos: duas cores velhas não podem cair na mesma cor nova. */
    @Test fun `o mapa nao colide`() =
        assertEquals(6, PaletaTags.ANTIGAS_PARA_NOVAS.values.toSet().size)

    // cada uma das seis linhas da tabela da decisão 15 do spec — por FAMÍLIA de matiz, não por
    // posição na lista (casar por índice trocaria a família de quatro das seis).

    @Test fun `vinho vira vinho`() =
        assertEquals(0xFFB63C62L, PaletaTags.ANTIGAS_PARA_NOVAS[0xFFA6486BL])

    @Test fun `ambar vira ambar`() =
        assertEquals(0xFF9A5A00L, PaletaTags.ANTIGAS_PARA_NOVAS[0xFFB95A2EL])

    @Test fun `teal vira teal`() =
        assertEquals(0xFF079E92L, PaletaTags.ANTIGAS_PARA_NOVAS[0xFF2A7A86L])

    @Test fun `roxo vira lilas`() =
        assertEquals(0xFFA672DCL, PaletaTags.ANTIGAS_PARA_NOVAS[0xFF4B4BC4L])

    @Test fun `verde vira oliva`() =
        assertEquals(0xFF719503L, PaletaTags.ANTIGAS_PARA_NOVAS[0xFF14663AL])

    /** O único que troca de família: a paleta nova não tem laranja claro. */
    @Test fun `pessego vira azul`() =
        assertEquals(0xFF036EAEL, PaletaTags.ANTIGAS_PARA_NOVAS[0xFFE58A5AL])

    /** Idempotência sem depender da marca no DataStore: nenhuma cor NOVA é também uma cor VELHA. */
    @Test fun `nenhuma cor nova e tambem uma cor velha`() =
        assertEquals(
            emptySet<Long>(),
            PaletaTags.cores.toSet() intersect PaletaTags.ANTIGAS_PARA_NOVAS.keys,
        )

    /** É esta a razão de o repintar ser obrigatório: a cor velha deixou de contar no rodízio. */
    @Test fun `cor da paleta velha nao conta no rodizio`() =
        assertEquals(PaletaTags.cores[0], PaletaTags.proxima(listOf(0xFFA6486BL, 0xFFB95A2EL)))

    /** Uma cor fora da paleta velha (importada, escolhida à mão) não é tocada pelo mapa. */
    @Test fun `cor fora da paleta velha nao esta no mapa`() =
        assertEquals(null, PaletaTags.ANTIGAS_PARA_NOVAS[0xFF000000L])
}
