package com.scholze.saldo

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.scholze.saldo.ui.board.TAG_BOARD_GRADE
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * O botão Voltar do sistema: subtela → aba → saldos → sair. Antes, saía do app de qualquer
 * lugar que não tivesse `BackHandler` próprio — do ledger da tag, de totais, de tags.
 */
@RunWith(AndroidJUnit4::class)
class SaldoAppBackTest {

    @get:Rule(order = 0)
    val estadoLimpo = EstadoLimpo()

    @get:Rule(order = 1)
    val rule = createEmptyComposeRule()

    private fun app() {
        val app = ApplicationProvider.getApplicationContext<SaldoApplication>()
        runBlocking { app.container.settings.definirSaldoInicial(100_000_00, LocalDate.now()) }
    }

    private fun esperarBoard() = rule.waitUntil(5_000) {
        rule.onAllNodesWithTag(TAG_BOARD_GRADE).fetchSemanticsNodes().isNotEmpty()
    }

    private fun esperarTexto(t: String) = rule.waitUntil(5_000) {
        rule.onAllNodesWithText(t, substring = true).fetchSemanticsNodes().isNotEmpty()
    }

    /** Espresso.pressBack lança se a activity fechar; o `runCatching` distingue "saiu" de "ficou". */
    private fun voltar(): Boolean = runCatching { Espresso.pressBack() }.isSuccess

    @Test
    fun deUmaAbaOVoltarVaiParaSaldos() {
        app()
        ActivityScenario.launch(MainActivity::class.java).use {
            esperarBoard()
            rule.onNodeWithText("totais").performClick()
            esperarTexto("totais")
            assertEquals(true, voltar())
            esperarBoard()
            rule.onAllNodesWithTag(TAG_BOARD_GRADE).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun daListaOVoltarVaiParaOBoard() {
        app()
        ActivityScenario.launch(MainActivity::class.java).use {
            esperarBoard()
            rule.onNodeWithContentDescription("ver como lista").performClick()
            esperarTexto("todas")
            assertEquals(true, voltar())
            esperarBoard()
        }
    }

    @Test
    fun deRecorrenciasOVoltarVaiParaTotaisEDepoisParaSaldos() {
        app()
        ActivityScenario.launch(MainActivity::class.java).use {
            esperarBoard()
            rule.onNodeWithText("totais").performClick()
            rule.onNodeWithText("a caminho").performClick()
            rule.onNodeWithText("recorrências", substring = true).performClick()
            esperarTexto("‹ totais")
            assertEquals(true, voltar())
            esperarTexto("a caminho")
            assertEquals(true, voltar())
            esperarBoard()
        }
    }

    @Test
    fun noBoardOVoltarSaiDoApp() {
        app()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            esperarBoard()
            voltar()
            rule.waitUntil(5_000) { scenario.state == androidx.lifecycle.Lifecycle.State.DESTROYED }
            assertEquals(androidx.lifecycle.Lifecycle.State.DESTROYED, scenario.state)
        }
    }

    /**
     * Sem a guarda "só conta na aba saldos", o primeiro toque em Voltar só fechava a vista de
     * lista escondida atrás de totais (sem trocar de aba) — precisava de um segundo toque
     * para o board aparecer. Com a guarda, trocar de aba já devolve a vista para o board, e
     * um único toque em Voltar mostra a grade.
     */
    @Test
    fun daListaTocaTotaisEUmVoltarVaiDireitoAoBoard() {
        app()
        ActivityScenario.launch(MainActivity::class.java).use {
            esperarBoard()
            rule.onNodeWithContentDescription("ver como lista").performClick()
            esperarTexto("todas")
            rule.onNodeWithText("totais").performClick()
            esperarTexto("totais")
            assertEquals(true, voltar())
            esperarBoard()
            assertEquals(true, rule.onAllNodesWithTag(TAG_BOARD_GRADE).fetchSemanticsNodes().isNotEmpty())
        }
    }

    /**
     * Com a busca aberta a barra da lista mostra só o campo e o × — "ver como grade" some da
     * árvore enquanto a busca está aberta, então voltar para saldos só é alcançável pela
     * própria barra de abas. Sair assim tem de fechar a busca também — senão ela fica aberta
     * e escondida, e o próximo Voltar (no board) a fecharia em vez de sair do app.
     */
    @Test
    fun daListaComABuscaAbertaTocarSaldosLimpaEDepoisVoltarSaiDoApp() {
        app()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            esperarBoard()
            rule.onNodeWithContentDescription("ver como lista").performClick()
            esperarTexto("todas")
            rule.onNodeWithContentDescription("buscar").performClick()
            rule.onNodeWithText("saldos").performClick()
            esperarBoard()
            voltar()
            rule.waitUntil(5_000) { scenario.state == androidx.lifecycle.Lifecycle.State.DESTROYED }
            assertEquals(androidx.lifecycle.Lifecycle.State.DESTROYED, scenario.state)
        }
    }

    /**
     * A busca escondida não sobrevivia a uma troca de aba que NÃO fosse para "saldos": tocar em
     * "totais" com a lista e a busca abertas devolvia a vista ao board (bem) mas deixava a
     * busca aberta atrás dele (mal) — o Voltar seguinte gastava um toque fechando-a sem nada
     * mudar na tela, e o board só aparecia no terceiro toque. Corrigido, dois toques bastam.
     */
    @Test
    fun daListaComABuscaAbertaTocarTotaisEDoisVoltaresSaiDoApp() {
        app()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            esperarBoard()
            rule.onNodeWithContentDescription("ver como lista").performClick()
            esperarTexto("todas")
            rule.onNodeWithContentDescription("buscar").performClick()
            rule.onNodeWithText("totais").performClick()
            esperarTexto("totais")
            assertEquals(true, voltar())
            esperarBoard()
            voltar()
            rule.waitUntil(5_000) { scenario.state == androidx.lifecycle.Lifecycle.State.DESTROYED }
            assertEquals(androidx.lifecycle.Lifecycle.State.DESTROYED, scenario.state)
        }
    }
}
