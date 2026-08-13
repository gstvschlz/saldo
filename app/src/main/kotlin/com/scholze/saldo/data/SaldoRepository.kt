package com.scholze.saldo.data

import androidx.room.withTransaction
import com.scholze.saldo.data.db.MesMaterializadoEntity
import com.scholze.saldo.data.db.SaldoDatabase
import com.scholze.saldo.data.db.toAnoMes
import com.scholze.saldo.data.db.toDomain
import com.scholze.saldo.data.db.toEntity
import com.scholze.saldo.data.db.toYearMonth
import com.scholze.saldo.domain.EscopoEdicao
import com.scholze.saldo.domain.EscopoExclusao
import com.scholze.saldo.domain.LedgerInput
import com.scholze.saldo.domain.Movimentacao
import com.scholze.saldo.domain.Recorrencia
import com.scholze.saldo.domain.RecurrenceExpander
import com.scholze.saldo.domain.RepetirOpcao
import com.scholze.saldo.domain.Tag
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

interface SaldoRepository {
    val ledger: Flow<LedgerInput>
    val tags: Flow<List<Tag>>
    suspend fun abrirMes(mes: YearMonth)
    suspend fun criar(mov: Movimentacao, repetir: RepetirOpcao)
    suspend fun editar(mov: Movimentacao, escopo: EscopoEdicao)
    suspend fun excluir(mov: Movimentacao): Movimentacao
    suspend fun restaurar(mov: Movimentacao)
    suspend fun excluirRecorrencia(recorrenciaId: Long, aPartirDe: YearMonth, escopo: EscopoExclusao)
    suspend fun criarTag(nome: String, cor: Long): Long
    suspend fun renomearTag(id: Long, nome: String)
    suspend fun excluirTag(id: Long)
}

class RoomSaldoRepository(
    private val db: SaldoDatabase,
    private val settingsStore: SettingsStore,
    private val hojeProvider: () -> LocalDate = LocalDate::now,
) : SaldoRepository {

    private val movDao = db.movimentacaoDao()
    private val recDao = db.recorrenciaDao()
    private val tagDao = db.tagDao()
    private val mesDao = db.mesMaterializadoDao()

    override val ledger: Flow<LedgerInput> = combine(
        movDao.observeAll(),
        recDao.observeAll(),
        mesDao.observeTodos(),
        settingsStore.settings,
    ) { movs, recs, meses, settings ->
        LedgerInput(
            saldoInicialCentavos = settings.saldoInicialCentavos ?: 0L,
            saldoInicialData = settings.saldoInicialData ?: hojeProvider(),
            movimentacoes = movs.map { it.toDomain() },
            recorrencias = recs.map { it.toDomain() },
            mesesMaterializados = meses.map { it.toYearMonth() }.toSet(),
            cartao = settings.cartao,
            hoje = hojeProvider(),
        )
    }

    override val tags: Flow<List<Tag>> = tagDao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun abrirMes(mes: YearMonth) = db.withTransaction {
        val marcados = mesDao.todos().map { it.toYearMonth() }.toSet()
        if (mes in marcados) return@withTransaction
        val templates = recDao.observeAll().first().map { it.toDomain() }
        RecurrenceExpander.ocorrenciasNoMes(templates, mes).forEach { insertComTags(it) }
        mesDao.marcar(MesMaterializadoEntity(mes.toAnoMes()))
    }

    override suspend fun criar(mov: Movimentacao, repetir: RepetirOpcao) = db.withTransaction {
        when (repetir) {
            is RepetirOpcao.Nao -> insertComTags(mov)
            is RepetirOpcao.TodoMes -> {
                val inicio = YearMonth.from(mov.data)
                val recId = recDao.insert(
                    Recorrencia(
                        descricao = mov.descricao, valorCentavos = mov.valorCentavos,
                        natureza = mov.natureza, diaDoMes = repetir.dia, inicio = inicio,
                    ).toEntity(),
                )
                recDao.setTags(recId, mov.tags.map { it.id })
                val template = Recorrencia(
                    id = recId, descricao = mov.descricao, valorCentavos = mov.valorCentavos,
                    natureza = mov.natureza, diaDoMes = repetir.dia, inicio = inicio, tags = mov.tags,
                )
                // Instância deste mês usa a data digitada; meses já materializados >= início são semeados.
                insertComTags(mov.copy(recorrenciaId = recId))
                mesDao.todos().map { it.toYearMonth() }
                    .filter { it >= inicio && it != YearMonth.from(mov.data) }
                    .forEach { m ->
                        RecurrenceExpander.ocorrenciaNoMes(template, m)?.let { insertComTags(it) }
                    }
                // Garante o mês da própria movimentação marcado como materializado.
                mesDao.marcar(MesMaterializadoEntity(inicio.toAnoMes()))
            }
        }
    }

    override suspend fun editar(mov: Movimentacao, escopo: EscopoEdicao) = db.withTransaction {
        when (escopo) {
            EscopoEdicao.SO_ESTE_MES -> {
                // UPDATE campo a campo: `toEntity()` não carrega o `criadaEm` original e
                // `recorrenciaId` não muda numa edição de instância.
                movDao.updateCampos(
                    id = mov.id,
                    descricao = mov.descricao,
                    valorCentavos = mov.valorCentavos,
                    dataEpochDay = mov.data.toEpochDay(),
                    natureza = mov.natureza.name,
                    editadaManualmente = mov.editadaManualmente || mov.recorrenciaId != null,
                )
                movDao.setTags(mov.id, mov.tags.map { it.id })
            }
            EscopoEdicao.DAQUI_EM_DIANTE -> {
                val recId = requireNotNull(mov.recorrenciaId) { "escopo DAQUI_EM_DIANTE exige recorrência" }
                val mesInicio = YearMonth.from(mov.data)
                val templateAntigo = recDao.observeAll().first().first { it.rec.id == recId }.toDomain()
                val templateNovo = templateAntigo.copy(
                    descricao = mov.descricao, valorCentavos = mov.valorCentavos,
                    natureza = mov.natureza, diaDoMes = mov.data.dayOfMonth, tags = mov.tags,
                )
                recDao.update(templateNovo.toEntity())
                recDao.setTags(recId, mov.tags.map { it.id })
                movDao.deleteInstanciasNaoEditadasAPartirDe(recId, mesInicio.atDay(1).toEpochDay())
                // Re-semeia meses materializados >= mesInicio que ficaram sem instância.
                mesDao.todos().map { it.toYearMonth() }
                    .filter { it >= mesInicio }
                    .forEach { m ->
                        val existentes = movDao.countInstancias(
                            recId, m.atDay(1).toEpochDay(), m.atEndOfMonth().toEpochDay(),
                        )
                        if (existentes == 0) {
                            val occ = if (m == mesInicio) {
                                RecurrenceExpander.ocorrenciaNoMes(templateNovo, m)?.copy(data = mov.data)
                            } else {
                                RecurrenceExpander.ocorrenciaNoMes(templateNovo, m)
                            }
                            occ?.let { insertComTags(it) }
                        }
                    }
            }
        }
    }

    override suspend fun excluir(mov: Movimentacao): Movimentacao {
        movDao.deleteById(mov.id)
        return mov
    }

    override suspend fun restaurar(mov: Movimentacao) = db.withTransaction {
        insertComTags(mov.copy(id = 0))
    }

    override suspend fun excluirRecorrencia(recorrenciaId: Long, aPartirDe: YearMonth, escopo: EscopoExclusao) =
        db.withTransaction {
            when (escopo) {
                EscopoExclusao.SO_FUTURAS -> {
                    movDao.deleteInstanciasNaoEditadasAPartirDe(recorrenciaId, aPartirDe.atDay(1).toEpochDay())
                    val template = recDao.observeAll().first().first { it.rec.id == recorrenciaId }.toDomain()
                    recDao.update(template.copy(fim = aPartirDe.minusMonths(1)).toEntity())
                }
                EscopoExclusao.TODAS -> {
                    movDao.deleteTodasInstancias(recorrenciaId)
                    recDao.deleteById(recorrenciaId)
                }
            }
        }

    override suspend fun criarTag(nome: String, cor: Long): Long =
        tagDao.insert(Tag(nome = nome, cor = cor).toEntity())

    override suspend fun renomearTag(id: Long, nome: String) = tagDao.rename(id, nome)

    override suspend fun excluirTag(id: Long) = tagDao.deleteById(id)

    private suspend fun insertComTags(mov: Movimentacao) {
        val id = movDao.insert(mov.toEntity())
        if (mov.tags.isNotEmpty()) movDao.setTags(id, mov.tags.map { it.id })
    }
}
