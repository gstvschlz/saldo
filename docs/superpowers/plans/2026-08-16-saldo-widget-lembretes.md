# saldo — widget + lembretes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A home-screen widget (saldo projetado + quick add, masked by default) and four opt-in local reminders (fatura vence amanhã, recorrência hoje, registrar gastos, fechamento do mês), per `docs/superpowers/specs/2026-08-16-saldo-widget-lembretes-design.md`.

**Architecture:** Reminder rules live in a pure-Kotlin `LembretesEngine` (domain) that reuses `ProjectionEngine`; a WorkManager `CoroutineWorker` runs it at two daily slots and posts notifications; a Glance widget renders the current-month projection from the same repository and is refreshed by an in-process observer. Both open the app through a small `Destino` deep-link type carried as Intent extras.

**Tech Stack:** Kotlin 2.2, Compose (BOM 2026.08.00), Room 2.8.4, DataStore, **Jetpack Glance 1.1.1**, **WorkManager 2.11.2**, JUnit4, `glance-appwidget-testing`, `work-testing`, `androidx.test:rules 1.7.0`.

## Global Constraints

- pt-BR copy, lowercase labels, money only through `com.scholze.saldo.ui.money` (U+2212 minus), exactly like the rest of the app.
- 100 % local: no network, no new storage outside the app, the DB stays out of cloud backup (nothing in this plan touches `AndroidManifest` backup attributes or `res/xml/*rules.xml`).
- Only new permission: `android.permission.POST_NOTIFICATIONS`. No exact alarms, no boot receiver, no foreground service.
- Defaults: all four reminder toggles **off**; `widgetMostrarValores` **false** (widget masked as `R$ •••••`); `horaInformativos` **09:00**, `horaNudge` **20:00**.
- Notifications always carry amounts, `VISIBILITY_PRIVATE` with a title-only public version; stable ids 1001 (fatura), 1002 (recorrências), 1003 (fechamento), 1004 (registrar).
- Widget: Glance, `SizeMode.Responsive` with `COMPACTO = 110×50 dp` and `LARGO = 250×50 dp`; `updatePeriodMillis = 21600000`.
- MainActivity is `android:launchMode="singleTop"` so widget/notification intents reach `onNewIntent` when the app is open.
- Versions: `glance = "1.1.1"`, `work = "2.11.2"`, `androidxTestRules = "1.7.0"` (added to `gradle/libs.versions.toml`).
- Every task ends green: JVM `mise run test` (55 tests today), instrumented `mise run test-device` on the `saldo_test` emulator (43 today), `mise exec -- ./gradlew lintDebug` with 0 errors. In this repo the mise tasks call `.\gradlew.bat` on Windows; the commands below use `mise exec -- ./gradlew …` which works from Git Bash.
- After a Room schema change you must run the instrumented suite twice (androidTest assets merge before KSP writes the schema JSON) — **no schema change in this plan**, so this does not apply.
- Commit after each task with the message given in the task (conventional prefix, imperative, no trailer needed).

## File structure

| File | Responsibility |
|---|---|
| `app/src/main/kotlin/com/scholze/saldo/domain/Modelos.kt` (modify) | `Movimentacao.criadaEm` |
| `app/src/main/kotlin/com/scholze/saldo/data/db/Entities.kt` (modify) | map `criadaEm` both ways |
| `app/src/main/kotlin/com/scholze/saldo/domain/Lembretes.kt` (create) | `LembretesConfig` (toggles + hours) |
| `app/src/main/kotlin/com/scholze/saldo/data/SettingsStore.kt` (modify) | keys + `Settings.widgetMostrarValores` / `Settings.lembretes` + setters |
| `app/src/main/kotlin/com/scholze/saldo/domain/LembretesEngine.kt` (create) | `Slot`, `Lembrete`, `LembretesEngine.avaliar` — every reminder rule |
| `app/src/main/kotlin/com/scholze/saldo/domain/ProjectionEngine.kt` (modify) | public `faturasAte` |
| `app/src/main/kotlin/com/scholze/saldo/ui/nav/Destino.kt` (create) | deep-link type + Intent codec |
| `app/src/main/kotlin/com/scholze/saldo/MainActivity.kt` (modify) | Intent → `Destino`, `onNewIntent`, `intent(context, destino)` |
| `app/src/main/kotlin/com/scholze/saldo/ui/SaldoApp.kt` (modify) | consume `Destino` once; hoist `TotaisViewModel` |
| `app/src/main/kotlin/com/scholze/saldo/ui/ledger/LedgerViewModel.kt` (modify) | public `irPara(mes, dia)`, `AlvoLedger` |
| `app/src/main/kotlin/com/scholze/saldo/ui/ledger/LedgerScreen.kt` (modify) | scroll to `AlvoLedger` |
| `app/src/main/kotlin/com/scholze/saldo/ui/totais/TotaisViewModel.kt` (modify) | public `irPara` |
| `app/src/main/kotlin/com/scholze/saldo/widget/WidgetEstado.kt` (create) | what the widget shows |
| `app/src/main/kotlin/com/scholze/saldo/widget/SaldoWidgetContent.kt` (create) | pure Glance UI (unit-testable) |
| `app/src/main/kotlin/com/scholze/saldo/widget/SaldoWidget.kt` (create) | `GlanceAppWidget` + receiver: loads state |
| `app/src/main/kotlin/com/scholze/saldo/widget/WidgetRefresher.kt` (create) | in-process `updateAll` on writes |
| `app/src/main/res/xml/saldo_widget_info.xml` (create) | provider info |
| `app/src/main/kotlin/com/scholze/saldo/lembretes/Notificacoes.kt` (create) | channel + one builder per `Lembrete` |
| `app/src/main/kotlin/com/scholze/saldo/lembretes/LembretesScheduler.kt` (create) | slot math + WorkManager enqueue/cancel |
| `app/src/main/kotlin/com/scholze/saldo/lembretes/LembretesWorker.kt` (create) | the daily run |
| `app/src/main/res/drawable/ic_notificacao.xml` (create) | small icon |
| `app/src/main/kotlin/com/scholze/saldo/SaldoApplication.kt` (modify) | `AppContainer`: scope, refresher, scheduler; channel + `agendar(KEEP)` at start |
| `app/src/main/kotlin/com/scholze/saldo/ui/mais/MaisViewModel.kt` (modify) | new setters + scheduling |
| `app/src/main/kotlin/com/scholze/saldo/ui/mais/MaisScreen.kt` (modify) | widget toggle, lembretes row |
| `app/src/main/kotlin/com/scholze/saldo/ui/mais/LembretesScreen.kt` (create) | toggles, hours, permission |
| `app/src/main/AndroidManifest.xml` (modify) | permission, singleTop, widget receiver |

---

### Task 1: `Movimentacao.criadaEm` in the domain model

**Files:**
- Modify: `app/src/main/kotlin/com/scholze/saldo/domain/Modelos.kt` (data class `Movimentacao`)
- Modify: `app/src/main/kotlin/com/scholze/saldo/data/db/Entities.kt` (`MovimentacaoComTags.toDomain()`, `Movimentacao.toEntity()`)
- Test: `app/src/test/kotlin/com/scholze/saldo/data/db/EntitiesTest.kt` (create)

**Interfaces:**
- Produces: `Movimentacao.criadaEm: Long` (epoch millis, `0` = unknown). Task 3's nudge rule reads it.

- [ ] **Step 1: Write the failing test**

Create `app/src/test/kotlin/com/scholze/saldo/data/db/EntitiesTest.kt`:

```kotlin
package com.scholze.saldo.data.db

import com.scholze.saldo.domain.Movimentacao
import com.scholze.saldo.domain.Natureza
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EntitiesTest {
    private val base = Movimentacao(
        descricao = "m", valorCentavos = -1_00, data = LocalDate.parse("2026-07-20"), natureza = Natureza.DIARIO,
    )

    /** Linha nova: o carimbo é "agora". */
    @Test
    fun toEntityCarimbaAgoraQuandoCriadaEmEDesconhecido() {
        val antes = System.currentTimeMillis()
        assertTrue(base.toEntity().criadaEm >= antes)
    }

    /** `restaurar` (desfazer) reinsere o snapshot: a data de criação original tem de sobreviver. */
    @Test
    fun toEntityPreservaCriadaEmConhecido() {
        assertEquals(123L, base.copy(criadaEm = 123L).toEntity().criadaEm)
    }

    @Test
    fun toDomainCarregaCriadaEm() {
        val entity = MovimentacaoEntity(
            id = 1, descricao = "m", valorCentavos = -1_00, dataEpochDay = 20654, natureza = "DIARIO", criadaEm = 123L,
        )
        assertEquals(123L, MovimentacaoComTags(entity, emptyList()).toDomain().criadaEm)
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `mise exec -- ./gradlew test --console=plain -q 2>&1 | grep "^e:" | head`
Expected: `e: ... EntitiesTest.kt: ... Unresolved reference 'criadaEm'` (twice: `copy(criadaEm = …)` and `.criadaEm`).

- [ ] **Step 3: Add the field and map it**

In `app/src/main/kotlin/com/scholze/saldo/domain/Modelos.kt`, replace the `Movimentacao` class with:

```kotlin
data class Movimentacao(
    val id: Long = 0,
    val descricao: String,
    val valorCentavos: Long,          // signed; negative = saída
    val data: LocalDate,
    val natureza: Natureza,
    val recorrenciaId: Long? = null,
    val editadaManualmente: Boolean = false,
    val tags: List<Tag> = emptyList(),
    /**
     * Epoch millis de quando a linha foi criada; `0` = desconhecido (fixtures de teste, ocorrências
     * virtuais). É o sinal honesto de "o usuário lançou algo hoje" — a `data` não serve: uma
     * recorrência materializada hoje tem `data` de hoje sem ninguém ter lançado nada, e uma
     * despesa de ontem lançada hoje tem `data` de ontem.
     */
    val criadaEm: Long = 0,
)
```

In `app/src/main/kotlin/com/scholze/saldo/data/db/Entities.kt`:

`MovimentacaoComTags.toDomain()` becomes:

```kotlin
    fun toDomain() = Movimentacao(
        id = mov.id, descricao = mov.descricao, valorCentavos = mov.valorCentavos,
        data = LocalDate.ofEpochDay(mov.dataEpochDay), natureza = Natureza.valueOf(mov.natureza),
        recorrenciaId = mov.recorrenciaId, editadaManualmente = mov.editadaManualmente,
        tags = tags.map { it.toDomain() },
        criadaEm = mov.criadaEm,
    )
```

`Movimentacao.toEntity()` becomes:

```kotlin
/**
 * Tags are persisted separately via `MovimentacaoDao.setTags` — inserting this entity alone writes no tags.
 * `criadaEm` conhecido é preservado (o "desfazer" reinsere o snapshot com a data de criação
 * original); só uma linha nova (`criadaEm == 0`) recebe o carimbo de agora.
 */
fun Movimentacao.toEntity() = MovimentacaoEntity(
    id = id, descricao = descricao, valorCentavos = valorCentavos,
    dataEpochDay = data.toEpochDay(), natureza = natureza.name,
    recorrenciaId = recorrenciaId, editadaManualmente = editadaManualmente,
    criadaEm = if (criadaEm != 0L) criadaEm else System.currentTimeMillis(),
)
```

- [ ] **Step 4: Run the JVM suite**

Run: `mise exec -- ./gradlew test --console=plain -q 2>&1 | grep -c "^e:"; for f in app/build/test-results/testDebugUnitTest/*.xml; do grep -o 'tests="[0-9]*" skipped="[0-9]*" failures="[0-9]*" errors="[0-9]*"' "$f" | head -1; done | awk -F'"' '{t+=$2; f+=$6; e+=$8} END {print "tests="t" failures="f" errors="e}'`
Expected: `0` compile errors, `tests=58 failures=0 errors=0`.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/scholze/saldo/domain/Modelos.kt app/src/main/kotlin/com/scholze/saldo/data/db/Entities.kt app/src/test/kotlin/com/scholze/saldo/data/db/EntitiesTest.kt
git commit -m "feat: carry criadaEm into the domain Movimentacao"
```

---

### Task 2: Settings — `widgetMostrarValores` and `LembretesConfig`

**Files:**
- Create: `app/src/main/kotlin/com/scholze/saldo/domain/Lembretes.kt`
- Modify: `app/src/main/kotlin/com/scholze/saldo/data/SettingsStore.kt`
- Test: `app/src/androidTest/kotlin/com/scholze/saldo/data/SettingsStoreTest.kt` (modify)

**Interfaces:**
- Produces: `LembretesConfig(faturaAmanha, recorrenciaHoje, registrarGastos, fechamentoMes: Boolean; horaInformativos, horaNudge: LocalTime)` with `algumInformativo`, `algum`; `Settings.widgetMostrarValores: Boolean`, `Settings.lembretes: LembretesConfig`; `SettingsStore.definirWidgetMostrarValores(Boolean)`, `SettingsStore.definirLembretes(LembretesConfig)`.

- [ ] **Step 1: Write the failing tests**

In `app/src/androidTest/kotlin/com/scholze/saldo/data/SettingsStoreTest.kt` add the imports

```kotlin
import com.scholze.saldo.domain.LembretesConfig
import java.time.LocalTime
import org.junit.Assert.assertFalse
```

extend `defaultsSaoSeguros` with two assertions at the end of the test body:

```kotlin
        assertFalse(s.widgetMostrarValores)          // widget mascarado por padrão
        assertEquals(LembretesConfig(), s.lembretes)  // tudo desligado, 09:00 / 20:00
```

and add a new test:

```kotlin
    @Test
    fun persisteWidgetELembretes() = runBlocking {
        val st = store()
        val config = LembretesConfig(
            faturaAmanha = true, recorrenciaHoje = false, registrarGastos = true, fechamentoMes = true,
            horaInformativos = LocalTime.of(8, 30), horaNudge = LocalTime.of(21, 15),
        )
        st.definirWidgetMostrarValores(true)
        st.definirLembretes(config)
        val s = st.settings.first()
        assertTrue(s.widgetMostrarValores)
        assertEquals(config, s.lembretes)
    }
```

- [ ] **Step 2: Run to verify it fails**

Run: `mise exec -- ./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.scholze.saldo.data.SettingsStoreTest --console=plain 2>&1 | grep "^e:" | head`
Expected: `Unresolved reference 'LembretesConfig'` / `'widgetMostrarValores'` / `'definirLembretes'`.

- [ ] **Step 3: Create `LembretesConfig`**

Create `app/src/main/kotlin/com/scholze/saldo/domain/Lembretes.kt`:

```kotlin
package com.scholze.saldo.domain

import java.time.LocalTime

/**
 * Quais lembretes estão ligados e a que horas saem. Tudo desligado por padrão: lembrete é opt-in,
 * e ligar o primeiro é o que pede a permissão de notificação (ver `LembretesScreen`).
 */
data class LembretesConfig(
    val faturaAmanha: Boolean = false,
    val recorrenciaHoje: Boolean = false,
    val registrarGastos: Boolean = false,
    val fechamentoMes: Boolean = false,
    /** Hora dos informativos: fatura vence amanhã, recorrência hoje, fechamento do mês. */
    val horaInformativos: LocalTime = LocalTime.of(9, 0),
    /** Hora do "registrar os gastos de hoje?". */
    val horaNudge: LocalTime = LocalTime.of(20, 0),
) {
    val algumInformativo: Boolean get() = faturaAmanha || recorrenciaHoje || fechamentoMes
    val algum: Boolean get() = algumInformativo || registrarGastos
}
```

- [ ] **Step 4: Persist it in `SettingsStore`**

In `app/src/main/kotlin/com/scholze/saldo/data/SettingsStore.kt`:

Add imports:

```kotlin
import com.scholze.saldo.domain.LembretesConfig
import java.time.LocalTime
```

Replace the `Settings` data class with (new fields last, with defaults — `ExportersTest` constructs it positionally):

```kotlin
data class Settings(
    val saldoInicialCentavos: Long?,
    val saldoInicialData: LocalDate?,
    val cartao: CartaoConfig,
    val comecarOculto: Boolean,
    val tema: Tema,
    /** O widget mostra dinheiro na tela inicial? Padrão `false`: `R$ •••••` até o usuário optar. */
    val widgetMostrarValores: Boolean = false,
    val lembretes: LembretesConfig = LembretesConfig(),
)
```

Extend `Keys`:

```kotlin
    private object Keys {
        val saldoInicial = longPreferencesKey("saldo_inicial_centavos")
        val saldoInicialData = longPreferencesKey("saldo_inicial_epoch_day")
        val cartaoNome = stringPreferencesKey("cartao_nome")
        val cartaoFechamento = intPreferencesKey("cartao_fechamento_dia")
        val cartaoVencimento = intPreferencesKey("cartao_vencimento_dia")
        val comecarOculto = booleanPreferencesKey("comecar_oculto")
        val tema = stringPreferencesKey("tema")
        val widgetMostrarValores = booleanPreferencesKey("widget_mostrar_valores")
        val lembreteFaturaAmanha = booleanPreferencesKey("lembrete_fatura_amanha")
        val lembreteRecorrenciaHoje = booleanPreferencesKey("lembrete_recorrencia_hoje")
        val lembreteRegistrarGastos = booleanPreferencesKey("lembrete_registrar_gastos")
        val lembreteFechamentoMes = booleanPreferencesKey("lembrete_fechamento_mes")
        /** Minutos desde a meia-noite (0..1439). */
        val lembretesHoraInformativos = intPreferencesKey("lembretes_hora_informativos")
        val lembretesHoraNudge = intPreferencesKey("lembretes_hora_nudge")
    }
```

In the `settings` flow's `map`, add to the `Settings(...)` construction (after `tema = …`):

```kotlin
            widgetMostrarValores = p[Keys.widgetMostrarValores] ?: false,
            lembretes = LembretesConfig(
                faturaAmanha = p[Keys.lembreteFaturaAmanha] ?: false,
                recorrenciaHoje = p[Keys.lembreteRecorrenciaHoje] ?: false,
                registrarGastos = p[Keys.lembreteRegistrarGastos] ?: false,
                fechamentoMes = p[Keys.lembreteFechamentoMes] ?: false,
                horaInformativos = p.hora(Keys.lembretesHoraInformativos, LembretesConfig().horaInformativos),
                horaNudge = p.hora(Keys.lembretesHoraNudge, LembretesConfig().horaNudge),
            ),
```

Add the setters after `definirTema`:

```kotlin
    suspend fun definirWidgetMostrarValores(v: Boolean) {
        dataStore.edit { it[Keys.widgetMostrarValores] = v }
    }

    suspend fun definirLembretes(config: LembretesConfig) {
        dataStore.edit {
            it[Keys.lembreteFaturaAmanha] = config.faturaAmanha
            it[Keys.lembreteRecorrenciaHoje] = config.recorrenciaHoje
            it[Keys.lembreteRegistrarGastos] = config.registrarGastos
            it[Keys.lembreteFechamentoMes] = config.fechamentoMes
            it[Keys.lembretesHoraInformativos] = config.horaInformativos.toSecondOfDay() / 60
            it[Keys.lembretesHoraNudge] = config.horaNudge.toSecondOfDay() / 60
        }
    }
```

And a private helper at the bottom of the file (top level, after the class):

```kotlin
/** Minutos do dia gravados → hora; um valor fora de 0..1439 (versão futura, disco corrompido) cai no padrão. */
private fun Preferences.hora(key: Preferences.Key<Int>, padrao: LocalTime): LocalTime =
    this[key]?.takeIf { it in 0..1439 }?.let { LocalTime.ofSecondOfDay(it * 60L) } ?: padrao
```

- [ ] **Step 5: Run the settings tests, then the full suites**

Run: `mise exec -- ./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.scholze.saldo.data.SettingsStoreTest --console=plain 2>&1 | grep -i "tests on\|FAILED\|BUILD"`
Expected: `Finished 3 tests on saldo_test(AVD)` … `BUILD SUCCESSFUL`.

Run: `mise exec -- ./gradlew test --console=plain -q 2>&1 | grep -c "^e:"` → `0` (ExportersTest still compiles: the new fields have defaults).

- [ ] **Step 6: Commit**

```bash
git add app/src/main/kotlin/com/scholze/saldo/domain/Lembretes.kt app/src/main/kotlin/com/scholze/saldo/data/SettingsStore.kt app/src/androidTest/kotlin/com/scholze/saldo/data/SettingsStoreTest.kt
git commit -m "feat: settings for the widget mask and the lembretes config"
```

---

### Task 3: `LembretesEngine` — the reminder rules

**Files:**
- Create: `app/src/main/kotlin/com/scholze/saldo/domain/LembretesEngine.kt`
- Modify: `app/src/main/kotlin/com/scholze/saldo/domain/ProjectionEngine.kt` (add public `faturasAte`)
- Test: `app/src/test/kotlin/com/scholze/saldo/domain/LembretesEngineTest.kt` (create)

**Interfaces:**
- Consumes: `LembretesConfig` (Task 2), `Movimentacao.criadaEm` (Task 1), `ProjectionEngine.mes/totais`, `FaturaCalculator`.
- Produces: `enum class Slot { INFORMATIVOS, NUDGE }`; `sealed interface Lembrete` with `FaturaAmanha(fatura: Fatura, nomeCartao: String)`, `RecorrenciasHoje(dia: LocalDate, itens: List<ItemDia>)`, `RegistrarGastos`, `FechamentoMes(mes: YearMonth, sobrouCentavos: Long, entradasCentavos: Long, saidasCentavos: Long)`; `LembretesEngine.avaliar(input: LedgerInput, config: LembretesConfig, slot: Slot, zona: ZoneId = ZoneId.systemDefault()): List<Lembrete>`; `ProjectionEngine.faturasAte(input: LedgerInput, ateMes: YearMonth): List<Fatura>`.

- [ ] **Step 1: Write the failing tests**

Create `app/src/test/kotlin/com/scholze/saldo/domain/LembretesEngineTest.kt`:

```kotlin
package com.scholze.saldo.domain

import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LembretesEngineTest {
    private val jul = YearMonth.of(2026, 7)
    private val ago = YearMonth.of(2026, 8)
    /** Fecha 28, vence 5 do mês seguinte: uma compra de julho vence em 5 de agosto. */
    private val cartao = CartaoConfig(nome = "nubank", fechamentoDia = 28, vencimentoDia = 5)
    private val zona = ZoneId.of("America/Sao_Paulo")
    private val tudo = LembretesConfig(faturaAmanha = true, recorrenciaHoje = true, registrarGastos = true, fechamentoMes = true)

    private fun mov(dia: String, centavos: Long, natureza: Natureza = Natureza.DIARIO, rec: Long? = null, criadaEm: Long = 0) =
        Movimentacao(
            descricao = "m", valorCentavos = centavos, data = LocalDate.parse(dia), natureza = natureza,
            recorrenciaId = rec, criadaEm = criadaEm,
        )

    private fun input(
        movs: List<Movimentacao> = emptyList(),
        recs: List<Recorrencia> = emptyList(),
        materializados: Set<YearMonth> = setOf(jul),
        hoje: String = "2026-07-20",
    ) = LedgerInput(
        saldoInicialCentavos = 100_000_00,
        saldoInicialData = LocalDate.parse("2026-07-01"),
        movimentacoes = movs,
        recorrencias = recs,
        mesesMaterializados = materializados,
        cartao = cartao,
        hoje = LocalDate.parse(hoje),
    )

    private fun criadaEm(dia: String): Long = LocalDate.parse(dia).atTime(10, 0).atZone(zona).toInstant().toEpochMilli()

    private fun avaliar(input: LedgerInput, slot: Slot, config: LembretesConfig = tudo) =
        LembretesEngine.avaliar(input, config, slot, zona)

    // ---- fatura vence amanhã ----

    @Test
    fun faturaQueVenceAmanhaAvisaComOTotal() {
        val i = input(movs = listOf(mov("2026-07-10", -250_00, Natureza.CARTAO)), materializados = setOf(jul, ago), hoje = "2026-08-04")
        val l = avaliar(i, Slot.INFORMATIVOS).single() as Lembrete.FaturaAmanha
        assertEquals(LocalDate.parse("2026-08-05"), l.fatura.vencimento)
        assertEquals(-250_00L, l.fatura.totalCentavos)
        assertEquals("nubank", l.nomeCartao)
    }

    @Test
    fun faturaSemComprasNaoAvisa() {
        val i = input(materializados = setOf(jul, ago), hoje = "2026-08-04")
        assertEquals(emptyList<Lembrete>(), avaliar(i, Slot.INFORMATIVOS))
    }

    @Test
    fun faturaDoisDiasAntesNaoAvisa() {
        val i = input(movs = listOf(mov("2026-07-10", -250_00, Natureza.CARTAO)), materializados = setOf(jul, ago), hoje = "2026-08-03")
        assertEquals(emptyList<Lembrete>(), avaliar(i, Slot.INFORMATIVOS))
    }

    /** No dia do vencimento a fatura é uma linha fixa do dia — entra em "recorrências hoje", não em "amanhã". */
    @Test
    fun faturaQueVenceHojeEntraNasRecorrenciasDeHoje() {
        val i = input(movs = listOf(mov("2026-07-10", -250_00, Natureza.CARTAO)), materializados = setOf(jul, ago), hoje = "2026-08-05")
        val l = avaliar(i, Slot.INFORMATIVOS).single() as Lembrete.RecorrenciasHoje
        assertEquals(listOf("fatura nubank"), l.itens.map { it.descricao })
        assertEquals(-250_00L, l.itens.single().valorCentavos)
    }

    // ---- recorrência hoje ----

    @Test
    fun recorrenciasDeHojeListamSoAsFixas() {
        val aluguel = Recorrencia(id = 1, descricao = "aluguel", valorCentavos = -2_400_00, natureza = Natureza.DIARIO, diaDoMes = 20, inicio = YearMonth.of(2026, 1))
        // jul não materializado: o template expande virtualmente no dia 20; a avulsa de hoje fica de fora.
        val i = input(movs = listOf(mov("2026-07-20", -30_00)), recs = listOf(aluguel), materializados = emptySet())
        val l = avaliar(i, Slot.INFORMATIVOS).single() as Lembrete.RecorrenciasHoje
        assertEquals(listOf("aluguel"), l.itens.map { it.descricao })
    }

    @Test
    fun semFixasHojeNaoAvisa() {
        assertEquals(emptyList<Lembrete>(), avaliar(input(movs = listOf(mov("2026-07-20", -30_00))), Slot.INFORMATIVOS))
    }

    // ---- fechamento do mês ----

    @Test
    fun fechamentoSoNoDia1ComOsNumerosDoMesAnterior() {
        val movs = listOf(mov("2026-07-10", -3_000_00), mov("2026-07-15", 8_240_00))
        val l = avaliar(input(movs, materializados = setOf(jul, ago), hoje = "2026-08-01"), Slot.INFORMATIVOS).single() as Lembrete.FechamentoMes
        assertEquals(jul, l.mes)
        assertEquals(5_240_00L, l.sobrouCentavos)      // saldoReal(fim jul) − saldoReal(fim jun)
        assertEquals(8_240_00L, l.entradasCentavos)
        assertEquals(3_000_00L, l.saidasCentavos)
    }

    @Test
    fun fechamentoNoDia2NaoAvisa() {
        val movs = listOf(mov("2026-07-10", -3_000_00), mov("2026-07-15", 8_240_00))
        assertEquals(emptyList<Lembrete>(), avaliar(input(movs, materializados = setOf(jul, ago), hoje = "2026-08-02"), Slot.INFORMATIVOS))
    }

    /** Um mês sem nenhuma movimentação não tem o que fechar — nada de "sobrou R$ 0,00". */
    @Test
    fun fechamentoDeMesVazioNaoAvisa() {
        assertEquals(emptyList<Lembrete>(), avaliar(input(materializados = setOf(jul, ago), hoje = "2026-08-01"), Slot.INFORMATIVOS))
    }

    // ---- registrar gastos (nudge) ----

    @Test
    fun nudgeQuandoNadaFoiCriadoHoje() {
        val i = input(movs = listOf(mov("2026-07-20", -30_00, criadaEm = criadaEm("2026-07-19"))))
        assertEquals(listOf(Lembrete.RegistrarGastos), avaliar(i, Slot.NUDGE))
    }

    /** Conta a data de CRIAÇÃO, não a da movimentação: uma despesa de ontem lançada hoje é "lançou hoje". */
    @Test
    fun nudgeCalaQuandoUmAvulsoFoiCriadoHoje() {
        val i = input(movs = listOf(mov("2026-07-19", -30_00, criadaEm = criadaEm("2026-07-20"))))
        assertEquals(emptyList<Lembrete>(), avaliar(i, Slot.NUDGE))
    }

    /** Abrir o mês materializa recorrências com `criadaEm` de hoje — isso não é lançar nada. */
    @Test
    fun nudgeIgnoraRecorrenciaMaterializadaHoje() {
        val i = input(movs = listOf(mov("2026-07-20", -2_400_00, rec = 1L, criadaEm = criadaEm("2026-07-20"))))
        assertEquals(listOf(Lembrete.RegistrarGastos), avaliar(i, Slot.NUDGE))
    }

    // ---- slots e toggles ----

    @Test
    fun slotsNaoSeMisturam() {
        val i = input(movs = listOf(mov("2026-07-10", -250_00, Natureza.CARTAO, criadaEm = criadaEm("2026-07-10"))), materializados = setOf(jul, ago), hoje = "2026-08-04")
        val informativos = avaliar(i, Slot.INFORMATIVOS)
        val nudge = avaliar(i, Slot.NUDGE)
        assertTrue(informativos.single() is Lembrete.FaturaAmanha)
        assertEquals(listOf(Lembrete.RegistrarGastos), nudge)
    }

    @Test
    fun togglesDesligadosCalamTudo() {
        val i = input(movs = listOf(mov("2026-07-10", -250_00, Natureza.CARTAO)), materializados = setOf(jul, ago), hoje = "2026-08-04")
        assertEquals(emptyList<Lembrete>(), avaliar(i, Slot.INFORMATIVOS, LembretesConfig()))
        assertEquals(emptyList<Lembrete>(), avaliar(i, Slot.NUDGE, LembretesConfig()))
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `mise exec -- ./gradlew test --console=plain -q 2>&1 | grep "^e:" | head -3`
Expected: `Unresolved reference 'Slot'`, `'Lembrete'`, `'LembretesEngine'`.

- [ ] **Step 3: Expose `faturasAte` on `ProjectionEngine`**

In `app/src/main/kotlin/com/scholze/saldo/domain/ProjectionEngine.kt`, add right after the public `totais(...)` function (before `// ---- internals ----`):

```kotlin
    /**
     * Todas as faturas cujas compras cabem até o fim de [ateMes] — linhas materializadas e
     * expansões virtuais, como [mes] usa por dentro. É o que o `LembretesEngine` precisa para
     * achar "a fatura que vence amanhã" sem refazer a expansão por conta própria.
     */
    fun faturasAte(input: LedgerInput, ateMes: YearMonth): List<Fatura> =
        FaturaCalculator.faturas(efetivas(input, ateMes), input.cartao)
```

- [ ] **Step 4: Write the engine**

Create `app/src/main/kotlin/com/scholze/saldo/domain/LembretesEngine.kt`:

```kotlin
package com.scholze.saldo.domain

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/** Os dois horários diários em que os lembretes rodam (ver `LembretesConfig.horaInformativos` / `horaNudge`). */
enum class Slot { INFORMATIVOS, NUDGE }

sealed interface Lembrete {
    data class FaturaAmanha(val fatura: Fatura, val nomeCartao: String) : Lembrete
    /** As linhas fixas de [dia] (hoje): recorrências e uma fatura que vença nesse dia. */
    data class RecorrenciasHoje(val dia: LocalDate, val itens: List<ItemDia>) : Lembrete
    data object RegistrarGastos : Lembrete
    data class FechamentoMes(
        val mes: YearMonth,
        val sobrouCentavos: Long,
        val entradasCentavos: Long,
        val saidasCentavos: Long,
    ) : Lembrete
}

/**
 * Decide quais lembretes saem num [Slot], a partir do mesmo `LedgerInput` que o ledger usa.
 * Puro e determinístico: toda regra de lembrete mora aqui e em nenhum outro lugar. Cada regra
 * é gated pelo seu toggle e só emite quando há algo a dizer — sem "sobrou R$ 0,00" nem
 * "hoje: nada".
 */
object LembretesEngine {

    fun avaliar(
        input: LedgerInput,
        config: LembretesConfig,
        slot: Slot,
        zona: ZoneId = ZoneId.systemDefault(),
    ): List<Lembrete> = when (slot) {
        Slot.INFORMATIVOS -> buildList {
            if (config.faturaAmanha) faturaAmanha(input)?.let(::add)
            if (config.recorrenciaHoje) recorrenciasHoje(input)?.let(::add)
            if (config.fechamentoMes) fechamentoMes(input)?.let(::add)
        }
        Slot.NUDGE ->
            if (config.registrarGastos && nadaCriadoHoje(input, zona)) listOf(Lembrete.RegistrarGastos) else emptyList()
    }

    /** A fatura com vencimento em hoje+1, se tiver compras. */
    private fun faturaAmanha(input: LedgerInput): Lembrete.FaturaAmanha? {
        val amanha = input.hoje.plusDays(1)
        val fatura = ProjectionEngine.faturasAte(input, YearMonth.from(amanha))
            .firstOrNull { it.vencimento == amanha } ?: return null
        if (fatura.totalCentavos == 0L) return null
        return Lembrete.FaturaAmanha(fatura, input.cartao.nome)
    }

    /** As linhas fixas do dia de hoje — recorrências e uma fatura que vença hoje. */
    private fun recorrenciasHoje(input: LedgerInput): Lembrete.RecorrenciasHoje? {
        val ledger = ProjectionEngine.mes(input, YearMonth.from(input.hoje), FiltroLedger.TODAS)
        val itens = ledger.dias.getOrNull(input.hoje.dayOfMonth - 1)?.itens.orEmpty().filter { it.recorrente }
        return if (itens.isEmpty()) null else Lembrete.RecorrenciasHoje(input.hoje, itens)
    }

    /** Só no dia 1, sobre o mês que acabou de fechar; um mês sem movimentação nenhuma não avisa. */
    private fun fechamentoMes(input: LedgerInput): Lembrete.FechamentoMes? {
        if (input.hoje.dayOfMonth != 1) return null
        val mes = YearMonth.from(input.hoje).minusMonths(1)
        val t = ProjectionEngine.totais(input, mes)
        val saidas = t.saidasPorNatureza.values.sum()
        if (t.entradasCentavos == 0L && saidas == 0L) return null
        return Lembrete.FechamentoMes(mes, t.sobrouCentavos, t.entradasCentavos, saidas)
    }

    /**
     * "Nada lançado hoje" = nenhuma avulsa (`recorrenciaId == null`) CRIADA hoje. A data da
     * movimentação não serve (ver `Movimentacao.criadaEm`), e instância de recorrência não conta:
     * abrir o mês materializa linhas com `criadaEm` de hoje sem ninguém ter lançado nada.
     */
    private fun nadaCriadoHoje(input: LedgerInput, zona: ZoneId): Boolean =
        input.movimentacoes.none { m ->
            m.recorrenciaId == null && m.criadaEm != 0L &&
                Instant.ofEpochMilli(m.criadaEm).atZone(zona).toLocalDate() == input.hoje
        }
}
```

- [ ] **Step 5: Run the JVM suite**

Run: `mise exec -- ./gradlew test --console=plain -q 2>&1 | grep -c "^e:"; for f in app/build/test-results/testDebugUnitTest/*.xml; do grep -o 'tests="[0-9]*" skipped="[0-9]*" failures="[0-9]*" errors="[0-9]*"' "$f" | head -1; done | awk -F'"' '{t+=$2; f+=$6; e+=$8} END {print "tests="t" failures="f" errors="e}'`
Expected: `0`, `tests=72 failures=0 errors=0` (58 + 14).

- [ ] **Step 6: Commit**

```bash
git add app/src/main/kotlin/com/scholze/saldo/domain/LembretesEngine.kt app/src/main/kotlin/com/scholze/saldo/domain/ProjectionEngine.kt app/src/test/kotlin/com/scholze/saldo/domain/LembretesEngineTest.kt
git commit -m "feat: LembretesEngine — the four reminder rules, pure and tested"
```

---

### Task 4: `Destino` — deep links from outside the app

**Files:**
- Create: `app/src/main/kotlin/com/scholze/saldo/ui/nav/Destino.kt`
- Modify: `app/src/main/kotlin/com/scholze/saldo/MainActivity.kt`
- Modify: `app/src/main/kotlin/com/scholze/saldo/ui/SaldoApp.kt`
- Modify: `app/src/main/kotlin/com/scholze/saldo/ui/ledger/LedgerViewModel.kt`
- Modify: `app/src/main/kotlin/com/scholze/saldo/ui/ledger/LedgerScreen.kt`
- Modify: `app/src/main/kotlin/com/scholze/saldo/ui/totais/TotaisViewModel.kt`
- Modify: `app/src/main/AndroidManifest.xml` (`android:launchMode="singleTop"`)
- Test: `app/src/test/kotlin/com/scholze/saldo/ui/nav/DestinoTest.kt` (create, JVM)
- Test: `app/src/androidTest/kotlin/com/scholze/saldo/DeepLinkTest.kt` (create)

**Interfaces:**
- Produces: `sealed interface Destino { Saldos(mes: YearMonth, dia: Int? = null); NovaMovimentacao; Totais(mes: YearMonth) }` with `paraPares(): List<Pair<String, Any>>`, `aplicarEm(intent: Intent): Intent`, `Destino.de(tipo: String?, anoMes: Int?, dia: Int?): Destino?`, `Destino.deIntent(intent: Intent?): Destino?`, constants `EXTRA_DESTINO/EXTRA_ANO_MES/EXTRA_DIA`, `TIPO_SALDOS/TIPO_NOVA/TIPO_TOTAIS`; `MainActivity.intent(context: Context, destino: Destino): Intent`; `LedgerViewModel.irPara(mes: YearMonth, dia: Int? = null)`, `LedgerViewModel.alvo: StateFlow<AlvoLedger?>`, `LedgerViewModel.limparAlvo()`, `data class AlvoLedger(val mes: YearMonth, val dia: Int)`; `TotaisViewModel.irPara(mes)`. Tasks 5 and 6 build intents/action parameters from `paraPares()`.

- [ ] **Step 1: Write the failing JVM test for the codec**

Create `app/src/test/kotlin/com/scholze/saldo/ui/nav/DestinoTest.kt`:

```kotlin
package com.scholze.saldo.ui.nav

import com.scholze.saldo.data.db.toAnoMes
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** O codec puro por trás dos extras de Intent (e dos ActionParameters do widget). */
class DestinoTest {
    private val ago = YearMonth.of(2026, 8)

    private fun volta(d: Destino): Destino? {
        val p = d.paraPares().toMap()
        return Destino.de(p[Destino.EXTRA_DESTINO] as String?, p[Destino.EXTRA_ANO_MES] as Int?, p[Destino.EXTRA_DIA] as Int?)
    }

    @Test
    fun saldosComDiaVaiEVolta() = assertEquals(Destino.Saldos(ago, 5), volta(Destino.Saldos(ago, 5)))

    @Test
    fun saldosSemDiaNaoEmiteAChaveDia() {
        val d = Destino.Saldos(ago)
        assertEquals(setOf(Destino.EXTRA_DESTINO, Destino.EXTRA_ANO_MES), d.paraPares().map { it.first }.toSet())
        assertEquals(d, volta(d))
    }

    @Test
    fun novaMovimentacaoVaiEVolta() = assertEquals(Destino.NovaMovimentacao, volta(Destino.NovaMovimentacao))

    @Test
    fun totaisVaiEVolta() = assertEquals(Destino.Totais(ago), volta(Destino.Totais(ago)))

    @Test
    fun lixoViraNulo() {
        assertNull(Destino.de(null, null, null))
        assertNull(Destino.de("x", ago.toAnoMes(), null))
        assertNull(Destino.de(Destino.TIPO_SALDOS, null, null))     // saldos sem mês
        assertNull(Destino.de(Destino.TIPO_TOTAIS, -1, null))       // mês negativo
    }

    @Test
    fun diaForaDaFaixaEIgnorado() =
        assertEquals(Destino.Saldos(ago), Destino.de(Destino.TIPO_SALDOS, ago.toAnoMes(), 40))
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `mise exec -- ./gradlew test --console=plain -q 2>&1 | grep "^e:" | head -3`
Expected: `Unresolved reference 'Destino'`.

- [ ] **Step 3: Create `Destino`**

Create `app/src/main/kotlin/com/scholze/saldo/ui/nav/Destino.kt`:

```kotlin
package com.scholze.saldo.ui.nav

import android.content.Intent
import com.scholze.saldo.data.db.toAnoMes
import com.scholze.saldo.data.db.toYearMonth
import java.time.YearMonth

/**
 * Para onde o app abre quando chega por fora — toque no widget ou num lembrete. Viaja como
 * extras de Intent (e como ActionParameters no Glance, que os converte nos mesmos extras): um
 * tipo e até dois inteiros, nada que precise de Parcelable. [de] é o núcleo puro do parse, para
 * ser testado na JVM.
 */
sealed interface Destino {
    /** Aba saldos em [mes]; com [dia] (1..31), o ledger rola até esse dia. */
    data class Saldos(val mes: YearMonth, val dia: Int? = null) : Destino

    /** Aba saldos com a sheet de nova movimentação já aberta. */
    data object NovaMovimentacao : Destino

    /** Aba totais em [mes]. */
    data class Totais(val mes: YearMonth) : Destino

    /** Os extras que representam este destino — [aplicarEm] e o widget usam a mesma lista. */
    fun paraPares(): List<Pair<String, Any>> = when (this) {
        is Saldos -> listOfNotNull(
            EXTRA_DESTINO to TIPO_SALDOS,
            EXTRA_ANO_MES to mes.toAnoMes(),
            dia?.let { EXTRA_DIA to it },
        )
        NovaMovimentacao -> listOf(EXTRA_DESTINO to TIPO_NOVA)
        is Totais -> listOf(EXTRA_DESTINO to TIPO_TOTAIS, EXTRA_ANO_MES to mes.toAnoMes())
    }

    fun aplicarEm(intent: Intent): Intent = intent.apply {
        paraPares().forEach { (chave, valor) ->
            when (valor) {
                is Int -> putExtra(chave, valor)
                else -> putExtra(chave, valor.toString())
            }
        }
    }

    companion object {
        const val EXTRA_DESTINO = "destino"
        const val EXTRA_ANO_MES = "anoMes"
        const val EXTRA_DIA = "dia"
        const val TIPO_SALDOS = "saldos"
        const val TIPO_NOVA = "nova"
        const val TIPO_TOTAIS = "totais"

        /** `null` para tipo desconhecido, mês ausente ou negativo; um dia fora de 1..31 é ignorado. */
        fun de(tipo: String?, anoMes: Int?, dia: Int?): Destino? {
            val mes = anoMes?.takeIf { it >= 0 }?.toYearMonth()
            return when (tipo) {
                TIPO_SALDOS -> mes?.let { Saldos(it, dia?.takeIf { d -> d in 1..31 }) }
                TIPO_NOVA -> NovaMovimentacao
                TIPO_TOTAIS -> mes?.let { Totais(it) }
                else -> null
            }
        }

        fun deIntent(intent: Intent?): Destino? {
            if (intent == null || !intent.hasExtra(EXTRA_DESTINO)) return null
            return de(
                tipo = intent.getStringExtra(EXTRA_DESTINO),
                anoMes = if (intent.hasExtra(EXTRA_ANO_MES)) intent.getIntExtra(EXTRA_ANO_MES, -1) else null,
                dia = if (intent.hasExtra(EXTRA_DIA)) intent.getIntExtra(EXTRA_DIA, 0) else null,
            )
        }
    }
}
```

- [ ] **Step 4: Run the JVM suite (codec green)**

Run: `mise exec -- ./gradlew test --console=plain -q 2>&1 | grep -c "^e:"; for f in app/build/test-results/testDebugUnitTest/*.xml; do grep -o 'tests="[0-9]*" skipped="[0-9]*" failures="[0-9]*" errors="[0-9]*"' "$f" | head -1; done | awk -F'"' '{t+=$2; f+=$6; e+=$8} END {print "tests="t" failures="f" errors="e}'`
Expected: `0`, `tests=78 failures=0 errors=0`.

- [ ] **Step 5: Write the failing instrumented deep-link test**

Create `app/src/androidTest/kotlin/com/scholze/saldo/DeepLinkTest.kt`:

```kotlin
package com.scholze.saldo

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.scholze.saldo.ui.nav.Destino
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Um Intent com [Destino] (widget, lembrete) abre o app já no lugar certo. */
@RunWith(AndroidJUnit4::class)
class DeepLinkTest {

    @get:Rule(order = 0)
    val estadoLimpo = EstadoLimpo()

    @get:Rule(order = 1)
    val rule = createEmptyComposeRule()

    @Test
    fun destinoNovaMovimentacaoAbreASheet() {
        val app = ApplicationProvider.getApplicationContext<SaldoApplication>()
        // Já com onboarding feito: antes dele o destino é ignorado (o teclado toma a tela).
        runBlocking { app.container.settings.definirSaldoInicial(100_000_00, LocalDate.now()) }

        ActivityScenario.launch<MainActivity>(MainActivity.intent(app, Destino.NovaMovimentacao)).use {
            rule.waitUntil(5_000) {
                rule.onAllNodesWithText("nova movimentação").fetchSemanticsNodes().isNotEmpty()
            }
            rule.onNodeWithText("nova movimentação").assertIsDisplayed()
        }
    }
}
```

- [ ] **Step 6: Run to verify it fails**

Run: `mise exec -- ./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.scholze.saldo.DeepLinkTest --console=plain 2>&1 | grep "^e:" | head -3`
Expected: `Unresolved reference 'intent'` (on `MainActivity.intent`).

- [ ] **Step 7: MainActivity — parse the Intent, handle `onNewIntent`, build intents**

Replace the whole `app/src/main/kotlin/com/scholze/saldo/MainActivity.kt` with:

```kotlin
package com.scholze.saldo

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.scholze.saldo.data.Tema
import com.scholze.saldo.ui.SaldoApp
import com.scholze.saldo.ui.nav.Destino
import com.scholze.saldo.ui.privacy.LocalPrivacy
import com.scholze.saldo.ui.privacy.rememberPrivacyState
import com.scholze.saldo.ui.theme.SaldoTheme
import kotlinx.coroutines.flow.MutableStateFlow

/** Os scrims que o `enableEdgeToEdge` sem argumentos usa por baixo dos panos. */
private val LIGHT_SCRIM = Color.argb(0xe6, 0xFF, 0xFF, 0xFF)
private val DARK_SCRIM = Color.argb(0x80, 0x1b, 0x1b, 0x1b)

class MainActivity : ComponentActivity() {

    /**
     * Destino pedido por fora (widget, lembrete). `SaldoApp` o aplica uma vez e devolve como
     * consumido; a activity é `singleTop` no manifest, então com o app aberto o Intent chega em
     * [onNewIntent] em vez de criar uma segunda instância.
     */
    private val destinos = MutableStateFlow<Destino?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Só na criação de verdade: numa recriação (rotação, restauração) o destino do Intent
        // original já foi consumido, e reaplicá-lo abriria a sheet de novo no meio de uma edição.
        if (savedInstanceState == null) destinos.value = Destino.deIntent(intent)
        val container = (application as SaldoApplication).container
        setContent {
            val settings by container.settings.settings.collectAsState(initial = null)
            val s = settings ?: return@setContent
            val destino by destinos.collectAsState()
            val privacidade = rememberPrivacyState(ocultoInicial = s.comecarOculto)
            val escuro = when (s.tema) {
                Tema.SISTEMA -> isSystemInDarkTheme()
                Tema.CLARO -> false
                Tema.ESCURO -> true
            }
            // O `enableEdgeToEdge()` do onCreate decide a cor dos ícones da status bar pelo
            // dark mode DO SISTEMA, uma vez só. Com o tema do app escolhido em "mais", um
            // "escuro" sobre sistema claro deixava relógio e bateria pretos sobre preto —
            // invisíveis. Reaplicar a cada mudança de `escuro` amarra os dois.
            LaunchedEffect(escuro) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { escuro },
                    navigationBarStyle = SystemBarStyle.auto(LIGHT_SCRIM, DARK_SCRIM) { escuro },
                )
            }
            CompositionLocalProvider(LocalPrivacy provides privacidade) {
                SaldoTheme(darkTheme = escuro) {
                    SaldoApp(container, s, destino = destino, onDestinoConsumido = { destinos.value = null })
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        destinos.value = Destino.deIntent(intent)
    }

    companion object {
        /** Intent que abre (ou traz à frente) o app em [destino] — lembretes e testes usam este. */
        fun intent(context: Context, destino: Destino): Intent =
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .let(destino::aplicarEm)
    }
}
```

In `app/src/main/AndroidManifest.xml` add `android:launchMode="singleTop"` to the activity:

```xml
        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:label="@string/app_name"
            android:launchMode="singleTop"
            android:theme="@style/Theme.Saldo">
```

- [ ] **Step 8: LedgerViewModel — public `irPara(mes, dia)` and the scroll target**

In `app/src/main/kotlin/com/scholze/saldo/ui/ledger/LedgerViewModel.kt`:

Add after `LedgerUiState`:

```kotlin
/** Dia para o qual o ledger deve rolar assim que [mes] estiver na tela — pedido por um deep link. */
data class AlvoLedger(val mes: YearMonth, val dia: Int)
```

Inside the class, after `_eventoExclusao`, add:

```kotlin
    private val _alvo = MutableStateFlow<AlvoLedger?>(null)

    /** Consumido pela tela (`limparAlvo`) depois de rolar; separado de [state] para não engordar o combine. */
    val alvo: StateFlow<AlvoLedger?> = _alvo
```

Replace `private fun irPara(mes: YearMonth) { mesAtual.value = mes; abrir(mes) }` with:

```kotlin
    /** Navega para [mes]; com [dia], o ledger rola até ele quando o mês chegar (deep link). */
    fun irPara(mes: YearMonth, dia: Int? = null) {
        mesAtual.value = mes
        _alvo.value = dia?.let { AlvoLedger(mes, it) }
        abrir(mes)
    }

    fun limparAlvo() { _alvo.value = null }
```

(`mesAnterior()` / `proximoMes()` keep calling `irPara(...)` — a chevron tap clears any pending target, which is what we want.)

- [ ] **Step 9: LedgerScreen — scroll to the target once the month is on screen**

In `app/src/main/kotlin/com/scholze/saldo/ui/ledger/LedgerScreen.kt`, add two parameters to `LedgerScreen` (after `onLimparTag`):

```kotlin
    onLimparTag: () -> Unit,
    alvo: AlvoLedger? = null,
    onAlvoConsumido: () -> Unit = {},
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
```

and right after the `mostraPillHoje` declaration add:

```kotlin
    // Chegada por deep link (widget/lembrete) pedindo um dia: rola até ele assim que o mês
    // pedido está na tela — `mes` pode ainda ser o mês anterior por um quadro — e devolve o
    // alvo como consumido. Num mês sem movimentação os dias nem viram itens: só consome.
    LaunchedEffect(alvo, mes) {
        val a = alvo ?: return@LaunchedEffect
        val m = mes ?: return@LaunchedEffect
        if (m.mes != a.mes) return@LaunchedEffect
        if (m.dias.any { it.itens.isNotEmpty() }) listState.scrollToItem(cabecalhos + a.dia - 1)
        onAlvoConsumido()
    }
```

- [ ] **Step 10: TotaisViewModel — public `irPara`**

In `app/src/main/kotlin/com/scholze/saldo/ui/totais/TotaisViewModel.kt` change `private fun irPara(mes: YearMonth) {` to `fun irPara(mes: YearMonth) {`.

- [ ] **Step 11: SaldoApp — consume the destino once**

In `app/src/main/kotlin/com/scholze/saldo/ui/SaldoApp.kt`:

Add imports:

```kotlin
import com.scholze.saldo.ui.nav.Destino
import com.scholze.saldo.ui.nav.SaldoTab
```

(`SaldoTab` is already imported — keep one.) Change the signature to:

```kotlin
@Composable
fun SaldoApp(
    container: AppContainer,
    settings: Settings,
    destino: Destino? = null,
    onDestinoConsumido: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
```

In the onboarding branch, before `AmountKeypadScreen(`, add:

```kotlin
        // Sem saldo inicial não há para onde ir: o destino é descartado, não guardado.
        LaunchedEffect(destino) { if (destino != null) onDestinoConsumido() }
```

Replace the line `val totaisFactory = remember(container) { TotaisViewModel.factory(container) }` with:

```kotlin
    val totaisVm: TotaisViewModel = viewModel(factory = remember(container) { TotaisViewModel.factory(container) })
```

and `SaldoTab.TOTAIS -> TotaisScreen(viewModel(factory = totaisFactory))` with `SaldoTab.TOTAIS -> TotaisScreen(totaisVm)`.

Right after `var sheetAberto by rememberSaveable { mutableStateOf(false) }` add:

```kotlin
    val alvoLedger by ledgerVm.alvo.collectAsState()

    // Deep link (widget, lembrete): aplicado uma vez e devolvido como consumido, para que uma
    // recomposição — ou o mesmo Intent reentregue — não o reaplique.
    LaunchedEffect(destino) {
        when (destino) {
            null -> return@LaunchedEffect
            is Destino.Saldos -> { ledgerVm.irPara(destino.mes, destino.dia); tab = SaldoTab.SALDOS }
            Destino.NovaMovimentacao -> { entryVm.iniciarNova(LocalDate.now()); sheetAberto = true }
            is Destino.Totais -> { totaisVm.irPara(destino.mes); tab = SaldoTab.TOTAIS }
        }
        onDestinoConsumido()
    }
```

and pass the target into the ledger call:

```kotlin
                        onLimparTag = { ledgerVm.definirTagFiltro(null) },
                        alvo = alvoLedger,
                        onAlvoConsumido = ledgerVm::limparAlvo,
                        contentPadding = PaddingValues(bottom = 24.dp),
```

- [ ] **Step 12: Run the deep-link test, then the whole instrumented suite**

Run: `mise exec -- ./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.scholze.saldo.DeepLinkTest --console=plain 2>&1 | grep -i "tests on\|FAILED\|BUILD"`
Expected: `Finished 1 tests on saldo_test(AVD)`, `BUILD SUCCESSFUL`.

Run: `mise run test-device 2>&1 | grep -i "tests on\|FAILED\|BUILD"` (or `mise exec -- ./gradlew connectedDebugAndroidTest`)
Expected: `Finished 45 tests` (43 + settings round trip + deep link), 0 failed.

- [ ] **Step 13: Commit**

```bash
git add app/src/main/kotlin/com/scholze/saldo/ui/nav/Destino.kt app/src/main/kotlin/com/scholze/saldo/MainActivity.kt app/src/main/kotlin/com/scholze/saldo/ui/SaldoApp.kt app/src/main/kotlin/com/scholze/saldo/ui/ledger/LedgerViewModel.kt app/src/main/kotlin/com/scholze/saldo/ui/ledger/LedgerScreen.kt app/src/main/kotlin/com/scholze/saldo/ui/totais/TotaisViewModel.kt app/src/main/AndroidManifest.xml app/src/test/kotlin/com/scholze/saldo/ui/nav/DestinoTest.kt app/src/androidTest/kotlin/com/scholze/saldo/DeepLinkTest.kt
git commit -m "feat: Destino deep links — Intent codec, singleTop activity, ledger/totais navigation"
```

---

### Task 5: The Glance widget

**Files:**
- Modify: `gradle/libs.versions.toml`, `app/build.gradle.kts` (Glance deps)
- Create: `app/src/main/kotlin/com/scholze/saldo/widget/WidgetEstado.kt`
- Create: `app/src/main/kotlin/com/scholze/saldo/widget/SaldoWidgetContent.kt`
- Create: `app/src/main/kotlin/com/scholze/saldo/widget/SaldoWidget.kt`
- Create: `app/src/main/kotlin/com/scholze/saldo/widget/WidgetRefresher.kt`
- Create: `app/src/main/res/xml/saldo_widget_info.xml`
- Modify: `app/src/main/res/values/strings.xml`, `app/src/main/AndroidManifest.xml`, `app/src/main/kotlin/com/scholze/saldo/SaldoApplication.kt`
- Test: `app/src/test/kotlin/com/scholze/saldo/widget/SaldoWidgetContentTest.kt` (create, JVM)

**Interfaces:**
- Consumes: `Destino` + `paraPares()` (Task 4), `Settings.widgetMostrarValores` (Task 2), `ProjectionEngine.mes`.
- Produces: `SaldoWidget : GlanceAppWidget` (with `SaldoWidget.COMPACTO`/`LARGO`), `SaldoWidgetReceiver`, `SaldoWidgetContent(estado: WidgetEstado)`, `WidgetEstado`, `WidgetRefresher`, `AppContainer.scope: CoroutineScope`. Task 6's worker calls `SaldoWidget().updateAll(context)`.

- [ ] **Step 1: Add the Glance dependencies**

In `gradle/libs.versions.toml`, `[versions]` add `glance = "1.1.1"`; `[libraries]` add:

```toml
androidx-glance-appwidget = { group = "androidx.glance", name = "glance-appwidget", version.ref = "glance" }
androidx-glance-testing = { group = "androidx.glance", name = "glance-testing", version.ref = "glance" }
androidx-glance-appwidget-testing = { group = "androidx.glance", name = "glance-appwidget-testing", version.ref = "glance" }
```

In `app/build.gradle.kts` `dependencies {}` add:

```kotlin
    implementation(libs.androidx.glance.appwidget)
    testImplementation(libs.androidx.glance.testing)
    testImplementation(libs.androidx.glance.appwidget.testing)
```

Run: `mise exec -- ./gradlew :app:compileDebugKotlin --console=plain -q 2>&1 | grep -v "^w:" | tail -3`
Expected: no output (Glance 1.1.1 resolves next to Compose BOM 2026.08.00). If resolution fails, use `glance = "1.2.0-rc01"` and note it in the commit.

- [ ] **Step 2: Write the failing widget content tests**

Create `app/src/test/kotlin/com/scholze/saldo/widget/SaldoWidgetContentTest.kt`:

```kotlin
package com.scholze.saldo.widget

import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.testing.unit.assertHasText
import androidx.glance.testing.unit.hasTestTag
import androidx.glance.testing.unit.hasText
import com.scholze.saldo.ui.privacy.MASCARA_PRIVACIDADE
import java.time.LocalDate
import org.junit.Test

/** Roda na JVM: o conteúdo do widget não lê Context, só um [WidgetEstado]. */
class SaldoWidgetContentTest {
    private val pronto = WidgetEstado.Pronto(
        projetadoEm = LocalDate.parse("2026-08-31"),
        saldoProjetadoCentavos = 4_738_72,
        deltaNoMesCentavos = -61_28,
        mostrarValores = false,
    )

    @Test
    fun mascaradoPorPadraoESemDelta() = runGlanceAppWidgetUnitTest {
        setAppWidgetSize(SaldoWidget.LARGO)
        provideComposable { SaldoWidgetContent(pronto) }
        onNode(hasTestTag(TAG_WIDGET_SALDO)).assertHasText(MASCARA_PRIVACIDADE)
        onNode(hasTestTag(TAG_WIDGET_DELTA)).assertDoesNotExist()
    }

    @Test
    fun reveladoMostraLegendaSaldoEDelta() = runGlanceAppWidgetUnitTest {
        setAppWidgetSize(SaldoWidget.LARGO)
        provideComposable { SaldoWidgetContent(pronto.copy(mostrarValores = true)) }
        onNode(hasTestTag(TAG_WIDGET_LEGENDA)).assertHasText("saldo projetado · 31 ago")
        onNode(hasTestTag(TAG_WIDGET_SALDO)).assertHasText("R$ 4.738,72")
        onNode(hasTestTag(TAG_WIDGET_DELTA)).assertHasText("−R$ 61,28 no mês")
    }

    @Test
    fun compactoSoMostraOSaldo() = runGlanceAppWidgetUnitTest {
        setAppWidgetSize(SaldoWidget.COMPACTO)
        provideComposable { SaldoWidgetContent(pronto.copy(mostrarValores = true)) }
        onNode(hasTestTag(TAG_WIDGET_SALDO)).assertHasText("R$ 4.738,72")
        onNode(hasTestTag(TAG_WIDGET_LEGENDA)).assertDoesNotExist()
        onNode(hasTestTag(TAG_WIDGET_DELTA)).assertDoesNotExist()
    }

    @Test
    fun semOnboardingConvidaAComecar() = runGlanceAppWidgetUnitTest {
        setAppWidgetSize(SaldoWidget.LARGO)
        provideComposable { SaldoWidgetContent(WidgetEstado.SemOnboarding) }
        onNode(hasText("toque para começar")).assertExists()
    }
}
```

- [ ] **Step 3: Run to verify it fails**

Run: `mise exec -- ./gradlew test --console=plain -q 2>&1 | grep "^e:" | head -3`
Expected: `Unresolved reference 'WidgetEstado'` / `'SaldoWidget'` / `'SaldoWidgetContent'`.

- [ ] **Step 4: `WidgetEstado`**

Create `app/src/main/kotlin/com/scholze/saldo/widget/WidgetEstado.kt`:

```kotlin
package com.scholze.saldo.widget

import java.time.LocalDate

/** O que o widget mostra — calculado em `SaldoWidget.provideGlance`, desenhado por `SaldoWidgetContent`. */
sealed interface WidgetEstado {
    /** Antes do onboarding não há saldo inicial: o widget só convida a abrir o app. */
    data object SemOnboarding : WidgetEstado

    data class Pronto(
        val projetadoEm: LocalDate,
        val saldoProjetadoCentavos: Long,
        val deltaNoMesCentavos: Long,
        /** `false` = mascarado (`R$ •••••`, sem delta). É o padrão — ver "mostrar valores no widget". */
        val mostrarValores: Boolean,
    ) : WidgetEstado

    /** Qualquer exceção ao carregar: mostra o convite a abrir o app em vez de um número velho. */
    data object Falha : WidgetEstado
}
```

- [ ] **Step 5: `SaldoWidgetContent` (pure Glance UI)**

Create `app/src/main/kotlin/com/scholze/saldo/widget/SaldoWidgetContent.kt`:

```kotlin
package com.scholze.saldo.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.semantics.semantics
import androidx.glance.semantics.testTag
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.scholze.saldo.MainActivity
import com.scholze.saldo.ui.money.centavosAssinadoComSimbolo
import com.scholze.saldo.ui.money.centavosComSimbolo
import com.scholze.saldo.ui.nav.Destino
import com.scholze.saldo.ui.privacy.MASCARA_PRIVACIDADE
import com.scholze.saldo.ui.theme.DarkSaldoColors
import com.scholze.saldo.ui.theme.LightSaldoColors
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

const val TAG_WIDGET_LEGENDA = "widget:legenda"
const val TAG_WIDGET_SALDO = "widget:saldo"
const val TAG_WIDGET_DELTA = "widget:delta"

private val ptBr = Locale.forLanguageTag("pt-BR")
private val diaCurto = DateTimeFormatter.ofPattern("d MMM", ptBr)

/** Cores dia/noite tiradas dos tokens do app — o launcher decide o modo, não o tema escolhido em "mais". */
private object CoresWidget {
    val fundo = ColorProvider(day = LightSaldoColors.surface, night = DarkSaldoColors.surface)
    val label = ColorProvider(day = LightSaldoColors.label, night = DarkSaldoColors.label)
    val secundario = ColorProvider(day = LightSaldoColors.secondaryLabel, night = DarkSaldoColors.secondaryLabel)
    val positivo = ColorProvider(day = LightSaldoColors.positive, night = DarkSaldoColors.positive)
    val negativo = ColorProvider(day = LightSaldoColors.categoryVariable, night = DarkSaldoColors.categoryVariable)
    val tint = ColorProvider(day = LightSaldoColors.tint, night = DarkSaldoColors.tint)
    // Só a fábrica dia/noite é importada (evita o choque de nome com a interface `androidx.glance.unit.ColorProvider`).
    val branco = ColorProvider(day = Color.White, night = Color.White)
}

/**
 * O widget: hero do saldo projetado (mascarado por padrão) e um `+`. Não lê Context nem
 * repositório — recebe tudo em [estado] — para poder ser testado na JVM. Em COMPACTO só cabem
 * o valor e o botão; em LARGO entram a legenda e o delta.
 */
@Composable
fun SaldoWidgetContent(estado: WidgetEstado) {
    val largo = LocalSize.current.width >= SaldoWidget.LARGO.width
    Row(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(CoresWidget.fundo)
            .cornerRadius(16.dp)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(GlanceModifier.defaultWeight().clickable(abrir(Destino.Saldos(YearMonth.now())))) {
            when (estado) {
                WidgetEstado.SemOnboarding -> Text(
                    "toque para começar",
                    style = TextStyle(color = CoresWidget.label, fontSize = 15.sp, fontWeight = FontWeight.Medium),
                )
                WidgetEstado.Falha -> {
                    Text("não foi possível carregar", style = TextStyle(color = CoresWidget.secundario, fontSize = 12.sp))
                    Text("toque para abrir", style = TextStyle(color = CoresWidget.label, fontSize = 15.sp, fontWeight = FontWeight.Medium))
                }
                is WidgetEstado.Pronto -> {
                    if (largo) {
                        Text(
                            "saldo projetado · " + estado.projetadoEm.format(diaCurto).removeSuffix("."),
                            modifier = GlanceModifier.semantics { testTag = TAG_WIDGET_LEGENDA },
                            style = TextStyle(color = CoresWidget.secundario, fontSize = 12.sp),
                            maxLines = 1,
                        )
                    }
                    Text(
                        if (estado.mostrarValores) estado.saldoProjetadoCentavos.centavosComSimbolo() else MASCARA_PRIVACIDADE,
                        modifier = GlanceModifier.semantics { testTag = TAG_WIDGET_SALDO },
                        style = TextStyle(color = CoresWidget.label, fontSize = 22.sp, fontWeight = FontWeight.Bold),
                        maxLines = 1,
                    )
                    if (largo && estado.mostrarValores) {
                        Text(
                            estado.deltaNoMesCentavos.centavosAssinadoComSimbolo() + " no mês",
                            modifier = GlanceModifier.semantics { testTag = TAG_WIDGET_DELTA },
                            style = TextStyle(
                                color = if (estado.deltaNoMesCentavos < 0) CoresWidget.negativo else CoresWidget.positivo,
                                fontSize = 12.sp,
                            ),
                            maxLines = 1,
                        )
                    }
                }
            }
        }
        if (estado != WidgetEstado.SemOnboarding) {
            Box(
                modifier = GlanceModifier
                    .size(36.dp)
                    .background(CoresWidget.tint)
                    .cornerRadius(18.dp)
                    .clickable(abrir(Destino.NovaMovimentacao)),
                contentAlignment = Alignment.Center,
            ) {
                Text("+", style = TextStyle(color = CoresWidget.branco, fontSize = 22.sp, fontWeight = FontWeight.Bold))
            }
        }
    }
}

private fun abrir(destino: Destino): Action = actionStartActivity<MainActivity>(destino.paraParametros())

/** Os mesmos extras de [Destino.aplicarEm], no formato do Glance — que os converte em extras do Intent. */
internal fun Destino.paraParametros(): ActionParameters {
    val pares: List<ActionParameters.Pair<out Any>> = paraPares().map { (chave, valor) ->
        when (valor) {
            is Int -> ActionParameters.Key<Int>(chave) to valor
            else -> ActionParameters.Key<String>(chave) to valor.toString()
        }
    }
    return actionParametersOf(*pares.toTypedArray())
}
```

- [ ] **Step 6: `SaldoWidget` + receiver**

Create `app/src/main/kotlin/com/scholze/saldo/widget/SaldoWidget.kt`:

```kotlin
package com.scholze.saldo.widget

import android.content.Context
import android.util.Log
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import com.scholze.saldo.SaldoApplication
import com.scholze.saldo.domain.FiltroLedger
import com.scholze.saldo.domain.ProjectionEngine
import java.time.YearMonth
import kotlinx.coroutines.flow.first

/**
 * Lê o mesmo repositório do app, projeta o mês corrente e entrega um [WidgetEstado] ao conteúdo.
 * Um snapshot (`first()`), não uma coleta: quem atualiza o widget quando algo muda é o
 * [WidgetRefresher] (processo vivo), o `updatePeriodMillis` do provider (6 h) e o worker dos
 * lembretes (09:00).
 */
class SaldoWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Responsive(setOf(COMPACTO, LARGO))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val estado = try {
            carregar(context)
        } catch (e: Exception) {
            Log.e(TAG, "widget: falha ao carregar", e)
            WidgetEstado.Falha
        }
        provideContent { SaldoWidgetContent(estado) }
    }

    private suspend fun carregar(context: Context): WidgetEstado {
        val container = (context.applicationContext as SaldoApplication).container
        val settings = container.settings.settings.first()
        if (settings.saldoInicialCentavos == null) return WidgetEstado.SemOnboarding
        val input = container.repository.ledger.first()
        val mes = ProjectionEngine.mes(input, YearMonth.from(input.hoje), FiltroLedger.TODAS)
        return WidgetEstado.Pronto(
            projetadoEm = mes.projetadoEm,
            saldoProjetadoCentavos = mes.saldoProjetadoCentavos,
            deltaNoMesCentavos = mes.deltaNoMesCentavos,
            mostrarValores = settings.widgetMostrarValores,
        )
    }

    companion object {
        private const val TAG = "saldo"
        val COMPACTO = DpSize(110.dp, 50.dp)
        val LARGO = DpSize(250.dp, 50.dp)
    }
}

class SaldoWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SaldoWidget()
}
```

- [ ] **Step 7: `WidgetRefresher` and the container scope**

Create `app/src/main/kotlin/com/scholze/saldo/widget/WidgetRefresher.kt`:

```kotlin
package com.scholze.saldo.widget

import android.content.Context
import android.util.Log
import androidx.glance.appwidget.updateAll
import com.scholze.saldo.data.SaldoRepository
import com.scholze.saldo.data.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch

/**
 * Mantém o widget em dia enquanto o processo vive: toda escrita acontece neste processo, então
 * observar o ledger (que já embute a virada do dia, ver `diaAtual`) e as settings (a máscara)
 * cobre tudo que muda o número. `updateAll` sem widget na tela é um no-op barato.
 */
@OptIn(FlowPreview::class)
class WidgetRefresher(
    private val context: Context,
    repository: SaldoRepository,
    settings: SettingsStore,
    scope: CoroutineScope,
) {
    init {
        scope.launch {
            merge(repository.ledger.map { }, settings.settings.map { })
                .debounce(300)
                .collect {
                    try {
                        SaldoWidget().updateAll(context)
                    } catch (e: Exception) {
                        Log.e(TAG, "widget: updateAll falhou", e)
                    }
                }
        }
    }

    private companion object {
        const val TAG = "saldo"
    }
}
```

In `app/src/main/kotlin/com/scholze/saldo/SaldoApplication.kt` replace `AppContainer` with:

```kotlin
/** Manual DI: one graph, built once, handed down from [MainActivity]. */
class AppContainer(context: Context) {
    /** Trabalho de fundo com a vida do processo (refresh do widget); nunca cancelado de propósito. */
    val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val database: SaldoDatabase = SaldoDatabase.build(context)
    val settings: SettingsStore = SettingsStore(context.settingsDataStore)
    val repository: SaldoRepository = RoomSaldoRepository(database, settings)
    val widgetRefresher: WidgetRefresher = WidgetRefresher(context.applicationContext, repository, settings, scope)
}
```

with the imports:

```kotlin
import com.scholze.saldo.widget.WidgetRefresher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
```

- [ ] **Step 8: Provider info, string, manifest**

Create `app/src/main/res/xml/saldo_widget_info.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<!--
    2×1 por padrão (COMPACTO), redimensionável na horizontal até o LARGO. `updatePeriodMillis`
    de 6 h é a rede de segurança para um processo morto; o refresh de verdade vem do
    WidgetRefresher (processo vivo) e do worker dos lembretes.
-->
<appwidget-provider xmlns:android="http://schemas.android.com/apk/res/android"
    android:minWidth="110dp"
    android:minHeight="40dp"
    android:minResizeWidth="110dp"
    android:minResizeHeight="40dp"
    android:maxResizeWidth="400dp"
    android:maxResizeHeight="120dp"
    android:targetCellWidth="2"
    android:targetCellHeight="1"
    android:resizeMode="horizontal|vertical"
    android:updatePeriodMillis="21600000"
    android:widgetCategory="home_screen"
    android:description="@string/widget_descricao"
    android:initialLayout="@layout/glance_default_loading_layout" />
```

In `app/src/main/res/values/strings.xml` add:

```xml
    <string name="widget_descricao">saldo projetado e novo lançamento</string>
```

In `app/src/main/AndroidManifest.xml`, inside `<application>` after the activity, add:

```xml
        <receiver
            android:name=".widget.SaldoWidgetReceiver"
            android:exported="true"
            android:label="saldo">
            <intent-filter>
                <action android:name="android.appwidget.action.APPWIDGET_UPDATE" />
            </intent-filter>
            <meta-data
                android:name="android.appwidget.provider"
                android:resource="@xml/saldo_widget_info" />
        </receiver>
```

- [ ] **Step 9: Run the JVM suite and lint**

Run: `mise exec -- ./gradlew test --console=plain -q 2>&1 | grep -c "^e:"; for f in app/build/test-results/testDebugUnitTest/*.xml; do grep -o 'tests="[0-9]*" skipped="[0-9]*" failures="[0-9]*" errors="[0-9]*"' "$f" | head -1; done | awk -F'"' '{t+=$2; f+=$6; e+=$8} END {print "tests="t" failures="f" errors="e}'`
Expected: `0`, `tests=82 failures=0 errors=0`.

Run: `mise exec -- ./gradlew lintDebug --console=plain -q 2>&1 | grep -v "^w:" | tail -3; grep -o "[0-9]* errors\?, [0-9]* warnings\?" app/build/reports/lint-results-debug.txt | head -1`
Expected: `0 errors, …` (a `targetCellWidth` "unused on API < 31" warning is fine).

- [ ] **Step 10: See it on the emulator**

Run: `mise run install` then, with the emulator on screen (start it without `-no-window` if it is headless), long-press the launcher → widgets → "saldo" → place it. Expected: `R$ •••••` and a blue `+`; tapping `+` opens the app with the sheet; tapping the value opens the ledger. Take `adb exec-out screencap -p > .superpowers/sdd/shots/24-widget-mascarado.png` (path is gitignored, for the final pass).

- [ ] **Step 11: Commit**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts app/src/main/kotlin/com/scholze/saldo/widget app/src/main/res/xml/saldo_widget_info.xml app/src/main/res/values/strings.xml app/src/main/AndroidManifest.xml app/src/main/kotlin/com/scholze/saldo/SaldoApplication.kt app/src/test/kotlin/com/scholze/saldo/widget/SaldoWidgetContentTest.kt
git commit -m "feat: home-screen widget (Glance) — saldo projetado masked by default, quick add"
```

---

### Task 6: Lembretes — notifications, scheduler, worker

**Files:**
- Modify: `gradle/libs.versions.toml`, `app/build.gradle.kts` (WorkManager, work-testing, test rules)
- Create: `app/src/main/res/drawable/ic_notificacao.xml`
- Create: `app/src/main/kotlin/com/scholze/saldo/lembretes/Notificacoes.kt`
- Create: `app/src/main/kotlin/com/scholze/saldo/lembretes/LembretesScheduler.kt`
- Create: `app/src/main/kotlin/com/scholze/saldo/lembretes/LembretesWorker.kt`
- Modify: `app/src/main/AndroidManifest.xml` (permission), `app/src/main/kotlin/com/scholze/saldo/SaldoApplication.kt` (scheduler in the container, channel + `agendar(KEEP)` at start)
- Test: `app/src/test/kotlin/com/scholze/saldo/lembretes/LembretesSchedulerTest.kt` (create, JVM)
- Test: `app/src/androidTest/kotlin/com/scholze/saldo/lembretes/NotificacoesTest.kt` (create)
- Test: `app/src/androidTest/kotlin/com/scholze/saldo/lembretes/LembretesWorkerTest.kt` (create)

**Interfaces:**
- Consumes: `LembretesEngine.avaliar`, `Lembrete`, `Slot`, `LembretesConfig` (Tasks 2–3); `MainActivity.intent(context, destino)`, `Destino` (Task 4); `SaldoWidget().updateAll` (Task 5).
- Produces: `Notificacoes.criarCanal(context)`, `Notificacoes.podeNotificar(context): Boolean`, `Notificacoes.mostrar(context, lembrete)`, `Notificacoes.construir(context, lembrete): Pair<Int, Notification>`; `LembretesScheduler(context).agendar(config, politica = REPLACE, agora = now())`, `.reagendar(slot, config, agora = now())`, `LembretesScheduler.proximaOcorrencia(agora: LocalDateTime, hora: LocalTime): Duration`, `LembretesScheduler.nome(slot)`; `LembretesWorker` (`CHAVE_SLOT = "slot"`); `AppContainer.lembretesScheduler`. Task 7's `MaisViewModel` calls `agendar`.

- [ ] **Step 1: Dependencies, permission, icon**

`gradle/libs.versions.toml` — `[versions]`: `work = "2.11.2"`, `androidxTestRules = "1.7.0"`; `[libraries]`:

```toml
androidx-work-runtime-ktx = { group = "androidx.work", name = "work-runtime-ktx", version.ref = "work" }
androidx-work-testing = { group = "androidx.work", name = "work-testing", version.ref = "work" }
androidx-test-rules = { group = "androidx.test", name = "rules", version.ref = "androidxTestRules" }
```

`app/build.gradle.kts` `dependencies {}`:

```kotlin
    implementation(libs.androidx.work.runtime.ktx)
    androidTestImplementation(libs.androidx.work.testing)
    androidTestImplementation(libs.androidx.test.rules)
```

`app/src/main/AndroidManifest.xml` — before `<application>`:

```xml
    <!-- Único pedido novo: os lembretes são notificações locais. Nada de alarme exato nem boot. -->
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
```

Create `app/src/main/res/drawable/ic_notificacao.xml` (the launcher's wallet, white, as an alpha mask):

```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- Ícone pequeno de notificação: só o alfa conta, o sistema tinge. É a carteira do launcher
     recortada da zona segura (18dp de cada lado do canvas de 108). -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="72"
    android:viewportHeight="72">
    <group android:translateX="-18" android:translateY="-18">
        <path
            android:pathData="M30,38 h48 a6,6 0 0 1 6,6 v20 a6,6 0 0 1 -6,6 h-48 a6,6 0 0 1 -6,-6 v-20 a6,6 0 0 1 6,-6 z"
            android:fillColor="#FFFFFF" />
        <path
            android:pathData="M30,38 v-4 a4,4 0 0 1 4,-4 h34 a4,4 0 0 1 4,4 v4 z"
            android:fillColor="#FFFFFF"
            android:fillAlpha="0.75" />
        <path
            android:pathData="M68,49 h18 a4,4 0 0 1 4,4 v2 a4,4 0 0 1 -4,4 h-18 a5,5 0 0 1 0,-10 z"
            android:fillColor="#FFFFFF" />
    </group>
</vector>
```

- [ ] **Step 2: Write the failing JVM test for the slot math**

Create `app/src/test/kotlin/com/scholze/saldo/lembretes/LembretesSchedulerTest.kt`:

```kotlin
package com.scholze.saldo.lembretes

import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Test

class LembretesSchedulerTest {
    private val nove = LocalTime.of(9, 0)

    @Test
    fun antesDaHoraEHojeMesmo() =
        assertEquals(Duration.ofMinutes(30), LembretesScheduler.proximaOcorrencia(LocalDateTime.parse("2026-07-20T08:30"), nove))

    @Test
    fun depoisDaHoraEAmanha() =
        assertEquals(Duration.ofHours(23), LembretesScheduler.proximaOcorrencia(LocalDateTime.parse("2026-07-20T10:00"), nove))

    /** O worker se reagenda logo depois de rodar: um segundo depois da hora já é amanhã. */
    @Test
    fun umSegundoDepoisDaHoraEAmanha() =
        assertEquals(
            Duration.ofDays(1).minusSeconds(1),
            LembretesScheduler.proximaOcorrencia(LocalDateTime.parse("2026-07-20T09:00:01"), nove),
        )

    @Test
    fun naHoraExataEAgora() =
        assertEquals(Duration.ZERO, LembretesScheduler.proximaOcorrencia(LocalDateTime.parse("2026-07-20T09:00"), nove))
}
```

Run: `mise exec -- ./gradlew test --console=plain -q 2>&1 | grep "^e:" | head -2`
Expected: `Unresolved reference 'LembretesScheduler'`.

- [ ] **Step 3: `Notificacoes`**

Create `app/src/main/kotlin/com/scholze/saldo/lembretes/Notificacoes.kt`:

```kotlin
package com.scholze.saldo.lembretes

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.scholze.saldo.MainActivity
import com.scholze.saldo.R
import com.scholze.saldo.domain.Lembrete
import com.scholze.saldo.ui.money.centavosAssinado
import com.scholze.saldo.ui.money.centavosValor
import com.scholze.saldo.ui.nav.Destino
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * Canal, ids fixos e um construtor por [Lembrete]. Valores sempre aparecem no texto; a versão
 * pública (tela bloqueada segura) leva só o título — é a decisão "sempre, mas privado na tela
 * de bloqueio". Ids fixos por tipo: uma rodada repetida substitui, não duplica.
 */
object Notificacoes {
    const val CANAL = "lembretes"
    const val ID_FATURA = 1001
    const val ID_RECORRENCIAS = 1002
    const val ID_FECHAMENTO = 1003
    const val ID_REGISTRAR = 1004

    private val ptBr = Locale.forLanguageTag("pt-BR")
    private val diaDeMes = DateTimeFormatter.ofPattern("d 'de' MMMM", ptBr)

    fun criarCanal(context: Context) {
        NotificationManagerCompat.from(context).createNotificationChannel(
            NotificationChannelCompat.Builder(CANAL, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                .setName("lembretes")
                .setDescription("fatura, recorrências, registrar gastos e fechamento do mês")
                .build(),
        )
    }

    /** Antes do Android 13 não há permissão de runtime; a partir dele, só com ela concedida. */
    fun podeNotificar(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    fun mostrar(context: Context, lembrete: Lembrete) {
        // Checagem inline (não via podeNotificar) para o lint enxergar a guarda de MissingPermission.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return
        val (id, notificacao) = construir(context, lembrete)
        NotificationManagerCompat.from(context).notify(id, notificacao)
    }

    /** Separado de [mostrar] para os testes lerem título/texto sem depender do que o sistema exibe. */
    fun construir(context: Context, lembrete: Lembrete): Pair<Int, Notification> = when (lembrete) {
        is Lembrete.FaturaAmanha -> {
            val titulo = "fatura do ${lembrete.nomeCartao} vence amanhã"
            ID_FATURA to notificacao(
                context, ID_FATURA,
                titulo = titulo,
                texto = "R$ " + lembrete.fatura.totalCentavos.centavosValor() +
                    " · vence " + lembrete.fatura.vencimento.format(diaDeMes),
                tituloPublico = titulo,
                destino = Destino.Saldos(YearMonth.from(lembrete.fatura.vencimento), lembrete.fatura.vencimento.dayOfMonth),
            )
        }
        is Lembrete.RecorrenciasHoje -> {
            val linhas = lembrete.itens.map { it.descricao + " " + it.valorCentavos.centavosAssinado() }
            ID_RECORRENCIAS to notificacao(
                context, ID_RECORRENCIAS,
                titulo = if (linhas.size == 1) "hoje: ${linhas.single()}" else "hoje: ${linhas.size} movimentações fixas",
                texto = linhas.joinToString(" · "),
                tituloPublico = "movimentações fixas de hoje",
                destino = Destino.Saldos(YearMonth.from(lembrete.dia), lembrete.dia.dayOfMonth),
            )
        }
        Lembrete.RegistrarGastos -> ID_REGISTRAR to notificacao(
            context, ID_REGISTRAR,
            titulo = "registrar os gastos de hoje?",
            texto = "nada anotado hoje — toque para lançar",
            tituloPublico = "registrar os gastos de hoje?",
            destino = Destino.NovaMovimentacao,
        )
        is Lembrete.FechamentoMes -> {
            val nomeMes = lembrete.mes.month.getDisplayName(TextStyle.FULL, ptBr)
            val verbo = if (lembrete.sobrouCentavos >= 0) "sobrou" else "faltou"
            ID_FECHAMENTO to notificacao(
                context, ID_FECHAMENTO,
                titulo = "$nomeMes fechou: $verbo R$ " + lembrete.sobrouCentavos.centavosValor(),
                texto = "entradas R$ " + lembrete.entradasCentavos.centavosValor() +
                    " · saídas R$ " + lembrete.saidasCentavos.centavosValor(),
                tituloPublico = "$nomeMes fechou",
                destino = Destino.Totais(lembrete.mes),
            )
        }
    }

    private fun notificacao(
        context: Context,
        id: Int,
        titulo: String,
        texto: String,
        tituloPublico: String,
        destino: Destino,
    ): Notification {
        val abrir = PendingIntent.getActivity(
            context, id, MainActivity.intent(context, destino),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val publica = NotificationCompat.Builder(context, CANAL)
            .setSmallIcon(R.drawable.ic_notificacao)
            .setContentTitle(tituloPublico)
            .build()
        return NotificationCompat.Builder(context, CANAL)
            .setSmallIcon(R.drawable.ic_notificacao)
            .setContentTitle(titulo)
            .setContentText(texto)
            .setStyle(NotificationCompat.BigTextStyle().bigText(texto))
            .setContentIntent(abrir)
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publica)
            .build()
    }
}
```

- [ ] **Step 4: `LembretesScheduler`**

Create `app/src/main/kotlin/com/scholze/saldo/lembretes/LembretesScheduler.kt`:

```kotlin
package com.scholze.saldo.lembretes

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.scholze.saldo.domain.LembretesConfig
import com.scholze.saldo.domain.Slot
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Dois trabalhos únicos, um por [Slot], cada um agendado para a PRÓXIMA ocorrência da sua hora e
 * reagendado pelo próprio worker ao terminar. Inexato de propósito (WorkManager, sem alarme
 * exato nem receiver de boot — o WorkManager sobrevive ao reboot sozinho): um lembrete alguns
 * minutos atrasado sob Doze é aceitável.
 */
class LembretesScheduler(private val context: Context) {

    /** A cada mudança de toggle/hora ([ExistingWorkPolicy.REPLACE]) e no arranque do app ([ExistingWorkPolicy.KEEP]). */
    fun agendar(
        config: LembretesConfig,
        politica: ExistingWorkPolicy = ExistingWorkPolicy.REPLACE,
        agora: LocalDateTime = LocalDateTime.now(),
    ) {
        agendarSlot(Slot.INFORMATIVOS, config.algumInformativo, config.horaInformativos, politica, agora)
        agendarSlot(Slot.NUDGE, config.registrarGastos, config.horaNudge, politica, agora)
    }

    /** O worker chama ao terminar: a ocorrência de hoje acabou de rodar, então a próxima é amanhã. */
    fun reagendar(slot: Slot, config: LembretesConfig, agora: LocalDateTime = LocalDateTime.now()) {
        when (slot) {
            Slot.INFORMATIVOS -> agendarSlot(slot, config.algumInformativo, config.horaInformativos, ExistingWorkPolicy.REPLACE, agora)
            Slot.NUDGE -> agendarSlot(slot, config.registrarGastos, config.horaNudge, ExistingWorkPolicy.REPLACE, agora)
        }
    }

    private fun agendarSlot(slot: Slot, ligado: Boolean, hora: LocalTime, politica: ExistingWorkPolicy, agora: LocalDateTime) {
        val wm = WorkManager.getInstance(context)
        if (!ligado) {
            wm.cancelUniqueWork(nome(slot))
            return
        }
        val pedido = OneTimeWorkRequestBuilder<LembretesWorker>()
            .setInitialDelay(proximaOcorrencia(agora, hora))
            .setInputData(workDataOf(LembretesWorker.CHAVE_SLOT to slot.name))
            .build()
        wm.enqueueUniqueWork(nome(slot), politica, pedido)
    }

    companion object {
        fun nome(slot: Slot): String = "lembretes-" + slot.name.lowercase()

        /** Quanto falta até a próxima [hora]: hoje, se ainda não passou; senão amanhã. Exatamente na hora conta como agora. */
        fun proximaOcorrencia(agora: LocalDateTime, hora: LocalTime): Duration {
            val hoje = agora.toLocalDate().atTime(hora)
            val proxima = if (hoje.isBefore(agora)) hoje.plusDays(1) else hoje
            return Duration.between(agora, proxima)
        }
    }
}
```

- [ ] **Step 5: `LembretesWorker`**

Create `app/src/main/kotlin/com/scholze/saldo/lembretes/LembretesWorker.kt`:

```kotlin
package com.scholze.saldo.lembretes

import android.content.Context
import android.util.Log
import androidx.glance.appwidget.updateAll
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.scholze.saldo.SaldoApplication
import com.scholze.saldo.domain.LembretesConfig
import com.scholze.saldo.domain.LembretesEngine
import com.scholze.saldo.domain.Slot
import com.scholze.saldo.widget.SaldoWidget
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.first

/**
 * A rodada diária de um [Slot]: lê ledger + settings, pergunta ao [LembretesEngine] o que sai e
 * posta. Termina em `success` mesmo quando algo falha — um lembrete perdido não vale uma
 * tempestade de retries — e se reagenda para amanhã no `finally`, enquanto os toggles do slot
 * continuarem ligados. Se o próprio WorkManager o parou (toggle desligado, `cancelUniqueWork`),
 * não reinsere: `isStopped`.
 */
class LembretesWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val slot = inputData.getString(CHAVE_SLOT)?.let { nome -> Slot.entries.firstOrNull { it.name == nome } }
            ?: return Result.failure()
        val container = (applicationContext as SaldoApplication).container
        val config = try {
            container.settings.settings.first().lembretes
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "lembretes($slot): settings ilegíveis", e)
            LembretesConfig()
        }
        try {
            val input = container.repository.ledger.first()
            LembretesEngine.avaliar(input, config, slot).forEach { Notificacoes.mostrar(applicationContext, it) }
            // De graça: o widget acorda uma vez por dia mesmo com o processo morto o resto do tempo.
            SaldoWidget().updateAll(applicationContext)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "lembretes($slot) falharam", e)
        } finally {
            if (!isStopped) container.lembretesScheduler.reagendar(slot, config)
        }
        return Result.success()
    }

    companion object {
        const val CHAVE_SLOT = "slot"
        private const val TAG = "saldo"
    }
}
```

- [ ] **Step 6: Wire the container and the app start**

In `app/src/main/kotlin/com/scholze/saldo/SaldoApplication.kt` the file becomes:

```kotlin
package com.scholze.saldo

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import androidx.work.ExistingWorkPolicy
import com.scholze.saldo.data.RoomSaldoRepository
import com.scholze.saldo.data.SaldoRepository
import com.scholze.saldo.data.SettingsStore
import com.scholze.saldo.data.db.SaldoDatabase
import com.scholze.saldo.lembretes.LembretesScheduler
import com.scholze.saldo.lembretes.Notificacoes
import com.scholze.saldo.widget.WidgetRefresher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

/** Manual DI: one graph, built once, handed down from [MainActivity]. */
class AppContainer(context: Context) {
    /** Trabalho de fundo com a vida do processo (refresh do widget, agendamento inicial); nunca cancelado de propósito. */
    val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val database: SaldoDatabase = SaldoDatabase.build(context)
    val settings: SettingsStore = SettingsStore(context.settingsDataStore)
    val repository: SaldoRepository = RoomSaldoRepository(database, settings)
    val widgetRefresher: WidgetRefresher = WidgetRefresher(context.applicationContext, repository, settings, scope)
    val lembretesScheduler: LembretesScheduler = LembretesScheduler(context.applicationContext)
}

class SaldoApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        Notificacoes.criarCanal(this)
        // Rede de segurança (KEEP): normalmente os trabalhos já existem — o WorkManager sobrevive
        // ao reboot — mas depois de "limpar dados" ou de um restore eles precisam voltar.
        container.scope.launch {
            container.lembretesScheduler.agendar(container.settings.settings.first().lembretes, ExistingWorkPolicy.KEEP)
        }
    }
}
```

- [ ] **Step 7: JVM green, then the instrumented tests for notifications and the worker**

Run: `mise exec -- ./gradlew test --console=plain -q 2>&1 | grep -c "^e:"; for f in app/build/test-results/testDebugUnitTest/*.xml; do grep -o 'tests="[0-9]*" skipped="[0-9]*" failures="[0-9]*" errors="[0-9]*"' "$f" | head -1; done | awk -F'"' '{t+=$2; f+=$6; e+=$8} END {print "tests="t" failures="f" errors="e}'`
Expected: `0`, `tests=86 failures=0 errors=0`.

Create `app/src/androidTest/kotlin/com/scholze/saldo/lembretes/NotificacoesTest.kt`:

```kotlin
package com.scholze.saldo.lembretes

import android.app.Notification
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.scholze.saldo.domain.Fatura
import com.scholze.saldo.domain.ItemDia
import com.scholze.saldo.domain.Lembrete
import com.scholze.saldo.domain.Movimentacao
import com.scholze.saldo.domain.Natureza
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/** Título, texto e versão pública de cada lembrete — o que o usuário lê. */
@RunWith(AndroidJUnit4::class)
class NotificacoesTest {
    private val ctx = ApplicationProvider.getApplicationContext<Context>()
    private val hoje = LocalDate.parse("2026-08-20")

    private fun mov(descricao: String, centavos: Long) =
        Movimentacao(descricao = descricao, valorCentavos = centavos, data = hoje, natureza = Natureza.DIARIO, recorrenciaId = 1)

    private fun titulo(n: Notification) = n.extras.getString(Notification.EXTRA_TITLE)
    private fun texto(n: Notification) = n.extras.getString(Notification.EXTRA_TEXT)

    @Test
    fun faturaAmanha() {
        val fatura = Fatura(ciclo = YearMonth.of(2026, 8), vencimento = LocalDate.parse("2026-09-05"), totalCentavos = -812_40, compras = emptyList())
        val (id, n) = Notificacoes.construir(ctx, Lembrete.FaturaAmanha(fatura, "nubank"))
        assertEquals(Notificacoes.ID_FATURA, id)
        assertEquals("fatura do nubank vence amanhã", titulo(n))
        assertEquals("R$ 812,40 · vence 5 de setembro", texto(n))
        assertEquals("fatura do nubank vence amanhã", titulo(n.publicVersion))
        assertNull(texto(n.publicVersion))
        assertEquals(Notification.VISIBILITY_PRIVATE, n.visibility)
    }

    @Test
    fun recorrenciasHojeUmItemVaiNoTitulo() {
        val (id, n) = Notificacoes.construir(ctx, Lembrete.RecorrenciasHoje(hoje, listOf(ItemDia.Mov(mov("aluguel", -2_400_00)))))
        assertEquals(Notificacoes.ID_RECORRENCIAS, id)
        assertEquals("hoje: aluguel −2.400,00", titulo(n))
        assertEquals("movimentações fixas de hoje", titulo(n.publicVersion))
    }

    @Test
    fun recorrenciasHojeVariosItensContamNoTituloEListamNoTexto() {
        val fatura = Fatura(ciclo = YearMonth.of(2026, 7), vencimento = hoje, totalCentavos = -812_40, compras = emptyList())
        val itens = listOf(ItemDia.Mov(mov("aluguel", -2_400_00)), ItemDia.Mov(mov("internet", -129_90)), ItemDia.FaturaDia(fatura, "nubank"))
        val (_, n) = Notificacoes.construir(ctx, Lembrete.RecorrenciasHoje(hoje, itens))
        assertEquals("hoje: 3 movimentações fixas", titulo(n))
        assertEquals("aluguel −2.400,00 · internet −129,90 · fatura nubank −812,40", texto(n))
    }

    @Test
    fun registrarGastos() {
        val (id, n) = Notificacoes.construir(ctx, Lembrete.RegistrarGastos)
        assertEquals(Notificacoes.ID_REGISTRAR, id)
        assertEquals("registrar os gastos de hoje?", titulo(n))
        assertEquals("nada anotado hoje — toque para lançar", texto(n))
    }

    @Test
    fun fechamentoSobrouEFaltou() {
        val sobrou = Notificacoes.construir(ctx, Lembrete.FechamentoMes(YearMonth.of(2026, 7), 312_50, 8_240_00, 7_927_50)).second
        assertEquals("julho fechou: sobrou R$ 312,50", titulo(sobrou))
        assertEquals("entradas R$ 8.240,00 · saídas R$ 7.927,50", texto(sobrou))
        assertEquals("julho fechou", titulo(sobrou.publicVersion))
        val faltou = Notificacoes.construir(ctx, Lembrete.FechamentoMes(YearMonth.of(2026, 7), -61_28, 0, 61_28)).second
        assertEquals("julho fechou: faltou R$ 61,28", titulo(faltou))
    }
}
```

Create `app/src/androidTest/kotlin/com/scholze/saldo/lembretes/LembretesWorkerTest.kt`:

```kotlin
package com.scholze.saldo.lembretes

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import androidx.work.ListenableWorker
import androidx.work.WorkManager
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.workDataOf
import com.scholze.saldo.EstadoLimpo
import com.scholze.saldo.SaldoApplication
import com.scholze.saldo.domain.LembretesConfig
import com.scholze.saldo.domain.Movimentacao
import com.scholze.saldo.domain.Natureza
import com.scholze.saldo.domain.RepetirOpcao
import com.scholze.saldo.domain.Slot
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A rodada de verdade, sobre o container do app: o cenário determinístico é o nudge (não depende
 * do dia do mês nem do cartão). As regras de fatura/recorrência/fechamento vivem nos testes JVM
 * do LembretesEngine.
 */
@RunWith(AndroidJUnit4::class)
class LembretesWorkerTest {

    @get:Rule(order = 0)
    val estadoLimpo = EstadoLimpo()

    @get:Rule(order = 1)
    val permissao: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)

    private val app = ApplicationProvider.getApplicationContext<SaldoApplication>()
    private val nm: NotificationManager get() = app.getSystemService(NotificationManager::class.java)

    @Before
    fun preparar() {
        nm.cancelAll()
        Notificacoes.criarCanal(app)
        runBlocking {
            app.container.settings.definirSaldoInicial(100_000_00, LocalDate.now())
            app.container.settings.definirLembretes(LembretesConfig(registrarGastos = true))
        }
    }

    @After
    fun limpar() {
        nm.cancelAll()
        WorkManager.getInstance(app).cancelAllWork()
    }

    private fun rodar(slot: Slot): ListenableWorker.Result = runBlocking {
        TestListenableWorkerBuilder<LembretesWorker>(app, inputData = workDataOf(LembretesWorker.CHAVE_SLOT to slot.name))
            .build()
            .doWork()
    }

    private fun titulos(): List<String?> {
        // O NotificationManager pode levar um instante para listar o que acabou de ser postado.
        repeat(20) {
            val ativos = nm.activeNotifications
            if (ativos.isNotEmpty()) return ativos.map { it.notification.extras.getString(Notification.EXTRA_TITLE) }
            Thread.sleep(100)
        }
        return emptyList()
    }

    @Test
    fun nudgeAvisaQuandoNadaFoiLancadoHoje() {
        assertEquals(ListenableWorker.Result.success(), rodar(Slot.NUDGE))
        assertEquals(listOf("registrar os gastos de hoje?"), titulos())
    }

    @Test
    fun nudgeCalaDepoisDeUmLancamentoHoje() {
        runBlocking {
            app.container.repository.criar(
                Movimentacao(descricao = "café", valorCentavos = -8_50, data = LocalDate.now(), natureza = Natureza.DIARIO),
                RepetirOpcao.Nao,
            )
        }
        assertEquals(ListenableWorker.Result.success(), rodar(Slot.NUDGE))
        assertEquals(emptyList<String?>(), titulos())
    }
}
```

Run: `mise exec -- ./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.package=com.scholze.saldo.lembretes --console=plain 2>&1 | grep -i "tests on\|FAILED\|BUILD"`
Expected: `Finished 7 tests on saldo_test(AVD)`, `BUILD SUCCESSFUL`.

- [ ] **Step 8: Lint**

Run: `mise exec -- ./gradlew lintDebug --console=plain -q 2>&1 | grep -v "^w:" | tail -3; grep -o "[0-9]* errors\?, [0-9]* warnings\?" app/build/reports/lint-results-debug.txt | head -1`
Expected: `0 errors`. If lint reports `MissingPermission` on `NotificationManagerCompat.notify` despite the inline check, annotate `Notificacoes.mostrar` with `@android.annotation.SuppressLint("MissingPermission")` and a comment pointing at the guard.

- [ ] **Step 9: Commit**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts app/src/main/AndroidManifest.xml app/src/main/res/drawable/ic_notificacao.xml app/src/main/kotlin/com/scholze/saldo/lembretes app/src/main/kotlin/com/scholze/saldo/SaldoApplication.kt app/src/test/kotlin/com/scholze/saldo/lembretes app/src/androidTest/kotlin/com/scholze/saldo/lembretes
git commit -m "feat: lembretes — notifications, WorkManager slots, daily worker"
```

---

### Task 7: mais — widget toggle, lembretes screen, permission, scheduling

**Files:**
- Modify: `gradle/libs.versions.toml`, `app/build.gradle.kts` (`lifecycle-runtime-compose`)
- Modify: `app/src/main/kotlin/com/scholze/saldo/ui/mais/MaisViewModel.kt`
- Modify: `app/src/main/kotlin/com/scholze/saldo/ui/mais/MaisScreen.kt`
- Create: `app/src/main/kotlin/com/scholze/saldo/ui/mais/LembretesScreen.kt`
- Test: `app/src/androidTest/kotlin/com/scholze/saldo/ui/mais/LembretesScreenTest.kt` (create)

**Interfaces:**
- Consumes: `Settings.widgetMostrarValores`, `Settings.lembretes`, `SettingsStore.definirWidgetMostrarValores/definirLembretes` (Task 2); `LembretesScheduler.agendar`, `Notificacoes.podeNotificar` (Task 6); `Slot` (Task 3).
- Produces: `MaisViewModel(settingsStore, scheduler)` with `definirWidgetMostrarValores(Boolean)`, `definirLembretes(LembretesConfig)`; `LembretesScreen(config, onDefinir, onVoltar, modifier)`.

- [ ] **Step 1: Write the failing screen test**

Create `app/src/androidTest/kotlin/com/scholze/saldo/ui/mais/LembretesScreenTest.kt`:

```kotlin
package com.scholze.saldo.ui.mais

import android.Manifest
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.isOn
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.scholze.saldo.EstadoLimpo
import com.scholze.saldo.SaldoApplication
import com.scholze.saldo.ui.theme.SaldoTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Ligar um lembrete grava a config (e o switch renderiza a partir do que foi gravado). */
@RunWith(AndroidJUnit4::class)
class LembretesScreenTest {

    @get:Rule(order = 0)
    val estadoLimpo = EstadoLimpo()

    // Com a permissão já concedida a tela grava direto, sem o diálogo do sistema no meio do teste.
    @get:Rule(order = 1)
    val permissao: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)

    @get:Rule(order = 2)
    val rule = createComposeRule()

    @Test
    fun ligarUmLembreteGravaAConfig() {
        val app = ApplicationProvider.getApplicationContext<SaldoApplication>()
        val vm = MaisViewModel(app.container.settings, app.container.lembretesScheduler)
        rule.setContent {
            SaldoTheme {
                val settings by vm.settings.collectAsState()
                settings?.let { s ->
                    LembretesScreen(config = s.lembretes, onDefinir = vm::definirLembretes, onVoltar = {})
                }
            }
        }
        rule.onNodeWithText("fatura vence amanhã").assertIsDisplayed()
        rule.onAllNodes(isToggleable()).onFirst().performClick()   // o primeiro switch é "fatura vence amanhã"
        rule.waitUntil(5_000) { rule.onAllNodes(isOn()).fetchSemanticsNodes().size == 1 }
    }
}
```

Run: `mise exec -- ./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.scholze.saldo.ui.mais.LembretesScreenTest --console=plain 2>&1 | grep "^e:" | head -3`
Expected: `Unresolved reference 'LembretesScreen'` and a constructor mismatch on `MaisViewModel`.

- [ ] **Step 2: Dependency for `LifecycleResumeEffect`**

`gradle/libs.versions.toml` `[libraries]`:

```toml
androidx-lifecycle-runtime-compose = { group = "androidx.lifecycle", name = "lifecycle-runtime-compose", version.ref = "lifecycleRuntimeKtx" }
```

`app/build.gradle.kts`: `implementation(libs.androidx.lifecycle.runtime.compose)`.

- [ ] **Step 3: `MaisViewModel`**

Replace the class header and add the setters/factory in `app/src/main/kotlin/com/scholze/saldo/ui/mais/MaisViewModel.kt`:

```kotlin
class MaisViewModel(
    private val settingsStore: SettingsStore,
    private val scheduler: LembretesScheduler,
) : ViewModel() {
```

after `definirTema` add:

```kotlin
    fun definirWidgetMostrarValores(v: Boolean) =
        escrever("definirWidgetMostrarValores") { settingsStore.definirWidgetMostrarValores(v) }

    /** Grava e (re)agenda: os toggles e as horas só valem quando o WorkManager sabe deles. */
    fun definirLembretes(config: LembretesConfig) = escrever("definirLembretes") {
        settingsStore.definirLembretes(config)
        scheduler.agendar(config)
    }
```

the factory becomes `initializer { MaisViewModel(container.settings, container.lembretesScheduler) }`, and the imports gain:

```kotlin
import com.scholze.saldo.domain.LembretesConfig
import com.scholze.saldo.lembretes.LembretesScheduler
```

- [ ] **Step 4: `LembretesScreen`**

Create `app/src/main/kotlin/com/scholze/saldo/ui/mais/LembretesScreen.kt`:

```kotlin
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
```

- [ ] **Step 5: `MaisScreen` — the toggle and the row**

In `app/src/main/kotlin/com/scholze/saldo/ui/mais/MaisScreen.kt`:

Add imports:

```kotlin
import com.scholze.saldo.domain.LembretesConfig
```

Add a helper above `MaisScreen`:

```kotlin
private fun resumo(l: LembretesConfig): String {
    val n = listOf(l.faturaAmanha, l.recorrenciaHoje, l.registrarGastos, l.fechamentoMes).count { it }
    return when (n) {
        0 -> "desligados"
        1 -> "1 ativo"
        else -> "$n ativos"
    }
}
```

After `var escolhendoTema by remember { mutableStateOf(false) }` add:

```kotlin
    var abrindoLembretes by rememberSaveable { mutableStateOf(false) }

    if (abrindoLembretes) {
        LembretesScreen(
            config = s.lembretes,
            onDefinir = vm::definirLembretes,
            onVoltar = { abrindoLembretes = false },
            modifier = modifier,
        )
        return
    }
```

Replace the privacidade/tema group with:

```kotlin
            InsetGroup {
                InsetRow(
                    label = "começar oculto",
                    trailing = { Switch(checked = s.comecarOculto, onCheckedChange = { vm.definirComecarOculto(it) }) },
                )
                HairlineDivider(startIndent = 16.dp)
                InsetRow(
                    label = "mostrar valores no widget",
                    trailing = { Switch(checked = s.widgetMostrarValores, onCheckedChange = { vm.definirWidgetMostrarValores(it) }) },
                )
                Text(
                    "o widget mostra o saldo projetado na tela inicial; desligado, mostra R$ •••••",
                    Modifier.padding(start = 16.dp, end = 16.dp, bottom = 10.dp),
                    style = SaldoTheme.type.caption, color = colors.secondaryLabel,
                )
                HairlineDivider(startIndent = 16.dp)
                InsetRow(label = "tema", value = rotulo(s.tema), onClick = { escolhendoTema = true })
            }

            InsetGroup {
                InsetRow(label = "lembretes", value = resumo(s.lembretes), onClick = { abrindoLembretes = true })
            }
```

- [ ] **Step 6: Run the screen test, then everything**

Run: `mise exec -- ./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.scholze.saldo.ui.mais.LembretesScreenTest --console=plain 2>&1 | grep -i "tests on\|FAILED\|BUILD"`
Expected: `Finished 1 tests`, `BUILD SUCCESSFUL`.

Run: `mise run test-device 2>&1 | grep -i "tests on\|FAILED\|BUILD"` → `Finished 53 tests` (43 + 1 settings + 1 deep link + 5 notificações + 2 worker + 1 screen), 0 failed.
Run: `mise run test` → JVM 86/86. Run lint → 0 errors.

- [ ] **Step 7: Commit**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts app/src/main/kotlin/com/scholze/saldo/ui/mais app/src/androidTest/kotlin/com/scholze/saldo/ui/mais
git commit -m "feat: mais — widget mask toggle and the lembretes screen with permission flow"
```

---

### Task 8: Emulator pass, docs, spec sync

**Files:**
- Modify: `README.md`, `docs/superpowers/specs/2026-08-16-saldo-widget-lembretes-design.md`
- Screenshots (gitignored): `.superpowers/sdd/shots/24-…png`

- [ ] **Step 1: Manual pass on `saldo_test` (window on)**

Start the emulator with a window (`mise exec -- emulator -avd saldo_test`), `mise run install`, then:
1. Place the widget (long-press launcher → widgets → saldo). Expect `R$ •••••` + `+`. Screenshot `24-widget-mascarado.png`.
2. mais › privacidade › "mostrar valores no widget" on → back to launcher: value + delta visible. Resize the widget to 4×1 (legenda + delta) and back to 2×1 (value only). Screenshot `25-widget-largo.png`, then dark mode (`adb shell cmd uimode night yes`) `26-widget-escuro.png`, then `night no`.
3. Tap `+` → app opens with the sheet; cancel. Tap the value → ledger, current month.
4. mais › lembretes → toggle "registrar gastos" → the system permission dialog → allow → switch stays on. Set "hora do lembrete de registrar" to one minute from now → wait ≤ 2 min → notification "registrar os gastos de hoje?" appears (`adb shell cmd notification list` or the shade). Screenshot `27-notificacao.png`. Lock (`adb shell input keyevent 26`, wake `224`) → the lock screen shows only the title. Tap the notification → the sheet opens.
5. Deny path: revoke (`adb shell pm revoke com.scholze.saldo android.permission.POST_NOTIFICATIONS`), reopen lembretes → the "notificações desativadas no sistema · abrir ajustes" row shows; tap → app notification settings.
6. `adb shell dumpsys jobscheduler | grep -A2 saldo` (or WorkManager's `adb shell am broadcast -a "androidx.work.diagnostics.REQUEST_DIAGNOSTICS" -p com.scholze.saldo` + logcat) shows the two unique works when both slots are on.

Anything off → fix in the task that owns it, re-run its tests, amend that task's commit message style (a follow-up commit is fine).

- [ ] **Step 2: README**

In `README.md`, after the "Os dados ficam 100% no aparelho…" paragraph add:

```markdown
Widget de tela inicial (saldo projetado + novo lançamento) mascarado por padrão — ligue em
`mais → mostrar valores no widget`. Lembretes locais (fatura vence amanhã, recorrência hoje,
registrar gastos, fechamento do mês) em `mais → lembretes`; todos desligados por padrão.
```

- [ ] **Step 3: Spec check**

The spec already carries the three planning-time deviations (singleTop, `RecorrenciasHoje.dia` + empty-month skip, nudge as the worker's instrumented scenario). If the manual pass changed anything else that the spec states (copy, defaults, hours), update `docs/superpowers/specs/2026-08-16-saldo-widget-lembretes-design.md` in the same commit.

- [ ] **Step 4: Final verification and commit**

Run: `mise run test` (JVM 86/86), `mise run test-device` (53/53), `mise exec -- ./gradlew lintDebug` (0 errors).

```bash
git add README.md docs/superpowers/specs/2026-08-16-saldo-widget-lembretes-design.md
git commit -m "docs: README + spec sync for widget and lembretes"
```

Then hand back to the user for `finishing-a-development-branch` (merge `v1.1-pending` → `main`, tag, push).
