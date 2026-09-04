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

        // O caso acima discrimina proporcional vs. desconto igual por só ~2.1e-4 (a tolerância é
        // 1e-4): um futuro ajuste de tolerância mataria o teste em silêncio. Um spread maior pina
        // a mesma regra com folga.
        // [0.9, 0.09, 0.01]: só 0.01 < piso (0.02) → restante = 0.9 + 0.09 = 0.99,
        // escala = (1 - 1 * 0.02) / 0.99 = 0.98 / 0.99 ≈ 0.989899.
        // l2[0] = 0.9 * 0.989899 ≈ 0.890909 ; l2[1] = 0.09 * 0.989899 ≈ 0.089091 ; l2[2] = 0.02.
        // Proporcional: l2[0] / l2[1] = 0.9 / 0.09 = 10.0 exatamente (a escala se cancela na razão).
        // Desconto igual (deficit 0.01 dividido pelas 2 fatias grandes, 0.005 cada) daria
        // 0.895 / 0.085 ≈ 10.53 — 0.53 de diferença, bem acima da tolerância.
        val l2 = ChartMath.larguras(listOf(0.9f, 0.09f, 0.01f))
        assertEquals(0.02f, l2[2], 1e-6f)
        assertEquals(1f, l2.sum(), 1e-4f)
        assertEquals(0.9f / 0.09f, l2[0] / l2[1], 1e-4f)
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

    // --- linha(): escala min-max pura (sem forçar o zero), usada por ReservaLine. ---

    @Test
    fun linhaAscendente() {
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
    fun linhaConstanteFicaNoMeio() {
        // Sem nenhuma variação pra mostrar: meio da régua, neutro (min-max puro não sabe de sinal).
        assertEquals(listOf(0.5f, 0.5f), ChartMath.linha(listOf(0L, 0L)))
        assertEquals(listOf(0.5f, 0.5f), ChartMath.linha(listOf(7L, 7L)))
    }

    @Test
    fun linhaReservaCrescenteEPositivaOcupaAEscalaInteira() {
        // Achado do round 2: linha() (o caminho de ReservaLine) NÃO pode ancorar no zero, senão
        // uma reserva positiva que sobe pouco em relação ao próprio tamanho vira quase reta.
        // [420000,450000,470000,470000,500000,530000]: min=420000, max=530000, faixa=110000.
        val l = ChartMath.linha(listOf(420_000L, 450_000L, 470_000L, 470_000L, 500_000L, 530_000L))
        assertEquals(0f, l[0], 1e-6f)                  // (420000-420000)/110000 = 0
        assertEquals(30_000f / 110_000f, l[1], 1e-6f)  // (450000-420000)/110000
        assertEquals(50_000f / 110_000f, l[2], 1e-6f)  // (470000-420000)/110000
        assertEquals(50_000f / 110_000f, l[3], 1e-6f)  // ponto repetido, mesma posição
        assertEquals(80_000f / 110_000f, l[4], 1e-6f)  // (500000-420000)/110000
        assertEquals(1f, l[5], 1e-6f)                  // (530000-420000)/110000 = 1
    }

    // --- linhaComSinal(): como linha(), mas ancorada em zero — usada pela linha do sobrou. ---

    @Test
    fun linhaComSinalAscendenteComMinimoJaEmZero() {
        // min já é 0, então incluir o zero na escala não muda nada aqui.
        assertEquals(listOf(0f, 0.5f, 1f), ChartMath.linhaComSinal(listOf(0L, 50L, 100L)))
    }

    @Test
    fun linhaComSinalVaziaFicaVazia() {
        assertTrue(ChartMath.linhaComSinal(emptyList()).isEmpty())
    }

    @Test
    fun linhaComSinalConstanteZeroFicaNoMeio() {
        // A série é exatamente zero: nem positiva nem negativa, meio da régua.
        assertEquals(listOf(0.5f, 0.5f), ChartMath.linhaComSinal(listOf(0L, 0L)))
    }

    @Test
    fun linhaComSinalConstantePositivaSobePertoDoTopo() {
        // min = min(0, 7) = 0 ; max = max(0, 7) = 7 → (7-0)/(7-0) = 1 para os dois pontos.
        // Sem incluir o zero, uma série constante desenharia reto no meio, escondendo que ela é
        // positiva; com o zero na escala o sinal aparece mesmo sem variação.
        assertEquals(listOf(1f, 1f), ChartMath.linhaComSinal(listOf(7L, 7L)))
    }

    @Test
    fun linhaComSinalConstanteNegativaDescePertoDoFundo() {
        // min = min(0, -7) = -7 ; max = max(0, -7) = 0 → (-7-(-7))/(0-(-7)) = 0 para os dois.
        assertEquals(listOf(0f, 0f), ChartMath.linhaComSinal(listOf(-7L, -7L)))
    }

    @Test
    fun linhaComSinalMistoESoPositivoNaoDesenhamMaisAMesmaForma() {
        // Achado original: [-500,+500] e [+100,+900] desenhavam a MESMA forma ([0,1] nos dois) na
        // linha() de antes, porque a normalização só olhava pra variação dos próprios dados.
        // Incluindo o zero na escala (linhaComSinal): a série que já mistura sinal não muda — zero
        // já cai dentro da variação dela (min(0,-500)=-500, max(0,500)=500, iguais aos dados).
        // [-500,500]: min=min(0,-500)=-500, max=max(0,500)=500 → (-500-(-500))/1000=0 ; (500-(-500))/1000=1
        assertEquals(listOf(0f, 1f), ChartMath.linhaComSinal(listOf(-500L, 500L)))
        // A série só positiva passa a nascer perto do FUNDO, não mais em 0 puro: o mínimo da régua
        // agora é zero, bem abaixo do menor valor real (100). Ela ainda sobe até o topo — só que a
        // partir de quase embaixo, não do meio nem do topo.
        // [100,900]: min=min(0,100)=0, max=max(0,900)=900 → 100/900 ; 900/900=1
        val soPositivo = ChartMath.linhaComSinal(listOf(100L, 900L))
        assertEquals(100f / 900f, soPositivo[0], 1e-6f) // ≈ 0.11 — perto do fundo, não da metade
        assertEquals(1f, soPositivo[1], 1e-6f)
    }

    // --- linhaZero(): mesma escala de linhaComSinal(). ---

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

    // --- pisoSeNaoZero(): geometria do "mínimo de visibilidade" usada pelas barras. ---

    @Test
    fun pisoSeNaoZeroFracaoZeroOuNegativaFicaZero() {
        assertEquals(0f, ChartMath.pisoSeNaoZero(0f, 100f, 2f), 0f)
        assertEquals(0f, ChartMath.pisoSeNaoZero(-0.5f, 100f, 2f), 0f)
    }

    @Test
    fun pisoSeNaoZeroValorMinusculoSobeParaOPiso() {
        // 0.001 * 100 = 0.1, bem abaixo do piso de 2 — sobe pra não sumir da tela.
        assertEquals(2f, ChartMath.pisoSeNaoZero(0.001f, 100f, 2f), 1e-6f)
    }

    @Test
    fun pisoSeNaoZeroValorGrandeFicaProporcionalSemAjuste() {
        // 0.8 * 100 = 80, já acima do piso de 2 — passa direto, sem ajuste.
        assertEquals(80f, ChartMath.pisoSeNaoZero(0.8f, 100f, 2f), 1e-4f)
    }

    @Test
    fun pisoSeNaoZeroPisoNaoUltrapassaOEspacoDisponivel() {
        // Canvas de 1px com piso de 2: o piso encolhe pra caber, senão desenharia fora da área
        // (TrendChart(altura = 1.dp) ou WeekdayBars(altura = 1.dp) sem esse limite).
        assertEquals(1f, ChartMath.pisoSeNaoZero(0.001f, 1f, 2f), 1e-6f)
    }

    // ---- linhasNaMesmaEscala ----

    @Test
    fun `duas series dividem a escala e a origem no zero`() {
        val (a, b) = ChartMath.linhasNaMesmaEscala(listOf(0L, 50L, 100L), listOf(0L, 25L, 50L))
        assertEquals(listOf(0f, 0.5f, 1f), a)
        assertEquals(listOf(0f, 0.25f, 0.5f), b)
    }

    /** A série menor não pode ser reescalada para o próprio máximo: sumiria a diferença. */
    @Test
    fun `a serie menor fica embaixo`() {
        val (a, b) = ChartMath.linhasNaMesmaEscala(listOf(100L), listOf(10L))
        assertTrue(a.first() > b.first())
    }

    @Test
    fun `tudo zero nao estoura`() {
        val (a, b) = ChartMath.linhasNaMesmaEscala(listOf(0L, 0L), listOf(0L, 0L))
        assertEquals(listOf(0f, 0f), a)
        assertEquals(listOf(0f, 0f), b)
    }

    @Test
    fun `referencia vazia nao atrapalha a outra`() {
        val (a, b) = ChartMath.linhasNaMesmaEscala(listOf(0L, 100L), emptyList())
        assertEquals(listOf(0f, 1f), a)
        assertTrue(b.isEmpty())
    }

    // ---- empilhado ----

    @Test
    fun `a coluna maior enche a altura e as outras ficam proporcionais`() {
        val r = ChartMath.empilhado(listOf(listOf(50L, 50L), listOf(25L, 25L)))
        assertEquals(1f, r[0].sum(), 0.0001f)
        assertEquals(0.5f, r[1].sum(), 0.0001f)
    }

    @Test
    fun `cada segmento e a sua fatia da coluna maior`() {
        val r = ChartMath.empilhado(listOf(listOf(75L, 25L)))
        assertEquals(listOf(0.75f, 0.25f), r.single())
    }

    @Test
    fun `tudo zero nao estoura no empilhado`() {
        val r = ChartMath.empilhado(listOf(listOf(0L, 0L), listOf(0L, 0L)))
        assertTrue(r.all { coluna -> coluna.all { it == 0f } })
    }

    @Test
    fun `sem coluna nenhuma devolve vazio`() {
        assertTrue(ChartMath.empilhado(emptyList()).isEmpty())
    }
}
