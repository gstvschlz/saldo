package com.scholze.saldo

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.scholze.saldo.domain.SEM_DESCRICAO
import com.scholze.saldo.ui.entry.TAG_TECLADO
import com.scholze.saldo.ui.nav.TAG_ADD
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

    /**
  * Onboarding e o toggle de privacidade — o começo de todo teste daqui.
  *
  * Não há mais troca de vista: a aba `saldos` é o board, e o painel de hoje já vem aberto
  * embaixo da grade. Uma movimentação lançada agora aparece nele sem rolar nada.
  */
    private fun abrirBoardRevelado() {
        rule.onNodeWithText("qual seu saldo hoje?").assertIsDisplayed()
        "100000".forEach { rule.onNodeWithText(it.toString()).performClick() }   // R$ 1.000,00
        rule.onNodeWithText("começar").performClick()
        rule.waitUntil(5_000) {
            rule.onAllNodesWithText("saldo projetado", substring = true).fetchSemanticsNodes().isNotEmpty()
        }

        // revela valores (hero toggle)
        rule.onAllNodesWithText("saldo projetado", substring = true).onFirst().performClick()
    }

    /**
     * Digita o valor no teclado 1g e volta para a sheet.
     *
     * As teclas são procuradas DENTRO do teclado: a sheet fica por cima do board, e o
     * número do dia de hoje na grade é um dígito tão legítimo quanto o da tecla — sem o
     * escopo, o teste passa ou falha conforme o dia do mês em que roda.
     */
    private fun digitarValor(digitos: String) {
        rule.onNodeWithText("0,00").performClick()               // abre o teclado
        digitos.forEach { d ->
            rule.onNode(hasAnyAncestor(hasTestTag(TAG_TECLADO)) and hasText(d.toString()))
                .performClick()
        }
        rule.onNodeWithText("continuar").performClick()
    }

    @Test
    fun adicionaMovimentacaoEEnxergaNoPainelDoDia() {
        abrirBoardRevelado()

        // adiciona
        rule.onNodeWithTag(TAG_ADD).performClick()
        rule.onNodeWithText("opcional").performClick()
        rule.onNode(hasSetTextAction()).performTextInput("mercado")
        digitarValor("18990")
        rule.onNodeWithText("adicionar diário").performClick()

        rule.waitUntil(5_000) { rule.onAllNodesWithText("mercado").fetchSemanticsNodes().isNotEmpty() }
        rule.onAllNodesWithText("−189,90").onFirst().assertIsDisplayed()
    }

    /**
     * A descrição é opcional: só o valor prende o salvar, e a linha sem nome aparece no
     * ledger como [SEM_DESCRICAO] em vez de um espaço vazio ao lado do dinheiro.
     */
    @Test
    fun adicionaSemDescricaoEALinhaApareceNomeada() {
        abrirBoardRevelado()

        rule.onNodeWithTag(TAG_ADD).performClick()
        digitarValor("2500")
        rule.onNodeWithText("adicionar diário").performClick()

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
        abrirBoardRevelado()

        rule.onNodeWithTag(TAG_ADD).performClick()
        rule.onNodeWithText("opcional").performClick()
        rule.onNode(hasSetTextAction()).performTextInput("mer")
        rule.onNode(hasSetTextAction()).performTextInput("cado")
        rule.onNode(hasSetTextAction()).assertTextEquals("mercado")
    }

    /**
     * Editar o valor e, sem sair da sheet, excluir. O "desfazer" tem de trazer a linha COMO ELA ERA
     * no ledger — o formulário aberto não é a verdade, é um rascunho.
     */
    @Test
    fun desfazerDepoisDeEditarEExcluirTrazOValorOriginal() {
        abrirBoardRevelado()

        rule.onNodeWithTag(TAG_ADD).performClick()
        rule.onNodeWithText("opcional").performClick()
        rule.onNode(hasSetTextAction()).performTextInput("mercado")
        digitarValor("8000")                                     // R$ 80,00
        rule.onNodeWithText("adicionar diário").performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithText("mercado").fetchSemanticsNodes().isNotEmpty() }

        // reabre a linha na sheet e mexe SÓ no formulário: um zero a mais faz 80,00 virar 800,00
        rule.onAllNodesWithText("mercado").onFirst().performClick()
        rule.waitUntil(5_000) {
            rule.onAllNodesWithText("editar movimentação").fetchSemanticsNodes().isNotEmpty()
        }
        // O valor da sheet é um bloco clicável só — "R$", o número e a legenda caem no mesmo nó —,
        // e a legenda é o texto dele que não colide com o board, que continua composto atrás.
        rule.onNodeWithText("gasto variável · sai do saldo").performClick()
        rule.onNode(hasAnyAncestor(hasTestTag(TAG_TECLADO)) and hasText("0")).performClick()
        rule.onNodeWithText("continuar").performClick()
        rule.onNodeWithText("800,00").assertIsDisplayed()         // o rascunho está mesmo mexido

        rule.onNodeWithText("excluir movimentação").performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithText("desfazer").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("desfazer").performClick()

        // volta o que estava no ledger (−80,00), não o que estava sendo digitado (−800,00)
        rule.waitUntil(5_000) { rule.onAllNodesWithText("−80,00").fetchSemanticsNodes().isNotEmpty() }
        rule.onAllNodesWithText("−800,00").assertCountEquals(0)
    }

    /** `5`, `0`, `00` = R$ 50,00: a tecla do ponto de venda que a vírgula inerte ocupava. */
    @Test
    fun aTeclaDuploZeroAnexaDoisZeros() {
        abrirBoardRevelado()
        rule.onNodeWithTag(TAG_ADD).performClick()
        rule.onNodeWithText("0,00").performClick()
        listOf("5", "0", "00").forEach { d ->
            rule.onNode(hasAnyAncestor(hasTestTag(TAG_TECLADO)) and hasText(d)).performClick()
        }
        rule.onNodeWithText("R$ 50,00").assertIsDisplayed()
        rule.onAllNodesWithText(",").assertCountEquals(0)
    }
}
