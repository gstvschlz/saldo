package com.scholze.saldo.ui.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChangeIgnoreConsumed
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/**
 * Trocar de mês arrastando para os lados, cedendo a vez aos filhos.
 *
 * Um `detectHorizontalDragGestures` na raiz **compete** com o `SwipeToDismissBox` de cada
 * linha: num arrasto real (não no `swipeLeft()` sintético dos testes) a raiz costumava ganhar
 * a corrida do touch slop, engolir o gesto e trocar o mês em vez de excluir a linha.
 *
 * O loop abaixo roda no pass Main, que num nó pai chega DEPOIS dos filhos: se a linha (ou a
 * rolagem da lista) já consumiu movimento, `alheio` fecha a porta e o mês não muda. Só o
 * consumo de MOVIMENTO conta — `clickable` consome o down para marcar o press, e isso não
 * pode valer como "alguém pegou o gesto".
 *
 * Quando é a raiz que assume, ela consome: sem isso o `clickable` do hero, que não tem slop
 * nenhum, dispararia junto e alternaria a privacidade no fim do arrasto.
 *
 * [chave] reinicia o reconhecedor quando o mês muda, como qualquer `pointerInput`.
 */
fun Modifier.arrastoDeMes(
    chave: Any?,
    onMesAnterior: () -> Unit,
    onProximoMes: () -> Unit,
): Modifier = this.pointerInput(chave) {
    val limiar = 120.dp.toPx()
    val slop = viewConfiguration.touchSlop
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        var totalX = 0f
        var totalY = 0f
        var meu = false
        var alheio = false
        while (true) {
            val evento = awaitPointerEvent()
            val mudanca = evento.changes.firstOrNull() ?: break
            val delta = mudanca.positionChangeIgnoreConsumed()
            if (!meu && mudanca.isConsumed && delta != Offset.Zero) alheio = true
            if (!alheio) {
                totalX += delta.x
                totalY += delta.y
                // Predominantemente horizontal, senão uma rolagem na diagonal sobre a
                // coluna de dias viraria troca de mês.
                if (!meu && abs(totalX) > slop && abs(totalX) > abs(totalY)) meu = true
                if (meu) mudanca.consume()
            }
            if (!mudanca.pressed) break
        }
        if (meu) {
            if (totalX > limiar) onMesAnterior() else if (totalX < -limiar) onProximoMes()
        }
    }
}
