package com.scholze.saldo.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SugestaoEngineTest {

    private val agora = 1_788_000_000_000L
    private val config = CapturaConfig(ligada = true, marcados = setOf("com.nubank"))

    private fun candidata(
        pacote: String = "com.nubank",
        centavos: Long = 3_290,
        chave: String = "k1",
        em: Long = agora,
    ) = Deteccao(pacote = pacote, chave = chave, centavos = centavos, emMillis = em)

    private fun avaliar(
        c: Deteccao = candidata(),
        cfg: CapturaConfig = config,
        recentes: List<Deteccao> = emptyList(),
        hoje: List<Long> = emptyList(),
    ) = SugestaoEngine.avaliar(c, cfg, recentes, hoje)

    @Test fun `app marcado e valor novo vira sugestao nova`() {
        assertTrue(avaliar() is Sugestao.Nova)
    }

    @Test fun `app nao marcado e ignorado`() {
        assertEquals(Sugestao.Ignorar, avaliar(c = candidata(pacote = "com.whatsapp")))
    }

    @Test fun `captura desligada ignora ate o app marcado`() {
        assertEquals(Sugestao.Ignorar, avaliar(cfg = config.copy(ligada = false)))
    }

    @Test fun `valor zero e ignorado`() {
        assertEquals(Sugestao.Ignorar, avaliar(c = candidata(centavos = 0)))
    }

    @Test fun `mesmo app e valor dentro da janela e repetida`() {
        val antes = candidata(chave = "k0", em = agora - 9 * 60_000).copy(id = 7)
        assertEquals(Sugestao.Repetida(antes), avaliar(recentes = listOf(antes)))
    }

    @Test fun `fora da janela volta a ser nova`() {
        val antes = candidata(chave = "k0", em = agora - 11 * 60_000).copy(id = 7)
        assertTrue(avaliar(recentes = listOf(antes)) is Sugestao.Nova)
    }

    @Test fun `no limite exato da janela ainda e repetida`() {
        val antes = candidata(chave = "k0", em = agora - SugestaoEngine.JANELA_MILLIS).copy(id = 7)
        assertEquals(Sugestao.Repetida(antes), avaliar(recentes = listOf(antes)))
    }

    @Test fun `mesmo valor em app diferente nao e repetida`() {
        val outro = candidata(pacote = "com.itau", chave = "k0", em = agora - 60_000).copy(id = 7)
        assertTrue(avaliar(recentes = listOf(outro)) is Sugestao.Nova)
    }

    @Test fun `deteccao ja resolvida na janela nao volta a sugerir`() {
        val antes = candidata(chave = "k0", em = agora - 60_000).copy(id = 7, resolvida = true)
        assertEquals(Sugestao.Ignorar, avaliar(recentes = listOf(antes)))
    }

    @Test fun `valor ja lancado hoje avisa em vez de sugerir do zero`() {
        assertTrue(avaliar(hoje = listOf(3_290L)) is Sugestao.JaLancado)
    }

    @Test fun `valor parecido mas diferente nao conta como lancado`() {
        assertTrue(avaliar(hoje = listOf(3_291L)) is Sugestao.Nova)
    }

    /** A janela é mais específica: repetida é repetida, mesmo com o valor já lançado hoje. */
    @Test fun `repetida ganha de ja lancado`() {
        val antes = candidata(chave = "k0", em = agora - 60_000).copy(id = 7)
        assertEquals(Sugestao.Repetida(antes), avaliar(recentes = listOf(antes), hoje = listOf(3_290L)))
    }
}
