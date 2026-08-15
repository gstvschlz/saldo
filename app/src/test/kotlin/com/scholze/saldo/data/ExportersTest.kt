package com.scholze.saldo.data

import com.scholze.saldo.domain.CartaoConfig
import com.scholze.saldo.domain.Movimentacao
import com.scholze.saldo.domain.Natureza
import com.scholze.saldo.domain.Recorrencia
import com.scholze.saldo.domain.Tag
import java.time.LocalDate
import java.time.YearMonth
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExportersTest {

    private val tag = Tag(id = 1, nome = "comida", cor = 0xFFA6486BL)

    // Descrição hostil de propósito: ponto-e-vírgula (o separador) e aspas (o escape).
    private val mov = Movimentacao(
        id = 3, descricao = "mercado; \"orgânico\"", valorCentavos = -189_90,
        data = LocalDate.parse("2026-07-14"), natureza = Natureza.DIARIO, tags = listOf(tag),
    )

    @Test
    fun csvEscapaEDelimita() {
        val csv = Exporters.csv(listOf(mov))
        val linhas = csv.trim().lines()
        assertEquals("data;descricao;valor_centavos;natureza;tags;recorrente", linhas[0])
        assertEquals("2026-07-14;\"mercado; \"\"orgânico\"\"\";-18990;DIARIO;comida;false", linhas[1])
    }

    @Test
    fun csvOrdenaPorDataEMarcaRecorrentes() {
        val antiga = mov.copy(id = 1, descricao = "aluguel", data = LocalDate.parse("2026-07-01"), recorrenciaId = 9, tags = emptyList())
        val csv = Exporters.csv(listOf(mov, antiga))
        val linhas = csv.trim().lines()
        assertEquals("2026-07-01;aluguel;-18990;DIARIO;;true", linhas[1])
        assertTrue(linhas[2].startsWith("2026-07-14;"))
    }

    @Test
    fun csvSemMovimentacoesAindaTemCabecalho() {
        assertEquals("data;descricao;valor_centavos;natureza;tags;recorrente", Exporters.csv(emptyList()).trim())
    }

    @Test
    fun jsonCompleto() {
        val rec = Recorrencia(
            id = 2, descricao = "salário", valorCentavos = 8_240_00,
            natureza = Natureza.DIARIO, diaDoMes = 15, inicio = YearMonth.of(2026, 1),
        )
        val settings = Settings(100_000_00, LocalDate.parse("2026-07-01"), CartaoConfig("nubank", 28, 5), true, Tema.SISTEMA)
        val json = JSONObject(Exporters.json(listOf(mov), listOf(rec), listOf(tag), settings))

        assertEquals(1, json.getInt("schema"))
        assertEquals(100_000_00L, json.getJSONObject("settings").getLong("saldoInicialCentavos"))
        assertEquals("2026-07-01", json.getJSONObject("settings").getString("saldoInicialData"))
        assertEquals("nubank", json.getJSONObject("settings").getString("cartaoNome"))
        assertEquals("salário", json.getJSONArray("recorrencias").getJSONObject(0).getString("descricao"))
        assertEquals("mercado; \"orgânico\"", json.getJSONArray("movimentacoes").getJSONObject(0).getString("descricao"))
        assertEquals("comida", json.getJSONArray("movimentacoes").getJSONObject(0).getJSONArray("tags").getString(0))
    }

    @Test
    fun jsonNaoLevaSaldoNemDataQuandoOnboardingNaoAconteceu() {
        val settings = Settings(null, null, CartaoConfig(), false, Tema.ESCURO)
        val s = JSONObject(Exporters.json(emptyList(), emptyList(), emptyList(), settings)).getJSONObject("settings")
        // org.json remove a chave num put(null): o dump não inventa um saldo zero.
        assertFalse(s.has("saldoInicialCentavos"))
        assertFalse(s.has("saldoInicialData"))
    }
}
