package com.scholze.saldo.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Cada bump de versão do banco ganha um caso aqui: cria o banco na versão antiga a partir do
 * JSON em `app/schemas`, semeia linhas, migra e valida contra o schema novo. É o teste que a
 * spec pede ("migration test from schema v1") e o que impede uma migração de apagar dados.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {
    private val nome = "migration-test.db"

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), SaldoDatabase::class.java)

    @Test
    fun de1Para2CriaIndiceEmRecorrenciaIdEPreservaLinhas() {
        helper.createDatabase(nome, 1).use { db ->
            db.execSQL(
                "INSERT INTO recorrencias (descricao, valorCentavos, natureza, diaDoMes, inicioAnoMes, fimAnoMes, ativa) " +
                    "VALUES ('aluguel', -240000, 'DIARIO', 3, 24306, NULL, 1)",
            )
            db.execSQL(
                "INSERT INTO movimentacoes (descricao, valorCentavos, dataEpochDay, natureza, recorrenciaId, editadaManualmente, criadaEm) " +
                    "VALUES ('aluguel', -240000, 20700, 'DIARIO', 1, 0, 0)",
            )
        }

        helper.runMigrationsAndValidate(nome, 2, true).use { db ->
            db.query("SELECT name FROM sqlite_master WHERE type = 'index' AND tbl_name = 'movimentacoes'").use { c ->
                val indices = generateSequence { if (c.moveToNext()) c.getString(0) else null }.toList()
                assertTrue("índices em movimentacoes: $indices", "index_movimentacoes_recorrenciaId" in indices)
            }
            db.query("SELECT descricao, recorrenciaId FROM movimentacoes").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("aluguel", c.getString(0))
                assertEquals(1L, c.getLong(1))
                assertEquals(1, c.count)
            }
        }
    }
}
