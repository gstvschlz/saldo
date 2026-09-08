package com.scholze.saldo

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.scholze.saldo.domain.Movimentacao
import com.scholze.saldo.domain.Natureza
import com.scholze.saldo.domain.RepetirOpcao
import com.scholze.saldo.ui.board.TAG_BOARD_GRADE
import com.scholze.saldo.ui.components.TAG_CAMPO_BUSCA
import com.scholze.saldo.ui.ledger.TAG_RESULTADOS
import com.scholze.saldo.ui.ledger.TAG_SALDO_PROJETADO
import com.scholze.saldo.ui.privacy.MASCARA_PRIVACIDADE
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Cold-start flow on a fresh install: onboarding keypad -> ledger masked by default.
 *
 * A reinstall keeps app data, and the test itself writes the saldo inicial, so the
 * fresh state is made rather than assumed: [estadoLimpo] runs before the activity rule
 * launches [MainActivity].
 *
 * O reset é feito pelas instâncias vivas do container, não apagando arquivos — ver o
 * KDoc de [EstadoLimpo]. Apagar `filesDir/datastore` e `saldo.db`, como esta classe
 * fazia, só funciona enquanto ninguém tiver lido: o container do `SaldoApplication`
 * (DataStore singleton + conexão do Room) sobrevive de `@Test` para `@Test` dentro do
 * mesmo processo e continuaria servindo o cache antigo.
 */
@RunWith(AndroidJUnit4::class)
class LedgerScreenTest {

    @get:Rule(order = 0)
    val estadoLimpo = EstadoLimpo()

    @get:Rule(order = 1)
    val rule = createAndroidComposeRule<MainActivity>()

    @Test
    fun onboardingDepoisLedgerOculto() {
        rule.onNodeWithText("qual seu saldo hoje?").assertIsDisplayed()
        rule.onNodeWithText("1").performClick()
        rule.onNodeWithText("0").performClick()
        rule.onNodeWithText("0").performClick()
        rule.onNodeWithText("0").performClick()
        rule.onNodeWithText("0").performClick()   // 1 + quatro zeros => R$ 100,00
        rule.onNodeWithText("começar").performClick()
        rule.waitUntil(5_000) {
            rule.onAllNodesWithText("saldo projetado", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        // começa oculto por padrão. O hero inteiro é clicável, então o nó do valor só
        // existe na árvore não-mesclada.
        rule.onNodeWithTag(TAG_SALDO_PROJETADO, useUnmergedTree = true)
            .assertTextEquals(MASCARA_PRIVACIDADE)
    }

    /** Semeia direto no repositório da activity — é o mesmo objeto que a tela lê. */
    private fun semear(descricao: String, centavos: Long, data: LocalDate) =
        semearCom(descricao, centavos, data, Natureza.DIARIO)

    private fun semearCom(descricao: String, centavos: Long, data: LocalDate, natureza: Natureza) = runBlocking {
        ApplicationProvider.getApplicationContext<SaldoApplication>().container.repository.criar(
            Movimentacao(descricao = descricao, valorCentavos = centavos, data = data, natureza = natureza),
            RepetirOpcao.Nao,
        )
    }

    /** Onboarding (R$ 1.000,00), o board, e o toggle para a lista. */
    private fun abrirLista() {
        rule.onNodeWithText("qual seu saldo hoje?").assertIsDisplayed()
        "100000".forEach { rule.onNodeWithText(it.toString()).performClick() }
        rule.onNodeWithText("começar").performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithTag(TAG_BOARD_GRADE).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithContentDescription("ver como lista").performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithContentDescription("buscar").fetchSemanticsNodes().isNotEmpty() }
    }

    /** Cria uma etiqueta no repositório da activity — é o mesmo objeto que a tela lê. */
    private fun criarTag(nome: String, cor: Long) = runBlocking {
        ApplicationProvider.getApplicationContext<SaldoApplication>().container.repository.criarTag(nome, cor)
    }

    /** Etiqueta a primeira linha por FORA da tela, para exercitar o chip reagindo ao dado. */
    private fun etiquetarPorFora(nomeTag: String) = runBlocking {
        val repo = ApplicationProvider.getApplicationContext<SaldoApplication>().container.repository
        val tagId = repo.criarTag(nomeTag, 0xFFB63C62L)
        repo.definirTags(repo.ledger.first().movimentacoes.first().id, listOf(tagId))
    }

    /**
     * Semeia UMA linha no dia 1 do mês corrente e abre a lista.
     *
     * O dia 1, e não hoje: sob `sem tag` a lista desenha os trinta dias do mês, e a linha de hoje
     * cairia fora da viewport num mês adiantado — um `performClick` num nó fora dela não chega ao
     * `onClick`. No dia 1 a linha é sempre o primeiro item depois do hero e dos chips.
     *
     * O [garantirSaldoInicialAntesDe] vem junto porque o onboarding grava o saldo inicial em HOJE,
     * e `efetivas` corta tudo que é anterior a ele — sem isso a linha do dia 1 nunca apareceria.
     */
    private fun semearNoDia1EAbrir(descricao: String) {
        val dia1 = LocalDate.now().withDayOfMonth(1)
        semear(descricao, -12_00, dia1)
        abrirLista()
        garantirSaldoInicialAntesDe(dia1)
    }

    /**
     * Empurra o saldo inicial para antes de [data]. O onboarding do teclado grava o saldo
     * inicial em hoje; sem isto, uma movimentação semeada num mês passado ficaria fora das
     * "efetivas" (`ProjectionEngine.efetivas` só olha `data >= saldoInicialData`) e a busca
     * nunca a acharia.
     */
    private fun garantirSaldoInicialAntesDe(data: LocalDate) = runBlocking {
        ApplicationProvider.getApplicationContext<SaldoApplication>()
            .container.settings.definirSaldoInicial(100_000_00, data.minusDays(1))
    }

    @Test
    fun aLupaAbreOCampoEOsResultadosVemAgrupadosPorMes() {
        val cincoMesesAtras = LocalDate.now().minusMonths(5).withDayOfMonth(3)
        semear("uber", -23_90, LocalDate.now())
        semear("uber", -31_00, cincoMesesAtras)
        abrirLista()
        garantirSaldoInicialAntesDe(cincoMesesAtras)
        rule.onNodeWithContentDescription("buscar").performClick()
        rule.onNodeWithTag(TAG_CAMPO_BUSCA).performTextInput("uber")
        // Escopado dentro de TAG_RESULTADOS: sem isso, o próprio campo de busca (cujo texto
        // digitado também é "uber") entra na contagem como um terceiro nó.
        rule.waitUntil(5_000) {
            rule.onAllNodes(hasText("uber") and hasAnyAncestor(hasTestTag(TAG_RESULTADOS)))
                .fetchSemanticsNodes().size == 2
        }
        val titulo = YearMonth.from(cincoMesesAtras).format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.forLanguageTag("pt-BR")))
        rule.onNodeWithTag(TAG_RESULTADOS).performScrollToNode(hasText(titulo))
        rule.onNodeWithText(titulo).assertIsDisplayed()
    }

    @Test
    fun buscaSemResultadoDizQueNaoAchou() {
        semear("uber", -23_90, LocalDate.now())
        abrirLista()
        rule.onNodeWithContentDescription("buscar").performClick()
        rule.onNodeWithTag(TAG_CAMPO_BUSCA).performTextInput("zzz")
        // Espera a árvore de resultados (e o "nada com") existirem antes de contar dentro dela —
        // sem isso, a contagem de "uber" podia rodar antes da recomposição do filtro e passar
        // por sorte, não porque a busca de fato não achou nada.
        rule.waitUntil(5_000) { rule.onAllNodesWithTag(TAG_RESULTADOS).fetchSemanticsNodes().isNotEmpty() }
        rule.waitUntil(5_000) { rule.onAllNodesWithText("nada com \"zzz\"").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("nada com \"zzz\"").assertIsDisplayed()
        // Sem isto o teste passaria mesmo se a busca ignorasse o filtro e devolvesse tudo:
        // "nada com" e uma lista cheia por baixo não são contraditórios para o Compose.
        rule.onAllNodes(hasText("uber") and hasAnyAncestor(hasTestTag(TAG_RESULTADOS)))
            .assertCountEquals(0)
    }

    @Test
    fun fecharABuscaVoltaAoMes() {
        abrirLista()
        rule.onNodeWithContentDescription("buscar").performClick()
        rule.onNodeWithContentDescription("fechar busca").performClick()
        rule.onNodeWithText(YearMonth.now().format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.forLanguageTag("pt-BR")))).assertIsDisplayed()
    }

    // ---- a fila de sem tag (arrumacao-1) ----

    /** O chip aparece com a contagem quando há trabalho, e some quando não há. */
    @Test
    fun oChipSemTagTrazAContagemESomeQuandoNaoHaFila() {
        semearNoDia1EAbrir("padaria")
        rule.waitUntil(5_000) {
            rule.onAllNodesWithContentDescription("sem tag, 1 lançamento").fetchSemanticsNodes().isNotEmpty()
        }
        etiquetarPorFora("comida")
        // Sem o filtro selecionado, a fila zerada tira o chip da barra: um chip permanente
        // anunciando uma tarefa é ruído nos meses em que está tudo etiquetado.
        rule.waitUntil(5_000) { rule.onAllNodesWithText("sem tag").fetchSemanticsNodes().isEmpty() }
    }

    /** Selecionado, o chip fica com `0` e a lista explica — a interface não se puxa debaixo do toque. */
    @Test
    fun selecionadoOChipSobreviveAoZerar() {
        semearNoDia1EAbrir("padaria")
        rule.waitUntil(5_000) { rule.onAllNodesWithText("sem tag").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("sem tag").performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithText("padaria").fetchSemanticsNodes().isNotEmpty() }

        etiquetarPorFora("comida")

        rule.waitUntil(5_000) {
            rule.onAllNodesWithText("tudo etiquetado neste mês").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithContentDescription("sem tag, 0 lançamentos").assertIsDisplayed()

        // e sair do filtro tira o chip da barra
        rule.onNodeWithText("todas").performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithText("sem tag").fetchSemanticsNodes().isEmpty() }
    }

    /** Um toque etiqueta, a linha sai da fila, e o "desfazer" do snackbar a traz de volta sem etiqueta. */
    @Test
    fun umToqueEtiquetaEODesfazerTrazDeVolta() {
        criarTag("comida", 0xFFB63C62L)
        semearNoDia1EAbrir("padaria")
        rule.waitUntil(5_000) { rule.onAllNodesWithText("sem tag").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("sem tag").performClick()
        rule.waitUntil(5_000) {
            rule.onAllNodesWithContentDescription("etiquetar como comida").fetchSemanticsNodes().isNotEmpty()
        }

        rule.onNodeWithContentDescription("etiquetar como comida").performClick()
        rule.waitUntil(5_000) {
            rule.onAllNodesWithText("tudo etiquetado neste mês").fetchSemanticsNodes().isNotEmpty()
        }

        rule.onNodeWithText("desfazer").performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithText("padaria").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithContentDescription("etiquetar como comida").assertIsDisplayed()
    }

    /** A fileira só existe sob `sem tag`: em `todas` a linha é só a linha. */
    @Test
    fun aFileiraDeEtiquetasSoApareceSobSemTag() {
        criarTag("comida", 0xFFB63C62L)
        semearNoDia1EAbrir("padaria")
        rule.waitUntil(5_000) { rule.onAllNodesWithText("padaria").fetchSemanticsNodes().isNotEmpty() }
        rule.onAllNodesWithContentDescription("etiquetar como comida").assertCountEquals(0)
    }

    // ---- a meta no hero (arrumacao-1) ----

    /** Batida a meta, a leitura do TalkBack diz que ela bateu — a cor sozinha não diz o número. */
    @Test
    fun aPillDoHeroLeAMetaBatida() {
        val app = ApplicationProvider.getApplicationContext<SaldoApplication>()
        val hoje = LocalDate.now()
        runBlocking { app.container.settings.definirMetaGuardar(20) }
        semearCom("salário", 1_000_00, hoje, Natureza.DIARIO)
        semearCom("reserva", -300_00, hoje, Natureza.ECONOMIA)   // 30% do que entrou
        abrirLista()
        rule.waitUntil(5_000) {
            rule.onAllNodesWithContentDescription("guardou 30%, meta de 20% batida").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithContentDescription("guardou 30%, meta de 20% batida").assertIsDisplayed()
    }

    /** Abaixo da meta, a leitura traz o alvo — e a pill continua a de sempre. */
    @Test
    fun aPillDoHeroLeAMetaNaoBatida() {
        val app = ApplicationProvider.getApplicationContext<SaldoApplication>()
        val hoje = LocalDate.now()
        runBlocking { app.container.settings.definirMetaGuardar(20) }
        semearCom("salário", 1_000_00, hoje, Natureza.DIARIO)
        semearCom("reserva", -100_00, hoje, Natureza.ECONOMIA)   // 10%
        abrirLista()
        rule.waitUntil(5_000) {
            rule.onAllNodesWithContentDescription("guardou 10%, meta 20%").fetchSemanticsNodes().isNotEmpty()
        }
    }
}
