package com.scholze.saldo.data

import com.scholze.saldo.domain.Movimentacao
import com.scholze.saldo.domain.Recorrencia
import com.scholze.saldo.domain.Tag
import org.json.JSONArray
import org.json.JSONObject

/**
 * Os dois formatos de saída do "exportar dados".
 *
 * CSV é o formato de planilha: uma linha por movimentação, ';' como separador (é o que o
 * Excel em pt-BR espera). JSON é o dump completo e versionado — settings, tags,
 * recorrências e movimentações — pensado para ser lido por outra ferramenta, ou por uma
 * futura importação.
 */
object Exporters {

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

    fun json(
        movimentacoes: List<Movimentacao>,
        recorrencias: List<Recorrencia>,
        tags: List<Tag>,
        settings: Settings,
    ): String = JSONObject().apply {
        put("schema", 1)
        put(
            "settings",
            JSONObject().apply {
                // put(chave, null) no org.json REMOVE a chave — antes do onboarding o dump
                // simplesmente não traz saldo inicial, em vez de inventar um zero.
                put("saldoInicialCentavos", settings.saldoInicialCentavos)
                put("saldoInicialData", settings.saldoInicialData?.toString())
                put("cartaoNome", settings.cartao.nome)
                put("cartaoFechamentoDia", settings.cartao.fechamentoDia)
                put("cartaoVencimentoDia", settings.cartao.vencimentoDia)
            },
        )
        put(
            "tags",
            JSONArray().apply {
                tags.forEach { put(JSONObject().apply { put("nome", it.nome); put("cor", it.cor) }) }
            },
        )
        put(
            "recorrencias",
            JSONArray().apply {
                recorrencias.forEach { r ->
                    put(
                        JSONObject().apply {
                            put("descricao", r.descricao)
                            put("valorCentavos", r.valorCentavos)
                            put("natureza", r.natureza.name)
                            put("diaDoMes", r.diaDoMes)
                            put("inicio", r.inicio.toString())
                            put("fim", r.fim?.toString())
                            put("ativa", r.ativa)
                            put("tags", JSONArray(r.tags.map { it.nome }))
                        },
                    )
                }
            },
        )
        put(
            "movimentacoes",
            JSONArray().apply {
                movimentacoes.sortedBy { it.data }.forEach { m ->
                    put(
                        JSONObject().apply {
                            put("data", m.data.toString())
                            put("descricao", m.descricao)
                            put("valorCentavos", m.valorCentavos)
                            put("natureza", m.natureza.name)
                            put("recorrente", m.recorrenciaId != null)
                            put("tags", JSONArray(m.tags.map { it.nome }))
                        },
                    )
                }
            },
        )
    }.toString(2)
}
