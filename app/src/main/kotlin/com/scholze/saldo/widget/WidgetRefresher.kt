package com.scholze.saldo.widget

import android.content.Context
import android.util.Log
import androidx.glance.appwidget.updateAll
import com.scholze.saldo.data.SaldoRepository
import com.scholze.saldo.data.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch

/**
 * Mantém o widget em dia enquanto o processo vive: toda escrita acontece neste processo, então
 * observar o ledger (que já embute a virada do dia, ver `diaAtual`) e as settings (a máscara)
 * cobre tudo que muda o número. `updateAll` sem widget na tela é um no-op barato.
 */
@OptIn(FlowPreview::class)
class WidgetRefresher(
    private val context: Context,
    repository: SaldoRepository,
    settings: SettingsStore,
    scope: CoroutineScope,
) {
    init {
        scope.launch {
            merge(repository.ledger.map { }, settings.settings.map { })
                .debounce(300)
                .collect {
                    try {
                        SaldoWidget().updateAll(context)
                    } catch (e: Exception) {
                        Log.e(TAG, "widget: updateAll falhou", e)
                    }
                }
        }
    }

    private companion object {
        const val TAG = "saldo"
    }
}
