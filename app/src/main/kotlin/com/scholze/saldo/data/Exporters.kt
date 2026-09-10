package com.scholze.saldo.data

import com.scholze.saldo.domain.LedgerInput
import com.scholze.saldo.domain.Movimentacao
import com.scholze.saldo.domain.ProjectionEngine
import java.time.YearMonth
import org.json.JSONArray
import org.json.JSONObject

/**
 * Os dois formatos de saída do "exportar dados".
 *
 * CSV é o formato de planilha: uma linha por movimentação EFETIVA até hoje — o que o app mostra,
 * com as ocorrências de um mês nunca aberto incluídas e sem nada que ainda não aconteceu. ';' como
 * separador, que é o que o Excel em pt-BR espera.
 *
 * JSON é o dump fiel e versionado ([Dump]), pensado para voltar: ele carrega ids, o vínculo de
 * recorrência, `criadaEm`, `editadaManualmente` e os meses materializados, que é exatamente o que
 * `Importers` precisa para reconstruir o banco.
 */
object Exporters {

    /** "O que o app mostra": as efetivas do saldo inicial até hoje. Ver a decisão 1 do spec. */
    fun csvDoLedger(input: LedgerInput): String =
        csv(ProjectionEngine.movimentacoesAte(input, YearMonth.from(input.hoje)).filter { it.data <= input.hoje })

    fun csv(movimentacoes: List<Movimentacao>): String = buildString {
        appendLine("data;descricao;valor_centavos;natureza;tags;recorrente")
        movimentacoes.sortedBy { it.data }.forEach { m ->
            appendLine(
                listOf(
                    m.data.toString(),
                    campo(m.descricao),
                    m.valorCentavos.toString(),
                    m.natureza.name,
                    campo(m.tags.joinToString(",") { it.nome }),
                    (m.recorrenciaId != null).toString(),
                ).joinToString(";"),
            )
        }
    }

    /** Aspas só quando precisa; as de dentro dobram. */
    private fun campo(v: String): String =
        if (v.contains(';') || v.contains('"') || v.contains('\n')) "\"" + v.replace("\"", "\"\"") + "\"" else v

    fun json(dump: Dump): String = JSONObject().apply {
        put("schema", Dump.SCHEMA)
        put("exportadoEm", dump.exportadoEm)
        put("app", dump.app)
        put("settings", settings(dump.settings))
        put(
            "tags",
            JSONArray().apply {
                dump.tags.forEach {
                    put(JSONObject().apply { put("id", it.id); put("nome", it.nome); put("cor", it.cor) })
                }
            },
        )
        put(
            "recorrencias",
            JSONArray().apply {
                dump.recorrencias.forEach { r ->
                    put(
                        JSONObject().apply {
                            put("id", r.id)
                            put("descricao", r.descricao)
                            put("valorCentavos", r.valorCentavos)
                            put("natureza", r.natureza.name)
                            put("diaDoMes", r.diaDoMes)
                            put("inicio", r.inicio.toString())
                            // put(chave, null) no org.json REMOVE a chave: nulo vira ausência, e o
                            // leitor trata ausência como nulo. É o que mantém a ida e volta estável.
                            put("fim", r.fim?.toString())
                            put("ativa", r.ativa)
                            put("tagIds", JSONArray(r.tags.map { it.id }))
                        },
                    )
                }
            },
        )
        put(
            "movimentacoes",
            JSONArray().apply {
                dump.movimentacoes.sortedBy { it.data }.forEach { m ->
                    put(
                        JSONObject().apply {
                            put("id", m.id)
                            put("descricao", m.descricao)
                            put("valorCentavos", m.valorCentavos)
                            put("data", m.data.toString())
                            put("natureza", m.natureza.name)
                            put("recorrenciaId", m.recorrenciaId)
                            put("editadaManualmente", m.editadaManualmente)
                            put("criadaEm", m.criadaEm)
                            put("tagIds", JSONArray(m.tags.map { it.id }))
                        },
                    )
                }
            },
        )
        put("mesesMaterializados", JSONArray(dump.mesesMaterializados.sorted().map { it.toString() }))
    }.toString(2)

    private fun settings(s: Settings): JSONObject = JSONObject().apply {
        put("saldoInicialCentavos", s.saldoInicialCentavos)
        put("saldoInicialData", s.saldoInicialData?.toString())
        put("cartaoNome", s.cartao.nome)
        put("cartaoFechamentoDia", s.cartao.fechamentoDia)
        put("cartaoVencimentoDia", s.cartao.vencimentoDia)
        put("comecarOculto", s.comecarOculto)
        put("tema", s.tema.name)
        put("widgetMostrarValores", s.widgetMostrarValores)
        put("metaGuardarPercent", s.metaGuardarPercent)
        // Ordenado: um Set não tem ordem, e duas exportações do mesmo estado têm de dar o mesmo
        // texto — é isso que faz a ida e volta ser comparável no teste.
        put("assinaturasDispensadas", JSONArray(s.assinaturasDispensadas.sorted()))
        put(
            "lembretes",
            JSONObject().apply {
                put("faturaAmanha", s.lembretes.faturaAmanha)
                put("recorrenciaHoje", s.lembretes.recorrenciaHoje)
                put("registrarGastos", s.lembretes.registrarGastos)
                put("fechamentoMes", s.lembretes.fechamentoMes)
                put("etiquetarHoje", s.lembretes.etiquetarHoje)
                // "HH:mm": LocalTime.toString() omite os segundos quando são zero, e a hora do
                // usuário sempre é redonda no minuto (o TimePicker não oferece segundos).
                put("horaInformativos", s.lembretes.horaInformativos.toString())
                put("horaNudge", s.lembretes.horaNudge.toString())
            },
        )
        put(
            "captura",
            JSONObject().apply {
                put("ligada", s.captura.ligada)
                put("marcados", JSONArray(s.captura.marcados.sorted()))
                put("vistos", JSONArray(s.captura.vistos.sorted()))
                // `perguntados` e `recusados` chegam com captura-2. Escrever as listas vazias agora
                // deixa o formato estável: quando os campos existirem, é só trocar o JSONArray().
                put("perguntados", JSONArray())
                put("recusados", JSONArray())
            },
        )
    }
}
