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

    @Query("DELETE FROM meses_materializados") suspend fun deleteTodos()
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
     * Touches only the user-visible fields, leaving `recorrenciaId` and `criadaEm` alone —
     * unlike [update], which overwrites the whole row. Neither is user-editable through an
     * instance edit.
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

    /** A linha vira avulsa: sem template e sem a marca de editada (que só faz sentido numa instância). */
    @Query("UPDATE movimentacoes SET recorrenciaId = NULL, editadaManualmente = 0 WHERE id = :id")
    suspend fun desligarDaRecorrencia(id: Long)

    /**
     * Mesma coisa que [desligarDaRecorrencia], mas para TODAS as instâncias da recorrência de
     * uma vez — usado quando o template é apagado, para nenhuma linha (nem as editadas à mão,
     * que sobrevivem à limpeza normal) ficar apontando para um id que não existe mais.
     */
    @Query("UPDATE movimentacoes SET recorrenciaId = NULL, editadaManualmente = 0 WHERE recorrenciaId = :id")
    suspend fun desligarTodasDaRecorrencia(id: Long)

    @Query("UPDATE movimentacoes SET recorrenciaId = :recorrenciaId, editadaManualmente = :editada WHERE id = :id")
    suspend fun ligarARecorrencia(id: Long, recorrenciaId: Long, editada: Boolean)

    /** Restaurar/apagar: `clearAllTables` abre a própria transação e não serve aqui dentro. */
    @Query("DELETE FROM movimentacoes") suspend fun deleteTodas()

    @Query("DELETE FROM movimentacao_tags") suspend fun deleteTodosCruzamentos()
}

@Dao
interface RecorrenciaDao {
    @Transaction
    @Query("SELECT * FROM recorrencias ORDER BY diaDoMes")
    fun observeAll(): Flow<List<RecorrenciaComTags>>

    /** Leitura única para dentro de transações — um Flow não participa da transação. */
    @Transaction
    @Query("SELECT * FROM recorrencias ORDER BY diaDoMes")
    suspend fun todos(): List<RecorrenciaComTags>

    @Query("UPDATE recorrencias SET ativa = :ativa WHERE id = :id")
    suspend fun definirAtiva(id: Long, ativa: Boolean)

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

    @Query("DELETE FROM recorrencias") suspend fun deleteTodas()

    @Query("DELETE FROM recorrencia_tags") suspend fun deleteTodosCruzamentos()
}

@Dao
interface TagDao {
    @Query("SELECT * FROM tags ORDER BY nome")
    fun observeAll(): Flow<List<TagEntity>>

    /** Leitura única para dentro de transações — um Flow não participa da transação. */
    @Query("SELECT * FROM tags ORDER BY nome")
    suspend fun todas(): List<TagEntity>

    @Query("SELECT * FROM tags WHERE id = :id")
    suspend fun porId(id: Long): TagEntity?

    @Insert suspend fun insert(tag: TagEntity): Long
    @Query("UPDATE tags SET nome = :nome WHERE id = :id") suspend fun rename(id: Long, nome: String)
    @Query("DELETE FROM tags WHERE id = :id") suspend fun deleteById(id: Long)

    @Query("UPDATE tags SET cor = :cor WHERE id = :id") suspend fun recolor(id: Long, cor: Long)

    @Query("SELECT movimentacaoId FROM movimentacao_tags WHERE tagId = :tagId")
    suspend fun movimentacoesDaTag(tagId: Long): List<Long>

    @Query("SELECT recorrenciaId FROM recorrencia_tags WHERE tagId = :tagId")
    suspend fun recorrenciasDaTag(tagId: Long): List<Long>

    /** Reinsere com o id do snapshot — os vínculos apontam para ele. Conflito = já voltou. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertComId(tag: TagEntity)

    /**
     * Religa só as movimentações de [ids] que ainda existem — um `@Insert` direto do cross
     * violaria a FK e derrubaria a transação inteira se uma delas tivesse sido apagada
     * enquanto a tag estava excluída (o "desfazer" chegou tarde para aquela linha).
     */
    @Query(
        "INSERT OR IGNORE INTO movimentacao_tags (movimentacaoId, tagId) " +
            "SELECT id, :tagId FROM movimentacoes WHERE id IN (:ids)",
    )
    suspend fun religarMovimentacoes(tagId: Long, ids: List<Long>)

    /** Mesma razão de [religarMovimentacoes], para recorrências. */
    @Query(
        "INSERT OR IGNORE INTO recorrencia_tags (recorrenciaId, tagId) " +
            "SELECT id, :tagId FROM recorrencias WHERE id IN (:ids)",
    )
    suspend fun religarRecorrencias(tagId: Long, ids: List<Long>)

    @Query("DELETE FROM tags") suspend fun deleteTodas()
}

@Dao
interface DeteccaoDao {
    @Insert suspend fun insert(d: DeteccaoEntity): Long

    @Query("SELECT * FROM deteccoes WHERE emMillis >= :desde ORDER BY emMillis")
    suspend fun desde(desde: Long): List<DeteccaoEntity>

    @Query("SELECT * FROM deteccoes WHERE id = :id")
    suspend fun porId(id: Long): DeteccaoEntity?

    @Query("UPDATE deteccoes SET resolvida = 1 WHERE id = :id")
    suspend fun resolver(id: Long)

    /** A varredura das 24 h. Chamada a cada detecção: é um DELETE indexado, sai barato. */
    @Query("DELETE FROM deteccoes WHERE emMillis < :antesDe")
    suspend fun limpar(antesDe: Long)

    @Query("DELETE FROM deteccoes") suspend fun deleteTodas()
}
