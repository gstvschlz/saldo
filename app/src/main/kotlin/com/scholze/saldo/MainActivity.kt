package com.scholze.saldo

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.scholze.saldo.data.Tema
import com.scholze.saldo.ui.SaldoApp
import com.scholze.saldo.ui.privacy.LocalPrivacy
import com.scholze.saldo.ui.privacy.rememberPrivacyState
import com.scholze.saldo.ui.theme.SaldoTheme

/** Os scrims que o `enableEdgeToEdge` sem argumentos usa por baixo dos panos. */
private val LIGHT_SCRIM = Color.argb(0xe6, 0xFF, 0xFF, 0xFF)
private val DARK_SCRIM = Color.argb(0x80, 0x1b, 0x1b, 0x1b)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as SaldoApplication).container
        setContent {
            val settings by container.settings.settings.collectAsState(initial = null)
            val s = settings ?: return@setContent
            val privacidade = rememberPrivacyState(ocultoInicial = s.comecarOculto)
            val escuro = when (s.tema) {
                Tema.SISTEMA -> isSystemInDarkTheme()
                Tema.CLARO -> false
                Tema.ESCURO -> true
            }
            // O `enableEdgeToEdge()` do onCreate decide a cor dos ícones da status bar pelo
            // dark mode DO SISTEMA, uma vez só. Com o tema do app escolhido em "mais", um
            // "escuro" sobre sistema claro deixava relógio e bateria pretos sobre preto —
            // invisíveis. Reaplicar a cada mudança de `escuro` amarra os dois.
            LaunchedEffect(escuro) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { escuro },
                    navigationBarStyle = SystemBarStyle.auto(LIGHT_SCRIM, DARK_SCRIM) { escuro },
                )
            }
            CompositionLocalProvider(LocalPrivacy provides privacidade) {
                SaldoTheme(darkTheme = escuro) {
                    SaldoApp(container, s)
                }
            }
        }
    }
}
