package com.scholze.saldo

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import com.scholze.saldo.data.RoomSaldoRepository
import com.scholze.saldo.data.SaldoRepository
import com.scholze.saldo.data.SettingsStore
import com.scholze.saldo.data.db.SaldoDatabase

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

/** Manual DI: one graph, built once, handed down from [MainActivity]. */
class AppContainer(context: Context) {
    val database: SaldoDatabase = SaldoDatabase.build(context)
    val settings: SettingsStore = SettingsStore(context.settingsDataStore)
    val repository: SaldoRepository = RoomSaldoRepository(database, settings)
}

class SaldoApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
