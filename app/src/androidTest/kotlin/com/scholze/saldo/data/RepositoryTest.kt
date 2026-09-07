package com.scholze.saldo.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.scholze.saldo.data.db.SaldoDatabase
import com.scholze.saldo.domain.EscopoEdicao
import com.scholze.saldo.domain.EscopoExclusao
import com.scholze.saldo.domain.Movimentacao
import com.scholze.saldo.domain.Natureza
import com.scholze.saldo.domain.ProjectionEngine
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

    // ---- converter / encerrar / pausar / retomar (uso-diario-1) ----

    private suspend fun linhas() = repo.ledger.first().movimentacoes.sortedBy { it.data }
    private suspend fun templates() = repo.ledger.first().recorrencias

    @Test
    fun converterEmRecorrenciaLigaALinhaESemeiaOsMesesAbertosDepois() = runBlocking {
        repo.criar(mov("2026-07-10", -80_00), RepetirOpcao.Nao)
        repo.abrirMes(YearMonth.of(2026, 8))   // agosto aberto ANTES de converter
        // Julho também aberto: pinça o `>` estrito de `marcados.filter { it > inicio }` — um
        // `>=` por engano semearia OUTRA linha do template em julho, além da que já é a própria
        // instância convertida.
        repo.abrirMes(YearMonth.of(2026, 7))
        val avulsa = linhas().single()
        repo.converterEmRecorrencia(avulsa, diaDoMes = 10)

        val t = templates().single()
        assertEquals(10, t.diaDoMes)
        assertEquals(YearMonth.of(2026, 7), t.inicio)
        val julho = linhas().first { it.data == LocalDate.parse("2026-07-10") }
        assertEquals(t.id, julho.recorrenciaId)
        assertEquals(avulsa.id, julho.id)                       // a mesma linha, não uma cópia
        assertEquals(
            1,
            linhas().count { it.data == LocalDate.parse("2026-07-10") },
        )
        assertEquals(
            listOf("2026-07-10", "2026-08-10"),
            linhas().map { it.data.toString() },
        )
    }

    @Test
    fun converterComDiaDiferenteDaDataMarcaALinhaComoEditada() = runBlocking {
        repo.criar(mov("2026-07-10", -80_00), RepetirOpcao.Nao)
        repo.converterEmRecorrencia(linhas().single(), diaDoMes = 31)
        val julho = linhas().single()
        assertEquals(true, julho.editadaManualmente)
        assertEquals(31, templates().single().diaDoMes)
    }

    /**
     * A sheet muda descrição/valor no mesmo formulário em que liga "todo mês": não há um
     * `editar` separado antes de `converterEmRecorrencia`, então é ela quem tem de gravar os
     * dois — na linha E no template recém-criado.
     */
    @Test
    fun converterGravaDescricaoEValorMudadosNaLinhaENoTemplate() = runBlocking {
        repo.criar(mov("2026-07-10", -80_00), RepetirOpcao.Nao)
        val avulsa = linhas().single()
        repo.converterEmRecorrencia(avulsa.copy(descricao = "academia", valorCentavos = -90_00), diaDoMes = 10)

        val julho = linhas().single()
        assertEquals("academia", julho.descricao)
        assertEquals(-90_00L, julho.valorCentavos)
        val t = templates().single()
        assertEquals("academia", t.descricao)
        assertEquals(-90_00L, t.valorCentavos)
    }

    @Test
    fun encerrarRecorrenciaDesligaALinhaEApagaAsFuturas() = runBlocking {
        repo.criar(mov("2026-07-15", -50_00), RepetirOpcao.TodoMes(15))
        repo.abrirMes(YearMonth.of(2026, 8))
        repo.abrirMes(YearMonth.of(2026, 9))
        val agosto = linhas().first { it.data == LocalDate.parse("2026-08-15") }
        repo.encerrarRecorrencia(agosto, mesDaSerie = YearMonth.of(2026, 8))

        val t = templates().single()
        assertEquals(YearMonth.of(2026, 7), t.fim)
        val restantes = linhas()
        assertEquals(listOf("2026-07-15", "2026-08-15"), restantes.map { it.data.toString() })
        val ago = restantes.first { it.data == LocalDate.parse("2026-08-15") }
        assertEquals(null, ago.recorrenciaId)                   // virou avulsa
        assertEquals(false, ago.editadaManualmente)
    }

    /**
     * O usuário moveu a data da instância de setembro para dezembro no mesmo formulário em que
     * desligou "repetir". A série tem de acabar em agosto — o mês ANTERIOR ao de origem
     * (setembro) — e outubro/novembro (que ficariam "depois" de dezembro, se o corte usasse
     * `mov.data`) têm de sumir; usar `mov.data` (dezembro) cortaria a série no mês errado e
     * deixaria outubro/novembro vivos.
     */
    @Test
    fun encerrarUsaOMesDaSerieQuandoADataFoiMovidaNoMesmoFormulario() = runBlocking {
        repo.criar(mov("2026-07-15", -50_00), RepetirOpcao.TodoMes(15))
        repo.abrirMes(YearMonth.of(2026, 8))
        repo.abrirMes(YearMonth.of(2026, 9))
        repo.abrirMes(YearMonth.of(2026, 10))
        repo.abrirMes(YearMonth.of(2026, 11))
        repo.abrirMes(YearMonth.of(2026, 12))
        val setembro = linhas().first { it.data == LocalDate.parse("2026-09-15") }

        repo.encerrarRecorrencia(
            setembro.copy(data = LocalDate.parse("2026-12-15")),
            mesDaSerie = YearMonth.of(2026, 9),
        )

        val t = templates().single()
        assertEquals(YearMonth.of(2026, 8), t.fim)
        assertEquals(
            listOf("2026-07-15", "2026-08-15", "2026-12-15"),
            linhas().map { it.data.toString() },
        )
        val dez = linhas().first { it.data == LocalDate.parse("2026-12-15") }
        assertEquals(null, dez.recorrenciaId)                   // virou avulsa
        assertEquals(false, dez.editadaManualmente)
    }

    @Test
    fun encerrarNoPrimeiroMesApagaOTemplate() = runBlocking {
        repo.criar(mov("2026-07-15", -50_00), RepetirOpcao.TodoMes(15))
        repo.encerrarRecorrencia(linhas().single(), mesDaSerie = YearMonth.of(2026, 7))
        assertEquals(0, templates().size)
        val unica = linhas().single()
        assertEquals(null, unica.recorrenciaId)
    }

    @Test
    fun pausarMantemOMesCorrenteEApagaAsFuturasNaoEditadas() = runBlocking {
        repo.criar(mov("2026-07-15", -50_00), RepetirOpcao.TodoMes(15))
        repo.abrirMes(YearMonth.of(2026, 8))
        repo.abrirMes(YearMonth.of(2026, 9))
        // setembro editada à mão: sobrevive à pausa
        val set = linhas().first { it.data == LocalDate.parse("2026-09-15") }
        repo.editar(set.copy(valorCentavos = -99_00), EscopoEdicao.SO_ESTE_MES)

        repo.pausar(templates().single().id, hoje)              // hoje = 2026-07-20

        assertEquals(false, templates().single().ativa)
        assertEquals(listOf("2026-07-15", "2026-09-15"), linhas().map { it.data.toString() })
    }

    @Test
    fun pausarCongelaOsMesesNuncaAbertosAntesDeDesligar() = runBlocking {
        repo.criar(mov("2026-04-15", -50_00), RepetirOpcao.TodoMes(15))
        // maio e junho nunca abertos: hoje só existem como expansão virtual
        repo.pausar(templates().single().id, hoje)              // hoje = 2026-07-20
        val input = repo.ledger.first()
        assertEquals(
            listOf("2026-04-15", "2026-05-15", "2026-06-15", "2026-07-15"),
            input.movimentacoes.map { it.data.toString() }.sorted(),
        )
        assertEquals(true, YearMonth.of(2026, 5) in input.mesesMaterializados)
    }

    @Test
    fun retomarDeixaOIntervaloVazioESemeiaOMesCorrente() = runBlocking {
        repo.criar(mov("2026-05-15", -50_00), RepetirOpcao.TodoMes(15))
        val id = templates().single().id
        repo.pausar(id, LocalDate.parse("2026-05-20"))
        // julho aberto enquanto pausada: nada da recorrência entra
        repo.abrirMes(YearMonth.of(2026, 7))
        assertEquals(listOf("2026-05-15"), linhas().map { it.data.toString() })

        repo.retomar(id, hoje)                                  // hoje = 2026-07-20

        assertEquals(true, templates().single().ativa)
        val input = repo.ledger.first()
        // junho ficou materializado (vazio para esta recorrência) e julho ganhou a instância
        assertEquals(true, YearMonth.of(2026, 6) in input.mesesMaterializados)
        assertEquals(listOf("2026-05-15", "2026-07-15"), linhas().map { it.data.toString() })
        // e a projeção não inventa junho
        val junho = ProjectionEngine.movimentacoesDoMes(input, YearMonth.of(2026, 6))
        assertEquals(0, junho.size)
    }

    /**
     * `retomar` reabre só `mesHoje`, mas um mês DEPOIS dele também pode já estar materializado
     * — totais/ledger navegam pra frente e chamam `abrirMes` — e a pausa apagou a instância não
     * editada de lá. Sem re-semear todos os meses `>= mesHoje`, setembro ficaria vazio para
     * sempre.
     */
    @Test
    fun retomarReseiaTodoMesMaterializadoDepoisDaPausaNaoSoOMesCorrente() = runBlocking {
        repo.criar(mov("2026-07-15", -50_00), RepetirOpcao.TodoMes(15))
        repo.abrirMes(YearMonth.of(2026, 9))                    // setembro aberto; agosto, não
        val id = templates().single().id

        repo.pausar(id, hoje)                                   // hoje = 2026-07-20
        repo.retomar(id, hoje)

        assertEquals(true, templates().single().ativa)
        val setembro = linhas().filter { YearMonth.from(it.data) == YearMonth.of(2026, 9) }
        assertEquals(listOf("2026-09-15"), setembro.map { it.data.toString() })

        // agosto nunca foi aberto: sem linha no banco, mas a expansão virtual continua ativa
        val input = repo.ledger.first()
        assertEquals(false, YearMonth.of(2026, 8) in input.mesesMaterializados)
        val agosto = ProjectionEngine.movimentacoesDoMes(input, YearMonth.of(2026, 8))
        assertEquals(1, agosto.size)
    }

    /**
     * `encerrarRecorrencia` no primeiro mês apaga o template inteiro. Uma instância editada à
     * mão em outro mês não é tocada pelo `deleteInstanciasNaoEditadasAPartirDe` (que só apaga as
     * não editadas) e sobreviveria com `recorrenciaId` apontando para um template que não existe
     * mais, se o template não desligasse todas as instâncias antes de sumir.
     */
    @Test
    fun encerrarApagandoOTemplateNaoDeixaInstanciaEditadaOrfa() = runBlocking {
        repo.criar(mov("2026-07-15", -50_00), RepetirOpcao.TodoMes(15))
        repo.abrirMes(YearMonth.of(2026, 8))
        val agosto = linhas().first { it.data == LocalDate.parse("2026-08-15") }
        repo.editar(agosto.copy(valorCentavos = -99_00), EscopoEdicao.SO_ESTE_MES)

        val julho = linhas().first { it.data == LocalDate.parse("2026-07-15") }
        repo.encerrarRecorrencia(julho, mesDaSerie = YearMonth.of(2026, 7))

        assertEquals(0, templates().size)
        val ago = linhas().first { it.data == LocalDate.parse("2026-08-15") }
        assertEquals(null, ago.recorrenciaId)
        assertEquals(-99_00L, ago.valorCentavos)                // a linha em si sobrevive intacta
    }

    @Test
    fun pausarAntesDoInicioDoTemplateNaoCriaLinhaNemQuebra() = runBlocking {
        // Início em setembro, "hoje" (2026-07-20) ainda não chegou lá.
        repo.criar(mov("2026-09-15", -50_00), RepetirOpcao.TodoMes(15))
        val id = templates().single().id

        repo.pausar(id, hoje)

        assertEquals(false, templates().single().ativa)
        assertEquals(emptyList<String>(), linhas().map { it.data.toString() })
    }

    @Test
    fun retomarPreservaOFimDoTemplate() = runBlocking {
        repo.criar(mov("2026-05-15", -50_00), RepetirOpcao.TodoMes(15))
        val id = templates().single().id
        repo.excluirRecorrencia(id, YearMonth.of(2026, 9), EscopoExclusao.SO_FUTURAS) // fim = agosto

        repo.pausar(id, hoje)                                   // hoje = 2026-07-20
        repo.retomar(id, hoje)

        assertEquals(YearMonth.of(2026, 8), templates().single().fim)
    }

    /**
     * Achado da revisão: numa pausada, a linha do mês corrente é real (`pausar` a congela ANTES
     * de desligar — ver comentário lá). O código antigo de `DAQUI_EM_DIANTE` apagava essa linha
     * (não editada) e contava com o re-semeio para trazê-la de volta — mas `templateNovo.ativa`
     * continua `false`, então `ocorrenciaNoMes` devolve `null` e a linha simplesmente sumia.
     */
    @Test
    fun editarDaquiEmDiantePausadaNaoApagaALinhaDoMesCorrente() = runBlocking {
        repo.criar(mov("2026-07-15", -50_00), RepetirOpcao.TodoMes(15))
        repo.pausar(templates().single().id, hoje)              // hoje = 2026-07-20
        val julho = linhas().single()

        repo.editar(julho.copy(valorCentavos = -99_00), EscopoEdicao.DAQUI_EM_DIANTE)

        val julhoDepois = linhas().single()
        assertEquals(julho.id, julhoDepois.id)                  // a mesma linha, não sumiu
        assertEquals(-99_00L, julhoDepois.valorCentavos)
        val t = templates().single()
        assertEquals(-99_00L, t.valorCentavos)
        assertEquals(false, t.ativa)                            // continua pausada
    }

    /** Mesmo achado, caso não pausado: a linha do mês de início tem de manter o próprio id. */
    @Test
    fun editarDaquiEmDianteMantemOIdDaLinhaDoMesDeInicio() = runBlocking {
        repo.criar(mov("2026-07-15", -50_00), RepetirOpcao.TodoMes(15))
        repo.abrirMes(YearMonth.of(2026, 8))
        val julho = linhas().first { it.data == LocalDate.parse("2026-07-15") }

        repo.editar(julho.copy(valorCentavos = -70_00), EscopoEdicao.DAQUI_EM_DIANTE)

        val julhoDepois = linhas().first { it.data == LocalDate.parse("2026-07-15") }
        assertEquals(julho.id, julhoDepois.id)
        assertEquals(-70_00L, julhoDepois.valorCentavos)
        val agosto = linhas().first { it.data == LocalDate.parse("2026-08-15") }
        assertEquals(-70_00L, agosto.valorCentavos)
    }

    // ---- tags: desfazer e cor (uso-diario-1) ----

    @Test
    fun excluirTagDevolveOSnapshotComOsVinculos() = runBlocking {
        val id = repo.criarTag("mercado", 0xFF112233L)
        val tag = Tag(id = id, nome = "mercado", cor = 0xFF112233L)
        repo.criar(mov("2026-07-10", -80_00).copy(tags = listOf(tag)), RepetirOpcao.Nao)
        repo.criar(mov("2026-07-15", -50_00).copy(tags = listOf(tag)), RepetirOpcao.TodoMes(15))
        val idsEsperados = linhas().map { it.id }.toSet()         // a avulsa e a instância de julho
        val recIdEsperado = templates().single().id

        val snapshot = repo.excluirTag(id)

        assertEquals(tag, snapshot.tag)
        assertEquals(idsEsperados, snapshot.movimentacaoIds.toSet())
        assertEquals(listOf(recIdEsperado), snapshot.recorrenciaIds)
        assertEquals(0, repo.tags.first().size)
        assertEquals(true, linhas().all { it.tags.isEmpty() })
    }

    @Test
    fun restaurarTagTrazAMesmaTagEOsVinculosDeVolta() = runBlocking {
        val id = repo.criarTag("mercado", 0xFF112233L)
        val tag = Tag(id = id, nome = "mercado", cor = 0xFF112233L)
        repo.criar(mov("2026-07-10", -80_00).copy(tags = listOf(tag)), RepetirOpcao.Nao)
        repo.criar(mov("2026-07-15", -50_00).copy(tags = listOf(tag)), RepetirOpcao.TodoMes(15))
        val snapshot = repo.excluirTag(id)

        repo.restaurarTag(snapshot)

        assertEquals(listOf(tag), repo.tags.first())
        assertEquals(true, linhas().all { it.tags == listOf(tag) })
        assertEquals(listOf(tag), templates().single().tags)
    }

    @Test
    fun recolorirTagTrocaSoACor() = runBlocking {
        val id = repo.criarTag("mercado", 0xFF112233L)
        repo.recolorirTag(id, 0xFF445566L)
        assertEquals(Tag(id = id, nome = "mercado", cor = 0xFF445566L), repo.tags.first().single())
    }

    /**
     * Achado da revisão: `restaurarTag` religava com `@Insert` direto do cross — se a linha
     * ligada tivesse sido apagada enquanto a tag estava excluída (o "desfazer" chegou depois de
     * outro delete), a FK estourava e a transação inteira voltava, perdendo até o vínculo da
     * linha que sobreviveu.
     */
    @Test
    fun restaurarTagComUmaLinhaApagadaNoMeioReligaSoAQueSobrou() = runBlocking {
        val id = repo.criarTag("mercado", 0xFF112233L)
        val tag = Tag(id = id, nome = "mercado", cor = 0xFF112233L)
        repo.criar(mov("2026-07-10", -80_00).copy(tags = listOf(tag)), RepetirOpcao.Nao)
        repo.criar(mov("2026-07-11", -30_00).copy(tags = listOf(tag)), RepetirOpcao.Nao)
        val snapshot = repo.excluirTag(id)
        val sobrevivente = linhas().first { it.data == LocalDate.parse("2026-07-11") }
        val apagada = linhas().first { it.data == LocalDate.parse("2026-07-10") }
        repo.excluir(apagada)

        repo.restaurarTag(snapshot)                             // não pode lançar

        assertEquals(listOf(tag), repo.tags.first())
        assertEquals(listOf(tag), linhas().single { it.id == sobrevivente.id }.tags)
        assertEquals(1, linhas().size)                          // a apagada continua apagada
    }
}
