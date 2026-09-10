package com.scholze.saldo.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.util.Log
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.updateAll
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

private const val TAG = "saldo"

/**
 * Os oito tipos de widget, num lugar só.
 *
 * Existia uma lista destas dentro do [WidgetRefresher] e outra, de UM item, dentro do
 * `LembretesWorker` — que por isso acordava só o widget de saldo. Widget novo agora entra aqui e
 * em nenhum outro lugar.
 */
internal fun todosOsWidgets(): List<GlanceAppWidget> = listOf(
    SaldoWidget(),
    ACaminhoWidget(),
    ParaOndeFoiWidget(),
    LancarWidget(),
    BoardWidget(),
    RitmoWidget(),
    PoupancaWidget(),
    TetoWidget(),
)

/**
 * Redesenha todo widget que esteja numa tela inicial.
 *
 * Sem widget daquele tipo na tela, `updateAll` é um no-op barato — então não custa varrer os
 * oito. Engole as exceções de propósito: quem chama são workers e coletores de longa vida, e um
 * widget que não redesenhou não pode derrubar o processo.
 */
suspend fun atualizarTodos(context: Context) {
    try {
        todosOsWidgets().forEach { it.updateAll(context) }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.e(TAG, "widget: atualizarTodos falhou", e)
    }
}

/** Há ao menos um widget do saldo numa tela inicial agora? */
suspend fun haWidgetNaTela(context: Context): Boolean = try {
    val gerente = GlanceAppWidgetManager(context)
    todosOsWidgets().any { gerente.getGlanceIds(it.javaClass).isNotEmpty() }
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    // Um erro aqui não pode decidir "não há widget" com certeza; assumir que HÁ só custa um
    // coletor a mais, enquanto assumir que não há deixaria widgets parados na tela.
    Log.e(TAG, "widget: getGlanceIds falhou", e)
    true
}

/**
 * O interruptor do [WidgetRefresher]: `true` enquanto existir widget numa tela inicial.
 *
 * Memória, nunca disco — é uma pergunta sobre o AGORA, e o launcher é a fonte da verdade. Vive
 * aqui, e não no `AppContainer`, porque quem o liga e desliga são os receivers, que o Android
 * instancia sozinho e sem acesso ao container.
 */
internal val haWidget = MutableStateFlow(false)

/** Escopo próprio para o `goAsync` dos receivers: eles não têm `viewModelScope` nem container. */
private val escopoDoReceiver = CoroutineScope(SupervisorJob() + Dispatchers.Default)

/**
 * A base dos oito receivers.
 *
 * Faz duas coisas que o Glance não faz sozinho: liga/desliga o fluxo [haWidget] — para o
 * refresher não manter o ledger coletado (e o ticker de um minuto do `diaAtual` vivo) num app sem
 * widget nenhum na tela — e enfileira o worker da meia-noite quando o primeiro widget aparece.
 *
 * `onDisabled` só dispara quando some o ÚLTIMO widget DAQUELE tipo, então ele não pode desligar o
 * fluxo direto: os outros sete podem continuar na tela. Quem responde é [haWidgetNaTela].
 */
abstract class SaldoWidgetReceiver : GlanceAppWidgetReceiver() {

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        haWidget.value = true
        AtualizacaoDiariaWorker.agendar(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        val app = context.applicationContext
        // `goAsync` porque a resposta é suspensa: sem ele o receiver morre antes de o launcher
        // responder, e o processo pode ser morto no meio.
        val pendente = goAsync()
        escopoDoReceiver.launch {
            try {
                val ainda = haWidgetNaTela(app)
                haWidget.value = ainda
                if (!ainda) AtualizacaoDiariaWorker.cancelar(app)
            } finally {
                pendente.finish()
            }
        }
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        // Restauração de backup, troca de launcher, reinstalação: o widget reaparece sem passar
        // por `onEnabled`, e sem isto o refresher ficaria desligado com widget na tela.
        if (appWidgetIds.isNotEmpty()) haWidget.value = true
    }
}
