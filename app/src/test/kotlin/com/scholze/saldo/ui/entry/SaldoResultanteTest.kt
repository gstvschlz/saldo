package com.scholze.saldo.ui.entry

import com.scholze.saldo.domain.CartaoConfig
import com.scholze.saldo.domain.Natureza
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class SaldoResultanteTest {

    private val hoje = LocalDate.parse("2026-07-20")
    private val saldoDoDia = 1_000_00L
    /** Fecha dia 28, vence dia 5 do mês seguinte: uma compra de julho pesa em 5 de agosto. */
    private val cartao = CartaoConfig(fechamentoDia = 28, vencimentoDia = 5)

    /** Compra no cartão pesa na fatura, num dia futuro — o saldo de hoje não se mexe. */
    @Test
    fun cartaoNovoNaoMexeNoSaldoDoDia() {
        assertEquals(
            1_000_00L,
            saldoResultante(
                saldoDoDia = saldoDoDia,
                natureza = Natureza.CARTAO,
                valorAssinado = -200_00,
                naturezaOriginal = null,
                valorOriginal = 0,
                dataForm = hoje,
                dataOriginal = null,
                cartao = cartao,
            ),
        )
    }

    @Test
    fun diarioNovoEntraNoSaldoDoDia() {
        assertEquals(
            810_10L,
            saldoResultante(
                saldoDoDia = saldoDoDia,
                natureza = Natureza.DIARIO,
                valorAssinado = -189_90,
                naturezaOriginal = null,
                valorOriginal = 0,
                dataForm = hoje,
                dataOriginal = null,
                cartao = cartao,
            ),
        )
    }

    /** Editar o valor na mesma data: o saldo do dia já conta o valor antigo, então só o delta. */
    @Test
    fun editarValorNaMesmaDataAplicaSoODelta() {
        assertEquals(
            950_00L,
            saldoResultante(
                saldoDoDia = saldoDoDia,
                natureza = Natureza.DIARIO,
                valorAssinado = -150_00,
                naturezaOriginal = Natureza.DIARIO,
                valorOriginal = -100_00,
                dataForm = hoje,
                dataOriginal = hoje,
                cartao = cartao,
            ),
        )
    }

    /** Adiar a movimentação: a data nova ainda vem depois da original, que já foi contada. */
    @Test
    fun mostrarDataPosteriorAindaDescontaOValorAntigo() {
        assertEquals(
            1_000_00L,
            saldoResultante(
                saldoDoDia = saldoDoDia,
                natureza = Natureza.DIARIO,
                valorAssinado = -100_00,
                naturezaOriginal = Natureza.DIARIO,
                valorOriginal = -100_00,
                dataForm = LocalDate.parse("2026-07-25"),
                dataOriginal = hoje,
                cartao = cartao,
            ),
        )
    }

    /**
     * Antecipar: o saldo corrente da data NOVA é anterior à linha original, que portanto ainda
     * não está lá para ser descontada. Descontar mesmo assim inventaria R$ 100,00.
     */
    @Test
    fun mostrarDataAnteriorNaoDescontaOValorAntigo() {
        assertEquals(
            900_00L,
            saldoResultante(
                saldoDoDia = saldoDoDia,
                natureza = Natureza.DIARIO,
                valorAssinado = -100_00,
                naturezaOriginal = Natureza.DIARIO,
                valorOriginal = -100_00,
                dataForm = LocalDate.parse("2026-07-05"),
                dataOriginal = hoje,
                cartao = cartao,
            ),
        )
    }

    /** Virar uma despesa diária em compra de cartão devolve o valor ao saldo do dia. */
    @Test
    fun virarCartaoDescontaOAntigoESomaNada() {
        assertEquals(
            1_100_00L,
            saldoResultante(
                saldoDoDia = saldoDoDia,
                natureza = Natureza.CARTAO,
                valorAssinado = -250_00,
                naturezaOriginal = Natureza.DIARIO,
                valorOriginal = -100_00,
                dataForm = hoje,
                dataOriginal = hoje,
                cartao = cartao,
            ),
        )
    }

    /** O caminho inverso: o valor antigo era do cartão, nunca esteve nesta coluna. */
    @Test
    fun sairDoCartaoNaoDescontaOAntigo() {
        assertEquals(
            900_00L,
            saldoResultante(
                saldoDoDia = saldoDoDia,
                natureza = Natureza.DIARIO,
                valorAssinado = -100_00,
                naturezaOriginal = Natureza.CARTAO,
                valorOriginal = -250_00,
                dataForm = hoje,
                dataOriginal = hoje,
                cartao = cartao,
            ),
        )
    }

    /**
     * A compra original no cartão JÁ pesou no saldo: sua fatura venceu em 5 de agosto, e a
     * edição a joga para o dia 10 como despesa diária. O saldo corrente do dia 10 carrega a
     * fatura antiga (com os R$ 250,00 dentro) — que a edição vai encolher — e ganha os
     * R$ 100,00 novos: 1.000 + 250 − 100.
     */
    @Test
    fun cartaoOriginalJaFaturadoEhDescontado() {
        assertEquals(
            1_150_00L,
            saldoResultante(
                saldoDoDia = saldoDoDia,
                natureza = Natureza.DIARIO,
                valorAssinado = -100_00,
                naturezaOriginal = Natureza.CARTAO,
                valorOriginal = -250_00,
                dataForm = LocalDate.parse("2026-08-10"),
                dataOriginal = LocalDate.parse("2026-07-10"),
                cartao = cartao,
            ),
        )
    }

    /** Continua no cartão, mas noutro ciclo (vence 5 de setembro): a fatura antiga devolve, a nova ainda não pesa. */
    @Test
    fun cartaoMovidoParaCicloSeguinteDevolveAFaturaAntiga() {
        assertEquals(
            1_250_00L,
            saldoResultante(
                saldoDoDia = saldoDoDia,
                natureza = Natureza.CARTAO,
                valorAssinado = -250_00,
                naturezaOriginal = Natureza.CARTAO,
                valorOriginal = -250_00,
                dataForm = LocalDate.parse("2026-08-10"),
                dataOriginal = LocalDate.parse("2026-07-10"),
                cartao = cartao,
            ),
        )
    }

    /** Mudar só o valor dentro do mesmo ciclo: nada disso vence antes do dia editado. */
    @Test
    fun cartaoEditadoNoMesmoCicloNaoMexeNoSaldoDoDia() {
        assertEquals(
            1_000_00L,
            saldoResultante(
                saldoDoDia = saldoDoDia,
                natureza = Natureza.CARTAO,
                valorAssinado = -300_00,
                naturezaOriginal = Natureza.CARTAO,
                valorOriginal = -250_00,
                dataForm = LocalDate.parse("2026-07-15"),
                dataOriginal = LocalDate.parse("2026-07-10"),
                cartao = cartao,
            ),
        )
    }

    /**
     * Borda: fecha 30 e vence 31 clampam ambos para 28 de fevereiro, então a fatura vence no
     * próprio dia da compra — e uma compra nova nesse dia pesa no saldo do dia, sim.
     */
    @Test
    fun cartaoNovoQueVenceNoMesmoDiaEntraNoSaldoDoDia() {
        assertEquals(
            800_00L,
            saldoResultante(
                saldoDoDia = saldoDoDia,
                natureza = Natureza.CARTAO,
                valorAssinado = -200_00,
                naturezaOriginal = null,
                valorOriginal = 0,
                dataForm = LocalDate.parse("2026-02-28"),
                dataOriginal = null,
                cartao = CartaoConfig(fechamentoDia = 30, vencimentoDia = 31),
            ),
        )
    }
}
