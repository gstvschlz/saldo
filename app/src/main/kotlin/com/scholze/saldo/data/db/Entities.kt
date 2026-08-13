package com.scholze.saldo.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Months whose recurrence templates have been materialized into rows. */
@Entity(tableName = "meses_materializados")
data class MesMaterializadoEntity(
    /** YearMonth encoded as year * 12 + (monthValue - 1). */
    @PrimaryKey val anoMes: Int,
)
