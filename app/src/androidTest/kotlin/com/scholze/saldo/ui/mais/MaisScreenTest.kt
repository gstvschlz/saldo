package com.scholze.saldo.ui.mais

import android.net.Uri
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.scholze.saldo.EstadoLimpo
import com.scholze.saldo.SaldoApplication
import com.scholze.saldo.data.Dump
import com.scholze.saldo.data.Exporters
import com.scholze.saldo.data.LeitorDeArquivo
import com.scholze.saldo.data.Settings
import com.scholze.saldo.data.Tema
import com.scholze.saldo.domain.CartaoConfig
import com.scholze.saldo.domain.Movimentacao
import com.scholze.saldo.domain.Natureza
import com.scholze.saldo.domain.RepetirOpcao
import com.scholze.saldo.domain.Tag
import com.scholze.saldo.ui.theme.SaldoTheme
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * O ciclo fechado, sem seletor de arquivos: o [LeitorDeArquivo] é injetado e devolve um texto, então
 * o teste exercita o caminho de verdade — validar em memória, confirmar, transação, ajustes.
 */
@RunWith(AndroidJUnit4::class)
class MaisScreenTest {

    @get:Rule(order = 0)
    val estadoLimpo = EstadoLimpo()

    @get:Rule(order = 1)
    val rule = createComposeRule()

    private val app = ApplicationProvider.getApplicationContext<SaldoApplication>()

    private val tag = Tag(id = 9, nome = "mercado", cor = 0xFF2E7D32)

    private val doArquivo = Movimentacao(
        id = 77,
        descricao = "do arquivo",
        valorCentavos = -12_34,
        data = LocalDate.parse("2026-08-15"),
        natureza = Natureza.DIARIO,
        tags = listOf(tag),
    )

    private val dump = Dump(
        exportadoEm = "2026-09-07T10:12:00-03:00",
        app = "0.6.0",
        settings = Settings(
            saldoInicialCentavos = 50_000_00, saldoInicialData = LocalDate.parse("2026-07-01"),
            cartao = CartaoConfig(), comecarOculto = true, tema = Tema.SISTEMA,
        ),
        tags = listOf(tag),
        recorrencias = emptyList(),
        movimentacoes = listOf(doArquivo),
        mesesMaterializados = emptySet(),
    )

    private fun vm(leitor: LeitorDeArquivo) = MaisViewModel(
        settingsStore = app.container.settings,
        repository = app.container.repository,
        scheduler = app.container.lembretesScheduler,
        leitor = leitor,
        limparNotificacoes = {},
        limparSugestoes = {},
    )

    private fun tela(vm: MaisViewModel) {
        rule.setContent {
            SaldoTheme {
                MaisScreen(
                    vm = vm,
                    onExportar = {},
                    // No app o Uri vem do seletor do sistema; aqui ele é ignorado pelo leitor falso.
                    onEscolherArquivo = { vm.prepararRestauracao(Uri.EMPTY) },
                )
            }
        }
    }

    /** Um arquivo do schema 1 não é restaurável: mensagem, e o diálogo de confirmar nem aparece. */
    @Test
    fun arquivoSchema1MostraAMensagemENaoAbreODialogo() {
        val antigo = """{"schema":1,"settings":{},"tags":[],"recorrencias":[],"movimentacoes":[]}"""
        val v = vm { antigo }
        runBlocking { app.container.settings.definirSaldoInicial(1_00, LocalDate.now()) }
        tela(v)

        rule.onNodeWithText("restaurar dados").performClick()

        rule.waitUntil(5_000) {
            rule.onAllNodesWithText("este arquivo é de uma versão antiga do saldo; exporte de novo na versão atual")
                .fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText("substituir").assertDoesNotExist()
        assertEquals(1_00L, runBlocking { app.container.settings.settings.first().saldoInicialCentavos })
    }

    /**
     * O que o app exporta volta a entrar: os ajustes viram os do arquivo, e o banco fica com as
     * linhas do arquivo — a que só existia neste aparelho vai embora.
     */
    @Test
    fun umArquivoValidoPedeConfirmacaoESubstituiTudo() {
        val v = vm { Exporters.json(dump) }
        runBlocking {
            app.container.settings.definirSaldoInicial(1_00, LocalDate.now())
            app.container.repository.criar(
                Movimentacao(
                    descricao = "só deste aparelho", valorCentavos = -5_00,
                    data = LocalDate.now(), natureza = Natureza.DIARIO,
                ),
                RepetirOpcao.Nao,
            )
        }
        tela(v)

        rule.onNodeWithText("restaurar dados").performClick()
        rule.waitUntil(5_000) {
            rule.onAllNodesWithText("substituir").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText("substituir").performClick()

        // Os ajustes são gravados DEPOIS do commit do banco: quando o saldo inicial é o do
        // arquivo, a transação já passou e o ledger pode ser lido sem corrida.
        rule.waitUntil(5_000) {
            runBlocking { app.container.settings.settings.first().saldoInicialCentavos } == 50_000_00L
        }
        val ledger = runBlocking { app.container.repository.ledger.first() }
        assertEquals(listOf(77L), ledger.movimentacoes.map { it.id })
        assertEquals(listOf("do arquivo"), ledger.movimentacoes.map { it.descricao })
        assertEquals(listOf("mercado"), runBlocking { app.container.repository.tags.first() }.map { it.nome })
    }

    /** Confirmar é a última chance de desistir — e desistir não escreve nada. */
    @Test
    fun cancelarNaoTocaEmNada() {
        val v = vm { Exporters.json(dump) }
        runBlocking { app.container.settings.definirSaldoInicial(1_00, LocalDate.now()) }
        tela(v)

        rule.onNodeWithText("restaurar dados").performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithText("cancelar").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("cancelar").performClick()

        assertEquals(1_00L, runBlocking { app.container.settings.settings.first().saldoInicialCentavos })
        assertEquals(0, runBlocking { app.container.repository.ledger.first() }.movimentacoes.size)
    }
}
