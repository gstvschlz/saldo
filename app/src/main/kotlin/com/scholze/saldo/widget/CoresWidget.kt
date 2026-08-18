package com.scholze.saldo.widget

import androidx.compose.ui.graphics.Color
import androidx.glance.color.ColorProvider
import com.scholze.saldo.ui.theme.DarkSaldoColors
import com.scholze.saldo.ui.theme.LightSaldoColors

/**
 * Cores dia/noite tiradas dos tokens do app — o launcher decide o modo, não o tema escolhido
 * em "mais".
 *
 * Compartilhado pelos quatro widgets. Em especial [sobreTint]: como o Glance não enxerga o
 * `MaterialTheme.colorScheme`, esta é a única casa do papel "tinta que se lê sobre o tint" no
 * mundo do widget. Se algum widget novo pintar texto sobre `tint`, é daqui que a cor sai.
 */
internal object CoresWidget {
    val fundo = ColorProvider(day = LightSaldoColors.surface, night = DarkSaldoColors.surface)
    val label = ColorProvider(day = LightSaldoColors.label, night = DarkSaldoColors.label)
    val secundario = ColorProvider(day = LightSaldoColors.secondaryLabel, night = DarkSaldoColors.secondaryLabel)
    val positivo = ColorProvider(day = LightSaldoColors.positive, night = DarkSaldoColors.positive)
    val negativo = ColorProvider(day = LightSaldoColors.categoryVariable, night = DarkSaldoColors.categoryVariable)
    val tint = ColorProvider(day = LightSaldoColors.tint, night = DarkSaldoColors.tint)

    /** O card tonal do app, para o cabeçalho dos widgets maiores. */
    val container = ColorProvider(day = LightSaldoColors.primaryContainer, night = DarkSaldoColors.primaryContainer)
    val sobreContainer = ColorProvider(day = LightSaldoColors.onPrimaryContainer, night = DarkSaldoColors.onPrimaryContainer)

    /** A trilha cinza por trás de uma barra proporcional, e o "sem tag" dela. */
    val trilha = ColorProvider(day = LightSaldoColors.insightSemTag, night = DarkSaldoColors.insightSemTag)
    val outras = ColorProvider(day = LightSaldoColors.insightOutras, night = DarkSaldoColors.insightOutras)

    /**
     * A tinta que se lê SOBRE o [tint]. NÃO é branco fixo: com a semente verde do M3 o tint é
     * escuro no claro mas CLARO no escuro (#99D5AC), e branco em cima dele dá 1,68:1 — contra o
     * mínimo de 4,5:1. É o mesmo papel que o app chama de `onPrimary`.
     */
    val sobreTint = ColorProvider(day = Color.White, night = Color(0xFF003919))
}
