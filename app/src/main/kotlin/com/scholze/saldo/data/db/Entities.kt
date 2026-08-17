package com.scholze.saldo.data.db

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Junction
import androidx.room.PrimaryKey
import androidx.room.Relation
import com.scholze.saldo.domain.Movimentacao
import com.scholze.saldo.domain.Natureza
import com.scholze.saldo.domain.Recorrencia
import com.scholze.saldo.domain.Tag
import java.time.LocalDate
import java.time.YearMonth

/** Months whose recurrence templates have been materialized into rows. */
@Entity(tableName = "meses_materializados")
data class MesMaterializadoEntity(
    /** YearMonth encoded as year * 12 + (monthValue - 1). */
    @PrimaryKey val anoMes: Int,
)

/** `recorrenciaId` indexado: toda edição/exclusão de recorrência filtra as instâncias por ele. */
@Entity(tableName = "movimentacoes", indices = [Index("recorrenciaId")])
data class MovimentacaoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val descricao: String,
    val valorCentavos: Long,
    val dataEpochDay: Long,
    val natureza: String,                 // Natureza.name
    val recorrenciaId: Long? = null,
    val editadaManualmente: Boolean = false,
    val criadaEm: Long = 0,
)

@Entity(tableName = "recorrencias")
data class RecorrenciaEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val descricao: String,
    val valorCentavos: Long,
    val natureza: String,
    val diaDoMes: Int,
    val inicioAnoMes: Int,                // year * 12 + monthValue - 1
    val fimAnoMes: Int? = null,
    val ativa: Boolean = true,
)

@Entity(tableName = "tags")
data class TagEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nome: String,
    val cor: Long,
)

@Entity(
    tableName = "movimentacao_tags",
    primaryKeys = ["movimentacaoId", "tagId"],
    foreignKeys = [
        ForeignKey(MovimentacaoEntity::class, ["id"], ["movimentacaoId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(TagEntity::class, ["id"], ["tagId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("tagId")],
)
data class MovimentacaoTagCross(val movimentacaoId: Long, val tagId: Long)

@Entity(
    tableName = "recorrencia_tags",
    primaryKeys = ["recorrenciaId", "tagId"],
    foreignKeys = [
        ForeignKey(RecorrenciaEntity::class, ["id"], ["recorrenciaId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(TagEntity::class, ["id"], ["tagId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("tagId")],
)
data class RecorrenciaTagCross(val recorrenciaId: Long, val tagId: Long)

data class MovimentacaoComTags(
    @Embedded val mov: MovimentacaoEntity,
    @Relation(
        parentColumn = "id", entityColumn = "id",
        associateBy = Junction(MovimentacaoTagCross::class, parentColumn = "movimentacaoId", entityColumn = "tagId"),
    )
    val tags: List<TagEntity>,
) {
    fun toDomain() = Movimentacao(
        id = mov.id, descricao = mov.descricao, valorCentavos = mov.valorCentavos,
        data = LocalDate.ofEpochDay(mov.dataEpochDay), natureza = Natureza.valueOf(mov.natureza),
        recorrenciaId = mov.recorrenciaId, editadaManualmente = mov.editadaManualmente,
        tags = tags.map { it.toDomain() },
        criadaEm = mov.criadaEm,
    )
}

data class RecorrenciaComTags(
    @Embedded val rec: RecorrenciaEntity,
    @Relation(
        parentColumn = "id", entityColumn = "id",
        associateBy = Junction(RecorrenciaTagCross::class, parentColumn = "recorrenciaId", entityColumn = "tagId"),
    )
    val tags: List<TagEntity>,
) {
    fun toDomain() = Recorrencia(
        id = rec.id, descricao = rec.descricao, valorCentavos = rec.valorCentavos,
        natureza = Natureza.valueOf(rec.natureza), diaDoMes = rec.diaDoMes,
        inicio = rec.inicioAnoMes.toYearMonth(), fim = rec.fimAnoMes?.toYearMonth(),
        ativa = rec.ativa, tags = tags.map { it.toDomain() },
    )
}

fun TagEntity.toDomain() = Tag(id = id, nome = nome, cor = cor)
fun Tag.toEntity() = TagEntity(id = id, nome = nome, cor = cor)
/**
 * Tags are persisted separately via `MovimentacaoDao.setTags` — inserting this entity alone writes no tags.
 * `criadaEm` conhecido é preservado (o "desfazer" reinsere o snapshot com a data de criação
 * original); só uma linha nova (`criadaEm == 0`) recebe o carimbo de agora.
 */
fun Movimentacao.toEntity() = MovimentacaoEntity(
    id = id, descricao = descricao, valorCentavos = valorCentavos,
    dataEpochDay = data.toEpochDay(), natureza = natureza.name,
    recorrenciaId = recorrenciaId, editadaManualmente = editadaManualmente,
    criadaEm = if (criadaEm != 0L) criadaEm else System.currentTimeMillis(),
)
/** Tags are persisted separately via `RecorrenciaDao.setTags` — inserting this entity alone writes no tags. */
fun Recorrencia.toEntity() = RecorrenciaEntity(
    id = id, descricao = descricao, valorCentavos = valorCentavos, natureza = natureza.name,
    diaDoMes = diaDoMes, inicioAnoMes = inicio.toAnoMes(), fimAnoMes = fim?.toAnoMes(), ativa = ativa,
)

fun YearMonth.toAnoMes(): Int = year * 12 + monthValue - 1
fun Int.toYearMonth(): YearMonth = YearMonth.of(this / 12, this % 12 + 1)
