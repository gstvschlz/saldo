package com.scholze.saldo.ui.mais

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.scholze.saldo.AppContainer
import com.scholze.saldo.data.ArquivoInvalido
import com.scholze.saldo.data.Dump
import com.scholze.saldo.data.Importers
import com.scholze.saldo.data.LeitorDeArquivo
import com.scholze.saldo.data.SaldoRepository
import com.scholze.saldo.data.Settings
import com.scholze.saldo.data.SettingsStore
import com.scholze.saldo.data.Tema
import com.scholze.saldo.domain.CartaoConfig
import com.scholze.saldo.domain.LembretesConfig
import com.scholze.saldo.lembretes.LembretesScheduler
import com.scholze.saldo.ui.components.MENSAGEM_ERRO_LEITURA
import java.time.LocalDate
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** O que a tela mostra depois de escolher um arquivo. */
sealed interface Restauracao {
    /** O arquivo é válido: o diálogo pergunta antes de substituir. */
    data class Confirmar(val dump: Dump) : Restauracao

    /** O arquivo não serve; [mensagem] vem do `Importers` e é texto de tela. */
    data class Erro(val mensagem: String) : Restauracao
}

class MaisViewModel(
    private val settingsStore: SettingsStore,
    private val repository: SaldoRepository,
    private val scheduler: LembretesScheduler,
    private val leitor: LeitorDeArquivo,
    /** Reservado ao "apagar dados": tira lembretes e sugestões da barra de uma vez. */
    private val limparNotificacoes: () -> Unit,
    private val limparSugestoes: () -> Unit,
) : ViewModel() {

    private val tentativas = MutableStateFlow(0)
    private val _erro = MutableStateFlow<String?>(null)

    /** Mensagem de falha ao ler os ajustes; `null` = está tudo bem. */
    val erro: StateFlow<String?> = _erro

    /** O botão "tentar de novo" da tela: reassina o fluxo dos ajustes. */
    fun tentarDeNovo() {
        _erro.value = null
        tentativas.value++
    }

    // O estado desta tela é o `Settings?` cru — não há um UiState para carregar um campo `erro`,
    // e inventar um wrapper só por isto custaria mais do que compra. Então o erro anda ao lado,
    // num fluxo próprio; o `flatMapLatest` sobre [tentativas] faz o mesmo papel de `fluxoComErro`:
    // o botão REASSINA a leitura, em vez de só apagar a mensagem de uma tela morta.
    @OptIn(ExperimentalCoroutinesApi::class)
    val settings: StateFlow<Settings?> = tentativas
        .flatMapLatest {
            settingsStore.settings
                .onEach { _erro.value = null }
                .catch { e ->
                    Log.e(TAG, "fluxo de ajustes falhou", e)
                    _erro.value = MENSAGEM_ERRO_LEITURA
                }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * Reancora o saldo inicial em hoje — é a semântica pretendida ("meu saldo HOJE é X"),
     * e não uma correção retroativa: o engine ignora tudo antes de `saldoInicialData`,
     * então movimentações anteriores deixam de contar a partir daqui.
     */
    fun definirSaldoInicial(centavos: Long) =
        escrever("definirSaldoInicial") { settingsStore.definirSaldoInicial(centavos, LocalDate.now()) }

    fun definirCartao(config: CartaoConfig) = escrever("definirCartao") { settingsStore.definirCartao(config) }
    fun definirComecarOculto(v: Boolean) = escrever("definirComecarOculto") { settingsStore.definirComecarOculto(v) }
    fun definirTema(t: Tema) = escrever("definirTema") { settingsStore.definirTema(t) }

    fun definirWidgetMostrarValores(v: Boolean) =
        escrever("definirWidgetMostrarValores") { settingsStore.definirWidgetMostrarValores(v) }

    fun definirCapturaLigada(v: Boolean) =
        escrever("definirCapturaLigada") { settingsStore.definirCapturaLigada(v) }

    fun definirAppMarcado(pacote: String, marcado: Boolean) =
        escrever("definirAppMarcado") { settingsStore.definirAppMarcado(pacote, marcado) }

    /** Grava e (re)agenda: os toggles e as horas só valem quando o WorkManager sabe deles. */
    fun definirLembretes(config: LembretesConfig) = escrever("definirLembretes") {
        settingsStore.definirLembretes(config)
        scheduler.agendar(config)
    }

    // ---- restaurar ----

    private val _restauracao = MutableStateFlow<Restauracao?>(null)

    /** `null` = nenhum arquivo em jogo. Ver [prepararRestauracao]. */
    val restauracao: StateFlow<Restauracao?> = _restauracao

    private val _avisos = MutableSharedFlow<String>(extraBufferCapacity = 1)

    /** Frases para o snackbar da shell ("dados restaurados"). */
    val avisos: SharedFlow<String> = _avisos

    /**
     * Lê e VALIDA o arquivo, sem tocar em nada. Uma falha aqui é [Restauracao.Erro] e o banco
     * continua exatamente como estava — é o ponto da ordem "valida tudo em memória, depois grava".
     */
    fun prepararRestauracao(uri: Uri) {
        viewModelScope.launch {
            _restauracao.value = try {
                Restauracao.Confirmar(Importers.json(leitor.ler(uri)))
            } catch (e: CancellationException) {
                throw e
            } catch (e: ArquivoInvalido) {
                Restauracao.Erro(e.message ?: "arquivo inválido")
            } catch (e: Exception) {
                Log.e(TAG, "ler o arquivo falhou", e)
                Restauracao.Erro("não deu para ler o arquivo")
            }
        }
    }

    fun cancelarRestauracao() {
        _restauracao.value = null
    }

    /**
     * Grava, na ordem da decisão 2 do spec: banco primeiro (uma transação — falhou, nada mudou),
     * ajustes depois. Room e DataStore não compartilham transação, então o caso raro de os ajustes
     * falharem DEPOIS do commit existe — e a mensagem diz exatamente isso em vez de mentir.
     */
    fun restaurar(dump: Dump) {
        viewModelScope.launch {
            _restauracao.value = null
            try {
                repository.substituirTudo(dump)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "substituirTudo falhou", e)
                _avisos.emit("não deu para restaurar: ${e.message ?: "erro no banco"}")
                return@launch
            }
            try {
                settingsStore.substituir(dump.settings)
                // Não existe "sincronizar": `agendar` com REPLACE (o padrão) é exatamente
                // "reagenda tudo a partir desta config", e é o que o toggle da tela já usa.
                scheduler.agendar(dump.settings.lembretes)
                limparSugestoes()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "ajustes do restaurar falharam", e)
                _avisos.emit("dados restaurados, ajustes não — confira saldo inicial e cartão em mais")
                return@launch
            }
            _avisos.emit("dados restaurados")
        }
    }

    private fun escrever(qual: String, bloco: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                bloco()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "$qual falhou", e)
            }
        }
    }

    companion object {
        private const val TAG = "saldo"

        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                MaisViewModel(
                    settingsStore = container.settings,
                    repository = container.repository,
                    scheduler = container.lembretesScheduler,
                    leitor = container.leitorDeArquivo,
                    limparNotificacoes = container.limparNotificacoes,
                    limparSugestoes = container.limparSugestoes,
                )
            }
        }
    }
}
