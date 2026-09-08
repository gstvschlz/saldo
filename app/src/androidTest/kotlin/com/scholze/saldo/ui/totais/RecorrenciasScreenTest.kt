package com.scholze.saldo.ui.totais

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.scholze.saldo.EstadoLimpo
import com.scholze.saldo.MainActivity
import com.scholze.saldo.SaldoApplication
import com.scholze.saldo.domain.Movimentacao
import com.scholze.saldo.domain.Natureza
import com.scholze.saldo.domain.RepetirOpcao
import com.scholze.saldo.ui.nav.Destino
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * totais › a caminho › recorrências, pelo container real: o template aparece e tocá-lo abre a
 * ocorrência DO MÊS VISTO no editor (materializando o mês, se preciso).
 */
@RunWith(AndroidJUnit4::class)
class RecorrenciasScreenTest {

    @get:Rule(order = 0)
    val estadoLimpo = EstadoLimpo()

    @get:Rule(order = 1)
    val rule = createEmptyComposeRule()

    @Test
    fun listaAsFixasEAbreAOcorrenciaDoMes() {
        val app = ApplicationProvider.getApplicationContext<SaldoApplication>()
        val hoje = LocalDate.now()
        runBlocking {
            app.container.settings.definirSaldoInicial(100_000_00, hoje)
            // Dia 28 existe em todo mês — o teste roda em qualquer data.
            app.container.repository.criar(
                Movimentacao(
                    descricao = "aluguel", valorCentavos = -2_400_00,
                    data = hoje.withDayOfMonth(28), natureza = Natureza.DIARIO,
                ),
                RepetirOpcao.TodoMes(28),
            )
        }

        ActivityScenario.launch<MainActivity>(MainActivity.intent(app, Destino.Totais(YearMonth.from(hoje)))).use {
            rule.waitUntil(5_000) { rule.onAllNodesWithText("a caminho").fetchSemanticsNodes().isNotEmpty() }
            rule.onNodeWithText("a caminho").performClick()
            rule.onNodeWithText("recorrências").performScrollTo().performClick()

            rule.waitUntil(5_000) { rule.onAllNodesWithText("dia 28").fetchSemanticsNodes().isNotEmpty() }
            rule.onNodeWithText("aluguel").assertIsDisplayed()
            rule.onNodeWithText("1 fixa").assertIsDisplayed()

            rule.onNodeWithText("aluguel").performClick()
            rule.waitUntil(5_000) { rule.onAllNodesWithText("editar movimentação").fetchSemanticsNodes().isNotEmpty() }
            rule.onNodeWithText("editar movimentação").assertIsDisplayed()
        }
    }

    @Test
    fun voltaParaTotais() {
        val app = ApplicationProvider.getApplicationContext<SaldoApplication>()
        runBlocking { app.container.settings.definirSaldoInicial(100_000_00, LocalDate.now()) }

        ActivityScenario.launch<MainActivity>(MainActivity.intent(app, Destino.Totais(YearMonth.now()))).use {
            rule.waitUntil(5_000) { rule.onAllNodesWithText("a caminho").fetchSemanticsNodes().isNotEmpty() }
            rule.onNodeWithText("a caminho").performClick()
            rule.onNodeWithText("recorrências").performScrollTo().performClick()
            rule.waitUntil(5_000) { rule.onAllNodesWithText("nenhuma recorrência").fetchSemanticsNodes().isNotEmpty() }
            rule.onNodeWithText("‹ totais").performClick()
            // O chip "a caminho" é o marco de volta: o cabeçalho de seção "A CAMINHO" que este
            // teste esperava no plano original não existe mais (ver Task 6 — em caixa baixa ele
            // duplicaria o texto do chip e quebraria o performClick que troca de segmento).
            rule.waitUntil(5_000) { rule.onAllNodesWithText("a caminho").fetchSemanticsNodes().isNotEmpty() }
            rule.onNodeWithText("a caminho").assertIsDisplayed()
        }
    }

    @Test
    fun oInterruptorPausaEALinhaDizPausada() {
        val app = ApplicationProvider.getApplicationContext<SaldoApplication>()
        val hoje = LocalDate.now()
        runBlocking {
            app.container.settings.definirSaldoInicial(100_000_00, hoje)
            app.container.repository.criar(
                Movimentacao(descricao = "academia", valorCentavos = -120_00, data = hoje.withDayOfMonth(5), natureza = Natureza.DIARIO),
                RepetirOpcao.TodoMes(5),
            )
        }
        ActivityScenario.launch<MainActivity>(MainActivity.intent(app, Destino.Totais(YearMonth.from(hoje)))).use {
            rule.waitUntil(5_000) { rule.onAllNodesWithText("a caminho").fetchSemanticsNodes().isNotEmpty() }
            rule.onNodeWithText("a caminho").performClick()
            rule.onNodeWithText("recorrências").performScrollTo().performClick()
            rule.waitUntil(5_000) { rule.onAllNodesWithContentDescription("pausar academia").fetchSemanticsNodes().isNotEmpty() }

            rule.onNodeWithContentDescription("pausar academia").performClick()
            rule.waitUntil(5_000) { rule.onAllNodesWithText("pausada").fetchSemanticsNodes().isNotEmpty() }
            rule.onNodeWithContentDescription("retomar academia").assertIsDisplayed()
            // e continua na lista, não em "encerradas"
            rule.onAllNodesWithText("encerradas", substring = true).assertCountEquals(0)
        }
    }

    /**
     * Achado da revisão: `fixas(r.ativas.size)` contava a pausada também — `ativas` inclui
     * templates pausados (ver `InsightsEngine.recorrencias`), então o cabeçalho continuava
     * dizendo "2 fixas" com uma delas parada.
     */
    @Test
    fun umaPausadaNaoContaComoFixaNoCabecalho() {
        val app = ApplicationProvider.getApplicationContext<SaldoApplication>()
        val hoje = LocalDate.now()
        runBlocking {
            app.container.settings.definirSaldoInicial(100_000_00, hoje)
            app.container.repository.criar(
                Movimentacao(descricao = "academia", valorCentavos = -120_00, data = hoje.withDayOfMonth(5), natureza = Natureza.DIARIO),
                RepetirOpcao.TodoMes(5),
            )
            app.container.repository.criar(
                Movimentacao(descricao = "aluguel", valorCentavos = -2_400_00, data = hoje.withDayOfMonth(28), natureza = Natureza.DIARIO),
                RepetirOpcao.TodoMes(28),
            )
        }
        ActivityScenario.launch<MainActivity>(MainActivity.intent(app, Destino.Totais(YearMonth.from(hoje)))).use {
            rule.waitUntil(5_000) { rule.onAllNodesWithText("a caminho").fetchSemanticsNodes().isNotEmpty() }
            rule.onNodeWithText("a caminho").performClick()
            rule.onNodeWithText("recorrências").performScrollTo().performClick()
            rule.waitUntil(5_000) { rule.onAllNodesWithContentDescription("pausar academia").fetchSemanticsNodes().isNotEmpty() }
            rule.onNodeWithText("2 fixas").assertIsDisplayed()

            rule.onNodeWithContentDescription("pausar academia").performClick()
            rule.waitUntil(5_000) { rule.onAllNodesWithText("pausada").fetchSemanticsNodes().isNotEmpty() }
            rule.onNodeWithText("1 fixa").assertIsDisplayed()
        }
    }

    // ---- parece assinatura (arrumacao-1) ----

    /**
     * Uma avulsa por mês, nos meses fechados mais recentes, no dia 10 (existe em todo mês).
     *
     * [valores] vai do mês mais ANTIGO para o mais novo — o último é a ocorrência mais recente, a
     * que o motor usa como "o preço de hoje". A sequência termina no mês anterior ao corrente, que
     * é o mais tarde que o motor aceita sem exigir que a cobrança deste mês já tenha caído.
     */
    private fun semearAssinatura(
        app: SaldoApplication,
        hoje: LocalDate,
        valores: List<Long> = listOf(-39_90, -39_90, -39_90),
    ) = runBlocking {
        app.container.settings.definirSaldoInicial(100_000_00, hoje.minusMonths(6))
        valores.forEachIndexed { i, valor ->
            app.container.repository.criar(
                Movimentacao(
                    descricao = "netflix", valorCentavos = valor,
                    data = hoje.minusMonths((valores.size - i).toLong()).withDayOfMonth(10),
                    natureza = Natureza.DIARIO,
                ),
                RepetirOpcao.Nao,
            )
        }
    }

    private fun abrirRecorrencias() {
        rule.waitUntil(5_000) { rule.onAllNodesWithText("a caminho").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("a caminho").performClick()
        rule.onNodeWithText("recorrências").performScrollTo().performClick()
    }

    /** A candidata aparece com a contagem de meses, e "tornar mensal" a cadastra de verdade. */
    @Test
    fun aCandidataApareceETornarMensalACadastra() {
        val app = ApplicationProvider.getApplicationContext<SaldoApplication>()
        val hoje = LocalDate.now()
        semearAssinatura(app, hoje)

        ActivityScenario.launch<MainActivity>(MainActivity.intent(app, Destino.Totais(YearMonth.from(hoje)))).use {
            abrirRecorrencias()
            rule.waitUntil(5_000) { rule.onAllNodesWithText("parece assinatura").fetchSemanticsNodes().isNotEmpty() }
            rule.onNodeWithText("3 meses · dia 10").assertIsDisplayed()

            rule.onNodeWithContentDescription("tornar mensal netflix").performClick()
            // A linha mais recente ganha `recorrenciaId` e deixa de ser avulsa; as duas que sobram
            // param dois meses atrás e não chegam mais até agora — a seção inteira some.
            rule.waitUntil(5_000) { rule.onAllNodesWithText("parece assinatura").fetchSemanticsNodes().isEmpty() }
            rule.onNodeWithText("1 fixa").assertIsDisplayed()
            // E a fixa nasceu no dia da mediana, não no dia em que a sheet teria perguntado.
            rule.onNodeWithText("dia 10").assertIsDisplayed()
        }
    }

    /** Dispensar é para sempre: nem uma recomposição nem sair e voltar a trazem de volta. */
    @Test
    fun dispensarTiraACandidataENaoVolta() {
        val app = ApplicationProvider.getApplicationContext<SaldoApplication>()
        val hoje = LocalDate.now()
        semearAssinatura(app, hoje)

        ActivityScenario.launch<MainActivity>(MainActivity.intent(app, Destino.Totais(YearMonth.from(hoje)))).use {
            abrirRecorrencias()
            rule.waitUntil(5_000) { rule.onAllNodesWithText("parece assinatura").fetchSemanticsNodes().isNotEmpty() }

            rule.onNodeWithContentDescription("dispensar netflix").performClick()
            rule.waitUntil(5_000) { rule.onAllNodesWithText("parece assinatura").fetchSemanticsNodes().isEmpty() }

            rule.onNodeWithText("‹ totais").performClick()
            abrirRecorrencias()
            // Nada foi cadastrado — e nada volta a ser sugerido.
            rule.waitUntil(5_000) { rule.onAllNodesWithText("nenhuma recorrência").fetchSemanticsNodes().isNotEmpty() }
            rule.onAllNodesWithText("parece assinatura").assertCountEquals(0)
        }
    }

    /** Sem candidata, nenhum bloco: a seção não pode ocupar espaço para dizer que não achou nada. */
    @Test
    fun semCandidataNaoHaBloco() {
        val app = ApplicationProvider.getApplicationContext<SaldoApplication>()
        val hoje = LocalDate.now()
        runBlocking {
            app.container.settings.definirSaldoInicial(100_000_00, hoje.minusMonths(6))
            app.container.repository.criar(
                Movimentacao(
                    descricao = "netflix", valorCentavos = -39_90,
                    data = hoje.withDayOfMonth(10), natureza = Natureza.DIARIO,
                ),
                RepetirOpcao.Nao,
            )
        }
        ActivityScenario.launch<MainActivity>(MainActivity.intent(app, Destino.Totais(YearMonth.from(hoje)))).use {
            abrirRecorrencias()
            rule.waitUntil(5_000) { rule.onAllNodesWithText("nenhuma recorrência").fetchSemanticsNodes().isNotEmpty() }
            rule.onAllNodesWithText("parece assinatura").assertCountEquals(0)
        }
    }

    /**
     * O reajuste na segunda linha, com os dois valores em módulo e com símbolo.
     *
     * Sem máscara de propósito: a segunda linha É os dois números, e escondidos ela leria
     * "subiu de R$ ••••• para R$ •••••" — o teste não provaria nada.
     */
    @Test
    fun oReajusteApareceNaSegundaLinha() {
        val app = ApplicationProvider.getApplicationContext<SaldoApplication>()
        val hoje = LocalDate.now()
        runBlocking { app.container.settings.definirComecarOculto(false) }
        // 39,90 → 42,90 é +7,5%: reajuste de verdade (acima de 1%) e ainda dentro dos ±10% da
        // mediana que o motor exige. O "44,90" da copy do spec seria +12,5% e, com mediana 39,90,
        // derrubaria a candidata inteira antes de chegar à tela.
        semearAssinatura(app, hoje, listOf(-39_90, -39_90, -42_90))

        ActivityScenario.launch<MainActivity>(MainActivity.intent(app, Destino.Totais(YearMonth.from(hoje)))).use {
            abrirRecorrencias()
            rule.waitUntil(5_000) { rule.onAllNodesWithText("parece assinatura").fetchSemanticsNodes().isNotEmpty() }

            rule.onNodeWithText("subiu de").assertIsDisplayed()
            rule.onNodeWithText("R$ 39,90").assertIsDisplayed()
            rule.onNodeWithText("R$ 42,90").assertIsDisplayed()
            // O valor da linha é o de hoje, assinado e sem símbolo, como em toda linha do app.
            rule.onNodeWithText("−42,90").assertIsDisplayed()
        }
    }
}
