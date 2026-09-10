package com.scholze.saldo.ui.ledger

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChangeIgnoreConsumed
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.scholze.saldo.domain.DiaRow
import com.scholze.saldo.domain.Fatura
import com.scholze.saldo.domain.FiltroLedger
import com.scholze.saldo.domain.ItemDia
import com.scholze.saldo.domain.MesLedger
import com.scholze.saldo.domain.Movimentacao
import com.scholze.saldo.domain.Natureza
import com.scholze.saldo.domain.Tag
import com.scholze.saldo.domain.Teto
import com.scholze.saldo.domain.descricaoVisivel
import com.scholze.saldo.ui.components.BuscaTopBar
import com.scholze.saldo.ui.components.Carregando
import com.scholze.saldo.ui.components.DescricaoTexto
import com.scholze.saldo.ui.components.ErroDeLeitura
import com.scholze.saldo.ui.components.arrastoDeMes
import com.scholze.saldo.ui.components.DiaBadge
import com.scholze.saldo.ui.components.FiltroChips
import com.scholze.saldo.ui.components.IconeRedondo
import com.scholze.saldo.ui.components.SaldoGlyph
import com.scholze.saldo.ui.components.SaldoIcon
import com.scholze.saldo.ui.components.SaldoPill
import com.scholze.saldo.ui.components.SaldoTopBar
import com.scholze.saldo.ui.money.centavosComSimbolo
import com.scholze.saldo.ui.privacy.FormatoMoney
import com.scholze.saldo.ui.privacy.LocalPrivacy
import com.scholze.saldo.ui.privacy.MASCARA_PRIVACIDADE
import com.scholze.saldo.ui.privacy.MoneyText
import com.scholze.saldo.ui.theme.SaldoTheme
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlinx.coroutines.launch

private val ptBr = Locale.forLanguageTag("pt-BR")
private val tituloMes = DateTimeFormatter.ofPattern("MMMM yyyy", ptBr)
private val diaCurto = DateTimeFormatter.ofPattern("d MMM", ptBr)
private val diaSemanaCurto = DateTimeFormatter.ofPattern("EEE", ptBr)

/** O hero, para os testes: há vários nós de dinheiro mascarados na tela. */
const val TAG_SALDO_PROJETADO = "ledger:saldoProjetado"

/** A pill "guardou N%" do hero. */
const val TAG_PILL_GUARDADO = "hero:guardado"

/**
 * O que a pill do hero e o widget de saldo dizem num mês em que ainda não entrou nada.
 *
 * Um mês sem entrada não tem proporção guardada — e "0%" mentiria para quem guardou antes de
 * o salário cair. A pill continua na tela dizendo o motivo, que foi a decisão de 2026-09-10:
 * a % guardada não some, nem por privacidade nem por mês inacabado.
 */
const val SEM_ENTRADA = "sem entrada ainda"

/** O teto do dia no hero — "hoje R$ 87,40". */
const val TAG_TETO_HOJE = "hero:tetoHoje"

/** O que ainda cabe hoje — só existe depois do primeiro gasto do dia. */
const val TAG_TETO_RESTA = "hero:tetoResta"

/** A lista de resultados da busca (ou o "nada com …"). */
const val TAG_RESULTADOS = "ledger:resultados"

/** A coluna de saldo de um dia — mesma razão do hero: vários nós iguais na tela. */
fun tagSaldoDoDia(dia: Int): String = "ledger:saldoDia:$dia"

@Composable
fun LedgerScreen(
    state: LedgerUiState,
    onMesAnterior: () -> Unit,
    onProximoMes: () -> Unit,
    onFiltro: (FiltroLedger) -> Unit,
    onItemClick: (Movimentacao) -> Unit,
    onExcluir: (Movimentacao) -> Unit,
    onTogglePrivacidade: () -> Unit,
    onVerBoard: () -> Unit,
    onLimparTag: () -> Unit,
    onTentar: () -> Unit = {},
    onVerGuardado: () -> Unit = {},
    onEtiquetar: (Movimentacao, Tag) -> Unit = { _, _ -> },
    onMaisEtiquetas: (Movimentacao) -> Unit = {},
    /** A meta de guardar, em %; `0` = sem meta. Vem do `Settings`, que a shell já tem em mãos. */
    metaGuardarPercent: Int = 0,
    alvo: AlvoLedger? = null,
    onAlvoConsumido: () -> Unit = {},
    busca: String? = null,
    resultados: List<Movimentacao>? = null,
    onAbrirBusca: () -> Unit = {},
    onFecharBusca: () -> Unit = {},
    onBusca: (String) -> Unit = {},
    onAbrirResultado: (Movimentacao) -> Unit = onItemClick,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val colors = SaldoTheme.colors
    val mes = state.mes
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    // Fatura tocada: abre a lista de compras (Step 1b). Estado de tela, não de ViewModel —
    // é só uma leitura, não muda dado nenhum, então fechar na rotação é aceitável.
    var faturaAberta by remember { mutableStateOf<Fatura?>(null) }

    // Índice do item de hoje na LazyColumn: 2 headers antes dos dias (hero, chips), 3
    // quando o chip de tag entra. O cabeçalho de colunas sumiu com a grade. Num mês sem
    // movimentação alguma os dias nem viram itens — a pill não teria destino.
    val cabecalhos = if (state.tagFiltro != null) 3 else 2
    val indiceHoje = remember(mes, state.hoje, cabecalhos) {
        mes?.takeIf { m -> m.dias.any { it.itens.isNotEmpty() } }
            ?.dias?.indexOfFirst { it.data == state.hoje }?.takeIf { it >= 0 }?.plus(cabecalhos)
    }
    val mostraPillHoje by remember(indiceHoje) {
        derivedStateOf {
            indiceHoje != null &&
                (listState.firstVisibleItemIndex > indiceHoje ||
                    listState.firstVisibleItemIndex + listState.layoutInfo.visibleItemsInfo.size <= indiceHoje)
        }
    }

    // Chegada por deep link (widget/lembrete) pedindo um dia: rola até ele assim que o mês
    // pedido está na tela — `mes` pode ainda ser o mês anterior por um quadro — e devolve o
    // alvo como consumido. Num mês sem movimentação os dias nem viram itens: só consome.
    LaunchedEffect(alvo, mes) {
        val a = alvo ?: return@LaunchedEffect
        val m = mes ?: return@LaunchedEffect
        if (m.mes != a.mes) return@LaunchedEffect
        if (m.dias.any { it.itens.isNotEmpty() }) listState.scrollToItem(cabecalhos + a.dia - 1)
        onAlvoConsumido()
    }

    Box(
        modifier
            .fillMaxSize()
            .background(colors.background)
            // O arrasto de mês desliga com a busca aberta: um gesto horizontal sobre os
            // resultados não tem "mês" para trocar, e brigaria com o scroll da lista.
            .then(if (busca == null) Modifier.arrastoDeMes(state.mesAtual, onMesAnterior, onProximoMes) else Modifier)
    ) {
        Column(Modifier.fillMaxSize()) {
            SaldoTopBar(
                titulo = state.mesAtual.format(tituloMes),
                onAnterior = onMesAnterior,
                onProximo = onProximoMes,
                busca = busca?.let { BuscaTopBar(it, onBusca, onFecharBusca) },
                acoes = {
                    IconeRedondo(SaldoIcon.LUPA, "buscar", onAbrirBusca)
                    IconeRedondo(SaldoIcon.GRADE, "ver como grade", onVerBoard)
                    IconeRedondo(
                        if (LocalPrivacy.current.oculto) SaldoIcon.OLHO_RISCADO else SaldoIcon.OLHO,
                        "alternar privacidade",
                        onTogglePrivacidade,
                    )
                },
            )

            // Busca com texto: os resultados tomam o lugar do mês. A pill de "hoje" e o
            // diálogo da fatura ficam no Box de fora e não atrapalham — a pill depende da
            // lista do mês, que não está composta.
            if (busca != null && busca.isNotBlank()) {
                ResultadosBusca(
                    consulta = busca,
                    resultados = resultados.orEmpty(),
                    hoje = state.hoje,
                    onItemClick = onAbrirResultado,
                    onExcluir = onExcluir,
                    contentPadding = contentPadding,
                )
                return@Column
            }

            val erro = state.erro
            if (erro != null) {
                ErroDeLeitura(erro, onTentar)
            } else if (mes == null) {
                Carregando()
            } else {
                LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = contentPadding) {
                    item(key = "hero") { BalanceHero(mes, onTogglePrivacidade, onVerGuardado, metaGuardarPercent, state.teto) }
                    item(key = "filtro") {
                        // O chip "sem tag" só aparece quando há trabalho — um chip permanente
                        // anunciando uma tarefa é ruído nos meses em que está tudo etiquetado.
                        // A exceção: com o filtro JÁ selecionado ele fica (com `0`) até o usuário
                        // sair, porque a interface não pode se puxar debaixo do próprio toque.
                        val mostraSemTag = mes.semTag > 0 || state.filtro == FiltroLedger.SEM_TAG
                        // `SEM_TAG` é o último do enum: cortar o fim mantém `filtro.ordinal` válido
                        // como índice das duas listas.
                        val opcoes = if (mostraSemTag) FiltroLedger.entries else FiltroLedger.entries.dropLast(1)
                        FiltroChips(
                            opcoes = opcoes.map { it.rotulo },
                            selecionado = state.filtro.ordinal,
                            onSelect = { onFiltro(opcoes[it]) },
                            contagens = opcoes.map { if (it == FiltroLedger.SEM_TAG) mes.semTag else null },
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 12.dp),
                        )
                    }
                    state.tagFiltro?.let { tag ->
                        item(key = "tagchip") {
                            Row(
                                Modifier
                                    .padding(start = 16.dp, bottom = 6.dp)
                                    .clip(RoundedCornerShape(percent = 50))
                                    .background(colors.secondaryContainer)
                                    .clickable { onLimparTag() }
                                    .padding(horizontal = 12.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text("tag: ${tag.nome}", style = SaldoTheme.type.footnote, color = colors.label)
                                Text("×", style = SaldoTheme.type.footnote, color = colors.secondaryLabel)
                            }
                        }
                    }
                    if (mes.dias.all { it.itens.isEmpty() }) {
                        item(key = "vazio") { EmptyMonth(comTagFiltro = state.tagFiltro != null, filtro = state.filtro) }
                    } else {
                        itemsIndexed(mes.dias, key = { _, d -> d.data.toEpochDay() }) { _, dia ->
                            DayRow(
                                dia = dia,
                                faixa = mes.faixaSaldos(),
                                hoje = state.hoje,
                                onItemClick = onItemClick,
                                onExcluir = onExcluir,
                                onFaturaClick = { faturaAberta = it },
                                // A fileira só existe sob `sem tag` (decisão 4 do spec).
                                etiquetas = if (state.filtro == FiltroLedger.SEM_TAG) state.tagsSugeridas else emptyList(),
                                onEtiquetar = onEtiquetar,
                                onMaisEtiquetas = onMaisEtiquetas,
                            )
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            // Sem isto a pill flutuava por cima dos resultados da busca, apontando para um
            // "hoje" que nem está na tela.
            visible = mostraPillHoje && busca == null,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 18.dp),
        ) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(15.dp))
                    .background(colors.tint)
                    .clickable { indiceHoje?.let { scope.launch { listState.animateScrollToItem(it) } } }
                    .padding(horizontal = 16.dp, vertical = 7.dp),
            ) {
                Text(
                    "hoje",
                    style = SaldoTheme.type.footnote.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }

        faturaAberta?.let { DialogoFatura(it) { faturaAberta = null } }
    }
}

/**
 * As compras que formaram uma fatura. Vive fora da tela porque o board também abre esta
 * lista: a fatura é a mesma dos dois lados, e duas cópias divergiriam na primeira mudança.
 */
@Composable
internal fun DialogoFatura(fatura: Fatura, onFechar: () -> Unit) {
    AlertDialog(
        onDismissRequest = onFechar,
        title = { Text("fatura · vence " + fatura.vencimento.format(diaCurto).removeSuffix(".")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                fatura.compras.forEach { compra ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            compra.data.format(diaCurto).removeSuffix(".") + "  " +
                                compra.descricao.descricaoVisivel(),
                            Modifier.weight(1f), style = SaldoTheme.type.row,
                        )
                        MoneyText(centavos = compra.valorCentavos, style = SaldoTheme.type.row, formato = FormatoMoney.ASSINADO)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onFechar) { Text("ok") } },
    )
}

internal fun MesLedger.faixaSaldos(): ClosedRange<Long> {
    if (dias.isEmpty()) return 0L..0L
    val saldos = dias.map { it.saldoCentavos }
    return saldos.min()..saldos.max()
}

/** O card do saldo projetado. `internal` porque o board mostra exatamente o mesmo. */
@Composable
internal fun BalanceHero(
    mes: MesLedger,
    onTogglePrivacidade: () -> Unit,
    onVerGuardado: () -> Unit = {},
    /** A meta de guardar, em %; `0` = sem meta, e aí a pill é exatamente a de antes. */
    metaGuardarPercent: Int = 0,
    /**
     * O teto do dia; `null` fora do mês corrente e num mês sem entrada, e aí o hero é o de antes.
     * Quem decide os dois casos é o ViewModel — ver `BoardUiState.teto`.
     */
    teto: Teto? = null,
) {
    val colors = SaldoTheme.colors
    Column(
        Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(colors.primaryContainer)
            .clickable(onClick = onTogglePrivacidade)   // tocar no hero tambem alterna
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            "saldo projetado · " + mes.projetadoEm.format(diaCurto).removeSuffix("."),
            style = SaldoTheme.type.footnote, color = colors.onPrimaryContainer.copy(alpha = 0.72f),
        )
        // Contagem ate o valor novo em vez de troca seca - de mes para mes, e quando uma
        // movimentacao entra ou sai. Mascarado o numero nem aparece, entao a animacao
        // simplesmente nao se ve; o alvo continua sendo o valor real.
        val animado by animateFloatAsState(
            targetValue = mes.saldoProjetadoCentavos.toFloat(),
            animationSpec = tween(durationMillis = 450),
            label = "saldoCountUp",
        )
        MoneyText(
            centavos = animado.toLong(),
            modifier = Modifier.testTag(TAG_SALDO_PROJETADO),
            style = SaldoTheme.type.largeTitle, color = colors.onPrimaryContainer,
        )
        Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier
                    .clip(RoundedCornerShape(percent = 50))
                    .background(colors.onPrimaryContainer.copy(alpha = 0.10f))
                    .padding(horizontal = 11.dp, vertical = 5.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MoneyText(
                    centavos = mes.deltaNoMesCentavos,
                    style = SaldoTheme.type.subhead,
                    color = colors.onPrimaryContainer,
                    formato = FormatoMoney.ASSINADO_COM_SIMBOLO,
                )
                Text("no mês", style = SaldoTheme.type.subhead, color = colors.onPrimaryContainer)
            }
            // Do que entrou, quanto foi guardado. Não é dinheiro: a privacidade não a esconde.
            //
            // E a pill NÃO SOME. Antes ela existia só quando havia proporção para mostrar, e o
            // hero ficava sem ela nos primeiros dias do mês — justo quando a pergunta "quanto
            // estou guardando" ainda está em aberto. Sem entrada não há porcentagem para
            // inventar (0% seria mentira para quem guardou antes do salário cair), então a pill
            // diz o motivo com todas as letras.
            run {
                val taxa = mes.taxaGuardada
                // Batida a meta, a pill INVERTE: fundo `onPrimaryContainer`, texto
                // `primaryContainer`. O hero já é um cartão verde, então pintar de verde o que
                // está em cima dele não mudaria nada; a inversão é diferença de luminância, não de
                // matiz — sobrevive ao daltonismo e ao tema escuro, onde os dois tokens já trocam
                // de lado sozinhos, e lê como "acendeu" à distância de um olhar.
                val bateu = taxa != null && metaGuardarPercent > 0 && taxa >= metaGuardarPercent
                val fundo = if (bateu) colors.onPrimaryContainer else colors.onPrimaryContainer.copy(alpha = 0.10f)
                val tinta = if (bateu) colors.primaryContainer else colors.onPrimaryContainer
                // A cor sozinha não diz o NÚMERO, então o TalkBack diz. Sem meta a frase continua
                // a de antes: não há alvo nenhum para anunciar.
                val leitura = when {
                    taxa == null -> "ainda não entrou nada neste mês"
                    metaGuardarPercent <= 0 -> "guardou $taxa% do que entrou"
                    bateu -> "guardou $taxa%, meta de $metaGuardarPercent% batida"
                    else -> "guardou $taxa%, meta $metaGuardarPercent%"
                }
                // O alvo de toque (44 dp) fica no Box de fora, invisível; a pill pintada é a de
                // dentro, do mesmo tamanho da "no mês" — o fundo não pode denunciar o alvo.
                //
                // `weight(fill = false)`: a frase "sem entrada ainda" é bem mais larga que
                // "guardou 12%", e ao lado de um delta de cinco dígitos as duas pills passariam
                // da largura do hero. Assim a segunda encolhe em vez de a linha estourar.
                Box(
                    Modifier
                        .weight(1f, fill = false)
                        .sizeIn(minHeight = 44.dp)
                        .clickable(onClick = onVerGuardado)
                        .testTag(TAG_PILL_GUARDADO)
                        .semantics { contentDescription = leitura },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (taxa == null) SEM_ENTRADA else "guardou $taxa%",
                        Modifier
                            .clip(RoundedCornerShape(percent = 50))
                            .background(fundo)
                            .padding(horizontal = 11.dp, vertical = 5.dp),
                        style = SaldoTheme.type.subhead, color = tinta,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        if (mes.estimativaCentavos > 0) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("inclui estimativa de", style = SaldoTheme.type.caption, color = colors.onPrimaryContainer.copy(alpha = 0.7f))
                MoneyText(
                    centavos = mes.estimativaCentavos,
                    style = SaldoTheme.type.caption, color = colors.onPrimaryContainer.copy(alpha = 0.7f),
                )
                Text("em diários", style = SaldoTheme.type.caption, color = colors.onPrimaryContainer.copy(alpha = 0.7f))
            }
        }
        teto?.let { LinhaDoTeto(it) }
    }
}

/**
 * O teto do dia, sob um filete: quanto o dia comporta e quanto ainda cabe nele.
 *
 * A segunda linha só existe depois do primeiro gasto do dia — de manhã os dois números são o
 * mesmo, e repeti-lo seria ruído. O negativo aparece cru, com o U+2212 que [MoneyText] já usa
 * no delta do mês: o hero não tem cor de alarme, e inventar uma aqui brigaria com o verde.
 *
 * É dinheiro, então os dois números passam por [MoneyText] e somem com o mascaramento — ao
 * contrário da pill da meta, que é porcentagem e continua legível.
 */
@Composable
private fun LinhaDoTeto(teto: Teto) {
    val colors = SaldoTheme.colors
    val oculto = LocalPrivacy.current.oculto
    val rotulo = SaldoTheme.type.footnote
    val tinta = colors.onPrimaryContainer

    Box(
        Modifier
            .padding(top = 12.dp, bottom = 10.dp)
            .fillMaxWidth()
            .height(1.dp)
            .background(tinta.copy(alpha = 0.15f)),
    )
    Column(
        // Um nó só para o TalkBack: linha a linha ele leria "hoje", "R$ 87,40", "restam",
        // "−R$ 14,60" em quatro paradas, e o sinal do último se perderia no caminho.
        Modifier.semantics(mergeDescendants = true) { contentDescription = descricaoDoTeto(teto, oculto) },
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("hoje", Modifier.width(52.dp), style = rotulo, color = tinta.copy(alpha = 0.72f))
            MoneyText(
                centavos = teto.tetoCentavos,
                modifier = Modifier.testTag(TAG_TETO_HOJE),
                style = SaldoTheme.type.subhead, color = tinta,
            )
        }
        if (teto.gastoDeHojeCentavos != 0L) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("restam", Modifier.width(52.dp), style = rotulo, color = tinta.copy(alpha = 0.72f))
                MoneyText(
                    centavos = teto.restaCentavos,
                    modifier = Modifier.testTag(TAG_TETO_RESTA),
                    style = SaldoTheme.type.subhead, color = tinta,
                )
            }
        }
    }
}

/**
 * A frase do TalkBack para o teto. [oculto] é passado e não lido do `LocalPrivacy` de propósito:
 * é uma função pura, testável sem composição, e é ela que impede o leitor de tela de anunciar o
 * valor que a tela está escondendo. Mesmo desenho de `descricaoDe` no board.
 */
internal fun descricaoDoTeto(teto: Teto, oculto: Boolean): String {
    fun valor(centavos: Long) = if (oculto) MASCARA_PRIVACIDADE else centavos.centavosComSimbolo()
    val abertura = if (teto.estourouOMes) {
        "o mês já estourou, hoje ${valor(teto.tetoCentavos)}"
    } else {
        "pode gastar ${valor(teto.tetoCentavos)} hoje"
    }
    if (teto.gastoDeHojeCentavos == 0L) return abertura
    val resta = if (teto.estourouODia) {
        "o dia passou em ${valor(-teto.restaCentavos)}"
    } else {
        "restam ${valor(teto.restaCentavos)}"
    }
    return "$abertura, $resta"
}

@Composable
private fun EmptyMonth(comTagFiltro: Boolean = false, filtro: FiltroLedger = FiltroLedger.TODAS) {
    val colors = SaldoTheme.colors
    // Sob `sem tag` o mês pode estar cheiíssimo e a fila vazia: é a mensagem de tarefa cumprida,
    // não a de mês vazio. Ela é o outro lado da exceção do chip — ele fica com `0`, e a lista
    // explica o que aquele zero quer dizer.
    val (titulo, ajuda) = when {
        filtro == FiltroLedger.SEM_TAG ->
            "tudo etiquetado neste mês" to "toque em outro filtro para ver o mês inteiro"
        // Sob filtro de tag o mês pode estar cheio: mandar "toque em +" seria mentira.
        comTagFiltro ->
            "nenhuma movimentação com essa tag" to "toque no × acima para ver o mês inteiro"
        else ->
            "sem movimentações neste mês" to "toque em + para adicionar"
    }
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(titulo, style = SaldoTheme.type.row, color = colors.secondaryLabel)
        Text(ajuda, style = SaldoTheme.type.footnote, color = colors.secondaryLabel)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
/** O canto dos cartões de lançamento — e do painel vermelho que aparece atrás no arrasto. */
private val CANTO_CARTAO = 16.dp

/** A coluna do marcador (bolinha ou glifo de recorrente) e o vão até a descrição. */
private val LARGURA_MARCADOR = 12.dp
private val GAP_MARCADOR = 8.dp

/**
 * Um dia e os seus lançamentos: o cabeçalho — a badge do dia e o saldo — e um CARTÃO por
 * lançamento.
 *
 * Era uma linha só: badge à esquerda, todos os lançamentos empilhados no meio, saldo à direita.
 * Nela a descrição e o valor dividiam a mesma linha, e quem cedia largura era sempre o número —
 * com uma descrição longa o `R$ …` ficava espremido contra a borda. No cartão o valor tem uma
 * linha inteira só para ele, e o tamanho da descrição deixa de decidir o que se consegue ler.
 *
 * O cabeçalho fica FORA dos cartões porque fala do dia, não de nenhum lançamento — e porque um
 * cartão contendo cartões é exatamente a moldura dentro de moldura que esta tela não quer.
 */
@Composable
internal fun DayRow(
    dia: DiaRow,
    faixa: ClosedRange<Long>,
    hoje: LocalDate,
    onItemClick: (Movimentacao) -> Unit,
    onExcluir: (Movimentacao) -> Unit,
    onFaturaClick: (Fatura) -> Unit,
    mostrarSaldo: Boolean = true,
    /** Não vazia só sob o filtro `sem tag`: a fileira de etiquetas dentro de cada cartão. */
    etiquetas: List<Tag> = emptyList(),
    onEtiquetar: (Movimentacao, Tag) -> Unit = { _, _ -> },
    onMaisEtiquetas: (Movimentacao) -> Unit = {},
) {
    val colors = SaldoTheme.colors
    val ehHoje = dia.data == hoje

    Column(
        Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            DiaBadge(
                dia = dia.data.dayOfMonth,
                diaSemana = dia.data.format(diaSemanaCurto).removeSuffix("."),
                destacado = ehHoje,
            )
            if (mostrarSaldo) {
                SaldoPill(
                    centavos = dia.saldoCentavos,
                    nivel = nivelDeCalor(dia.saldoCentavos, faixa),
                    modifier = Modifier.testTag(tagSaldoDoDia(dia.data.dayOfMonth)),
                )
            }
        }

        if (dia.itens.isEmpty()) {
            Text(
                "sem movimentações",
                Modifier.padding(start = 4.dp, bottom = 4.dp),
                style = SaldoTheme.type.row,
                color = colors.secondaryLabel,
            )
        } else {
            dia.itens.forEach { item ->
                // Exaustivo na interface selada: cada ramo sabe exatamente com que tipo
                // de item esta lidando, sem cast nenhum (nem seguro nem inseguro).
                when (item) {
                    is ItemDia.Mov -> key(item.mov.id, item.descricao) {
                        // A chave prende o `rememberSwipeToDismissBoxState` ao item, nao a
                        // posicao: sem ela, apagar o primeiro de dois itens do mesmo dia faria
                        // o segundo herdar o slot do primeiro - e aparecer arrastado para a
                        // esquerda, com o painel vermelho atras, enquanto a animacao volta.
                        val mov = item.mov
                        val dismissState = rememberSwipeToDismissBoxState()
                        // Reagir a TRANSICAO de currentValue, nao a um confirmValueChange.
                        // O anchoredDraggable chama aquele callback mais de uma vez no mesmo
                        // gesto: um swipe produzia DOIS snackbars, e o "desfazer" do segundo
                        // reinseria a linha de novo, agora duplicada.
                        LaunchedEffect(dismissState.currentValue) {
                            if (dismissState.currentValue == SwipeToDismissBoxValue.EndToStart) {
                                onExcluir(mov)
                                // A linha some porque o dado sumiu; se a exclusao for recusada
                                // (ocorrencia virtual), o reset devolve a linha ao lugar.
                                dismissState.reset()
                            }
                        }
                        SwipeToDismissBox(
                            state = dismissState,
                            enableDismissFromStartToEnd = false,
                            backgroundContent = { FundoExcluir() },
                        ) {
                            // O cartão pinta o próprio fundo, então o painel vermelho — que o
                            // SwipeToDismissBox mantém desenhado atrás o tempo todo — não vaza
                            // através dele com a linha parada.
                            CartaoMov(
                                descricao = item.descricao,
                                centavos = item.valorCentavos,
                                recorrente = item.recorrente,
                                natureza = mov.natureza,
                                destacado = ehHoje,
                                onClick = { onItemClick(mov) },
                            ) {
                                if (etiquetas.isNotEmpty()) {
                                    FileiraDeEtiquetas(
                                        etiquetas = etiquetas,
                                        onEtiquetar = { onEtiquetar(mov, it) },
                                        onMais = { onMaisEtiquetas(mov) },
                                    )
                                }
                            }
                        }
                    }

                    // A fatura e um total calculado, nao uma movimentacao de verdade: toca
                    // para abrir a lista de compras, mas nao passa por onItemClick - nao ha
                    // editor para uma linha que nao existe no banco. FaturaDia.recorrente e
                    // sempre true, entao nunca cai no branch da bolinha colorida.
                    is ItemDia.FaturaDia -> CartaoMov(
                        descricao = item.descricao,
                        centavos = item.valorCentavos,
                        recorrente = true,
                        natureza = Natureza.CARTAO,
                        destacado = ehHoje,
                        onClick = { onFaturaClick(item.fatura) },
                    )
                }
            }
        }
    }
}

/**
 * Um lançamento: marcador e descrição em cima, valor embaixo.
 *
 * Nesta ordem porque a descrição é o que se procura e o valor é o que se confere — e porque só
 * com o valor numa linha própria ele para de disputar largura com o texto. A descrição para em
 * duas linhas: além disso não é mais uma descrição, é um parágrafo, e o cartão viraria um bloco.
 */
@Composable
private fun CartaoMov(
    descricao: String,
    centavos: Long,
    recorrente: Boolean,
    natureza: Natureza,
    destacado: Boolean,
    onClick: () -> Unit,
    extra: @Composable ColumnScope.() -> Unit = {},
) {
    val colors = SaldoTheme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CANTO_CARTAO))
            .background(if (destacado) colors.secondaryContainer else colors.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(GAP_MARCADOR),
        ) {
            Marcador(recorrente, natureza)
            DescricaoTexto(descricao, Modifier.weight(1f), style = SaldoTheme.type.row, maxLines = 2)
        }
        // Alinhado com a descrição, não com o marcador: as duas linhas do cartão formam uma
        // coluna só, e o marcador fica sozinho na margem como a bolinha que é.
        MoneyText(
            centavos = centavos,
            modifier = Modifier.padding(start = LARGURA_MARCADOR + GAP_MARCADOR),
            style = SaldoTheme.type.row.copy(fontWeight = FontWeight.SemiBold),
            color = colors.label,
            formato = FormatoMoney.ASSINADO,
        )
        extra()
    }
}

/** A bolinha da natureza, ou o glifo de recorrente, numa coluna de largura fixa. */
@Composable
private fun Marcador(recorrente: Boolean, natureza: Natureza) {
    val colors = SaldoTheme.colors
    Box(Modifier.size(LARGURA_MARCADOR), contentAlignment = Alignment.Center) {
        if (recorrente) {
            SaldoGlyph(SaldoIcon.RECORRENTE, colors.secondaryLabel, size = 11.dp, strokeWidth = 1.3.dp)
        } else {
            Box(
                Modifier.size(6.dp).background(
                    when (natureza) {
                        Natureza.ECONOMIA -> colors.categoryFixed
                        else -> colors.categoryVariable
                    },
                    CircleShape,
                ),
            )
        }
    }
}

/** O painel que aparece atrás do cartão no arrasto para a esquerda. */
@Composable
private fun FundoExcluir() {
    Box(
        Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(CANTO_CARTAO))
            .background(SaldoTheme.colors.categoryVariable),
        contentAlignment = Alignment.CenterEnd,
    ) {
        Text(
            "excluir",
            Modifier.padding(end = 16.dp),
            style = SaldoTheme.type.footnote,
            // `categoryVariable` inverte de claridade entre os esquemas igual ao tint: branco
            // fixo dava 2,46:1 no escuro. `inverseOnSurface` tem exatamente a polaridade certa
            // - tinta clara no tema claro, escura no escuro - e da 5,4:1 nos dois.
            color = MaterialTheme.colorScheme.inverseOnSurface,
        )
    }
}

/**
 * Which of the three heat buckets a day's balance falls in - 0, 1 or 2.
 *
 * The thresholds are the ones the heat-tinted column used; only what consumes them
 * changed (a pill background instead of a column fill), so a month that read as
 * "thin at the end" still does.
 */
private fun nivelDeCalor(saldo: Long, faixa: ClosedRange<Long>): Int {
    if (faixa.endInclusive <= faixa.start) return 1
    val ratio = (saldo - faixa.start).toDouble() / (faixa.endInclusive - faixa.start).toDouble()
    return when {
        ratio < 0.34 -> 0
        ratio < 0.67 -> 1
        else -> 2
    }
}

/**
 * Os resultados da busca: um cabeçalho por mês, mais recentes primeiro, e as mesmas linhas
 * do ledger dentro. Sem saldo do dia — um resultado é uma linha solta, não um dia.
 */
@Composable
private fun ResultadosBusca(
    consulta: String,
    resultados: List<Movimentacao>,
    hoje: LocalDate,
    onItemClick: (Movimentacao) -> Unit,
    onExcluir: (Movimentacao) -> Unit,
    contentPadding: PaddingValues,
) {
    val colors = SaldoTheme.colors
    if (resultados.isEmpty()) {
        Column(
            Modifier.fillMaxWidth().padding(32.dp).testTag(TAG_RESULTADOS),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("nada com \"$consulta\"", style = SaldoTheme.type.row, color = colors.secondaryLabel)
        }
        return
    }
    val porMes = remember(resultados) { resultados.groupBy { YearMonth.from(it.data) } }
    LazyColumn(Modifier.fillMaxSize().testTag(TAG_RESULTADOS), contentPadding = contentPadding) {
        porMes.forEach { (mes, itens) ->
            item(key = "mes-$mes") {
                Text(
                    mes.format(tituloMes),
                    Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 6.dp),
                    style = SaldoTheme.type.sectionHeader, color = colors.secondaryLabel,
                )
            }
            items(itens, key = { "${it.id}-${it.data.toEpochDay()}-${it.recorrenciaId}" }) { mov ->
                DayRow(
                    dia = DiaRow(data = mov.data, itens = listOf(ItemDia.Mov(mov)), saldoCentavos = 0L),
                    faixa = 0L..0L,
                    hoje = hoje,
                    onItemClick = onItemClick,
                    onExcluir = onExcluir,
                    onFaturaClick = {},
                    mostrarSaldo = false,
                )
            }
        }
    }
}

/**
 * A fileira de etiquetas da fila de "sem tag": até seis chips e um `+`.
 *
 * Um toque aplica e a linha sai da lista; o `+` abre a sheet, onde escolher várias — e criar uma
 * etiqueta na hora (`criarTagInline`) — já existe. A fileira **só** é desenhada sob aquele filtro:
 * em `todas` ela seria um segundo jeito de editar cada linha, competindo com o toque que abre o
 * editor e engordando toda linha do mês por um trabalho que quase nenhuma delas tem.
 *
 * Rola na horizontal: seis chips com nomes longos não cabem numa tela estreita, e uma fileira que
 * corta a sexta etiqueta em silêncio é pior do que uma que se arrasta.
 */
@Composable
private fun FileiraDeEtiquetas(etiquetas: List<Tag>, onEtiquetar: (Tag) -> Unit, onMais: () -> Unit) {
    val colors = SaldoTheme.colors
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        etiquetas.forEach { tag ->
            // O alvo de toque (44 dp) fica no Box de fora, invisível; o chip pintado é o de
            // dentro — mesmo desenho da pill do hero, pela mesma razão: o fundo não pode
            // denunciar o alvo.
            Box(
                Modifier
                    .sizeIn(minHeight = 44.dp)
                    .clip(RoundedCornerShape(percent = 50))
                    .clickable { onEtiquetar(tag) }
                    .semantics(mergeDescendants = true) { contentDescription = "etiquetar como ${tag.nome}" },
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    Modifier
                        .clip(RoundedCornerShape(percent = 50))
                        .border(1.dp, colors.separator, RoundedCornerShape(percent = 50))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(6.dp).background(Color(tag.cor), CircleShape))
                    Text(tag.nome, style = SaldoTheme.type.caption, color = colors.secondaryLabel)
                }
            }
        }
        Box(
            Modifier
                .sizeIn(minWidth = 44.dp, minHeight = 44.dp)
                .clip(CircleShape)
                .clickable(onClick = onMais)
                .semantics { contentDescription = "mais etiquetas" },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "+",
                Modifier
                    .clip(CircleShape)
                    .border(1.dp, colors.separator, CircleShape)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                style = SaldoTheme.type.caption, color = colors.tint,
            )
        }
    }
}
