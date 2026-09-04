package com.scholze.saldo.captura

import android.app.Notification
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.scholze.saldo.domain.Deteccao
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** O que a sugestão diz e o que ela oferece, sem depender do que o sistema exibe. */
@RunWith(AndroidJUnit4::class)
class NotificacaoSugestaoTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    private val deteccao = Deteccao(
        id = 42, pacote = "com.exemplo.banco", chave = "k",
        centavos = 3_290, emMillis = 1_788_000_000_000,
    )

    private fun titulo(n: Notification) = n.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
    private fun texto(n: Notification) = n.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()

    @Test
    fun mostraORotuloEOValor() {
        val n = NotificacaoSugestao.construir(context, deteccao, "Banco", jaLancado = false)
        assertEquals("Banco · R$ 32,90", titulo(n))
        assertEquals("lançar como saída de hoje?", texto(n))
    }

    @Test
    fun quandoJaLancadoAPerguntaMuda() {
        val n = NotificacaoSugestao.construir(context, deteccao, "Banco", jaLancado = true)
        assertEquals("já lançado hoje · lançar mesmo assim?", texto(n))
    }

    @Test
    fun tresOpcoesLancarIgnorarEAbrir() {
        val n = NotificacaoSugestao.construir(context, deteccao, "Banco", jaLancado = false)
        assertEquals(listOf("lançar", "ignorar"), n.actions.orEmpty().map { it.title.toString() })
        assertTrue("o corpo tem de abrir a sheet", n.contentIntent != null)
    }

    /**
     * O valor não vai para a tela de bloqueio. É mais fechado que os lembretes de propósito:
     * ali o app fala de dinheiro que já é seu, aqui de um dado recém-chegado de outro app.
     */
    @Test
    fun aVersaoPublicaNaoLevaValor() {
        val n = NotificacaoSugestao.construir(context, deteccao, "Banco", jaLancado = false)
        val publica = n.publicVersion
        assertEquals("sugestão de lançamento", titulo(publica!!))
        assertTrue("nada de valor na tela bloqueada", titulo(publica)?.contains("32,90") != true)
    }

    /** Uma repetida renotifica no mesmo id e substitui; detecções diferentes não colidem. */
    @Test
    fun oIdSaiDaDeteccao() {
        assertEquals(NotificacaoSugestao.idDe(42), NotificacaoSugestao.idDe(42))
        assertNotEquals(NotificacaoSugestao.idDe(42), NotificacaoSugestao.idDe(43))
    }

    /** A faixa dos lembretes (1001–1004) não pode ser invadida. */
    @Test
    fun oIdNaoColideComOsLembretes() {
        listOf(0L, 1L, 99_999L, 100_000L, 1_234_567L).forEach {
            assertTrue("id ${NotificacaoSugestao.idDe(it)} colide", NotificacaoSugestao.idDe(it) >= 2000)
        }
    }
}
