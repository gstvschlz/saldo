package com.scholze.saldo.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface MesMaterializadoDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun marcar(mes: MesMaterializadoEntity)

    @Query("SELECT anoMes FROM meses_materializados")
    suspend fun todos(): List<Int>
}
