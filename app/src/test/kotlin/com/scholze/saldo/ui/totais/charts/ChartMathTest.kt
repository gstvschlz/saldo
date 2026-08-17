package com.scholze.saldo.ui.totais.charts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChartMathTest {

    @Test
    fun largurasAplicamPisoERenormalizam() {
        val l = ChartMath.larguras(listOf(0.5f, 0.49f, 0.01f))
        assertEquals(0.02f, l[2], 1e-6f)          // a fatia de 1 % sobe para o piso
        assertEquals(1f, l.sum(), 1e-4f)           // e as outras encolhem para fechar em 1
        assertTrue(l[0] > l[1])
        // O encolhimento é PROPORCIONAL: as duas fatias grandes são multiplicadas pela mesma
        // escala, então a razão entre elas sobrevive. Um desconto igual (subtrair o mesmo valor
        // das duas) mudaria essa razão — só a versão proporcional bate com 0.5f / 0.49f.
        assertEquals(0.5f / 0.49f, l[0] / l[1], 1e-4f)
    }

    @Test
    fun largurasSemFatiasPequenasSaoAsProprias() {
        val l = ChartMath.larguras(listOf(0.6f, 0.4f))
        assertEquals(0.6f, l[0], 1e-6f)
        assertEquals(0.4f, l[1], 1e-6f)
    }

    @Test
    fun largurasZeroFicamZero() {
        assertEquals(listOf(0f, 0f), ChartMath.larguras(listOf(0f, 0f)))
        val l = ChartMath.larguras(listOf(0.7f, 0f, 0.3f))
        assertEquals(0f, l[1], 0f)
        assertEquals(1f, l.sum(), 1e-4f)
    }

    @Test
    fun largurasVazioRetornaVazio() {
        assertTrue(ChartMath.larguras(emptyList()).isEmpty())
    }

    @Test
    fun largurasNormalizamQuandoNaoSomamUm() {
        // 2 e 1 não somam 1 — sem o passo `it / total`, este teste passaria mesmo que alguém
        // removesse a normalização (todo outro teste já soma 1 de saída).
        val l = ChartMath.larguras(listOf(2f, 1f))
        assertEquals(2f / 3f, l[0], 1e-4f)
        assertEquals(1f / 3f, l[1], 1e-4f)
    }

    @Test
    fun alturasRelativasAoMaiorValorAbsoluto() {
        assertEquals(listOf(1f, 0.5f, 0f), ChartMath.alturas(listOf(200L, 100L, 0L)))
        assertEquals(listOf(1f, 0.5f), ChartMath.alturas(listOf(-200L, 100L)))
        assertEquals(listOf(0f, 0f), ChartMath.alturas(listOf(0L, 0L)))
    }

    @Test
    fun linhaAscendenteComMinimoJaEmZero() {
        // min já é 0, então o comportamento é igual ao de antes de incluir o zero na escala.
        assertEquals(listOf(0f, 0.5f, 1f), ChartMath.linha(listOf(0L, 50L, 100L)))
    }

    @Test
    fun linhaDescendenteEspelhaAAscendente() {
        assertEquals(listOf(1f, 0.5f, 0f), ChartMath.linha(listOf(100L, 50L, 0L)))
    }

    @Test
    fun linhaVaziaFicaVazia() {
        assertTrue(ChartMath.linha(emptyList()).isEmpty())
    }

    @Test
    fun linhaConstanteZeroFicaNoMeio() {
        // Sem nenhum sinal pra mostrar (a série é exatamente zero): meio da régua, neutro.
        assertEquals(listOf(0.5f, 0.5f), ChartMath.linha(listOf(0L, 0L)))
    }

    @Test
    fun linhaConstantePositivaSobePertoDoTopo() {
        // min = min(0, 7) = 0 ; max = max(0, 7) = 7 → (7-0)/(7-0) = 1 para os dois pontos.
        // Antes (min-max só dos dados) uma série constante desenhava reto no meio, escondendo que
        // ela é positiva; agora a escala nasce do zero, então o sinal aparece mesmo sem variação.
        assertEquals(listOf(1f, 1f), ChartMath.linha(listOf(7L, 7L)))
    }

    @Test
    fun linhaConstanteNegativaDescePertoDoFundo() {
        // min = min(0, -7) = -7 ; max = max(0, -7) = 0 → (-7-(-7))/(0-(-7)) = 0 para os dois.
        assertEquals(listOf(0f, 0f), ChartMath.linha(listOf(-7L, -7L)))
    }

    @Test
    fun linhaComSinalMistoNaoDesenhaAMesmaFormaQueSoPositivo() {
        // Achado original: [-500,+500] e [+100,+900] desenhavam a MESMA forma ([0,1] nos dois),
        // porque a normalização só olhava pra variação dos próprios dados. Incluindo o zero na
        // escala elas ficam diferentes: a que mistura sinal cobre a régua inteira; a que é só
        // positiva fica presa na metade de cima.
        // [-500,500]: min=min(0,-500)=-500, max=max(0,500)=500 → (-500-(-500))/1000=0 ; (500-(-500))/1000=1
        assertEquals(listOf(0f, 1f), ChartMath.linha(listOf(-500L, 500L)))
        // [100,900]: min=min(0,100)=0, max=max(0,900)=900 → 100/900 ; 900/900=1
        val soPositivo = ChartMath.linha(listOf(100L, 900L))
        assertEquals(100f / 900f, soPositivo[0], 1e-6f)
        assertEquals(1f, soPositivo[1], 1e-6f)
    }

    @Test
    fun linhaZeroNuloQuandoNaoMisturaSinal() {
        assertNull(ChartMath.linhaZero(emptyList()))
        assertNull(ChartMath.linhaZero(listOf(100L, 200L)))   // só positivo
        assertNull(ChartMath.linhaZero(listOf(-100L, -200L))) // só negativo
    }

    @Test
    fun linhaZeroPosicaoQuandoASerieMisturaSinal() {
        // [-500,500]: min=-500, max=500 → (0-(-500))/(500-(-500)) = 500/1000 = 0.5 (simétrico).
        assertEquals(0.5f, ChartMath.linhaZero(listOf(-500L, 500L))!!, 1e-6f)
        // [-500,100,900]: min=min(0,-500)=-500, max=max(0,900)=900 → (0-(-500))/(900-(-500)) = 500/1400.
        assertEquals(500f / 1400f, ChartMath.linhaZero(listOf(-500L, 100L, 900L))!!, 1e-6f)
    }
}
