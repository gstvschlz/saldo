package com.scholze.saldo.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        MesMaterializadoEntity::class,
        MovimentacaoEntity::class,
        RecorrenciaEntity::class,
        TagEntity::class,
        MovimentacaoTagCross::class,
        RecorrenciaTagCross::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class SaldoDatabase : RoomDatabase() {
    abstract fun mesMaterializadoDao(): MesMaterializadoDao
    abstract fun movimentacaoDao(): MovimentacaoDao
    abstract fun recorrenciaDao(): RecorrenciaDao
    abstract fun tagDao(): TagDao

    companion object {
        /**
         * `applicationContext`: o banco vive enquanto o processo viver, e guardar um
         * Context de Activity aqui seria segurá-la para sempre. Hoje o único chamador é o
         * [com.scholze.saldo.AppContainer], que já recebe a Application — a normalização
         * é para que continue verdade se alguém construir o container de outro lugar.
         */
        fun build(context: Context): SaldoDatabase =
            Room.databaseBuilder(context.applicationContext, SaldoDatabase::class.java, "saldo.db").build()

        fun inMemory(context: Context): SaldoDatabase =
            Room.inMemoryDatabaseBuilder(context, SaldoDatabase::class.java).build()
    }
}
