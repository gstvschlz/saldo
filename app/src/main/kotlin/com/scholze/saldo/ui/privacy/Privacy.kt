package com.scholze.saldo.ui.privacy

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.scholze.saldo.model.centavosAssinado
import com.scholze.saldo.model.centavosComSimbolo
import com.scholze.saldo.model.centavosValor
import com.scholze.saldo.ui.theme.tabular

const val MASCARA_PRIVACIDADE = "R$ •••••"

/** Session-scoped visibility of money values. */
class PrivacyState(ocultoInicial: Boolean) {
    var oculto by mutableStateOf(ocultoInicial)
        private set

    fun alternar() { oculto = !oculto }
}

val LocalPrivacy = staticCompositionLocalOf { PrivacyState(ocultoInicial = false) }

enum class FormatoMoney { VALOR, COM_SIMBOLO, ASSINADO }

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
