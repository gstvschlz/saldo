package com.scholze.saldo.data

import com.scholze.saldo.domain.CapturaConfig
import com.scholze.saldo.domain.CartaoConfig
import com.scholze.saldo.domain.LembretesConfig
import com.scholze.saldo.domain.Movimentacao
import com.scholze.saldo.domain.Natureza
import com.scholze.saldo.domain.Recorrencia
import com.scholze.saldo.domain.Tag
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportersTest {

    private val tag = Tag(id = 1, nome = "comida", cor = 0xFFA6486BL)

    private val dump = Dump(
        exportadoEm = "2026-09-07T10:12:00-03:00",
        app = "0.6.0",
        settings = Settings(
            saldoInicialCentavos = 100_000_00,
            saldoInicialData = LocalDate.parse("2026-07-01"),
            cartao = CartaoConfig("nubank", 28, 5),
            comecarOculto = true,
            tema = Tema.ESCURO,
            widgetMostrarValores = true,
            lembretes = LembretesConfig(
                faturaAmanha = true, registrarGastos = true,
                horaInformativos = LocalTime.of(8, 30), horaNudge = LocalTime.of(21, 15),
            ),
            captura = CapturaConfig(ligada = true, marcados = setOf("com.nubank"), vistos = setOf("com.itau")),
        ),
        tags = listOf(tag),
        recorrencias = listOf(
            Recorrencia(
                id = 2, descricao = "salário", valorCentavos = 8_240_00, natureza = Natureza.DIARIO,
                diaDoMes = 15, inicio = YearMonth.of(2026, 1), fim = null, ativa = true, tags = listOf(tag),
            ),
        ),
        movimentacoes = listOf(
            Movimentacao(
                id = 3, descricao = "mercado; \"orgânico\"", valorCentavos = -189_90,
                data = LocalDate.parse("2026-07-14"), natureza = Natureza.DIARIO,
                recorrenciaId = 2, editadaManualmente = true, tags = listOf(tag), criadaEm = 1_757_000_000_000L,
            ),
        ),
        mesesMaterializados = setOf(YearMonth.of(2026, 8), YearMonth.of(2026, 9)),
    )

    /** O texto do dump acima, com [mexer] aplicado — cada teste de erro estraga um pedaço só. */
    private fun texto(mexer: JSONObject.() -> Unit = {}): String =
        JSONObject(Exporters.json(dump)).apply(mexer).toString()

    private fun erro(texto: String): String =
        runCatching { Importers.json(texto) }.exceptionOrNull()
            .let { it as? ArquivoInvalido ?: throw AssertionError("esperava ArquivoInvalido, veio $it") }
            .message!!

    @Test
    fun idaEVolta() {
        val lido = Importers.json(Exporters.json(dump))
        assertEquals(dump, lido)
        // e o texto reescrito é idêntico: o formato não tem canto que se perca na volta
        assertEquals(Exporters.json(dump), Exporters.json(lido))
    }

    @Test
    fun schema1ERecusadoComAMensagemCerta() {
        assertEquals(
            "este arquivo é de uma versão antiga do saldo; exporte de novo na versão atual",
            erro(texto { put("schema", 1) }),
        )
    }

    @Test
    fun textoQueNaoEJsonERecusado() {
        assertTrue(erro("isto não é json").startsWith("arquivo inválido:"))
    }

    @Test
    fun tagIdInexistente() {
        assertEquals(
            "arquivo inválido: a movimentação 3 aponta para a tag 99, que não está no arquivo",
            erro(texto { getJSONArray("movimentacoes").getJSONObject(0).put("tagIds", org.json.JSONArray(listOf(99))) }),
        )
    }

    @Test
    fun recorrenciaIdInexistente() {
        assertEquals(
            "arquivo inválido: a movimentação 3 aponta para a recorrência 99, que não está no arquivo",
            erro(texto { getJSONArray("movimentacoes").getJSONObject(0).put("recorrenciaId", 99) }),
        )
    }

    @Test
    fun idRepetido() {
        assertEquals(
            "arquivo inválido: id 3 repetido em movimentações",
            erro(
                texto {
                    val arr = getJSONArray("movimentacoes")
                    arr.put(JSONObject(arr.getJSONObject(0).toString()))
                },
            ),
        )
    }

    @Test
    fun naturezaDesconhecida() {
        assertEquals(
            "arquivo inválido: natureza desconhecida \"PIX\" na movimentação 3",
            erro(texto { getJSONArray("movimentacoes").getJSONObject(0).put("natureza", "PIX") }),
        )
    }

    @Test
    fun dataInvalida() {
        assertEquals(
            "arquivo inválido: data inválida \"2026-13-40\" na movimentação 3",
            erro(texto { getJSONArray("movimentacoes").getJSONObject(0).put("data", "2026-13-40") }),
        )
    }

    @Test
    fun campoObrigatorioAusente() {
        assertEquals(
            "arquivo inválido: falta \"valorCentavos\" numa movimentação",
            erro(texto { getJSONArray("movimentacoes").getJSONObject(0).remove("valorCentavos") }),
        )
    }

    @Test
    fun mesMaterializadoInvalido() {
        assertEquals(
            "arquivo inválido: mês inválido \"2026-13\" em mesesMaterializados",
            erro(texto { put("mesesMaterializados", org.json.JSONArray(listOf("2026-13"))) }),
        )
    }

    /** captura-2 ainda não existe: um arquivo sem as duas chaves novas é válido. */
    @Test
    fun perguntadosERecusadosAusentesSaoAceitos() {
        val lido = Importers.json(
            texto {
                getJSONObject("settings").getJSONObject("captura").remove("perguntados")
                getJSONObject("settings").getJSONObject("captura").remove("recusados")
            },
        )
        assertEquals(setOf("com.nubank"), lido.settings.captura.marcados)
    }

    /** Antes do onboarding o dump não traz saldo inicial; o leitor devolve nulo, não zero. */
    @Test
    fun semSaldoInicialVoltaNulo() {
        val d = dump.copy(settings = dump.settings.copy(saldoInicialCentavos = null, saldoInicialData = null))
        val lido = Importers.json(Exporters.json(d))
        assertNull(lido.settings.saldoInicialCentavos)
        assertNull(lido.settings.saldoInicialData)
    }
}
