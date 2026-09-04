package com.scholze.saldo

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToKey
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.scholze.saldo.domain.SEM_DESCRICAO
import com.scholze.saldo.ui.nav.TAG_ADD
import java.time.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Fresh-install flow: onboarding -> reveal -> add an entry -> it shows in the ledger. */
@RunWith(AndroidJUnit4::class)
class EntryFlowTest {

    @get:Rule(order = 0)
    val estadoLimpo = EstadoLimpo()

    @get:Rule(order = 1)
    val rule = createAndroidComposeRule<MainActivity>()

    /** Onboarding, o toggle de privacidade e a troca para a lista — o começo de todo teste daqui. */
    private fun abrirLedgerRevelado() {
        rule.onNodeWithText("qual seu saldo hoje?").assertIsDisplayed()
        "100000".forEach { rule.onNodeWithText(it.toString()).performClick() }   // R$ 1.000,00
        rule.onNodeWithText("começar").performClick()
        rule.waitUntil(5_000) {
            rule.onAllNodesWithText("saldo projetado", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        // O app abre no board desde a board-1; este fluxo é sobre o ledger.
        rule.onNodeWithContentDescription("ver como lista").performClick()

        // revela valores (hero toggle)
        rule.onAllNodesWithText("saldo projetado", substring = true).onFirst().performClick()
    }

    /** Digita o valor no teclado 1g e volta para a sheet. */
    private fun digitarValor(digitos: String) {
        rule.onNodeWithText("0,00").performClick()               // abre o teclado
        digitos.forEach { rule.onNodeWithText(it.toString()).performClick() }
        rule.onNodeWithText("continuar").performClick()
    }

    @Test
    fun adicionaMovimentacaoEEnxergaNoLedger() {
        abrirLedgerRevelado()

        // adiciona
        rule.onNodeWithTag(TAG_ADD).performClick()
        rule.onNodeWithText("opcional").performClick()
        rule.onNode(hasSetTextAction()).performTextInput("mercado")
        digitarValor("18990")
        rule.onNodeWithText("adicionar diário").performClick()

        // Enquanto o mês está vazio a lista não emite dia nenhum, só o placeholder; a
        // gravação ter chegado é justamente ele sumir e a grade de dias existir.
        rule.waitUntil(5_000) {
            rule.onAllNodesWithText("toque em + para adicionar").fetchSemanticsNodes().isEmpty()
        }
        // A linha de hoje raramente cabe na primeira dobra (dia 13 já fica fora numa tela
        // de 1080x2400). Rolar pela CHAVE do item — o epochDay do dia — evita depender do
        // dia do mês em que o teste roda.
        rule.onNode(hasScrollToIndexAction()).performScrollToKey(LocalDate.now().toEpochDay())

        rule.waitUntil(5_000) { rule.onAllNodesWithText("mercado").fetchSemanticsNodes().isNotEmpty() }
        rule.onAllNodesWithText("−189,90").onFirst().assertIsDisplayed()
    }

    /**
     * A descrição é opcional: só o valor prende o salvar, e a linha sem nome aparece no
     * ledger como [SEM_DESCRICAO] em vez de um espaço vazio ao lado do dinheiro.
     */
    @Test
    fun adicionaSemDescricaoEALinhaApareceNomeada() {
        abrirLedgerRevelado()

        rule.onNodeWithTag(TAG_ADD).performClick()
        digitarValor("2500")
        rule.onNodeWithText("adicionar diário").performClick()

        rule.waitUntil(5_000) {
            rule.onAllNodesWithText("toque em + para adicionar").fetchSemanticsNodes().isEmpty()
        }
        rule.onNode(hasScrollToIndexAction()).performScrollToKey(LocalDate.now().toEpochDay())

        rule.waitUntil(5_000) { rule.onAllNodesWithText(SEM_DESCRICAO).fetchSemanticsNodes().isNotEmpty() }
        rule.onAllNodesWithText("−25,00").onFirst().assertIsDisplayed()
    }

    /**
     * O campo de descrição abre com o cursor e é dono do próprio texto: dois pedaços
     * digitados em sequência saem na ordem em que foram digitados, e não embaralhados por
     * um `value` que voltou atrasado do ViewModel.
     */
    @Test
    fun descricaoAceitaTextoEmPedacos() {
        abrirLedgerRevelado()

        rule.onNodeWithTag(TAG_ADD).performClick()
        rule.onNodeWithText("opcional").performClick()
        rule.onNode(hasSetTextAction()).performTextInput("mer")
        rule.onNode(hasSetTextAction()).performTextInput("cado")
        rule.onNode(hasSetTextAction()).assertTextEquals("mercado")
    }
}
