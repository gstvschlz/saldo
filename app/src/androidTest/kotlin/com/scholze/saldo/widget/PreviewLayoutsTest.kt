package com.scholze.saldo.widget

import android.content.res.Configuration
import android.content.res.Resources
import android.view.LayoutInflater
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.scholze.saldo.R
import com.scholze.saldo.ui.theme.DarkSaldoColors
import com.scholze.saldo.ui.theme.LightSaldoColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * As prévias do seletor de widgets, e o espelho de cores que elas obrigam a existir.
 *
 * O widget de verdade é desenhado pelo Glance a partir de `CoresWidget`, que lê os tokens em
 * Kotlin. A prévia é uma view clássica inflada pelo LAUNCHER, que não roda o nosso código — então
 * os mesmos valores vivem também em `res/values/colors.xml` e `res/values-night/colors.xml`.
 *
 * Duas fontes para a mesma cor divergem em silêncio na primeira mudança de tema. Este teste é o
 * que faz a divergência falhar em vez de aparecer no aparelho de alguém.
 */
@RunWith(AndroidJUnit4::class)
class PreviewLayoutsTest {

    private val contexto = InstrumentationRegistry.getInstrumentation().targetContext

    /** Os oito layouts, na ordem em que os widgets aparecem no seletor. */
    private val previas = listOf(
        R.layout.preview_saldo,
        R.layout.preview_acaminho,
        R.layout.preview_paraondefoi,
        R.layout.preview_lancar,
        R.layout.preview_board,
        R.layout.preview_ritmo,
        R.layout.preview_poupanca,
        R.layout.preview_teto,
    )

    /**
     * Um [Resources] preso a um modo. `createConfigurationContext` é o único jeito de ler o
     * `values-night` sem trocar o tema do aparelho no meio da suíte.
     */
    private fun recursos(noite: Boolean): Resources {
        val cfg = Configuration(contexto.resources.configuration)
        cfg.uiMode = (cfg.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
            (if (noite) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO)
        return contexto.createConfigurationContext(cfg).resources
    }

    private fun cor(res: Resources, id: Int): Int = res.getColor(id, null)

    @Test
    fun asOitoPreviasInflamSemExplodir() {
        val inflater = LayoutInflater.from(contexto)
        previas.forEach { assertNotNull(inflater.inflate(it, null)) }
        assertEquals(8, previas.size)
    }

    @Test
    fun oEspelhoClaroBateComOsTokensDoTemaClaro() = confere(recursos(noite = false), LightSaldoColors)

    @Test
    fun oEspelhoEscuroBateComOsTokensDoTemaEscuro() = confere(recursos(noite = true), DarkSaldoColors)

    private fun confere(res: Resources, tema: com.scholze.saldo.ui.theme.SaldoColors) {
        val pares: List<Triple<String, Int, Color>> = listOf(
            Triple("widget_fundo", R.color.widget_fundo, tema.surface),
            Triple("widget_label", R.color.widget_label, tema.label),
            Triple("widget_secundario", R.color.widget_secundario, tema.secondaryLabel),
            Triple("widget_tint", R.color.widget_tint, tema.tint),
            Triple("widget_positivo", R.color.widget_positivo, tema.positive),
            Triple("widget_negativo", R.color.widget_negativo, tema.categoryVariable),
            Triple("widget_container", R.color.widget_container, tema.primaryContainer),
            Triple("widget_sobre_container", R.color.widget_sobre_container, tema.onPrimaryContainer),
            Triple("widget_trilha", R.color.widget_trilha, tema.insightSemTag),
            Triple("widget_outras", R.color.widget_outras, tema.insightOutras),
            Triple("widget_board_neg3", R.color.widget_board_neg3, tema.boardNeg3),
            Triple("widget_board_neg2", R.color.widget_board_neg2, tema.boardNeg2),
            Triple("widget_board_neg1", R.color.widget_board_neg1, tema.boardNeg1),
            Triple("widget_board_zero", R.color.widget_board_zero, tema.boardZero),
            Triple("widget_board_pos1", R.color.widget_board_pos1, tema.boardPos1),
            Triple("widget_board_pos2", R.color.widget_board_pos2, tema.boardPos2),
            Triple("widget_board_pos3", R.color.widget_board_pos3, tema.boardPos3),
        )
        pares.forEach { (nome, id, esperada) ->
            assertEquals(
                "$nome saiu do lugar: o XML e Color.kt discordam",
                esperada.toArgb(),
                cor(res, id),
            )
        }
    }
}
