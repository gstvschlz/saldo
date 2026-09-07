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
    private val dinheiro = Regex("^\\d{1,9}([.,]\\d{1,2})?$")

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
    private fun centavosDe(q: String): Set<Long> {
        if (!dinheiro.matches(q)) return emptySet()
        val partes = q.split(',', '.')
        val reais = partes[0].toLong()
        val fracao = partes.getOrNull(1)
        return if (fracao != null) {
            setOf(reais * 100 + fracao.padEnd(2, '0').toLong())
        } else {
            setOf(reais * 100, reais)
        }
    }
}
