package com.scholze.saldo.ui.nav

import com.scholze.saldo.data.db.toAnoMes
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** O codec puro por trás dos extras de Intent (e dos ActionParameters do widget). */
class DestinoTest {
    private val ago = YearMonth.of(2026, 8)

    private fun volta(d: Destino): Destino? {
        val p = d.paraPares().toMap()
        return Destino.de(p[Destino.EXTRA_DESTINO] as String?, p[Destino.EXTRA_ANO_MES] as Int?, p[Destino.EXTRA_DIA] as Int?)
    }

    @Test
    fun saldosComDiaVaiEVolta() = assertEquals(Destino.Saldos(ago, 5), volta(Destino.Saldos(ago, 5)))

    @Test
    fun saldosSemDiaNaoEmiteAChaveDia() {
        val d = Destino.Saldos(ago)
        assertEquals(setOf(Destino.EXTRA_DESTINO, Destino.EXTRA_ANO_MES), d.paraPares().map { it.first }.toSet())
        assertEquals(d, volta(d))
    }

    @Test
    fun novaMovimentacaoVaiEVolta() = assertEquals(Destino.NovaMovimentacao, volta(Destino.NovaMovimentacao))

    @Test
    fun totaisVaiEVolta() = assertEquals(Destino.Totais(ago), volta(Destino.Totais(ago)))

    @Test
    fun lixoViraNulo() {
        assertNull(Destino.de(null, null, null))
        assertNull(Destino.de("x", ago.toAnoMes(), null))
        assertNull(Destino.de(Destino.TIPO_SALDOS, null, null))     // saldos sem mês
        assertNull(Destino.de(Destino.TIPO_TOTAIS, -1, null))       // mês negativo
    }

    @Test
    fun diaForaDaFaixaEIgnorado() =
        assertEquals(Destino.Saldos(ago), Destino.de(Destino.TIPO_SALDOS, ago.toAnoMes(), 40))
}
