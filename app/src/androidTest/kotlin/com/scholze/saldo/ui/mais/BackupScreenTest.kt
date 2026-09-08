package com.scholze.saldo.ui.mais

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.scholze.saldo.domain.BackupConfig
import com.scholze.saldo.domain.Cadencia
import com.scholze.saldo.ui.theme.SaldoTheme
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A tela do backup mostra o estado real — a pasta, o último sucesso e o motivo da última falha —
 * e, sem pasta, os controles que não teriam onde gravar ficam desabilitados de verdade.
 */
@RunWith(AndroidJUnit4::class)
class BackupScreenTest {

    @get:Rule
    val rule = createComposeRule()

    private var cadencia: Cadencia? = null
    private var agoras = 0

    private fun tela(config: BackupConfig) {
        rule.setContent {
            SaldoTheme {
                BackupScreen(
                    config = config,
                    onEscolherPasta = {},
                    onCadencia = { cadencia = it },
                    onAgora = { agoras++ },
                    onVoltar = {},
                )
            }
        }
    }

    @Test
    fun semPastaOsControlesFicamDesabilitados() {
        tela(BackupConfig())
        rule.onNodeWithText("escolher pasta").assertIsDisplayed()
        rule.onNodeWithTag(TAG_BACKUP_QUANDO).assertIsNotEnabled()
        rule.onNodeWithTag(TAG_BACKUP_AGORA).assertIsNotEnabled()
        // Desabilitado é desabilitado: o toque chega ao nó e não vira nada.
        rule.onNodeWithTag(TAG_BACKUP_AGORA).performClick()
        assertEquals(0, agoras)
    }

    @Test
    fun comPastaOsControlesFuncionam() {
        tela(BackupConfig(pastaUri = "content://tree/quintal", cadencia = Cadencia.SEMANAL))
        rule.onNodeWithTag(TAG_BACKUP_QUANDO).assertIsEnabled()
        rule.onNodeWithTag(TAG_BACKUP_QUANDO).performClick()
        rule.onNodeWithText("diário").performClick()
        assertEquals(Cadencia.DIARIO, cadencia)

        rule.onNodeWithTag(TAG_BACKUP_AGORA).performClick()
        assertEquals(1, agoras)
    }

    @Test
    fun oUltimoSucessoApareceNaTela() {
        tela(
            BackupConfig(
                pastaUri = "content://tree/quintal",
                ultimoSucesso = LocalDate.parse("2026-09-07"),
            ),
        )
        rule.onNodeWithText("07/09/2026").assertIsDisplayed()
    }

    /** Não basta "falhou": a linha diz o que fazer a respeito. */
    @Test
    fun oErroGravadoApareceNaLinha() {
        tela(
            BackupConfig(
                pastaUri = "content://tree/quintal",
                ultimoErro = "escolha a pasta de novo",
                ultimoErroEm = LocalDate.parse("2026-09-07"),
            ),
        )
        rule.onNodeWithText("escolha a pasta de novo").assertIsDisplayed()
    }

    @Test
    fun oResumoDaLinhaDeMais() {
        assertEquals("desligado", resumoBackup(BackupConfig()))
        assertEquals(
            "semanal · último em 07/09",
            resumoBackup(BackupConfig(pastaUri = "x", ultimoSucesso = LocalDate.parse("2026-09-07"))),
        )
        assertEquals("semanal · nunca rodou", resumoBackup(BackupConfig(pastaUri = "x")))
        assertEquals(
            "falhou em 07/09",
            resumoBackup(
                BackupConfig(
                    pastaUri = "x",
                    ultimoErro = "disco cheio",
                    ultimoErroEm = LocalDate.parse("2026-09-07"),
                ),
            ),
        )
    }
}
