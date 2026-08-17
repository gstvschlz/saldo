package com.scholze.saldo.widget

import java.time.LocalDate

/** O que o widget mostra — calculado em `SaldoWidget.provideGlance`, desenhado por `SaldoWidgetContent`. */
sealed interface WidgetEstado {
    /** Antes do onboarding não há saldo inicial: o widget só convida a abrir o app. */
    data object SemOnboarding : WidgetEstado

    data class Pronto(
        val projetadoEm: LocalDate,
        val saldoProjetadoCentavos: Long,
        val deltaNoMesCentavos: Long,
        /** `false` = mascarado (`R$ •••••`, sem delta). É o padrão — ver "mostrar valores no widget". */
        val mostrarValores: Boolean,
    ) : WidgetEstado

    /** Qualquer exceção ao carregar: mostra o convite a abrir o app em vez de um número velho. */
    data object Falha : WidgetEstado
}
