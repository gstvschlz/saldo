package com.scholze.saldo.ui.privacy

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.scholze.saldo.ui.money.centavosAssinado
import com.scholze.saldo.ui.money.centavosAssinadoComSimbolo
import com.scholze.saldo.ui.money.centavosComSimbolo
import com.scholze.saldo.ui.money.centavosValor
import com.scholze.saldo.ui.theme.tabular

const val MASCARA_PRIVACIDADE = "R$ •••••"

/** Visibilidade dos valores. Escolha de sessão: sobrevive à rotação, não a uma abertura nova. */
class PrivacyState(ocultoInicial: Boolean) {
    var oculto by mutableStateOf(ocultoInicial)
        private set

    fun alternar() { oculto = !oculto }

    companion object {
        val Saver: Saver<PrivacyState, Boolean> = Saver(save = { it.oculto }, restore = { PrivacyState(it) })
    }
}

val LocalPrivacy = staticCompositionLocalOf { PrivacyState(ocultoInicial = false) }

/**
 * Sobrevive à rotação e à morte do processo com a activity viva: esconder é uma decisão, e
 * girar o aparelho não pode desfazê-la. [ocultoInicial] só é lido na primeira composição.
 */
@Composable
fun rememberPrivacyState(ocultoInicial: Boolean): PrivacyState =
    rememberSaveable(saver = PrivacyState.Saver) { PrivacyState(ocultoInicial) }

enum class FormatoMoney { VALOR, COM_SIMBOLO, ASSINADO, ASSINADO_COM_SIMBOLO }

/** Every money value in the app renders through here — masking is centralized. */
@Composable
fun MoneyText(
    centavos: Long,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    formato: FormatoMoney = FormatoMoney.COM_SIMBOLO,
    fontWeight: FontWeight? = null,
    textAlign: TextAlign? = null,
) {
    val texto = if (LocalPrivacy.current.oculto) {
        MASCARA_PRIVACIDADE
    } else {
        when (formato) {
            FormatoMoney.VALOR -> centavos.centavosValor()
            FormatoMoney.COM_SIMBOLO -> centavos.centavosComSimbolo()
            FormatoMoney.ASSINADO -> centavos.centavosAssinado()
            FormatoMoney.ASSINADO_COM_SIMBOLO -> centavos.centavosAssinadoComSimbolo()
        }
    }
    Text(
        text = texto,
        modifier = modifier,
        style = style.tabular,
        color = color,
        fontWeight = fontWeight,
        textAlign = textAlign,
    )
}
