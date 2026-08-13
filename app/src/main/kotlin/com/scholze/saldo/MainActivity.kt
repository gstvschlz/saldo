package com.scholze.saldo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.scholze.saldo.data.Tema
import com.scholze.saldo.ui.SaldoApp
import com.scholze.saldo.ui.privacy.LocalPrivacy
import com.scholze.saldo.ui.privacy.rememberPrivacyState
import com.scholze.saldo.ui.theme.SaldoTheme

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
            CompositionLocalProvider(LocalPrivacy provides privacidade) {
                SaldoTheme(darkTheme = escuro) {
                    SaldoApp(container)
                }
            }
        }
    }
}
