package com.scholze.saldo.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.scholze.saldo.data.db.SaldoDatabase
import com.scholze.saldo.domain.EscopoEdicao
import com.scholze.saldo.domain.Movimentacao
import com.scholze.saldo.domain.Natureza
import com.scholze.saldo.domain.RepetirOpcao
import java.io.File
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RepositoryTest {
    private lateinit var db: SaldoDatabase
    private lateinit var repo: RoomSaldoRepository
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val hoje = LocalDate.parse("2026-07-20")

    @Before
    fun setup() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = SaldoDatabase.inMemory(ctx)
        val store = SettingsStore(
            PreferenceDataStoreFactory.create(scope = scope) {
                File(ctx.cacheDir, "test-${UUID.randomUUID()}.preferences_pb")
            },
        )
        runBlocking { store.definirSaldoInicial(100_000_00, LocalDate.parse("2026-07-01")) }
        repo = RoomSaldoRepository(db, store) { hoje }
    }

    @After fun tearDown() { db.close(); scope.cancel() }

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
    fun excluirERestaurar() = runBlocking {
        repo.criar(mov("2026-07-10", -50_00), RepetirOpcao.Nao)
        val salva = repo.ledger.first().movimentacoes.single()
        val snapshot = repo.excluir(salva)
        assertEquals(0, repo.ledger.first().movimentacoes.size)
        repo.restaurar(snapshot)
        assertEquals(-50_00L, repo.ledger.first().movimentacoes.single().valorCentavos)
    }
}
