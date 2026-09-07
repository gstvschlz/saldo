package com.scholze.saldo.ui.entry

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scholze.saldo.domain.EscopoEdicao
import com.scholze.saldo.domain.EscopoExclusao
import com.scholze.saldo.domain.Natureza
import com.scholze.saldo.domain.RepetirOpcao
import com.scholze.saldo.ui.money.formatarCentavos
import com.scholze.saldo.ui.components.FilledActionButton
import com.scholze.saldo.ui.components.InsetGroup
import com.scholze.saldo.ui.components.InsetRow
import com.scholze.saldo.ui.components.FiltroChips
import com.scholze.saldo.ui.components.SaldoGlyph
import com.scholze.saldo.ui.components.SaldoIcon
import com.scholze.saldo.ui.privacy.MoneyText
import com.scholze.saldo.ui.theme.SaldoTheme
import com.scholze.saldo.ui.theme.tabular
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

private val ptBr = Locale.forLanguageTag("pt-BR")
private val dataCurta = DateTimeFormatter.ofPattern("EEE, d MMM", ptBr)
private val NATUREZAS = listOf(Natureza.DIARIO to "diário", Natureza.ECONOMIA to "economia", Natureza.CARTAO to "cartão")

/**
 * Option 1d — HIG sheet: segmented type over a grouped-inset form, now driven by
 * [EntryViewModel] and writing through to the repository.
 *
 * Tapping the amount hands off to the 1g keypad, which REPLACES the sheet content
 * rather than stacking over it.
 */
@Composable
fun NewEntrySheet(vm: EntryViewModel, onFechar: () -> Unit, modifier: Modifier = Modifier) {
    val colors = SaldoTheme.colors
    val state by vm.state.collectAsState()

    var editandoValor by rememberSaveable { mutableStateOf(false) }
    var pedindoEscopo by rememberSaveable { mutableStateOf(false) }
    var pedindoExclusao by rememberSaveable { mutableStateOf(false) }
    var escolhendoData by rememberSaveable { mutableStateOf(false) }
    var escolhendoRepetir by rememberSaveable { mutableStateOf(false) }
    var escolhendoTags by rememberSaveable { mutableStateOf(false) }
    var editandoDescricao by rememberSaveable { mutableStateOf(false) }

    if (editandoValor) {
        // O teclado substitui o conteúdo da sheet e não tem "cancelar": sem isto, o back
        // do sistema fecharia a sheet inteira (ou o app) em vez de só voltar ao formulário.
        BackHandler { editandoValor = false }
        AmountKeypadScreen(
            onContinue = { vm.definirCentavos(it); editandoValor = false },
            initialCentavos = state.centavos,
            modifier = modifier,
        )
        return
    }

    // Back fecha a sheet, como o "cancelar" da nav bar.
    BackHandler(onBack = onFechar)

    val ehRecorrente = state.recorrenciaId != null
    val salvar: () -> Unit = {
        if (state.precisaEscopo) pedindoEscopo = true
        else vm.salvar(EscopoEdicao.SO_ESTE_MES) { onFechar() }
    }

    Column(
        modifier
            .fillMaxSize()
            // Cantos de sheet do M3: a folha desliza de baixo, então só o topo arredonda.
            .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
            .background(colors.background),
    ) {
        // Nav bar da sheet.
        Column {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(colors.navBar)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "cancelar",
                    Modifier.clickable(onClick = onFechar),
                    style = SaldoTheme.type.body,
                    color = colors.tint,
                )
                Text(
                    if (state.editandoId == null) "nova movimentação" else "editar movimentação",
                    Modifier.weight(1f),
                    style = SaldoTheme.type.navTitle,
                    color = colors.label,
                    textAlign = TextAlign.Center,
                )
                Text(
                    "salvar",
                    Modifier.clickable(enabled = state.podeSalvar, onClick = salvar),
                    style = SaldoTheme.type.body.copy(fontWeight = FontWeight.SemiBold),
                    color = if (state.podeSalvar) colors.tint else colors.secondaryLabel,
                )
            }
        }

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            FiltroChips(
                opcoes = listOf("entrada", "saída"),
                selecionado = if (state.saida) 1 else 0,
                onSelect = { vm.definirSaida(it == 1) },
                modifier = Modifier.padding(top = 16.dp),
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NATUREZAS.forEach { (n, rotulo) ->
                    val selecionada = state.natureza == n
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(percent = 50))
                            .background(if (selecionada) colors.tint else colors.surface)
                            .clickable { vm.definirNatureza(n) }
                            .padding(horizontal = 14.dp, vertical = 7.dp),
                    ) {
                        Text(
                            rotulo,
                            style = SaldoTheme.type.footnote.copy(
                                fontWeight = if (selecionada) FontWeight.SemiBold else FontWeight.Normal,
                            ),
                            // Branco fixo aqui dava 1,68:1 no escuro, onde o tint é verde claro.
                            color = if (selecionada) MaterialTheme.colorScheme.onPrimary else colors.label,
                        )
                    }
                }
            }

            // Valor — toca para abrir o teclado 1g. Este é o número SENDO DIGITADO, um
            // contexto de entrada: fica em Text puro, fora da máscara de privacidade.
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth().clickable { editandoValor = true },
            ) {
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "R$",
                        Modifier.padding(bottom = 7.dp),
                        style = SaldoTheme.type.navTitle,
                        color = colors.secondaryLabel,
                    )
                    Text(
                        state.centavos.formatarCentavos(),
                        style = SaldoTheme.type.largeTitle.tabular.copy(fontSize = 44.sp),
                        color = colors.label,
                    )
                }
                Text(
                    legenda(state),
                    Modifier.padding(top = 6.dp),
                    style = SaldoTheme.type.footnote,
                    color = colors.secondaryLabel,
                )
            }

            InsetGroup {
                if (editandoDescricao) {
                    // Enquanto está aberto, o campo é dono do próprio texto.
                    //
                    // `state` vem de um `combine` com `flowOn(Default)`: o que se digita só
                    // volta do ViewModel um ou dois frames depois. Um TextField cujo `value`
                    // chega atrasado desfaz e refaz a edição a cada tecla — o cursor pula para
                    // o fim no meio da palavra, e digitando rápido some letra. Este `remember`
                    // é síncrono e responde na mesma composição; o ViewModel continua recebendo
                    // tudo, porque é `form` quem o `salvar` lê.
                    var texto by remember { mutableStateOf(state.descricao) }
                    val foco = remember { FocusRequester() }
                    // Abrir o campo é um toque deliberado na linha: ele já vem com o cursor.
                    LaunchedEffect(Unit) { foco.requestFocus() }
                    OutlinedTextField(
                        value = texto,
                        onValueChange = {
                            texto = it
                            vm.definirDescricao(it)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                            .focusRequester(foco),
                        placeholder = { Text("descrição", color = colors.secondaryLabel) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions.Default,
                    )
                } else {
                    // "opcional" e não "toque para escrever": a linha inteira já é clicável, e
                    // o que precisa ser dito aqui é que dá para salvar sem escrever nada.
                    InsetRow(
                        label = "descrição",
                        value = state.descricao.ifBlank { "opcional" },
                        onClick = { editandoDescricao = true },
                    )
                }
                InsetRow(label = "data", value = rotuloData(state.data), onClick = { escolhendoData = true })
                InsetRow(
                    label = "repetir",
                    value = rotuloRepetir(state.repetir),
                    onClick = { escolhendoRepetir = true },
                )
                InsetRow(
                    label = "tags",
                    onClick = { escolhendoTags = true },
                    trailing = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            state.tagsSelecionadas.take(3).forEach { TagPill(it.nome) }
                            Box(
                                Modifier.size(24.dp).clip(CircleShape).background(colors.secondaryContainer),
                                contentAlignment = Alignment.Center,
                            ) {
                                SaldoGlyph(SaldoIcon.PLUS, colors.tint, size = 16.dp, strokeWidth = 1.6.dp)
                            }
                        }
                    },
                )
            }

            FilledActionButton(
                text = when {
                    state.editandoId != null -> "salvar alterações"
                    else -> "adicionar " + NATUREZAS.first { it.first == state.natureza }.second
                },
                onClick = salvar,
                enabled = state.podeSalvar,
            )

            if (state.editandoId != null) {
                Text(
                    if (ehRecorrente) "excluir recorrência" else "excluir movimentação",
                    Modifier
                        .fillMaxWidth()
                        .clickable { if (ehRecorrente) pedindoExclusao = true else vm.excluir { onFechar() } }
                        .padding(vertical = 6.dp),
                    style = SaldoTheme.type.body,
                    color = colors.categoryVariable,
                    textAlign = TextAlign.Center,
                )
            }

            val saldoFooter = state.saldoResultanteCentavos
            if (saldoFooter != null) {
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 24.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "saldo de ${rotuloData(state.data)} ficará em ",
                        style = SaldoTheme.type.footnote,
                        color = colors.secondaryLabel,
                    )
                    MoneyText(
                        centavos = saldoFooter,
                        style = SaldoTheme.type.footnote,
                        color = colors.balance,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }

    if (escolhendoData) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = state.data.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { escolhendoData = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let {
                        vm.definirData(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate())
                    }
                    escolhendoData = false
                }) { Text("ok") }
            },
            dismissButton = { TextButton(onClick = { escolhendoData = false }) { Text("cancelar") } },
        ) { DatePicker(state = pickerState) }
    }

    if (escolhendoRepetir) {
        // O dia do TEMPLATE, não o da linha: numa instância clamped (29/30/31 num mês curto) a
        // data cai num dia diferente do template, e reoferecer "todo mês" com `state.data.dayOfMonth`
        // silenciosamente encolheria a série inteira para o dia clamped.
        val diaTemplate = (state.repetir as? RepetirOpcao.TodoMes)?.dia ?: state.data.dayOfMonth
        AlertDialog(
            onDismissRequest = { escolhendoRepetir = false },
            title = { Text("repetir") },
            text = {
                Column {
                    Text(
                        if (state.repetirOriginal is RepetirOpcao.TodoMes) "parar de repetir a partir deste mês" else "não repete",
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                vm.definirRepetir(RepetirOpcao.Nao)
                                escolhendoRepetir = false
                            }
                            .padding(vertical = 12.dp),
                    )
                    Text(
                        "todo mês no dia $diaTemplate",
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                vm.definirRepetir(RepetirOpcao.TodoMes(diaTemplate))
                                escolhendoRepetir = false
                            }
                            .padding(vertical = 12.dp),
                    )
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { escolhendoRepetir = false }) { Text("cancelar") } },
        )
    }

    if (escolhendoTags) {
        var novaTag by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { escolhendoTags = false },
            title = { Text("tags") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.todasTags.forEach { tag ->
                        val marcada = state.tagsSelecionadas.any { it.id == tag.id }
                        Row(
                            Modifier.fillMaxWidth().clickable { vm.alternarTag(tag) }.padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Box(Modifier.size(10.dp).background(Color(tag.cor), CircleShape))
                            Text(tag.nome, Modifier.weight(1f))
                            if (marcada) Text("✓", color = SaldoTheme.colors.tint)
                        }
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedTextField(
                            value = novaTag,
                            onValueChange = { novaTag = it },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text("nova tag") },
                            singleLine = true,
                        )
                        TextButton(
                            onClick = {
                                if (novaTag.isNotBlank()) {
                                    vm.criarTagInline(novaTag.trim()) { vm.alternarTag(it) }
                                    novaTag = ""
                                }
                            },
                        ) { Text("criar") }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { escolhendoTags = false }) { Text("ok") } },
            dismissButton = { TextButton(onClick = { escolhendoTags = false }) { Text("cancelar") } },
        )
    }

    if (pedindoEscopo) {
        AlertDialog(
            onDismissRequest = { pedindoEscopo = false },
            title = { Text("aplicar a") },
            text = { Text("essa movimentação vem de uma recorrência.") },
            confirmButton = {
                TextButton(onClick = {
                    pedindoEscopo = false
                    vm.salvar(EscopoEdicao.DAQUI_EM_DIANTE) { onFechar() }
                }) { Text("daqui em diante") }
            },
            dismissButton = {
                TextButton(onClick = {
                    pedindoEscopo = false
                    vm.salvar(EscopoEdicao.SO_ESTE_MES) { onFechar() }
                }) { Text("só este mês") }
            },
        )
    }

    if (pedindoExclusao) {
        AlertDialog(
            onDismissRequest = { pedindoExclusao = false },
            title = { Text("excluir recorrência") },
            // Três opções não cabem nos dois slots de botão do AlertDialog, e a que faltava
            // — apagar só a ocorrência deste mês, sem tocar no template — era justamente a
            // mais comum. Ficam empilhadas no corpo, como no seletor de tema.
            text = {
                Column {
                    TextButton(onClick = {
                        pedindoExclusao = false
                        vm.excluir { onFechar() }
                    }) { Text("só este mês") }
                    TextButton(onClick = {
                        pedindoExclusao = false
                        vm.excluirRecorrencia(EscopoExclusao.SO_FUTURAS) { onFechar() }
                    }) { Text("esta e as futuras") }
                    TextButton(onClick = {
                        pedindoExclusao = false
                        vm.excluirRecorrencia(EscopoExclusao.TODAS) { onFechar() }
                    }) { Text("todas") }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { pedindoExclusao = false }) { Text("cancelar") } },
        )
    }
}

private fun legenda(state: EntryUiState): String = when {
    !state.saida -> "entrada · entra no saldo"
    state.natureza == Natureza.DIARIO && state.repetir is RepetirOpcao.Nao -> "gasto variável · sai do saldo"
    state.natureza == Natureza.DIARIO -> "gasto fixo · sai do saldo todo mês"
    state.natureza == Natureza.ECONOMIA -> "vai para a reserva · sai do saldo"
    else -> "no cartão · pesa na fatura, não no saldo de hoje"
}

private fun rotuloData(data: LocalDate): String =
    if (data == LocalDate.now()) "hoje, " + data.format(dataCurta).substringAfter(", ").replace(".", "")
    else data.format(dataCurta).replace(".", "")

private fun rotuloRepetir(r: RepetirOpcao): String = when (r) {
    is RepetirOpcao.Nao -> "não repete"
    is RepetirOpcao.TodoMes -> "todo mês no dia ${r.dia}"
}

@Composable
private fun TagPill(text: String) {
    val colors = SaldoTheme.colors
    Box(
        Modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(colors.secondaryContainer)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(text, style = SaldoTheme.type.footnote, color = colors.label)
    }
}
