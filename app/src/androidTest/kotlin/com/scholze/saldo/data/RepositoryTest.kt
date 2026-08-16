package com.scholze.saldo.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.scholze.saldo.data.db.SaldoDatabase
import com.scholze.saldo.domain.EscopoEdicao
import com.scholze.saldo.domain.EscopoExclusao
import com.scholze.saldo.domain.Movimentacao
import com.scholze.saldo.domain.Natureza
import com.scholze.saldo.domain.RepetirOpcao
import com.scholze.saldo.domain.Tag
import java.io.File
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RepositoryTest {
    private lateinit var db: SaldoDatabase
    private lateinit var repo: RoomSaldoRepository
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val hoje = LocalDate.parse("2026-07-20")

    private lateinit var arquivoSettings: File

    @Before
    fun setup() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = SaldoDatabase.inMemory(ctx)
        // Caminho decidido uma vez, fora da lambda: `produceFile` tem de devolver sempre o mesmo.
        arquivoSettings = File(ctx.cacheDir, "test-${UUID.randomUUID()}.preferences_pb")
        val store = SettingsStore(PreferenceDataStoreFactory.create(scope = scope) { arquivoSettings })
        runBlocking { store.definirSaldoInicial(100_000_00, LocalDate.parse("2026-07-01")) }
        repo = RoomSaldoRepository(db, store, hoje = flowOf(hoje))
    }

    @After
    fun tearDown() {
        db.close()
        scope.cancel()
        arquivoSettings.delete()
    }

    private fun mov(dia: String, centavos: Long) = Movimentacao(
        descricao = "m", valorCentavos = centavos, data = LocalDate.parse(dia), natureza = Natureza.DIARIO,
    )

    @Test
    fun abrirMesMaterializaUmaVezSo() = runBlocking {
        repo.criar(mov("2026-07-15", 8_240_00), RepetirOpcao.TodoMes(15))
        repo.abrirMes(YearMonth.of(2026, 8))
        repo.abrirMes(YearMonth.of(2026, 8)) // idempotente
        val input = repo.ledger.first()
        val agosto = input.movimentacoes.filter { YearMonth.from(it.data) == YearMonth.of(2026, 8) }
        assertEquals(1, agosto.size)
        assertEquals(LocalDate.parse("2026-08-15"), agosto[0].data)
    }

    @Test
    fun criarRecorrenteSemeiaMesesJaMaterializados() = runBlocking {
        repo.abrirMes(YearMonth.of(2026, 8)) // agosto aberto ANTES do template
        repo.criar(mov("2026-07-15", 8_240_00), RepetirOpcao.TodoMes(15))
        val input = repo.ledger.first()
        assertEquals(
            listOf("2026-07-15", "2026-08-15"),
            input.movimentacoes.map { it.data.toString() }.sorted(),
        )
    }

    @Test
    fun editarDaquiEmDianteRespeitaInstanciasEditadas() = runBlocking {
        repo.abrirMes(YearMonth.of(2026, 8))
        repo.abrirMes(YearMonth.of(2026, 9))
        repo.criar(mov("2026-07-03", -2_400_00).copy(descricao = "aluguel"), RepetirOpcao.TodoMes(3))
        var input = repo.ledger.first()
        val setembro = input.movimentacoes.first { YearMonth.from(it.data) == YearMonth.of(2026, 9) }
        repo.editar(setembro.copy(valorCentavos = -2_600_00), EscopoEdicao.SO_ESTE_MES)

        val agosto = repo.ledger.first().movimentacoes.first { YearMonth.from(it.data) == YearMonth.of(2026, 8) }
        repo.editar(agosto.copy(valorCentavos = -2_500_00), EscopoEdicao.DAQUI_EM_DIANTE)

        input = repo.ledger.first()
        val valores = input.movimentacoes.sortedBy { it.data }.map { it.valorCentavos }
        // jul intocado (antes do "daqui"), ago atualizado, set preservado (editado manualmente)
        assertEquals(listOf(-2_400_00L, -2_500_00L, -2_600_00L), valores)
    }

    @Test
    fun criarRecorrenteEmMesNuncaAbertoSemeiaOutrosTemplates() = runBlocking {
        repo.criar(mov("2026-07-15", 8_240_00).copy(descricao = "salário"), RepetirOpcao.TodoMes(15))
        // Outubro nunca foi aberto: marcá-lo materializado sem semear "salário" o apagaria de lá.
        repo.criar(mov("2026-10-03", -2_400_00).copy(descricao = "aluguel"), RepetirOpcao.TodoMes(3))

        suspend fun outubro() = repo.ledger.first().movimentacoes
            .filter { YearMonth.from(it.data) == YearMonth.of(2026, 10) }
            .sortedBy { it.data }
            .map { "${it.data} ${it.descricao}" }

        assertEquals(listOf("2026-10-03 aluguel", "2026-10-15 salário"), outubro())

        repo.abrirMes(YearMonth.of(2026, 10))
        repo.abrirMes(YearMonth.of(2026, 10))
        assertEquals(listOf("2026-10-03 aluguel", "2026-10-15 salário"), outubro())
    }

    @Test
    fun editarDaquiEmDianteCongelaMesesPassadosNaoAbertos() = runBlocking {
        repo.criar(mov("2026-06-03", -2_400_00).copy(descricao = "aluguel"), RepetirOpcao.TodoMes(3))
        repo.abrirMes(YearMonth.of(2026, 8)) // julho fica pulado, nunca aberto
        val agosto = repo.ledger.first().movimentacoes
            .first { YearMonth.from(it.data) == YearMonth.of(2026, 8) }
        repo.editar(agosto.copy(valorCentavos = -2_500_00), EscopoEdicao.DAQUI_EM_DIANTE)

        repo.abrirMes(YearMonth.of(2026, 7)) // julho já foi congelado pela edição; não duplica
        val porMes = repo.ledger.first().movimentacoes
            .sortedBy { it.data }
            .map { "${it.data} ${it.valorCentavos}" }
        // Junho e julho preservam o valor ANTIGO; só agosto em diante muda.
        assertEquals(
            listOf("2026-06-03 -240000", "2026-07-03 -240000", "2026-08-03 -250000"),
            porMes,
        )
    }

    @Test
    fun excluirInstanciaNaoRessuscita() = runBlocking {
        repo.criar(mov("2026-07-15", 8_240_00), RepetirOpcao.TodoMes(15))
        val julho = repo.ledger.first().movimentacoes.single()
        repo.excluir(julho)
        repo.abrirMes(YearMonth.of(2026, 7)) // julho já materializado: não re-semeia

        val input = repo.ledger.first()
        assertEquals(
            emptyList<String>(),
            input.movimentacoes
                .filter { YearMonth.from(it.data) == YearMonth.of(2026, 7) }
                .map { it.data.toString() },
        )
        assertEquals(1, input.recorrencias.size) // o template sobrevive, só a instância morreu
    }

    @Test
    fun excluirRecorrenciaSoFuturas() = runBlocking {
        repo.criar(mov("2026-06-03", -2_400_00).copy(descricao = "aluguel"), RepetirOpcao.TodoMes(3))
        repo.abrirMes(YearMonth.of(2026, 8)) // julho fica pulado
        repo.abrirMes(YearMonth.of(2026, 9))
        val setembro = repo.ledger.first().movimentacoes
            .first { YearMonth.from(it.data) == YearMonth.of(2026, 9) }
        repo.editar(setembro.copy(valorCentavos = -2_600_00), EscopoEdicao.SO_ESTE_MES)

        val recId = repo.ledger.first().recorrencias.single().id
        repo.excluirRecorrencia(recId, YearMonth.of(2026, 8), EscopoExclusao.SO_FUTURAS)

        val input = repo.ledger.first()
        // Junho é anterior ao corte e sobrevive; agosto (não editado) some; setembro sobrevive
        // por estar marcado como editado manualmente. Julho nunca foi aberto — segue virtual.
        assertEquals(
            listOf("2026-06-03 -240000", "2026-09-03 -260000"),
            input.movimentacoes.sortedBy { it.data }.map { "${it.data} ${it.valorCentavos}" },
        )
        assertEquals(YearMonth.of(2026, 7), input.recorrencias.single().fim)
    }

    @Test
    fun excluirRecorrenciaTodas() = runBlocking {
        repo.criar(mov("2026-06-03", -2_400_00).copy(descricao = "aluguel"), RepetirOpcao.TodoMes(3))
        repo.abrirMes(YearMonth.of(2026, 8))
        repo.abrirMes(YearMonth.of(2026, 9))
        val recId = repo.ledger.first().recorrencias.single().id
        repo.excluirRecorrencia(recId, YearMonth.of(2026, 8), EscopoExclusao.TODAS)

        val input = repo.ledger.first()
        assertEquals(emptyList<Long?>(), input.movimentacoes.map { it.recorrenciaId })
        assertEquals(0, input.recorrencias.size)
    }

    @Test
    fun materializacaoCarregaTags() = runBlocking {
        val tagId = repo.criarTag("comida", 0xFFA6486B)
        val comTag = mov("2026-07-15", -50_00)
            .copy(tags = listOf(Tag(id = tagId, nome = "comida", cor = 0xFFA6486B)))
        repo.criar(comTag, RepetirOpcao.TodoMes(15))
        repo.abrirMes(YearMonth.of(2026, 8))

        val agosto = repo.ledger.first().movimentacoes
            .single { YearMonth.from(it.data) == YearMonth.of(2026, 8) }
        assertEquals("comida", agosto.tags.single().nome)
    }

    @Test
    fun excluirRejeitaMovimentacaoVirtual() = runBlocking {
        // Ocorrência virtual do ProjectionEngine: id 0, sem linha correspondente no banco.
        val virtual = mov("2026-07-15", -50_00)
        try {
            repo.excluir(virtual)
            fail("excluir deveria rejeitar ocorrência virtual")
        } catch (esperado: IllegalArgumentException) {
            // o delete seria um no-op e o "desfazer" criaria uma duplicata permanente
        }
        try {
            repo.editar(virtual, EscopoEdicao.SO_ESTE_MES)
            fail("editar SO_ESTE_MES deveria rejeitar ocorrência virtual")
        } catch (esperado: IllegalArgumentException) {
            // o UPDATE ... WHERE id = 0 não acertaria linha nenhuma
        }
    }

    @Test
    fun editarDaquiEmDianteAceitaOcorrenciaVirtual() = runBlocking {
        repo.criar(mov("2026-06-03", -2_400_00).copy(descricao = "aluguel"), RepetirOpcao.TodoMes(3))
        val recId = repo.ledger.first().recorrencias.single().id

        // Agosto NUNCA foi aberto: a linha que o usuário vê ali é a expansão virtual do
        // template, montada aqui à mão exatamente como o ProjectionEngine a produz — id 0.
        // SO_ESTE_MES e excluir rejeitam isso; DAQUI_EM_DIANTE é a exceção deliberada,
        // porque reescreve o template e não a linha.
        val virtualAgosto = Movimentacao(
            id = 0,
            descricao = "aluguel",
            valorCentavos = -2_400_00,
            data = LocalDate.parse("2026-08-03"),
            natureza = Natureza.DIARIO,
            recorrenciaId = recId,
        )
        repo.editar(virtualAgosto.copy(valorCentavos = -2_500_00), EscopoEdicao.DAQUI_EM_DIANTE)

        val input = repo.ledger.first()
        assertEquals(-2_500_00L, input.recorrencias.single().valorCentavos)
        assertEquals(
            -2_400_00L,
            input.movimentacoes.single { YearMonth.from(it.data) == YearMonth.of(2026, 6) }.valorCentavos,
        )
        // Agosto continua sem linha e sem marca: a edição olhou só para o template.
        assertEquals(
            emptyList<String>(),
            input.movimentacoes
                .filter { YearMonth.from(it.data) == YearMonth.of(2026, 8) }
                .map { it.data.toString() },
        )
        assertEquals(false, YearMonth.of(2026, 8) in input.mesesMaterializados)
    }

    @Test
    fun moverInstanciaParaMesNaoAbertoMaterializaDestino() = runBlocking {
        repo.criar(mov("2026-07-15", 8_240_00).copy(descricao = "salário"), RepetirOpcao.TodoMes(15))
        repo.criar(mov("2026-07-03", -2_400_00).copy(descricao = "aluguel"), RepetirOpcao.TodoMes(3))

        suspend fun setembro() = repo.ledger.first().movimentacoes
            .filter { YearMonth.from(it.data) == YearMonth.of(2026, 9) }
            .sortedBy { it.data }
            .map { "${it.data} ${it.descricao}" }

        val aluguel = repo.ledger.first().movimentacoes.first { it.descricao == "aluguel" }
        // Setembro nunca foi aberto: sem materializar o destino, o template do aluguel seguiria
        // expandindo virtualmente lá e a recorrência apareceria duas vezes.
        repo.editar(aluguel.copy(data = LocalDate.parse("2026-09-03")), EscopoEdicao.SO_ESTE_MES)

        assertEquals(listOf("2026-09-03 aluguel", "2026-09-15 salário"), setembro())
        repo.abrirMes(YearMonth.of(2026, 9)) // destino já marcado: não duplica
        assertEquals(listOf("2026-09-03 aluguel", "2026-09-15 salário"), setembro())

        // O mês de origem segue materializado e simplesmente perde a instância movida.
        assertEquals(
            listOf("2026-07-15 salário"),
            repo.ledger.first().movimentacoes
                .filter { YearMonth.from(it.data) == YearMonth.of(2026, 7) }
                .map { "${it.data} ${it.descricao}" },
        )
    }

    @Test
    fun excluirERestaurar() = runBlocking {
        repo.criar(mov("2026-07-10", -50_00), RepetirOpcao.Nao)
        val salva = repo.ledger.first().movimentacoes.single()
        val snapshot = repo.excluir(salva)
        assertEquals(0, repo.ledger.first().movimentacoes.size)
        repo.restaurar(snapshot)
        assertEquals(-50_00L, repo.ledger.first().movimentacoes.single().valorCentavos)
    }

    // ---- tags: o fluxo `tags` e as três escritas ----

    @Test
    fun tagsFluemAoCriarRenomearEExcluir() = runBlocking {
        val id = repo.criarTag("comida", 1L)
        assertEquals(listOf("comida"), repo.tags.first().map { it.nome })
        repo.renomearTag(id, "mercado")
        assertEquals(listOf("mercado"), repo.tags.first().map { it.nome })
        repo.excluirTag(id)
        assertEquals(emptyList<Tag>(), repo.tags.first())
    }

    /** Excluir a etiqueta desanexa; a movimentação continua no ledger, só sem ela. */
    @Test
    fun excluirTagDesanexaDasMovimentacoes() = runBlocking {
        val id = repo.criarTag("comida", 1L)
        val tag = Tag(id = id, nome = "comida", cor = 1L)
        repo.criar(mov("2026-07-10", -50_00).copy(tags = listOf(tag)), RepetirOpcao.Nao)
        assertEquals(listOf("comida"), repo.ledger.first().movimentacoes.single().tags.map { it.nome })
        repo.excluirTag(id)
        val depois = repo.ledger.first().movimentacoes.single()
        assertEquals(-50_00L, depois.valorCentavos)
        assertEquals(emptyList<Tag>(), depois.tags)
    }
}
