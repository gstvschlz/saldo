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
        /** Quanto do que entrou no mês foi para economia; nulo sem entrada. */
        val taxaGuardada: Int? = null,
        /** A meta de guardar, em %; `0` = sem meta e a cor nunca muda. */
        val metaGuardarPercent: Int = 0,
    ) : WidgetEstado {
        /**
         * A meta bateu — a única condição em que a cor do "guardou" muda.
         *
         * Já dependeu também de [mostrarValores]: enquanto o widget escondia a porcentagem atrás
         * de `••%`, deixar a cor mudar contaria pela cor exatamente o que o texto escondia. A
         * porcentagem deixou de ser mascarada em 2026-09-10 — ela não é dinheiro, e o usuário
         * pediu que ela ficasse sempre visível —, então a máscara saiu dos dois lados de uma vez.
         */
        val metaBatida: Boolean
            get() {
                val taxa = taxaGuardada ?: return false
                return metaGuardarPercent > 0 && taxa >= metaGuardarPercent
            }
    }

    /** Qualquer exceção ao carregar: mostra o convite a abrir o app em vez de um número velho. */
    data object Falha : WidgetEstado
}
