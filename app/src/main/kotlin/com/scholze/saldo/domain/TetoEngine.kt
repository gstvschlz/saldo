package com.scholze.saldo.domain

import java.time.YearMonth

/**
 * O teto do dia: quanto ainda sobra do mês, dividido pelos dias que faltam.
 *
 * É o oposto de [ProjectionEngine.mediaDiaria], que descreve o que o usuário TEM gasto. Este
 * número é prescritivo — o que ele PODE gastar —, e por isso sai da renda do mês e não do
 * histórico.
 *
 * [tetoCentavos] é fixado à meia-noite: o gasto de hoje não o move, só desce de
 * [restaCentavos]. Um furo de hoje só se dilui pelos dias restantes amanhã, quando a conta
 * roda de novo — é o que faz o número responder "ainda posso?" em vez de "quanto o mês
 * comportava".
 */
data class Teto(
    val mes: YearMonth,
    /** O que o dia comporta, fixado à meia-noite. Negativo num mês que já estourou. */
    val tetoCentavos: Long,
    /** O que já saiu hoje em gasto avulso — cartão contado no dia da compra. */
    val gastoDeHojeCentavos: Long,
    /** De hoje ao fim do mês, **hoje incluído**. No dia 31 é 1, nunca 0. */
    val diasRestantes: Int,
    /** O que sobra do mês depois do que já saiu hoje; é [tetoCentavos] × dias, menos o de hoje. */
    val sobraDoMesCentavos: Long,
) {
    /** O que ainda cabe hoje. Fica negativo assim que o dia estoura, e é para ficar. */
    val restaCentavos: Long get() = tetoCentavos - gastoDeHojeCentavos

    /** O dia já estourou o que comportava. */
    val estourouODia: Boolean get() = restaCentavos < 0

    /** O mês inteiro já estourou — não há teto positivo nenhum para dividir. */
    val estourouOMes: Boolean get() = tetoCentavos < 0
}

/**
 * O motor do teto.
 *
 * Conta **só o mês corrente**: "hoje" não existe em setembro visto de outubro, e um teto de um
 * mês que já fechou não é um teto, é um histórico.
 *
 * Uma compra no cartão pesa **no dia da compra**, como no ritmo e no "para onde foi" — e é por
 * isso que a fatura nunca entra na conta: as compras dela já foram cobradas uma a uma, e as
 * recorrências de cartão já saíram em `fixas`. Somá-la de novo cobraria o mesmo dinheiro duas
 * vezes.
 */
object TetoEngine {

    /**
     * O teto de hoje, ou `null` quando o mês não teve entrada nenhuma.
     *
     * Nulo e não zero, pela mesma razão de [MesLedger.taxaGuardada]: sem renda não há proporção
     * a fazer, e um `R$ 0,00` na tela leria como "você não pode gastar nada" quando a verdade é
     * "não sei dizer". Quem mostra some com a linha.
     *
     * [metaGuardarPercent] é o piso da reserva, não o teto dela: quem já guardou mais do que a
     * meta tem o excedente descontado também, porque o dinheiro saiu de fato. `0` — sem meta —
     * cai sozinho na economia real.
     */
    fun teto(input: LedgerInput, metaGuardarPercent: Int): Teto? {
        val mes = YearMonth.from(input.hoje)
        val doMes = ProjectionEngine.movimentacoesDoMes(input, mes)

        // Mês inteiro, não até hoje: o salário do dia 5 conta desde o dia 1, e uma fixa que
        // ainda não venceu já está comprometida. É a expansão das recorrências que permite isto.
        val renda = doMes
            .filter { it.natureza != Natureza.CARTAO && it.valorCentavos > 0 }
            .sumOf { it.valorCentavos }
        if (renda <= 0) return null

        // ECONOMIA fica de fora: ela vira `reserva` logo abaixo. Contá-la aqui e lá removeria o
        // mesmo dinheiro duas vezes — uma transferência mensal para a poupança é exatamente a
        // linha que cai nas duas peneiras.
        val fixas = -doMes
            .filter { it.recorrenciaId != null && it.natureza != Natureza.ECONOMIA && it.valorCentavos < 0 }
            .sumOf { it.valorCentavos }

        // Só as saídas, como em `ProjectionEngine.taxaGuardada`: um resgate da poupança não
        // devolve reserva, ele já entrou em `renda`.
        val economia = -doMes
            .filter { it.natureza == Natureza.ECONOMIA && it.valorCentavos < 0 }
            .sumOf { it.valorCentavos }

        val reserva = maxOf(renda * metaGuardarPercent / 100, economia)

        // O gasto do dia a dia: sem recorrência, DIARIO ou CARTAO, cartão no dia da compra.
        // ECONOMIA avulsa não está aqui — guardar dinheiro não come o teto, empurra a reserva.
        val avulso = doMes.filter {
            it.recorrenciaId == null && it.valorCentavos < 0 &&
                (it.natureza == Natureza.DIARIO || it.natureza == Natureza.CARTAO)
        }
        val avulsoAteOntem = -avulso.filter { it.data < input.hoje }.sumOf { it.valorCentavos }
        val gastoDeHoje = -avulso.filter { it.data == input.hoje }.sumOf { it.valorCentavos }

        val sobraAoAmanhecer = renda - fixas - reserva - avulsoAteOntem
        val diasRestantes = mes.lengthOfMonth() - input.hoje.dayOfMonth + 1

        return Teto(
            mes = mes,
            // Divisão de Long trunca para zero, então o teto positivo é conservador (teto × dias
            // nunca passa da sobra) e o negativo preserva o sinal, que é o que a tela mostra.
            tetoCentavos = sobraAoAmanhecer / diasRestantes,
            gastoDeHojeCentavos = gastoDeHoje,
            diasRestantes = diasRestantes,
            sobraDoMesCentavos = sobraAoAmanhecer - gastoDeHoje,
        )
    }
}
