package com.scholze.saldo.ui.totais

import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

// Locale e formatador de mês abreviado (pt-BR) compartilhados por todo o pacote `ui.totais` —
// tela, segmentos e (via `internal`, visível no módulo inteiro) os gráficos em `ui.totais.charts`.
internal val ptBr: Locale = Locale.forLanguageTag("pt-BR")
internal val mesCurto: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM", ptBr)

/**
 * "ago/26" — o mês abreviado em pt-BR sai com ponto ("ago."), que colidiria com a barra
 * do ano. Mesmo `removeSuffix(".")` que o resto do app usa, só que antes de concatenar.
 */
internal fun YearMonth.rotuloCurto(): String =
    format(mesCurto).removeSuffix(".") + "/" + (year % 100).toString().padStart(2, '0')
