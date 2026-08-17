package com.scholze.saldo.ui.mais

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.scholze.saldo.domain.LembretesConfig
import com.scholze.saldo.domain.Slot
import com.scholze.saldo.lembretes.Notificacoes
import com.scholze.saldo.ui.components.HairlineDivider
import com.scholze.saldo.ui.components.InsetGroup
import com.scholze.saldo.ui.components.InsetRow
import com.scholze.saldo.ui.theme.SaldoTheme
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private val hhmm = DateTimeFormatter.ofPattern("HH:mm")

/**
 * mais › lembretes: quatro switches e duas horas. Ligar um lembrete no Android 13+ sem a
 * permissão pede primeiro e só grava se ela vier; desligar nunca pede. Se algum lembrete está
 * ligado mas a permissão foi negada/revogada, uma linha leva às configurações do sistema.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LembretesScreen(
    config: LembretesConfig,
    onDefinir: (LembretesConfig) -> Unit,
    onVoltar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SaldoTheme.colors
    val context = LocalContext.current
    BackHandler(onBack = onVoltar)

    // `podeNotificar` não é observável: relido ao voltar do sistema (resume) e depois do pedido.
    var permitido by remember { mutableStateOf(Notificacoes.podeNotificar(context)) }
    LifecycleResumeEffect(Unit) {
        permitido = Notificacoes.podeNotificar(context)
        onPauseOrDispose { }
    }
    var pendente by remember { mutableStateOf<LembretesConfig?>(null) }
    val pedirPermissao = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { concedida ->
        permitido = concedida
        val p = pendente
        pendente = null
        if (concedida && p != null) onDefinir(p)
    }

    /** [ligando] = esta mudança liga um lembrete; só aí a permissão importa. */
    fun mudar(novo: LembretesConfig, ligando: Boolean) {
        if (ligando && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !permitido) {
            pendente = novo
            pedirPermissao.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            onDefinir(novo)
        }
    }

    var editandoHora by remember { mutableStateOf<Slot?>(null) }

    Column(modifier.fillMaxSize().background(colors.background)) {
        Box(Modifier.fillMaxWidth().background(colors.navBar).padding(vertical = 12.dp)) {
            Row(
                Modifier.align(Alignment.CenterStart).padding(start = 8.dp).clickable(onClick = onVoltar).padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("‹ mais", style = SaldoTheme.type.body, color = colors.tint)
            }
            Text(
                "lembretes", Modifier.fillMaxWidth(),
                style = SaldoTheme.type.navTitle, color = colors.label, textAlign = TextAlign.Center,
            )
        }
        HairlineDivider()

        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 32.dp)),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            if (config.algum && !permitido) {
                InsetGroup {
                    InsetRow(
                        label = "notificações desativadas no sistema",
                        value = "abrir ajustes",
                        valueColor = colors.tint,
                        onClick = {
                            context.startActivity(
                                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                            )
                        },
                    )
                }
            }

            InsetGroup {
                InsetRow(
                    label = "fatura vence amanhã",
                    trailing = { Switch(checked = config.faturaAmanha, onCheckedChange = { mudar(config.copy(faturaAmanha = it), ligando = it) }) },
                )
                HairlineDivider(startIndent = 16.dp)
                InsetRow(
                    label = "recorrência hoje",
                    trailing = { Switch(checked = config.recorrenciaHoje, onCheckedChange = { mudar(config.copy(recorrenciaHoje = it), ligando = it) }) },
                )
                HairlineDivider(startIndent = 16.dp)
                InsetRow(
                    label = "fechamento do mês",
                    trailing = { Switch(checked = config.fechamentoMes, onCheckedChange = { mudar(config.copy(fechamentoMes = it), ligando = it) }) },
                )
                HairlineDivider(startIndent = 16.dp)
                InsetRow(
                    label = "hora dos lembretes",
                    value = config.horaInformativos.format(hhmm),
                    onClick = { editandoHora = Slot.INFORMATIVOS },
                )
            }

            InsetGroup {
                InsetRow(
                    label = "registrar gastos",
                    trailing = { Switch(checked = config.registrarGastos, onCheckedChange = { mudar(config.copy(registrarGastos = it), ligando = it) }) },
                )
                HairlineDivider(startIndent = 16.dp)
                InsetRow(
                    label = "hora do lembrete de registrar",
                    value = config.horaNudge.format(hhmm),
                    onClick = { editandoHora = Slot.NUDGE },
                )
            }

            Text(
                "os lembretes são locais: nada sai do aparelho. na tela de bloqueio aparece só o título, sem valores.",
                Modifier.padding(horizontal = 16.dp),
                style = SaldoTheme.type.caption, color = colors.secondaryLabel,
            )
        }
    }

    editandoHora?.let { slot ->
        key(slot) {
            val atual = if (slot == Slot.INFORMATIVOS) config.horaInformativos else config.horaNudge
            val estado = rememberTimePickerState(initialHour = atual.hour, initialMinute = atual.minute, is24Hour = true)
            AlertDialog(
                onDismissRequest = { editandoHora = null },
                title = { Text(if (slot == Slot.INFORMATIVOS) "hora dos lembretes" else "hora do lembrete de registrar") },
                text = { TimePicker(state = estado) },
                confirmButton = {
                    TextButton(onClick = {
                        val hora = LocalTime.of(estado.hour, estado.minute)
                        val novo = if (slot == Slot.INFORMATIVOS) config.copy(horaInformativos = hora) else config.copy(horaNudge = hora)
                        mudar(novo, ligando = false)
                        editandoHora = null
                    }) { Text("salvar") }
                },
                dismissButton = { TextButton(onClick = { editandoHora = null }) { Text("cancelar") } },
            )
        }
    }
}
