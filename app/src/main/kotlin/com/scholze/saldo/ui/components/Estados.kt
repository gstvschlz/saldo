package com.scholze.saldo.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.scholze.saldo.ui.theme.SaldoTheme
import kotlinx.coroutines.delay

const val TAG_ERRO_LEITURA = "estados:erro"
const val TAG_CARREGANDO = "estados:carregando"

/** A frase única do erro de leitura — as seis telas dizem a mesma coisa, com as mesmas palavras. */
const val MENSAGEM_ERRO_LEITURA = "algo deu errado ao ler os dados"

/**
 * O que uma tela mostra quando o fluxo do banco falhou.
 *
 * Antes disso, um erro de leitura virava `Log.e` e um retângulo vazio para sempre: o
 * `.catch` de um `StateFlow` para de emitir, e a tela congela sem crash que explique. Com um botão,
 * o usuário tem o que fazer — e "tentar de novo" reassina o fluxo de verdade (ver Task 11), não é
 * um placebo que só limpa a mensagem.
 */
@Composable
fun ErroDeLeitura(mensagem: String, onTentar: () -> Unit, modifier: Modifier = Modifier) {
    val colors = SaldoTheme.colors
    Column(
        modifier.fillMaxSize().testTag(TAG_ERRO_LEITURA).padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(mensagem, style = SaldoTheme.type.body, color = colors.secondaryLabel, textAlign = TextAlign.Center)
        FilledActionButton(text = "tentar de novo", onClick = onTentar)
    }
}

/**
 * O vazio de "ainda não chegou".
 *
 * Um retângulo pelos primeiros [atraso] ms e só depois o indicador: no aparelho rápido o primeiro
 * `LedgerInput` chega antes disso e ninguém vê um spinner piscar; no lento, o app diz que está vivo.
 */
@Composable
fun Carregando(modifier: Modifier = Modifier, atraso: Long = 300L) {
    var mostrar by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(atraso)
        mostrar = true
    }
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (mostrar) CircularProgressIndicator(Modifier.testTag(TAG_CARREGANDO), color = SaldoTheme.colors.tint)
    }
}
