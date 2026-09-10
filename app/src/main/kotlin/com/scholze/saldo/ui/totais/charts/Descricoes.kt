package com.scholze.saldo.ui.totais.charts

import com.scholze.saldo.domain.GrupoGasto
import com.scholze.saldo.domain.PontoMes
import com.scholze.saldo.domain.TagsNoTempo
import com.scholze.saldo.ui.money.centavosComSimbolo
import java.time.DayOfWeek
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

/**
 * O que cada gráfico diz em voz alta.
 *
 * Seis gráficos eram invisíveis para o TalkBack: um `Canvas` não tem filhos, então o leitor de
 * tela passava por cima deles sem nada a anunciar — a aba `totais` inteira era uma sequência de
 * buracos. Aqui cada um vira uma frase.
 *
 * **Funções puras**, ao lado da matemática que já é pura ([ChartMath]): recebem os mesmos dados
 * que o desenho recebe e devolvem uma `String`, sem `Composable` nem `Context`. É o que as torna
 * testáveis na JVM — e o que impede a frase de discordar do desenho, já que as duas leem a mesma
 * entrada.
 *
 * Com a privacidade ligada, [oculto] troca cada dinheiro por "valor oculto": um leitor de tela
 * que anuncia o saldo em voz alta desfaz exatamente o que o olho riscado prometeu. Porcentagem
 * não é dinheiro e continua sendo dita — a mesma regra do hero.
 */
object Descricoes {

    private val ptBr = Locale.forLanguageTag("pt-BR")

    /** "sem dados" é uma frase melhor que o silêncio de um Canvas vazio. */
    private const val VAZIO = "sem dados"

    private const val OCULTO = "valor oculto"

    private fun dinheiro(centavos: Long, oculto: Boolean): String =
        if (oculto) OCULTO else centavos.centavosComSimbolo()

    private fun mes(m: YearMonth): String = m.month.getDisplayName(TextStyle.FULL, ptBr)

    private fun diaDaSemana(d: DayOfWeek): String = d.getDisplayName(TextStyle.FULL, ptBr)

    /** Entradas, saídas e o que sobrou, mês a mês. */
    fun tendencia(pontos: List<PontoMes>, oculto: Boolean): String {
        if (pontos.isEmpty()) return "tendência: $VAZIO"
        val frases = pontos.joinToString("; ") { p ->
            "em ${mes(p.mes)} entrou ${dinheiro(p.entradas, oculto)}, " +
                "saiu ${dinheiro(p.saidas, oculto)}, sobrou ${dinheiro(p.sobrou, oculto)}"
        }
        return "tendência: $frases"
    }

    /** Quanto já saiu neste ponto do mês, contra o que é o costume. */
    fun ritmo(acumuladoCentavos: Long, referenciaCentavos: Long?, oculto: Boolean): String {
        val ate = "ritmo: ${dinheiro(acumuladoCentavos, oculto)} até hoje"
        // Sem histórico não há costume com que comparar — e uma referência inventada seria pior
        // que nenhuma, que é a mesma decisão que o desenho já toma.
        val ref = referenciaCentavos ?: return "$ate, sem histórico para comparar"
        return "$ate; o costume neste ponto do mês é ${dinheiro(ref, oculto)}"
    }

    /** A taxa guardada mês a mês, e a meta quando existe. */
    fun poupanca(pontos: List<PontoMes>, metaPercent: Int): String {
        if (pontos.isEmpty()) return "poupança: $VAZIO"
        val frases = pontos.joinToString(", ") { p ->
            val taxa = p.taxaPoupanca
            if (taxa == null) "${mes(p.mes)} sem taxa" else "${mes(p.mes)} $taxa%"
        }
        val alvo = if (metaPercent > 0) ", meta $metaPercent%" else ""
        return "poupança: $frases$alvo"
    }

    /** A reserva acumulada: de quanto era no começo da janela a quanto é agora. */
    fun reserva(pontos: List<PontoMes>, oculto: Boolean): String {
        if (pontos.isEmpty()) return "reserva: $VAZIO"
        val primeiro = pontos.first()
        val ultimo = pontos.last()
        if (pontos.size == 1) {
            return "reserva: ${dinheiro(ultimo.reservaAcumulada, oculto)} em ${mes(ultimo.mes)}"
        }
        return "reserva: de ${dinheiro(primeiro.reservaAcumulada, oculto)} em ${mes(primeiro.mes)} " +
            "a ${dinheiro(ultimo.reservaAcumulada, oculto)} em ${mes(ultimo.mes)}"
    }

    /** Quanto saiu em cada dia da semana. */
    fun porDiaDaSemana(porDia: Map<DayOfWeek, Long>, oculto: Boolean): String {
        val comValor = DayOfWeek.entries.filter { (porDia[it] ?: 0L) != 0L }
        if (comValor.isEmpty()) return "por dia da semana: $VAZIO"
        val frases = DayOfWeek.entries.joinToString(", ") { d ->
            "${diaDaSemana(d)} ${dinheiro(porDia[d] ?: 0L, oculto)}"
        }
        return "por dia da semana: $frases"
    }

    /**
     * Para onde foi o dinheiro, em porcentagem.
     *
     * Porcentagem e não valor: é o que a barra desenha, e é o que sobrevive à privacidade — a
     * proporção não diz quanto se gastou. As fatias são arredondadas para inteiro, então elas
     * podem não somar exatamente cem; dizer "40%, 30%, 30%" é honesto sobre o que se vê.
     */
    fun paraOndeFoi(rotulos: List<String>, valores: List<Long>): String {
        val total = valores.sumOf { if (it > 0) it else 0L }
        if (total == 0L) return "para onde foi: $VAZIO"
        val frases = rotulos.indices.mapNotNull { i ->
            val v = valores.getOrElse(i) { 0L }
            if (v <= 0L) null else "${rotulos[i]} ${Math.round(v * 100.0 / total)}%"
        }
        return "para onde foi: " + frases.joinToString(", ")
    }

    /** As colunas empilhadas: o total de cada mês e os grupos que o formam. */
    fun tagsNoTempo(serie: TagsNoTempo, oculto: Boolean): String {
        if (serie.meses.isEmpty()) return "por tag: $VAZIO"
        val nomes: List<String> = serie.grupos.map { it.nome }
        val frases = serie.meses.joinToString("; ") { m ->
            val total = m.valores.sum()
            if (total == 0L) {
                "${mes(m.mes)} sem gastos"
            } else {
                val partes = m.valores.indices.mapNotNull { g ->
                    val v = m.valores[g]
                    if (v <= 0L) null else "${nomes.getOrElse(g) { "outras" }} ${Math.round(v * 100.0 / total)}%"
                }
                "em ${mes(m.mes)}, ${dinheiro(total, oculto)}: " + partes.joinToString(", ")
            }
        }
        return "por tag: $frases"
    }
}
