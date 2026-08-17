# saldo — insights 1 (views) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Turn the *totais* tab into the insights home — segments **mês | tendência | a caminho** with "para onde foi" (segmented bar by tag, maiores gastos, padrões), a 6-month trend + poupança, "a caminho" until end of month, and a recorrências overview screen — per `docs/superpowers/specs/2026-08-17-saldo-insights-1-design.md`.

**Architecture:** A new pure-Kotlin `InsightsEngine` (domain) computes everything from `LedgerInput`, reusing `ProjectionEngine`; `TotaisViewModel` adds the results to its state; `TotaisScreen` gains a segmented control and three segment composables built on small Canvas charts whose layout math is pure and unit-tested; a `RecorrenciasScreen` + `RecorrenciasViewModel` list templates and open the viewed month's occurrence in the existing entry sheet.

**Tech Stack:** Kotlin 2.2, Compose (BOM 2026.08.00, Canvas), Room/DataStore untouched, JUnit4. No new dependencies.

## Global Constraints

- pt-BR lowercase copy; money only through `MoneyText`/`ui/money` (U+2212 minus); charts show shapes only — every number on screen is a `MoneyText` so the privacy mask applies (`R$ •••••`).
- No schema change, no new settings, no new dependencies, 100 % local unchanged.
- Chart language **C**: segmented 100 % bar (top 4 tags + outras + sem tag; 2 % minimum segment width for non-zero slices) and per-month bars (saídas `categoryVariable`, entradas `balance`) with the sobrou line (`tint`).
- Segments `mês | tendência | a caminho` via the existing `SegmentedControl`; the selected segment is `rememberSaveable`; the month nav applies to all three.
- Attribution rules (spec): the tag **list** counts a movimentação fully in each of its tags; the **bar** uses first-tag attribution so it sums to 100 %. Delta vs previous month: `null` when the previous month is 0 ("novo"), `0` when |Δ| < 1 % ("=").
- Padrões: weekday averages over the trailing 84 days ending at `hoje` (respecting `saldoInicialData`), divided by the number of that weekday in the window; fewer than 14 days → `diaMaisCaro = null` ("ainda sem padrão"). "avulsas por dia" = one-off DIARIO saídas of the month ÷ days elapsed (through `hoje` for the current month, whole month if past, `null` if future).
- Trend: 6 months ending at the viewed month; `sobrou = TotaisMes.sobrouCentavos`; taxa de poupança = ECONOMIA saídas ÷ entradas (percent, `null` when entradas = 0).
- A caminho: items dated **after** `hoje` through the end of the viewed month, from `ProjectionEngine.mes(...).dias` (so CARTAO purchases enter only via faturas); a month whose end ≤ `hoje` is `mesEncerrado`.
- Recorrências: `ativas` = `ativa && (fim == null || fim >= mes)` (future-start templates included, but they do not count in the monthly totals until `inicio <= mes`); `encerradas` = `!ativa || fim < mes`.
- Every task ends green: JVM `mise run test` (93 today), instrumented `mise run test-device` on `saldo_test` (62 today), `mise exec -- ./gradlew lintDebug` 0 errors. Files LF/UTF-8 (verify no CR bytes — `od -c file | grep -c '\\r'`... use a Python one-liner if unsure). Commit per task with the message given (add the usual Claude trailer lines).
- Baseline: `main` at the commit that carries this plan. Work on a branch `insights-1` created from it.

## File structure

| File | Responsibility |
|---|---|
| `app/src/main/kotlin/com/scholze/saldo/domain/InsightsEngine.kt` (create) | types + `paraOndeFoi`, `tendencia`, `aCaminho`, `recorrencias` |
| `app/src/main/kotlin/com/scholze/saldo/domain/ProjectionEngine.kt` (modify) | public `movimentacoesDoMes`, public `mediaDiaria` |
| `app/src/main/kotlin/com/scholze/saldo/ui/theme/Color.kt` (modify) | `insightOutras`, `insightSemTag` tokens |
| `app/src/main/kotlin/com/scholze/saldo/ui/totais/charts/ChartMath.kt` (create) | pure layout math (widths with floor, bar heights, line points) |
| `app/src/main/kotlin/com/scholze/saldo/ui/totais/charts/Charts.kt` (create) | `SegmentedBar`, `TrendChart`, `WeekdayBars`, `ReservaLine` |
| `app/src/main/kotlin/com/scholze/saldo/ui/totais/TotaisViewModel.kt` (modify) | state gains `insights`, `tendencia`, `aCaminho` |
| `app/src/main/kotlin/com/scholze/saldo/ui/totais/TotaisScreen.kt` (modify) | segmented control, `SegmentoTotais`, callbacks |
| `app/src/main/kotlin/com/scholze/saldo/ui/totais/SegmentoMes.kt` (create) | para onde foi / maiores gastos / padrões |
| `app/src/main/kotlin/com/scholze/saldo/ui/totais/SegmentoTendencia.kt` (create) | trend chart + poupança |
| `app/src/main/kotlin/com/scholze/saldo/ui/totais/SegmentoACaminho.kt` (create) | header + list + recorrências row |
| `app/src/main/kotlin/com/scholze/saldo/ui/totais/RecorrenciasScreen.kt` (create) | overview screen |
| `app/src/main/kotlin/com/scholze/saldo/ui/totais/RecorrenciasViewModel.kt` (create) | resumo + abrir ocorrência |
| `app/src/main/kotlin/com/scholze/saldo/ui/SaldoApp.kt` (modify) | wire the callbacks |
| tests | `domain/InsightsEngineTest.kt` (JVM), `ui/totais/charts/ChartMathTest.kt` (JVM), `ui/totais/TotaisContentTest.kt` (instrumented, extended), `ui/totais/RecorrenciasScreenTest.kt` (instrumented) |

---

### Task 1: `InsightsEngine.paraOndeFoi` (+ two public helpers on `ProjectionEngine`)

**Files:**
- Create: `app/src/main/kotlin/com/scholze/saldo/domain/InsightsEngine.kt`
- Modify: `app/src/main/kotlin/com/scholze/saldo/domain/ProjectionEngine.kt` (`movimentacoesDoMes` public helper; `mediaDiaria` becomes public)
- Test: `app/src/test/kotlin/com/scholze/saldo/domain/InsightsEngineTest.kt` (create)

**Interfaces:**
- Consumes: `LedgerInput`, `Movimentacao`, `Tag`, `Natureza`, `ProjectionEngine` (private `efetivas`).
- Produces: `sealed interface GrupoGasto { DeTag(tag: Tag); Outras; SemTag }`; `data class Fatia(grupo, centavos: Long, share: Float, deltaPercent: Int?)`; `data class Padroes(porDiaDaSemana: Map<DayOfWeek, Long>, diaMaisCaro: DayOfWeek?, avulsasPorDiaMes: Long?, mediaDiaria30: Long)`; `data class ParaOndeFoi(saidasCentavos: Long, fatias: List<Fatia>, barra: List<Fatia>, maioresGastos: List<Movimentacao>, padroes: Padroes)`; `InsightsEngine.paraOndeFoi(input, mes): ParaOndeFoi`; `ProjectionEngine.movimentacoesDoMes(input, mes): List<Movimentacao>`; `ProjectionEngine.mediaDiaria(input): Long` (public).

- [ ] **Step 1: Create the branch and write the failing tests**

```bash
git checkout -b insights-1
```

Create `app/src/test/kotlin/com/scholze/saldo/domain/InsightsEngineTest.kt`:

```kotlin
package com.scholze.saldo.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InsightsEngineTest {
    private val jun = YearMonth.of(2026, 6)
    private val jul = YearMonth.of(2026, 7)
    private val ago = YearMonth.of(2026, 8)
    private val cartao = CartaoConfig(nome = "nubank", fechamentoDia = 28, vencimentoDia = 5)
    private val comida = Tag(1, "comida", 0xFFA6486B)
    private val moradia = Tag(2, "moradia", 0xFFB95A2E)
    private val transporte = Tag(3, "transporte", 0xFF2A7A86)

    private fun mov(
        dia: String,
        centavos: Long,
        natureza: Natureza = Natureza.DIARIO,
        rec: Long? = null,
        tags: List<Tag> = emptyList(),
        descricao: String = "m",
    ) = Movimentacao(
        descricao = descricao, valorCentavos = centavos, data = LocalDate.parse(dia), natureza = natureza,
        recorrenciaId = rec, tags = tags,
    )

    private fun input(
        movs: List<Movimentacao> = emptyList(),
        recs: List<Recorrencia> = emptyList(),
        materializados: Set<YearMonth> = setOf(jul),
        hoje: String = "2026-07-20",
        saldoInicialData: String = "2026-07-01",
    ) = LedgerInput(
        saldoInicialCentavos = 100_000_00,
        saldoInicialData = LocalDate.parse(saldoInicialData),
        movimentacoes = movs,
        recorrencias = recs,
        mesesMaterializados = materializados,
        cartao = cartao,
        hoje = LocalDate.parse(hoje),
    )

    private fun Fatia.nome() = when (val g = grupo) {
        is GrupoGasto.DeTag -> g.tag.nome
        GrupoGasto.Outras -> "outras"
        GrupoGasto.SemTag -> "sem tag"
    }

    // ---- para onde foi: fatias ----

    @Test
    fun fatiasPorTagOrdenadasComSemTagNoFim() {
        val movs = listOf(
            mov("2026-07-02", -100_00, tags = listOf(comida)),
            mov("2026-07-03", -50_00, tags = listOf(comida)),
            mov("2026-07-04", -30_00, tags = listOf(moradia)),
            mov("2026-07-05", -20_00),
        )
        val p = InsightsEngine.paraOndeFoi(input(movs), jul)
        assertEquals(200_00L, p.saidasCentavos)
        assertEquals(listOf("comida", "moradia", "sem tag"), p.fatias.map { it.nome() })
        assertEquals(listOf(150_00L, 30_00L, 20_00L), p.fatias.map { it.centavos })
        assertEquals(0.75f, p.fatias[0].share, 0.001f)
        // Julho é o primeiro mês com dados: não há mês anterior para comparar.
        assertTrue(p.fatias.all { it.deltaPercent == null })
    }

    @Test
    fun deltaContraOMesAnterior() {
        val movs = listOf(
            mov("2026-06-10", -100_00, tags = listOf(comida)),
            mov("2026-06-11", -30_00, tags = listOf(moradia)),
            mov("2026-06-12", -80_00, tags = listOf(transporte)),
            mov("2026-07-10", -130_00, tags = listOf(comida)),
            mov("2026-07-11", -30_00, tags = listOf(moradia)),
        )
        val p = InsightsEngine.paraOndeFoi(input(movs, materializados = setOf(jun, jul), saldoInicialData = "2026-06-01"), jul)
        assertEquals(30, p.fatias.first { it.nome() == "comida" }.deltaPercent)
        assertEquals(0, p.fatias.first { it.nome() == "moradia" }.deltaPercent)
        // transporte não teve saída em julho: não entra na lista.
        assertTrue(p.fatias.none { it.nome() == "transporte" })
    }

    /** A lista conta a movimentação em cada tag; a barra a atribui só à primeira, para somar 100 %. */
    @Test
    fun movimentacaoComDuasTagsContaNasDuasNaListaMasUmaVezNaBarra() {
        val movs = listOf(mov("2026-07-02", -100_00, tags = listOf(comida, moradia)))
        val p = InsightsEngine.paraOndeFoi(input(movs), jul)
        assertEquals(listOf(100_00L, 100_00L), p.fatias.map { it.centavos })
        assertEquals(listOf("comida"), p.barra.map { it.nome() })
        assertEquals(1f, p.barra.sumOf { it.share.toDouble() }.toFloat(), 0.001f)
    }

    @Test
    fun barraTemTop4MaisOutrasESemTag() {
        val tags = (1..6).map { Tag(it.toLong(), "t$it", 1L) }
        val movs = tags.mapIndexed { i, t -> mov("2026-07-0${i + 1}", -(60_00L - i * 10_00L), tags = listOf(t)) } +
            mov("2026-07-09", -5_00)
        val p = InsightsEngine.paraOndeFoi(input(movs), jul)
        assertEquals(listOf("t1", "t2", "t3", "t4", "outras", "sem tag"), p.barra.map { it.nome() })
        assertEquals(30_00L, p.barra[4].centavos)                     // t5 (20) + t6 (10)
        assertEquals(1f, p.barra.sumOf { it.share.toDouble() }.toFloat(), 0.001f)
    }

    @Test
    fun semSaidasNoMes() {
        val p = InsightsEngine.paraOndeFoi(input(listOf(mov("2026-07-02", 100_00))), jul)
        assertEquals(0L, p.saidasCentavos)
        assertTrue(p.fatias.isEmpty())
        assertTrue(p.barra.isEmpty())
        assertTrue(p.maioresGastos.isEmpty())
    }

    // ---- maiores gastos ----

    @Test
    fun maioresGastosSaoOsCincoMaioresEmOrdem() {
        val movs = listOf(
            mov("2026-07-01", -10_00, descricao = "a"), mov("2026-07-02", -60_00, descricao = "b"),
            mov("2026-07-03", -30_00, descricao = "c"), mov("2026-07-04", -250_00, Natureza.CARTAO, descricao = "d"),
            mov("2026-07-05", -40_00, descricao = "e"), mov("2026-07-06", -20_00, descricao = "f"),
            mov("2026-07-07", 500_00, descricao = "entrada"),
        )
        val p = InsightsEngine.paraOndeFoi(input(movs), jul)
        assertEquals(listOf("d", "b", "e", "c", "f"), p.maioresGastos.map { it.descricao })
    }

    // ---- padrões ----

    /** 2026-07-20 é segunda; os sábados 4, 11 e 18 de julho carregam R$ 100,00 cada. */
    @Test
    fun diaMaisCaroPelaMediaPorDiaDaSemana() {
        val movs = listOf(
            mov("2026-07-04", -100_00), mov("2026-07-11", -100_00), mov("2026-07-18", -100_00),
            mov("2026-07-06", -50_00),
            mov("2026-07-13", -999_00, rec = 1L),                       // recorrência: fora dos padrões
            mov("2026-07-15", -999_00, natureza = Natureza.ECONOMIA),   // economia: fora
        )
        val p = InsightsEngine.paraOndeFoi(input(movs, materializados = setOf(jul)), jul).padroes
        assertEquals(DayOfWeek.SATURDAY, p.diaMaisCaro)
        assertEquals(100_00L, p.porDiaDaSemana[DayOfWeek.SATURDAY])   // 300 / 3 sábados na janela 1..20 jul
        assertEquals(16_66L, p.porDiaDaSemana[DayOfWeek.MONDAY])     // 50 / 3 segundas (6, 13, 20)
        assertEquals(0L, p.porDiaDaSemana[DayOfWeek.SUNDAY])
    }

    @Test
    fun semDadosSuficientesNaoHaPadrao() {
        val p = InsightsEngine.paraOndeFoi(input(listOf(mov("2026-07-18", -100_00)), saldoInicialData = "2026-07-15"), jul).padroes
        assertNull(p.diaMaisCaro)
    }

    @Test
    fun avulsasPorDiaDoMesCorrenteEPassadoEFuturo() {
        val movs = listOf(
            mov("2026-06-05", -300_00),
            mov("2026-07-02", -100_00), mov("2026-07-10", -50_00), mov("2026-07-15", -50_00),
            mov("2026-07-12", -999_00, rec = 1L),
        )
        val i = input(movs, materializados = setOf(jun, jul), saldoInicialData = "2026-06-01")
        assertEquals(10_00L, InsightsEngine.paraOndeFoi(i, jul).padroes.avulsasPorDiaMes)     // 200 / 20 dias
        assertEquals(10_00L, InsightsEngine.paraOndeFoi(i, jun).padroes.avulsasPorDiaMes)     // 300 / 30 dias
        assertNull(InsightsEngine.paraOndeFoi(i, ago).padroes.avulsasPorDiaMes)
        assertEquals(ProjectionEngine.mediaDiaria(i), InsightsEngine.paraOndeFoi(i, jul).padroes.mediaDiaria30)
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `mise exec -- ./gradlew test --console=plain -q 2>&1 | grep "^e:" | head -3`
Expected: `Unresolved reference 'Fatia'` / `'GrupoGasto'` / `'InsightsEngine'`.

- [ ] **Step 3: Expose the two helpers on `ProjectionEngine`**

In `app/src/main/kotlin/com/scholze/saldo/domain/ProjectionEngine.kt`:

Add right after `faturasAte(...)` (still above `// ---- internals ----`):

```kotlin
    /**
     * As movimentações efetivas de [mes] — linhas materializadas e expansões virtuais, sem
     * faturas. É a lista sobre a qual "para onde foi o dinheiro" conta: uma compra no cartão
     * conta no dia em que foi feita, não no vencimento da fatura.
     */
    fun movimentacoesDoMes(input: LedgerInput, mes: YearMonth): List<Movimentacao> =
        efetivas(input, mes).filter { YearMonth.from(it.data) == mes }
```

Change `private fun mediaDiaria(input: LedgerInput): Long {` to `fun mediaDiaria(input: LedgerInput): Long {` (the KDoc above it stays).

- [ ] **Step 4: Write `InsightsEngine` (paraOndeFoi part)**

Create `app/src/main/kotlin/com/scholze/saldo/domain/InsightsEngine.kt`:

```kotlin
package com.scholze.saldo.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/** Uma fatia de "para onde foi": uma etiqueta, o resto agrupado, ou o que não tem etiqueta. */
sealed interface GrupoGasto {
    data class DeTag(val tag: Tag) : GrupoGasto
    data object Outras : GrupoGasto
    data object SemTag : GrupoGasto
}

/**
 * [share] é fração de `ParaOndeFoi.saidasCentavos` (0..1). [deltaPercent] compara com o mês
 * anterior: `null` = o mês anterior não teve nada nessa fatia ("novo"); `0` = variou menos de 1 %.
 */
data class Fatia(val grupo: GrupoGasto, val centavos: Long, val share: Float, val deltaPercent: Int?)

/**
 * [porDiaDaSemana]: média de avulsas DIARIO por dia da semana nas últimas 12 semanas.
 * [diaMaisCaro] `null` = menos de 14 dias de dados ("ainda sem padrão").
 * [avulsasPorDiaMes] `null` = mês futuro (nenhum dia decorrido).
 */
data class Padroes(
    val porDiaDaSemana: Map<DayOfWeek, Long>,
    val diaMaisCaro: DayOfWeek?,
    val avulsasPorDiaMes: Long?,
    val mediaDiaria30: Long,
)

/**
 * [fatias]: toda etiqueta com saída no mês (a movimentação conta em CADA tag dela) + sem tag;
 * [barra]: top 4 + outras + sem tag pela PRIMEIRA tag, para somar 100 %.
 */
data class ParaOndeFoi(
    val saidasCentavos: Long,
    val fatias: List<Fatia>,
    val barra: List<Fatia>,
    val maioresGastos: List<Movimentacao>,
    val padroes: Padroes,
)

/**
 * As leituras da aba totais que não são o saldo em si: para onde foi, tendência, a caminho e
 * recorrências. Puro e determinístico como o [ProjectionEngine], que ele reaproveita — nenhuma
 * conta de saldo é refeita aqui.
 */
object InsightsEngine {

    fun paraOndeFoi(input: LedgerInput, mes: YearMonth): ParaOndeFoi {
        val saidas = ProjectionEngine.movimentacoesDoMes(input, mes).filter { it.valorCentavos < 0 }
        val saidasAnterior = ProjectionEngine.movimentacoesDoMes(input, mes.minusMonths(1)).filter { it.valorCentavos < 0 }
        val total = -saidas.sumOf { it.valorCentavos }

        val porTag = somaPorTag(saidas)
        val porTagAnterior = somaPorTag(saidasAnterior)
        val semTag = -saidas.filter { it.tags.isEmpty() }.sumOf { it.valorCentavos }
        val semTagAnterior = -saidasAnterior.filter { it.tags.isEmpty() }.sumOf { it.valorCentavos }

        val fatias = buildList {
            porTag.entries.sortedByDescending { it.value }.forEach { (tag, centavos) ->
                add(Fatia(GrupoGasto.DeTag(tag), centavos, share(centavos, total), delta(centavos, porTagAnterior[tag] ?: 0L)))
            }
            if (semTag > 0) add(Fatia(GrupoGasto.SemTag, semTag, share(semTag, total), delta(semTag, semTagAnterior)))
        }

        // Barra: cada movimentação uma vez só (primeira tag), então as fatias fecham em 100 %.
        val porPrimeiraTag = saidas.filter { it.tags.isNotEmpty() }
            .groupBy { it.tags.first() }
            .mapValues { (_, movs) -> -movs.sumOf { it.valorCentavos } }
            .entries.sortedByDescending { it.value }
        val barra = buildList {
            porPrimeiraTag.take(4).forEach { (tag, centavos) -> add(Fatia(GrupoGasto.DeTag(tag), centavos, share(centavos, total), null)) }
            val outras = porPrimeiraTag.drop(4).sumOf { it.value }
            if (outras > 0) add(Fatia(GrupoGasto.Outras, outras, share(outras, total), null))
            if (semTag > 0) add(Fatia(GrupoGasto.SemTag, semTag, share(semTag, total), null))
        }

        return ParaOndeFoi(
            saidasCentavos = total,
            fatias = fatias,
            barra = barra,
            maioresGastos = saidas.sortedBy { it.valorCentavos }.take(5),
            padroes = padroes(input, mes),
        )
    }

    // ---- padrões ----

    private const val JANELA_DIAS = 84L
    private const val MINIMO_DIAS_PADRAO = 14L

    private fun padroes(input: LedgerInput, mes: YearMonth): Padroes {
        val fim = input.hoje
        val inicio = maxOf(input.saldoInicialData, fim.minusDays(JANELA_DIAS - 1))
        val dias = ChronoUnit.DAYS.between(inicio, fim) + 1
        val avulsas = input.movimentacoes.filter { avulsaDiaria(it) }

        val porDia = DayOfWeek.entries.associateWith { dow ->
            val ocorrencias = (0 until dias).count { inicio.plusDays(it).dayOfWeek == dow }
            if (ocorrencias == 0) 0L
            else -avulsas.filter { it.data >= inicio && it.data <= fim && it.data.dayOfWeek == dow }
                .sumOf { it.valorCentavos } / ocorrencias
        }
        val diaMaisCaro = if (dias < MINIMO_DIAS_PADRAO || porDia.values.all { it == 0L }) null
        else porDia.maxBy { it.value }.key

        val mesAtual = YearMonth.from(input.hoje)
        val diasDecorridos = when {
            mes > mesAtual -> 0
            mes == mesAtual -> input.hoje.dayOfMonth
            else -> mes.lengthOfMonth()
        }
        val avulsasPorDiaMes = if (diasDecorridos == 0) null
        else -avulsas.filter { YearMonth.from(it.data) == mes && it.data <= input.hoje }.sumOf { it.valorCentavos } / diasDecorridos

        return Padroes(porDia, diaMaisCaro, avulsasPorDiaMes, ProjectionEngine.mediaDiaria(input))
    }

    private fun avulsaDiaria(m: Movimentacao) =
        m.recorrenciaId == null && m.natureza == Natureza.DIARIO && m.valorCentavos < 0

    // ---- helpers ----

    private fun somaPorTag(saidas: List<Movimentacao>): Map<Tag, Long> =
        saidas.flatMap { m -> m.tags.map { it to -m.valorCentavos } }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, v) -> v.sum() }

    private fun share(centavos: Long, total: Long): Float = if (total == 0L) 0f else centavos.toFloat() / total

    private fun delta(atual: Long, anterior: Long): Int? =
        if (anterior == 0L) null else Math.round((atual - anterior) * 100.0 / anterior).toInt()
}
```

- [ ] **Step 5: Run the JVM suite**

Run: `mise exec -- ./gradlew test --console=plain -q 2>&1 | grep -c "^e:"; for f in app/build/test-results/testDebugUnitTest/*.xml; do grep -o 'tests="[0-9]*" skipped="[0-9]*" failures="[0-9]*" errors="[0-9]*"' "$f" | head -1; done | awk -F'"' '{t+=$2; f+=$6; e+=$8} END {print "tests="t" failures="f" errors="e}'`
Expected: `0`, `tests=102 failures=0 errors=0` (93 + 9). If `diaMaisCaroPelaMediaPorDiaDaSemana` disagrees on `MONDAY` (16_66 vs 16_67), the plan's rounding assumption is off — integer division truncates, so `50_00 / 3 = 16_66`; if the engine differs, report it (do not adjust the test blindly).

- [ ] **Step 6: Commit**

```bash
git add app/src/main/kotlin/com/scholze/saldo/domain/InsightsEngine.kt app/src/main/kotlin/com/scholze/saldo/domain/ProjectionEngine.kt app/src/test/kotlin/com/scholze/saldo/domain/InsightsEngineTest.kt
git commit -m "feat: InsightsEngine.paraOndeFoi — fatias por tag, barra, maiores gastos, padrões"
```

---

### Task 2: `InsightsEngine.tendencia`, `aCaminho`, `recorrencias`

**Files:**
- Modify: `app/src/main/kotlin/com/scholze/saldo/domain/InsightsEngine.kt`
- Test: `app/src/test/kotlin/com/scholze/saldo/domain/InsightsEngineTest.kt` (extend)

**Interfaces:**
- Consumes: `ProjectionEngine.totais/mes`, `TotaisMes`, `ItemDia`, `Recorrencia`.
- Produces: `data class PontoMes(mes: YearMonth, entradas: Long, saidas: Long, sobrou: Long, reservaAcumulada: Long, taxaPoupanca: Int?)`; `data class ItemFuturo(data: LocalDate, item: ItemDia)`; `data class ACaminho(saemCentavos: Long, entramCentavos: Long, itens: List<ItemFuturo>, mesEncerrado: Boolean)`; `data class ResumoRecorrencias(ativas: List<Recorrencia>, encerradas: List<Recorrencia>, entramMes: Long, saemMes: Long)`; `InsightsEngine.tendencia(input, ateMes, meses = 6): List<PontoMes>`, `InsightsEngine.aCaminho(input, mes): ACaminho`, `InsightsEngine.recorrencias(input, mes): ResumoRecorrencias`.

- [ ] **Step 1: Write the failing tests**

Append inside `InsightsEngineTest` (before the final `}`):

```kotlin
    // ---- tendência ----

    @Test
    fun tendenciaTemSeisPontosTerminandoNoMes() {
        val t = InsightsEngine.tendencia(input(), jul)
        assertEquals(6, t.size)
        assertEquals(YearMonth.of(2026, 2), t.first().mes)
        assertEquals(jul, t.last().mes)
        // Meses antes do saldo inicial (1/jul) são zero, sem taxa.
        assertTrue(t.dropLast(1).all { it.entradas == 0L && it.saidas == 0L && it.sobrou == 0L && it.reservaAcumulada == 0L && it.taxaPoupanca == null })
    }

    @Test
    fun tendenciaSobrouReservaETaxa() {
        val mai = YearMonth.of(2026, 5)
        val movs = listOf(
            mov("2026-05-05", 1_000_00), mov("2026-05-10", -200_00, Natureza.ECONOMIA),
            mov("2026-06-05", 1_000_00), mov("2026-06-10", -300_00, Natureza.ECONOMIA),
            mov("2026-07-05", 1_000_00),
        )
        // hoje já em agosto: os três meses estão fechados, sobrou = saldoReal(fim) − saldoReal(fim anterior).
        val i = input(movs, materializados = setOf(mai, jun, jul), hoje = "2026-08-01", saldoInicialData = "2026-05-01")
        val t = InsightsEngine.tendencia(i, jul)
        val pMai = t.first { it.mes == mai }
        assertEquals(1_000_00L, pMai.entradas)
        assertEquals(200_00L, pMai.saidas)
        assertEquals(800_00L, pMai.sobrou)
        assertEquals(200_00L, pMai.reservaAcumulada)
        assertEquals(20, pMai.taxaPoupanca)
        val pJun = t.first { it.mes == jun }
        assertEquals(700_00L, pJun.sobrou)
        assertEquals(500_00L, pJun.reservaAcumulada)
        assertEquals(30, pJun.taxaPoupanca)
        val pJul = t.last()
        assertEquals(1_000_00L, pJul.sobrou)
        assertEquals(500_00L, pJul.reservaAcumulada)
        assertEquals(0, pJul.taxaPoupanca)
    }

    @Test
    fun taxaNulaSemEntradas() {
        val i = input(listOf(mov("2026-07-10", -100_00, Natureza.ECONOMIA)))
        assertNull(InsightsEngine.tendencia(i, jul).last().taxaPoupanca)
    }

    // ---- a caminho ----

    @Test
    fun aCaminhoSoDepoisDeHojeAteOFimDoMes() {
        val aluguel = Recorrencia(id = 1, descricao = "aluguel", valorCentavos = -2_400_00, natureza = Natureza.DIARIO, diaDoMes = 28, inicio = YearMonth.of(2026, 1))
        val movs = listOf(
            mov("2026-07-20", -100_00),                       // hoje: fora
            mov("2026-07-25", -50_00),
            mov("2026-07-31", 200_00),                        // último dia: dentro
            mov("2026-08-01", -30_00),                        // mês seguinte: fora
            mov("2026-07-10", -250_00, Natureza.CARTAO),      // vence 5/ago: fora de julho
        )
        // julho não materializado: o aluguel expande virtualmente no dia 28.
        val a = InsightsEngine.aCaminho(input(movs, recs = listOf(aluguel), materializados = emptySet()), jul)
        assertEquals(false, a.mesEncerrado)
        assertEquals(listOf("2026-07-25", "2026-07-28", "2026-07-31"), a.itens.map { it.data.toString() })
        assertEquals(2_450_00L, a.saemCentavos)
        assertEquals(200_00L, a.entramCentavos)
    }

    @Test
    fun mesPassadoEstaEncerrado() {
        val a = InsightsEngine.aCaminho(input(listOf(mov("2026-07-25", -50_00)), hoje = "2026-08-10"), jul)
        assertTrue(a.mesEncerrado)
        assertTrue(a.itens.isEmpty())
        assertEquals(0L, a.saemCentavos)
    }

    @Test
    fun faturaEntraNoVencimento() {
        val i = input(listOf(mov("2026-07-10", -250_00, Natureza.CARTAO)), materializados = setOf(jul, ago), hoje = "2026-08-01")
        val a = InsightsEngine.aCaminho(i, ago)
        assertEquals(listOf("2026-08-05"), a.itens.map { it.data.toString() })
        assertTrue(a.itens.single().item is ItemDia.FaturaDia)
        assertEquals(250_00L, a.saemCentavos)
    }

    // ---- recorrências ----

    @Test
    fun recorrenciasAtivasEncerradasETotais() {
        val aluguel = Recorrencia(id = 1, descricao = "aluguel", valorCentavos = -2_400_00, natureza = Natureza.DIARIO, diaDoMes = 3, inicio = YearMonth.of(2026, 1))
        val salario = Recorrencia(id = 2, descricao = "salário", valorCentavos = 8_240_00, natureza = Natureza.DIARIO, diaDoMes = 5, inicio = YearMonth.of(2026, 1))
        val netflix = Recorrencia(id = 3, descricao = "netflix", valorCentavos = -50_00, natureza = Natureza.DIARIO, diaDoMes = 10, inicio = YearMonth.of(2026, 1), fim = jun)
        val academia = Recorrencia(id = 4, descricao = "academia", valorCentavos = -120_00, natureza = Natureza.DIARIO, diaDoMes = 1, inicio = YearMonth.of(2026, 9))
        val antiga = Recorrencia(id = 5, descricao = "antiga", valorCentavos = -10_00, natureza = Natureza.DIARIO, diaDoMes = 1, inicio = YearMonth.of(2026, 1), ativa = false)
        val r = InsightsEngine.recorrencias(input(recs = listOf(aluguel, salario, netflix, academia, antiga)), jul)
        assertEquals(listOf("academia", "aluguel", "salário"), r.ativas.map { it.descricao })      // por dia do mês: 1, 3, 5
        assertEquals(listOf("antiga", "netflix"), r.encerradas.map { it.descricao })
        assertEquals(8_240_00L, r.entramMes)
        assertEquals(2_400_00L, r.saemMes)                                                           // academia só começa em setembro
    }
```

- [ ] **Step 2: Run to verify it fails**

Run: `mise exec -- ./gradlew test --console=plain -q 2>&1 | grep "^e:" | head -3`
Expected: `Unresolved reference 'tendencia'` (and `aCaminho`, `recorrencias`).

- [ ] **Step 3: Implement**

In `app/src/main/kotlin/com/scholze/saldo/domain/InsightsEngine.kt` add the types after `ParaOndeFoi`:

```kotlin
/** Um mês da tendência: totais fechados (ou projetados, para o mês corrente) e a reserva acumulada até ali. */
data class PontoMes(
    val mes: YearMonth,
    val entradas: Long,
    val saidas: Long,
    val sobrou: Long,
    val reservaAcumulada: Long,
    /** ECONOMIA do mês ÷ entradas do mês, em %; `null` sem entradas. */
    val taxaPoupanca: Int?,
)

data class ItemFuturo(val data: LocalDate, val item: ItemDia)

/** O que ainda passa pela coluna de saldo depois de hoje até o fim do mês visto. */
data class ACaminho(
    val saemCentavos: Long,
    val entramCentavos: Long,
    val itens: List<ItemFuturo>,
    /** O mês visto já terminou: nada a caminho, por definição. */
    val mesEncerrado: Boolean,
)

/**
 * [ativas]: templates ativos e não encerrados no mês visto (inclui os que só começam depois);
 * [entramMes]/[saemMes] somam só os vigentes no mês (`inicio ≤ mês`).
 */
data class ResumoRecorrencias(
    val ativas: List<Recorrencia>,
    val encerradas: List<Recorrencia>,
    val entramMes: Long,
    val saemMes: Long,
)
```

and, inside `object InsightsEngine`, after `paraOndeFoi(...)` (before the `// ---- padrões ----` section):

```kotlin
    /** Os [meses] meses até [ateMes], inclusive; cada ponto vem de [ProjectionEngine.totais]. */
    fun tendencia(input: LedgerInput, ateMes: YearMonth, meses: Int = 6): List<PontoMes> =
        (meses - 1 downTo 0).map { ateMes.minusMonths(it.toLong()) }.map { m ->
            val t = ProjectionEngine.totais(input, m)
            val economia = t.saidasPorNatureza[Natureza.ECONOMIA] ?: 0L
            PontoMes(
                mes = m,
                entradas = t.entradasCentavos,
                saidas = t.saidasPorNatureza.values.sum(),
                sobrou = t.sobrouCentavos,
                reservaAcumulada = t.economiaBucketCentavos,
                taxaPoupanca = if (t.entradasCentavos > 0) (economia * 100 / t.entradasCentavos).toInt() else null,
            )
        }

    /** Itens datados DEPOIS de hoje até o fim de [mes], tirados das linhas de dia do próprio ledger. */
    fun aCaminho(input: LedgerInput, mes: YearMonth): ACaminho {
        if (!mes.atEndOfMonth().isAfter(input.hoje)) return ACaminho(0L, 0L, emptyList(), mesEncerrado = true)
        val itens = ProjectionEngine.mes(input, mes, FiltroLedger.TODAS).dias
            .filter { it.data > input.hoje }
            .flatMap { dia -> dia.itens.map { ItemFuturo(dia.data, it) } }
        return ACaminho(
            saemCentavos = -itens.filter { it.item.valorCentavos < 0 }.sumOf { it.item.valorCentavos },
            entramCentavos = itens.filter { it.item.valorCentavos > 0 }.sumOf { it.item.valorCentavos },
            itens = itens,
            mesEncerrado = false,
        )
    }

    fun recorrencias(input: LedgerInput, mes: YearMonth): ResumoRecorrencias {
        val (ativas, encerradas) = input.recorrencias.partition { r -> r.ativa && (r.fim?.let { it >= mes } ?: true) }
        val vigentes = ativas.filter { it.inicio <= mes }
        return ResumoRecorrencias(
            ativas = ativas.sortedBy { it.diaDoMes },
            encerradas = encerradas.sortedBy { it.diaDoMes },
            entramMes = vigentes.filter { it.valorCentavos > 0 }.sumOf { it.valorCentavos },
            saemMes = -vigentes.filter { it.valorCentavos < 0 }.sumOf { it.valorCentavos },
        )
    }
```

- [ ] **Step 4: Run the JVM suite**

Run: `mise exec -- ./gradlew test --console=plain -q 2>&1 | grep -c "^e:"; for f in app/build/test-results/testDebugUnitTest/*.xml; do grep -o 'tests="[0-9]*" skipped="[0-9]*" failures="[0-9]*" errors="[0-9]*"' "$f" | head -1; done | awk -F'"' '{t+=$2; f+=$6; e+=$8} END {print "tests="t" failures="f" errors="e}'`
Expected: `0`, `tests=109 failures=0 errors=0` (102 + 7).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/scholze/saldo/domain/InsightsEngine.kt app/src/test/kotlin/com/scholze/saldo/domain/InsightsEngineTest.kt
git commit -m "feat: InsightsEngine — tendência, a caminho, recorrências"
```

---

### Task 3: Theme tokens, `ChartMath`, and the four chart composables

**Files:**
- Modify: `app/src/main/kotlin/com/scholze/saldo/ui/theme/Color.kt`
- Create: `app/src/main/kotlin/com/scholze/saldo/ui/totais/charts/ChartMath.kt`
- Create: `app/src/main/kotlin/com/scholze/saldo/ui/totais/charts/Charts.kt`
- Test: `app/src/test/kotlin/com/scholze/saldo/ui/totais/charts/ChartMathTest.kt` (create, JVM)

**Interfaces:**
- Consumes: `PontoMes` (Task 2), `SaldoTheme.colors`.
- Produces: `SaldoColors.insightOutras`, `SaldoColors.insightSemTag`; `ChartMath.larguras(shares: List<Float>, piso: Float = 0.02f): List<Float>`, `ChartMath.alturas(valores: List<Long>): List<Float>`, `ChartMath.linha(valores: List<Long>): List<Float>`; composables `SegmentedBar(shares: List<Float>, cores: List<Color>, modifier, altura: Dp = 12.dp)`, `TrendChart(pontos: List<PontoMes>, mesDestacado: YearMonth, onMes: (YearMonth) -> Unit, modifier)`, `WeekdayBars(porDia: Map<DayOfWeek, Long>, destaque: DayOfWeek?, modifier)`, `ReservaLine(valores: List<Long>, modifier)`.

- [ ] **Step 1: Write the failing math tests**

Create `app/src/test/kotlin/com/scholze/saldo/ui/totais/charts/ChartMathTest.kt`:

```kotlin
package com.scholze.saldo.ui.totais.charts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChartMathTest {

    @Test
    fun largurasAplicamPisoERenormalizam() {
        val l = ChartMath.larguras(listOf(0.5f, 0.49f, 0.01f))
        assertEquals(0.02f, l[2], 1e-6f)          // a fatia de 1 % sobe para o piso
        assertEquals(1f, l.sum(), 1e-4f)           // e as outras encolhem para fechar em 1
        assertTrue(l[0] > l[1])
    }

    @Test
    fun largurasSemFatiasPequenasSaoAsProprias() {
        val l = ChartMath.larguras(listOf(0.6f, 0.4f))
        assertEquals(0.6f, l[0], 1e-6f)
        assertEquals(0.4f, l[1], 1e-6f)
    }

    @Test
    fun largurasZeroFicamZero() {
        assertEquals(listOf(0f, 0f), ChartMath.larguras(listOf(0f, 0f)))
        val l = ChartMath.larguras(listOf(0.7f, 0f, 0.3f))
        assertEquals(0f, l[1], 0f)
        assertEquals(1f, l.sum(), 1e-4f)
    }

    @Test
    fun alturasRelativasAoMaiorValorAbsoluto() {
        assertEquals(listOf(1f, 0.5f, 0f), ChartMath.alturas(listOf(200L, 100L, 0L)))
        assertEquals(listOf(1f, 0.5f), ChartMath.alturas(listOf(-200L, 100L)))
        assertEquals(listOf(0f, 0f), ChartMath.alturas(listOf(0L, 0L)))
    }

    @Test
    fun linhaNormalizaEntreMinimoEMaximo() {
        assertEquals(listOf(0f, 0.5f, 1f), ChartMath.linha(listOf(0L, 50L, 100L)))
        assertEquals(listOf(0.5f, 0.5f), ChartMath.linha(listOf(7L, 7L)))
        assertTrue(ChartMath.linha(emptyList()).isEmpty())
    }
}
```

Run: `mise exec -- ./gradlew test --console=plain -q 2>&1 | grep "^e:" | head -2` → `Unresolved reference 'ChartMath'`.

- [ ] **Step 2: Tokens**

In `app/src/main/kotlin/com/scholze/saldo/ui/theme/Color.kt` add to `SaldoColors` (after `categoryFixed`):

```kotlin
    /** "para onde foi": as tags além do top 4 agrupadas, e o que não tem tag. */
    val insightOutras: Color,
    val insightSemTag: Color,
```

and to the palettes (after `categoryFixed = …`): light `insightOutras = Color(0xFF8E8E93), insightSemTag = Color(0xFFC7C7CC),`; dark `insightOutras = Color(0xFF8E8E93), insightSemTag = Color(0xFF48484A),`.

- [ ] **Step 3: `ChartMath`**

Create `app/src/main/kotlin/com/scholze/saldo/ui/totais/charts/ChartMath.kt`:

```kotlin
package com.scholze.saldo.ui.totais.charts

import kotlin.math.abs

/** A geometria dos gráficos, sem Compose: testável na JVM, os composables só desenham. */
object ChartMath {

    /**
     * Frações de largura da barra segmentada. Uma fatia > 0 nunca fica abaixo de [piso] (senão
     * some da tela); as demais encolhem proporcionalmente para o total continuar 1. Zeros ficam 0.
     */
    fun larguras(shares: List<Float>, piso: Float = 0.02f): List<Float> {
        if (shares.isEmpty()) return emptyList()
        val total = shares.sum()
        if (total <= 0f) return shares.map { 0f }
        val normal = shares.map { it / total }
        val pequenas = normal.count { it > 0f && it < piso }
        if (pequenas == 0) return normal
        val restante = normal.filter { it >= piso }.sum()
        val escala = if (restante > 0f) (1f - pequenas * piso) / restante else 0f
        return normal.map {
            when {
                it <= 0f -> 0f
                it < piso -> piso
                else -> it * escala
            }
        }
    }

    /** Alturas 0..1 relativas ao maior valor absoluto; tudo zero → tudo zero. */
    fun alturas(valores: List<Long>): List<Float> {
        val max = valores.maxOfOrNull { abs(it) } ?: 0L
        return if (max == 0L) valores.map { 0f } else valores.map { abs(it).toFloat() / max }
    }

    /** Posições 0..1 de uma linha (0 = mínimo, 1 = máximo); série constante → 0.5. */
    fun linha(valores: List<Long>): List<Float> {
        val min = valores.minOrNull() ?: return emptyList()
        val max = valores.max()
        return if (max == min) valores.map { 0.5f } else valores.map { (it - min).toFloat() / (max - min) }
    }
}
```

- [ ] **Step 4: The composables**

Create `app/src/main/kotlin/com/scholze/saldo/ui/totais/charts/Charts.kt`:

```kotlin
package com.scholze.saldo.ui.totais.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.scholze.saldo.domain.PontoMes
import com.scholze.saldo.ui.theme.SaldoTheme
import java.time.DayOfWeek
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

private val ptBr = Locale.forLanguageTag("pt-BR")
private val mesCurto = DateTimeFormatter.ofPattern("MMM", ptBr)

/** A barra 100 % de "para onde foi": uma Row com pesos vindos de [ChartMath.larguras]. */
@Composable
fun SegmentedBar(shares: List<Float>, cores: List<Color>, modifier: Modifier = Modifier, altura: Dp = 12.dp) {
    val larguras = remember(shares) { ChartMath.larguras(shares) }
    Row(modifier.fillMaxWidth().height(altura).clip(RoundedCornerShape(altura / 2))) {
        larguras.forEachIndexed { i, w ->
            if (w > 0f) Box(Modifier.fillMaxHeight().weight(w).background(cores[i]))
        }
    }
}

/**
 * Tendência: por mês, barra de saídas e barra de entradas (mesma escala) e a linha do sobrou por
 * cima (escala própria). Toque numa coluna ou no rótulo → [onMes].
 */
@Composable
fun TrendChart(
    pontos: List<PontoMes>,
    mesDestacado: YearMonth,
    onMes: (YearMonth) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SaldoTheme.colors
    val n = pontos.size
    // Saídas e entradas dividem a escala: alturas calculadas sobre as duas séries juntas.
    val alturas = remember(pontos) { ChartMath.alturas(pontos.map { it.saidas } + pontos.map { it.entradas }) }
    val linha = remember(pontos) { ChartMath.linha(pontos.map { it.sobrou }) }
    val corSaidas = colors.categoryVariable
    val corEntradas = colors.balance
    val corLinha = colors.tint
    val corFundoPonto = colors.surface

    Column(modifier) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(120.dp)
                .pointerInput(pontos) {
                    detectTapGestures { o ->
                        if (n > 0) onMes(pontos[(o.x / size.width * n).toInt().coerceIn(0, n - 1)].mes)
                    }
                },
        ) {
            if (n == 0) return@Canvas
            val colW = size.width / n
            val barW = colW * 0.28f
            val gap = colW * 0.06f
            val h = size.height
            val raio = CornerRadius(3.dp.toPx())
            pontos.forEachIndexed { i, _ ->
                val x0 = i * colW + (colW - (2 * barW + gap)) / 2
                val hs = alturas[i] * h
                val he = alturas[n + i] * h
                if (hs > 0f) drawRoundRect(corSaidas, topLeft = Offset(x0, h - hs), size = Size(barW, hs), cornerRadius = raio)
                if (he > 0f) drawRoundRect(corEntradas, topLeft = Offset(x0 + barW + gap, h - he), size = Size(barW, he), cornerRadius = raio)
            }
            // A linha do sobrou fica entre 5 % e 95 % da altura, para os pontos não colarem nas bordas.
            val pts = linha.mapIndexed { i, y -> Offset(i * colW + colW / 2, h * 0.05f + h * 0.9f * (1f - y)) }
            for (i in 0 until pts.size - 1) {
                drawLine(corLinha, pts[i], pts[i + 1], strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
            }
            pts.forEach {
                drawCircle(corLinha, radius = 3.5.dp.toPx(), center = it)
                drawCircle(corFundoPonto, radius = 1.5.dp.toPx(), center = it)
            }
        }
        Row(Modifier.fillMaxWidth()) {
            pontos.forEach { p ->
                val destacado = p.mes == mesDestacado
                Text(
                    p.mes.format(mesCurto).removeSuffix("."),
                    Modifier.weight(1f).clickable { onMes(p.mes) },
                    style = SaldoTheme.type.footnote.copy(fontWeight = if (destacado) FontWeight.SemiBold else FontWeight.Normal),
                    color = if (destacado) colors.label else colors.secondaryLabel,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** Sete barrinhas seg…dom; [destaque] (o dia mais caro) na cor de saída, o resto neutro. */
@Composable
fun WeekdayBars(porDia: Map<DayOfWeek, Long>, destaque: DayOfWeek?, modifier: Modifier = Modifier) {
    val colors = SaldoTheme.colors
    val dias = DayOfWeek.entries
    val alturas = remember(porDia) { ChartMath.alturas(dias.map { porDia[it] ?: 0L }) }
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
        dias.forEachIndexed { i, d ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(maxOf(2.dp, 28.dp * alturas[i]))
                        .clip(RoundedCornerShape(2.dp))
                        .background(if (d == destaque) colors.categoryVariable else colors.separator),
                )
                Text(rotulo(d), style = SaldoTheme.type.caption, color = colors.secondaryLabel)
            }
        }
    }
}

private fun rotulo(d: DayOfWeek): String = when (d) {
    DayOfWeek.MONDAY -> "s"
    DayOfWeek.TUESDAY -> "t"
    DayOfWeek.WEDNESDAY -> "q"
    DayOfWeek.THURSDAY -> "q"
    DayOfWeek.FRIDAY -> "s"
    DayOfWeek.SATURDAY -> "s"
    DayOfWeek.SUNDAY -> "d"
}

/** A reserva acumulada mês a mês: uma linha com o ponto final marcado. */
@Composable
fun ReservaLine(valores: List<Long>, modifier: Modifier = Modifier) {
    val cor = SaldoTheme.colors.balance
    val ys = remember(valores) { ChartMath.linha(valores) }
    Canvas(modifier.fillMaxWidth().height(56.dp)) {
        if (ys.size < 2) return@Canvas
        val passo = size.width / (ys.size - 1)
        val pts = ys.mapIndexed { i, y -> Offset(i * passo, size.height * 0.1f + size.height * 0.8f * (1f - y)) }
        for (i in 0 until pts.size - 1) {
            drawLine(cor, pts[i], pts[i + 1], strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
        }
        drawCircle(cor, radius = 4.dp.toPx(), center = pts.last())
    }
}
```

- [ ] **Step 5: JVM suite + compile check**

Run: `mise exec -- ./gradlew test --console=plain -q 2>&1 | grep -c "^e:"; for f in app/build/test-results/testDebugUnitTest/*.xml; do grep -o 'tests="[0-9]*" skipped="[0-9]*" failures="[0-9]*" errors="[0-9]*"' "$f" | head -1; done | awk -F'"' '{t+=$2; f+=$6; e+=$8} END {print "tests="t" failures="f" errors="e}'`
Expected: `0`, `tests=114 failures=0 errors=0` (109 + 5). The main source set compiles as part of the test task (Charts.kt is compiled even though nothing uses it yet).

- [ ] **Step 6: Commit**

```bash
git add app/src/main/kotlin/com/scholze/saldo/ui/theme/Color.kt app/src/main/kotlin/com/scholze/saldo/ui/totais/charts app/src/test/kotlin/com/scholze/saldo/ui/totais/charts
git commit -m "feat: chart primitives — ChartMath (tested), SegmentedBar, TrendChart, WeekdayBars, ReservaLine"
```

---

### Task 4: The *mês* content — "para onde foi", maiores gastos, padrões

**Files:**
- Modify: `app/src/main/kotlin/com/scholze/saldo/ui/totais/TotaisViewModel.kt` (state gains `insights`)
- Create: `app/src/main/kotlin/com/scholze/saldo/ui/totais/SegmentoMes.kt`
- Modify: `app/src/main/kotlin/com/scholze/saldo/ui/totais/TotaisScreen.kt` (callbacks; replaces the "TAGS DO MÊS" block)
- Modify: `app/src/main/kotlin/com/scholze/saldo/ui/SaldoApp.kt` (wire callbacks)
- Test: `app/src/androidTest/kotlin/com/scholze/saldo/ui/totais/TotaisContentTest.kt` (extend)

**Interfaces:**
- Consumes: `InsightsEngine.paraOndeFoi`, `ParaOndeFoi/Fatia/GrupoGasto/Padroes` (Task 1), `SegmentedBar`, `WeekdayBars`, tokens (Task 3), `LedgerViewModel.definirTagFiltro`, `EntryViewModel.iniciarEdicao`.
- Produces: `TotaisUiState.insights: ParaOndeFoi?`; `TotaisContent(state, onMesAnterior, onProximoMes, onVerTag: (Tag) -> Unit = {}, onAbrirMovimentacao: (Movimentacao) -> Unit = {}, modifier)`; `TotaisScreen(vm, onVerTag, onAbrirMovimentacao, modifier)`; composable `SegmentoMesInsights(p: ParaOndeFoi, onVerTag, onAbrirMovimentacao)`.

- [ ] **Step 1: Write the failing tests**

In `app/src/androidTest/kotlin/com/scholze/saldo/ui/totais/TotaisContentTest.kt`:

Add imports:

```kotlin
import androidx.compose.ui.test.performClick
import com.scholze.saldo.domain.Fatia
import com.scholze.saldo.domain.GrupoGasto
import com.scholze.saldo.domain.Movimentacao
import com.scholze.saldo.domain.Padroes
import com.scholze.saldo.domain.ParaOndeFoi
import java.time.DayOfWeek
import org.junit.Assert.assertEquals
```

After the `totais` fixture add:

```kotlin
    private val comida = Tag(1, "comida", 0xFFA6486B)
    private val moradia = Tag(2, "moradia", 0xFFB95A2E)
    private val mercado = Movimentacao(id = 9, descricao = "mercado", valorCentavos = -489_90, data = LocalDate.parse("2026-07-13"), natureza = Natureza.DIARIO)
    private val fatias = listOf(
        Fatia(GrupoGasto.DeTag(comida), 700_00, 0.25f, 30),
        Fatia(GrupoGasto.DeTag(moradia), 600_00, 0.21f, 0),
        Fatia(GrupoGasto.SemTag, 100_00, 0.04f, null),
    )
    private val insights = ParaOndeFoi(
        saidasCentavos = 2_800_00,
        fatias = fatias,
        barra = fatias,
        maioresGastos = listOf(mercado),
        padroes = Padroes(
            porDiaDaSemana = DayOfWeek.entries.associateWith { 0L } + (DayOfWeek.SATURDAY to 98_10L),
            diaMaisCaro = DayOfWeek.SATURDAY,
            avulsasPorDiaMes = 82_10,
            mediaDiaria30 = 71_00,
        ),
    )
```

Change `montar` to accept the callbacks:

```kotlin
    private fun montar(
        state: TotaisUiState,
        oculto: Boolean = false,
        onVerTag: (Tag) -> Unit = {},
        onAbrirMovimentacao: (Movimentacao) -> Unit = {},
    ) {
        rule.setContent {
            SaldoTheme(darkTheme = false) {
                CompositionLocalProvider(LocalPrivacy provides PrivacyState(ocultoInicial = oculto)) {
                    TotaisContent(state, {}, {}, onVerTag = onVerTag, onAbrirMovimentacao = onAbrirMovimentacao)
                }
            }
        }
    }
```

In `mostraPerformanceEBlocos` change the first line to `montar(TotaisUiState(YearMonth.of(2026, 7), totais, insights = insights))` (the "comida" / "−700,00" assertions now come from the new section). Add:

```kotlin
    @Test
    fun paraOndeFoiListaFatiasDeltasMaioresGastosEPadroes() {
        montar(TotaisUiState(YearMonth.of(2026, 7), totais, insights = insights))
        rule.onNodeWithText("PARA ONDE FOI").assertIsDisplayed()
        rule.onNodeWithText("+30%").assertIsDisplayed()
        rule.onNodeWithText("=").assertIsDisplayed()
        rule.onNodeWithText("novo").assertIsDisplayed()
        rule.onNodeWithText("sem tag").assertIsDisplayed()
        rule.onNodeWithText("mercado").assertIsDisplayed()
        rule.onNodeWithText("−489,90").assertIsDisplayed()
        rule.onNodeWithText("sábado é o dia mais caro").assertIsDisplayed()
        rule.onNodeWithText("avulsas por dia este mês").assertIsDisplayed()
    }

    @Test
    fun tocarNumaTagENumGastoDisparaOsCallbacks() {
        var tagVista: Tag? = null
        var aberta: Movimentacao? = null
        montar(TotaisUiState(YearMonth.of(2026, 7), totais, insights = insights), onVerTag = { tagVista = it }, onAbrirMovimentacao = { aberta = it })
        rule.onNodeWithText("comida").performClick()
        assertEquals(comida, tagVista)
        rule.onNodeWithText("mercado").performClick()
        assertEquals(mercado, aberta)
    }

    @Test
    fun semSaidasNoMesMostraOVazio() {
        val vazio = insights.copy(saidasCentavos = 0, fatias = emptyList(), barra = emptyList(), maioresGastos = emptyList())
        montar(TotaisUiState(YearMonth.of(2026, 7), totais, insights = vazio))
        rule.onNodeWithText("nenhuma saída este mês").assertIsDisplayed()
    }
```

Run: `mise exec -- ./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.scholze.saldo.ui.totais.TotaisContentTest --console=plain 2>&1 | grep "^e:" | head -3` → `No value passed for parameter`/`Cannot find a parameter with this name: insights` and `onVerTag`.

- [ ] **Step 2: ViewModel state**

In `app/src/main/kotlin/com/scholze/saldo/ui/totais/TotaisViewModel.kt`:

```kotlin
data class TotaisUiState(
    val mesAtual: YearMonth,
    /** `null` enquanto o primeiro `LedgerInput` não chegou do banco. */
    val totais: TotaisMes?,
    val estimativaCentavos: Long = 0,
    /** "para onde foi" do mês visto; `null` junto com [totais]. */
    val insights: ParaOndeFoi? = null,
)
```

and in the `combine`:

```kotlin
        TotaisUiState(
            mesAtual = mes,
            totais = ProjectionEngine.totais(input, mes),
            estimativaCentavos = ProjectionEngine.mes(input, mes, FiltroLedger.TODAS).estimativaCentavos,
            insights = InsightsEngine.paraOndeFoi(input, mes),
        )
```

with imports `com.scholze.saldo.domain.InsightsEngine` and `com.scholze.saldo.domain.ParaOndeFoi`.

- [ ] **Step 3: `SegmentoMes.kt`**

Create `app/src/main/kotlin/com/scholze/saldo/ui/totais/SegmentoMes.kt`:

```kotlin
package com.scholze.saldo.ui.totais

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.scholze.saldo.domain.Fatia
import com.scholze.saldo.domain.GrupoGasto
import com.scholze.saldo.domain.Movimentacao
import com.scholze.saldo.domain.Padroes
import com.scholze.saldo.domain.ParaOndeFoi
import com.scholze.saldo.domain.Tag
import com.scholze.saldo.ui.components.HairlineDivider
import com.scholze.saldo.ui.components.InsetGroup
import com.scholze.saldo.ui.components.InsetRow
import com.scholze.saldo.ui.privacy.FormatoMoney
import com.scholze.saldo.ui.privacy.MoneyText
import com.scholze.saldo.ui.theme.SaldoColors
import com.scholze.saldo.ui.theme.SaldoTheme
import com.scholze.saldo.ui.totais.charts.SegmentedBar
import com.scholze.saldo.ui.totais.charts.WeekdayBars
import java.time.DayOfWeek

/** As três seções de insight do segmento "mês": para onde foi, maiores gastos, padrões. */
@Composable
fun SegmentoMesInsights(p: ParaOndeFoi, onVerTag: (Tag) -> Unit, onAbrirMovimentacao: (Movimentacao) -> Unit) {
    val colors = SaldoTheme.colors

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("PARA ONDE FOI", Modifier.weight(1f), style = SaldoTheme.type.sectionHeader, color = colors.secondaryLabel)
            if (p.saidasCentavos > 0) {
                Text("saídas ", style = SaldoTheme.type.footnote, color = colors.secondaryLabel)
                MoneyText(centavos = p.saidasCentavos, style = SaldoTheme.type.footnote, color = colors.secondaryLabel)
            }
        }
        InsetGroup {
            if (p.saidasCentavos == 0L) {
                InsetRow(label = "nenhuma saída este mês")
            } else {
                SegmentedBar(
                    shares = p.barra.map { it.share },
                    cores = p.barra.map { corDe(it.grupo, colors) },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
                p.fatias.forEach { f ->
                    HairlineDivider(startIndent = 16.dp)
                    LinhaFatia(f, onClick = (f.grupo as? GrupoGasto.DeTag)?.let { g -> { onVerTag(g.tag) } })
                }
            }
        }
    }

    if (p.maioresGastos.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("MAIORES GASTOS", style = SaldoTheme.type.sectionHeader, color = colors.secondaryLabel)
            InsetGroup {
                p.maioresGastos.forEachIndexed { i, mov ->
                    if (i > 0) HairlineDivider(startIndent = 16.dp)
                    Row(
                        Modifier.fillMaxWidth().clickable { onAbrirMovimentacao(mov) }.padding(horizontal = 16.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            mov.data.dayOfMonth.toString().padStart(2, '0'),
                            style = SaldoTheme.type.footnote, color = colors.secondaryLabel,
                        )
                        Text(mov.descricao, Modifier.weight(1f), style = SaldoTheme.type.body, color = colors.label)
                        MoneyText(centavos = mov.valorCentavos, style = SaldoTheme.type.body, color = colors.label, formato = FormatoMoney.ASSINADO)
                    }
                }
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("PADRÕES", style = SaldoTheme.type.sectionHeader, color = colors.secondaryLabel)
        InsetGroup {
            PadroesRows(p.padroes)
        }
    }
}

@Composable
private fun LinhaFatia(f: Fatia, onClick: (() -> Unit)?) {
    val colors = SaldoTheme.colors
    val base = Modifier.fillMaxWidth()
    Row(
        (if (onClick != null) base.clickable(onClick = onClick) else base).padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(8.dp).background(corDe(f.grupo, colors), CircleShape))
        Text(nomeDe(f.grupo), Modifier.weight(1f), style = SaldoTheme.type.body, color = colors.label)
        MoneyText(centavos = -f.centavos, style = SaldoTheme.type.body, color = colors.label, formato = FormatoMoney.ASSINADO)
        // Variação contra o mês anterior: gastar mais é vinho, menos é verde, "novo" e "=" neutros.
        val delta = f.deltaPercent
        Text(
            when {
                delta == null -> "novo"
                delta == 0 -> "="
                delta > 0 -> "+$delta%"
                else -> "−${-delta}%"
            },
            Modifier.width(44.dp),
            style = SaldoTheme.type.footnote,
            color = when {
                delta == null || delta == 0 -> colors.secondaryLabel
                delta > 0 -> colors.categoryVariable
                else -> colors.positive
            },
        )
    }
}

@Composable
private fun PadroesRows(pd: Padroes) {
    val colors = SaldoTheme.colors
    Column(Modifier.padding(horizontal = 16.dp, vertical = 11.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val dia = pd.diaMaisCaro
        if (dia == null) {
            Text("ainda sem padrão", style = SaldoTheme.type.body, color = colors.secondaryLabel)
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${nomeDia(dia)} é o dia mais caro", Modifier.weight(1f), style = SaldoTheme.type.body, color = colors.label)
                Text("média ", style = SaldoTheme.type.footnote, color = colors.secondaryLabel)
                MoneyText(centavos = pd.porDiaDaSemana[dia] ?: 0L, style = SaldoTheme.type.footnote, color = colors.secondaryLabel)
            }
        }
        WeekdayBars(pd.porDiaDaSemana, dia)
    }
    HairlineDivider(startIndent = 16.dp)
    InsetRow(
        label = "avulsas por dia este mês",
        trailing = {
            val v = pd.avulsasPorDiaMes
            if (v == null) Text("—", style = SaldoTheme.type.body, color = colors.secondaryLabel)
            else MoneyText(centavos = v, style = SaldoTheme.type.body, color = colors.secondaryLabel)
        },
    )
    HairlineDivider(startIndent = 16.dp)
    InsetRow(
        label = "média 30 dias (a da projeção)",
        trailing = { MoneyText(centavos = pd.mediaDiaria30, style = SaldoTheme.type.body, color = colors.secondaryLabel) },
    )
}

private fun corDe(g: GrupoGasto, colors: SaldoColors): Color = when (g) {
    is GrupoGasto.DeTag -> Color(g.tag.cor)
    GrupoGasto.Outras -> colors.insightOutras
    GrupoGasto.SemTag -> colors.insightSemTag
}

private fun nomeDe(g: GrupoGasto): String = when (g) {
    is GrupoGasto.DeTag -> g.tag.nome
    GrupoGasto.Outras -> "outras"
    GrupoGasto.SemTag -> "sem tag"
}

private fun nomeDia(d: DayOfWeek): String = when (d) {
    DayOfWeek.MONDAY -> "segunda"
    DayOfWeek.TUESDAY -> "terça"
    DayOfWeek.WEDNESDAY -> "quarta"
    DayOfWeek.THURSDAY -> "quinta"
    DayOfWeek.FRIDAY -> "sexta"
    DayOfWeek.SATURDAY -> "sábado"
    DayOfWeek.SUNDAY -> "domingo"
}
```

- [ ] **Step 4: `TotaisScreen` — callbacks and the section**

In `app/src/main/kotlin/com/scholze/saldo/ui/totais/TotaisScreen.kt`:

Add imports:

```kotlin
import com.scholze.saldo.domain.Movimentacao
import com.scholze.saldo.domain.Tag
```

Replace the two function headers:

```kotlin
@Composable
fun TotaisScreen(
    vm: TotaisViewModel,
    onVerTag: (Tag) -> Unit,
    onAbrirMovimentacao: (Movimentacao) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by vm.state.collectAsState()
    TotaisContent(state, vm::mesAnterior, vm::proximoMes, onVerTag = onVerTag, onAbrirMovimentacao = onAbrirMovimentacao, modifier = modifier)
}

@Composable
fun TotaisContent(
    state: TotaisUiState,
    onMesAnterior: () -> Unit,
    onProximoMes: () -> Unit,
    onVerTag: (Tag) -> Unit = {},
    onAbrirMovimentacao: (Movimentacao) -> Unit = {},
    modifier: Modifier = Modifier,
) {
```

Replace the whole `if (t.topTags.isNotEmpty()) { … }` block (the "TAGS DO MÊS" section) with:

```kotlin
            state.insights?.let { SegmentoMesInsights(it, onVerTag, onAbrirMovimentacao) }
```

and remove the now-unused imports (`Box`, `size`, `CircleShape`, `Color`) only if the compiler/lint reports them unused — `Box` is still used for the empty state.

- [ ] **Step 5: `SaldoApp` wiring**

In `app/src/main/kotlin/com/scholze/saldo/ui/SaldoApp.kt` replace `SaldoTab.TOTAIS -> TotaisScreen(totaisVm)` with:

```kotlin
                    SaldoTab.TOTAIS -> TotaisScreen(
                        totaisVm,
                        onVerTag = { ledgerVm.definirTagFiltro(it); tab = SaldoTab.SALDOS },
                        // Mesma guarda do ledger: ocorrência virtual (id 0) não abre o editor.
                        onAbrirMovimentacao = { if (it.id != 0L) { entryVm.iniciarEdicao(it); sheetAberto = true } },
                    )
```

- [ ] **Step 6: Run the tests**

Run: `mise exec -- ./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.scholze.saldo.ui.totais.TotaisContentTest --console=plain 2>&1 | grep -i "tests on\|FAILED\|BUILD"` → `Finished 7 tests`, BUILD SUCCESSFUL.
Run: `mise exec -- ./gradlew test --console=plain -q 2>&1 | grep -c "^e:"` → `0` (JVM still 114).
Run: `mise exec -- ./gradlew lintDebug --console=plain -q 2>&1 | grep -v "^w:" | tail -3; grep -o "[0-9]* errors\?, [0-9]* warnings\?" app/build/reports/lint-results-debug.txt | head -1` → 0 errors.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/kotlin/com/scholze/saldo/ui/totais app/src/main/kotlin/com/scholze/saldo/ui/SaldoApp.kt app/src/androidTest/kotlin/com/scholze/saldo/ui/totais/TotaisContentTest.kt
git commit -m "feat: totais — para onde foi (barra por tag), maiores gastos, padrões"
```

---

### Task 5: The segmented control + the *tendência* segment

**Files:**
- Modify: `app/src/main/kotlin/com/scholze/saldo/ui/totais/TotaisViewModel.kt` (state gains `tendencia`)
- Create: `app/src/main/kotlin/com/scholze/saldo/ui/totais/SegmentoTendencia.kt`
- Modify: `app/src/main/kotlin/com/scholze/saldo/ui/totais/TotaisScreen.kt` (`SegmentoTotais`, the control, `onIrParaMes`)
- Modify: `app/src/main/kotlin/com/scholze/saldo/ui/SaldoApp.kt` (pass `onIrParaMes`)
- Test: `app/src/androidTest/kotlin/com/scholze/saldo/ui/totais/TotaisContentTest.kt` (extend)

**Interfaces:**
- Consumes: `InsightsEngine.tendencia`, `PontoMes` (Task 2), `TrendChart`, `ReservaLine` (Task 3), `SegmentedControl` (components), `TotaisViewModel.irPara`.
- Produces: `enum class SegmentoTotais(val rotulo: String) { MES("mês"), TENDENCIA("tendência") }` (A_CAMINHO added in Task 6); `TotaisUiState.tendencia: List<PontoMes>?`; `TotaisContent(..., onIrParaMes: (YearMonth) -> Unit = {}, ...)`; `TotaisScreen(vm, onVerTag, onAbrirMovimentacao, modifier)` (it wires `onIrParaMes = vm::irPara` itself); composable `SegmentoTendencia(pontos: List<PontoMes>, mesDestacado: YearMonth, onMes: (YearMonth) -> Unit)`; test tag `TAG_SEGMENTO_TOTAIS = "totais:segmento"`.

- [ ] **Step 1: Write the failing test**

In `TotaisContentTest.kt` add imports `com.scholze.saldo.domain.PontoMes` and `java.time.YearMonth` (already there), and the fixture + tests:

```kotlin
    private val tendencia = (5 downTo 0).map { k ->
        val m = YearMonth.of(2026, 7).minusMonths(k.toLong())
        PontoMes(
            // `k` é Int: o sufixo L é obrigatório, senão os campos Long não tipam.
            mes = m, entradas = 8_000_00, saidas = 7_000_00 + k * 100_00L, sobrou = 1_000_00 - k * 100_00L,
            reservaAcumulada = 1_200_00 - k * 100_00L, taxaPoupanca = 12 - k,
        )
    }

    @Test
    fun tendenciaMostraGraficoPoupancaEVoltaAoMesTocado() {
        var mesPedido: YearMonth? = null
        montar(TotaisUiState(YearMonth.of(2026, 7), totais, insights = insights, tendencia = tendencia), onIrParaMes = { mesPedido = it })
        rule.onNodeWithText("tendência").performClick()
        rule.onNodeWithText("6 MESES").assertIsDisplayed()
        rule.onNodeWithText("POUPANÇA").assertIsDisplayed()
        rule.onNodeWithText("12% este mês (jun 11%)").assertIsDisplayed()
        rule.onNodeWithText("PARA ONDE FOI").assertDoesNotExist()
        rule.onNodeWithText("mai").performClick()             // rótulo do mês no gráfico
        assertEquals(YearMonth.of(2026, 5), mesPedido)
        rule.onNodeWithText("PARA ONDE FOI").assertIsDisplayed() // voltou ao segmento mês
    }
```

and extend `montar` with `onIrParaMes: (YearMonth) -> Unit = {}` passed through to `TotaisContent(state, {}, {}, onVerTag = onVerTag, onAbrirMovimentacao = onAbrirMovimentacao, onIrParaMes = onIrParaMes)`.

Run the class → compile errors on `tendencia`/`onIrParaMes` (RED).

- [ ] **Step 2: ViewModel state**

`TotaisUiState` gains `val tendencia: List<PontoMes>? = null` (KDoc: "6 meses até o mês visto"), the `combine` sets `tendencia = InsightsEngine.tendencia(input, mes)`; import `com.scholze.saldo.domain.PontoMes`.

- [ ] **Step 3: `SegmentoTendencia.kt`**

```kotlin
package com.scholze.saldo.ui.totais

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.scholze.saldo.domain.PontoMes
import com.scholze.saldo.ui.components.HairlineDivider
import com.scholze.saldo.ui.components.InsetGroup
import com.scholze.saldo.ui.components.InsetRow
import com.scholze.saldo.ui.privacy.MoneyText
import com.scholze.saldo.ui.theme.SaldoTheme
import com.scholze.saldo.ui.totais.charts.ReservaLine
import com.scholze.saldo.ui.totais.charts.TrendChart
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

private val ptBr = Locale.forLanguageTag("pt-BR")
private val mesCurto = DateTimeFormatter.ofPattern("MMM", ptBr)

/** Segmento "tendência": os 6 meses (entradas, saídas, sobrou) e a poupança (reserva + taxa). */
@Composable
fun SegmentoTendencia(pontos: List<PontoMes>, mesDestacado: YearMonth, onMes: (YearMonth) -> Unit) {
    val colors = SaldoTheme.colors

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("6 MESES", style = SaldoTheme.type.sectionHeader, color = colors.secondaryLabel)
        InsetGroup {
            TrendChart(pontos, mesDestacado, onMes, Modifier.padding(start = 12.dp, end = 12.dp, top = 14.dp, bottom = 6.dp))
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Legenda(colors.categoryVariable, "saídas")
                Legenda(colors.balance, "entradas")
                Legenda(colors.tint, "sobrou")
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("POUPANÇA", style = SaldoTheme.type.sectionHeader, color = colors.secondaryLabel)
        InsetGroup {
            val atual = pontos.lastOrNull()
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("reserva acumulada", Modifier.weight(1f), style = SaldoTheme.type.body, color = colors.label)
                MoneyText(centavos = atual?.reservaAcumulada ?: 0L, style = SaldoTheme.type.body, color = colors.balance)
            }
            ReservaLine(pontos.map { it.reservaAcumulada }, Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp))
            HairlineDivider(startIndent = 16.dp)
            val anterior = pontos.getOrNull(pontos.size - 2)
            InsetRow(
                label = "taxa de poupança",
                value = when {
                    atual?.taxaPoupanca == null -> "—"
                    anterior?.taxaPoupanca == null -> "${atual.taxaPoupanca}% este mês"
                    else -> "${atual.taxaPoupanca}% este mês (${anterior.mes.format(mesCurto).removeSuffix(".")} ${anterior.taxaPoupanca}%)"
                },
            )
        }
        Text(
            "taxa = economia do mês ÷ entradas do mês",
            Modifier.padding(horizontal = 16.dp),
            style = SaldoTheme.type.caption, color = colors.secondaryLabel,
        )
    }
}

@Composable
private fun Legenda(cor: androidx.compose.ui.graphics.Color, rotulo: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.size(8.dp).background(cor, CircleShape))
        Text(rotulo, style = SaldoTheme.type.caption, color = SaldoTheme.colors.secondaryLabel)
    }
}
```

- [ ] **Step 4: The control in `TotaisScreen`**

Add to `TotaisScreen.kt` (top level, near the formatters):

```kotlin
/** Os segmentos da aba totais; a seleção é estado de tela (`rememberSaveable`), não de ViewModel. */
enum class SegmentoTotais(val rotulo: String) { MES("mês"), TENDENCIA("tendência") }

const val TAG_SEGMENTO_TOTAIS = "totais:segmento"
```

Add imports: `androidx.compose.runtime.mutableStateOf`, `androidx.compose.runtime.saveable.rememberSaveable`, `androidx.compose.runtime.setValue`, `androidx.compose.ui.platform.testTag`, `com.scholze.saldo.ui.components.SegmentedControl`.

`TotaisScreen` becomes:

```kotlin
@Composable
fun TotaisScreen(
    vm: TotaisViewModel,
    onVerTag: (Tag) -> Unit,
    onAbrirMovimentacao: (Movimentacao) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by vm.state.collectAsState()
    TotaisContent(
        state, vm::mesAnterior, vm::proximoMes,
        onVerTag = onVerTag, onAbrirMovimentacao = onAbrirMovimentacao, onIrParaMes = vm::irPara,
        modifier = modifier,
    )
}
```

`TotaisContent` gains `onIrParaMes: (YearMonth) -> Unit = {}` (after `onAbrirMovimentacao`), and inside, right after the hero `Column { … performance … }` block, insert:

```kotlin
            var segmento by rememberSaveable { mutableStateOf(SegmentoTotais.MES) }
            SegmentedControl(
                options = SegmentoTotais.entries.map { it.rotulo },
                selectedIndex = segmento.ordinal,
                onSelect = { segmento = SegmentoTotais.entries[it] },
                modifier = Modifier.testTag(TAG_SEGMENTO_TOTAIS),
            )

            when (segmento) {
                SegmentoTotais.MES -> {
                    // (the existing InsetGroups — números, reserva, fatura — and the insights section move inside this branch, unchanged)
                }
                SegmentoTotais.TENDENCIA -> state.tendencia?.let { pontos ->
                    SegmentoTendencia(pontos, state.mesAtual) { mes ->
                        onIrParaMes(mes)
                        segmento = SegmentoTotais.MES
                    }
                }
            }
```

Move the three existing `InsetGroup { … }` blocks (numbers, reserva, fatura) and `state.insights?.let { … }` into the `MES` branch verbatim.

- [ ] **Step 5: `SaldoApp`** — no change needed (`TotaisScreen` wires `vm::irPara`); confirm it compiles.

- [ ] **Step 6: Run the tests**

Run: `mise exec -- ./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.scholze.saldo.ui.totais.TotaisContentTest --console=plain 2>&1 | grep -i "tests on\|FAILED\|BUILD"` → `Finished 8 tests`, BUILD SUCCESSFUL. JVM `test` still 114/0. Lint 0 errors.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/kotlin/com/scholze/saldo/ui/totais app/src/androidTest/kotlin/com/scholze/saldo/ui/totais/TotaisContentTest.kt
git commit -m "feat: totais — segmentos mês | tendência, gráfico de 6 meses e poupança"
```

---

### Task 6: The *a caminho* segment

**Files:**
- Modify: `app/src/main/kotlin/com/scholze/saldo/ui/totais/TotaisViewModel.kt` (state gains `aCaminho`, `recorrencias`)
- Create: `app/src/main/kotlin/com/scholze/saldo/ui/totais/SegmentoACaminho.kt`
- Modify: `app/src/main/kotlin/com/scholze/saldo/ui/totais/TotaisScreen.kt` (`A_CAMINHO`, the branch, two callbacks)
- Modify: `app/src/main/kotlin/com/scholze/saldo/ui/SaldoApp.kt` (wire `onIrParaDia`)
- Test: `app/src/androidTest/kotlin/com/scholze/saldo/ui/totais/TotaisContentTest.kt` (extend)

**Interfaces:**
- Consumes: `InsightsEngine.aCaminho`/`recorrencias`, `ACaminho`/`ItemFuturo`/`ResumoRecorrencias` (Task 2), `ItemDia`, `LedgerViewModel.irPara(mes, dia)`.
- Produces: `TotaisUiState.aCaminho: ACaminho?` + `TotaisUiState.recorrencias: ResumoRecorrencias?`; `SegmentoTotais.A_CAMINHO("a caminho")`; `TotaisContent(…, onIrParaDia: (YearMonth, Int) -> Unit = {}, onAbrirRecorrencias: () -> Unit = {}, …)`; `TotaisScreen(vm, onVerTag, onAbrirMovimentacao, onIrParaDia, modifier)`; composable `SegmentoACaminho(a, resumo, mes, onIrParaDia, onAbrirRecorrencias)`.
- **The "recorrências ›" row is rendered here but inert until Task 7** (`onAbrirRecorrencias` keeps its default `{}` in `TotaisScreen`); this task's test drives it directly on `TotaisContent`. Do not add screen-level state for it now — Task 7 owns the takeover, and it lives in `SaldoApp`, where the ViewModel factories are.

- [ ] **Step 1: Write the failing tests**

In `TotaisContentTest.kt` add imports:

```kotlin
import com.scholze.saldo.domain.ACaminho
import com.scholze.saldo.domain.ItemDia
import com.scholze.saldo.domain.ItemFuturo
import com.scholze.saldo.domain.Recorrencia
import com.scholze.saldo.domain.ResumoRecorrencias
import org.junit.Assert.assertTrue
```

and after the `tendencia` fixture:

```kotlin
    private val aluguelMov = Movimentacao(
        id = 7, descricao = "aluguel", valorCentavos = -2_400_00, data = LocalDate.parse("2026-07-28"),
        natureza = Natureza.DIARIO, recorrenciaId = 1,
    )
    private val aCaminho = ACaminho(
        saemCentavos = 2_400_00,
        entramCentavos = 8_240_00,
        itens = listOf(
            ItemFuturo(aluguelMov.data, ItemDia.Mov(aluguelMov)),
            ItemFuturo(
                LocalDate.parse("2026-07-31"),
                ItemDia.Mov(
                    Movimentacao(
                        id = 8, descricao = "salário", valorCentavos = 8_240_00,
                        data = LocalDate.parse("2026-07-31"), natureza = Natureza.DIARIO,
                    ),
                ),
            ),
        ),
        mesEncerrado = false,
    )
    private val resumoRecorrencias = ResumoRecorrencias(
        ativas = listOf(
            Recorrencia(
                id = 1, descricao = "aluguel", valorCentavos = -2_400_00, natureza = Natureza.DIARIO,
                diaDoMes = 28, inicio = YearMonth.of(2026, 1),
            ),
        ),
        encerradas = emptyList(),
        entramMes = 8_240_00,
        saemMes = 2_400_00,
    )

    private fun comACaminho(a: ACaminho) = TotaisUiState(
        YearMonth.of(2026, 7), totais, insights = insights, tendencia = tendencia,
        aCaminho = a, recorrencias = resumoRecorrencias,
    )

    @Test
    fun aCaminhoMostraCabecalhoListaEAtalhoDeRecorrencias() {
        var dia: Pair<YearMonth, Int>? = null
        var recorrenciasPedidas = false
        montar(
            comACaminho(aCaminho),
            onIrParaDia = { m, d -> dia = m to d },
            onAbrirRecorrencias = { recorrenciasPedidas = true },
        )
        rule.onNodeWithText("a caminho").performClick()

        rule.onNodeWithText("ainda saem").assertIsDisplayed()
        rule.onNodeWithText("até 31 jul").assertIsDisplayed()
        rule.onNodeWithText("aluguel").assertIsDisplayed()
        rule.onNodeWithText("salário").assertIsDisplayed()
        rule.onNodeWithText("PARA ONDE FOI").assertDoesNotExist()

        rule.onNodeWithText("aluguel").performClick()
        assertEquals(YearMonth.of(2026, 7) to 28, dia)

        rule.onNodeWithText("1 fixa ·", substring = true).assertIsDisplayed()
        rule.onNodeWithText("recorrências").performClick()
        assertTrue(recorrenciasPedidas)
    }

    @Test
    fun mesPassadoMostraEncerradoEAindaOAtalho() {
        montar(comACaminho(ACaminho(0, 0, emptyList(), mesEncerrado = true)))
        rule.onNodeWithText("a caminho").performClick()
        rule.onNodeWithText("mês encerrado").assertIsDisplayed()
        rule.onNodeWithText("ainda saem").assertDoesNotExist()
        rule.onNodeWithText("recorrências").assertIsDisplayed()
    }

    @Test
    fun semItensFuturosMostraNadaAgendado() {
        montar(comACaminho(aCaminho.copy(saemCentavos = 0, entramCentavos = 0, itens = emptyList())))
        rule.onNodeWithText("a caminho").performClick()
        rule.onNodeWithText("nada agendado até o fim do mês").assertIsDisplayed()
    }
```

and extend `montar` with the two callbacks:

```kotlin
    private fun montar(
        state: TotaisUiState,
        oculto: Boolean = false,
        onVerTag: (Tag) -> Unit = {},
        onAbrirMovimentacao: (Movimentacao) -> Unit = {},
        onIrParaMes: (YearMonth) -> Unit = {},
        onIrParaDia: (YearMonth, Int) -> Unit = { _, _ -> },
        onAbrirRecorrencias: () -> Unit = {},
    ) {
        rule.setContent {
            SaldoTheme(darkTheme = false) {
                CompositionLocalProvider(LocalPrivacy provides PrivacyState(ocultoInicial = oculto)) {
                    TotaisContent(
                        state, {}, {},
                        onVerTag = onVerTag, onAbrirMovimentacao = onAbrirMovimentacao,
                        onIrParaMes = onIrParaMes, onIrParaDia = onIrParaDia,
                        onAbrirRecorrencias = onAbrirRecorrencias,
                    )
                }
            }
        }
    }
```

Run the class → compile errors on `aCaminho`/`recorrencias`/`onIrParaDia` (RED).

- [ ] **Step 2: ViewModel state**

In `TotaisViewModel.kt` the state gains two fields (imports `com.scholze.saldo.domain.ACaminho`, `com.scholze.saldo.domain.ResumoRecorrencias`):

```kotlin
data class TotaisUiState(
    val mesAtual: YearMonth,
    /** `null` enquanto o primeiro `LedgerInput` não chegou do banco. */
    val totais: TotaisMes?,
    val estimativaCentavos: Long = 0,
    /** "para onde foi" do mês visto; `null` junto com [totais]. */
    val insights: ParaOndeFoi? = null,
    /** 6 meses até o mês visto. */
    val tendencia: List<PontoMes>? = null,
    /** O que ainda passa pelo saldo depois de hoje até o fim do mês visto. */
    val aCaminho: ACaminho? = null,
    /** Só o resumo, para a linha de atalho; a tela própria tem seu ViewModel. */
    val recorrencias: ResumoRecorrencias? = null,
)
```

and in the `combine`:

```kotlin
            aCaminho = InsightsEngine.aCaminho(input, mes),
            recorrencias = InsightsEngine.recorrencias(input, mes),
```

- [ ] **Step 3: `SegmentoACaminho.kt`**

Create `app/src/main/kotlin/com/scholze/saldo/ui/totais/SegmentoACaminho.kt`:

```kotlin
package com.scholze.saldo.ui.totais

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.scholze.saldo.domain.ACaminho
import com.scholze.saldo.domain.ItemDia
import com.scholze.saldo.domain.ItemFuturo
import com.scholze.saldo.domain.ResumoRecorrencias
import com.scholze.saldo.ui.components.HairlineDivider
import com.scholze.saldo.ui.components.InsetGroup
import com.scholze.saldo.ui.components.InsetRow
import com.scholze.saldo.ui.components.SaldoGlyph
import com.scholze.saldo.ui.components.SaldoIcon
import com.scholze.saldo.ui.privacy.FormatoMoney
import com.scholze.saldo.ui.privacy.MoneyText
import com.scholze.saldo.ui.theme.SaldoTheme
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

private val ptBr = Locale.forLanguageTag("pt-BR")
private val diaMesCurto = DateTimeFormatter.ofPattern("d MMM", ptBr)

/** "1 fixa" / "3 fixas" — a contagem aparece aqui e na tela de recorrências. */
internal fun fixas(n: Int): String = if (n == 1) "1 fixa" else "$n fixas"

/**
 * Segmento "a caminho": o que ainda passa pela coluna de saldo depois de hoje até o fim do mês
 * visto, e o atalho para as recorrências. Tocar numa linha abre o dia dela no ledger.
 *
 * O atalho fica FORA do grupo da lista de propósito: ele vale também num mês encerrado, onde não
 * existe nada a caminho para listar.
 */
@Composable
fun SegmentoACaminho(
    a: ACaminho,
    resumo: ResumoRecorrencias?,
    mes: YearMonth,
    onIrParaDia: (YearMonth, Int) -> Unit,
    onAbrirRecorrencias: () -> Unit,
) {
    val colors = SaldoTheme.colors

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("A CAMINHO", style = SaldoTheme.type.sectionHeader, color = colors.secondaryLabel)
        InsetGroup {
            if (a.mesEncerrado) {
                InsetRow(label = "mês encerrado", value = "nada a caminho")
            } else {
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 11.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    // Duas linhas em vez de uma frase: com valor revelado de cinco dígitos a
                    // frase "ainda saem R$ … até 31 jul" estoura a largura numa tela pequena.
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("ainda saem", Modifier.weight(1f), style = SaldoTheme.type.body, color = colors.label)
                        MoneyText(
                            centavos = a.saemCentavos,
                            style = SaldoTheme.type.body, color = colors.categoryVariable,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "até " + mes.atEndOfMonth().format(diaMesCurto).removeSuffix("."),
                            style = SaldoTheme.type.caption, color = colors.secondaryLabel,
                        )
                        if (a.entramCentavos > 0) {
                            Text(" · entram ", style = SaldoTheme.type.caption, color = colors.secondaryLabel)
                            MoneyText(centavos = a.entramCentavos, style = SaldoTheme.type.caption, color = colors.positive)
                        }
                    }
                }
                if (a.itens.isEmpty()) {
                    HairlineDivider(startIndent = 16.dp)
                    InsetRow(label = "nada agendado até o fim do mês")
                } else {
                    a.itens.forEach { f ->
                        HairlineDivider(startIndent = 16.dp)
                        LinhaFuturo(f) { onIrParaDia(mes, f.data.dayOfMonth) }
                    }
                }
            }
        }
    }

    if (resumo != null) {
        InsetGroup {
            InsetRow(
                label = "recorrências",
                onClick = onAbrirRecorrencias,
                trailing = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(fixas(resumo.ativas.size) + " ·", style = SaldoTheme.type.footnote, color = colors.secondaryLabel)
                        MoneyText(centavos = resumo.saemMes, style = SaldoTheme.type.footnote, color = colors.secondaryLabel)
                        Text("/mês", style = SaldoTheme.type.footnote, color = colors.secondaryLabel)
                        SaldoGlyph(SaldoIcon.CHEVRON_RIGHT, colors.secondaryLabel, size = 14.dp, strokeWidth = 1.8.dp)
                    }
                },
            )
        }
    }
}

@Composable
private fun LinhaFuturo(f: ItemFuturo, onClick: () -> Unit) {
    val colors = SaldoTheme.colors
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            f.data.dayOfMonth.toString().padStart(2, '0'),
            Modifier.width(20.dp),
            style = SaldoTheme.type.footnote, color = colors.secondaryLabel,
        )
        Text(f.item.descricao, Modifier.weight(1f), style = SaldoTheme.type.body, color = colors.label)
        // Fixa (recorrência ou fatura): o mesmo glifo do ledger, mesma leitura.
        if (f.item.recorrente) {
            SaldoGlyph(SaldoIcon.RECORRENTE, colors.secondaryLabel, size = 12.dp, strokeWidth = 1.6.dp)
        }
        MoneyText(
            centavos = f.item.valorCentavos,
            style = SaldoTheme.type.body,
            color = if (f.item is ItemDia.FaturaDia) colors.secondaryLabel else colors.label,
            formato = FormatoMoney.ASSINADO,
        )
    }
}
```

- [ ] **Step 4: `TotaisScreen` — the third segment**

In `TotaisScreen.kt`:

```kotlin
enum class SegmentoTotais(val rotulo: String) { MES("mês"), TENDENCIA("tendência"), A_CAMINHO("a caminho") }
```

`TotaisScreen` gains `onIrParaDia: (YearMonth, Int) -> Unit` (after `onAbrirMovimentacao`) and passes it through; `TotaisContent` gains `onIrParaDia: (YearMonth, Int) -> Unit = {}` and `onAbrirRecorrencias: () -> Unit = {}` after `onIrParaMes`. Add the branch:

```kotlin
                SegmentoTotais.A_CAMINHO -> state.aCaminho?.let { a ->
                    SegmentoACaminho(a, state.recorrencias, state.mesAtual, onIrParaDia, onAbrirRecorrencias)
                }
```

- [ ] **Step 5: `SaldoApp` wiring**

```kotlin
                    SaldoTab.TOTAIS -> TotaisScreen(
                        totaisVm,
                        onVerTag = { ledgerVm.definirTagFiltro(it); tab = SaldoTab.SALDOS },
                        onAbrirMovimentacao = { if (it.id != 0L) { entryVm.iniciarEdicao(it); sheetAberto = true } },
                        onIrParaDia = { mes, dia -> ledgerVm.irPara(mes, dia); tab = SaldoTab.SALDOS },
                    )
```

- [ ] **Step 6: Run the tests**

Run: `mise exec -- ./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.scholze.saldo.ui.totais.TotaisContentTest --console=plain 2>&1 | grep -i "tests on\|FAILED\|BUILD"` → `Finished 11 tests`, BUILD SUCCESSFUL.
Run: `mise exec -- ./gradlew test --console=plain -q 2>&1 | grep -c "^e:"` → `0` (JVM still 114).
Run: `mise exec -- ./gradlew lintDebug --console=plain -q > /dev/null 2>&1; grep -o "[0-9]* errors\?, [0-9]* warnings\?" app/build/reports/lint-results-debug.txt | head -1` → 0 errors.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/kotlin/com/scholze/saldo/ui/totais app/src/main/kotlin/com/scholze/saldo/ui/SaldoApp.kt app/src/androidTest/kotlin/com/scholze/saldo/ui/totais/TotaisContentTest.kt
git commit -m "feat: totais — segmento a caminho (o que ainda sai até o fim do mês)"
```

---

### Task 7: `RecorrenciasScreen` + `RecorrenciasViewModel`

**Files:**
- Create: `app/src/main/kotlin/com/scholze/saldo/ui/totais/RecorrenciasViewModel.kt`
- Create: `app/src/main/kotlin/com/scholze/saldo/ui/totais/RecorrenciasScreen.kt`
- Modify: `app/src/main/kotlin/com/scholze/saldo/ui/SaldoApp.kt` (the takeover + wire `onAbrirRecorrencias`)
- Modify: `app/src/main/kotlin/com/scholze/saldo/ui/totais/TotaisScreen.kt` (`onAbrirRecorrencias` param)
- Test: `app/src/androidTest/kotlin/com/scholze/saldo/ui/totais/RecorrenciasScreenTest.kt` (create)

**Interfaces:**
- Consumes: `InsightsEngine.recorrencias`, `ResumoRecorrencias` (Task 2), `SaldoRepository.abrirMes/ledger`, `EntryViewModel.iniciarEdicao` (through `SaldoApp`'s existing `onAbrirMovimentacao`), `fixas(n)` from `SegmentoACaminho.kt` (Task 6).
- Produces: `RecorrenciasViewModel(repo)` with `state: StateFlow<ResumoRecorrencias?>`, `verMes(mes)`, `abrirOcorrencia(rec, onPronta)` and `factory(container)`; composable `RecorrenciasScreen(vm, mes, onAbrirMovimentacao, onVoltar, modifier)`; `TotaisScreen(…, onAbrirRecorrencias: () -> Unit, …)`.
- **Where the takeover lives:** `SaldoApp`, not `TotaisScreen` — the screen needs its own ViewModel and the factories live in `SaldoApp` (unlike mais › lembretes, where `LembretesScreen` is stateless and rides `MaisViewModel`). `TotaisScreen` only reports the tap.

- [ ] **Step 1: Write the failing test**

Create `app/src/androidTest/kotlin/com/scholze/saldo/ui/totais/RecorrenciasScreenTest.kt`. It drives the real container (`DeepLinkTest`'s pattern: seed, then launch the activity on a `Destino`), because the point of the test is the *round trip* — template → materialized occurrence → editor:

```kotlin
package com.scholze.saldo.ui.totais

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.scholze.saldo.EstadoLimpo
import com.scholze.saldo.MainActivity
import com.scholze.saldo.SaldoApplication
import com.scholze.saldo.domain.Movimentacao
import com.scholze.saldo.domain.Natureza
import com.scholze.saldo.domain.RepetirOpcao
import com.scholze.saldo.ui.nav.Destino
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * totais › a caminho › recorrências, pelo container real: o template aparece e tocá-lo abre a
 * ocorrência DO MÊS VISTO no editor (materializando o mês, se preciso).
 */
@RunWith(AndroidJUnit4::class)
class RecorrenciasScreenTest {

    @get:Rule(order = 0)
    val estadoLimpo = EstadoLimpo()

    @get:Rule(order = 1)
    val rule = createEmptyComposeRule()

    @Test
    fun listaAsFixasEAbreAOcorrenciaDoMes() {
        val app = ApplicationProvider.getApplicationContext<SaldoApplication>()
        val hoje = LocalDate.now()
        runBlocking {
            app.container.settings.definirSaldoInicial(100_000_00, hoje)
            // Dia 28 existe em todo mês — o teste roda em qualquer data.
            app.container.repository.criar(
                Movimentacao(
                    descricao = "aluguel", valorCentavos = -2_400_00,
                    data = hoje.withDayOfMonth(28), natureza = Natureza.DIARIO,
                ),
                RepetirOpcao.TodoMes(28),
            )
        }

        ActivityScenario.launch<MainActivity>(MainActivity.intent(app, Destino.Totais(YearMonth.from(hoje)))).use {
            rule.waitUntil(5_000) { rule.onAllNodesWithText("a caminho").fetchSemanticsNodes().isNotEmpty() }
            rule.onNodeWithText("a caminho").performClick()
            rule.onNodeWithText("recorrências").performScrollTo().performClick()

            rule.waitUntil(5_000) { rule.onAllNodesWithText("dia 28").fetchSemanticsNodes().isNotEmpty() }
            rule.onNodeWithText("aluguel").assertIsDisplayed()
            rule.onNodeWithText("1 fixa").assertIsDisplayed()

            rule.onNodeWithText("aluguel").performClick()
            rule.waitUntil(5_000) { rule.onAllNodesWithText("editar movimentação").fetchSemanticsNodes().isNotEmpty() }
            rule.onNodeWithText("editar movimentação").assertIsDisplayed()
        }
    }

    @Test
    fun voltaParaTotais() {
        val app = ApplicationProvider.getApplicationContext<SaldoApplication>()
        runBlocking { app.container.settings.definirSaldoInicial(100_000_00, LocalDate.now()) }

        ActivityScenario.launch<MainActivity>(MainActivity.intent(app, Destino.Totais(YearMonth.now()))).use {
            rule.waitUntil(5_000) { rule.onAllNodesWithText("a caminho").fetchSemanticsNodes().isNotEmpty() }
            rule.onNodeWithText("a caminho").performClick()
            rule.onNodeWithText("recorrências").performScrollTo().performClick()
            rule.waitUntil(5_000) { rule.onAllNodesWithText("nenhuma recorrência").fetchSemanticsNodes().isNotEmpty() }
            rule.onNodeWithText("‹ totais").performClick()
            rule.waitUntil(5_000) { rule.onAllNodesWithText("A CAMINHO").fetchSemanticsNodes().isNotEmpty() }
        }
    }
}
```

Run: `mise exec -- ./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.scholze.saldo.ui.totais.RecorrenciasScreenTest --console=plain 2>&1 | grep "^e:" | head -3` → red (no `RecorrenciasScreen`; the "recorrências" row goes nowhere).

- [ ] **Step 2: `RecorrenciasViewModel.kt`**

```kotlin
package com.scholze.saldo.ui.totais

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.scholze.saldo.AppContainer
import com.scholze.saldo.data.SaldoRepository
import com.scholze.saldo.domain.InsightsEngine
import com.scholze.saldo.domain.Movimentacao
import com.scholze.saldo.domain.Recorrencia
import com.scholze.saldo.domain.ResumoRecorrencias
import java.time.YearMonth
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** A tela de recorrências: um resumo do mês visto na aba totais, mais a ponte para o editor. */
class RecorrenciasViewModel(private val repo: SaldoRepository) : ViewModel() {

    private val mes = MutableStateFlow(YearMonth.now())

    val state: StateFlow<ResumoRecorrencias?> = combine(repo.ledger, mes) { input, m ->
        InsightsEngine.recorrencias(input, m)
    }
        .flowOn(Dispatchers.Default)
        // Mesma razão do TotaisViewModel: uma exceção do banco não pode congelar a tela em silêncio.
        .catch { Log.e(TAG, "fluxo de recorrências falhou", it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** O mês visto é o da aba totais; esta tela é só uma leitura dele. */
    fun verMes(m: YearMonth) {
        mes.value = m
    }

    /**
     * Abre no editor a ocorrência de [rec] **no mês visto**.
     *
     * A ocorrência pode ainda ser virtual (`id == 0`) num mês não materializado: [SaldoRepository.abrirMes]
     * materializa e a releitura do ledger traz a linha com id — que é o que `iniciarEdicao` precisa
     * para gravar com `SO_ESTE_MES`. Um template que só começa depois do mês visto não tem
     * ocorrência nenhuma: nada abre (a tela também não deixa tocar nessas linhas).
     */
    fun abrirOcorrencia(rec: Recorrencia, onPronta: (Movimentacao) -> Unit) {
        val m = mes.value
        viewModelScope.launch {
            try {
                repo.abrirMes(m)
                val ocorrencia = repo.ledger.first().movimentacoes.firstOrNull {
                    it.recorrenciaId == rec.id && YearMonth.from(it.data) == m
                }
                if (ocorrencia != null && ocorrencia.id != 0L) {
                    onPronta(ocorrencia)
                } else {
                    Log.w(TAG, "sem ocorrência da recorrência ${rec.id} em $m")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "abrirOcorrencia(${rec.id}, $m) falhou", e)
            }
        }
    }

    companion object {
        private const val TAG = "saldo"

        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { RecorrenciasViewModel(container.repository) }
        }
    }
}
```

- [ ] **Step 3: `RecorrenciasScreen.kt`**

```kotlin
package com.scholze.saldo.ui.totais

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.scholze.saldo.domain.Movimentacao
import com.scholze.saldo.domain.Recorrencia
import com.scholze.saldo.ui.components.HairlineDivider
import com.scholze.saldo.ui.components.InsetGroup
import com.scholze.saldo.ui.components.InsetRow
import com.scholze.saldo.ui.privacy.FormatoMoney
import com.scholze.saldo.ui.privacy.MoneyText
import com.scholze.saldo.ui.theme.SaldoTheme
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

private val ptBr = Locale.forLanguageTag("pt-BR")
private val mesAbrev = DateTimeFormatter.ofPattern("MMM", ptBr)

/** "set/26" — mesmo rótulo curto da aba totais (o ponto de "set." colidiria com a barra). */
private fun YearMonth.rotuloCurto(): String =
    format(mesAbrev).removeSuffix(".") + "/" + (year % 100).toString().padStart(2, '0')

/**
 * totais › recorrências: toma a aba, lista os templates do mês visto por dia do mês e abre a
 * ocorrência daquele mês no editor — é lá que "daqui em diante" e "excluir recorrência" já
 * existem, então esta tela não repete nenhuma ação de escrita.
 */
@Composable
fun RecorrenciasScreen(
    vm: RecorrenciasViewModel,
    mes: YearMonth,
    onAbrirMovimentacao: (Movimentacao) -> Unit,
    onVoltar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SaldoTheme.colors
    BackHandler(onBack = onVoltar)
    // O mês vem da aba: navegar o mês em totais e voltar aqui mostra o mês certo.
    LaunchedEffect(mes) { vm.verMes(mes) }
    val resumo by vm.state.collectAsState()

    Column(modifier.fillMaxSize().background(colors.background)) {
        Box(Modifier.fillMaxWidth().background(colors.navBar).padding(vertical = 12.dp)) {
            Text(
                "‹ totais",
                Modifier.align(Alignment.CenterStart)
                    .clickable(role = Role.Button, onClick = onVoltar)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                style = SaldoTheme.type.body, color = colors.tint,
            )
            Text(
                "recorrências · " + mes.rotuloCurto(), Modifier.fillMaxWidth(),
                style = SaldoTheme.type.navTitle, color = colors.label, textAlign = TextAlign.Center,
            )
        }
        HairlineDivider()

        // `null` só até o primeiro LedgerInput chegar — mesma escolha do resto do app.
        val r = resumo ?: run {
            Box(Modifier.fillMaxSize())
            return@Column
        }

        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 32.dp)),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(fixas(r.ativas.size), style = SaldoTheme.type.body, color = colors.label)
                InsetGroup {
                    LinhaMes("entram por mês", r.entramMes, colors.positive)
                    HairlineDivider(startIndent = 16.dp)
                    LinhaMes("saem por mês", -r.saemMes)
                }
            }

            InsetGroup {
                if (r.ativas.isEmpty()) {
                    InsetRow(label = "nenhuma recorrência")
                } else {
                    r.ativas.forEachIndexed { i, rec ->
                        if (i > 0) HairlineDivider(startIndent = 16.dp)
                        // Um template que só começa depois do mês visto não tem ocorrência para
                        // editar: a linha existe (é uma fixa ativa), mas não abre nada.
                        LinhaRecorrencia(
                            rec, mes,
                            onClick = if (rec.inicio <= mes) {
                                { vm.abrirOcorrencia(rec, onAbrirMovimentacao) }
                            } else {
                                null
                            },
                        )
                    }
                }
            }

            if (r.encerradas.isNotEmpty()) {
                var abertas by rememberSaveable { mutableStateOf(false) }
                InsetGroup {
                    InsetRow(
                        label = "encerradas (${r.encerradas.size})",
                        value = if (abertas) "esconder" else "ver",
                        valueColor = colors.tint,
                        onClick = { abertas = !abertas },
                    )
                    if (abertas) {
                        r.encerradas.forEach { rec ->
                            HairlineDivider(startIndent = 16.dp)
                            LinhaRecorrencia(rec, mes, onClick = null)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LinhaMes(rotulo: String, centavos: Long, cor: Color? = null) {
    val colors = SaldoTheme.colors
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(rotulo, Modifier.weight(1f), style = SaldoTheme.type.body, color = colors.label)
        MoneyText(
            centavos = centavos,
            style = SaldoTheme.type.body, color = cor ?: colors.label,
            formato = FormatoMoney.ASSINADO,
        )
    }
}

@Composable
private fun LinhaRecorrencia(rec: Recorrencia, mes: YearMonth, onClick: (() -> Unit)?) {
    val colors = SaldoTheme.colors
    val base = Modifier.fillMaxWidth()
    Row(
        (if (onClick != null) base.clickable(onClick = onClick) else base).padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("dia ${rec.diaDoMes}", Modifier.width(50.dp), style = SaldoTheme.type.footnote, color = colors.secondaryLabel)
        Column(Modifier.weight(1f)) {
            Text(rec.descricao, style = SaldoTheme.type.body, color = colors.label)
            val nota = when {
                rec.inicio > mes -> "começa em " + rec.inicio.rotuloCurto()
                !rec.ativa -> "encerrada"
                rec.fim != null -> "até " + rec.fim.rotuloCurto()
                else -> null
            }
            if (nota != null) {
                Text(nota, style = SaldoTheme.type.caption, color = colors.secondaryLabel)
            }
        }
        if (rec.tags.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                rec.tags.take(3).forEach { Box(Modifier.size(6.dp).background(Color(it.cor), CircleShape)) }
            }
        }
        MoneyText(
            centavos = rec.valorCentavos,
            style = SaldoTheme.type.body, color = colors.label, formato = FormatoMoney.ASSINADO,
        )
    }
}
```

- [ ] **Step 4: `TotaisScreen` reports the tap**

`TotaisScreen` gains `onAbrirRecorrencias: () -> Unit` (after `onIrParaDia`) and forwards it to `TotaisContent`.

- [ ] **Step 5: `SaldoApp` — the takeover**

Add the factory next to the others, the flag, and the `Destino`/tab hygiene:

```kotlin
    val recorrenciasFactory = remember(container) { RecorrenciasViewModel.factory(container) }
    val totaisState by totaisVm.state.collectAsState()
    // A tela de recorrências toma a aba totais; sair da aba fecha (voltar depois em "totais"
    // deve mostrar totais, não a subtela onde o usuário estava dez minutos antes).
    var abrindoRecorrencias by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(tab) { if (tab != SaldoTab.TOTAIS) abrindoRecorrencias = false }
```

The edit route is now used from two places, so hoist it (it keeps the same `id != 0` guard):

```kotlin
    val abrirMovimentacao: (Movimentacao) -> Unit = { if (it.id != 0L) { entryVm.iniciarEdicao(it); sheetAberto = true } }
```

`Destino.Totais` also closes it (a deep link means "show me the totais of that month"):

```kotlin
            is Destino.Totais -> { totaisVm.irPara(destino.mes); abrindoRecorrencias = false; tab = SaldoTab.TOTAIS }
```

and the branch:

```kotlin
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
                            onVerTag = { ledgerVm.definirTagFiltro(it); tab = SaldoTab.SALDOS },
                            onAbrirMovimentacao = abrirMovimentacao,
                            onIrParaDia = { mes, dia -> ledgerVm.irPara(mes, dia); tab = SaldoTab.SALDOS },
                            onAbrirRecorrencias = { abrindoRecorrencias = true },
                        )
                    }
```

Use `abrirMovimentacao` for the ledger's `onItemClick` too (same lambda, one behaviour), keeping its comment about `id == 0`. Imports: `com.scholze.saldo.domain.Movimentacao`, `com.scholze.saldo.ui.totais.RecorrenciasScreen`, `com.scholze.saldo.ui.totais.RecorrenciasViewModel`.

- [ ] **Step 6: Run the tests**

Run: `mise exec -- ./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.scholze.saldo.ui.totais.RecorrenciasScreenTest --console=plain 2>&1 | grep -i "tests on\|FAILED\|BUILD"` → `Finished 2 tests`, BUILD SUCCESSFUL.
Then the whole suites: `mise run test-device` → 71 instrumented (62 + 3 + 1 + 3 + 2), `mise run test` → 114 JVM, `mise exec -- ./gradlew lintDebug` → 0 errors.

If `RecorrenciasScreenTest` is flaky on the first tap, the cause is almost certainly the seed: `criar` with `RepetirOpcao.TodoMes` writes the template *and* the current month's instance, so the tap needs no materialization — but a month opened later (`Destino.Totais` of another month) does. Keep `waitUntil` around the editor assertion, never a fixed sleep.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/kotlin/com/scholze/saldo/ui app/src/androidTest/kotlin/com/scholze/saldo/ui/totais/RecorrenciasScreenTest.kt
git commit -m "feat: tela de recorrências — fixas do mês, abre a ocorrência no editor"
```

---

### Task 8: Emulator pass, README, spec sync

**Files:**
- Modify: `README.md`
- Modify: `docs/superpowers/specs/2026-08-17-saldo-insights-1-design.md` (only if the build deviated)
- Evidence: `.superpowers/sdd/shots/29-*.png` … (gitignored)

- [ ] **Step 1: Emulator pass**

Boot `saldo_test` (`mise exec -- emulator -avd saldo_test -no-window -no-audio -no-snapshot &`), install (`mise exec -- ./gradlew installDebug`), and drive it by hand (`adb shell input tap/swipe`, `adb exec-out screencap -p > shot.png`). Note that `connectedDebugAndroidTest` **uninstalls** the app, so run this pass after the test runs, not before.

Seed something worth looking at first — a couple of months of movimentações with tags, one recorrência, one cartão purchase — via the app or `adb shell am start` + taps; then capture:

- [ ] `29-mes.png` — segmento *mês*: segmented bar, fatias with deltas, maiores gastos, padrões.
- [ ] `30-mes-oculto.png` — the same with the privacy mask on (every number `R$ •••••`, shapes intact).
- [ ] `31-tendencia.png` — 6-month chart + legend, reserva line, taxa row.
- [ ] `32-a-caminho.png` — header, dated list with the ↺ glyph, recorrências row.
- [ ] `33-recorrencias.png` — the overview screen (with an *encerradas* group if the seed has one).
- [ ] `34-tendencia-escuro.png`, `35-a-caminho-escuro.png` — dark theme (mais › tema › escuro).

Check while driving: tapping a month bar lands on that month's *mês* segment; tapping a tag row opens the ledger filtered; tapping a future item lands on its day; the segmented control keeps its selection across a rotation (`adb shell settings put system user_rotation 1`) and across a tab round trip; the recorrências screen returns with both `‹ totais` and the system back gesture.

- [ ] **Step 2: README**

In the totais section (search for "totais" in `README.md`), replace the "tags do mês" sentence with the three segments:

```markdown
- **totais** — três segmentos sobre o mês navegado: **mês** (para onde foi o dinheiro, com
  barra por tag, maiores gastos e padrões), **tendência** (6 meses de entradas/saídas/sobrou,
  reserva acumulada e taxa de poupança) e **a caminho** (o que ainda sai e entra até o fim do
  mês, com atalho para a tela de recorrências). Todo número respeita a máscara de privacidade.
```

- [ ] **Step 3: Spec sync**

Read the spec's "Testing" and "a caminho"/"RecorrênciasScreen" sections against what was built and fix the spec where the build deviated deliberately (e.g. the a-caminho header split into two lines; the recorrências shortcut rendered outside the list group so it survives a closed month; templates starting after the viewed month listed but not tappable). Note each deviation in `.superpowers/sdd/progress.md` as well.

- [ ] **Step 4: Final counts**

Run all three and paste the real numbers into the ledger:

```bash
mise run test                                  # esperado: 114 JVM
mise run test-device                           # esperado: 71 instrumentados
mise exec -- ./gradlew lintDebug --console=plain -q; grep -o "[0-9]* errors\?, [0-9]* warnings\?" app/build/reports/lint-results-debug.txt | head -1
```

- [ ] **Step 5: Commit**

```bash
git add README.md docs/superpowers/specs/2026-08-17-saldo-insights-1-design.md
git commit -m "docs: README + spec sync para os insights de totais"
```

Then: final whole-branch review (most capable model, whole diff `main..insights-1`), fix wave if needed, and `superpowers:finishing-a-development-branch` — the merge/PR call is the user's.
