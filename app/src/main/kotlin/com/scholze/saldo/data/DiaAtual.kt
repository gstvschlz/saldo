package com.scholze.saldo.data

import java.time.LocalDate
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * "Hoje" como fluxo: emite a data ao ser coletado e de novo só quando o dia vira.
 *
 * O ledger recebe `hoje` daqui em vez de ler o relógio a cada emissão do banco — o app aberto
 * atravessando a meia-noite (na frente ou vivo em segundo plano) ficava com o "hoje" da véspera:
 * linha errada destacada, estimativa contando um dia a mais, até que alguma escrita disparasse
 * o `combine`. Checa o relógio a cada [intervalo] em vez de dormir até a meia-noite porque um
 * `delay` longo não conta o tempo em que o aparelho dormiu e acordaria horas depois da virada.
 */
fun diaAtual(relogio: () -> LocalDate = LocalDate::now, intervalo: Duration = 1.minutes): Flow<LocalDate> = flow {
    var ultimo = relogio()
    emit(ultimo)
    while (true) {
        delay(intervalo)
        val agora = relogio()
        if (agora != ultimo) {
            ultimo = agora
            emit(agora)
        }
    }
}
