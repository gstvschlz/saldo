package com.scholze.saldo.ui.totais

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.scholze.saldo.domain.Fatura
import com.scholze.saldo.domain.Natureza
import com.scholze.saldo.domain.Tag
import com.scholze.saldo.domain.TotaisMes
import com.scholze.saldo.ui.privacy.LocalPrivacy
import com.scholze.saldo.ui.privacy.MASCARA_PRIVACIDADE
import com.scholze.saldo.ui.privacy.PrivacyState
import com.scholze.saldo.ui.theme.SaldoTheme
import java.time.LocalDate
import java.time.YearMonth
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

    private fun montar(state: TotaisUiState, oculto: Boolean = false) {
        rule.setContent {
            SaldoTheme(darkTheme = false) {
                CompositionLocalProvider(LocalPrivacy provides PrivacyState(ocultoInicial = oculto)) {
                    TotaisContent(state, {}, {})
                }
            }
        }
    }

    @Test
    fun mostraPerformanceEBlocos() {
        montar(TotaisUiState(YearMonth.of(2026, 7), totais))

        rule.onNodeWithText("totais · jul/26").assertIsDisplayed()
        rule.onNodeWithText("sobrou dinheiro").assertIsDisplayed()
        rule.onNodeWithText("+5.440,00").assertIsDisplayed()
        rule.onNodeWithText("+8.240,00").assertIsDisplayed()
        // As saídas chegam positivas do engine e têm de sair com sinal na tela.
        rule.onNodeWithText("−1.000,00").assertIsDisplayed()
        rule.onNodeWithText("reserva acumulada").assertIsDisplayed()
        rule.onNodeWithText("+12.000,00").assertIsDisplayed()
        rule.onNodeWithText("fecha em").assertIsDisplayed()
        rule.onNodeWithText("28 jul").assertIsDisplayed()
        rule.onNodeWithText("comida").assertIsDisplayed()
        rule.onNodeWithText("−700,00").assertIsDisplayed()
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
        montar(TotaisUiState(YearMonth.of(2026, 7), totais), oculto = true)
        // Os rótulos ficam; todo número passa por MoneyText e vira máscara.
        rule.onNodeWithText("reserva acumulada").assertIsDisplayed()
        rule.onNodeWithText("+5.440,00").assertDoesNotExist()
        rule.onAllNodesWithText(MASCARA_PRIVACIDADE).onFirst().assertIsDisplayed()
    }
}
