package com.scholze.saldo

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.datastore.preferences.preferencesDataStore
import com.scholze.saldo.data.RoomSaldoRepository
import com.scholze.saldo.data.SaldoRepository
import com.scholze.saldo.data.SettingsStore
import com.scholze.saldo.data.db.SaldoDatabase
import com.scholze.saldo.widget.WidgetRefresher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

/** Manual DI: one graph, built once, handed down from [MainActivity]. */
class AppContainer(context: Context) {
    /**
     * Trabalho de fundo com a vida do processo (refresh do widget); nunca cancelado de propósito.
     * O [CoroutineExceptionHandler] é a segunda rede de segurança: mesmo que algo lançado aqui
     * escape de um `.catch`/try-catch interno, ele só loga — nada iniciado neste scope pode
     * derrubar o app.
     */
    val scope: CoroutineScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, e ->
            Log.e("saldo", "trabalho de fundo falhou", e)
        },
    )
    val database: SaldoDatabase = SaldoDatabase.build(context)
    val settings: SettingsStore = SettingsStore(context.settingsDataStore)
    val repository: SaldoRepository = RoomSaldoRepository(database, settings)
    val widgetRefresher: WidgetRefresher = WidgetRefresher(context.applicationContext, repository, settings, scope)
}

class SaldoApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
