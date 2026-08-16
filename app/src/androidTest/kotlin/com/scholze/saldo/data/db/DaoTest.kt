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

    // ---- recorrência × tags: a relação que `RecorrenciaComTags` monta pela `recorrencia_tags` ----

    @Test
    fun recorrenciaComTagsRoundtrip() = runBlocking {
        val tagId = db.tagDao().insert(TagEntity(nome = "moradia", cor = 0xFF2A7A86L))
        val recId = db.recorrenciaDao().insert(
            RecorrenciaEntity(descricao = "aluguel", valorCentavos = -2_400_00, natureza = "DIARIO", diaDoMes = 3, inicioAnoMes = 24306),
        )
        db.recorrenciaDao().setTags(recId, listOf(tagId))
        val lida = db.recorrenciaDao().observeAll().first().single()
        assertEquals("moradia", lida.tags.single().nome)
        assertEquals(listOf("moradia"), lida.toDomain().tags.map { it.nome })
    }

    @Test
    fun setTagsDaRecorrenciaSubstituiAsAnteriores() = runBlocking {
        val a = db.tagDao().insert(TagEntity(nome = "a", cor = 1))
        val b = db.tagDao().insert(TagEntity(nome = "b", cor = 2))
        val recId = db.recorrenciaDao().insert(
            RecorrenciaEntity(descricao = "r", valorCentavos = -1, natureza = "DIARIO", diaDoMes = 1, inicioAnoMes = 24306),
        )
        db.recorrenciaDao().setTags(recId, listOf(a))
        db.recorrenciaDao().setTags(recId, listOf(b))
        assertEquals(listOf("b"), db.recorrenciaDao().observeAll().first().single().tags.map { it.nome })
    }

    @Test
    fun deleteTagCascataRemoveCrossDaRecorrencia() = runBlocking {
        val tagId = db.tagDao().insert(TagEntity(nome = "x", cor = 0xFF000000))
        val recId = db.recorrenciaDao().insert(
            RecorrenciaEntity(descricao = "r", valorCentavos = -1, natureza = "DIARIO", diaDoMes = 1, inicioAnoMes = 24306),
        )
        db.recorrenciaDao().setTags(recId, listOf(tagId))
        db.tagDao().deleteById(tagId)
        val lida = db.recorrenciaDao().observeAll().first().single()
        assertEquals(0, lida.tags.size)
        assertEquals("r", lida.rec.descricao)   // a recorrência em si fica
    }

    // ---- tags: renomear / excluir ----

    @Test
    fun renameMudaSoONomeDaTag() = runBlocking {
        val id = db.tagDao().insert(TagEntity(nome = "comida", cor = 0xFFA6486BL))
        db.tagDao().rename(id, "mercado")
        val lida = db.tagDao().observeAll().first().single()
        assertEquals("mercado", lida.nome)
        assertEquals(id, lida.id)
        assertEquals(0xFFA6486BL, lida.cor)
    }

    @Test
    fun deleteByIdSomeApenasATag() = runBlocking {
        val fica = db.tagDao().insert(TagEntity(nome = "fica", cor = 1))
        val sai = db.tagDao().insert(TagEntity(nome = "sai", cor = 2))
        db.tagDao().deleteById(sai)
        assertEquals(listOf(fica), db.tagDao().observeAll().first().map { it.id })
    }
}
