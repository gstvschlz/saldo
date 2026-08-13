package com.scholze.saldo

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.rules.ExternalResource

/**
 * Estado de instalação nova, para os testes que passam pelo onboarding.
 *
 * Apagar os arquivos do app NÃO basta e é a armadilha óbvia aqui: toda a suíte
 * instrumentada roda num processo só, o [AppContainer] já foi construído em
 * `SaldoApplication.onCreate` e o DataStore é um singleton por arquivo — depois que
 * alguém leu, o valor fica em memória e apagar o `.preferences_pb` por baixo não
 * invalida esse cache (a segunda `@Test` a rodar veria o saldo inicial da primeira e
 * nunca chegaria ao onboarding). O mesmo vale para o banco: o Room mantém a conexão
 * aberta e `deleteDatabase` deixaria o inode órfão em uso.
 *
 * Por isso o reset passa pelas MESMAS instâncias — `settings.limpar()` e
 * `clearAllTables()` — em vez de mexer no disco. Assim a ordem em que os testes rodam
 * deixa de importar.
 *
 * Use com `@get:Rule(order = 0)`, antes da regra que sobe a activity.
 */
class EstadoLimpo : ExternalResource() {
    override fun before() {
        val app = ApplicationProvider.getApplicationContext<SaldoApplication>()
        runBlocking { app.container.settings.limpar() }
        app.container.database.clearAllTables()
    }
}
