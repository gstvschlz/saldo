package com.scholze.saldo.data

import com.scholze.saldo.domain.LedgerInput
import com.scholze.saldo.domain.Movimentacao
import com.scholze.saldo.domain.Recorrencia
import com.scholze.saldo.domain.Tag
import java.time.OffsetDateTime
import java.time.YearMonth

/**
 * O conteúdo do arquivo de exportação, já em tipos do domínio.
 *
 * É o único modelo entre o disco e o app: [Exporters.json] escreve um destes e
 * `Importers.json` devolve um destes. Nenhuma tela, ViewModel ou worker monta ou lê JSON por
 * conta própria — o formato mora nesses dois lugares e em mais nenhum.
 *
 * [movimentacoes] são as LINHAS do banco (o dump fiel), não as efetivas: restaurar precisa
 * reconstruir o banco como ele era, e as ocorrências virtuais são derivadas de
 * [recorrencias] + [mesesMaterializados]. Quem quer a lista renderizada tem o CSV.
 */
data class Dump(
    /** ISO-8601 com offset, como o aparelho o tinha na hora de exportar. */
    val exportadoEm: String,
    /** `BuildConfig.VERSION_NAME` de quem exportou — para um humano entender um arquivo antigo. */
    val app: String,
    val settings: Settings,
    val tags: List<Tag>,
    val recorrencias: List<Recorrencia>,
    val movimentacoes: List<Movimentacao>,
    val mesesMaterializados: Set<YearMonth>,
) {
    companion object {
        /**
         * A versão do FORMATO DO ARQUIVO. Não tem relação com a versão do banco (3): são números
         * de coisas diferentes, e confundi-los é o erro que este comentário existe para evitar.
         * O schema 1 não tem ids nem o vínculo de recorrência, e por isso não é restaurável.
         */
        const val SCHEMA = 2

        fun de(
            input: LedgerInput,
            tags: List<Tag>,
            settings: Settings,
            app: String,
            agora: OffsetDateTime = OffsetDateTime.now(),
        ): Dump = Dump(
            exportadoEm = agora.toString(),
            app = app,
            settings = settings,
            tags = tags,
            recorrencias = input.recorrencias,
            movimentacoes = input.movimentacoes,
            mesesMaterializados = input.mesesMaterializados,
        )
    }
}
