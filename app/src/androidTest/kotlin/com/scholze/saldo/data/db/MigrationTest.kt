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

    @Test
    fun de2Para3CriaTabelaDeteccoesEPreservaLinhas() {
        helper.createDatabase(nome, 2).use { db ->
            db.execSQL(
                "INSERT INTO movimentacoes (descricao, valorCentavos, dataEpochDay, natureza, recorrenciaId, editadaManualmente, criadaEm) " +
                    "VALUES ('mercado', -18990, 20700, 'DIARIO', NULL, 0, 0)",
            )
        }

        helper.runMigrationsAndValidate(nome, 3, true).use { db ->
            db.query("SELECT name FROM sqlite_master WHERE type = 'table' AND name = 'deteccoes'").use { c ->
                assertEquals("a tabela deteccoes tem de existir na v3", 1, c.count)
            }
            // A tabela nasce vazia e a migração não pode tocar no que já havia.
            db.query("SELECT COUNT(*) FROM deteccoes").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals(0L, c.getLong(0))
            }
            db.query("SELECT descricao, valorCentavos FROM movimentacoes").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("mercado", c.getString(0))
                assertEquals(-18990L, c.getLong(1))
                assertEquals(1, c.count)
            }
        }
    }

    /**
     * O caminho de quem instalou a v0.1 e só atualizou agora: o Room aplica 1→2 e 2→3 em sequência
     * numa abertura só. Os dois testes acima cobrem os degraus; este cobre a escada.
     */
    @Test
    fun de1Para3NumaAberturaSoPreservaTudo() {
        helper.createDatabase(nome, 1).use { db ->
            db.execSQL(
                "INSERT INTO tags (nome, cor) VALUES ('mercado', 4279901696)",
            )
            db.execSQL(
                "INSERT INTO recorrencias (descricao, valorCentavos, natureza, diaDoMes, inicioAnoMes, fimAnoMes, ativa) " +
                    "VALUES ('aluguel', -240000, 'DIARIO', 3, 24306, NULL, 1)",
            )
            db.execSQL(
                "INSERT INTO movimentacoes (descricao, valorCentavos, dataEpochDay, natureza, recorrenciaId, editadaManualmente, criadaEm) " +
                    "VALUES ('aluguel', -240000, 20700, 'DIARIO', 1, 0, 0)",
            )
            db.execSQL("INSERT INTO movimentacao_tags (movimentacaoId, tagId) VALUES (1, 1)")
            db.execSQL("INSERT INTO meses_materializados (anoMes) VALUES (24306)")
        }

        helper.runMigrationsAndValidate(nome, 3, true).use { db ->
            // o degrau 1→2: o índice
            db.query("SELECT name FROM sqlite_master WHERE type = 'index' AND tbl_name = 'movimentacoes'").use { c ->
                val indices = generateSequence { if (c.moveToNext()) c.getString(0) else null }.toList()
                assertTrue("índices em movimentacoes: $indices", "index_movimentacoes_recorrenciaId" in indices)
            }
            // o degrau 2→3: a tabela nova, vazia
            db.query("SELECT COUNT(*) FROM deteccoes").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals(0L, c.getLong(0))
            }
            // e nada do que havia se perdeu pelo caminho
            db.query("SELECT descricao, recorrenciaId, criadaEm FROM movimentacoes").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("aluguel", c.getString(0))
                assertEquals(1L, c.getLong(1))
                assertEquals(0L, c.getLong(2))
                assertEquals(1, c.count)
            }
            db.query("SELECT nome FROM tags").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("mercado", c.getString(0))
            }
            db.query("SELECT tagId FROM movimentacao_tags WHERE movimentacaoId = 1").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals(1L, c.getLong(0))
            }
            db.query("SELECT anoMes FROM meses_materializados").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals(24306L, c.getLong(0))
            }
        }
    }
}
