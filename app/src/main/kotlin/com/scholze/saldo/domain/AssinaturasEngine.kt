package com.scholze.saldo.domain

import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.abs

/**
 * Uma avulsa que se repete todo mês e ainda não foi cadastrada como recorrência.
 *
 * Não é um dado gravado: é uma leitura do que já está no banco, recalculada a cada emissão do
 * ledger. Nada aqui existe até o usuário tocar em "tornar mensal".
 */
data class Assinatura(
    /** `Busca.normalizar(descricao)` — a chave do agrupamento, e a que `dispensar` guarda. */
    val chave: String,
    /** Como a descrição aparece na ÚLTIMA ocorrência: é o texto que o usuário viu por último. */
    val descricao: String,
    /** O valor da última ocorrência — o preço de hoje, não a média do período. Negativo. */
    val valorCentavos: Long,
    /** O valor da penúltima, e só quando a última mudou mais de 1%: é o "subiu de … para …". */
    val valorAnteriorCentavos: Long?,
    /** A mediana dos dias do mês — é o dia que "tornar mensal" grava no template. */
    val diaDoMes: Int,
    /** O tamanho da sequência de meses seguidos que chega até agora. */
    val meses: Int,
    /** A linha REAL mais recente; `converterEmRecorrencia` parte dela e exige `id != 0`. */
    val ocorrenciaMaisRecente: Movimentacao,
)

/**
 * "Isto parece uma assinatura." Puro e determinístico como os outros motores: mesma entrada, mesma
 * saída, sem relógio próprio (o `hoje` chega por parâmetro) e sem tocar em nada.
 *
 * O critério inteiro está na decisão 10 do spec de `arrumacao-1`, e é este:
 * sobre as **avulsas** (`recorrenciaId == null`) de **saída** (`valorCentavos < 0`) com descrição
 * não vazia, agrupadas por [Busca.normalizar], nos **seis meses fechados mais o corrente** —
 * é candidata quando há **uma ocorrência por mês** em **três meses consecutivos ou mais**, com a
 * sequência **chegando até o mês corrente ou o anterior**, com **valores dentro de ±10% da
 * mediana** (assinatura reajusta) e **dias dentro de ±5 da mediana** (a cobrança cai em dia útil
 * e anda um pouco).
 */
object AssinaturasEngine {

    /** Quantos meses fechados entram na janela, além do corrente. */
    private const val MESES_DE_JANELA = 6L

    /** Quantos meses seguidos bastam para a coisa parecer assinatura. */
    private const val MESES_MINIMOS = 3

    /** O teto da lista: a seção é um empurrão, não uma caixa de entrada (decisão 13). */
    private const val MAXIMO = 3

    fun candidatas(input: LedgerInput, hoje: LocalDate, dispensadas: Set<String>): List<Assinatura> {
        val mesCorrente = YearMonth.from(hoje)
        val inicio = mesCorrente.minusMonths(MESES_DE_JANELA)

        // As LINHAS do banco, não as efetivas: uma ocorrência virtual sempre tem `recorrenciaId`,
        // então nunca seria avulsa, e `converterEmRecorrencia` exige `id != 0` — que só linha real
        // tem. Ler as efetivas custaria a expansão inteira e não traria nada.
        return input.movimentacoes
            .asSequence()
            .filter { it.recorrenciaId == null && it.valorCentavos < 0 }
            .filter { YearMonth.from(it.data) in inicio..mesCorrente }
            .mapNotNull { m -> Busca.normalizar(m.descricao).takeIf { it.isNotEmpty() }?.let { it to m } }
            // As dispensadas saem ANTES de qualquer conta: o usuário já respondeu esta pergunta.
            .filter { (chave, _) -> chave !in dispensadas }
            .groupBy({ it.first }, { it.second })
            .mapNotNull { (chave, ocorrencias) -> assinatura(chave, ocorrencias, mesCorrente) }
            // Maior valor primeiro; a chave desempata só para a lista ser determinística.
            .sortedWith(compareByDescending<Assinatura> { abs(it.valorCentavos) }.thenBy { it.chave })
            .take(MAXIMO)
    }

    private fun assinatura(
        chave: String,
        ocorrencias: List<Movimentacao>,
        mesCorrente: YearMonth,
    ): Assinatura? {
        // Uma cobrança por mês. Duas no mesmo mês derrubam a candidata inteira — é o
        // discriminador mais barato que existe contra o falso positivo óbvio: iFood, posto e
        // mercado saem várias vezes no mesmo mês (decisão 9).
        val porMes = ocorrencias.groupBy { YearMonth.from(it.data) }
        if (porMes.values.any { it.size != 1 }) return null

        // A sequência tem de CHEGAR até agora — mês corrente ou o anterior. Uma cobrança que
        // apareceu em março, abril e maio e sumiu desde então não é uma assinatura a cadastrar:
        // é uma que o usuário cancelou, e "tornar mensal" nela criaria uma recorrência de algo
        // que não existe mais. O mês anterior entra porque a cobrança deste mês pode ainda não
        // ter caído.
        val mesesOrdenados = porMes.keys.sorted()
        if (mesesOrdenados.last() < mesCorrente.minusMonths(1)) return null

        // E é essa sequência — a que chega até agora — que conta, não a maior da janela: quatro
        // meses seguidos que pararam em junho não viram "4 meses" ao lado de duas cobranças
        // recentes.
        val meses = sequenciaFinal(mesesOrdenados)
        if (meses < MESES_MINIMOS) return null

        // ±10% da mediana, em inteiros: `v/mediana in [0,9 .. 1,1]` vira `v*10` contra
        // `mediana*9` e `mediana*11`, sem ponto flutuante nenhum perto de dinheiro.
        val valores = ocorrencias.map { abs(it.valorCentavos) }
        val medianaValor = mediana(valores)
        if (valores.any { it * 10 < medianaValor * 9 || it * 10 > medianaValor * 11 }) return null

        // ±5 dias, sem circularidade: dia 1 e dia 30 são 29 dias de distância, não 2 — e é isso
        // que se quer, porque uma cobrança que pula de ponta a ponta do mês não é uma assinatura.
        val dias = ocorrencias.map { it.data.dayOfMonth.toLong() }
        val medianaDia = mediana(dias)
        if (dias.any { abs(it - medianaDia) > 5 }) return null

        // Por data, e o id só desempata: duas linhas no mesmo dia já foram descartadas acima, mas
        // a ordenação não pode depender da ordem em que o banco devolveu as linhas.
        val ordenadas = ocorrencias.sortedWith(compareBy({ it.data }, { it.id }))
        val ultima = ordenadas.last()
        val penultima = ordenadas[ordenadas.size - 2]
        val u = abs(ultima.valorCentavos)
        val p = abs(penultima.valorCentavos)
        return Assinatura(
            chave = chave,
            descricao = ultima.descricao,
            valorCentavos = ultima.valorCentavos,
            // Mais de 1% de diferença: abaixo disso é arredondamento, não reajuste, e "subiu de
            // R$ 39,90 para R$ 39,95" é ruído com cara de notícia.
            valorAnteriorCentavos = penultima.valorCentavos.takeIf { abs(u - p) * 100 > p },
            diaDoMes = medianaDia.toInt(),
            meses = meses,
            ocorrenciaMaisRecente = ultima,
        )
    }

    /**
     * Quantos meses seguidos terminam no ÚLTIMO mês de [meses] (já ordenado e sem repetição).
     *
     * Não é a maior sequência da janela: é a que continua até agora, a única que responde "isto
     * ainda está sendo cobrado".
     */
    private fun sequenciaFinal(meses: List<YearMonth>): Int {
        var n = 1
        for (i in meses.size - 1 downTo 1) {
            if (meses[i - 1].plusMonths(1) != meses[i]) break
            n++
        }
        return n
    }

    /** Mediana de uma lista não vazia; com contagem par, a média inteira dos dois do meio. */
    private fun mediana(valores: List<Long>): Long {
        val s = valores.sorted()
        val n = s.size
        return if (n % 2 == 1) s[n / 2] else (s[n / 2 - 1] + s[n / 2]) / 2
    }
}
