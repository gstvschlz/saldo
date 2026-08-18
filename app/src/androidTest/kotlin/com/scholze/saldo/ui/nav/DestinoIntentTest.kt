package com.scholze.saldo.ui.nav

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * `aplicarEm`/`deIntent` sobre um [Intent] de verdade — `DestinoTest` (JVM) já cobre o codec puro
 * (`de`/`paraPares`) contra um mapa; aqui é a ida e volta pelos extras reais de um Intent do
 * framework, o que `MainActivity.intent` e o widget (`actionStartActivity`) de fato produzem.
 */
@RunWith(AndroidJUnit4::class)
class DestinoIntentTest {
    private val ago = YearMonth.of(2026, 8)

    @Test
    fun saldosVaiEVoltaPeloIntent() {
        val destino = Destino.Saldos(ago, 20)
        assertEquals(destino, Destino.deIntent(destino.aplicarEm(Intent())))
    }

    @Test
    fun totaisVaiEVoltaPeloIntent() {
        val destino = Destino.Totais(ago)
        assertEquals(destino, Destino.deIntent(destino.aplicarEm(Intent())))
    }

    @Test
    fun novaMovimentacaoVaiEVoltaPeloIntent() {
        assertEquals(Destino.NovaMovimentacao(), Destino.deIntent(Destino.NovaMovimentacao().aplicarEm(Intent())))
    }

    @Test
    fun intentSemExtrasENulo() {
        assertNull(Destino.deIntent(Intent()))
    }
}
