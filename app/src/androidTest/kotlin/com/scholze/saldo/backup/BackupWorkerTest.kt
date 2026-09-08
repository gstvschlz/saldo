package com.scholze.saldo.backup

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import androidx.work.workDataOf
import com.scholze.saldo.EstadoLimpo
import com.scholze.saldo.SaldoApplication
import com.scholze.saldo.data.Importers
import com.scholze.saldo.domain.BackupConfig
import com.scholze.saldo.domain.Cadencia
import com.scholze.saldo.domain.Movimentacao
import com.scholze.saldo.domain.Natureza
import com.scholze.saldo.domain.RepetirOpcao
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * O worker inteiro sobre o container do app, com a pasta trocada por arquivos temporários
 * ([PastaTemporaria]): o que fica no disco, o que a rotação apaga e os três finais.
 */
@RunWith(AndroidJUnit4::class)
class BackupWorkerTest {

    @get:Rule
    val estadoLimpo = EstadoLimpo()

    private val app = ApplicationProvider.getApplicationContext<SaldoApplication>()
    private lateinit var raiz: File
    private lateinit var pasta: PastaTemporaria

    @Before
    fun preparar() {
        WorkManagerTestInitHelper.initializeTestWorkManager(
            app, Configuration.Builder().setExecutor(SynchronousExecutor()).build(),
        )
        raiz = File(app.cacheDir, "backup-${System.nanoTime()}").apply { mkdirs() }
        pasta = PastaTemporaria(raiz)
        app.container.pastaBackup = { pasta }
        runBlocking {
            app.container.settings.definirSaldoInicial(100_000_00, LocalDate.parse("2026-09-01"))
            app.container.settings.definirPastaBackup("content://tree/quintal")
            app.container.settings.definirCadenciaBackup(Cadencia.DIARIO)
            app.container.repository.criar(
                Movimentacao(descricao = "café", valorCentavos = -8_50, data = LocalDate.now(), natureza = Natureza.DIARIO),
                RepetirOpcao.Nao,
            )
        }
    }

    @After
    fun limpar() {
        app.container.pastaBackup = { PastaSaf(app, it) }
        raiz.deleteRecursively()
        WorkManager.getInstance(app).cancelAllWork()
    }

    private fun rodar(manual: Boolean = false): ListenableWorker.Result = runBlocking {
        TestListenableWorkerBuilder<BackupWorker>(app, inputData = workDataOf(BackupWorker.CHAVE_MANUAL to manual))
            .build()
            .doWork()
    }

    private fun arquivos() = raiz.listFiles()?.map { it.name }.orEmpty().sorted()

    private fun pendentes(nome: String) =
        WorkManager.getInstance(app).getWorkInfosForUniqueWork(nome).get().filter { !it.state.isFinished }

    private fun agendarOAutomatico() = app.container.backupScheduler.agendar(
        BackupConfig(pastaUri = "content://tree/quintal", cadencia = Cadencia.DIARIO),
    )

    /** O worker roda numa corrotina: o `SynchronousExecutor` enfileira na hora, mas não espera. */
    private fun aguardar(condicao: () -> Boolean) {
        repeat(30) {
            if (condicao()) return
            Thread.sleep(100)
        }
    }

    private val nomeDeHoje get() = NomeBackup.de(LocalDate.now())

    @Test
    fun gravaOArquivoDoDiaEOParcialSome() {
        assertEquals(ListenableWorker.Result.success(), rodar())
        assertEquals(listOf(nomeDeHoje), arquivos())
        assertFalse(File(raiz, nomeDeHoje + NomeBackup.SUFIXO_PARCIAL).exists())
    }

    /** O que ficou no disco tem de voltar pelo Importers com o conteúdo do aparelho. */
    @Test
    fun oConteudoVoltaPeloImporters() {
        rodar()
        val dump = Importers.json(File(raiz, nomeDeHoje).readText())
        assertEquals(1, dump.movimentacoes.size)
        assertEquals("café", dump.movimentacoes.single().descricao)
        assertEquals(100_000_00L, dump.settings.saldoInicialCentavos)
    }

    @Test
    fun rodarDuasVezesNoMesmoDiaDeixaUmArquivoSo() {
        rodar()
        rodar()
        assertEquals(listOf(nomeDeHoje), arquivos())
    }

    @Test
    fun aRotacaoDeixaSeteEDeixaOsEstranhosEmPaz() {
        // oito arquivos antigos do padrão + dois de outro nome
        (1..8).forEach { File(raiz, "saldo-2026-01-%02d.json".format(it)).writeText("{}") }
        File(raiz, "planilha.xlsx").writeText("x")
        File(raiz, "saldo-antigo.json").writeText("x")

        rodar()

        val nossos = arquivos().filter { NomeBackup.ehDoPadrao(it) }
        assertEquals(7, nossos.size)
        assertTrue(nomeDeHoje in nossos) // o de hoje é o mais recente
        assertFalse("saldo-2026-01-01.json" in nossos) // os dois mais antigos saíram
        assertFalse("saldo-2026-01-02.json" in nossos)
        assertTrue("planilha.xlsx" in arquivos())
        assertTrue("saldo-antigo.json" in arquivos())
    }

    /** O trabalho único só continua vivo porque o próprio worker o reinsere ao terminar bem. */
    @Test
    fun umSucessoReagendaOProximo() {
        assertEquals(ListenableWorker.Result.success(), rodar())
        assertEquals(1, pendentes(BackupScheduler.NOME).size)
    }

    /**
     * O "agora" da tela é uma rodada EXTRA: grava como qualquer outra, mas não pode acrescentar um
     * nó à cadeia do agendado — que é o que o `reagendar` (APPEND_OR_REPLACE) faz quando é chamado
     * de fora dela. Um toque, um nó, e a cadeia nunca encolheria.
     */
    @Test
    fun oAgoraGravaSemMexerNoAgendamento() {
        agendarOAutomatico()
        assertEquals(1, pendentes(BackupScheduler.NOME).size)

        assertEquals(ListenableWorker.Result.success(), rodar(manual = true))

        assertEquals(listOf(nomeDeHoje), arquivos())
        assertEquals(1, pendentes(BackupScheduler.NOME).size)
    }

    /** E o pedido que o botão enfileira vem mesmo marcado — senão o teste de cima não prova nada. */
    @Test
    fun oPedidoDoAgoraVemMarcadoComoManual() {
        agendarOAutomatico()
        app.container.backupScheduler.agora()
        // As duas condições: o arquivo prova que a rodada aconteceu, e o trabalho terminado prova
        // que ela já passou do ponto onde reagendaria — sem isso o "não empilhou" seria só pressa.
        aguardar { File(raiz, nomeDeHoje).exists() && pendentes(BackupScheduler.NOME_AGORA).isEmpty() }

        assertTrue(File(raiz, nomeDeHoje).exists()) // rodou de verdade
        assertEquals(1, pendentes(BackupScheduler.NOME).size)
    }

    @Test
    fun umaFalhaDeEscritaDevolveRetryEDeixaOUltimoSucessoIntacto() {
        rodar() // um sucesso primeiro
        val sucesso = runBlocking { app.container.settings.settings.first().backup.ultimoSucesso }

        pasta.falharAoCriar = true
        assertEquals(ListenableWorker.Result.retry(), rodar())

        val b = runBlocking { app.container.settings.settings.first().backup }
        assertEquals(sucesso, b.ultimoSucesso) // intacto
        assertTrue(b.ultimoErro != null)
        assertEquals("content://tree/quintal", b.pastaUri) // e o backup continua ligado
    }

    /** Permissão revogada: desliga, e não fica tentando para sempre. */
    @Test
    fun securityExceptionDesligaOBackupENaoReagenda() {
        pasta.lancarSecurity = true
        assertEquals(ListenableWorker.Result.success(), rodar())

        val b = runBlocking { app.container.settings.settings.first().backup }
        assertNull(b.pastaUri)
        assertEquals("escolha a pasta de novo", b.ultimoErro)
        assertTrue(pendentes(BackupScheduler.NOME).isEmpty())
    }

    /** Sem pasta configurada o worker não faz nada — e não é um erro. */
    @Test
    fun semPastaOWorkerSaiEmPaz() {
        runBlocking { app.container.settings.definirPastaBackup(null) }
        assertEquals(ListenableWorker.Result.success(), rodar())
        assertEquals(emptyList<String>(), arquivos())
    }
}
