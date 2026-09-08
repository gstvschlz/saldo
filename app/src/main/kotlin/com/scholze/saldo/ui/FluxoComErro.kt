package com.scholze.saldo.ui

import android.util.Log
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart

/**
 * O molde de "erro de leitura vira estado", igual nas seis telas.
 *
 * Antes, cada ViewModel fazia `.catch { Log.e(...) }`: o `StateFlow` parava de emitir e a tela
 * ficava congelada no último frame — ou num retângulo vazio, se nada tinha chegado ainda — sem
 * crash, sem mensagem e sem saída. Aqui o erro vira um campo do estado e a tela mostra a linha e o
 * botão.
 *
 * O `flatMapLatest` sobre [tentativas] é o que faz o botão valer alguma coisa: cada toque **refaz**
 * a assinatura de [fonte] (`repo.ledger`, `repo.tags`, o que for), em vez de só apagar a mensagem
 * de uma tela que continuaria morta. O `catch` fica DENTRO da lambda, para o fluxo externo continuar
 * vivo e aceitar a tentativa seguinte.
 *
 * @param inicial o estado antes de qualquer emissão — o mesmo que vai no `stateIn`.
 * @param marcarErro põe a mensagem no estado (`{ it.copy(erro = MENSAGEM_ERRO_LEITURA) }`).
 * @param limparErro tira a mensagem (`{ it.copy(erro = null) }`) — emitido ao (re)assinar, para o
 *   toque no botão ter efeito visível na hora, antes de o banco responder.
 * @param rotulo o nome do fluxo no log ("fluxo do ledger").
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun <T> fluxoComErro(
    tentativas: Flow<Int>,
    inicial: T,
    marcarErro: (T) -> T,
    limparErro: (T) -> T,
    rotulo: String,
    fonte: () -> Flow<T>,
): Flow<T> {
    var ultimo = inicial
    return tentativas.flatMapLatest {
        fonte()
            .onEach { ultimo = it }
            .onStart { emit(limparErro(ultimo)) }
            .catch { e ->
                Log.e("saldo", "$rotulo falhou", e)
                emit(marcarErro(ultimo))
            }
    }
}
