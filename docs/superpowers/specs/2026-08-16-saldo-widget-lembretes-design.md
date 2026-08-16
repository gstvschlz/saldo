# saldo — widget + lembretes — design

2026-08-16. Approved by Gustavo after interview (behavior and technical sections
approved separately). First sub-project of the "more features / more insights"
round; the others get their own specs, in this order: **widget + lembretes →
insights → import/restore → Play release pipeline** (import/restore before
publishing is my assumption, to be confirmed when we get there).

## Overview

Two of the features the v1 spec approved and deferred, built on the same Room
DB and the same `ProjectionEngine`, with no design-language change:

- a **home-screen widget** showing the *saldo projetado* hero and a quick-add
  button, masked by default;
- **lembretes**: four local, offline notifications — fatura vence amanhã,
  recorrência hoje, registrar gastos, fechamento do mês — each an opt-in toggle.

The 100 %-local guarantee is untouched: no network, no new storage outside the
app, the DB stays out of cloud backup.

## Decisions (from interview)

| Topic | Decision |
|---|---|
| Widget content | Hero (`saldo projetado · 31 ago`, value, `−R$ 61,28 no mês`) + a `+` that opens the entry sheet. Not the "what's coming" list, not a button-only widget. |
| Widget privacy | New toggle *mais › privacidade › "mostrar valores no widget"*, **default off** → `R$ •••••`, no delta; `+` always works. |
| Reminders | All four: fatura vence amanhã, recorrência hoje, registrar gastos (nudge), fechamento do mês. Toggles under *mais › lembretes*, **default off**. |
| Notification privacy | Amounts **always** shown; `VISIBILITY_PRIVATE` with a public version carrying only the title, so the secure lock screen never shows money. |
| Hours | "hora dos lembretes" (fatura, recorrência, fechamento) default **09:00**; "hora do lembrete de registrar" default **20:00**; both editable. |
| Widget tech | Jetpack Glance (over `RemoteViews`/XML). |
| Scheduling tech | WorkManager, inexact, self-rescheduling daily works (over `AlarmManager` exact alarms / foreground service). No special permissions. |
| Out of scope | Biometric lock / FLAG_SECURE (not chosen this round), tap-to-peek on the widget, insights, import/restore, Play pipeline. |

## Architecture

```
domain/     LembretesEngine (pure Kotlin: which reminders fire), Movimentacao.criadaEm
data/       SettingsStore + Settings: widgetMostrarValores, LembretesConfig
widget/     SaldoWidget (GlanceAppWidget), SaldoWidgetReceiver, WidgetRefresher
lembretes/  LembretesWorker (CoroutineWorker), LembretesScheduler, Notificacoes
ui/nav/     Destino (deep links from widget + notifications)
ui/mais/    privacidade toggle; new LembretesScreen
MainActivity: Intent → Destino; SaldoApp consumes it once
```

- All balance math stays in `ProjectionEngine`; all reminder rules live in
  `LembretesEngine` and nowhere else. Both are pure Kotlin and unit-tested.
- The widget and the worker read through `AppContainer` (`repository`,
  `settings`) obtained from the `Application` — no second data path.
- `SaldoRepository`'s interface does not change.

## Data model

### `Movimentacao.criadaEm`

`Movimentacao` (domain) gains `criadaEm: Long = 0` (epoch millis; `0` =
unknown, only in tests and legacy fixtures). The column already exists in
`MovimentacaoEntity`; `MovimentacaoComTags.toDomain()` maps it, and
`Movimentacao.toEntity()` keeps a non-zero `criadaEm` instead of overwriting it
(so `restaurar` keeps the original creation time; inserts of new rows still stamp
`now`). `updateCampos` never touched it. **No schema bump.**

Why: the nudge must know whether the user *logged anything today* — "dated
today" would be fooled by recurrence rows materialized today and by logging
yesterday's expense today. The honest signal is "a one-off entry
(`recorrenciaId == null`) created today".

### Settings (DataStore keys, all optional with defaults)

| key | type | default | field |
|---|---|---|---|
| `widget_mostrar_valores` | Boolean | `false` | `Settings.widgetMostrarValores` |
| `lembrete_fatura_amanha` | Boolean | `false` | `LembretesConfig.faturaAmanha` |
| `lembrete_recorrencia_hoje` | Boolean | `false` | `LembretesConfig.recorrenciaHoje` |
| `lembrete_registrar_gastos` | Boolean | `false` | `LembretesConfig.registrarGastos` |
| `lembrete_fechamento_mes` | Boolean | `false` | `LembretesConfig.fechamentoMes` |
| `lembretes_hora_informativos` | Int (minutes of day) | `540` (09:00) | `LembretesConfig.horaInformativos: LocalTime` |
| `lembretes_hora_nudge` | Int (minutes of day) | `1200` (20:00) | `LembretesConfig.horaNudge: LocalTime` |

`Settings` gains `widgetMostrarValores` and `lembretes: LembretesConfig`.
`SettingsStore` gains `definirWidgetMostrarValores`, `definirLembretes(config)`.
Unknown/garbage values fall back to defaults exactly like `tema` does today.
`limpar()` resets these too (they are preferences).

## LembretesEngine

```kotlin
enum class Slot { INFORMATIVOS, NUDGE }

sealed interface Lembrete {
    data class FaturaAmanha(val fatura: Fatura, val nomeCartao: String) : Lembrete
    data class RecorrenciasHoje(val dia: LocalDate, val itens: List<ItemDia>) : Lembrete
    data object RegistrarGastos : Lembrete
    data class FechamentoMes(val mes: YearMonth, val sobrouCentavos: Long, val entradas: Long, val saidas: Long) : Lembrete
}

object LembretesEngine {
    fun avaliar(input: LedgerInput, config: LembretesConfig, slot: Slot): List<Lembrete>
}
```

Rules (`hoje = input.hoje`; every rule is gated by its toggle):

- **INFORMATIVOS**
  - `FaturaAmanha`: the fatura whose `vencimento == hoje + 1`, if it exists and
    `totalCentavos != 0`. Faturas come from `ProjectionEngine.faturasAte(input,
    YearMonth.from(hoje + 1))` — a small public helper exposing what `mes()`
    already computes (efetivas → `FaturaCalculator.faturas`).
  - `RecorrenciasHoje`: today's `DiaRow.itens` (from `ProjectionEngine.mes(input,
    YearMonth.from(hoje), TODAS)`) filtered `recorrente == true` — this includes
    a fatura due today. Emitted only when non-empty.
  - `FechamentoMes`: only when `hoje.dayOfMonth == 1`; uses
    `ProjectionEngine.totais(input, hoje.minusMonths(1))`: `sobrouCentavos`
    (= `deltaNoMesCentavos` of that month), `entradasCentavos`, and the sum of
    `saidasPorNatureza`. Skipped when that month had neither entradas nor
    saídas — nothing to close, no "sobrou R$ 0,00".
- **NUDGE**
  - `RegistrarGastos`: when no `Movimentacao` in `input.movimentacoes` with
    `recorrenciaId == null` has `criadaEm` on `hoje` (epoch millis converted
    with the system default zone — the same zone `LocalDate.now()` uses).

Order of the returned list: fatura, recorrências, fechamento — the worker maps
each to a stable notification id (`1001`–`1004`), so a repeated run replaces
rather than duplicates.

## Notifications (`lembretes/Notificacoes.kt`)

One channel `lembretes` ("lembretes", importance DEFAULT). Money strings come
from `ui/money` (they are presentation). Every notification: `setVisibility
(VISIBILITY_PRIVATE)` + `setPublicVersion(title only)`, auto-cancel, tap →
`MainActivity` with a `Destino`.

| Lembrete | title | text | public title | Destino |
|---|---|---|---|---|
| FaturaAmanha | `fatura do nubank vence amanhã` | `R$ 812,40 · vence 5 de setembro` | same as title | `Saldos(mês do vencimento, dia do vencimento)` |
| RecorrenciasHoje | 1 item: `hoje: aluguel −2.400,00`; n items: `hoje: 3 movimentações fixas` | items joined by ` · ` (BigTextStyle) | `movimentações fixas de hoje` | `Saldos(mês de hoje, hoje)` |
| RegistrarGastos | `registrar os gastos de hoje?` | `nada anotado hoje — toque para lançar` | same as title | `NovaMovimentacao` |
| FechamentoMes | `julho fechou: sobrou R$ 312,50` / `faltou R$ 61,28` | `entradas R$ X · saídas R$ Y` | `julho fechou` | `Totais(mes)` |

## Scheduling (`lembretes/LembretesScheduler.kt`, `LembretesWorker.kt`)

- Two unique works: `lembretes-informativos` and `lembretes-nudge`. Each is a
  `OneTimeWorkRequest<LembretesWorker>` with `initialDelay =
  proximaOcorrencia(agora, hora)` and the `Slot` as input data.
- `proximaOcorrencia(agora: LocalDateTime, hora: LocalTime): Duration` — pure:
  today at `hora` if still ahead, else tomorrow at `hora`.
- `agendar(config)`: for each slot, if any toggle of that slot is on →
  `enqueueUniqueWork(name, REPLACE, request)`; else `cancelUniqueWork(name)`.
  Called by `MaisViewModel` whenever a toggle or hour changes.
- App start (`SaldoApplication.onCreate`): `agendar` with `KEEP` (only fills a
  missing work; WorkManager already survives reboots — this is a safety net for
  a cleared app data / first install after restore).
- `LembretesWorker.doWork(slot)`: `ledger.first()` + `settings.first()` →
  `LembretesEngine.avaliar` → post each → `Result.success()`. Any exception is
  logged and still `success` (a missed reminder is not worth a retry storm).
  In `finally`: re-enqueue this slot for the next day **iff** its toggles are
  still on. It also calls `SaldoWidget().updateAll()` (free daily refresh).
- Inexact by design: under Doze a reminder may arrive minutes late.

## Widget (`widget/`)

- `SaldoWidget : GlanceAppWidget`, `SizeMode.Responsive`: **compact**
  (≈110×50 dp: value + `+`) and **wide** (≈250×50 dp: caption, value, delta,
  `+`); taller cells just add vertical padding. Rounded 16 dp surface using the
  app palette via `ColorProvider(light, dark)`; text styles mirror the hero
  (tabular figures where Glance allows).
- Content: `ProjectionEngine.mes(input, YearMonth.now(), TODAS)` →
  `saldoProjetadoCentavos`, `deltaNoMesCentavos`, `projetadoEm`. Masked when
  `widgetMostrarValores == false`: `R$ •••••` and no delta line, layout
  identical (fixed-width mask, like `MoneyText`).
- Not onboarded (`saldoInicialCentavos == null`): "toque para começar" — the
  whole widget opens the app.
- Actions: value/body → `actionStartActivity(MainActivity, Destino.Saldos
  (YearMonth.now()))`; `+` → `Destino.NovaMovimentacao`.
- Refresh: `WidgetRefresher` (in `AppContainer`, app-process scope) collects
  `repository.ledger` and `settings.settings` (debounced ~300 ms) and calls
  `updateAll` — every write happens in this process, so this covers edits;
  `diaAtual` inside `ledger` covers the day change while the process lives;
  `android:updatePeriodMillis="21600000"` (6 h) in the provider info covers a
  dead process; the 09:00 informativos run refreshes too.
- Render failure (any exception in `provideGlance`): "não foi possível carregar
  · toque para abrir".

## Deep links (`ui/nav/Destino.kt`)

```kotlin
sealed interface Destino {
    /** [dia] = anchor the ledger on that day (like the today anchor); null = default anchoring. */
    data class Saldos(val mes: YearMonth, val dia: Int? = null) : Destino
    data object NovaMovimentacao : Destino
    data class Totais(val mes: YearMonth) : Destino
}
```

- Carried in the `Intent` as extras (`destino` name + `anoMes` int + optional
  `dia`). `MainActivity` is `android:launchMode="singleTop"` in the manifest
  (Glance widget actions cannot set Intent flags), and notification
  PendingIntents add `FLAG_ACTIVITY_SINGLE_TOP | FLAG_ACTIVITY_CLEAR_TOP`, so an
  already-open `MainActivity` receives `onNewIntent` instead of being recreated.
- `MainActivity` keeps `destinos: MutableStateFlow<Destino?>` set from
  `onCreate` (cold start) and `onNewIntent` (app already open).
- `SaldoApp(container, settings, destino, onDestinoConsumido)`: a
  `LaunchedEffect(destino)` applies it once — `Saldos` → `ledgerVm.irPara(mes)`
  + tab *saldos* + the ledger anchors on `dia` when given (the existing today-
  anchor mechanism, parameterized); `NovaMovimentacao` → `entryVm.iniciarNova
  (hoje)` + sheet open; `Totais` → `totaisVm.irPara(mes)` + tab *totais* — then
  calls `onDestinoConsumido()`. `LedgerViewModel.irPara` becomes public;
  `TotaisViewModel` gets the equivalent.
- Before onboarding the destino is ignored (the keypad shows) and consumed.

## mais

- *privacidade*: new switch **mostrar valores no widget** under "começar
  oculto", with a one-line caption ("o widget mostra o saldo projetado na tela
  inicial").
- New row **lembretes** → `LembretesScreen`: four switches (fatura vence
  amanhã, recorrência hoje, registrar gastos, fechamento do mês); two time rows
  ("hora dos lembretes" 09:00, "hora do lembrete de registrar" 20:00) opening the
  M3 `TimePickerDialog` themed with `SaldoTheme` (same accepted divergence as
  the date picker); a hint row "notificações desativadas no sistema — abrir
  ajustes" whenever any toggle is on but `POST_NOTIFICATIONS` is not granted.
- Turning the first toggle on (API 33+) launches the `POST_NOTIFICATIONS`
  request; the toggle is persisted only if granted (denied → stays off, hint
  row explains). Toggling anything calls `LembretesScheduler.agendar`.
- `MaisViewModel` receives `LembretesScheduler` from `AppContainer`.

## Manifest, dependencies, build

- `<uses-permission android:name="android.permission.POST_NOTIFICATIONS"/>` —
  the only new permission (no exact alarms, no boot receiver).
- `SaldoWidgetReceiver` (`APPWIDGET_UPDATE`) + `res/xml/saldo_widget_info.xml`
  (min 110×40 dp, `targetCellWidth/Height` 2×1, resizable horizontally,
  `updatePeriodMillis` 6 h, `widgetCategory` home_screen, description
  "saldo projetado e novo lançamento", preview image from a screenshot).
- New deps (versions pinned in the plan from the current catalog):
  `androidx.glance:glance-appwidget`, `androidx.work:work-runtime-ktx`;
  tests: `androidx.work:work-testing`, `androidx.glance:glance-appwidget-testing`.
- WorkManager default initializer (the worker reaches the graph through
  `applicationContext as SaldoApplication`).
- Release build stays minified; Glance and WorkManager ship consumer rules.

## Error handling & edge cases

- Worker: exceptions logged, `success`, always rescheduled (see above).
- Widget: exceptions render the fallback; masked by default so a stale value is
  never a leak.
- Permission revoked later: notifications silently dropped by the system; the
  hint row in lembretes surfaces it next time the screen is opened.
- Month/day boundaries: `FechamentoMes` only on day 1; a nudge on a day when
  the user only restored (undo) an entry counts as logged (restore keeps
  `criadaEm`, so only if that entry was created today).
- Time changes / timezone: the works are re-computed at each run; a manual
  clock change may fire once early/late — accepted.
- Recurrence day-31 clamping and virtual months are already handled by the
  engine; the reminders reuse it.

## Testing

- **JVM** — `LembretesEngineTest`: fatura amanhã (exists / no purchases / not
  tomorrow), recorrências hoje (none / one / several incl. a fatura due today),
  fechamento only on day 1 with the previous month's numbers, nudge (nothing
  today; a materialized recurrence today ≠ logged; a one-off created today =
  logged; a one-off created yesterday but dated today = not logged), every
  toggle off → empty; `proximaOcorrencia` slot math (before / after / exactly at
  the hour); `SettingsStore` defaults + round trip (instrumented, like today);
  `Movimentacao.toEntity()` keeps a non-zero `criadaEm`.
- **Instrumented** — `LembretesWorker` via `TestListenableWorkerBuilder` on the
  app container: the **nudge** scenario (deterministic on any day — nothing
  created today → one notification; a one-off created today → none) with
  `GrantPermissionRule` for `POST_NOTIFICATIONS`; `Notificacoes.construir`
  title/text/public-version per lembrete; Glance unit test
  (`glance-appwidget-testing`, JVM): masked vs revealed vs compact;
  `MainActivity` launched with `Destino.NovaMovimentacao` opens the sheet;
  `LembretesScreen` toggle persists.
- **Emulator pass** — widget compact/wide, light/dark, masked/revealed; one of
  each notification (private + lock-screen public version); lembretes screen.

## Risks / notes

- Glance's own Compose runtime must agree with the Compose BOM in use — verify
  at plan step 1 (`glance-appwidget` + BOM 2026.08.00 compile together).
- Glance layouts are limited (no custom fonts / tabular figures guarantee): the
  widget mirrors the hero's proportions, not its exact typography.
- `updatePeriodMillis` has a 30-min floor and is inexact; the in-process
  refresher is the primary path.
