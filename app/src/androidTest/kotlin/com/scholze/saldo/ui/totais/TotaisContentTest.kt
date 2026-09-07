package com.scholze.saldo.ui.totais

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.scholze.saldo.domain.ACaminho
import com.scholze.saldo.domain.Fatia
import com.scholze.saldo.domain.Fatura
import com.scholze.saldo.domain.GrupoGasto
import com.scholze.saldo.domain.ItemDia
import com.scholze.saldo.domain.ItemFuturo
import com.scholze.saldo.domain.MesPorTag
import com.scholze.saldo.domain.Movimentacao
import com.scholze.saldo.domain.Natureza
import com.scholze.saldo.domain.Padroes
import com.scholze.saldo.domain.ParaOndeFoi
import com.scholze.saldo.domain.PontoMes
import com.scholze.saldo.domain.Recorrencia
import com.scholze.saldo.domain.ResumoRecorrencias
import com.scholze.saldo.domain.Ritmo
import com.scholze.saldo.domain.Tag
import com.scholze.saldo.domain.TagsNoTempo
import com.scholze.saldo.domain.TotaisMes
import com.scholze.saldo.ui.privacy.LocalPrivacy
import com.scholze.saldo.ui.privacy.MASCARA_PRIVACIDADE
import com.scholze.saldo.ui.privacy.PrivacyState
import com.scholze.saldo.ui.theme.SaldoTheme
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** A tela de totais sobre estado fabricado — sem ViewModel, sem banco. */
@RunWith(AndroidJUnit4::class)
class TotaisContentTest {

    @get:Rule val rule = createComposeRule()

    private val totais = TotaisMes(
        entradasCentavos = 8_240_00,
        // O engine devolve MAGNITUDES positivas aqui; a tela é que aplica o sinal.
        saidasPorNatureza = mapOf(
            Natureza.DIARIO to 1_000_00,
            Natureza.ECONOMIA to 1_500_00,
            Natureza.CARTAO to 300_00,
        ),
        sobrouCentavos = 5_440_00,
        economiaBucketCentavos = 12_000_00,
        faturaAtual = Fatura(YearMonth.of(2026, 7), LocalDate.parse("2026-08-05"), -300_00, emptyList()),
        fechamentoFaturaAtual = LocalDate.parse("2026-07-28"),
        topTags = listOf(Tag(1, "comida", 0xFFA6486B) to 700_00L),
    )

    private val comida = Tag(1, "comida", 0xFFA6486B)
    private val moradia = Tag(2, "moradia", 0xFFB95A2E)
    private val lazer = Tag(3, "lazer", 0xFF3B7A57)
    private val mercado = Movimentacao(id = 9, descricao = "mercado", valorCentavos = -489_90, data = LocalDate.parse("2026-07-13"), natureza = Natureza.DIARIO)
    private val fatias = listOf(
        Fatia(GrupoGasto.DeTag(comida), 700_00, 0.25f, 30),
        Fatia(GrupoGasto.DeTag(moradia), 600_00, 0.21f, 0),
        // Delta negativo: pega o "−" hand-typed de InsightsEngine.delta + o sinal invertido juntos
        // (um bug de duplo-menos ou um hífen ASCII no lugar do U+2212 passariam pela fixture antiga).
        Fatia(GrupoGasto.DeTag(lazer), 200_00, 0.07f, -18),
        Fatia(GrupoGasto.SemTag, 100_00, 0.04f, null),
    )
    private val insights = ParaOndeFoi(
        saidasCentavos = 2_800_00,
        fatias = fatias,
        barra = fatias,
        maioresGastos = listOf(mercado),
        padroes = Padroes(
            porDiaDaSemana = DayOfWeek.entries.associateWith { 0L } + (DayOfWeek.SATURDAY to 98_10L),
            diaMaisCaro = DayOfWeek.SATURDAY,
            avulsasPorDiaMes = 82_10,
            mediaDiaria30 = 71_00,
        ),
    )

    private val tendencia = (5 downTo 0).map { k ->
        val m = YearMonth.of(2026, 7).minusMonths(k.toLong())
        PontoMes(
            mes = m, entradas = 8_000_00, saidas = 7_000_00 + k * 100_00L, sobrou = 1_000_00 - k * 100_00L,
            reservaAcumulada = 1_200_00 - k * 100_00L, taxaPoupanca = 12 - k,
        )
    }


    private val aluguelMov = Movimentacao(
        id = 7, descricao = "aluguel", valorCentavos = -2_400_00, data = LocalDate.parse("2026-07-28"),
        natureza = Natureza.DIARIO, recorrenciaId = 1,
    )
    private val aCaminho = ACaminho(
        saemCentavos = 2_400_00,
        entramCentavos = 8_240_00,
        itens = listOf(
            ItemFuturo(aluguelMov.data, ItemDia.Mov(aluguelMov)),
            ItemFuturo(
                LocalDate.parse("2026-07-31"),
                ItemDia.Mov(
                    Movimentacao(
                        id = 8, descricao = "salário", valorCentavos = 8_240_00,
                        data = LocalDate.parse("2026-07-31"), natureza = Natureza.DIARIO,
                    ),
                ),
            ),
        ),
        mesEncerrado = false,
    )
    private val resumoRecorrencias = ResumoRecorrencias(
        ativas = listOf(
            Recorrencia(
                id = 1, descricao = "aluguel", valorCentavos = -2_400_00, natureza = Natureza.DIARIO,
                diaDoMes = 28, inicio = YearMonth.of(2026, 1),
            ),
        ),
        encerradas = emptyList(),
        entramMes = 8_240_00,
        saemMes = 2_400_00,
    )

    // Dia 1..4 de setembro: saiu 15,00 no acumulado, contra um costume de 10,00.
    private val ritmo = Ritmo(
        mes = YearMonth.of(2026, 7),
        acumulado = listOf(0L, 500L, 1_500L, 1_500L),
        referencia = listOf(200L, 400L, 800L, 1_000L),
        mesesComparados = 3,
    )

    private val noTempo = TagsNoTempo(
        grupos = listOf(GrupoGasto.DeTag(comida), GrupoGasto.SemTag),
        meses = (5 downTo 0).map { k ->
            val m = YearMonth.of(2026, 7).minusMonths(k.toLong())
            MesPorTag(m, listOf(300_00L - k * 20_00L, 50_00L), 350_00L - k * 20_00L)
        },
    )

    private fun comACaminho(a: ACaminho) = TotaisUiState(
        YearMonth.of(2026, 7), totais, insights = insights, tendencia = tendencia,
        aCaminho = a, recorrencias = resumoRecorrencias,
    )

    private fun montar(
        state: TotaisUiState,
        oculto: Boolean = false,
        onVerTag: (Tag) -> Unit = {},
        onAbrirMovimentacao: (Movimentacao) -> Unit = {},
        onIrParaMes: (YearMonth) -> Unit = {},
        onIrParaDia: (YearMonth, Int) -> Unit = { _, _ -> },
        onAbrirRecorrencias: () -> Unit = {},
    ) {
        rule.setContent {
            SaldoTheme(darkTheme = false) {
                CompositionLocalProvider(LocalPrivacy provides PrivacyState(ocultoInicial = oculto)) {
                    var segmento by rememberSaveable { mutableStateOf(SegmentoTotais.MES) }
                    TotaisContent(
                        state, {}, {},
                        onVerTag = onVerTag, onAbrirMovimentacao = onAbrirMovimentacao,
                        onIrParaMes = onIrParaMes, onIrParaDia = onIrParaDia,
                        onAbrirRecorrencias = onAbrirRecorrencias,
                        segmento = segmento, onSegmento = { segmento = it },
                    )
                }
            }
        }
    }

    /**
     * Escopa a opção do segmented control via [TAG_SEGMENTO_TOTAIS] em vez de bater no texto
     * visível solto — [TAG_SEGMENTO_TOTAIS] existia sem leitor nenhum; "tendência"/"mês" também
     * poderiam colidir com cópia igual em outro canto da tela.
     */
    private fun segmento(rotulo: String) =
        rule.onNode(hasAnyAncestor(hasTestTag(TAG_SEGMENTO_TOTAIS)) and hasText(rotulo))

    @Test
    fun mostraPerformanceEBlocos() {
        montar(TotaisUiState(YearMonth.of(2026, 7), totais, insights = insights))

        rule.onNodeWithText("totais · jul/26").assertIsDisplayed()
        rule.onNodeWithText("sobrou dinheiro").assertIsDisplayed()
        rule.onNodeWithText("+R$ 5.440,00").assertIsDisplayed()
        rule.onNodeWithText("+8.240,00").assertIsDisplayed()
        // As saídas chegam positivas do engine e têm de sair com sinal na tela.
        rule.onNodeWithText("−1.000,00").assertIsDisplayed()
        rule.onNodeWithText("reserva acumulada").assertIsDisplayed()
        rule.onNodeWithText("+12.000,00").assertIsDisplayed()
        rule.onNodeWithText("fecha em").assertIsDisplayed()
        rule.onNodeWithText("28 jul").assertIsDisplayed()
    }

    @Test
    fun paraOndeFoiListaFatiasDeltasMaioresGastosEPadroes() {
        montar(TotaisUiState(YearMonth.of(2026, 7), totais, insights = insights))
        rule.onNodeWithText("para onde foi").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("+30%").assertIsDisplayed()
        rule.onNodeWithText("=").assertIsDisplayed()
        // U+2212 (mesmo caractere de InsightsEngine.delta), não hífen — pega duplo-menos e ASCII.
        rule.onNodeWithText("−18%").assertIsDisplayed()
        rule.onNodeWithText("novo").assertIsDisplayed()
        rule.onNodeWithText("sem tag").assertIsDisplayed()
        // O controle segmentado (Task 5) empurrou estas linhas ~50dp mais para baixo — abaixo da
        // dobra em telas/fontes menores. performScrollTo() é um no-op quando já visível.
        rule.onNodeWithText("maiores gastos").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("mercado").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("−489,90").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("padrões").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("sábado é o dia mais caro").performScrollTo().assertIsDisplayed()
        // A fatia extra do delta negativo empurra estas duas linhas para fora da viewport inicial;
        // performScrollTo() rola o Column da tela até elas antes de checar.
        rule.onNodeWithText("avulsas por dia este mês").performScrollTo().assertIsDisplayed()
        // As duas médias por dia (avulsasPorDiaMes ÷ dias cobertos pelo ledger vs. mediaDiaria30, a
        // média fixa de 30 dias da projeção) não podem virar a mesma leitura por um merge futuro.
        rule.onNodeWithText("média 30 dias (a da projeção)").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun tocarNumaTagENumGastoDisparaOsCallbacks() {
        var tagVista: Tag? = null
        var aberta: Movimentacao? = null
        montar(TotaisUiState(YearMonth.of(2026, 7), totais, insights = insights), onVerTag = { tagVista = it }, onAbrirMovimentacao = { aberta = it })
        rule.onNodeWithText("comida").performClick()
        assertEquals(comida, tagVista)
        // "mercado" (em "maiores gastos") fica fora da viewport inicial — performClick() num nó fora
        // da tela estoura por falta de bounds; performScrollTo() primeiro é um no-op se já visível.
        rule.onNodeWithText("mercado").performScrollTo().performClick()
        assertEquals(mercado, aberta)
        // "sem tag" não é GrupoGasto.DeTag: o guard `as? GrupoGasto.DeTag` barra o clique e a linha
        // fica inerte — sem isso o guard poderia sumir sem nenhum teste notar.
        rule.onNodeWithText("sem tag").performClick()
        assertEquals(comida, tagVista)
    }

    @Test
    fun semSaidasNoMesMostraOVazio() {
        val vazio = insights.copy(saidasCentavos = 0, fatias = emptyList(), barra = emptyList(), maioresGastos = emptyList())
        montar(TotaisUiState(YearMonth.of(2026, 7), totais, insights = vazio))
        rule.onNodeWithText("nenhuma saída este mês").assertIsDisplayed()
    }

    @Test
    fun estimativaSoApareceQuandoHaMesPelaFrente() {
        montar(TotaisUiState(YearMonth.of(2026, 7), totais, estimativaCentavos = 640_00))
        rule.onNodeWithText("estimativa restante").assertIsDisplayed()
        rule.onNodeWithText("−640,00").assertIsDisplayed()
    }

    @Test
    fun semFaturaNoCicloMostraOTextoEmVezDoValor() {
        montar(TotaisUiState(YearMonth.of(2026, 7), totais.copy(faturaAtual = null)))
        rule.onNodeWithText("sem compras no ciclo").assertIsDisplayed()
    }

    @Test
    fun privacidadeMascaraOsValores() {
        // `insights` entra aqui também: é o maior bloco com dinheiro da aba, e sem ele nenhum dos
        // seis valores da seção nova fica testado sob máscara.
        montar(TotaisUiState(YearMonth.of(2026, 7), totais, insights = insights), oculto = true)
        // Os rótulos ficam; todo número passa por MoneyText e vira máscara.
        rule.onNodeWithText("reserva acumulada").assertIsDisplayed()
        rule.onNodeWithText("+R$ 5.440,00").assertDoesNotExist()
        rule.onNodeWithText("+5.440,00").assertDoesNotExist()
        // A fatia "comida" (700_00 centavos, ASSINADO) revelaria "−700,00" se a máscara vazasse.
        rule.onNodeWithText("−700,00").assertDoesNotExist()
        rule.onAllNodesWithText(MASCARA_PRIVACIDADE).onFirst().assertIsDisplayed()
    }

    @Test
    fun tendenciaMostraGraficoPoupancaEVoltaAoMesTocado() {
        var mesPedido: YearMonth? = null
        montar(TotaisUiState(YearMonth.of(2026, 7), totais, insights = insights, tendencia = tendencia), onIrParaMes = { mesPedido = it })
        segmento("tendência").performClick()
        rule.onNodeWithText("6 MESES").assertIsDisplayed()
        rule.onNodeWithText("POUPANÇA").assertIsDisplayed()
        // "reserva acumulada" existe nos dois segmentos (a linha que motivou o fix de formato) —
        // contar 1 é um trap melhor pra um `when` que passasse a ACRESCENTAR em vez de substituir
        // do que só checar a ausência de um rótulo exclusivo do segmento "mês" (abaixo).
        // onAllNodesWithText enxerga nós fora da viewport (o Column com verticalScroll compõe tudo,
        // só posiciona fora da tela), então não precisa de performScrollTo() aqui.
        rule.onAllNodesWithText("reserva acumulada").assertCountEquals(1)
        // O controle segmentado empurrou esta linha ~480dp para baixo, fora da viewport inicial.
        rule.onNodeWithText("12% este mês (jun 11%)").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("para onde foi").assertDoesNotExist()
        // O mês do TrendChart é um botão; o rótulo da barra de poupança é texto puro, e
        // desde as barras os dois existem com o mesmo texto.
        rule.onNode(hasText("mai") and hasClickAction()).performClick()             // rótulo do mês no gráfico
        assertEquals(YearMonth.of(2026, 5), mesPedido)
        // Volta ao segmento "mês", mas com o offset de rolagem que a tendência deixou no scroll
        // state compartilhado — sem performScrollTo() aqui o teste depende de sorte de viewport.
        rule.onNodeWithText("para onde foi").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun segmentoTendenciaSobreviveARestauracaoDeEstado() {
        // `segmento` usa rememberSaveable — um teste que só recompõe (como os outros desta classe)
        // passa igualzinho contra `remember {}`, porque recomposição não recria a instância. Só
        // StateRestorationTester força a recriação que rememberSaveable precisa sobreviver.
        val restorationTester = StateRestorationTester(rule)
        restorationTester.setContent {
            SaldoTheme(darkTheme = false) {
                CompositionLocalProvider(LocalPrivacy provides PrivacyState(ocultoInicial = false)) {
                    var segmento by rememberSaveable { mutableStateOf(SegmentoTotais.MES) }
                    TotaisContent(
                        TotaisUiState(YearMonth.of(2026, 7), totais, insights = insights, tendencia = tendencia), {}, {},
                        segmento = segmento, onSegmento = { segmento = it },
                    )
                }
            }
        }
        segmento("tendência").performClick()
        rule.onNodeWithText("6 MESES").assertIsDisplayed()

        restorationTester.emulateSavedInstanceStateRestore()

        // Se `segmento` fosse `remember {}` puro, a restauração voltaria para "mês" — o teste
        // continua vendo o conteúdo da tendência só porque o estado sobreviveu de verdade.
        rule.onNodeWithText("6 MESES").assertIsDisplayed()
    }

    /**
     * O "—" aparece duas vezes de propósito desde as barras: na linha "taxa de poupança" e
     * na barra do mês sem entrada. As duas dizem a mesma coisa sobre o mesmo mês.
     */
    @Test
    fun taxaPoupancaNulaMostraTraco() {
        val semTaxa = tendencia.dropLast(1) + tendencia.last().copy(taxaPoupanca = null)
        montar(TotaisUiState(YearMonth.of(2026, 7), totais, tendencia = semTaxa))
        segmento("tendência").performClick()
        rule.onNodeWithText("taxa de poupança").performScrollTo().assertIsDisplayed()
        rule.onAllNodesWithText("—").assertCountEquals(2)
    }

    @Test
    fun taxaPoupancaSemMesAnteriorMostraFormaCurta() {
        // Um único ponto: pontos.getOrNull(pontos.size - 2) dá null — o ramo de "não há taxa do mês
        // anterior" que a fixture normal (6 meses) nunca exercita.
        val umPonto = listOf(tendencia.last())
        montar(TotaisUiState(YearMonth.of(2026, 7), totais, tendencia = umPonto))
        segmento("tendência").performClick()
        rule.onNodeWithText("12% este mês").performScrollTo().assertIsDisplayed()
    }
    @Test
    fun aCaminhoMostraCabecalhoListaEAtalhoDeRecorrencias() {
        var dia: Pair<YearMonth, Int>? = null
        var recorrenciasPedidas = false
        montar(
            comACaminho(aCaminho),
            onIrParaDia = { m, d -> dia = m to d },
            onAbrirRecorrencias = { recorrenciasPedidas = true },
        )
        rule.onNodeWithText("a caminho").performClick()

        rule.onNodeWithText("ainda saem").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("até 31 jul").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("aluguel").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("salário").performScrollTo().assertIsDisplayed()
        // O segmento substitui o de mês, nao soma a ele.
        rule.onNodeWithText("para onde foi").assertDoesNotExist()

        rule.onNodeWithText("aluguel").performScrollTo().performClick()
        assertEquals(YearMonth.of(2026, 7) to 28, dia)

        rule.onNodeWithText("1 fixa ·", substring = true).performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("recorrências").performScrollTo().performClick()
        assertTrue(recorrenciasPedidas)
    }

    /** Mes passado: nada a caminho por definicao, mas o atalho continua valendo. */
    @Test
    fun mesPassadoMostraEncerradoEAindaOAtalho() {
        montar(comACaminho(ACaminho(0, 0, emptyList(), mesEncerrado = true)))
        rule.onNodeWithText("a caminho").performClick()
        rule.onNodeWithText("mês encerrado").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("ainda saem").assertDoesNotExist()
        rule.onNodeWithText("recorrências").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun semItensFuturosMostraNadaAgendado() {
        montar(comACaminho(aCaminho.copy(saemCentavos = 0, entramCentavos = 0, itens = emptyList())))
        rule.onNodeWithText("a caminho").performClick()
        rule.onNodeWithText("nada agendado até o fim do mês").performScrollTo().assertIsDisplayed()
    }

    /** Todo valor do segmento passa por MoneyText, entao a mascara vale aqui tambem. */
    @Test
    fun aCaminhoRespeitaAMascaraDePrivacidade() {
        montar(comACaminho(aCaminho), oculto = true)
        rule.onNodeWithText("a caminho").performClick()
        rule.onNodeWithText("ainda saem").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("−2.400,00").assertDoesNotExist()
        rule.onNodeWithText("+8.240,00").assertDoesNotExist()
    }

    // ---- ritmo do mês ----

    @Test
    fun oRitmoDizQuantoAcimaDoCostume() {
        montar(TotaisUiState(YearMonth.of(2026, 7), totais, ritmo = ritmo))
        rule.onNodeWithText("ritmo do mês").performScrollTo().assertIsDisplayed()
        // 1.500 contra 1.000 = 50 % acima.
        rule.onNodeWithText("50% acima do costume").assertIsDisplayed()
    }

    /**
     * O caso que a captura pegou: há meses anteriores, mas até este dia do mês eles estavam
     * zerados. Não há porcentagem — e ainda assim não é "sem mês anterior".
     */
    @Test
    fun comCostumeZeradoORitmoDizSoOLado() {
        montar(
            TotaisUiState(
                YearMonth.of(2026, 7), totais,
                ritmo = ritmo.copy(referencia = listOf(0L, 0L, 0L, 0L)),
            ),
        )
        rule.onNodeWithText("ritmo do mês").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("acima do costume").assertIsDisplayed()
    }

    @Test
    fun semMesAnteriorORitmoDizQueNaoHaComparacao() {
        montar(
            TotaisUiState(
                YearMonth.of(2026, 7), totais,
                ritmo = ritmo.copy(referencia = emptyList(), mesesComparados = 0),
            ),
        )
        rule.onNodeWithText("ritmo do mês").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("sem mês anterior").assertIsDisplayed()
    }

    /** O valor do ritmo é dinheiro: some junto com o resto quando os valores estão ocultos. */
    @Test
    fun oRitmoRespeitaAPrivacidade() {
        montar(TotaisUiState(YearMonth.of(2026, 7), totais, ritmo = ritmo), oculto = true)
        rule.onNodeWithText("ritmo do mês").performScrollTo().assertIsDisplayed()
        rule.onAllNodesWithText(MASCARA_PRIVACIDADE).fetchSemanticsNodes().let {
            assertTrue("nenhum valor mascarado na tela", it.isNotEmpty())
        }
    }

    /** Um mês com um dia só não tem linha para desenhar; o bloco simplesmente não aparece. */
    @Test
    fun noPrimeiroDiaDoMesORitmoNaoAparece() {
        montar(
            TotaisUiState(
                YearMonth.of(2026, 7), totais,
                ritmo = ritmo.copy(acumulado = listOf(500L), referencia = listOf(200L)),
            ),
        )
        rule.onAllNodesWithText("ritmo do mês").fetchSemanticsNodes().let {
            assertEquals(0, it.size)
        }
    }

    // ---- poupança mês a mês ----

    /**
     * A taxa deixou de ser só o número deste mês e do anterior: as seis aparecem em barra.
     * O fixture vai de 7 % (seis meses atrás) a 12 % (o mês visto).
     */
    @Test
    fun aPoupancaMostraATaxaDeCadaMes() {
        montar(TotaisUiState(YearMonth.of(2026, 7), totais, tendencia = tendencia))
        segmento("tendência").performClick()
        rule.onNodeWithText("POUPANÇA").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("12%").assertIsDisplayed()
        rule.onNodeWithText("7%").assertIsDisplayed()
    }

    /** Mês sem entrada não tem taxa: a barra dele fica vazia com um "—" no lugar do número. */
    @Test
    fun mesSemEntradaAparaceComTracoNaPoupanca() {
        val comBuraco = tendencia.mapIndexed { i, p ->
            if (i == 0) p.copy(entradas = 0, taxaPoupanca = null) else p
        }
        montar(TotaisUiState(YearMonth.of(2026, 7), totais, tendencia = comBuraco))
        segmento("tendência").performClick()
        rule.onNodeWithText("POUPANÇA").performScrollTo().assertIsDisplayed()
        // O mês visto (o último) mantém a taxa, então a linha de texto NÃO mostra traço:
        // o único "—" da tela é o da barra do mês sem entrada.
        rule.onAllNodesWithText("—").assertCountEquals(1)
    }

    // ---- para onde foi ao longo do tempo ----

    @Test
    fun paraOndeFoiMostraOsSeisMeses() {
        montar(TotaisUiState(YearMonth.of(2026, 7), totais, insights = insights, tagsNoTempo = noTempo))
        rule.onNodeWithText("nos últimos 6 meses").performScrollTo().assertIsDisplayed()
        // Os rótulos de mês das colunas — fev é o primeiro da janela que termina em julho.
        // O cabeçalho da seção rola para dentro da tela, mas as colunas ficam abaixo dele.
        rule.onNodeWithText("fev").performScrollTo().assertIsDisplayed()
    }

    /** Sem grupo nenhum na janela não há o que empilhar, e a seção não aparece. */
    @Test
    fun semGrupoNaJanelaNaoDesenhaAsColunas() {
        montar(
            TotaisUiState(
                YearMonth.of(2026, 7), totais, insights = insights,
                tagsNoTempo = TagsNoTempo(emptyList(), emptyList()),
            ),
        )
        rule.onAllNodesWithText("nos últimos 6 meses").fetchSemanticsNodes().let {
            assertEquals(0, it.size)
        }
        rule.onNodeWithText("sem tags neste período").performScrollTo().assertIsDisplayed()
    }

    // ---- tendência: "precisa de mais um mês" ----

    private fun montarTendencia(pontosComMovimentoEm: Int) {
        val pontos = (5 downTo 0).map { k ->
            val m = YearMonth.now().minusMonths(k.toLong())
            val comMovimento = k < pontosComMovimentoEm
            PontoMes(
                mes = m,
                entradas = if (comMovimento) 1_000_00 else 0,
                saidas = if (comMovimento) 600_00 else 0,
                sobrou = if (comMovimento) 400_00 else 0,
                reservaAcumulada = 0,
                taxaPoupanca = null,
            )
        }
        rule.setContent {
            SaldoTheme(darkTheme = false) {
                Column {
                    SegmentoTendencia(pontos, YearMonth.now(), onMes = {})
                }
            }
        }
    }

    @Test
    fun comUmMesSoATendenciaPedeMaisUmMes() {
        montarTendencia(pontosComMovimentoEm = 1)
        rule.onAllNodesWithTag(TAG_PRECISA_MAIS_UM_MES).assertCountEquals(2)
        rule.onAllNodesWithText("saídas").assertCountEquals(0)
    }

    @Test
    fun comDoisMesesOsGraficosVoltam() {
        montarTendencia(pontosComMovimentoEm = 2)
        rule.onAllNodesWithTag(TAG_PRECISA_MAIS_UM_MES).assertCountEquals(0)
        rule.onNodeWithText("saídas").assertIsDisplayed()
    }
}
