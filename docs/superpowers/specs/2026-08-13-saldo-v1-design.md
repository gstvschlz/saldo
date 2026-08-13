# saldo v1 — design

2026-08-13. Approved by Gustavo after interview.

## Overview

saldo is a 100% local expense tracker (pt-BR) whose UI implements the
"Finance app design system" canvas, options **1a** (dense day×saldo ledger),
**1d** (HIG entry sheet), **1g** (centavos decimal keypad), **1k** (4 tabs +
center add). The scaffold with these four screens exists and is verified on an
emulator; this spec turns it into a working app.

Central concept: **saldo projetado** — the balance the user will have at the
end of the month, combining real entries, scheduled future entries, and an
estimate of routine daily spending.

## Decisions (from interview)

| Topic | Decision |
|---|---|
| Scope | Everything: CRUD + persistence, recurrence + projection, totais, tags, mais, privacy, UX polish, export |
| Projection | Scheduled entries **plus** trailing-30-day diário spending estimate |
| Naturezas | Buckets + fatura: economia is a savings bucket; cartão accumulates into a fatura that debits on vencimento day |
| Privacy | Quick hide-values toggle; **starts hidden on every launch**. No FLAG_SECURE, no biometric (future options in mais) |
| UX | Month swipe + today anchor, row swipe actions, haptics + micro-animations, onboarding + empty states |
| Extras | Export CSV/JSON. Widget + reminders approved but **deferred to a follow-up spec** |
| Stack | Room + repository + ViewModels; pure-Kotlin projection engine |

## Out of scope (v1)

- Home-screen widget, notifications/reminders (next spec — they read the same
  Room DB, no design change expected).
- FLAG_SECURE, biometric lock (future toggles in mais).
- Multiple cards, multiple accounts, foreign currency.

## Architecture

```
ui/          Compose screens (existing structure) + ViewModels
domain/      ProjectionEngine + domain models — pure Kotlin, no Android imports
data/        Room (entities, DAOs, database), DataStore settings, SaldoRepository
```

- `SaldoRepository` is the single source of truth; screens observe Kotlin
  Flows and never touch DAOs directly.
- `ProjectionEngine` is deterministic and side-effect free: inputs (entries,
  templates, settings, today) → outputs (day rows, projections, totals).
  All balance math lives here and only here.
- One ViewModel per tab + one for the entry sheet.

## Data model

### Room

`movimentacoes`
| column | type | notes |
|---|---|---|
| id | Long PK | |
| descricao | String | |
| valorCentavos | Long | signed; negative = saída |
| dataEpochDay | Long | LocalDate.toEpochDay() |
| natureza | enum | `DIARIO` \| `ECONOMIA` \| `CARTAO` |
| recorrenciaId | Long? | FK → recorrencias; null = one-off |
| editadaManualmente | Boolean | instance diverged from its template |
| criadaEm | Long | epoch millis |

`recorrencias` (templates)
| column | type | notes |
|---|---|---|
| id | Long PK | |
| descricao, valorCentavos, natureza | | as above |
| diaDoMes | Int | 1–31, clamped to month length |
| inicio | Int | YearMonth as (year*12+month) |
| fim | Int? | inclusive; null = open-ended |
| ativa | Boolean | soft-off switch |

`tags` (id, nome, cor) with join tables `movimentacao_tags` and
`recorrencia_tags`. Template tags copy to spawned instances.

### DataStore (Preferences)

- `saldoInicialCentavos: Long`, `saldoInicialEpochDay: Long`
- `cartaoFechamentoDia: Int`, `cartaoVencimentoDia: Int`, `cartaoNome: String`
- `comecarOculto: Boolean` (default **true**)
- `tema: SISTEMA | CLARO | ESCURO`

## Recurrence semantics

- Templates **materialize** into real `movimentacoes` rows (with
  `recorrenciaId` set) the first time a month is opened in the ledger.
- Months never opened are computed **virtually** by expanding templates in the
  engine; materialized rows override templates for their month.
- Editing an instance marks `editadaManualmente` and asks nothing.
- Editing a template asks **"só este mês / daqui em diante"**:
  - só este mês → edit that instance only (marks it edited).
  - daqui em diante → update template; re-materialize current-and-future
    instances that are *not* `editadaManualmente`.
- Deleting a template asks "só futuras / todas".
- `diaDoMes` 29–31 clamps to the month's last day.

## Cartão / fatura semantics

- CARTAO entries never touch saldo directly.
- Cycle *k* = purchases with `fechamento(k−1) < data ≤ fechamento(k)` where
  `fechamento(k)` = day F of month k (clamped). A purchase exactly on the
  closing day belongs to the cycle closing that day.
- The cycle's fatura is due on day V: in month k if V > F, else month k+1.
- The fatura is **computed, never stored** — a synthetic ledger row on the
  vencimento day whose value is the cycle sum. Tapping it lists the purchases.
- Faturas participate in running saldo and projections on their vencimento day.

## Balance math (ProjectionEngine)

- `saldoReal(d)` = saldoInicial + Σ non-CARTAO entries with data ≤ d
  + Σ faturas with vencimento ≤ d.
- Ledger day rows: running `saldoReal` per day; the segmented filter
  (todas/diários/fixas*) recomputes the running column over the filtered set.
  (*"fixas" = entries spawned from templates + faturas; "diários" = one-off
  DIARIO entries.)
- **Estimativa**: `média = Σ DIARIO saídas in the 30 days ending today ÷ 30`;
  applied to days *after* today.
- **saldo projetado (end of month M)** =
  `saldoReal(today) + Σ scheduled non-CARTAO entries in (today, end(M)] + Σ faturas due in (today, end(M)] − média × count(days in (today, end(M)])`.
  (CARTAO purchases enter only through their fatura; for a future M the
  estimate spans every day from tomorrow through end(M), including the gap
  months.) For past months this reduces to `saldoReal(end(M))` — no estimate,
  no schedule.
- Hero caption discloses the estimate: "inclui estimativa de R$ X em diários".
  The estimate never fabricates day rows — future rows show scheduled entries
  only.
- "no mês" delta = projected end(M) − saldoReal(end(M−1)).
- All money is integer centavos end to end; display formatting via the
  existing `Money.kt` (pt-BR, U+2212 minus, tabular figures).

## Screens

**saldos (ledger).** Real months; `‹ ›` + horizontal swipe; opens anchored to
today, today's row highlighted; "hoje" pill when scrolled away. Row tap →
edit sheet; swipe-left → delete with undo snackbar. Recurrence and fatura rows
get small glyphs. Empty state for months without entries.

**Sheet (1d).** Fully functional: date picker (future dates allowed; M3
DatePickerDialog themed with SaldoTheme — accepted visual divergence for v1),
repetir (`não repete` / `todo mês no dia N`), tag picker with inline create,
amount opens the 1g keypad. Footer "saldo de hoje ficará em" computed live by
the engine. Edit mode pre-fills and offers delete.

**totais.** Month summary: entradas, saídas by natureza, **sobrou dinheiro**
(performance), economia bucket running total (Σ all ECONOMIA entries), fatura
atual accumulating (+ closing date), top tags by monthly spend.

**tags.** CRUD with the design's dot colors; monthly total per tag; tapping a
tag filters the ledger to it.

**mais.** Grouped-inset settings: saldo inicial (keypad), cartão
(fechamento/vencimento), privacidade (começar oculto), tema, exportar dados,
sobre.

**Onboarding.** First launch → "qual seu saldo hoje?" on the 1g keypad →
seeds saldo inicial → ledger.

**Feel.** Sheet slides over dimmed scrim; segmented thumb spring; balance
count-up on reveal; keypad tick haptics (`HapticFeedback`).

## Privacy

- Every money value renders through one `MoneyText` composable; a
  `LocalPrivacy` composition-local masks all of them as `R$ •••••`
  (fixed-width mask — layouts don't shift, magnitudes can't be inferred).
- Toggles: eye button in the ledger nav bar; tapping the hero.
- `comecarOculto` default true: every cold launch starts masked; reveal lasts
  the session. Toggleable in mais.

## Export & backup

- mais → exportar → SAF file picker → **CSV** (data, descricao, valor,
  natureza, tags, recorrente) or **JSON** (versioned full dump: settings,
  tags, recorrencias, movimentacoes).
- `android:allowBackup="false"`; backup/data-extraction rules emptied so the
  DB never reaches Google cloud backup. Trade-off (accepted): lost phone =
  lost data unless exported.

## Error handling & edge cases

- Integer centavos everywhere; no floats.
- Day-31 templates clamp; Feb 29 handled by clamping (LocalDate math).
- Purchase on closing day → current cycle (tested).
- Tag delete detaches from entries after confirmation.
- Entry delete → undo snackbar (soft window before commit).
- DB writes transactional; export failures show plain error + retry.
- Keypad caps input at R$ 99.999.999,99 (existing behavior).

## Testing

- **ProjectionEngine unit tests** (the bulk): running balances across months,
  fatura cycle boundaries incl. closing-day purchase and V < F rollover,
  day-31 clamping, trailing-30 estimate, economia bucket, filtered running
  columns, past/current/future month projections.
- Room DAO instrumented tests; migration test from schema v1.
- Compose tests: add→appears in ledger, filter switch recomputes saldo column,
  privacy masking, onboarding seeds saldo inicial.
- Every implementation phase ends with an emulator screenshot pass
  (`saldo_test` AVD, light + dark).

## Tooling notes / risks

- Room needs KSP; KSP alongside AGP 9.3.1's built-in Kotlin (no
  `kotlin-android` plugin) is recent territory. Plan phase 1 must validate
  KSP applies and Room compiles **before** any feature work. Fallback:
  SQLDelight 2.x behind the same `SaldoRepository` interface — no design
  change.
- New deps: room-runtime/ktx/compiler(ksp), datastore-preferences,
  kotlinx-serialization-json, lifecycle-viewmodel-compose,
  kotlinx-datetime not needed (java.time is fine on minSdk 26).
