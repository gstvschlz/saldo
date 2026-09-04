package com.scholze.saldo.captura

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.scholze.saldo.EstadoLimpo
import com.scholze.saldo.SaldoApplication
import com.scholze.saldo.data.db.DeteccaoEntity
import com.scholze.saldo.domain.Deteccao
import com.scholze.saldo.domain.Movimentacao
import com.scholze.saldo.domain.Natureza
import java.time.LocalDate
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Os dois botões da sugestão, disparados pelo caminho de verdade: o `PendingIntent` que a
 * própria notificação carrega.
 *
 * Não dá para exercitar isto por `adb shell am broadcast`: o receiver é `exported="false"` —
 * ninguém de fora pode lançar dinheiro na sua conta — e o shell é de fora. O `PendingIntent`
 * roda com a identidade do app, que é exatamente como o toque do usuário chega lá.
 */
@RunWith(AndroidJUnit4::class)
class AcoesSugestaoTest {

    @get:Rule val estadoLimpo = EstadoLimpo()

    private val app = InstrumentationRegistry.getInstrumentation()
        .targetContext.applicationContext as SaldoApplication

    private val dao get() = app.container.database.deteccaoDao()

    private suspend fun semear(centavos: Long = 3_290): Deteccao {
        app.container.settings.definirSaldoInicial(100_000, LocalDate.now())
        val agora = System.currentTimeMillis()
        val id = dao.insert(
            DeteccaoEntity(pacote = "com.exemplo.banco", chave = "k", centavos = centavos, emMillis = agora),
        )
        return Deteccao(id = id, pacote = "com.exemplo.banco", chave = "k", centavos = centavos, emMillis = agora)
    }

    private fun disparar(deteccao: Deteccao, titulo: String) {
        val n = NotificacaoSugestao.construir(app, deteccao, "Banco", jaLancado = false)
        n.actions.first { it.title.toString() == titulo }.actionIntent.send()
    }

    /** O receiver responde noutro processo-tempo; esperar por estado é o único jeito honesto. */
    private suspend fun <T : Any> aguardar(o_que: String, bloco: suspend () -> T?): T =
        withTimeout(10_000) {
            while (true) {
                bloco()?.let { return@withTimeout it }
                delay(100)
            }
            @Suppress("UNREACHABLE_CODE") error("esperando $o_que")
        }

    @Test
    fun lancarGravaSaidaDeHojeEResolveADeteccao() = runBlocking {
        val deteccao = semear()
        disparar(deteccao, "lançar")

        val mov: Movimentacao = aguardar("a movimentação") {
            app.container.repository.ledger.first().movimentacoes.firstOrNull()
        }
        assertEquals("Banco", mov.descricao)
        assertEquals(-3_290L, mov.valorCentavos)
        assertEquals(LocalDate.now(), mov.data)
        // Sempre diária: a decisão foi um toque, sem heurística de conta × cartão.
        assertEquals(Natureza.DIARIO, mov.natureza)

        val depois = aguardar("a detecção resolvida") { dao.porId(deteccao.id)?.takeIf { it.resolvida } }
        assertTrue(depois.resolvida)
    }

    @Test
    fun ignorarResolveSemGravarNada() = runBlocking {
        val deteccao = semear(centavos = 4_500)
        disparar(deteccao, "ignorar")

        val depois = aguardar("a detecção resolvida") { dao.porId(deteccao.id)?.takeIf { it.resolvida } }
        assertTrue(depois.resolvida)
        // E nada foi lançado: "ignorar" é uma decisão sobre a sugestão, não sobre o dinheiro.
        assertNull(app.container.repository.ledger.first().movimentacoes.firstOrNull())
    }
}
