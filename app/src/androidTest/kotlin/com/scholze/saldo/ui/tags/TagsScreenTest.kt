package com.scholze.saldo.ui.tags

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.scholze.saldo.EstadoLimpo
import com.scholze.saldo.SaldoApplication
import com.scholze.saldo.domain.PaletaTags
import com.scholze.saldo.domain.TagSnapshot
import com.scholze.saldo.ui.theme.SaldoTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TagsScreenTest {

    @get:Rule(order = 0) val estadoLimpo = EstadoLimpo()
    @get:Rule(order = 1) val rule = createComposeRule()

    private val container get() = ApplicationProvider.getApplicationContext<SaldoApplication>().container

    private fun montar(vm: TagsViewModel = TagsViewModel(container.repository)): TagsViewModel {
        rule.setContent { SaldoTheme { TagsScreen(vm = vm, onTagClick = {}) } }
        return vm
    }

    @Test
    fun semTagsATelaExplicaOQueEUmaTag() {
        montar()
        rule.onNodeWithText("uma tag é uma etiqueta", substring = true).assertIsDisplayed()
        rule.onAllNodesWithText("toque numa tag para ver só ela no ledger").assertCountEquals(0)
    }

    @Test
    fun excluirEmiteOSnapshotParaDesfazer() {
        runBlocking { container.repository.criarTag("mercado", PaletaTags.cores[0]) }
        val vm = montar()
        var snapshot: TagSnapshot? = null
        val escopo = CoroutineScope(Dispatchers.Main)
        val coleta = escopo.launch { snapshot = vm.exclusoes.first() }
        rule.waitUntil(5_000) { rule.onAllNodesWithContentDescription("excluir mercado").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithContentDescription("excluir mercado").performClick()
        rule.onNodeWithText("excluir").performClick()                // o botão do diálogo
        rule.waitUntil(5_000) { snapshot != null }
        assertEquals("mercado", snapshot!!.tag.nome)
        coleta.cancel()
    }

    @Test
    fun renomearTambemTrocaACor() {
        runBlocking { container.repository.criarTag("mercado", PaletaTags.cores[0]) }
        val vm = montar()
        rule.waitUntil(5_000) { rule.onAllNodesWithContentDescription("editar mercado").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithContentDescription("editar mercado").performClick()
        rule.onNodeWithContentDescription("cor 3").performClick()
        rule.onNodeWithText("salvar").performClick()
        rule.waitUntil(5_000) { runBlocking { container.repository.tags.first().single().cor } == PaletaTags.cores[2] }
    }

    @Test
    fun osBotoesDaLinhaTem48dp() {
        runBlocking { container.repository.criarTag("mercado", PaletaTags.cores[0]) }
        montar()
        rule.waitUntil(5_000) { rule.onAllNodesWithContentDescription("editar mercado").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithContentDescription("editar mercado").assertHeightIsAtLeast(44.dp)
        rule.onNodeWithContentDescription("excluir mercado").assertHeightIsAtLeast(44.dp)
    }
}
