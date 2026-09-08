package com.scholze.saldo.data

import com.scholze.saldo.domain.CapturaConfig
import com.scholze.saldo.domain.CartaoConfig
import com.scholze.saldo.domain.LedgerInput
import com.scholze.saldo.domain.LembretesConfig
import com.scholze.saldo.domain.Movimentacao
import com.scholze.saldo.domain.Natureza
import com.scholze.saldo.domain.Recorrencia
import com.scholze.saldo.domain.Tag
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
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

    private val rec = Recorrencia(
        id = 2, descricao = "salário", valorCentavos = 8_240_00, natureza = Natureza.DIARIO,
        diaDoMes = 15, inicio = YearMonth.of(2026, 1), fim = null, tags = listOf(tag),
    )

    private val settings = Settings(
        saldoInicialCentavos = 100_000_00,
        saldoInicialData = LocalDate.parse("2026-07-01"),
        cartao = CartaoConfig("nubank", 28, 5),
        comecarOculto = true,
        tema = Tema.ESCURO,
        widgetMostrarValores = true,
        lembretes = LembretesConfig(
            faturaAmanha = true, recorrenciaHoje = false, registrarGastos = true, fechamentoMes = false,
            horaInformativos = LocalTime.of(8, 30), horaNudge = LocalTime.of(21, 15),
        ),
        captura = CapturaConfig(ligada = true, marcados = setOf("com.nubank"), vistos = setOf("com.itau")),
    )

    private fun dump(
        movs: List<Movimentacao> = listOf(mov.copy(recorrenciaId = 2, criadaEm = 1_757_000_000_000L, editadaManualmente = true)),
        recs: List<Recorrencia> = listOf(rec),
    ) = Dump(
        exportadoEm = "2026-09-07T10:12:00-03:00",
        app = "0.6.0",
        settings = settings,
        tags = listOf(tag),
        recorrencias = recs,
        movimentacoes = movs,
        mesesMaterializados = setOf(YearMonth.of(2026, 8), YearMonth.of(2026, 9)),
    )

    @Test
    fun jsonSchema2TemTudo() {
        val json = JSONObject(Exporters.json(dump()))

        assertEquals(2, json.getInt("schema"))
        assertEquals("2026-09-07T10:12:00-03:00", json.getString("exportadoEm"))
        assertEquals("0.6.0", json.getString("app"))

        val s = json.getJSONObject("settings")
        assertEquals(100_000_00L, s.getLong("saldoInicialCentavos"))
        assertEquals("2026-07-01", s.getString("saldoInicialData"))
        assertEquals("nubank", s.getString("cartaoNome"))
        assertEquals(28, s.getInt("cartaoFechamentoDia"))
        assertEquals(5, s.getInt("cartaoVencimentoDia"))
        assertEquals(true, s.getBoolean("comecarOculto"))
        assertEquals("ESCURO", s.getString("tema"))
        assertEquals(true, s.getBoolean("widgetMostrarValores"))
        assertEquals(true, s.getJSONObject("lembretes").getBoolean("faturaAmanha"))
        assertEquals("08:30", s.getJSONObject("lembretes").getString("horaInformativos"))
        assertEquals("21:15", s.getJSONObject("lembretes").getString("horaNudge"))
        assertEquals(true, s.getJSONObject("captura").getBoolean("ligada"))
        assertEquals("com.nubank", s.getJSONObject("captura").getJSONArray("marcados").getString(0))
        // perguntados/recusados chegam com captura-2; até lá saem vazios
        assertEquals(0, s.getJSONObject("captura").getJSONArray("perguntados").length())
        assertEquals(0, s.getJSONObject("captura").getJSONArray("recusados").length())

        val t = json.getJSONArray("tags").getJSONObject(0)
        assertEquals(1L, t.getLong("id"))
        assertEquals("comida", t.getString("nome"))

        val r = json.getJSONArray("recorrencias").getJSONObject(0)
        assertEquals(2L, r.getLong("id"))
        assertEquals(15, r.getInt("diaDoMes"))
        assertEquals("2026-01", r.getString("inicio"))
        assertEquals(false, r.has("fim"))                 // nulo = chave ausente
        assertEquals(true, r.getBoolean("ativa"))
        assertEquals(1L, r.getJSONArray("tagIds").getLong(0))

        val m = json.getJSONArray("movimentacoes").getJSONObject(0)
        assertEquals(3L, m.getLong("id"))
        assertEquals("2026-07-14", m.getString("data"))
        assertEquals("DIARIO", m.getString("natureza"))
        assertEquals(2L, m.getLong("recorrenciaId"))
        assertEquals(true, m.getBoolean("editadaManualmente"))
        assertEquals(1_757_000_000_000L, m.getLong("criadaEm"))
        assertEquals(1L, m.getJSONArray("tagIds").getLong(0))

        assertEquals(
            listOf("2026-08", "2026-09"),
            (0 until json.getJSONArray("mesesMaterializados").length())
                .map { json.getJSONArray("mesesMaterializados").getString(it) },
        )
    }

    @Test
    fun jsonNaoLevaSaldoNemDataQuandoOnboardingNaoAconteceu() {
        val d = dump().copy(settings = settings.copy(saldoInicialCentavos = null, saldoInicialData = null))
        val s = JSONObject(Exporters.json(d)).getJSONObject("settings")
        // org.json remove a chave num put(null): o dump não inventa um saldo zero.
        assertFalse(s.has("saldoInicialCentavos"))
        assertFalse(s.has("saldoInicialData"))
    }

    @Test
    fun dumpDeMontaAPartirDoLedger() {
        val input = LedgerInput(
            saldoInicialCentavos = 100_000_00,
            saldoInicialData = LocalDate.parse("2026-07-01"),
            movimentacoes = listOf(mov),
            recorrencias = listOf(rec),
            mesesMaterializados = setOf(YearMonth.of(2026, 7)),
            cartao = settings.cartao,
            hoje = LocalDate.parse("2026-07-20"),
        )
        val d = Dump.de(
            input, tags = listOf(tag), settings = settings, app = "0.6.0",
            agora = OffsetDateTime.parse("2026-09-07T10:12:00-03:00"),
        )
        // OffsetDateTime.toString() omite os segundos quando são zero; sem ":00" é ISO-8601 válido do mesmo jeito.
        assertEquals("2026-09-07T10:12-03:00", d.exportadoEm)
        assertEquals(listOf(mov), d.movimentacoes)          // as LINHAS do banco, não as efetivas
        assertEquals(setOf(YearMonth.of(2026, 7)), d.mesesMaterializados)
    }

    @Test
    fun csvDoLedgerSaiDasEfetivasAteHoje() {
        // agosto nunca foi aberto: a ocorrência de agosto é virtual e ainda assim entra.
        val input = LedgerInput(
            saldoInicialCentavos = 100_000_00,
            saldoInicialData = LocalDate.parse("2026-07-01"),
            movimentacoes = listOf(
                Movimentacao(id = 1, descricao = "mercado", valorCentavos = -80_00,
                    data = LocalDate.parse("2026-07-05"), natureza = Natureza.DIARIO),
                Movimentacao(id = 2, descricao = "futuro", valorCentavos = -10_00,
                    data = LocalDate.parse("2026-08-25"), natureza = Natureza.DIARIO),
            ),
            recorrencias = listOf(
                Recorrencia(id = 9, descricao = "aluguel", valorCentavos = -1_690_00,
                    natureza = Natureza.DIARIO, diaDoMes = 10, inicio = YearMonth.of(2026, 8)),
            ),
            mesesMaterializados = setOf(YearMonth.of(2026, 7)),
            cartao = settings.cartao,
            hoje = LocalDate.parse("2026-08-20"),
        )
        val linhas = Exporters.csvDoLedger(input).trim().lines()
        assertEquals("data;descricao;valor_centavos;natureza;tags;recorrente", linhas[0])
        assertEquals(listOf("2026-07-05;mercado;-8000;DIARIO;;false", "2026-08-10;aluguel;-169000;DIARIO;;true"), linhas.drop(1))
    }
}
