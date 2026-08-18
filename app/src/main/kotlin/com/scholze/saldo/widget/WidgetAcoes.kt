package com.scholze.saldo.widget

import androidx.glance.action.Action
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import com.scholze.saldo.MainActivity
import com.scholze.saldo.ui.nav.Destino
import java.time.YearMonth

/** Abrir o app num [Destino] — a única forma de qualquer widget navegar. */
internal fun abrirWidget(destino: Destino): Action = actionStartActivity<MainActivity>(destino.paraParametros())

/** O mês corrente, para os estados que não têm um número (e portanto não têm mês próprio). */
internal fun mesDeHoje(): YearMonth = YearMonth.now()

/** Os mesmos extras de [Destino.aplicarEm], no formato do Glance — que os converte em extras do Intent. */
internal fun Destino.paraParametros(): ActionParameters {
    val pares: List<ActionParameters.Pair<out Any>> = paraPares().map { (chave, valor) ->
        when (valor) {
            is Int -> ActionParameters.Key<Int>(chave) to valor
            else -> ActionParameters.Key<String>(chave) to valor.toString()
        }
    }
    return actionParametersOf(*pares.toTypedArray())
}
