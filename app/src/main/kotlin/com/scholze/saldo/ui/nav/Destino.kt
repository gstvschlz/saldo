package com.scholze.saldo.ui.nav

import android.content.Intent
import com.scholze.saldo.data.db.toAnoMes
import com.scholze.saldo.data.db.toYearMonth
import java.time.YearMonth

/**
 * Para onde o app abre quando chega por fora — toque no widget ou num lembrete. Viaja como
 * extras de Intent (e como ActionParameters no Glance, que os converte nos mesmos extras): um
 * tipo e até dois inteiros, nada que precise de Parcelable. [de] é o núcleo puro do parse, para
 * ser testado na JVM.
 */
sealed interface Destino {
    /** Aba saldos em [mes]; com [dia] (1..31), o ledger rola até esse dia. */
    data class Saldos(val mes: YearMonth, val dia: Int? = null) : Destino

    /**
     * Aba saldos com a sheet de nova movimentação já aberta.
     *
     * [saida] `null` deixa a sheet no padrão dela; `true`/`false` já a abre como saída ou
     * entrada — é o que os dois botões do widget "lançar" usam para poupar um toque.
     */
    data class NovaMovimentacao(
        val saida: Boolean? = null,
        /** Valor a pré-preencher, em centavos — a sugestão de notificação já sabe quanto foi. */
        val centavos: Long? = null,
        /** Descrição a pré-preencher; hoje é o nome do app que emitiu a notificação. */
        val descricao: String? = null,
    ) : Destino

    /** Aba totais em [mes]. */
    data class Totais(val mes: YearMonth) : Destino

    /** Os extras que representam este destino — [aplicarEm] e o widget usam a mesma lista. */
    fun paraPares(): List<Pair<String, Any>> = when (this) {
        is Saldos -> listOfNotNull(
            EXTRA_DESTINO to TIPO_SALDOS,
            EXTRA_ANO_MES to mes.toAnoMes(),
            dia?.let { EXTRA_DIA to it },
        )
        is NovaMovimentacao -> listOfNotNull(
            EXTRA_DESTINO to TIPO_NOVA,
            saida?.let { EXTRA_SAIDA to if (it) 1 else 0 },
            // Long e String viajam como extra de texto: `aplicarEm` só trata Int como int.
            centavos?.let { EXTRA_CENTAVOS to it.toString() },
            descricao?.let { EXTRA_DESCRICAO to it },
        )
        is Totais -> listOf(EXTRA_DESTINO to TIPO_TOTAIS, EXTRA_ANO_MES to mes.toAnoMes())
    }

    fun aplicarEm(intent: Intent): Intent = intent.apply {
        paraPares().forEach { (chave, valor) ->
            when (valor) {
                is Int -> putExtra(chave, valor)
                else -> putExtra(chave, valor.toString())
            }
        }
    }

    companion object {
        const val EXTRA_DESTINO = "destino"
        const val EXTRA_ANO_MES = "anoMes"
        const val EXTRA_DIA = "dia"
        const val EXTRA_SAIDA = "saida"
        const val EXTRA_CENTAVOS = "centavos"
        const val EXTRA_DESCRICAO = "descricao"
        const val TIPO_SALDOS = "saldos"
        const val TIPO_NOVA = "nova"
        const val TIPO_TOTAIS = "totais"

        /**
         * `null` para tipo desconhecido, mês ausente ou negativo; um dia fora de 1..31 é
         * ignorado, e um [saida] ausente vira `null` (a sheet decide sozinha).
         */
        fun de(
            tipo: String?,
            anoMes: Int?,
            dia: Int?,
            saida: Int? = null,
            centavos: String? = null,
            descricao: String? = null,
        ): Destino? {
            val mes = anoMes?.takeIf { it >= 0 }?.toYearMonth()
            return when (tipo) {
                TIPO_SALDOS -> mes?.let { Saldos(it, dia?.takeIf { d -> d in 1..31 }) }
                // Um extra corrompido vira `null` (a sheet decide sozinha), nunca crash:
                // `toLongOrNull` engole o lixo e um valor <= 0 não tem o que pré-preencher.
                TIPO_NOVA -> NovaMovimentacao(
                    saida = saida?.let { it != 0 },
                    centavos = centavos?.toLongOrNull()?.takeIf { it > 0 },
                    descricao = descricao?.takeIf { it.isNotBlank() },
                )
                TIPO_TOTAIS -> mes?.let { Totais(it) }
                else -> null
            }
        }

        fun deIntent(intent: Intent?): Destino? {
            if (intent == null || !intent.hasExtra(EXTRA_DESTINO)) return null
            return de(
                tipo = intent.getStringExtra(EXTRA_DESTINO),
                anoMes = if (intent.hasExtra(EXTRA_ANO_MES)) intent.getIntExtra(EXTRA_ANO_MES, -1) else null,
                dia = if (intent.hasExtra(EXTRA_DIA)) intent.getIntExtra(EXTRA_DIA, 0) else null,
                saida = if (intent.hasExtra(EXTRA_SAIDA)) intent.getIntExtra(EXTRA_SAIDA, 0) else null,
                centavos = intent.getStringExtra(EXTRA_CENTAVOS),
                descricao = intent.getStringExtra(EXTRA_DESCRICAO),
            )
        }
    }
}
