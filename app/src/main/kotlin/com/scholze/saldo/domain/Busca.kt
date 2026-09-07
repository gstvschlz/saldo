package com.scholze.saldo.domain

import java.text.Normalizer
import java.time.LocalDate
import kotlin.math.abs

/**
 * A busca do ledger: descrição, nome da tag ou valor, sobre as movimentações *efetivas* (as
 * linhas do banco mais as ocorrências virtuais) até hoje. Só fato consumado, como o board.
 *
 * Uma consulta só de dígitos (com vírgula ou ponto opcional) é lida como dinheiro dos dois
 * jeitos — "340" acha R$ 340,00, e "1690" acha tanto R$ 1.690,00 quanto R$ 16,90 —, porque
 * quem digita um número lembra do valor, não de como o teclado o formatou.
 */
object Busca {

    private val marcas = Regex("\\p{M}+")
    // Depois do separador: 1-2 dígitos são centavos ("16,90"); 3 são milhar ("1.690" = 1690).
    private val dinheiro = Regex("^\\d{1,9}([.,]\\d{1,3})?$")

    fun normalizar(s: String): String =
        Normalizer.normalize(s.trim().lowercase(), Normalizer.Form.NFD).replace(marcas, "")

    fun filtrar(efetivas: List<Movimentacao>, consulta: String, hoje: LocalDate): List<Movimentacao> {
        val q = normalizar(consulta)
        if (q.isEmpty()) return emptyList()
        val centavos = centavosDe(q)
        return efetivas
            .asSequence()
            .filter { it.data <= hoje }
            .filter { m ->
                normalizar(m.descricao).contains(q) ||
                    m.tags.any { normalizar(it.nome).contains(q) } ||
                    (centavos.isNotEmpty() && abs(m.valorCentavos) in centavos)
            }
            .sortedByDescending { it.data }
            .toList()
    }

    /** Os valores em centavos que uma consulta numérica pode significar; vazio se não é número. */
    internal fun centavosDe(q: String): Set<Long> {
        if (!dinheiro.matches(q)) return emptySet()
        val partes = q.split(',', '.')
        val fracao = partes.getOrNull(1)
        return when {
            fracao == null -> lidoComoInteiro(partes[0])
            // Três dígitos depois do separador é milhar, não centavos: "1.690" é o mesmo
            // número que "1690" — quem digitou o ponto lembrou do valor, não da vírgula de
            // centavos. Junta os dois pedaços e lê como um inteiro só, dos dois jeitos de novo.
            fracao.length == 3 -> lidoComoInteiro(partes[0] + fracao)
            else -> setOf(partes[0].toLong() * 100 + fracao.padEnd(2, '0').toLong())
        }
    }

    /** "340" pode ser R$ 340,00 OU 340 centavos — as duas leituras, como um valor sem separador. */
    private fun lidoComoInteiro(digitos: String): Set<Long> {
        val n = digitos.toLong()
        return setOf(n * 100, n)
    }
}
