package com.scholze.saldo.ui.mais

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.scholze.saldo.domain.BackupConfig
import com.scholze.saldo.domain.Cadencia
import com.scholze.saldo.ui.components.FilledActionButton
import com.scholze.saldo.ui.components.InsetGroup
import com.scholze.saldo.ui.components.InsetRow
import com.scholze.saldo.ui.theme.SaldoTheme
import java.time.format.DateTimeFormatter

const val TAG_BACKUP_QUANDO = "backup:quando"
const val TAG_BACKUP_AGORA = "backup:agora"

private val diaMes = DateTimeFormatter.ofPattern("dd/MM")
private val diaMesAno = DateTimeFormatter.ofPattern("dd/MM/yyyy")

/**
 * O resumo do backup na linha de `mais` — um estado, não um histórico.
 *
 * A ordem importa: um erro é a notícia mais recente e vem primeiro, porque é ele que pede uma ação.
 */
fun resumoBackup(config: BackupConfig): String = when {
    !config.ligado -> "desligado"
    config.ultimoErro != null ->
        "falhou" + (config.ultimoErroEm?.let { " em ${it.format(diaMes)}" } ?: "")
    config.ultimoSucesso != null -> "${config.cadencia.rotulo} · último em ${config.ultimoSucesso.format(diaMes)}"
    else -> "${config.cadencia.rotulo} · nunca rodou"
}

/**
 * mais › backup automático: a pasta, a cadência, o "agora" e o estado da última tentativa.
 *
 * Sem pasta escolhida, `quando` e `agora` ficam desabilitados — não teriam onde gravar. O "agora"
 * existe porque, sem ele, ligar um backup semanal significa uma semana sem saber se funciona; com
 * ele, o arquivo aparece na pasta antes de alguém precisar confiar nele.
 */
@Composable
fun BackupScreen(
    config: BackupConfig,
    onEscolherPasta: () -> Unit,
    onCadencia: (Cadencia) -> Unit,
    onAgora: () -> Unit,
    onVoltar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SaldoTheme.colors
    BackHandler(onBack = onVoltar)
    var escolhendoQuando by remember { mutableStateOf(false) }

    Column(modifier.fillMaxSize().background(colors.background)) {
        // Mesma carcaça de `lembretes`: a volta em cima com 48dp de alvo, o título grande e à
        // esquerda embaixo. É uma subtela de `mais` como aquela.
        Column(Modifier.fillMaxWidth()) {
            Text(
                "‹ mais",
                Modifier.clickable(role = Role.Button, onClick = onVoltar).padding(horizontal = 16.dp, vertical = 12.dp),
                style = SaldoTheme.type.body, color = colors.tint,
            )
            Text(
                "backup automático",
                Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 14.dp),
                style = SaldoTheme.type.navTitle, color = colors.label,
            )
        }

        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 32.dp)),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            InsetGroup {
                InsetRow(
                    label = "pasta",
                    // O `Uri` da árvore é ilegível; o que se mostra é o último pedaço dele, que é o
                    // nome que o usuário reconhece.
                    value = config.pastaUri?.let(::nomeLegivel) ?: "escolher pasta",
                    valueColor = colors.tint,
                    onClick = onEscolherPasta,
                )
                InsetRow(
                    label = "quando",
                    value = config.cadencia.rotulo,
                    modifier = Modifier.testTag(TAG_BACKUP_QUANDO),
                    habilitado = config.ligado,
                    onClick = { escolhendoQuando = true },
                )
            }

            InsetGroup {
                InsetRow(
                    label = "último backup",
                    value = config.ultimoSucesso?.format(diaMesAno) ?: "nunca",
                )
                // O motivo por extenso, e não só "falhou": é ele que diz se a ação é escolher a
                // pasta de novo ou abrir espaço no aparelho.
                config.ultimoErro?.let { erro ->
                    Text(
                        erro,
                        Modifier.padding(start = 16.dp, end = 16.dp, bottom = 10.dp),
                        style = SaldoTheme.type.caption, color = colors.secondaryLabel,
                    )
                }
            }

            FilledActionButton(
                text = "fazer backup agora",
                onClick = onAgora,
                enabled = config.ligado,
                modifier = Modifier.testTag(TAG_BACKUP_AGORA),
            )

            Text(
                "o saldo grava o mesmo arquivo do \"exportar dados\" na pasta que você escolher, e " +
                    "guarda os sete mais recentes. se essa pasta for sincronizada pelo aparelho, o " +
                    "histórico sobrevive a ele — o app continua sem tocar na rede.",
                Modifier.padding(horizontal = 16.dp),
                style = SaldoTheme.type.caption, color = colors.secondaryLabel,
            )
        }
    }

    if (escolhendoQuando) {
        AlertDialog(
            onDismissRequest = { escolhendoQuando = false },
            title = { Text("quando") },
            text = {
                Column {
                    Cadencia.entries.forEach { c ->
                        val atual = c == config.cadencia
                        TextButton(onClick = { onCadencia(c); escolhendoQuando = false }) {
                            Text(
                                c.rotulo,
                                // Como no diálogo do tema: três botões iguais não dizem qual está
                                // valendo, então o atual vem em semibold e na cor de label.
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
            dismissButton = { TextButton(onClick = { escolhendoQuando = false }) { Text("fechar") } },
        )
    }
}

/** O último segmento do `Uri` da árvore — "primary:Documentos/saldo" vira "saldo". */
private fun nomeLegivel(uri: String): String =
    uri.substringAfterLast("%2F").substringAfterLast('/').substringAfterLast(':').ifBlank { "pasta escolhida" }
