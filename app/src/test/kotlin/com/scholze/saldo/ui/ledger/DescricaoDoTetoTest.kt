package com.scholze.saldo.ui.ledger

import com.scholze.saldo.domain.Teto
import com.scholze.saldo.ui.privacy.MASCARA_PRIVACIDADE
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** A frase que o TalkBack lê na linha do teto — a linha visual são quatro nós soltos. */
class DescricaoDoTetoTest {

    private fun teto(
        tetoCentavos: Long = 8_740,
        gastoDeHoje: Long = 0,
        dias: Int = 12,
    ) = Teto(
        mes = YearMonth.of(2026, 9),
        tetoCentavos = tetoCentavos,
        gastoDeHojeCentavos = gastoDeHoje,
        diasRestantes = dias,
        sobraDoMesCentavos = tetoCentavos * dias - gastoDeHoje,
    )

    @Test
    fun `antes do primeiro gasto le so o teto`() {
        assertEquals("pode gastar R$ 87,40 hoje", descricaoDoTeto(teto(), oculto = false))
    }

    @Test
    fun `depois de gastar le os dois numeros`() {
        assertEquals(
            "pode gastar R$ 87,40 hoje, restam R$ 45,40",
            descricaoDoTeto(teto(gastoDeHoje = 4_200), oculto = false),
        )
    }

    @Test
    fun `dia estourado vira uma frase, e nao um numero negativo solto`() {
        // "restam −R$ 14,60" lido em voz alta perde o sinal com facilidade; "passou em" não.
        assertEquals(
            "pode gastar R$ 87,40 hoje, o dia passou em R$ 14,60",
            descricaoDoTeto(teto(gastoDeHoje = 10_200), oculto = false),
        )
    }

    @Test
    fun `mes estourado abre dizendo que estourou`() {
        assertEquals(
            "o mês já estourou, hoje −R$ 27,27",
            descricaoDoTeto(teto(tetoCentavos = -2_727), oculto = false),
        )
    }

    @Test
    fun `oculto nao vaza o valor pelo leitor de tela`() {
        val frase = descricaoDoTeto(teto(gastoDeHoje = 4_200), oculto = true)
        assertEquals("pode gastar $MASCARA_PRIVACIDADE hoje, restam $MASCARA_PRIVACIDADE", frase)
        assertFalse("87" in frase || "45" in frase)
    }
}
