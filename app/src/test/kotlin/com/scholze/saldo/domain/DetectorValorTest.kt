package com.scholze.saldo.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DetectorValorTest {

    private fun v(texto: String) = DetectorValor.primeiroValorEmCentavos(texto)

    @Test fun `valor simples com centavos`() {
        assertEquals(3_290L, v("Compra aprovada de R\$ 32,90"))
    }

    @Test fun `sem espaco depois do cifrao`() {
        assertEquals(3_290L, v("R\$32,90 no crédito"))
    }

    @Test fun `minusculo tambem vale`() {
        assertEquals(500L, v("pagamento de r\$ 5,00"))
    }

    @Test fun `milhar com ponto`() {
        assertEquals(123_456L, v("R\$ 1.234,56 debitado"))
    }

    @Test fun `varios milhares`() {
        assertEquals(123_456_789L, v("R\$ 1.234.567,89"))
    }

    /** Banco que não usa separador de milhar não pode virar R$ 1,23. */
    @Test fun `milhar sem separador`() {
        assertEquals(123_456L, v("R\$ 1234,56"))
    }

    @Test fun `sem centavos vale reais inteiros`() {
        assertEquals(123_400L, v("R\$ 1.234 transferidos"))
    }

    /** O primeiro é a transação; o segundo costuma ser o saldo da conta. */
    @Test fun `com dois valores fica o primeiro`() {
        assertEquals(3_290L, v("Compra de R\$ 32,90 · saldo R\$ 1.204,00"))
    }

    @Test fun `dolar nao conta`() {
        assertNull(v("Assinatura de US\$ 5,99"))
    }

    @Test fun `cifrao sem numero nao conta`() {
        assertNull(v("seu limite em R\$ mudou"))
    }

    @Test fun `texto sem valor`() {
        assertNull(v("Você tem uma nova mensagem"))
    }

    @Test fun `zero nao vira sugestao`() {
        assertNull(v("tarifa de R\$ 0,00"))
    }

    @Test fun `numero absurdo nao estoura`() {
        assertNull(v("R\$ 99.999.999.999.999.999,99"))
    }

    @Test fun `texto vazio`() {
        assertNull(v(""))
    }
}
