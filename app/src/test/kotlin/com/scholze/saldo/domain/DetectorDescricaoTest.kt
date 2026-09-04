package com.scholze.saldo.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DetectorDescricaoTest {

    private fun d(texto: String) = DetectorDescricao.descricao(texto)

    // ---- os dois textos do print, que são a mesma compra ----

    @Test
    fun `o app do banco escreve com preposicao`() {
        assertEquals(
            "VMT*CAROLINA",
            d("Compra de R\$ 16,90 APROVADA em VMT*CAROLINA via cartão digital."),
        )
    }

    @Test
    fun `a carteira escreve so o nome e o valor`() {
        assertEquals("VMT*CAROLINA", d("VMT*CAROLINA BRL 16.90"))
    }

    // ---- a regra da preposição ----

    @Test
    fun `pega o nome depois de no`() {
        assertEquals("MERCADO SAO JOSE", d("Compra aprovada de R\$ 32,90 no MERCADO SAO JOSE"))
    }

    @Test
    fun `pega nome com minuscula tambem`() {
        assertEquals("João Silva", d("Você pagou R\$ 50,00 para João Silva"))
    }

    @Test
    fun `a forma de pagamento nao entra no nome`() {
        assertEquals("PADARIA CENTRAL", d("Débito de R\$ 12,00 em PADARIA CENTRAL via aproximação"))
    }

    @Test
    fun `pontuacao fecha o nome`() {
        assertEquals("BAR DO ZE", d("Compra em BAR DO ZE, aprovada"))
    }

    /** "DA" no meio de um nome não pode cortá-lo, mesmo estando na lista de paradas. */
    @Test
    fun `parada no meio do nome nao corta`() {
        assertEquals("MERCADO DA ESQUINA", d("Compra de R\$ 20,00 no MERCADO DA ESQUINA"))
    }

    // ---- a regra das maiúsculas ----

    @Test
    fun `palavra de transacao nao vira nome`() {
        // "APROVADA" é maiúscula e não é estabelecimento; sem preposição, sobra o nome.
        assertEquals("SUPERMERCADO XYZ", d("APROVADA COMPRA SUPERMERCADO XYZ BRL 40.00"))
    }

    @Test
    fun `a maior sequencia ganha`() {
        assertEquals("POSTO IPIRANGA CENTRO", d("PIX POSTO IPIRANGA CENTRO R\$ 100,00"))
    }

    // ---- quando não dá para dizer ----

    @Test
    fun `aviso de saldo nao tem estabelecimento`() {
        assertNull(d("Seu saldo é de R\$ 1.000,00"))
    }

    @Test
    fun `texto vazio`() {
        assertNull(d(""))
    }

    @Test
    fun `so o valor nao vira nome`() {
        assertNull(d("R\$ 16,90"))
    }

    @Test
    fun `numero sozinho nao vira nome`() {
        assertNull(d("BRL 16.90 12345"))
    }

    // ---- tamanho ----

    @Test
    fun `nome comprido cabe numa linha de ledger`() {
        val gigante = "Compra em " + "A".repeat(80)
        val nome = d(gigante)!!
        assertEquals(40, nome.length)
    }
}
