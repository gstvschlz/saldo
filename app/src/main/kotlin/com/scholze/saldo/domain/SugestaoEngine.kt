package com.scholze.saldo.domain

/**
 * O que fazer com uma notificação que trouxe um valor.
 *
 * Duas defesas contra lançar a mesma compra duas vezes, e a ordem entre elas importa. A
 * **janela** pega o caso barulhento — o banco repostando o mesmo aviso, a loja avisando
 * junto — e vence primeiro por ser a mais específica: se já existe uma sugestão viva para
 * esta compra, o certo é atualizá-la, não perguntar de novo. O **ledger** pega o caso lento:
 * o valor já foi lançado hoje, à mão ou por uma sugestão anterior, e aí a pergunta muda de
 * "lançar?" para "lançar mesmo assim?".
 */
object SugestaoEngine {

    /** Dez minutos. Duas notificações do mesmo app com o mesmo valor aqui dentro são a mesma compra. */
    const val JANELA_MILLIS = 10 * 60 * 1000L

    fun avaliar(
        candidata: Deteccao,
        config: CapturaConfig,
        recentes: List<Deteccao>,
        valoresDeHojeCentavos: List<Long>,
    ): Sugestao {
        if (!config.ligada) return Sugestao.Ignorar
        if (candidata.pacote !in config.marcados) return Sugestao.Ignorar
        if (candidata.centavos <= 0) return Sugestao.Ignorar

        val naJanela = recentes.firstOrNull {
            it.pacote == candidata.pacote &&
                it.centavos == candidata.centavos &&
                candidata.emMillis - it.emMillis in 0..JANELA_MILLIS
        }
        if (naJanela != null) {
            // Já resolvida quer dizer que o usuário decidiu. Não se ressuscita uma decisão.
            return if (naJanela.resolvida) Sugestao.Ignorar else Sugestao.Repetida(naJanela)
        }

        return if (valoresDeHojeCentavos.any { it == candidata.centavos }) {
            Sugestao.JaLancado(candidata)
        } else {
            Sugestao.Nova(candidata)
        }
    }
}
