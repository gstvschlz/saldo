package com.scholze.saldo.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LembretesConfigTest {

    @Test
    fun nenhumLembreteLigadoTemZeroAtivos() {
        assertEquals(0, LembretesConfig().ativos)
    }

    @Test
    fun doisLembretesLigadosContamDois() {
        val config = LembretesConfig(faturaAmanha = true, registrarGastos = true)
        assertEquals(2, config.ativos)
    }

    @Test
    fun apenasRegistrarGastosEAlgumMasNaoAlgumInformativo() {
        val config = LembretesConfig(registrarGastos = true)
        assertTrue(config.algum)
        assertFalse(config.algumInformativo)
    }

    /** O slot do fim do dia tem dois lembretes; só um ligado já basta para ele ser agendado. */
    @Test
    fun soEtiquetarJaLigaOSlotDoFimDoDia() {
        val config = LembretesConfig(etiquetarHoje = true)
        assertTrue(config.algumDoFimDoDia)
        assertTrue(config.algum)
        assertFalse(config.algumInformativo)
        assertEquals(1, config.ativos)
    }

    @Test
    fun semNenhumDosDoisOSlotDoFimDoDiaFicaDesligado() {
        assertFalse(LembretesConfig(faturaAmanha = true).algumDoFimDoDia)
    }
}
