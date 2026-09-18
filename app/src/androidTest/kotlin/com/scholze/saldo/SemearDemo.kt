package com.scholze.saldo

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.scholze.saldo.domain.Movimentacao
import com.scholze.saldo.domain.Natureza
import com.scholze.saldo.domain.PaletaTags
import com.scholze.saldo.domain.RepetirOpcao
import com.scholze.saldo.domain.Tag
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith

private const val APP_SMS = "com.google.android.apps.messaging"

/**
 * Marca o que não é teste. `app/build.gradle.kts` passa esta anotação como `notAnnotation`, então
 * `connectedDebugAndroidTest` pula quem a carrega — um `mise run test-device` não pode encher o
 * device de dados falsos por acidente.
 */
annotation class Semeadura

/**
 * Semeia o aparelho com quatro meses plausíveis, para as capturas de tela do README.
 *
 * NÃO é um teste: não afirma nada, e é o único arquivo daqui que deixa estado para trás de
 * propósito. Escreve pelo mesmo repositório que a UI usa — nada de SQL à mão —, então o que
 * aparece na captura é o app calculando, não um banco montado por fora.
 *
 * Quatro meses e não um: a tendência, o ritmo e a fatura só têm o que dizer com histórico. O
 * mês corrente para no dia de hoje; os anteriores vão inteiros, com os valores 4% menores a
 * cada mês para trás e uma linha em cada quatro fora, para as barras não saírem iguais.
 */
@Semeadura
@RunWith(AndroidJUnit4::class)
class SemearDemo {
    private val hoje = LocalDate.now()
    private val mes = YearMonth.from(hoje)
    private val primeiroMes = mes.minusMonths(3)

    /** Dia do mês, descrição, centavos (negativo = saída), etiqueta e natureza. */
    private data class Lancamento(
        val dia: Int,
        val descricao: String,
        val centavos: Long,
        val etiqueta: String? = null,
        val natureza: Natureza = Natureza.DIARIO,
    )

    private val fixas = listOf(
        Lancamento(5, "salário", 5_200_00),
        Lancamento(5, "aluguel", -1_680_00, "casa"),
        Lancamento(8, "internet", -109_90, "casa"),
        Lancamento(12, "academia", -119_90, "saúde"),
        Lancamento(22, "streaming", -55_90, "lazer"),
        Lancamento(25, "celular", -49_90, "casa"),
    )

    private val variaveis = listOf(
        Lancamento(1, "mercado do mês", -238_47, "mercado"),
        Lancamento(2, "café", -14_50, "comida"),
        Lancamento(3, "uber", -27_90, "transporte"),
        Lancamento(3, "farmácia", -62_30, "saúde"),
        Lancamento(4, "almoço", -38_00, "comida"),
        Lancamento(5, "reserva", -800_00, natureza = Natureza.ECONOMIA),
        Lancamento(6, "feira", -84_20, "mercado"),
        Lancamento(7, "cinema", -72_00, "lazer", Natureza.CARTAO),
        Lancamento(8, "gasolina", -210_00, "transporte"),
        Lancamento(9, "padaria", -23_80, "comida"),
        Lancamento(10, "freela", 850_00),
        Lancamento(11, "mercado", -96_40, "mercado"),
        Lancamento(12, "uber", -31_20, "transporte"),
        Lancamento(13, "pizza", -89_90, "comida", Natureza.CARTAO),
        Lancamento(14, "livraria", -112_00, "lazer", Natureza.CARTAO),
        Lancamento(15, "mercado", -276_15, "mercado"),
        Lancamento(16, "café", -16_00, "comida"),
        Lancamento(16, "ônibus", -9_20, "transporte"),
        Lancamento(17, "almoço", -42_50, "comida"),
        Lancamento(17, "estacionamento", -18_00),
        Lancamento(19, "mercado", -184_30, "mercado"),
        Lancamento(21, "uber", -34_60, "transporte"),
        Lancamento(22, "farmácia", -47_90, "saúde"),
        Lancamento(24, "almoço", -45_00, "comida"),
        Lancamento(26, "mercado", -201_75, "mercado"),
        Lancamento(28, "bar", -96_00, "lazer", Natureza.CARTAO),
    )

    @Test
    fun semear() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<SaldoApplication>()
        val repo = app.container.repository
        val settings = app.container.settings

        settings.limpar()
        app.container.database.clearAllTables()
        settings.definirSaldoInicial(4_100_00, primeiroMes.atDay(1))
        // As capturas mostram os números; o borrão da privacidade tem a captura dele.
        settings.definirComecarOculto(false)
        // A captura de notificações, ligada e com o app de SMS do emulador já marcado: um
        // `adb emu sms send` com um valor em reais basta para a sugestão aparecer na barra.
        // O acesso em si é do sistema, não daqui:
        // `adb shell cmd notification allow_listener com.scholze.saldo/.captura.EscutaNotificacoes`.
        settings.definirCapturaLigada(true)
        settings.registrarAppVisto(APP_SMS)
        settings.definirAppMarcado(APP_SMS, true)

        val etiquetas = listOf("mercado", "transporte", "casa", "comida", "lazer", "saúde")
            .mapIndexed { i, nome ->
                val cor = PaletaTags.cores[i]
                nome to Tag(repo.criarTag(nome, cor), nome, cor)
            }.toMap()

        suspend fun lancar(m: YearMonth, l: Lancamento, centavos: Long, repetir: RepetirOpcao) {
            val data = m.atDay(minOf(l.dia, m.lengthOfMonth()))
            repo.criar(
                Movimentacao(
                    descricao = l.descricao,
                    valorCentavos = centavos,
                    data = data,
                    natureza = l.natureza,
                    tags = listOfNotNull(l.etiqueta?.let { etiquetas.getValue(it) }),
                    criadaEm = data.toEpochDay() * 86_400_000L,
                ),
                repetir,
            )
        }

        // As fixas nascem no primeiro mês e se repetem; `abrirMes` materializa cada mês seguinte.
        fixas.forEach { lancar(primeiroMes, it, it.centavos, RepetirOpcao.TodoMes(it.dia)) }
        (0..3).forEach { repo.abrirMes(primeiroMes.plusMonths(it.toLong())) }

        // As variáveis: o que de fato aconteceu. O mês corrente para em hoje.
        (3 downTo 0).forEach { atras ->
            val m = mes.minusMonths(atras.toLong())
            variaveis
                .filterIndexed { i, _ -> atras == 0 || (i + atras) % 4 != 0 }
                .filter { atras > 0 || it.dia <= hoje.dayOfMonth }
                .forEach { lancar(m, it, it.centavos * (100 - 4 * atras) / 100, RepetirOpcao.Nao) }
        }
    }
}
