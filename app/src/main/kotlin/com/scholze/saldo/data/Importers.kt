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
import java.time.format.DateTimeParseException
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * O arquivo não serve. A [message] é **texto de tela**, em pt-BR e minúsculas: quem pega a
 * exceção mostra a mensagem como ela veio.
 */
class ArquivoInvalido(mensagem: String) : Exception(mensagem)

/**
 * A leitura do arquivo de exportação. **É a única função do app que conhece o formato** — o
 * exportador escreve, este lê, e mais ninguém abre um `JSONObject`.
 *
 * Tudo é validado em memória e a função devolve um [Dump] inteiro ou lança: nenhuma escrita
 * acontece antes de o arquivo estar provado. É por isso que restaurar pode ser "valida tudo →
 * transação → ajustes" sem risco de deixar o banco pela metade por causa de um arquivo torto.
 *
 * Tolerância deliberada: chave ausente vale nulo onde o campo é nulável (`fim`, `recorrenciaId`,
 * saldo inicial antes do onboarding), e `perguntados`/`recusados` — que só chegam com captura-2 —
 * podem não existir. Intolerância deliberada: natureza desconhecida, data que não lê, vínculo que
 * aponta para fora do arquivo e id repetido são erro, porque cada um deles viraria dado corrompido
 * dentro do banco.
 */
object Importers {

    fun json(texto: String): Dump {
        val raiz = try {
            JSONObject(texto)
        } catch (e: JSONException) {
            throw ArquivoInvalido("arquivo inválido: não parece um json (${e.message})")
        }
        val schema = raiz.optInt("schema", -1)
        if (schema != Dump.SCHEMA) {
            throw ArquivoInvalido("este arquivo é de uma versão antiga do saldo; exporte de novo na versão atual")
        }

        val tags = raiz.arranjo("tags").map { t ->
            Tag(
                id = t.longObrigatorio("id", "uma tag"),
                nome = t.textoObrigatorio("nome", "numa tag"),
                cor = t.longObrigatorio("cor", "numa tag"),
            )
        }
        exigirIdsUnicos(tags.map { it.id }, "tags")
        val porId = tags.associateBy { it.id }

        val recorrencias = raiz.arranjo("recorrencias").map { r ->
            val id = r.longObrigatorio("id", "uma recorrência")
            // "numa recorrência" para campo ausente (a mensagem não cita o id); "na recorrência
            // $id" para valor ilegível; "a recorrência $id" como sujeito de "aponta para".
            val emCampo = "numa recorrência"
            val naRecorrencia = "na recorrência $id"
            val apontaPara = "a recorrência $id"
            Recorrencia(
                id = id,
                descricao = r.textoObrigatorio("descricao", emCampo),
                valorCentavos = r.longObrigatorio("valorCentavos", emCampo),
                natureza = natureza(r.textoObrigatorio("natureza", emCampo), naRecorrencia),
                diaDoMes = r.intObrigatorio("diaDoMes", emCampo),
                inicio = mes(r.textoObrigatorio("inicio", emCampo), naRecorrencia),
                fim = r.textoOuNulo("fim")?.let { mes(it, naRecorrencia) },
                ativa = r.optBoolean("ativa", true),
                tags = r.longs("tagIds").map { porId[it] ?: semTag(it, apontaPara) },
            )
        }
        exigirIdsUnicos(recorrencias.map { it.id }, "recorrências")
        val idsDeRecorrencia = recorrencias.map { it.id }.toSet()

        val movimentacoes = raiz.arranjo("movimentacoes").map { m ->
            val id = m.longObrigatorio("id", "uma movimentação")
            val emCampo = "numa movimentação"
            val naMovimentacao = "na movimentação $id"
            val apontaPara = "a movimentação $id"
            Movimentacao(
                id = id,
                descricao = m.textoObrigatorio("descricao", emCampo),
                valorCentavos = m.longObrigatorio("valorCentavos", emCampo),
                data = data(m.textoObrigatorio("data", emCampo), naMovimentacao),
                natureza = natureza(m.textoObrigatorio("natureza", emCampo), naMovimentacao),
                recorrenciaId = m.longOuNulo("recorrenciaId")?.also {
                    if (it !in idsDeRecorrencia) {
                        throw ArquivoInvalido("arquivo inválido: $apontaPara aponta para a recorrência $it, que não está no arquivo")
                    }
                },
                editadaManualmente = m.optBoolean("editadaManualmente", false),
                tags = m.longs("tagIds").map { porId[it] ?: semTag(it, apontaPara) },
                criadaEm = m.optLong("criadaEm", 0L),
            )
        }
        exigirIdsUnicos(movimentacoes.map { it.id }, "movimentações")

        return Dump(
            exportadoEm = raiz.textoObrigatorio("exportadoEm", "no arquivo"),
            app = raiz.textoObrigatorio("app", "no arquivo"),
            settings = settings(raiz.objetoObrigatorio("settings", "no arquivo")),
            tags = tags,
            recorrencias = recorrencias,
            movimentacoes = movimentacoes,
            mesesMaterializados = raiz.textos("mesesMaterializados")
                .map { mesMaterializado(it) }
                .toSet(),
        )
    }

    // ---- settings ----

    private fun settings(s: JSONObject): Settings {
        val l = s.objetoOuNulo("lembretes") ?: JSONObject()
        val c = s.objetoOuNulo("captura") ?: JSONObject()
        val padraoCartao = CartaoConfig()
        val padraoLembretes = LembretesConfig()
        return Settings(
            saldoInicialCentavos = s.longOuNulo("saldoInicialCentavos"),
            saldoInicialData = s.textoOuNulo("saldoInicialData")?.let { data(it, "nos ajustes") },
            cartao = CartaoConfig(
                nome = s.textoOuNulo("cartaoNome") ?: padraoCartao.nome,
                fechamentoDia = s.optInt("cartaoFechamentoDia", padraoCartao.fechamentoDia),
                vencimentoDia = s.optInt("cartaoVencimentoDia", padraoCartao.vencimentoDia),
            ),
            comecarOculto = s.optBoolean("comecarOculto", true),
            // Tolerante a um tema gravado por uma versão futura do enum, como o SettingsStore.
            tema = s.textoOuNulo("tema")?.let { v -> Tema.entries.find { it.name == v } } ?: Tema.SISTEMA,
            widgetMostrarValores = s.optBoolean("widgetMostrarValores", false),
            // Ausente = o padrão; fora de faixa = o padrão também. A MESMA regra do
            // `SettingsStore`, e não um `coerceIn`: aparar 250 para 100 inventaria uma meta que
            // ninguém escolheu, e aparar −5 para 0 desligaria a meta em nome do usuário.
            metaGuardarPercent = s.optInt("metaGuardarPercent", PADRAO_META_GUARDAR)
                .takeIf { it in 0..100 } ?: PADRAO_META_GUARDAR,
            assinaturasDispensadas = s.textos("assinaturasDispensadas").toSet(),
            lembretes = LembretesConfig(
                faturaAmanha = l.optBoolean("faturaAmanha", false),
                recorrenciaHoje = l.optBoolean("recorrenciaHoje", false),
                registrarGastos = l.optBoolean("registrarGastos", false),
                fechamentoMes = l.optBoolean("fechamentoMes", false),
                horaInformativos = hora(l.textoOuNulo("horaInformativos"), padraoLembretes.horaInformativos),
                horaNudge = hora(l.textoOuNulo("horaNudge"), padraoLembretes.horaNudge),
            ),
            captura = CapturaConfig(
                ligada = c.optBoolean("ligada", false),
                marcados = c.textos("marcados").toSet(),
                vistos = c.textos("vistos").toSet(),
                // `perguntados` e `recusados` chegam com captura-2 e são ignorados até lá — de
                // propósito: um arquivo do futuro tem de continuar entrando nesta versão.
            ),
        )
    }

    // ---- primitivas ----

    // `onde` já vem com a preposição contraída ("na movimentação 3", "nos ajustes"): nenhuma
    // destas mensagens acrescenta "em" na frente.
    private fun natureza(v: String, onde: String): Natureza =
        Natureza.entries.firstOrNull { it.name == v }
            ?: throw ArquivoInvalido("arquivo inválido: natureza desconhecida \"$v\" $onde")

    private fun data(v: String, onde: String): LocalDate =
        try {
            LocalDate.parse(v)
        } catch (e: DateTimeParseException) {
            throw ArquivoInvalido("arquivo inválido: data inválida \"$v\" $onde")
        }

    private fun mes(v: String, onde: String): YearMonth =
        try {
            YearMonth.parse(v)
        } catch (e: DateTimeParseException) {
            throw ArquivoInvalido("arquivo inválido: mês inválido \"$v\" $onde")
        }

    private fun mesMaterializado(v: String): YearMonth =
        try {
            YearMonth.parse(v)
        } catch (e: DateTimeParseException) {
            throw ArquivoInvalido("arquivo inválido: mês inválido \"$v\" em mesesMaterializados")
        }

    /** Uma hora ilegível cai no padrão, como o `SettingsStore` faz com um valor fora de faixa. */
    private fun hora(v: String?, padrao: LocalTime): LocalTime =
        v?.let { runCatching { LocalTime.parse(it) }.getOrNull() } ?: padrao

    private fun semTag(id: Long, onde: String): Nothing =
        throw ArquivoInvalido("arquivo inválido: $onde aponta para a tag $id, que não está no arquivo")

    private fun exigirIdsUnicos(ids: List<Long>, onde: String) {
        val visto = mutableSetOf<Long>()
        ids.forEach { if (!visto.add(it)) throw ArquivoInvalido("arquivo inválido: id $it repetido em $onde") }
    }

    private fun JSONObject.arranjo(chave: String): List<JSONObject> {
        val a = optJSONArray(chave) ?: return emptyList()
        return (0 until a.length()).map {
            a.optJSONObject(it) ?: throw ArquivoInvalido("arquivo inválido: \"$chave\" tem um item que não é um objeto")
        }
    }

    private fun JSONObject.textos(chave: String): List<String> {
        val a: JSONArray = optJSONArray(chave) ?: return emptyList()
        return (0 until a.length()).map { a.getString(it) }
    }

    private fun JSONObject.longs(chave: String): List<Long> {
        val a: JSONArray = optJSONArray(chave) ?: return emptyList()
        return (0 until a.length()).map { a.getLong(it) }
    }

    private fun JSONObject.objetoObrigatorio(chave: String, onde: String): JSONObject =
        optJSONObject(chave) ?: faltando(chave, onde)

    private fun JSONObject.objetoOuNulo(chave: String): JSONObject? = optJSONObject(chave)

    private fun JSONObject.textoObrigatorio(chave: String, onde: String): String =
        if (has(chave) && !isNull(chave)) getString(chave) else faltando(chave, onde)

    private fun JSONObject.textoOuNulo(chave: String): String? =
        if (has(chave) && !isNull(chave)) getString(chave) else null

    private fun JSONObject.longObrigatorio(chave: String, onde: String): Long =
        if (has(chave) && !isNull(chave)) getLong(chave) else faltando(chave, onde)

    private fun JSONObject.longOuNulo(chave: String): Long? =
        if (has(chave) && !isNull(chave)) getLong(chave) else null

    private fun JSONObject.intObrigatorio(chave: String, onde: String): Int =
        if (has(chave) && !isNull(chave)) getInt(chave) else faltando(chave, onde)

    private fun faltando(chave: String, onde: String): Nothing =
        throw ArquivoInvalido("arquivo inválido: falta \"$chave\" $onde")
}
