package com.scholze.saldo.ui

import java.time.YearMonth

/** `YearMonth` ↔ `Long` para o `SavedStateHandle`: ano × 12 + (mês − 1), como o banco já faz. */
fun YearMonth.toLongChave(): Long = year * 12L + (monthValue - 1)
fun Long.toYearMonth(): YearMonth = YearMonth.of((this / 12).toInt(), (this % 12).toInt() + 1)
