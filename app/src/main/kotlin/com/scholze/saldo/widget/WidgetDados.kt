package com.scholze.saldo.widget

import android.content.Context
import android.util.Log
import com.scholze.saldo.SaldoApplication
import com.scholze.saldo.domain.LedgerInput
import java.time.YearMonth
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.first

/**
 * A carga crua que todos os widgets compartilham: UM snapshot do repositório e das settings,
 * do qual cada tipo tira a fatia que desenha.
 *
 * Deliberadamente NÃO roda motor nenhum: `aCaminho` e `paraOndeFoi` custam uma passada cada, e
 * o widget de saldo não precisa de nenhuma das duas. Quem chama decide o que calcular, e assim
 * uma atualização do widget de saldo continua tão barata quanto era antes destes tipos novos.
 */
internal sealed interface Carga {
    /** Antes do onboarding não há saldo inicial: todo widget só convida a abrir o app. */
    data object SemOnboarding : Carga

    /** Qualquer exceção ao carregar: convite a abrir o app em vez de um número velho. */
    data object Falha : Carga

    data class Pronto(
        val input: LedgerInput,
        val mes: YearMonth,
        /** `false` = mascarado. É o padrão — ver "mostrar valores no widget". */
        val mostrarValores: Boolean,
    ) : Carga
}

/**
 * Lê settings + ledger uma vez e devolve a [Carga]. Um snapshot (`first()`), não uma coleta:
 * quem reage a mudanças é o [WidgetRefresher].
 */
internal suspend fun carregarWidget(context: Context): Carga = try {
    val container = (context.applicationContext as SaldoApplication).container
    val settings = container.settings.settings.first()
    if (settings.saldoInicialCentavos == null) {
        Carga.SemOnboarding
    } else {
        val input = container.repository.ledger.first()
        Carga.Pronto(
            input = input,
            mes = YearMonth.from(input.hoje),
            mostrarValores = settings.widgetMostrarValores,
        )
    }
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    Log.e("saldo", "widget: falha ao carregar", e)
    Carga.Falha
}
