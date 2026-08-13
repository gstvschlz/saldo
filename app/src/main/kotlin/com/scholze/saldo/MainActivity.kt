package com.scholze.saldo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.scholze.saldo.ui.SaldoApp
import com.scholze.saldo.ui.theme.SaldoTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SaldoTheme {
                SaldoApp()
            }
        }
    }
}
