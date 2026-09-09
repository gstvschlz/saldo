package com.scholze.saldo.widget

import android.content.Context
import android.util.Log
import androidx.glance.appwidget.updateAll
import com.scholze.saldo.data.SaldoRepository
import com.scholze.saldo.data.SettingsStore
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch

/**
 * Mantém os widgets em dia enquanto o processo vive: toda escrita acontece neste processo, então
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
                // `scope` sobrevive à Activity (vive com o processo, ver AppContainer): um erro do
                // Room/DataStore aqui não pode escapar do collect e derrubar o app — só degrada o widget.
                .catch { Log.e(TAG, "widget: fluxo de atualização falhou", it) }
                .collect {
                    try {
                        // Os OITO tipos: um `updateAll` por provider. Sem widget daquele tipo
                        // na tela a chamada é um no-op barato, então não custa nada varrer todos.
                        SaldoWidget().updateAll(context)
                        ACaminhoWidget().updateAll(context)
                        ParaOndeFoiWidget().updateAll(context)
                        LancarWidget().updateAll(context)
                        BoardWidget().updateAll(context)
                        RitmoWidget().updateAll(context)
                        PoupancaWidget().updateAll(context)
                        TetoWidget().updateAll(context)
                    } catch (e: CancellationException) {
                        throw e
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
