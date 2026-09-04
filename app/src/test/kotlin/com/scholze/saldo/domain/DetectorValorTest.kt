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

    // ---- BRL: a carteira do cartão escreve o mesmo gasto de outro jeito ----

    /**
     * As duas notificações da MESMA compra, como o aparelho as mostrou: o app do banco em
     * "R$ 16,90" e a carteira em "BRL 16.90". A segunda não era detectada de jeito nenhum.
     */
    @Test fun `o mesmo gasto nas duas escritas`() {
        assertEquals(1_690L, v("Compra de R\$ 16,90 APROVADA em VMT*CAROLINA via cartão digital."))
        assertEquals(1_690L, v("VMT*CAROLINA BRL 16.90"))
    }

    @Test fun `brl com virgula`() {
        assertEquals(1_690L, v("BRL 16,90"))
    }

    @Test fun `brl colado no numero`() {
        assertEquals(1_690L, v("BRL16,90"))
    }

    @Test fun `brl depois do numero`() {
        assertEquals(1_690L, v("débito de 16,90 BRL"))
    }

    @Test fun `brl minusculo tambem vale`() {
        assertEquals(1_690L, v("brl 16.90"))
    }

    /** `\bBRL` só casa em começo de palavra: senão qualquer "COBRL 5,00" viraria dinheiro. */
    @Test fun `brl dentro de palavra nao conta`() {
        assertNull(v("pedido COBRL 5,00"))
        assertNull(v("BRLX 16,90"))
    }

    // ---- ponto como decimal ----

    /** Antes isto casava só o "16" e virava R$ 16,00 calado — o pior tipo de erro. */
    @Test fun `ponto com dois digitos e decimal, nao milhar`() {
        assertEquals(1_690L, v("R\$ 16.90"))
    }

    @Test fun `milhar americano com ponto decimal`() {
        assertEquals(123_456L, v("BRL 1,234.56"))
    }

    /** Ponto com três dígitos continua sendo milhar. */
    @Test fun `ponto com tres digitos e milhar`() {
        assertEquals(123_400L, v("BRL 1.234"))
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
