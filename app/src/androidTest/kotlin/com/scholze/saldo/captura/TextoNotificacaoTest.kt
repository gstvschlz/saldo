package com.scholze.saldo.captura

import android.app.Notification
import android.os.Bundle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.scholze.saldo.domain.DetectorValor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * O que o listener enxerga de uma notificação.
 *
 * Instrumentado e não JVM porque `Bundle` é do Android: com `isReturnDefaultValues` todo
 * `getCharSequence` devolveria `null` e o teste provaria o contrário do que quer provar.
 */
@RunWith(AndroidJUnit4::class)
class TextoNotificacaoTest {

    private fun extras(vararg pares: Pair<String, Any?>) = Bundle().apply {
        pares.forEach { (chave, valor) ->
            when {
                valor is String -> putCharSequence(chave, valor)
                // O `is Array<*>` não distingue o tipo do elemento (apagamento), então a
                // escolha entre CharSequence[] e Parcelable[] sai do primeiro elemento.
                valor is Array<*> && valor.firstOrNull() is Bundle ->
                    putParcelableArray(chave, valor.filterIsInstance<Bundle>().toTypedArray())
                valor is Array<*> ->
                    putCharSequenceArray(chave, valor.map { it as CharSequence }.toTypedArray())
            }
        }
    }

    @Test
    fun juntaTituloTextoEBigText() {
        val texto = textoDaNotificacao(
            extras(
                Notification.EXTRA_TITLE to "Cartão XP",
                Notification.EXTRA_TEXT to "Compra aprovada",
                Notification.EXTRA_BIG_TEXT to "Compra de R\$ 16,90 em VMT*CAROLINA",
            ),
        )
        assertTrue(texto.contains("Cartão XP"))
        assertTrue(texto.contains("VMT*CAROLINA"))
        assertEquals(1_690L, DetectorValor.primeiroValorEmCentavos(texto))
    }

    /** InboxStyle: o banco lista várias transações e o corpo simples fica genérico. */
    @Test
    fun leAsLinhasDoInboxStyle() {
        val texto = textoDaNotificacao(
            extras(
                Notification.EXTRA_TITLE to "Banco",
                Notification.EXTRA_TEXT to "3 novas transações",
                Notification.EXTRA_TEXT_LINES to arrayOf<CharSequence>(
                    "Compra de R\$ 16,90 em VMT*CAROLINA",
                    "Compra de R\$ 42,00 em PADARIA",
                ),
            ),
        )
        assertTrue(texto.contains("VMT*CAROLINA"))
        assertTrue(texto.contains("PADARIA"))
        // O primeiro valor é o valor, como sempre.
        assertEquals(1_690L, DetectorValor.primeiroValorEmCentavos(texto))
    }

    @Test
    fun leSubTextESummary() {
        val texto = textoDaNotificacao(
            extras(
                Notification.EXTRA_TITLE to "Banco",
                Notification.EXTRA_SUB_TEXT to "R\$ 25,00",
                Notification.EXTRA_SUMMARY_TEXT to "débito",
            ),
        )
        assertEquals(2_500L, DetectorValor.primeiroValorEmCentavos(texto))
        assertTrue(texto.contains("débito"))
    }

    @Test
    fun leAsMensagensDoMessagingStyle() {
        val mensagem = Bundle().apply { putCharSequence("text", "seu pix de R\$ 30,00 caiu") }
        val texto = textoDaNotificacao(
            extras(
                Notification.EXTRA_TITLE to "Banco",
                Notification.EXTRA_MESSAGES to arrayOf(mensagem),
            ),
        )
        assertEquals(3_000L, DetectorValor.primeiroValorEmCentavos(texto))
    }

    /** Repetir o corpo no texto grande é a regra; o valor não pode contar duas vezes. */
    @Test
    fun naoRepeteAMesmaParte() {
        val texto = textoDaNotificacao(
            extras(
                Notification.EXTRA_TEXT to "Compra de R\$ 16,90",
                Notification.EXTRA_BIG_TEXT to "Compra de R\$ 16,90",
            ),
        )
        assertEquals("Compra de R\$ 16,90", texto)
    }

    /** Sem separador, o fim de um campo colaria no começo do outro e inventaria número. */
    @Test
    fun separaOsCamposParaNaoInventarNumero() {
        val texto = textoDaNotificacao(
            extras(
                Notification.EXTRA_TITLE to "16",
                Notification.EXTRA_TEXT to "90 reais",
            ),
        )
        assertTrue("os campos têm de vir separados", texto.contains(" · "))
    }

    @Test
    fun semExtrasNaoQuebra() {
        assertEquals("", textoDaNotificacao(null))
        assertEquals("", textoDaNotificacao(Bundle()))
    }
}
