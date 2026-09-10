package com.scholze.saldo.widget

import android.content.Context
import android.util.Log
import com.scholze.saldo.data.SaldoRepository
import com.scholze.saldo.data.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch

/**
 * Mantém os widgets em dia ENQUANTO HOUVER widget numa tela inicial: toda escrita acontece neste
 * processo, então observar o ledger (que já embute a virada do dia, ver `diaAtual`) e as settings
 * (a máscara) cobre tudo que muda o número.
 *
 * O `haWidget` na frente não é economia de `updateAll` — esse é um no-op barato. É o coletor do
 * Room que não pode existir à toa: ele mantém vivo o ticker de um minuto do `diaAtual` pelo tempo
 * de vida do processo, num app que pode não ter widget nenhum.
 */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class WidgetRefresher(
    private val context: Context,
    repository: SaldoRepository,
    settings: SettingsStore,
    scope: CoroutineScope,
) {
    init {
        scope.launch {
            // O coletor do ledger só existe enquanto HÁ widget na tela. Antes ele vivia pelo
            // tempo do processo, e com isso mantinha vivo o ticker de um minuto do `diaAtual`
            // (ver `LedgerInput.hoje`) num app que podia não ter widget nenhum.
            //
            // `flatMapLatest` e não um `if` dentro do collect: desligar o interruptor tem de
            // CANCELAR a assinatura do Room, não só ignorar o que ela emite.
            haWidget
                .flatMapLatest { ha ->
                    if (!ha) emptyFlow()
                    else merge(repository.ledger.map { }, settings.settings.map { }).debounce(300)
                }
                // `scope` sobrevive à Activity (vive com o processo, ver AppContainer): um erro do
                // Room/DataStore aqui não pode escapar do collect e derrubar o app — só degrada o widget.
                .catch { Log.e(TAG, "widget: fluxo de atualização falhou", it) }
                .collect { atualizarTodos(context) }
        }
    }

    private companion object {
        const val TAG = "saldo"
    }
}
