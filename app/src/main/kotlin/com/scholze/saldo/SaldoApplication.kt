package com.scholze.saldo

import android.app.Application
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.datastore.preferences.preferencesDataStore
import androidx.work.ExistingWorkPolicy
import com.scholze.saldo.backup.BackupScheduler
import com.scholze.saldo.backup.PastaBackup
import com.scholze.saldo.backup.PastaSaf
import com.scholze.saldo.data.RoomSaldoRepository
import com.scholze.saldo.data.SaldoRepository
import com.scholze.saldo.data.SettingsStore
import com.scholze.saldo.data.db.SaldoDatabase
import com.scholze.saldo.captura.NotificacaoSugestao
import com.scholze.saldo.lembretes.LembretesScheduler
import com.scholze.saldo.lembretes.Notificacoes
import com.scholze.saldo.widget.WidgetRefresher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

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
    val lembretesScheduler: LembretesScheduler = LembretesScheduler(context.applicationContext)
    val backupScheduler: BackupScheduler = BackupScheduler(context.applicationContext)

    /**
     * Como o worker abre a pasta do backup.
     *
     * `var` por um motivo só: o `BackupWorkerTest` a troca por uma pasta de arquivos temporários.
     * Sem essa costura o worker só seria testável num aparelho com uma árvore SAF escolhida à mão —
     * e um backup sem teste é uma promessa, não um recurso.
     */
    var pastaBackup: (Uri) -> PastaBackup = { PastaSaf(context.applicationContext, it) }
}

class SaldoApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        Notificacoes.criarCanal(this)
        NotificacaoSugestao.criarCanal(this)
        // Rede de segurança (KEEP): normalmente os trabalhos já existem — o WorkManager sobrevive
        // ao reboot — mas depois de "limpar dados" ou de um restore eles precisam voltar.
        container.scope.launch {
            val s = container.settings.settings.first()
            container.lembretesScheduler.agendar(s.lembretes, ExistingWorkPolicy.KEEP)
            container.backupScheduler.agendar(s.backup, ExistingWorkPolicy.KEEP)
        }
    }
}
