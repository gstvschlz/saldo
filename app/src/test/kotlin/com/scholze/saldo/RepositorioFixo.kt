package com.scholze.saldo

import com.scholze.saldo.data.SaldoRepository
import com.scholze.saldo.domain.EscopoEdicao
import com.scholze.saldo.domain.EscopoExclusao
import com.scholze.saldo.domain.LedgerInput
import com.scholze.saldo.domain.Movimentacao
import com.scholze.saldo.domain.RepetirOpcao
import com.scholze.saldo.domain.Tag
import com.scholze.saldo.domain.TagSnapshot
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** Um repositório que só devolve um ledger fixo; as escritas não fazem nada. */
class RepositorioFixo(input: LedgerInput, tags: List<Tag> = emptyList()) : SaldoRepository {
    override val ledger: Flow<LedgerInput> = flowOf(input)
    override val tags: Flow<List<Tag>> = flowOf(tags)
    override suspend fun abrirMes(mes: YearMonth) = Unit
    override suspend fun criar(mov: Movimentacao, repetir: RepetirOpcao) = Unit
    override suspend fun editar(mov: Movimentacao, escopo: EscopoEdicao) = Unit
    override suspend fun excluir(mov: Movimentacao): Movimentacao = mov
    override suspend fun restaurar(mov: Movimentacao) = Unit
    override suspend fun excluirRecorrencia(recorrenciaId: Long, aPartirDe: YearMonth, escopo: EscopoExclusao) = Unit
    override suspend fun converterEmRecorrencia(mov: Movimentacao, diaDoMes: Int) = Unit
    override suspend fun encerrarRecorrencia(mov: Movimentacao) = Unit
    override suspend fun pausar(recorrenciaId: Long, hoje: LocalDate) = Unit
    override suspend fun retomar(recorrenciaId: Long, hoje: LocalDate) = Unit
    override suspend fun criarTag(nome: String, cor: Long): Long = 1
    override suspend fun renomearTag(id: Long, nome: String) = Unit
    override suspend fun excluirTag(id: Long): TagSnapshot = TagSnapshot(Tag(id, "", 0), emptyList(), emptyList())
    override suspend fun restaurarTag(snapshot: TagSnapshot) = Unit
    override suspend fun recolorirTag(id: Long, cor: Long) = Unit
}
