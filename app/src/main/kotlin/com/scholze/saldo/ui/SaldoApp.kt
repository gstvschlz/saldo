package com.scholze.saldo.ui

import android.net.Uri
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.scholze.saldo.AppContainer
import com.scholze.saldo.BuildConfig
import com.scholze.saldo.data.Dump
import com.scholze.saldo.data.Exporters
import com.scholze.saldo.data.Settings
import com.scholze.saldo.ui.board.BoardScreen
import com.scholze.saldo.ui.board.BoardViewModel
import com.scholze.saldo.ui.entry.AmountKeypadScreen
import com.scholze.saldo.ui.entry.EntryViewModel
import com.scholze.saldo.ui.entry.NewEntrySheet
import com.scholze.saldo.ui.ledger.LedgerScreen
import com.scholze.saldo.ui.ledger.LedgerViewModel
import com.scholze.saldo.ui.mais.MaisScreen
import com.scholze.saldo.ui.mais.MaisViewModel
import com.scholze.saldo.domain.Movimentacao
import com.scholze.saldo.ui.nav.Destino
import com.scholze.saldo.ui.nav.ALTURA_BARRA
import com.scholze.saldo.ui.nav.ALTURA_FAIXA_FAB
import com.scholze.saldo.ui.nav.SaldoTab
import com.scholze.saldo.ui.nav.SaldoTabBar
import com.scholze.saldo.ui.components.InsetRow
import com.scholze.saldo.ui.privacy.LocalPrivacy
import com.scholze.saldo.ui.tags.TagsScreen
import com.scholze.saldo.ui.tags.TagsViewModel
import com.scholze.saldo.ui.theme.SaldoTheme
import com.scholze.saldo.ui.totais.RecorrenciasScreen
import com.scholze.saldo.ui.totais.RecorrenciasViewModel
import com.scholze.saldo.ui.totais.SegmentoTotais
import com.scholze.saldo.ui.totais.TotaisScreen
import com.scholze.saldo.ui.totais.TotaisViewModel
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * O que a aba `saldos` mostra. O board é a home; a lista é o mesmo mês em linhas (o
 * ledger com os chips); a tag é a lista filtrada por uma etiqueta, onde a aba tags e o
 * "ver tag" de totais aterrissam.
 */
private enum class VistaSaldos { BOARD, LISTA, TAG }

/**
 * The shell: onboarding gate, tabbed content, undo snackbar and the
 * nova-movimentação sheet sliding over the top.
 *
 * [settings] chega já coletado de [com.scholze.saldo.MainActivity] — que precisa dele para o
 * tema e a privacidade — em vez de este composable abrir um segundo coletor do mesmo fluxo.
 */
@Composable
fun SaldoApp(
    container: AppContainer,
    settings: Settings,
    destino: Destino? = null,
    onDestinoConsumido: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val colors = SaldoTheme.colors
    val s = settings

    if (s.saldoInicialCentavos == null) {
        // Onboarding: semear o saldo inicial com o teclado 1g.
        val scope = rememberCoroutineScope()
        // Sem saldo inicial não há para onde ir: o destino é descartado, não guardado.
        LaunchedEffect(destino) { if (destino != null) onDestinoConsumido() }
        AmountKeypadScreen(
            onContinue = { centavos ->
                scope.launch { container.settings.definirSaldoInicial(centavos, LocalDate.now()) }
            },
            titulo = "qual seu saldo hoje?",
            textoBotao = "começar",
            modifier = modifier.statusBarsPadding(),
        )
        return
    }

    // As factories são lembradas, não reconstruídas: `viewModel()` só consulta a factory na
    // primeira criação, então alocar uma nova a cada recomposição é lixo puro.
    val ledgerVm: LedgerViewModel = viewModel(factory = remember(container) { LedgerViewModel.factory(container) })
    val entryVm: EntryViewModel = viewModel(factory = remember(container) { EntryViewModel.factory(container) })
    val totaisVm: TotaisViewModel = viewModel(factory = remember(container) { TotaisViewModel.factory(container) })
    val boardVm: BoardViewModel = viewModel(factory = remember(container) { BoardViewModel.factory(container) })
    val tagsFactory = remember(container) { TagsViewModel.factory(container) }
    val tagsVm: TagsViewModel = viewModel(factory = tagsFactory)
    val maisFactory = remember(container) { MaisViewModel.factory(container) }
    val recorrenciasFactory = remember(container) { RecorrenciasViewModel.factory(container) }
    val ledgerState by ledgerVm.state.collectAsState()
    val totaisState by totaisVm.state.collectAsState()
    val boardState by boardVm.state.collectAsState()
    val privacidade = LocalPrivacy.current
    val snackbar = remember { SnackbarHostState() }

    var tab by rememberSaveable { mutableStateOf(SaldoTab.SALDOS) }
    // Só o "a sheet está aberta" é saveable; o formulário em si vive no [EntryViewModel]
    // e por isso sobrevive à rotação sem precisar ser serializado.
    var sheetAberto by rememberSaveable { mutableStateOf(false) }
    // A tela de recorrências toma a aba totais; sair da aba fecha (voltar depois em "totais"
    // deve mostrar totais, não a subtela onde o usuário estava dez minutos antes).
    // A aba `saldos` tem três vistas (ver VistaSaldos acima): o board é a home; a lista é o
    // mesmo mês em linhas, com busca; a tag é a lista filtrada por uma etiqueta, onde a aba
    // tags e o "ver tag" de totais aterrissam.
    var vistaSaldos by rememberSaveable { mutableStateOf(VistaSaldos.BOARD) }
    var abrindoRecorrencias by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(tab) { if (tab != SaldoTab.TOTAIS) abrindoRecorrencias = false }

    val alvoLedger by ledgerVm.alvo.collectAsState()
    val buscaLedger by ledgerVm.busca.collectAsState()
    val resultadosBusca by ledgerVm.resultados.collectAsState()

    // Fecha tudo que só faz sentido dentro da lista/etiqueta — a busca e o filtro de tag — e
    // volta a vista à grade, sincronizando o mês do board com o da lista (limitado a hoje).
    // Uma função só, chamada de toda saída da lista/etiqueta que não é o botão "voltar" dela
    // mesma: a barra de abas, o "ir para o dia" de totais, o BackHandler e o deep link de
    // saldos — para a busca nunca sobreviver aberta e escondida atrás do board.
    val voltarAoBoard: () -> Unit = {
        ledgerVm.fecharBusca()
        ledgerVm.definirTagFiltro(null)
        boardVm.sincronizarMes(ledgerVm.mesAtualAgora.coerceAtMost(YearMonth.now()))
        vistaSaldos = VistaSaldos.BOARD
    }

    // A pill "guardou N%" leva à poupança mês a mês, que mora em totais → tendência.
    val verGuardado: (YearMonth) -> Unit = { mes ->
        totaisVm.irPara(mes)
        totaisVm.selecionarSegmento(SegmentoTotais.TENDENCIA)
        abrindoRecorrencias = false
        tab = SaldoTab.TOTAIS
    }

    // Voltar: subtela → aba → saldos → sair. Um handler só, aqui, porque é aqui que as vistas
    // moram; a sheet e as subtelas de `mais` têm os seus e ganham por estarem mais fundo na
    // composição. Desabilitado no board para o sistema fechar o app — e só ele.
    //
    // A busca e a lista/etiqueta só contam enquanto a aba saldos está na tela: por isso o
    // "aba diferente de saldos" vem ANTES delas no `when` — sem essa guarda, uma busca ou
    // lista deixada aberta ao trocar de aba fazia Voltar precisar de dois toques para sair
    // da outra aba (o primeiro só fechava a subtela escondida). `vistaSaldos != BOARD` no
    // próprio `buscaAberta` é a segunda trava do mesmo problema: enquanto toda saída da
    // lista/etiqueta passar por `voltarAoBoard`, a busca nunca sobrevive com o board em
    // tela — mas se algum caminho novo esquecer, essa trava evita que ela prenda o Voltar
    // ali de qualquer forma.
    val buscaAberta = buscaLedger != null && vistaSaldos != VistaSaldos.BOARD
    BackHandler(
        enabled = !sheetAberto &&
            (abrindoRecorrencias || tab != SaldoTab.SALDOS || buscaAberta || vistaSaldos != VistaSaldos.BOARD),
    ) {
        when {
            abrindoRecorrencias -> abrindoRecorrencias = false
            tab != SaldoTab.SALDOS -> tab = SaldoTab.SALDOS
            buscaAberta -> ledgerVm.fecharBusca()
            vistaSaldos != VistaSaldos.BOARD -> voltarAoBoard()
            else -> {}
        }
    }

    // A rota de edição é usada de dois lugares (o ledger e a tela de recorrências), então mora
    // aqui: mesma guarda de sempre, ocorrência virtual (id 0) não abre o editor.
    val abrirMovimentacao: (Movimentacao) -> Unit = {
        if (it.id != 0L) {
            // O dia do template, não o da data: a data pode estar clamped (31 → 28 em fevereiro).
            val dia = it.recorrenciaId?.let { id -> ledgerState.recorrencias.firstOrNull { r -> r.id == id }?.diaDoMes }
            entryVm.iniciarEdicao(it, diaDoTemplate = dia)
            sheetAberto = true
        }
    }

    // Deep link (widget, lembrete): aplicado uma vez e devolvido como consumido, para que uma
    // recomposição — ou o mesmo Intent reentregue — não o reaplique.
    LaunchedEffect(destino) {
        when (destino) {
            null -> return@LaunchedEffect
            // Quem chega por widget ou lembrete pediu um dia, não um panorama.
            is Destino.Saldos -> {
                voltarAoBoard()
                boardVm.irPara(destino.mes, destino.dia)
                tab = SaldoTab.SALDOS
            }
            is Destino.NovaMovimentacao -> {
                entryVm.iniciarNova(
                    LocalDate.now(),
                    centavos = destino.centavos ?: 0L,
                    descricao = destino.descricao.orEmpty(),
                )
                // O widget "lançar" já diz de que lado é: pular esse toque é o ponto dele.
                destino.saida?.let { entryVm.definirSaida(it) }
                sheetAberto = true
            }
            is Destino.Totais -> { totaisVm.irPara(destino.mes); abrindoRecorrencias = false; tab = SaldoTab.TOTAIS }
        }
        onDestinoConsumido()
    }

    // Um só "desfazer" para as duas portas de exclusão: o swipe na linha (LedgerViewModel)
    // e o "excluir" dentro da sheet (EntryViewModel), que antes apagava sem volta.
    LaunchedEffect(Unit) {
        merge(ledgerVm.eventoExclusao, entryVm.exclusoes).collect { snapshot ->
            val resultado = snackbar.showSnackbar(
                message = "movimentação excluída",
                actionLabel = "desfazer",
            )
            if (resultado == SnackbarResult.ActionPerformed) ledgerVm.desfazerExclusao(snapshot)
        }
    }

    LaunchedEffect(Unit) {
        entryVm.erros.collect { snackbar.showSnackbar(it) }
    }

    LaunchedEffect(Unit) {
        tagsVm.exclusoes.collect { snapshot ->
            val resultado = snackbar.showSnackbar(message = "tag excluída", actionLabel = "desfazer")
            if (resultado == SnackbarResult.ActionPerformed) tagsVm.desfazerExclusao(snapshot)
        }
    }

    // ---- exportar dados (SAF) ----
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var escolhendoFormato by rememberSaveable { mutableStateOf(false) }

    // O conteúdo é montado e gravado fora da main thread; o Uri vem do seletor do sistema,
    // então o app nunca pede permissão de armazenamento nem escolhe pasta por conta própria.
    val gravar: (Uri, suspend () -> String) -> Unit = { uri, conteudo ->
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val texto = conteudo()
                    context.contentResolver.openOutputStream(uri)?.use { it.write(texto.toByteArray()) }
                        ?: error("sem stream de escrita para $uri")
                }
            }.onSuccess {
                snackbar.showSnackbar("dados exportados")
            }.onFailure { e ->
                Log.e("saldo", "exportar falhou", e)
                snackbar.showSnackbar("falha ao exportar")
            }
        }
    }

    // Um launcher por formato: o mime do CreateDocument é fixo na construção, e assim não
    // existe um "formato escolhido" guardado em estado para dessincronizar do arquivo.
    val exportarCsv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) gravar(uri) { Exporters.csvDoLedger(container.repository.ledger.first()) }
    }
    val exportarJson = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            gravar(uri) {
                Exporters.json(
                    Dump.de(
                        input = container.repository.ledger.first(),
                        tags = container.repository.tags.first(),
                        settings = container.settings.settings.first(),
                        app = BuildConfig.VERSION_NAME,
                    ),
                )
            }
        }
    }

    Box(modifier.fillMaxSize().background(colors.background)) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Box(Modifier.weight(1f)) {
                when (tab) {
                    SaldoTab.SALDOS -> if (vistaSaldos == VistaSaldos.BOARD) {
                        BoardScreen(
                            state = boardState,
                            // Tocar num dia abre os lançamentos dele embaixo da grade;
                            // tocar de novo no mesmo dia fecha.
                            onDiaClick = boardVm::alternarDia,
                            onMesAnterior = boardVm::mesAnterior,
                            onProximoMes = boardVm::proximoMes,
                            onVerLista = {
                                // LISTA é "sem filtro": uma etiqueta deixada pela vista TAG
                                // anterior não pode vazar para cá.
                                ledgerVm.definirTagFiltro(null)
                                ledgerVm.irPara(boardVm.mesAtualAgora)
                                vistaSaldos = VistaSaldos.LISTA
                            },
                            onItemClick = abrirMovimentacao,
                            // Mesmas guardas do ledger: ocorrência virtual (id 0) não abre o
                            // editor nem passa pelo delete, que a recusaria e faria o
                            // "desfazer" duplicar a linha.
                            onExcluir = { if (it.id != 0L) ledgerVm.excluir(it) },
                            onTogglePrivacidade = privacidade::alternar,
                            onVerGuardado = { verGuardado(boardVm.mesAtualAgora) },
                            onTentar = boardVm::tentarDeNovo,
                        )
                    } else {
                        LedgerScreen(
                            state = ledgerState,
                            onMesAnterior = ledgerVm::mesAnterior,
                            onProximoMes = ledgerVm::proximoMes,
                            onFiltro = ledgerVm::definirFiltro,
                            // id 0 = ocorrência virtual: o mês ainda não foi materializado (a
                            // `abrirMes` do ViewModel é assíncrona). Editá-la explodiria no save
                            // com SO_ESTE_MES, então a linha simplesmente não abre o editor.
                            onItemClick = abrirMovimentacao,
                            // Mesma razão: `repo.excluir` recusa id 0 (o delete seria no-op e o
                            // "desfazer" duplicaria a linha). Sem a guarda o swipe só produziria
                            // uma IllegalArgumentException engolida pelo ViewModel.
                            onExcluir = { if (it.id != 0L) ledgerVm.excluir(it) },
                            onTogglePrivacidade = privacidade::alternar,
                            onLimparTag = { ledgerVm.definirTagFiltro(null); vistaSaldos = VistaSaldos.LISTA },
                            onVerBoard = voltarAoBoard,
                            alvo = alvoLedger,
                            onAlvoConsumido = ledgerVm::limparAlvo,
                            busca = buscaLedger,
                            resultados = resultadosBusca,
                            onAbrirBusca = ledgerVm::abrirBusca,
                            onFecharBusca = ledgerVm::fecharBusca,
                            onBusca = ledgerVm::definirBusca,
                            onAbrirResultado = { ledgerVm.abrirResultado(it, abrirMovimentacao) },
                            contentPadding = PaddingValues(bottom = 24.dp),
                            onVerGuardado = { verGuardado(ledgerVm.mesAtualAgora) },
                            onTentar = ledgerVm::tentarDeNovo,
                        )
                    }
                    SaldoTab.TOTAIS -> if (abrindoRecorrencias) {
                        RecorrenciasScreen(
                            vm = viewModel(factory = recorrenciasFactory),
                            mes = totaisState.mesAtual,
                            onAbrirMovimentacao = abrirMovimentacao,
                            onVoltar = { abrindoRecorrencias = false },
                        )
                    } else {
                        TotaisScreen(
                            totaisVm,
                            onVerTag = { ledgerVm.definirTagFiltro(it); vistaSaldos = VistaSaldos.TAG; tab = SaldoTab.SALDOS },
                            onAbrirMovimentacao = abrirMovimentacao,
                            onIrParaDia = { mes, dia ->
                                voltarAoBoard()
                                boardVm.irPara(mes, dia)
                                tab = SaldoTab.SALDOS
                            },
                            onAbrirRecorrencias = { abrindoRecorrencias = true },
                        )
                    }
                    SaldoTab.TAGS -> TagsScreen(
                        vm = tagsVm,
                        onTagClick = { ledgerVm.definirTagFiltro(it); vistaSaldos = VistaSaldos.TAG; tab = SaldoTab.SALDOS },
                    )
                    SaldoTab.MAIS -> MaisScreen(
                        vm = viewModel(factory = maisFactory),
                        onExportar = { escolhendoFormato = true },
                    )
                }
            }
            SaldoTabBar(
                selected = tab,
                // Tocar em `saldos` na barra é pedir a home: fecha a subtela da etiqueta.
                // E trocar de aba PARA FORA de saldos também volta a vista ao board — ela é
                // estado só da aba saldos, então deixá-la em lista/tag ao sair faria Voltar
                // (que só olha vistaSaldos com a aba saldos em tela) precisar de dois toques
                // para sair da aba nova. `voltarAoBoard` roda para QUALQUER destino, não só
                // "saldos": uma busca deixada aberta ao trocar para "totais", por exemplo,
                // sobrevivia escondida e fazia o Voltar seguinte gastar um toque fechando-a
                // sem nada mudar na tela.
                onSelect = { novo ->
                    if (vistaSaldos != VistaSaldos.BOARD) voltarAoBoard()
                    tab = novo
                },
                // O `+` lança no dia aberto do board — é o dia que o usuário está olhando.
                // Fora do board (lista, outras abas) é hoje, como sempre foi.
                onAdd = {
                    val dia = if (tab == SaldoTab.SALDOS && vistaSaldos == VistaSaldos.BOARD) boardVm.diaAbertoAgora else null
                    entryVm.iniciarNova(dia ?: LocalDate.now())
                    sheetAberto = true
                },
            )
        }

        // Scrim. Além de escurecer, é ele que ENGOLE o toque: a sheet é uma irmã do ledger
        // dentro deste Box, e nas áreas dela sem nada clicável o hit test caía direto no
        // ledger de trás — dava para arrastar o mês e tocar em linha por baixo da sheet.
        AnimatedVisibility(visible = sheetAberto, enter = fadeIn(), exit = fadeOut()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.35f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { sheetAberto = false },
            )
        }

        AnimatedVisibility(visible = sheetAberto, enter = slideInVertically { it }, exit = slideOutVertically { it }) {
            NewEntrySheet(
                vm = entryVm,
                onFechar = { sheetAberto = false },
                modifier = Modifier.statusBarsPadding(),
            )
        }

        // Por último no Box, portanto por cima da sheet: um erro de gravação tem de ser
        // visível justamente quando a sheet continua aberta.
        // 70.dp era a altura da barra do HIG. A barra do M3 mede ALTURA_FAIXA_FAB +
        // ALTURA_BARRA acima do inset, e com o valor antigo o snackbar aparecia POR
        // DENTRO dela. Derivado das constantes para não desencontrar de novo.
        SnackbarHost(
            snackbar,
            Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = ALTURA_FAIXA_FAB + ALTURA_BARRA + 4.dp),
        )

        if (escolhendoFormato) {
            AlertDialog(
                onDismissRequest = { escolhendoFormato = false },
                title = { Text("exportar dados") },
                // Os dois formatos no corpo, como linhas: "json" no slot de cancelar lia como
                // cancelar, e não havia cancelar de verdade.
                text = {
                    Column {
                        InsetRow(
                            label = "csv",
                            value = "abre em planilha",
                            onClick = { escolhendoFormato = false; exportarCsv.launch("saldo-export.csv") },
                        )
                        InsetRow(
                            label = "json",
                            value = "o dump completo",
                            onClick = { escolhendoFormato = false; exportarJson.launch("saldo-export.json") },
                        )
                    }
                },
                confirmButton = {},
                dismissButton = { TextButton(onClick = { escolhendoFormato = false }) { Text("cancelar") } },
            )
        }
    }
}
