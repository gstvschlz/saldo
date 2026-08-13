package com.scholze.saldo.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface MesMaterializadoDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun marcar(mes: MesMaterializadoEntity)

    @Query("SELECT anoMes FROM meses_materializados")
    suspend fun todos(): List<Int>

    @Query("SELECT anoMes FROM meses_materializados")
    fun observeTodos(): Flow<List<Int>>
}

@Dao
interface MovimentacaoDao {
    @Transaction
    @Query("SELECT * FROM movimentacoes ORDER BY dataEpochDay")
    fun observeAll(): Flow<List<MovimentacaoComTags>>

    @Insert suspend fun insert(mov: MovimentacaoEntity): Long
    @Update suspend fun update(mov: MovimentacaoEntity)
    @Query("DELETE FROM movimentacoes WHERE id = :id") suspend fun deleteById(id: Long)

    /**
     * Edits the user-visible fields in place. Unlike [update], it leaves `recorrenciaId` and
     * `criadaEm` untouched — `Movimentacao.toEntity()` cannot carry the original `criadaEm`.
     */
    @Query(
        "UPDATE movimentacoes SET descricao = :descricao, valorCentavos = :valorCentavos, " +
            "dataEpochDay = :dataEpochDay, natureza = :natureza, editadaManualmente = :editadaManualmente " +
            "WHERE id = :id",
    )
    suspend fun updateCampos(
        id: Long,
        descricao: String,
        valorCentavos: Long,
        dataEpochDay: Long,
        natureza: String,
        editadaManualmente: Boolean,
    )

    @Query("DELETE FROM movimentacao_tags WHERE movimentacaoId = :movId")
    suspend fun clearTags(movId: Long)
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTagCross(cross: MovimentacaoTagCross)

    @Transaction
    suspend fun setTags(movId: Long, tagIds: List<Long>) {
        clearTags(movId)
        tagIds.forEach { insertTagCross(MovimentacaoTagCross(movId, it)) }
    }

    @Query("DELETE FROM movimentacoes WHERE recorrenciaId = :recorrenciaId AND dataEpochDay >= :fromEpochDay AND editadaManualmente = 0")
    suspend fun deleteInstanciasNaoEditadasAPartirDe(recorrenciaId: Long, fromEpochDay: Long)

    @Query("DELETE FROM movimentacoes WHERE recorrenciaId = :recorrenciaId")
    suspend fun deleteTodasInstancias(recorrenciaId: Long)

    @Query(
        "SELECT COUNT(*) FROM movimentacoes WHERE recorrenciaId = :recorrenciaId " +
            "AND dataEpochDay BETWEEN :fromEpochDay AND :toEpochDay",
    )
    suspend fun countInstancias(recorrenciaId: Long, fromEpochDay: Long, toEpochDay: Long): Int
}

@Dao
interface RecorrenciaDao {
    @Transaction
    @Query("SELECT * FROM recorrencias ORDER BY diaDoMes")
    fun observeAll(): Flow<List<RecorrenciaComTags>>

    @Insert suspend fun insert(rec: RecorrenciaEntity): Long
    @Update suspend fun update(rec: RecorrenciaEntity)
    @Query("DELETE FROM recorrencias WHERE id = :id") suspend fun deleteById(id: Long)

    @Query("DELETE FROM recorrencia_tags WHERE recorrenciaId = :recId")
    suspend fun clearTags(recId: Long)
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTagCross(cross: RecorrenciaTagCross)

    @Transaction
    suspend fun setTags(recId: Long, tagIds: List<Long>) {
        clearTags(recId)
        tagIds.forEach { insertTagCross(RecorrenciaTagCross(recId, it)) }
    }
}

@Dao
interface TagDao {
    @Query("SELECT * FROM tags ORDER BY nome")
    fun observeAll(): Flow<List<TagEntity>>

    @Insert suspend fun insert(tag: TagEntity): Long
    @Query("UPDATE tags SET nome = :nome WHERE id = :id") suspend fun rename(id: Long, nome: String)
    @Query("DELETE FROM tags WHERE id = :id") suspend fun deleteById(id: Long)
}
