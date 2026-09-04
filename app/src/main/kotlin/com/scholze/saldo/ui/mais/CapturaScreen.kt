package com.scholze.saldo.ui.mais

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.scholze.saldo.domain.CapturaConfig
import com.scholze.saldo.ui.components.InsetGroup
import com.scholze.saldo.ui.components.InsetRow
import com.scholze.saldo.ui.theme.SaldoTheme

const val TAG_CAPTURA_ACESSO = "captura:acesso"
const val TAG_CAPTURA_INTERRUPTOR = "captura:interruptor"
const val TAG_CAPTURA_VAZIO = "captura:vazio"

/**
 * mais › notificações: o acesso do sistema, o interruptor mestre e a lista de apps.
 *
 * A tela diz a verdade inteira logo no primeiro parágrafo, e isso é de propósito. O acesso a
 * notificações **não tem diálogo de permissão** — o app só consegue abrir a página do sistema
 * e mostrar o estado real — e, uma vez ligado, o Android entrega ao saldo o texto de toda
 * notificação do aparelho. O filtro por app é o que o saldo faz com o que chega, não o que
 * ele recebe, e quem liga isso precisa saber disso antes, não depois.
 *
 * A lista é **descoberta**: um app aparece aqui depois de emitir uma notificação com valor.
 * Enumerar os instalados exigiria `QUERY_ALL_PACKAGES`, permissão sensível da Play que
 * contradiz a postura do app.
 */
@Composable
fun CapturaScreen(
    config: CapturaConfig,
    onLigar: (Boolean) -> Unit,
    onMarcarApp: (String, Boolean) -> Unit,
    onVoltar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    // Não é observável: o acesso só muda nas configurações do sistema, então é relido ao
    // voltar de lá. Fica aqui, e não no conteúdo, para o conteúdo poder ser testado nos dois
    // estados sem depender de como o emulador está configurado.
    var acesso by remember { mutableStateOf(temAcesso(context)) }
    LifecycleResumeEffect(Unit) {
        acesso = temAcesso(context)
        onPauseOrDispose { }
    }

    CapturaConteudo(
        config = config,
        acesso = acesso,
        onLigar = onLigar,
        onMarcarApp = onMarcarApp,
        onAbrirAcesso = {
            // "abrir configurações", não "permitir": o app não pode conceder isto.
            runCatching {
                context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }.onFailure { Log.w("saldo", "acesso a notificações indisponível", it) }
        },
        onVoltar = onVoltar,
        modifier = modifier,
    )
}

/** A tela sem o estado do sistema: é esta que os testes montam, nos dois valores de [acesso]. */
@Composable
fun CapturaConteudo(
    config: CapturaConfig,
    acesso: Boolean,
    onLigar: (Boolean) -> Unit,
    onMarcarApp: (String, Boolean) -> Unit,
    onAbrirAcesso: () -> Unit,
    onVoltar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SaldoTheme.colors
    val context = LocalContext.current
    BackHandler(onBack = onVoltar)

    val naLista = (config.marcados + config.vistos).sortedBy { rotuloDe(context, it).lowercase() }

    Column(modifier.fillMaxSize().background(colors.background)) {
        Column(Modifier.fillMaxWidth()) {
            Text(
                "‹ mais",
                Modifier.clickable(role = Role.Button, onClick = onVoltar).padding(horizontal = 16.dp, vertical = 12.dp),
                style = SaldoTheme.type.body, color = colors.tint,
            )
            Text(
                "notificações",
                Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 14.dp),
                style = SaldoTheme.type.navTitle, color = colors.label,
            )
        }

        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 32.dp)),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Text(
                "com o acesso ligado, o android entrega ao saldo o texto de toda notificação do " +
                    "aparelho — não dá para pedir menos. o saldo só lê os apps que você marcar aqui, " +
                    "e não guarda o texto de nenhuma delas.",
                Modifier.padding(horizontal = 16.dp),
                style = SaldoTheme.type.footnote, color = colors.secondaryLabel,
            )

            InsetGroup {
                InsetRow(
                    label = "acesso a notificações",
                    value = if (acesso) "ligado" else "abrir configurações do sistema",
                    valueColor = if (acesso) colors.secondaryLabel else colors.tint,
                    modifier = Modifier.testTag(TAG_CAPTURA_ACESSO),
                    onClick = onAbrirAcesso,
                )
            }

            InsetGroup {
                InsetRow(
                    label = "sugerir lançamento",
                    modifier = Modifier.testTag(TAG_CAPTURA_INTERRUPTOR),
                    trailing = {
                        // Sem o acesso do sistema o interruptor não teria efeito nenhum, e um
                        // switch que não faz nada é pior do que um desabilitado.
                        Switch(checked = config.ligada && acesso, enabled = acesso, onCheckedChange = onLigar)
                    },
                )
            }

            if (naLista.isEmpty()) {
                Text(
                    "nenhum app ainda — quando um app mandar uma notificação com valor em reais, " +
                        "ele aparece aqui esperando a sua marcação.",
                    Modifier.padding(horizontal = 16.dp).testTag(TAG_CAPTURA_VAZIO),
                    style = SaldoTheme.type.footnote, color = colors.secondaryLabel,
                )
            } else {
                InsetGroup {
                    naLista.forEach { pacote ->
                        InsetRow(
                            label = rotuloDe(context, pacote),
                            trailing = {
                                Switch(
                                    checked = pacote in config.marcados,
                                    onCheckedChange = { onMarcarApp(pacote, it) },
                                )
                            },
                        )
                    }
                }
            }

            Text(
                "o que fica gravado de cada detecção é o nome do pacote, o valor e a hora — nunca " +
                    "o texto — e some sozinho em 24 horas. o lançamento entra como saída do dia, " +
                    "em diários; para pôr no cartão ou trocar o sinal, toque no corpo da sugestão.",
                Modifier.padding(horizontal = 16.dp),
                style = SaldoTheme.type.footnote, color = colors.secondaryLabel,
            )
        }
    }
}

/** O estado real do acesso, que é do sistema e não do app. */
private fun temAcesso(context: Context): Boolean =
    NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

/** Consulta por pacote, que não exige `QUERY_ALL_PACKAGES`; app desinstalado mostra o pacote. */
private fun rotuloDe(context: Context, pacote: String): String = runCatching {
    val pm = context.packageManager
    pm.getApplicationLabel(pm.getApplicationInfo(pacote, 0)).toString()
}.getOrDefault(pacote)
