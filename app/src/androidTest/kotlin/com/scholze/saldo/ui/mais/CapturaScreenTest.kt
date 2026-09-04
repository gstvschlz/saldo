package com.scholze.saldo.ui.mais

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.scholze.saldo.domain.CapturaConfig
import com.scholze.saldo.ui.theme.SaldoTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A tela `mais › notificações`, montada sem o estado do sistema.
 *
 * O acesso entra como parâmetro de propósito: lê-lo do sistema aqui tornaria o resultado
 * dependente de como o emulador está configurado no momento — e foi exatamente o que
 * quebrou estes testes depois de uma verificação manual que concedeu o acesso.
 */
@RunWith(AndroidJUnit4::class)
class CapturaScreenTest {

    @get:Rule val rule = createComposeRule()

    private var marcou: Pair<String, Boolean>? = null

    private fun montar(config: CapturaConfig, acesso: Boolean = false) {
        rule.setContent {
            SaldoTheme {
                CapturaConteudo(
                    config = config,
                    acesso = acesso,
                    onLigar = {},
                    onMarcarApp = { p, m -> marcou = p to m },
                    onAbrirAcesso = {},
                    onVoltar = {},
                )
            }
        }
    }

    @Test
    fun semAppNenhumExplicaComoUmAppAparece() {
        montar(CapturaConfig())
        rule.onNodeWithTag(TAG_CAPTURA_VAZIO).assertIsDisplayed()
    }

    /** Um switch que não faria nada é pior que um desabilitado. */
    @Test
    fun semAcessoDoSistemaOInterruptorFicaDesabilitado() {
        montar(CapturaConfig())
        // Sem apps na lista o mestre é o único switch da tela.
        rule.onAllNodes(isToggleable()).onFirst().assertIsNotEnabled()
    }

    @Test
    fun semAcessoALinhaConvidaAAbrirOSistema() {
        montar(CapturaConfig())
        rule.onNodeWithTag(TAG_CAPTURA_ACESSO).assertIsDisplayed()
        rule.onNodeWithText("abrir configurações do sistema").assertIsDisplayed()
    }

    @Test
    fun aTelaDizAVerdadeSobreOQueOAndroidEntrega() {
        montar(CapturaConfig())
        rule.onNodeWithText("o android entrega ao saldo o texto de toda notificação", substring = true)
            .assertIsDisplayed()
        rule.onNodeWithText("não guarda o texto de nenhuma delas", substring = true).assertIsDisplayed()
    }

    @Test
    fun appVistoAparaceNaLista() {
        montar(CapturaConfig(ligada = true, vistos = setOf("com.exemplo.banco")))
        // Sem o app instalado o rótulo cai no próprio nome do pacote — que é também o que
        // acontece com um app desinstalado depois de marcado.
        rule.onNodeWithText("com.exemplo.banco").assertIsDisplayed()
    }

    @Test
    fun marcarUmAppAvisaComOPacoteEOEstado() {
        marcou = null
        montar(CapturaConfig(ligada = true, vistos = setOf("com.exemplo.banco")))
        // Dois switches na tela: o mestre (índice 0) e o do app.
        rule.onAllNodes(isToggleable())[1].performClick()
        assertEquals("com.exemplo.banco" to true, marcou)
    }

    @Test
    fun comAcessoOInterruptorFicaHabilitado() {
        montar(CapturaConfig(), acesso = true)
        rule.onAllNodes(isToggleable()).onFirst().assertIsEnabled()
    }

    @Test
    fun comAcessoALinhaDizQueEstaLigado() {
        montar(CapturaConfig(), acesso = true)
        rule.onNodeWithText("ligado").assertIsDisplayed()
    }
}
