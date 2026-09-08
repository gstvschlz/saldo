package com.scholze.saldo.ui.mais

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.scholze.saldo.BuildConfig
import com.scholze.saldo.data.Dump
import com.scholze.saldo.data.Tema
import com.scholze.saldo.domain.CartaoConfig
import com.scholze.saldo.domain.CapturaConfig
import com.scholze.saldo.domain.LembretesConfig
import com.scholze.saldo.ui.components.Carregando
import com.scholze.saldo.ui.components.ErroDeLeitura
import com.scholze.saldo.ui.components.InsetGroup
import com.scholze.saldo.ui.components.InsetRow
import com.scholze.saldo.ui.entry.AmountKeypadScreen
import com.scholze.saldo.ui.money.centavosValor
import com.scholze.saldo.ui.privacy.MoneyText
import com.scholze.saldo.ui.theme.SaldoTheme
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** O campo onde se digita "apagar"; os testes o acham por aqui. */
const val TAG_CONFIRMACAO_APAGAR = "mais:confirmacao-apagar"

/**
 * O botão que apaga.
 *
 * Tem tag própria porque, depois de o campo receber "apagar", `onNodeWithText("apagar")` casa
 * com DOIS nós — o botão e o texto editável do campo, que a semântica de busca trata igual.
 */
const val TAG_BOTAO_APAGAR = "mais:botao-apagar"

/**
 * A linha e os controles do diálogo da meta.
 *
 * Por tag e não por texto: o diálogo repete o título da linha (dois nós com "meta de guardar"), e
 * `−`/`+` são glifos de um caractere que casariam com meio app.
 */
const val TAG_LINHA_META = "mais:meta"
const val TAG_META_MENOS = "mais:meta-menos"
const val TAG_META_MAIS = "mais:meta-mais"
const val TAG_META_VALOR = "mais:meta-valor"

private fun rotulo(t: Tema) = when (t) {
    Tema.SISTEMA -> "sistema"
    Tema.CLARO -> "claro"
    Tema.ESCURO -> "escuro"
}

private fun resumoCaptura(c: CapturaConfig): String = when {
    !c.ligada -> "desligado"
    c.marcados.isEmpty() -> "ligado · nenhum app"
    c.marcados.size == 1 -> "1 app"
    else -> "${c.marcados.size} apps"
}

private fun resumo(l: LembretesConfig): String = when (l.ativos) {
    0 -> "desligados"
    1 -> "1 ativo"
    else -> "${l.ativos} ativos"
}

private val dataLonga = DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", Locale.forLanguageTag("pt-BR"))

/** A data do diálogo do saldo inicial: curta, porque ela mora dentro de uma linha. */
private val dataCurta = DateTimeFormatter.ofPattern("dd/MM/yyyy")

/**
 * A pergunta do diálogo, com o que vai entrar e de quando é. Números concretos, não "tem certeza?":
 * é a última tela antes de o aparelho perder o que tem.
 */
private fun resumoDoDump(dump: Dump): String {
    val quando = runCatching { OffsetDateTime.parse(dump.exportadoEm).toLocalDate().format(dataLonga) }
        .getOrDefault(dump.exportadoEm)
    return "substituir tudo neste aparelho por ${dump.movimentacoes.size} lançamentos, " +
        "${dump.recorrencias.size} recorrências e ${dump.tags.size} tags, exportados em $quando?"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MaisScreen(
    vm: MaisViewModel,
    onExportar: () -> Unit,
    onEscolherArquivo: () -> Unit,
    onEscolherPasta: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SaldoTheme.colors
    val settings by vm.settings.collectAsState()
    val erro by vm.erro.collectAsState()
    // Assinado aqui em cima, antes de qualquer `return`: `vm.anterioresA` lê o VALOR corrente
    // do fluxo, e sob `WhileSubscribed` ele só começa a correr enquanto alguém o coleta — sem
    // esta linha a contagem do diálogo do saldo inicial seria eternamente zero. Lê-lo dentro do
    // diálogo é também o que faz a contagem recompor quando o ledger chega.
    val movimentacoes by vm.movimentacoes.collectAsState()
    val e = erro
    if (e != null) {
        ErroDeLeitura(e, vm::tentarDeNovo, modifier)
        return
    }
    val s = settings ?: run {
        Carregando(modifier)
        return
    }

    var editandoSaldo by rememberSaveable { mutableStateOf(false) }
    var editandoCartao by remember { mutableStateOf(false) }
    var escolhendoTema by remember { mutableStateOf(false) }
    var abrindoLembretes by rememberSaveable { mutableStateOf(false) }
    var abrindoNotificacoes by rememberSaveable { mutableStateOf(false) }
    var abrindoBackup by rememberSaveable { mutableStateOf(false) }
    var apagando by rememberSaveable { mutableStateOf(false) }
    var editandoMeta by rememberSaveable { mutableStateOf(false) }
    // O valor que saiu do teclado e ainda não foi gravado: é ele que abre o diálogo da data.
    var saldoPendente by rememberSaveable { mutableStateOf<Long?>(null) }

    if (abrindoNotificacoes) {
        CapturaScreen(
            config = s.captura,
            onLigar = vm::definirCapturaLigada,
            onMarcarApp = vm::definirAppMarcado,
            onVoltar = { abrindoNotificacoes = false },
            modifier = modifier,
        )
        return
    }

    if (abrindoLembretes) {
        LembretesScreen(
            config = s.lembretes,
            onDefinir = vm::definirLembretes,
            onVoltar = { abrindoLembretes = false },
            modifier = modifier,
        )
        return
    }

    if (abrindoBackup) {
        BackupScreen(
            config = s.backup,
            onEscolherPasta = onEscolherPasta,
            onCadencia = vm::definirCadenciaBackup,
            onAgora = vm::backupAgora,
            onVoltar = { abrindoBackup = false },
            modifier = modifier,
        )
        return
    }

    if (editandoSaldo) {
        // O teclado ocupa a aba inteira e não tem "cancelar" próprio: sem isto, voltar
        // atrás só seria possível salvando (ou saindo do app pelo back do sistema).
        BackHandler { editandoSaldo = false }
        AmountKeypadScreen(
            // O teclado não grava mais: ele entrega o valor ao diálogo, que é quem pergunta
            // a partir de QUANDO esse saldo vale e diz o que isso custa.
            onContinue = { saldoPendente = it; editandoSaldo = false },
            initialCentavos = s.saldoInicialCentavos ?: 0,
            titulo = "qual seu saldo hoje?",
            textoBotao = "salvar",
            modifier = modifier,
        )
        return
    }

    Column(modifier.fillMaxSize().background(colors.background)) {
        // Titulo grande e a ESQUERDA, como no ledger, em totais e em recorrencias: centrado
        // era a assinatura do HIG, e a 30sp do ramo novo ela ficava ainda mais evidente.
        Text(
            "mais",
            Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 14.dp),
            style = SaldoTheme.type.navTitle, color = colors.label,
        )

        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 32.dp)),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            InsetGroup {
                InsetRow(
                    label = "saldo inicial",
                    onClick = { editandoSaldo = true },
                    trailing = {
                        MoneyText(
                            centavos = s.saldoInicialCentavos ?: 0,
                            style = SaldoTheme.type.body, color = colors.secondaryLabel,
                        )
                    },
                )
                InsetRow(
                    label = "cartão",
                    value = "${s.cartao.nome} · fecha ${s.cartao.fechamentoDia} · vence ${s.cartao.vencimentoDia}",
                    onClick = { editandoCartao = true },
                )
                InsetRow(
                    label = "meta de guardar",
                    modifier = Modifier.testTag(TAG_LINHA_META),
                    value = if (s.metaGuardarPercent > 0) "${s.metaGuardarPercent}%" else "sem meta",
                    onClick = { editandoMeta = true },
                )
            }

            InsetGroup {
                InsetRow(
                    label = "começar oculto",
                    trailing = { Switch(checked = s.comecarOculto, onCheckedChange = { vm.definirComecarOculto(it) }) },
                )
                InsetRow(
                    label = "mostrar valores no widget",
                    trailing = { Switch(checked = s.widgetMostrarValores, onCheckedChange = { vm.definirWidgetMostrarValores(it) }) },
                )
                Text(
                    "o widget mostra o saldo projetado na tela inicial; desligado, mostra R$ •••••",
                    Modifier.padding(start = 16.dp, end = 16.dp, bottom = 10.dp),
                    style = SaldoTheme.type.caption, color = colors.secondaryLabel,
                )
                InsetRow(label = "tema", value = rotulo(s.tema), onClick = { escolhendoTema = true })
            }

            InsetGroup {
                InsetRow(label = "lembretes", value = resumo(s.lembretes), onClick = { abrindoLembretes = true })
                InsetRow(
                    label = "notificações",
                    value = resumoCaptura(s.captura),
                    onClick = { abrindoNotificacoes = true },
                )
            }

            InsetGroup {
                InsetRow(label = "exportar dados", onClick = onExportar)
                InsetRow(
                    label = "backup automático",
                    value = resumoBackup(s.backup),
                    onClick = { abrindoBackup = true },
                )
                InsetRow(label = "restaurar dados", value = "substitui tudo", onClick = onEscolherArquivo)
            }

            InsetGroup {
                InsetRow(label = "sobre", value = "saldo · 100% local")
                InsetRow(label = "versão", value = BuildConfig.VERSION_NAME)
            }

            // Sozinha, num grupo só dela e no fim de tudo: é a única linha da tela que não tem
            // volta, e ela não pode dividir cartão com "versão".
            InsetGroup {
                InsetRow(
                    label = "apagar dados",
                    value = "sem volta",
                    // Não há token de "destrutivo" no tema; `categoryVariable` é o rosa das
                    // saídas, a família que o app já usa para o que sai. Ver ui/theme/Color.kt.
                    valueColor = colors.categoryVariable,
                    onClick = { apagando = true },
                )
            }
        }
    }

    if (editandoCartao) {
        var nome by remember { mutableStateOf(s.cartao.nome) }
        var fechamento by remember { mutableStateOf(s.cartao.fechamentoDia.toString()) }
        var vencimento by remember { mutableStateOf(s.cartao.vencimentoDia.toString()) }
        AlertDialog(
            onDismissRequest = { editandoCartao = false },
            title = { Text("cartão") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = nome, onValueChange = { nome = it },
                        label = { Text("nome") }, singleLine = true,
                    )
                    OutlinedTextField(
                        value = fechamento, onValueChange = { fechamento = it.filter(Char::isDigit).take(2) },
                        label = { Text("dia do fechamento") }, singleLine = true,
                    )
                    OutlinedTextField(
                        value = vencimento, onValueChange = { vencimento = it.filter(Char::isDigit).take(2) },
                        label = { Text("dia do vencimento") }, singleLine = true,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val f = fechamento.toIntOrNull()?.coerceIn(1, 31)
                    val v = vencimento.toIntOrNull()?.coerceIn(1, 31)
                    if (f != null && v != null && nome.isNotBlank()) {
                        vm.definirCartao(CartaoConfig(nome = nome.trim(), fechamentoDia = f, vencimentoDia = v))
                        editandoCartao = false
                    }
                }) { Text("salvar") }
            },
            dismissButton = { TextButton(onClick = { editandoCartao = false }) { Text("cancelar") } },
        )
    }

    if (escolhendoTema) {
        AlertDialog(
            onDismissRequest = { escolhendoTema = false },
            title = { Text("tema") },
            text = {
                Column {
                    Tema.entries.forEach { t ->
                        val atual = t == s.tema
                        TextButton(onClick = { vm.definirTema(t); escolhendoTema = false }) {
                            Text(
                                rotulo(t),
                                // Uma lista de três botões idênticos não diz qual está
                                // valendo; o atual vem em semibold e na cor de label.
                                style = SaldoTheme.type.body.copy(
                                    fontWeight = if (atual) FontWeight.SemiBold else FontWeight.Normal,
                                ),
                                color = if (atual) colors.label else colors.tint,
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { escolhendoTema = false }) { Text("fechar") } },
        )
    }

    // `−`/`+` de um em um, e não um campo de texto: a meta é uma escolha grosseira ("uns 20%"),
    // e um teclado numérico para dois dígitos seria trabalho demais para a decisão que é.
    if (editandoMeta) {
        var percent by rememberSaveable { mutableIntStateOf(s.metaGuardarPercent) }
        AlertDialog(
            onDismissRequest = { editandoMeta = false },
            title = { Text("meta de guardar") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("quanto do que entra você quer guardar por mês?")
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        TextButton(
                            onClick = { percent -= 1 },
                            enabled = percent > 0,
                            modifier = Modifier.testTag(TAG_META_MENOS),
                        ) { Text("−") }
                        Text(
                            if (percent > 0) "$percent%" else "sem meta",
                            Modifier.testTag(TAG_META_VALOR),
                            style = SaldoTheme.type.body, color = colors.label,
                        )
                        TextButton(
                            onClick = { percent += 1 },
                            enabled = percent < 100,
                            modifier = Modifier.testTag(TAG_META_MAIS),
                        ) { Text("+") }
                    }
                    Text(
                        "zero desliga a meta: o hero volta a mostrar só quanto você guardou.",
                        style = SaldoTheme.type.caption, color = colors.secondaryLabel,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { vm.definirMetaGuardar(percent); editandoMeta = false }) { Text("salvar") }
            },
            dismissButton = { TextButton(onClick = { editandoMeta = false }) { Text("cancelar") } },
        )
    }

    // Reancorar o saldo inicial custa história: o motor ignora tudo antes de `saldoInicialData`.
    // Antes isto acontecia em silêncio, sempre em hoje; agora o diálogo diz quantos lançamentos
    // saem das contas e deixa manter a data original.
    saldoPendente?.let { centavos ->
        var data by rememberSaveable { mutableStateOf(LocalDate.now()) }
        var escolhendoData by remember { mutableStateOf(false) }
        // A contagem sai do ViewModel — é lá que ela é testada. `movimentacoes` entra como chave
        // só para a conta refazer quando o ledger chegar depois do diálogo já estar na tela.
        val quantos = remember(movimentacoes, data) { vm.anterioresA(data) }
        AlertDialog(
            onDismissRequest = { saldoPendente = null },
            title = { Text("saldo inicial") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("saldo de R$ ${centavos.centavosValor()} a partir de:")
                    InsetRow(label = data.format(dataCurta), onClick = { escolhendoData = true })
                    // Some com zero: quem reancora em hoje logo depois do onboarding não precisa
                    // ler um aviso sobre nada.
                    if (quantos > 0) {
                        Text(
                            if (quantos == 1) "1 lançamento anterior sai das contas"
                            else "$quantos lançamentos anteriores saem das contas",
                            style = SaldoTheme.type.footnote,
                            color = colors.secondaryLabel,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.definirSaldoInicial(centavos, data)
                    saldoPendente = null
                }) { Text("salvar") }
            },
            dismissButton = { TextButton(onClick = { saldoPendente = null }) { Text("cancelar") } },
        )

        if (escolhendoData) {
            val estado = rememberDatePickerState(
                initialSelectedDateMillis = data.toEpochDay() * 86_400_000L,
            )
            DatePickerDialog(
                onDismissRequest = { escolhendoData = false },
                confirmButton = {
                    TextButton(onClick = {
                        estado.selectedDateMillis?.let { data = LocalDate.ofEpochDay(it / 86_400_000L) }
                        escolhendoData = false
                    }) { Text("ok") }
                },
                dismissButton = { TextButton(onClick = { escolhendoData = false }) { Text("cancelar") } },
            ) { DatePicker(state = estado) }
        }
    }

    // Confirmação DIGITADA, e não um "tem certeza?": é a única ação do app sem volta nenhuma, e
    // um toque errado num botão vermelho não pode bastar.
    if (apagando) {
        var confirmacao by rememberSaveable { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { apagando = false },
            title = { Text("apagar dados") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "isto apaga todos os lançamentos, recorrências, tags e ajustes deste " +
                            "aparelho. escreva apagar para confirmar.",
                    )
                    OutlinedTextField(
                        value = confirmacao,
                        onValueChange = { confirmacao = it },
                        modifier = Modifier.testTag(TAG_CONFIRMACAO_APAGAR),
                        singleLine = true,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    // Aparado e em minúsculas: o teclado do Android capitaliza a primeira letra
                    // sozinho, e recusar "Apagar" seria implicância, não segurança.
                    enabled = confirmacao.trim().lowercase() == "apagar",
                    onClick = { apagando = false; vm.apagarTudo() },
                    modifier = Modifier.testTag(TAG_BOTAO_APAGAR),
                ) { Text("apagar") }
            },
            dismissButton = { TextButton(onClick = { apagando = false }) { Text("cancelar") } },
        )
    }

    // O restaurar tem dois diálogos porque tem dois desfechos: o arquivo não serve (uma frase e
    // "fechar"), ou serve e a próxima tela é a última chance de desistir.
    val restauracao by vm.restauracao.collectAsState()
    when (val r = restauracao) {
        null -> {}
        is Restauracao.Erro -> AlertDialog(
            onDismissRequest = vm::cancelarRestauracao,
            title = { Text("restaurar dados") },
            text = { Text(r.mensagem) },
            confirmButton = { TextButton(onClick = vm::cancelarRestauracao) { Text("fechar") } },
        )
        is Restauracao.Confirmar -> AlertDialog(
            onDismissRequest = vm::cancelarRestauracao,
            title = { Text("restaurar dados") },
            text = { Text(resumoDoDump(r.dump)) },
            confirmButton = { TextButton(onClick = { vm.restaurar(r.dump) }) { Text("substituir") } },
            dismissButton = { TextButton(onClick = vm::cancelarRestauracao) { Text("cancelar") } },
        )
    }
}
