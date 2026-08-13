package com.scholze.saldo.domain

import java.time.YearMonth

object RecurrenceExpander {

    fun ocorrenciaNoMes(template: Recorrencia, mes: YearMonth): Movimentacao? {
        if (!template.ativa) return null
        if (mes < template.inicio) return null
        val fim = template.fim
        if (fim != null && mes > fim) return null
        return Movimentacao(
            descricao = template.descricao,
            valorCentavos = template.valorCentavos,
            data = mes.atDay(minOf(template.diaDoMes, mes.lengthOfMonth())),
            natureza = template.natureza,
            recorrenciaId = template.id,
            tags = template.tags,
        )
    }

    fun ocorrenciasNoMes(templates: List<Recorrencia>, mes: YearMonth): List<Movimentacao> =
        templates.mapNotNull { ocorrenciaNoMes(it, mes) }.sortedBy { it.data }
}
