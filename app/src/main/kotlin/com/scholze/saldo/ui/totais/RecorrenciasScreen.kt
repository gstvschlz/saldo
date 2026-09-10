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
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.scholze.saldo.domain.Assinatura
import com.scholze.saldo.domain.Movimentacao
import com.scholze.saldo.domain.Recorrencia
import com.scholze.saldo.domain.descricaoVisivel
import com.scholze.saldo.ui.components.Carregando
import com.scholze.saldo.ui.components.DescricaoTexto
import com.scholze.saldo.ui.components.ErroDeLeitura
import com.scholze.saldo.ui.components.InsetGroup
import com.scholze.saldo.ui.components.LinhaDeValor
import com.scholze.saldo.ui.components.InsetRow
import com.scholze.saldo.ui.privacy.FormatoMoney
import com.scholze.saldo.ui.privacy.MoneyText
import com.scholze.saldo.ui.theme.SaldoTheme
import java.time.YearMonth
import kotlin.math.abs

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
    val estado by vm.state.collectAsState()

    // A descrição da candidata que virou recorrência agora, e o pedido de rolagem até a linha dela.
    var recemCriada by remember { mutableStateOf<String?>(null) }
    val trazerParaAVista = remember { BringIntoViewRequester() }

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

        val erroLeitura = estado.erro
        if (erroLeitura != null) {
            ErroDeLeitura(erroLeitura, vm::tentarDeNovo)
            return@Column
        }
        // `null` só até o primeiro LedgerInput chegar — mesma escolha do resto do app.
        val r = estado.resumo ?: run {
            Carregando()
            return@Column
        }

        // "tornar mensal" cria a recorrência lá embaixo, na ordem do dia do mês; sem rolar até ela,
        // a candidata some do topo e nada na tela prova que alguma coisa aconteceu.
        LaunchedEffect(recemCriada, r.ativas) {
            if (recemCriada != null && r.ativas.any { it.descricao == recemCriada }) {
                // Um quadro de espera antes de pedir: o efeito roda no callback de animação DESTE
                // quadro, e a linha nova só ganha coordenadas na travessia de layout que vem
                // depois — pedir agora seria pedir para rolar até um nó que ainda não tem lugar.
                withFrameNanos { }
                trazerParaAVista.bringIntoView()
                recemCriada = null
            }
        }

        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 32.dp)),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            // No topo, e só quando há candidata: é um empurrão para uma tarefa que o usuário não
            // sabia que existia, não uma seção permanente da tela.
            if (estado.assinaturas.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("parece assinatura", style = SaldoTheme.type.sectionHeader, color = colors.label)
                    InsetGroup {
                        estado.assinaturas.forEach { a ->
                            LinhaAssinatura(
                                a,
                                onTornarMensal = {
                                    recemCriada = a.descricao
                                    vm.tornarMensal(a)
                                },
                                onDispensar = { vm.dispensar(a) },
                            )
                        }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                // Uma pausada continua na lista de "ativas" (ver InsightsEngine.recorrencias),
                // mas não é uma "fixa" ligada — a contagem só soma as que realmente rodam.
                Text(fixas(r.ativas.count { it.ativa }), style = SaldoTheme.type.sectionHeader, color = colors.label)
                InsetGroup {
                    LinhaDeValor("entram por mês", r.entramMes, colors.positive)
                    LinhaDeValor("saem por mês", -r.saemMes)
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
                            modifier = if (rec.descricao == recemCriada) {
                                Modifier.bringIntoViewRequester(trazerParaAVista)
                            } else {
                                Modifier
                            },
                            onPausa = { vm.alternarPausa(rec) },
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
private fun LinhaRecorrencia(
    rec: Recorrencia,
    mes: YearMonth,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    onPausa: (() -> Unit)? = null,
) {
    val colors = SaldoTheme.colors
    val base = modifier.fillMaxWidth()
    Row(
        (if (onClick != null) base.clickable(onClick = onClick) else base).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("dia ${rec.diaDoMes}", Modifier.width(50.dp), style = SaldoTheme.type.footnote, color = colors.secondaryLabel)
        Column(Modifier.weight(1f)) {
            DescricaoTexto(rec.descricao)
            val nota = when {
                rec.inicio > mes -> "começa em " + rec.inicio.rotuloCurto()
                // `fim` antes de `!ativa`: uma pausada listada em "encerradas" (`fim` já
                // passou) tem de ler "até …", não "pausada" — encerrada é o que ela é ali.
                rec.fim != null -> "até " + rec.fim.rotuloCurto()
                !rec.ativa -> "pausada"
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
            style = SaldoTheme.type.body,
            color = if (!rec.ativa) colors.secondaryLabel else colors.label,
            formato = FormatoMoney.ASSINADO,
        )
        if (onPausa != null) {
            Switch(
                checked = rec.ativa,
                onCheckedChange = { onPausa() },
                modifier = Modifier.semantics {
                    contentDescription = (if (rec.ativa) "pausar " else "retomar ") + rec.descricao.descricaoVisivel()
                },
            )
        }
    }
}

/**
 * Uma candidata: descrição, valor, "N meses · dia D", o reajuste quando houve, e as duas ações.
 *
 * "tornar mensal" e "dispensar" lado a lado, sem hierarquia visual entre elas: as duas são
 * respostas legítimas, e a única que não tem volta é a segunda — que por isso não vira um botão
 * vermelho convidativo. O nome da candidata entra no `contentDescription` de cada uma pelo mesmo
 * motivo do interruptor de pausa acima: com três linhas na seção, três botões "dispensar" iguais
 * não dizem a um leitor de tela qual assinatura some.
 */
@Composable
private fun LinhaAssinatura(a: Assinatura, onTornarMensal: () -> Unit, onDispensar: () -> Unit) {
    val colors = SaldoTheme.colors
    val nome = a.descricao.descricaoVisivel()
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f)) {
                DescricaoTexto(a.descricao)
                Text(
                    "${a.meses} meses · dia ${a.diaDoMes}",
                    style = SaldoTheme.type.caption, color = colors.secondaryLabel,
                )
            }
            MoneyText(
                centavos = a.valorCentavos,
                style = SaldoTheme.type.body, color = colors.label,
                formato = FormatoMoney.ASSINADO,
            )
        }
        a.valorAnteriorCentavos?.let { anterior ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    if (abs(a.valorCentavos) > abs(anterior)) "subiu de" else "caiu de",
                    style = SaldoTheme.type.caption, color = colors.secondaryLabel,
                )
                // Em módulo: "subiu de −R$ 39,90 para −R$ 44,90" é uma frase que ninguém fala.
                // Passa por MoneyText mesmo assim — é dinheiro, e a privacidade o esconde.
                MoneyText(centavos = abs(anterior), style = SaldoTheme.type.caption, color = colors.secondaryLabel)
                Text("para", style = SaldoTheme.type.caption, color = colors.secondaryLabel)
                MoneyText(
                    centavos = abs(a.valorCentavos),
                    style = SaldoTheme.type.caption, color = colors.secondaryLabel,
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(
                onClick = onTornarMensal,
                modifier = Modifier.semantics { contentDescription = "tornar mensal $nome" },
            ) {
                Text("tornar mensal")
            }
            TextButton(
                onClick = onDispensar,
                modifier = Modifier.semantics { contentDescription = "dispensar $nome" },
            ) {
                Text("dispensar")
            }
        }
    }
}
