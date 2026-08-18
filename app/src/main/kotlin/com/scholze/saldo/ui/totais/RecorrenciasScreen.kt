package com.scholze.saldo.ui.totais

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.scholze.saldo.domain.Movimentacao
import com.scholze.saldo.domain.Recorrencia
import com.scholze.saldo.ui.components.InsetGroup
import com.scholze.saldo.ui.components.InsetRow
import com.scholze.saldo.ui.privacy.FormatoMoney
import com.scholze.saldo.ui.privacy.MoneyText
import com.scholze.saldo.ui.theme.SaldoTheme
import java.time.YearMonth

/**
 * totais › recorrências: toma a aba, lista os templates do mês visto por dia do mês e abre a
 * ocorrência daquele mês no editor — é lá que "daqui em diante" e "excluir recorrência" já
 * existem, então esta tela não repete nenhuma ação de escrita.
 */
@Composable
fun RecorrenciasScreen(
    vm: RecorrenciasViewModel,
    mes: YearMonth,
    onAbrirMovimentacao: (Movimentacao) -> Unit,
    onVoltar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SaldoTheme.colors
    BackHandler(onBack = onVoltar)
    // O mês vem da aba: navegar o mês em totais e voltar aqui mostra o mês certo.
    LaunchedEffect(mes) { vm.verMes(mes) }
    val resumo by vm.state.collectAsState()

    Column(modifier.fillMaxSize().background(colors.background)) {
        // Não é o SaldoTopBar: aquele existe para navegar meses e traz duas setas com
        // contentDescription de mês. Aqui não há mês para navegar — só a volta. A estrutura
        // é a mesma (linha de ação em cima, título grande e à esquerda embaixo).
        Column(Modifier.fillMaxWidth().background(colors.background)) {
            Text(
                "‹ totais",
                Modifier
                    .clickable(role = Role.Button, onClick = onVoltar)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                style = SaldoTheme.type.body,
                color = colors.tint,
            )
            Text(
                "recorrências · " + mes.rotuloCurto(),
                Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 14.dp),
                style = SaldoTheme.type.navTitle,
                color = colors.label,
            )
        }

        // `null` só até o primeiro LedgerInput chegar — mesma escolha do resto do app.
        val r = resumo ?: run {
            Box(Modifier.fillMaxSize())
            return@Column
        }

        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 32.dp)),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(fixas(r.ativas.size), style = SaldoTheme.type.sectionHeader, color = colors.label)
                InsetGroup {
                    LinhaMes("entram por mês", r.entramMes, colors.positive)
                    LinhaMes("saem por mês", -r.saemMes)
                }
            }

            InsetGroup {
                if (r.ativas.isEmpty()) {
                    InsetRow(label = "nenhuma recorrência")
                } else {
                    r.ativas.forEach { rec ->
                        // Um template que só começa depois do mês visto não tem ocorrência para
                        // editar: a linha existe (é uma fixa ativa), mas não abre nada.
                        LinhaRecorrencia(
                            rec, mes,
                            onClick = if (rec.inicio <= mes) {
                                { vm.abrirOcorrencia(rec, onAbrirMovimentacao) }
                            } else {
                                null
                            },
                        )
                    }
                }
            }

            if (r.encerradas.isNotEmpty()) {
                var abertas by rememberSaveable { mutableStateOf(false) }
                InsetGroup {
                    InsetRow(
                        label = "encerradas (${r.encerradas.size})",
                        value = if (abertas) "esconder" else "ver",
                        valueColor = colors.tint,
                        onClick = { abertas = !abertas },
                    )
                    if (abertas) {
                        r.encerradas.forEach { rec ->
                            LinhaRecorrencia(rec, mes, onClick = null)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LinhaMes(rotulo: String, centavos: Long, cor: Color? = null) {
    val colors = SaldoTheme.colors
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(rotulo, Modifier.weight(1f), style = SaldoTheme.type.body, color = colors.label)
        MoneyText(
            centavos = centavos,
            style = SaldoTheme.type.body, color = cor ?: colors.label,
            formato = FormatoMoney.ASSINADO,
        )
    }
}

@Composable
private fun LinhaRecorrencia(rec: Recorrencia, mes: YearMonth, onClick: (() -> Unit)?) {
    val colors = SaldoTheme.colors
    val base = Modifier.fillMaxWidth()
    Row(
        (if (onClick != null) base.clickable(onClick = onClick) else base).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("dia ${rec.diaDoMes}", Modifier.width(50.dp), style = SaldoTheme.type.footnote, color = colors.secondaryLabel)
        Column(Modifier.weight(1f)) {
            Text(rec.descricao, style = SaldoTheme.type.body, color = colors.label)
            val nota = when {
                rec.inicio > mes -> "começa em " + rec.inicio.rotuloCurto()
                !rec.ativa -> "encerrada"
                rec.fim != null -> "até " + rec.fim.rotuloCurto()
                else -> null
            }
            if (nota != null) {
                Text(nota, style = SaldoTheme.type.caption, color = colors.secondaryLabel)
            }
        }
        if (rec.tags.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                rec.tags.take(3).forEach { Box(Modifier.size(6.dp).background(Color(it.cor), CircleShape)) }
            }
        }
        MoneyText(
            centavos = rec.valorCentavos,
            style = SaldoTheme.type.body, color = colors.label, formato = FormatoMoney.ASSINADO,
        )
    }
}
