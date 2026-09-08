package com.scholze.saldo.domain

/**
 * As cores de tag — seis, afinadas como conjunto (proposta A, 2026-09-08).
 *
 * Matizes a cada 60° (6 × 60 fecha o círculo, então uma cor cai na faixa do roxo e outra na do
 * verde por construção, não por escolha), croma no teto de 0,16 e luminosidade dentro da faixa que
 * serve aos dois temas: contraste ≥ 3:1 contra `#F2F5EE` e contra `#191F1A`, e ΔE ≥ 8 sob
 * protanopia/deuteranopia. O único piso que fica de fora é o de visão normal — teal ↔ oliva em
 * ΔE 13,9 contra 15 —, e ele é inalcançável para qualquer conjunto de seis nessas condições; é
 * aceitável porque a cor nunca aparece sozinha: toda bolinha tem o nome ao lado.
 *
 * A rotação de 5° gasta a proximidade inevitável com a matiz da casa no VINHO (5° do
 * `categoryVariable`), e não no verde: o verde da casa é o acento (tint, hero, FAB, estado
 * selecionado, ícone) e uma tag nele leria como elemento de sistema, enquanto o vinho da casa é o
 * pontinho de 6 dp da avulsa — uma tag naquela família lê como "deste app", não como "do sistema".
 *
 * [proxima] devolve a cor menos usada entre as seis; empate vai pela ordem da paleta. Uma cor que
 * não está na paleta (importada, escolhida à mão, de uma versão antiga) não conta — e é exatamente
 * por isso que [ANTIGAS_PARA_NOVAS] existe.
 */
object PaletaTags {
    val cores: List<Long> = listOf(
        0xFFB63C62L, // vinho
        0xFF9A5A00L, // âmbar
        0xFF719503L, // oliva
        0xFF079E92L, // teal
        0xFF036EAEL, // azul
        0xFFA672DCL, // lilás
    )

    /**
     * De cada cor velha para a nova da **mesma família de matiz** — não por posição na lista
     * (decisão 15 do spec). As duas listas não estão na mesma ordem — a velha é
     * `[vinho, âmbar, teal, roxo, verde, pêssego]`, a nova é `[vinho, âmbar, oliva, teal, azul,
     * lilás]` — e casar por índice trocaria a família de quatro das seis (uma tag verde viraria
     * azul). O mapa, explícito:
     *
     *   vinho   `#A6486B` → `#B63C62`  (mesma família)
     *   âmbar   `#B95A2E` → `#9A5A00`  (mesma família)
     *   teal    `#2A7A86` → `#079E92`  (mesma família)
     *   roxo    `#4B4BC4` → `#A672DC`  (vira lilás, a mais próxima que sobrou)
     *   verde   `#14663A` → `#719503`  (vira oliva, a mais próxima que sobrou)
     *   pêssego `#E58A5A` → `#036EAE`  (o único que troca de família: a paleta nova não tem
     *                                   laranja claro — era a matiz duplicada do âmbar — e o
     *                                   azul é o slot sem antecessor)
     *
     * Sem repintar, [proxima] passaria a ignorar TODAS as tags existentes de uma vez — ele só
     * conta cores que estão em [cores] — e as primeiras tags novas sairiam todas na primeira
     * cor. Nenhum valor novo é também um valor velho, então rodar o remapeamento duas vezes não
     * acha nada na segunda: a marca `paleta_v2_aplicada` no DataStore é a trava explícita, e
     * esta disjunção é a rede embaixo dela.
     */
    val ANTIGAS_PARA_NOVAS: Map<Long, Long> = mapOf(
        0xFFA6486BL to 0xFFB63C62L, // vinho -> vinho
        0xFFB95A2EL to 0xFF9A5A00L, // âmbar -> âmbar
        0xFF2A7A86L to 0xFF079E92L, // teal -> teal
        0xFF4B4BC4L to 0xFFA672DCL, // roxo -> lilás
        0xFF14663AL to 0xFF719503L, // verde -> oliva
        0xFFE58A5AL to 0xFF036EAEL, // pêssego -> azul
    )

    fun proxima(usadas: List<Long>): Long {
        val contagem = usadas.filter { it in cores }.groupingBy { it }.eachCount()
        return cores.minBy { contagem[it] ?: 0 }
    }
}
