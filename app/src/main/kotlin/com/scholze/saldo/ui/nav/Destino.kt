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

    /** Aba saldos com a sheet de nova movimentação já aberta. */
    data object NovaMovimentacao : Destino

    /** Aba totais em [mes]. */
    data class Totais(val mes: YearMonth) : Destino

    /** Os extras que representam este destino — [aplicarEm] e o widget usam a mesma lista. */
    fun paraPares(): List<Pair<String, Any>> = when (this) {
        is Saldos -> listOfNotNull(
            EXTRA_DESTINO to TIPO_SALDOS,
            EXTRA_ANO_MES to mes.toAnoMes(),
            dia?.let { EXTRA_DIA to it },
        )
        NovaMovimentacao -> listOf(EXTRA_DESTINO to TIPO_NOVA)
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
        const val TIPO_SALDOS = "saldos"
        const val TIPO_NOVA = "nova"
        const val TIPO_TOTAIS = "totais"

        /** `null` para tipo desconhecido, mês ausente ou negativo; um dia fora de 1..31 é ignorado. */
        fun de(tipo: String?, anoMes: Int?, dia: Int?): Destino? {
            val mes = anoMes?.takeIf { it >= 0 }?.toYearMonth()
            return when (tipo) {
                TIPO_SALDOS -> mes?.let { Saldos(it, dia?.takeIf { d -> d in 1..31 }) }
                TIPO_NOVA -> NovaMovimentacao
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
            )
        }
    }
}
