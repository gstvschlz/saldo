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
        return Destino.de(
            p[Destino.EXTRA_DESTINO] as String?,
            p[Destino.EXTRA_ANO_MES] as Int?,
            p[Destino.EXTRA_DIA] as Int?,
            p[Destino.EXTRA_SAIDA] as Int?,
            p[Destino.EXTRA_CENTAVOS] as String?,
            p[Destino.EXTRA_DESCRICAO] as String?,
        )
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
    fun novaMovimentacaoVaiEVolta() = assertEquals(Destino.NovaMovimentacao(), volta(Destino.NovaMovimentacao()))

    /** Os dois botões do widget "lançar" — cada lado tem de sobreviver à ida e volta. */
    @Test
    fun novaMovimentacaoLevaOLadoEscolhido() {
        assertEquals(Destino.NovaMovimentacao(saida = true), volta(Destino.NovaMovimentacao(saida = true)))
        assertEquals(Destino.NovaMovimentacao(saida = false), volta(Destino.NovaMovimentacao(saida = false)))
    }

    /** Sem lado escolhido a chave nem é emitida: a sheet decide sozinha, como antes. */
    @Test
    fun novaMovimentacaoSemLadoNaoEmiteAChaveSaida() {
        val d = Destino.NovaMovimentacao()
        assertEquals(listOf(Destino.EXTRA_DESTINO), d.paraPares().map { it.first })
        assertNull((d as Destino.NovaMovimentacao).saida)
    }

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

    /** A sugestão de notificação manda valor e descrição junto para a sheet abrir preenchida. */
    @Test
    fun novaMovimentacaoLevaValorEDescricao() {
        val d = Destino.NovaMovimentacao(saida = true, centavos = 3_290, descricao = "Nubank")
        assertEquals(d, volta(d))
    }

    /** Sem valor nem descrição as chaves nem são emitidas — nada muda para o widget "lançar". */
    @Test
    fun novaMovimentacaoSemValorNaoEmiteAsChavesNovas() {
        val chaves = Destino.NovaMovimentacao(saida = true).paraPares().map { it.first }.toSet()
        assertEquals(setOf(Destino.EXTRA_DESTINO, Destino.EXTRA_SAIDA), chaves)
    }

    /** Extra corrompido não pode virar crash: vira `null` e a sheet decide sozinha. */
    @Test
    fun valorCorrompidoViraNulo() {
        val d = Destino.de(Destino.TIPO_NOVA, null, null, 1, centavos = "não é número", descricao = "  ")
        assertEquals(Destino.NovaMovimentacao(saida = true), d)
    }

    /** Valor zero ou negativo não tem o que pré-preencher. */
    @Test
    fun valorNaoPositivoViraNulo() {
        assertEquals(
            Destino.NovaMovimentacao(saida = true),
            Destino.de(Destino.TIPO_NOVA, null, null, 1, centavos = "0"),
        )
    }
}
