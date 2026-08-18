package com.scholze.saldo.widget

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.testing.unit.assertHasText
import androidx.glance.testing.unit.hasTestTag
import androidx.glance.testing.unit.hasText
import com.scholze.saldo.ui.privacy.MASCARA_PRIVACIDADE
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Test

/**
 * Os três tipos novos, na JVM: cada conteúdo recebe um estado puro, sem Context nem repositório.
 *
 * O que estes testes NÃO cobrem é cor — o harness do Glance assere semântica (texto, testTag,
 * contentDescription). Foi exatamente por isso que o `+` branco sobre o tint claro passou
 * despercebido: a defesa contra isso é o `CoresWidget.sobreTint` num lugar só, não um teste.
 */
class WidgetsNovosTest {

    private val ago = YearMonth.of(2026, 8)

    // ---------------------------------------------------------------- a caminho

    private val aCaminho = ACaminhoEstado.Pronto(
        mes = ago,
        fimDoMes = LocalDate.parse("2026-08-31"),
        saemCentavos = 2_400_00,
        mesEncerrado = false,
        linhas = listOf(
            ACaminhoEstado.Linha(28, "aluguel", -2_400_00),
            ACaminhoEstado.Linha(31, "salário", 8_240_00),
        ),
        restantes = 0,
        mostrarValores = true,
    )

    @Test
    fun aCaminhoMostraTotalPrazoELinhas() = runGlanceAppWidgetUnitTest {
        setAppWidgetSize(ACAMINHO)
        provideComposable { ACaminhoWidgetContent(aCaminho) }
        onNode(hasTestTag(TAG_ACAMINHO_TOTAL)).assertHasText("R$ 2.400,00")
        onNode(hasTestTag(TAG_ACAMINHO_PRAZO)).assertHasText("até 31 ago")
        onNode(hasText("aluguel")).assertExists()
        onNode(hasText("salário")).assertExists()
    }

    /** A máscara é só de dinheiro: a descrição continua visível, por decisão do usuário. */
    @Test
    fun aCaminhoMascaraOValorMasNaoADescricao() = runGlanceAppWidgetUnitTest {
        setAppWidgetSize(ACAMINHO)
        provideComposable { ACaminhoWidgetContent(aCaminho.copy(mostrarValores = false)) }
        onNode(hasTestTag(TAG_ACAMINHO_TOTAL)).assertHasText(MASCARA_PRIVACIDADE)
        onNode(hasText("aluguel")).assertExists()
    }

    @Test
    fun aCaminhoSemItensDizNadaAgendado() = runGlanceAppWidgetUnitTest {
        setAppWidgetSize(ACAMINHO)
        provideComposable { ACaminhoWidgetContent(aCaminho.copy(linhas = emptyList(), saemCentavos = 0)) }
        onNode(hasText("nada agendado até o fim do mês")).assertExists()
    }

    /** Mês encerrado não lista nada — nem o cabeçalho de total, que não teria sentido. */
    @Test
    fun aCaminhoComMesEncerradoSoDizIsso() = runGlanceAppWidgetUnitTest {
        setAppWidgetSize(ACAMINHO)
        provideComposable { ACaminhoWidgetContent(aCaminho.copy(mesEncerrado = true)) }
        onNode(hasText("mês encerrado")).assertExists()
        onNode(hasTestTag(TAG_ACAMINHO_TOTAL)).assertDoesNotExist()
    }

    @Test
    fun aCaminhoContaOQueNaoCoube() = runGlanceAppWidgetUnitTest {
        setAppWidgetSize(ACAMINHO)
        provideComposable { ACaminhoWidgetContent(aCaminho.copy(restantes = 4)) }
        onNode(hasText("+4 depois")).assertExists()
    }

    @Test
    fun aCaminhoSemOnboardingConvida() = runGlanceAppWidgetUnitTest {
        setAppWidgetSize(ACAMINHO)
        provideComposable { ACaminhoWidgetContent(ACaminhoEstado.SemOnboarding) }
        onNode(hasText("toque para começar")).assertExists()
    }

    // ------------------------------------------------------------ para onde foi

    private val paraOndeFoi = ParaOndeFoiEstado.Pronto(
        mes = ago,
        saidasCentavos = 1_600_00,
        barra = listOf(
            ParaOndeFoiEstado.Segmento("comida", 0xFFA6486B, 700_00, 0.44f),
            ParaOndeFoiEstado.Segmento("moradia", 0xFFB95A2E, 600_00, 0.37f),
            ParaOndeFoiEstado.Segmento("sem tag", -1L, 300_00, 0.19f),
        ),
        fatias = listOf(
            ParaOndeFoiEstado.Segmento("comida", 0xFFA6486B, 700_00, 0.44f),
            ParaOndeFoiEstado.Segmento("moradia", 0xFFB95A2E, 600_00, 0.37f),
        ),
        mostrarValores = true,
    )

    @Test
    fun paraOndeFoiMostraTotalEFatias() = runGlanceAppWidgetUnitTest {
        setAppWidgetSize(PARA_ONDE_FOI)
        provideComposable { ParaOndeFoiWidgetContent(paraOndeFoi) }
        onNode(hasTestTag(TAG_PARAONDEFOI_TOTAL)).assertHasText("R$ 1.600,00")
        onNode(hasText("comida")).assertExists()
        onNode(hasText("moradia")).assertExists()
    }

    /** As fatias entram como magnitude positiva e a linha mostra saída: o sinal é do desenho. */
    @Test
    fun paraOndeFoiMostraAsFatiasComoSaida() = runGlanceAppWidgetUnitTest {
        setAppWidgetSize(PARA_ONDE_FOI)
        provideComposable { ParaOndeFoiWidgetContent(paraOndeFoi) }
        onNode(hasText("−700,00")).assertExists()
    }

    @Test
    fun paraOndeFoiMascaraOsValoresMasNaoOsNomes() = runGlanceAppWidgetUnitTest {
        setAppWidgetSize(PARA_ONDE_FOI)
        provideComposable { ParaOndeFoiWidgetContent(paraOndeFoi.copy(mostrarValores = false)) }
        onNode(hasTestTag(TAG_PARAONDEFOI_TOTAL)).assertHasText(MASCARA_PRIVACIDADE)
        onNode(hasText("comida")).assertExists()
    }

    @Test
    fun paraOndeFoiSemSaidasDizIsso() = runGlanceAppWidgetUnitTest {
        setAppWidgetSize(PARA_ONDE_FOI)
        provideComposable {
            ParaOndeFoiWidgetContent(paraOndeFoi.copy(barra = emptyList(), fatias = emptyList(), saidasCentavos = 0))
        }
        onNode(hasText("nenhuma saída neste mês")).assertExists()
    }

    // -------------------------------------------------------------------- lançar

    @Test
    fun lancarTemOsDoisLados() = runGlanceAppWidgetUnitTest {
        setAppWidgetSize(LANCAR)
        provideComposable { LancarWidgetContent(pronto = true) }
        onNode(hasTestTag(TAG_LANCAR_SAIDA)).assertExists()
        onNode(hasTestTag(TAG_LANCAR_ENTRADA)).assertExists()
    }

    @Test
    fun lancarAntesDoOnboardingSoConvida() = runGlanceAppWidgetUnitTest {
        setAppWidgetSize(LANCAR)
        provideComposable { LancarWidgetContent(pronto = false) }
        onNode(hasText("toque para começar")).assertExists()
        onNode(hasTestTag(TAG_LANCAR_SAIDA)).assertDoesNotExist()
    }

    private companion object {
        val ACAMINHO = DpSize(250.dp, 110.dp)
        val PARA_ONDE_FOI = DpSize(250.dp, 110.dp)
        val LANCAR = DpSize(110.dp, 40.dp)
    }
}
