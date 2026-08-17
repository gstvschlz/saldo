package com.scholze.saldo.lembretes

import android.app.Notification
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.scholze.saldo.domain.Fatura
import com.scholze.saldo.domain.ItemDia
import com.scholze.saldo.domain.Lembrete
import com.scholze.saldo.domain.Movimentacao
import com.scholze.saldo.domain.Natureza
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/** Título, texto e versão pública de cada lembrete — o que o usuário lê. */
@RunWith(AndroidJUnit4::class)
class NotificacoesTest {
    private val ctx = ApplicationProvider.getApplicationContext<Context>()
    private val hoje = LocalDate.parse("2026-08-20")

    private fun mov(descricao: String, centavos: Long) =
        Movimentacao(descricao = descricao, valorCentavos = centavos, data = hoje, natureza = Natureza.DIARIO, recorrenciaId = 1)

    private fun titulo(n: Notification) = n.extras.getString(Notification.EXTRA_TITLE)
    private fun texto(n: Notification) = n.extras.getString(Notification.EXTRA_TEXT)

    @Test
    fun faturaAmanha() {
        val fatura = Fatura(ciclo = YearMonth.of(2026, 8), vencimento = LocalDate.parse("2026-09-05"), totalCentavos = -812_40, compras = emptyList())
        val (id, n) = Notificacoes.construir(ctx, Lembrete.FaturaAmanha(fatura, "nubank"))
        assertEquals(Notificacoes.ID_FATURA, id)
        assertEquals("fatura do nubank vence amanhã", titulo(n))
        assertEquals("R$ 812,40 · vence 5 de setembro", texto(n))
        assertEquals("fatura do nubank vence amanhã", titulo(n.publicVersion))
        assertNull(texto(n.publicVersion))
        assertEquals(Notification.VISIBILITY_PRIVATE, n.visibility)
    }

    @Test
    fun recorrenciasHojeUmItemVaiNoTitulo() {
        val (id, n) = Notificacoes.construir(ctx, Lembrete.RecorrenciasHoje(hoje, listOf(ItemDia.Mov(mov("aluguel", -2_400_00)))))
        assertEquals(Notificacoes.ID_RECORRENCIAS, id)
        assertEquals("hoje: aluguel −2.400,00", titulo(n))
        assertEquals("movimentações fixas de hoje", titulo(n.publicVersion))
    }

    @Test
    fun recorrenciasHojeVariosItensContamNoTituloEListamNoTexto() {
        val fatura = Fatura(ciclo = YearMonth.of(2026, 7), vencimento = hoje, totalCentavos = -812_40, compras = emptyList())
        val itens = listOf(ItemDia.Mov(mov("aluguel", -2_400_00)), ItemDia.Mov(mov("internet", -129_90)), ItemDia.FaturaDia(fatura, "nubank"))
        val (_, n) = Notificacoes.construir(ctx, Lembrete.RecorrenciasHoje(hoje, itens))
        assertEquals("hoje: 3 movimentações fixas", titulo(n))
        assertEquals("aluguel −2.400,00 · internet −129,90 · fatura nubank −812,40", texto(n))
    }

    @Test
    fun registrarGastos() {
        val (id, n) = Notificacoes.construir(ctx, Lembrete.RegistrarGastos)
        assertEquals(Notificacoes.ID_REGISTRAR, id)
        assertEquals("registrar os gastos de hoje?", titulo(n))
        assertEquals("nada anotado hoje — toque para lançar", texto(n))
    }

    @Test
    fun fechamentoSobrouEFaltou() {
        val sobrou = Notificacoes.construir(ctx, Lembrete.FechamentoMes(YearMonth.of(2026, 7), 312_50, 8_240_00, 7_927_50)).second
        assertEquals("julho fechou: sobrou R$ 312,50", titulo(sobrou))
        assertEquals("entradas R$ 8.240,00 · saídas R$ 7.927,50", texto(sobrou))
        assertEquals("julho fechou", titulo(sobrou.publicVersion))
        val faltou = Notificacoes.construir(ctx, Lembrete.FechamentoMes(YearMonth.of(2026, 7), -61_28, 0, 61_28)).second
        assertEquals("julho fechou: faltou R$ 61,28", titulo(faltou))
    }
}
