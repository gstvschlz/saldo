package com.scholze.saldo

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.scholze.saldo.ui.board.TAG_BOARD_GRADE
import com.scholze.saldo.ui.board.tagCelula
import com.scholze.saldo.ui.nav.Destino
import com.scholze.saldo.ui.nav.TAG_ADD
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A aba `saldos` é o board, e só ele: o ledger deixou de ser vista.
 *
 * O que este teste protege é a home — que ela abra na grade, que as setas troquem de mês,
 * que a seta de avançar não passe do mês corrente, e que quem chega por deep link pedindo
 * um dia caia na grade daquele mês em vez de numa lista que não existe mais.
 */
@RunWith(AndroidJUnit4::class)
class VistaSaldosTest {

    @get:Rule(order = 0)
    val estadoLimpo = EstadoLimpo()

    @get:Rule(order = 1)
    val rule = createEmptyComposeRule()

    private val tituloMes =
        DateTimeFormatter.ofPattern("MMMM yyyy", Locale.forLanguageTag("pt-BR"))

    private val mesCorrente: String get() = YearMonth.now().format(tituloMes)
    private val mesPassado: String get() = YearMonth.now().minusMonths(1).format(tituloMes)

    /** Onboarding já feito: sem saldo inicial o teclado toma a tela e não há vista nenhuma. */
    private fun app(): SaldoApplication {
        val app = ApplicationProvider.getApplicationContext<SaldoApplication>()
        runBlocking { app.container.settings.definirSaldoInicial(100_000_00, LocalDate.now()) }
        return app
    }

    private fun esperarBoard() = rule.waitUntil(5_000) {
        rule.onAllNodesWithTag(TAG_BOARD_GRADE).fetchSemanticsNodes().isNotEmpty()
    }

    private fun esperarTitulo(mes: String) = rule.waitUntil(5_000) {
        rule.onAllNodesWithText(mes).fetchSemanticsNodes().isNotEmpty()
    }

    @Test
    fun oAppAbreNoBoardDoMesCorrente() {
        app()
        ActivityScenario.launch(MainActivity::class.java).use {
            esperarBoard()
            rule.onNodeWithText(mesCorrente).assertIsDisplayed()
        }
    }

    @Test
    fun aSetaVoltaUmMes() {
        app()
        ActivityScenario.launch(MainActivity::class.java).use {
            esperarBoard()
            rule.onNodeWithContentDescription("mês anterior").performClick()
            esperarTitulo(mesPassado)
            rule.onNodeWithText(mesPassado).assertIsDisplayed()
        }
    }

    /** O futuro tem tela própria (`totais › a caminho`); a grade não vai até lá. */
    @Test
    fun aSetaDeAvancarNaoPassaDoMesCorrente() {
        app()
        ActivityScenario.launch(MainActivity::class.java).use {
            esperarBoard()
            rule.onNodeWithContentDescription("próximo mês").performClick()
            rule.waitForIdle()
            rule.onNodeWithText(mesCorrente).assertIsDisplayed()
        }
    }

    /** Voltar um mês e avançar de novo tem de terminar onde começou. */
    @Test
    fun voltarEAvancarFechaOCiclo() {
        app()
        ActivityScenario.launch(MainActivity::class.java).use {
            esperarBoard()
            rule.onNodeWithContentDescription("mês anterior").performClick()
            esperarTitulo(mesPassado)
            rule.onNodeWithContentDescription("próximo mês").performClick()
            esperarTitulo(mesCorrente)
            rule.onNodeWithText(mesCorrente).assertIsDisplayed()
        }
    }

    @Test
    fun deepLinkDeUmDiaCaiNoBoardDaqueleMes() {
        val app = app()
        val destino = Destino.Saldos(YearMonth.now(), LocalDate.now().dayOfMonth)
        ActivityScenario.launch<MainActivity>(MainActivity.intent(app, destino)).use {
            esperarBoard()
            rule.onNodeWithTag(TAG_BOARD_GRADE).assertExists()
            rule.onNodeWithText(mesCorrente).assertIsDisplayed()
        }
    }

    /** Sem lançamento nenhum, o painel do dia de hoje diz isso em vez de ficar vazio. */
    @Test
    fun oPainelDeHojeJaVemAberto() {
        app()
        ActivityScenario.launch(MainActivity::class.java).use {
            esperarBoard()
            rule.waitUntil(5_000) {
                rule.onAllNodesWithText("sem movimentações").fetchSemanticsNodes().isNotEmpty()
            }
        }
    }

    /**
     * Com um dia aberto na grade, o `+` lança naquele dia — e a sheet diz a data.
     *
     * A janela do board nunca passa de hoje, e o piso dela é o dia do saldo inicial — o
     * `app()` compartilhado grava esse saldo EM hoje, o que deixaria o dia 1 (e qualquer
     * outro dia antes de hoje) desabilitado, sem como abrir um dia diferente para testar o
     * `+`. Este teste grava o saldo inicial antes do mês corrente, só para si.
     */
    @Test
    fun oMaisLancaNoDiaAbertoDoBoard() {
        // No dia 1 do mês, tocar na célula de hoje FECHA o painel (já vem aberto por
        // padrão) em vez de abri-lo — o teste passaria pela razão errada.
        org.junit.Assume.assumeTrue(LocalDate.now().dayOfMonth > 1)
        val appCtx = ApplicationProvider.getApplicationContext<SaldoApplication>()
        runBlocking {
            appCtx.container.settings.definirSaldoInicial(100_000_00, YearMonth.now().atDay(1).minusDays(1))
        }
        ActivityScenario.launch(MainActivity::class.java).use {
            esperarBoard()
            val dia1 = LocalDate.now().withDayOfMonth(1)
            rule.onNodeWithTag(TAG_BOARD_GRADE).performScrollToNode(hasTestTag(tagCelula(dia1)))
            rule.onNodeWithTag(tagCelula(dia1), useUnmergedTree = true).performClick()
            rule.onNodeWithTag(TAG_ADD).performClick()
            val rotulo = dia1.format(DateTimeFormatter.ofPattern("EEE, d MMM", Locale.forLanguageTag("pt-BR"))).replace(".", "")
            // Duas ocorrências legítimas: a linha "data" e o rodapé "saldo de … ficará em".
            rule.onAllNodesWithText(rotulo, substring = true).onFirst().assertIsDisplayed()
        }
    }
}
