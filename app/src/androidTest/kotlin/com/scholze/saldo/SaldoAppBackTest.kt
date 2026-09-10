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
 * O botão Voltar do sistema: sobreposição → aba → saldos → sair.
 *
 * Antes saía do app de qualquer lugar que não tivesse `BackHandler` próprio. As "sobreposições"
 * eram a vista de lista e a da etiqueta; desde 2026-09-10 são a busca e o filtro de etiqueta,
 * que se desenham por cima da mesma grade.
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

    /**
     * O campo de busca abre o teclado; um `pressBack` disparado com o IME ainda escondendo é
     * engolido por ele antes de chegar ao `BackHandler` do Compose — flakiness pura, não um
     * segundo estado de "voltar" de verdade. Fecha o teclado e espera o Compose assentar antes.
     */
    private fun fecharTecladoEVoltar(): Boolean {
        Espresso.closeSoftKeyboard()
        rule.waitForIdle()
        return voltar()
    }

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

    /**
     * A busca é o que se sobrepõe à grade desde que a lista saiu: Voltar a fecha e devolve o mês,
     * sem sair do app. Era o papel que a vista de lista fazia antes.
     */
    @Test
    fun daBuscaOVoltarDevolveAGrade() {
        app()
        ActivityScenario.launch(MainActivity::class.java).use {
            esperarBoard()
            rule.onNodeWithContentDescription("buscar").performClick()
            esperarTexto("descrição, tag ou valor")
            assertEquals(true, fecharTecladoEVoltar())
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
     * Sem a guarda "só conta na aba saldos", o primeiro toque em Voltar só fechava a busca
     * escondida atrás de totais (sem trocar de aba) — precisava de um segundo toque para o
     * board aparecer. Com a guarda, trocar de aba já limpa a sobreposição, e um único toque
     * em Voltar mostra a grade.
     */
    @Test
    fun comBuscaAbertaTocarTotaisEUmVoltarVaiDireitoAoBoard() {
        app()
        ActivityScenario.launch(MainActivity::class.java).use {
            esperarBoard()
            rule.onNodeWithContentDescription("buscar").performClick()
            esperarTexto("descrição, tag ou valor")
            rule.onNodeWithText("totais").performClick()
            esperarTexto("totais")
            assertEquals(true, fecharTecladoEVoltar())
            esperarBoard()
            assertEquals(true, rule.onAllNodesWithTag(TAG_BOARD_GRADE).fetchSemanticsNodes().isNotEmpty())
        }
    }

    /**
     * Tocar na própria aba `saldos` é pedir a home: limpa a busca. Sem isso ela ficaria aberta
     * e escondida, e o Voltar seguinte a fecharia em vez de sair do app.
     */
    @Test
    fun comBuscaAbertaTocarSaldosLimpaEDepoisVoltarSaiDoApp() {
        app()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            esperarBoard()
            rule.onNodeWithContentDescription("buscar").performClick()
            esperarTexto("descrição, tag ou valor")
            rule.onNodeWithText("saldos").performClick()
            esperarBoard()
            fecharTecladoEVoltar()
            rule.waitUntil(5_000) { scenario.state == androidx.lifecycle.Lifecycle.State.DESTROYED }
            assertEquals(androidx.lifecycle.Lifecycle.State.DESTROYED, scenario.state)
        }
    }

    /**
     * A busca escondida não sobrevivia a uma troca de aba que NÃO fosse para "saldos": tocar em
     * "totais" com ela aberta deixava-a viva atrás da outra aba — o Voltar seguinte gastava um
     * toque fechando-a sem nada mudar na tela. Corrigido, dois toques bastam: um para voltar a
     * saldos, outro para sair.
     */
    @Test
    fun comBuscaAbertaTocarTotaisEDoisVoltaresSaiDoApp() {
        app()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            esperarBoard()
            rule.onNodeWithContentDescription("buscar").performClick()
            esperarTexto("descrição, tag ou valor")
            rule.onNodeWithText("totais").performClick()
            esperarTexto("totais")
            assertEquals(true, fecharTecladoEVoltar())
            esperarBoard()
            fecharTecladoEVoltar()
            rule.waitUntil(5_000) { scenario.state == androidx.lifecycle.Lifecycle.State.DESTROYED }
            assertEquals(androidx.lifecycle.Lifecycle.State.DESTROYED, scenario.state)
        }
    }

    /**
     * O filtro de etiqueta é a outra sobreposição: Voltar tira o filtro antes de pensar em sair.
     * A etiqueta é alcançada pela aba `tags`, que é de onde ela passou a levar à grade filtrada.
     */
    @Test
    fun comAGradeFiltradaOVoltarTiraOFiltroAntesDeSair() {
        app()
        runBlocking {
            ApplicationProvider.getApplicationContext<SaldoApplication>()
                .container.repository.criarTag("comida", 0xFFB63C62L)
        }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            esperarBoard()
            rule.onNodeWithText("tags").performClick()
            esperarTexto("comida")
            rule.onNodeWithText("comida").performClick()
            esperarTexto("tag: comida")

            // Primeiro Voltar: tira o filtro, sem sair.
            assertEquals(true, voltar())
            rule.waitUntil(5_000) {
                rule.onAllNodesWithText("tag: comida").fetchSemanticsNodes().isEmpty()
            }
            esperarBoard()

            // Só o segundo fecha o app.
            voltar()
            rule.waitUntil(5_000) { scenario.state == androidx.lifecycle.Lifecycle.State.DESTROYED }
            assertEquals(androidx.lifecycle.Lifecycle.State.DESTROYED, scenario.state)
        }
    }
}
