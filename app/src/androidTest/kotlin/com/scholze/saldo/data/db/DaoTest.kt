package com.scholze.saldo.data.db

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DaoTest {
    private lateinit var db: SaldoDatabase

    @Before fun open() { db = SaldoDatabase.inMemory(ApplicationProvider.getApplicationContext()) }
    @After fun close() { db.close() }

    @Test
    fun movimentacaoComTagsRoundtrip() = runBlocking {
        val tagId = db.tagDao().insert(TagEntity(nome = "comida", cor = 0xFFA6486BL))
        val movId = db.movimentacaoDao().insert(
            MovimentacaoEntity(descricao = "mercado", valorCentavos = -189_90, dataEpochDay = 20649, natureza = "DIARIO"),
        )
        db.movimentacaoDao().setTags(movId, listOf(tagId))
        val lidas = db.movimentacaoDao().observeAll().first()
        assertEquals(1, lidas.size)
        assertEquals("comida", lidas[0].tags.single().nome)
        assertEquals("mercado", lidas[0].toDomain().descricao)
    }

    @Test
    fun deleteInstanciasPreservaEditadas() = runBlocking {
        val recId = db.recorrenciaDao().insert(
            RecorrenciaEntity(descricao = "aluguel", valorCentavos = -2_400_00, natureza = "DIARIO", diaDoMes = 3, inicioAnoMes = 24306),
        )
        db.movimentacaoDao().insert(MovimentacaoEntity(descricao = "aluguel", valorCentavos = -2_400_00, dataEpochDay = 20700, natureza = "DIARIO", recorrenciaId = recId))
        db.movimentacaoDao().insert(MovimentacaoEntity(descricao = "aluguel ajustado", valorCentavos = -2_500_00, dataEpochDay = 20730, natureza = "DIARIO", recorrenciaId = recId, editadaManualmente = true))
        db.movimentacaoDao().deleteInstanciasNaoEditadasAPartirDe(recId, fromEpochDay = 20000)
        val restantes = db.movimentacaoDao().observeAll().first()
        assertEquals(listOf("aluguel ajustado"), restantes.map { it.mov.descricao })
    }

    @Test
    fun deleteTagCascataRemoveCross() = runBlocking {
        val tagId = db.tagDao().insert(TagEntity(nome = "x", cor = 0xFF000000))
        val movId = db.movimentacaoDao().insert(MovimentacaoEntity(descricao = "m", valorCentavos = -1, dataEpochDay = 20649, natureza = "DIARIO"))
        db.movimentacaoDao().setTags(movId, listOf(tagId))
        db.tagDao().deleteById(tagId)
        assertEquals(0, db.movimentacaoDao().observeAll().first()[0].tags.size)
    }
}
