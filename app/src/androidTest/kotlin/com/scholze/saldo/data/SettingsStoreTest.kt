package com.scholze.saldo.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.scholze.saldo.domain.CartaoConfig
import com.scholze.saldo.domain.LembretesConfig
import java.io.File
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

// NOTE: moved from a JVM unit test (app/src/test) to an instrumented test — the JVM
// datastore-core-okio backend throws "multiple instances of DataStore for this file" on the
// *second* sequential edit() against one DataStore instance on Windows, reproduced with both
// datastore 1.2.1 and 1.1.7. The real (Android runtime) FileStorage backend does not have this
// issue, so the test runs here instead.
@RunWith(AndroidJUnit4::class)
class SettingsStoreTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val arquivos = mutableListOf<File>()

    private fun store(): SettingsStore {
        // Caminho decidido uma vez, fora da lambda: `produceFile` tem de devolver sempre o mesmo.
        val arquivo = File(context.cacheDir, "${UUID.randomUUID()}.preferences_pb").also { arquivos += it }
        return SettingsStore(PreferenceDataStoreFactory.create(scope = scope) { arquivo })
    }

    @After
    fun tearDown() {
        scope.cancel()
        arquivos.forEach { it.delete() }
    }

    @Test
    fun defaultsSaoSeguros() = runBlocking {
        val s = store().settings.first()
        assertNull(s.saldoInicialCentavos)
        assertTrue(s.comecarOculto)
        assertEquals(Tema.SISTEMA, s.tema)
        assertEquals(28, s.cartao.fechamentoDia)
        assertFalse(s.widgetMostrarValores)          // widget mascarado por padrão
        assertEquals(LembretesConfig(), s.lembretes)  // tudo desligado, 09:00 / 20:00
    }

    @Test
    fun persisteSaldoInicialECartao() = runBlocking {
        val st = store()
        st.definirSaldoInicial(100_000_00, LocalDate.parse("2026-07-01"))
        st.definirCartao(CartaoConfig(nome = "nubank", fechamentoDia = 27, vencimentoDia = 4))
        st.definirComecarOculto(false)
        st.definirTema(Tema.ESCURO)
        val s = st.settings.first()
        assertEquals(100_000_00L, s.saldoInicialCentavos)
        assertEquals(LocalDate.parse("2026-07-01"), s.saldoInicialData)   // epoch_day ida e volta
        assertEquals("nubank", s.cartao.nome)
        assertEquals(27, s.cartao.fechamentoDia)
        assertEquals(4, s.cartao.vencimentoDia)
        assertEquals(false, s.comecarOculto)
        assertEquals(Tema.ESCURO, s.tema)
    }

    @Test
    fun persisteWidgetELembretes() = runBlocking {
        val st = store()
        val config = LembretesConfig(
            faturaAmanha = true, recorrenciaHoje = false, registrarGastos = true, fechamentoMes = true,
            horaInformativos = LocalTime.of(8, 30), horaNudge = LocalTime.of(21, 15),
        )
        st.definirWidgetMostrarValores(true)
        st.definirLembretes(config)
        val s = st.settings.first()
        assertTrue(s.widgetMostrarValores)
        assertEquals(config, s.lembretes)
    }

    @Test
    fun capturaComecaDesligadaESemApp() = runBlocking {
        val c = store().settings.first().captura
        assertFalse(c.ligada)
        assertTrue(c.marcados.isEmpty())
        assertTrue(c.vistos.isEmpty())
    }

    @Test
    fun ligarEMarcarAppsPersiste() = runBlocking {
        val s = store()
        s.definirCapturaLigada(true)
        s.definirAppMarcado("com.nubank", true)
        s.definirAppMarcado("com.itau", true)
        s.definirAppMarcado("com.itau", false)
        val c = s.settings.first().captura
        assertTrue(c.ligada)
        assertEquals(setOf("com.nubank"), c.marcados)
    }

    /** O listener chama isto para TODA notificação com valor: não pode escrever toda vez. */
    @Test
    fun registrarAppVistoEIdempotente() = runBlocking {
        val s = store()
        s.registrarAppVisto("com.nubank")
        s.registrarAppVisto("com.nubank")
        s.registrarAppVisto("com.picpay")
        assertEquals(setOf("com.nubank", "com.picpay"), s.settings.first().captura.vistos)
    }

    @Test
    fun lerCapturaVeOMesmoQueOFluxo() = runBlocking {
        val s = store()
        s.definirCapturaLigada(true)
        s.definirAppMarcado("com.nubank", true)
        assertEquals(s.settings.first().captura, s.lerCaptura())
    }
}
