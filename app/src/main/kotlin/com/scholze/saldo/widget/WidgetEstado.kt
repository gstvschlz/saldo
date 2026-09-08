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
         * A meta bateu **e** o número está à mostra — a única condição em que a cor do "guardou"
         * muda.
         *
         * O `mostrarValores` faz parte da regra de propósito: se a cor mudasse mesmo mascarado, o
         * widget contaria pela cor exatamente o que escondeu no `••%`. Valor escondido é meta
         * escondida (decisão 8).
         */
        val metaBatidaVisivel: Boolean
            get() {
                val taxa = taxaGuardada ?: return false
                return mostrarValores && metaGuardarPercent > 0 && taxa >= metaGuardarPercent
            }
    }

    /** Qualquer exceção ao carregar: mostra o convite a abrir o app em vez de um número velho. */
    data object Falha : WidgetEstado
}
