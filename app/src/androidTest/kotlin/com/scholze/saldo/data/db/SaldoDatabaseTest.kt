package com.scholze.saldo.data.db

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SaldoDatabaseTest {
    @Test
    fun marcaELeMesesMaterializados() = runBlocking {
        val db = SaldoDatabase.inMemory(ApplicationProvider.getApplicationContext())
        db.mesMaterializadoDao().marcar(MesMaterializadoEntity(24318)) // jul/2026 = 2026*12 + 6
        db.mesMaterializadoDao().marcar(MesMaterializadoEntity(24318)) // idempotent
        assertEquals(listOf(24318), db.mesMaterializadoDao().todos())
        db.close()
    }
}
