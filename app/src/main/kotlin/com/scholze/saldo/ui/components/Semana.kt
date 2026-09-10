package com.scholze.saldo.ui.components

import java.time.DayOfWeek

/**
 * Os dias da semana, de segunda a domingo, num lugar só.
 *
 * O cabeçalho do board escrevia `s t q q s s d` — quatro letras repetidas em sete colunas, que
 * não distinguem terça de quinta nem sábado de segunda —, e o `WeekdayBars` de totais
 * reimplementava a mesma lista por conta própria. Três letras cabem nos ~48 dp da coluna a 11 sp
 * e dizem qual dia é; a escolha do usuário foi `seg ter qua qui sex sáb dom`, minúsculas como o
 * resto do app.
 */
object Semana {

    /** Segunda primeiro — é a ordem em que a grade do board desenha. */
    val CURTOS: List<String> = listOf("seg", "ter", "qua", "qui", "sex", "sáb", "dom")

    /** O rótulo de três letras de [d]. */
    fun curto(d: DayOfWeek): String = CURTOS[d.value - 1]
}
