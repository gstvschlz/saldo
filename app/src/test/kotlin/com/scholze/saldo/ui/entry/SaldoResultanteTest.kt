package com.scholze.saldo.ui.entry

import com.scholze.saldo.domain.Natureza
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class SaldoResultanteTest {

    private val hoje = LocalDate.parse("2026-07-20")
    private val saldoDoDia = 1_000_00L

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
            ),
        )
    }
}
