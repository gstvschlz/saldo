package com.scholze.saldo.ui.mais

import android.net.Uri
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
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
import com.scholze.saldo.ui.entry.TAG_TECLADO
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
        backupScheduler = app.container.backupScheduler,
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
                    onEscolherPasta = {},
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

    // ---- apagar dados (dados-1) ----

    /**
     * O botão só habilita com a palavra exata. O nó é pego pela tag, e não pelo texto: depois de
     * "apagar" cair no campo, uma busca por texto casaria com o campo E com o botão.
     */
    @Test
    fun oBotaoApagarSoHabilitaComOTextoCerto() {
        val v = vm { "" }
        runBlocking { app.container.settings.definirSaldoInicial(1_00, LocalDate.now()) }
        tela(v)

        rule.onNodeWithText("apagar dados").performScrollTo().performClick()
        rule.onNodeWithTag(TAG_BOTAO_APAGAR).assertIsNotEnabled()

        rule.onNodeWithTag(TAG_CONFIRMACAO_APAGAR).performTextInput("apaga")
        rule.onNodeWithTag(TAG_BOTAO_APAGAR).assertIsNotEnabled()

        rule.onNodeWithTag(TAG_CONFIRMACAO_APAGAR).performTextInput("r")
        rule.onNodeWithTag(TAG_BOTAO_APAGAR).assertIsEnabled()
    }

    @Test
    fun apagarLevaTudo() {
        val v = vm { "" }
        runBlocking {
            app.container.settings.definirSaldoInicial(1_00, LocalDate.now())
            app.container.repository.criar(
                Movimentacao(
                    descricao = "café", valorCentavos = -8_50,
                    data = LocalDate.now(), natureza = Natureza.DIARIO,
                ),
                RepetirOpcao.Nao,
            )
        }
        tela(v)

        rule.onNodeWithText("apagar dados").performScrollTo().performClick()
        // Aparado e minúsculo: o teclado do Android capitaliza sozinho.
        rule.onNodeWithTag(TAG_CONFIRMACAO_APAGAR).performTextInput("  APAGAR ")
        rule.onNodeWithTag(TAG_BOTAO_APAGAR).assertIsEnabled().performClick()

        rule.waitUntil(5_000) {
            runBlocking { app.container.settings.settings.first().saldoInicialCentavos } == null
        }
        assertEquals(0, runBlocking { app.container.repository.ledger.first().movimentacoes.size })
    }

    // ---- diálogo do saldo inicial (dados-1) ----

    /**
     * A contagem é testada onde ela mora. O `DatePicker` do M3 não é dirigido daqui de propósito:
     * a grade tem vários "15" na tela e o teste seria frágil sem provar nada nosso.
     */
    @Test
    fun anterioresAContaSoAsLinhasReaisAntesDaData() {
        val v = vm { "" }
        runBlocking {
            app.container.settings.definirSaldoInicial(1_00, LocalDate.parse("2026-01-01"))
            listOf("2026-07-01", "2026-07-10", "2026-08-05").forEach { d ->
                app.container.repository.criar(
                    Movimentacao(
                        descricao = "x", valorCentavos = -1_00,
                        data = LocalDate.parse(d), natureza = Natureza.DIARIO,
                    ),
                    RepetirOpcao.Nao,
                )
            }
        }
        // A tela é quem assina o fluxo de movimentações; sem ela a contagem seria sempre zero.
        tela(v)
        rule.waitUntil(5_000) { v.anterioresA(LocalDate.parse("2027-01-01")) == 3 }

        assertEquals(0, v.anterioresA(LocalDate.parse("2026-07-01"))) // a do dia não é anterior
        assertEquals(2, v.anterioresA(LocalDate.parse("2026-08-01")))
        assertEquals(3, v.anterioresA(LocalDate.parse("2026-09-01")))
    }

    @Test
    fun oDialogoDoSaldoInicialMostraQuantosSaemDasContas() {
        val v = vm { "" }
        runBlocking {
            app.container.settings.definirSaldoInicial(1_00, LocalDate.parse("2026-01-01"))
            app.container.repository.criar(
                Movimentacao(
                    descricao = "antiga", valorCentavos = -1_00,
                    data = LocalDate.now().minusDays(3), natureza = Natureza.DIARIO,
                ),
                RepetirOpcao.Nao,
            )
        }
        tela(v)

        rule.onNodeWithText("saldo inicial").performClick() // abre o teclado
        rule.onNode(hasAnyAncestor(hasTestTag(TAG_TECLADO)) and hasText("5")).performClick()
        rule.onNodeWithText("salvar").performClick()

        rule.waitUntil(5_000) {
            rule.onAllNodesWithText("1 lançamento anterior sai das contas").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText("cancelar").performClick()
        // cancelar não grava
        assertEquals(1_00L, runBlocking { app.container.settings.settings.first().saldoInicialCentavos })
    }

    // ---- meta de guardar (arrumacao-1) ----

    @Test
    fun oDialogoDaMetaSalva() {
        val v = vm { "" }
        runBlocking { app.container.settings.definirSaldoInicial(1_00, LocalDate.now()) }
        tela(v)

        rule.waitUntil(5_000) { rule.onAllNodesWithText("20%").fetchSemanticsNodes().isNotEmpty() }
        // Pela tag, e não pelo texto: o diálogo repete o título da linha.
        rule.onNodeWithTag(TAG_LINHA_META).performClick()

        rule.onNodeWithTag(TAG_META_MAIS).performClick()
        rule.onNodeWithTag(TAG_META_MAIS).performClick()
        rule.onNodeWithTag(TAG_META_VALOR).assertTextEquals("22%")
        rule.onNodeWithText("salvar").performClick()

        rule.waitUntil(5_000) {
            runBlocking { app.container.settings.settings.first().metaGuardarPercent } == 22
        }
    }

    /** `0` não é "zero por cento": é "sem meta", e a linha tem de dizer isso. */
    @Test
    fun zeroMostraSemMeta() {
        val v = vm { "" }
        runBlocking {
            app.container.settings.definirSaldoInicial(1_00, LocalDate.now())
            app.container.settings.definirMetaGuardar(0)
        }
        tela(v)
        rule.waitUntil(5_000) { rule.onAllNodesWithText("sem meta").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("sem meta").assertIsDisplayed()
    }

    /** O `−` para em zero: abaixo disso não há meta negativa nenhuma para escolher. */
    @Test
    fun oMenosParaEmZero() {
        val v = vm { "" }
        runBlocking {
            app.container.settings.definirSaldoInicial(1_00, LocalDate.now())
            app.container.settings.definirMetaGuardar(1)
        }
        tela(v)
        rule.waitUntil(5_000) { rule.onAllNodesWithText("1%").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag(TAG_LINHA_META).performClick()
        rule.onNodeWithTag(TAG_META_MENOS).performClick()
        rule.onNodeWithTag(TAG_META_VALOR).assertTextEquals("sem meta")
        rule.onNodeWithTag(TAG_META_MENOS).assertIsNotEnabled()
    }
}
