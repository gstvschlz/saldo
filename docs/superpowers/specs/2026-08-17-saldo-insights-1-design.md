# saldo — insights 1 (views) — design

2026-08-17. Approved by Gustavo after interview (behavior and technical sections
approved separately; chart language and totais layout chosen on visual mockups
kept in `.superpowers/brainstorm/9667-1786958689/content/`, gitignored).
Second sub-project of the "more features / more insights" round. Order agreed:
widget + lembretes (done) → **insights 1 (views, this spec)** → insights 2
(orçamentos por tag + meta de reserva — new data, own spec) → import/restore →
Play release pipeline.

> **Sync do redesign B (2026-08-18).** Este spec foi escrito quando o app vestia HIG.
> A branch `redesign-b` trocou o vocabulário: o *segmented control* virou chips de
> filtro, a *coluna de saldo* do ledger virou uma pill dentro da linha do dia, e os
> três cabeçalhos em caixa alta ("PARA ONDE FOI", "MAIORES GASTOS", "PADRÕES")
> perderam as maiúsculas. **Nenhum número, regra ou cópia de negócio mudou** — só a
> superfície. Onde este documento ainda descrever o controle segmentado ou a coluna,
> leia "chips" e "pill". Ver `docs/superpowers/plans/2026-08-18-saldo-redesign-b.md`.

## Overview

The *totais* tab becomes the insights home, split by filter chips into
**mês | tendência | a caminho**, plus a **recorrências** overview screen. Everything
is a read-only view over data the app already has (no schema change, no new
settings, no new dependency); all numbers come from a new pure-Kotlin
`InsightsEngine` that reuses `ProjectionEngine`. Charts are drawn with Compose
`Canvas` in the app's own language: a segmented 100 % bar for the month's saídas
by tag, and per-month bars with a "sobrou" line for the trend (option C of the
chart mockups, with the trend content kept as entradas/saídas/sobrou).

## Decisions (from interview)

| Topic | Decision |
|---|---|
| Scope split | Views first (this spec). Budgets/goals ("am I on track" with new data) are insights 2. |
| Location | Evolve *totais*; no new tab. Layout **3**: `mês | tendência | a caminho` under the month nav (the nav applies to all three). |
| Chart language | **C**: segmented 100 % bar + list for tags; monthly bars for the trend. |
| "Where the money goes" | By tag (with *sem tag*), biggest expenses (top 5), patterns (weekday, avulsas/dia vs média 30 d). Not by natureza. |
| Trend | 6 months ending at the viewed month: entradas, saídas, sobrou (not per-tag composition). |
| "What's coming" | Until end of the viewed month: total + dated list; **recorrências overview screen**. Not the rolling 30 days, not a fatura card (fatura atual row stays). |
| Poupança (on track without new data) | Yes: reserva acumulada line (6 months) + taxa de poupança (this month vs last). |
| Tag drill-down | Tapping a tag row filters the ledger by that tag (existing behavior); a per-tag trend screen waits for insights 2. |
| Charts tech | Compose `Canvas`, hand-drawn; no library. |
| Numbers | New pure `InsightsEngine` next to `ProjectionEngine`, reusing it. |

## Behavior

Common: hero (performance sobrou/faltou) and the month nav stay as they are; the
chip row sits right under the hero; the selected segment is
`rememberSaveable` UI state (survives rotation, resets on process death to
*mês*). All money renders through `MoneyText` (privacy mask applies to every
number and chart label — masked charts keep their shapes, labels become
`R$ •••••`).

### mês

1. The existing numbers card: entradas, saídas diários / economia, compras no
   cartão, estimativa restante; then reserva acumulada; unchanged.
2. **para onde foi** — caption `para onde foi · saídas R$ X` (X = all saídas of
   the month, non-CARTAO + CARTAO purchases, i.e. what the user spent). The
   segmented bar: top 4 tags by saídas + *outras* (the rest of the tags) +
   *sem tag*; segment color = `Tag.cor` for tags, `SaldoColors.insightOutras`
   (grey) for outras and `insightSemTag` (lighter grey) for sem tag; segments
   thinner than 2 % are still drawn at a 2 % minimum so they remain visible.
   Below, one row per tag with saídas this month sorted desc (dot, name, value,
   delta vs the previous month: `+30%` / `−12%` / `=` (|Δ| < 1 %) / `novo`
   (previous month 0)), then *sem tag* if non-zero. A movimentação with several
   tags counts fully in each of its tags (shares can exceed 100 % in the list;
   the bar is computed on the first-tag attribution so it always sums to 100 %).
   Tap a tag row → `LedgerViewModel.definirTagFiltro(tag)` + tab *saldos*.
   Empty: "nenhuma saída este mês".
3. **maiores gastos** — top 5 saídas (single movimentações, any natureza) of the
   month: day, description, value; tap → `EntryViewModel.iniciarEdicao(mov)`
   (opens the sheet). Virtual occurrences (`id == 0`) don't open (same rule as
   the ledger).
4. **padrões** — two lines:
   - "sábado é o dia mais caro · média R$ 98,10": mean one-off DIARIO saídas per
     weekday over the trailing 12 weeks (84 days ending at `hoje`, respecting
     `saldoInicialData`), divided by the number of that weekday in the window;
     seven mini bars (seg…dom) with the max highlighted. Fewer than 14 days of
     data → "ainda sem padrão".
   - "avulsas por dia: R$ 82,10 este mês · média 30 dias R$ 71,00": this month's
     one-off DIARIO saídas ÷ days elapsed in the month (through `hoje`, or the
     whole month for a past month; a future month shows `—`), next to the same
     trailing-30 média the projection uses (`ProjectionEngine.mediaDiaria`,
     exposed).
5. fatura atual row (existing).

### tendência

- Chart of the 6 months ending at the viewed month (`mes-5 … mes`): per month a
  saídas bar and an entradas bar side by side (saídas `categoryVariable`,
  entradas `balance`), the sobrou/faltou line (`tint`) over them with a dot per
  month, month labels `mar … ago`; the viewed month is emphasized (label bold).
  Bars scale to the max of the six; the line to its own range. Months before
  `saldoInicialData` render as zero. Tap a month → `TotaisViewModel.irPara(mes)`
  and the segment switches to *mês*.
- **poupança** — "reserva acumulada" as a 6-point line (economia bucket at each
  month end, values from `TotaisMes.economiaBucketCentavos`) with the current
  value; "taxa de poupança: 12 % este mês (jul 9 %)" = ECONOMIA saídas of the
  month ÷ entradas of the month, `—` when entradas are 0.

### a caminho

- Header "ainda saem R$ 3.412,90 até 31/ago" — the sum of everything dated after
  `hoje` through the end of the viewed month that touches the running balance:
  non-CARTAO movimentações (materialized or virtual), faturas on their
  vencimento; entradas are listed too and shown separately: "entram R$ 8.240,00"
  when > 0. A past month: "mês encerrado" and no list. The current or a future
  month: dated list (day, ↺ glyph for recorrências, fatura row, description,
  value), tap → `LedgerViewModel.irPara(mes, dia)` + tab *saldos*.
  Empty: "nada agendado até o fim do mês".
- Last row **recorrências · N fixas · R$ X/mês ›** → **RecorrênciasScreen**.

### RecorrênciasScreen (takes over the tab, BackHandler, nav "‹ totais")

- Header: "N fixas · entram R$ A · saem R$ B por mês" (templates active in the
  viewed month: `inicio ≤ mês ≤ fim`).
- List sorted by `diaDoMes`: day, description, value (signed), tags dots;
  tap → the template's occurrence **in the viewed month** opens in the sheet via
  `EntryViewModel.iniciarEdicao`, so *daqui em diante* / *excluir recorrência*
  already work (an edit "daqui em diante" then applies from the viewed month,
  which is what the screen shows). If that occurrence is still virtual
  (`id == 0`) the screen first calls `repo.abrirMes(mes)` (materializes) and
  re-reads before opening.
- Ended templates (`fim` before the viewed month) in a collapsed "encerradas (n)"
  group at the end.

## Architecture

```
domain/InsightsEngine.kt      pure: paraOndeFoi, tendencia, aCaminho, recorrencias
domain/ProjectionEngine.kt    expose mediaDiaria(input) (public, unchanged math)
ui/totais/TotaisViewModel.kt  state gains insights/tendencia/aCaminho
ui/totais/TotaisScreen.kt     chip row + three segment composables
ui/totais/charts/             SegmentedBar, TrendChart, WeekdayBars, ReservaLine (Canvas)
ui/totais/RecorrenciasScreen.kt + RecorrenciasViewModel.kt
ui/theme/Color.kt             insightOutras / insightSemTag tokens (light + dark)
```

- `InsightsEngine` is deterministic and side-effect free, like `ProjectionEngine`,
  and reuses it: `tendencia` calls `ProjectionEngine.totais` per month;
  `aCaminho` reads `ProjectionEngine.mes(input, mes, TODAS).dias`; the breakdown
  works on the month's effective movimentações (`ProjectionEngine.faturasAte`
  is not needed — CARTAO purchases are attributed by their own date, which is
  what "where the money goes" means).
- `TotaisViewModel.state` keeps its single `combine(repo.ledger, mesAtual)`; the
  three new fields are computed there (`flowOn(Default)`, `.catch`).
- The segment is UI state in `TotaisScreen` (`rememberSaveable`), and the
  screen exposes `onVerTag`, `onAbrirMovimentacao`, `onIrParaDia`,
  `onAbrirRecorrencias` callbacks that `SaldoApp` wires to the ledger/entry
  ViewModels (all existing calls).
- `RecorrenciasViewModel(repo)`: `state = repo.ledger.map { InsightsEngine.recorrencias(it, mes) }`
  + `abrirProxima(rec, onPronta: (Movimentacao) -> Unit)` that materializes when
  needed.

## Data model

None. No schema change, no settings, no dependencies.

### Types (domain)

```kotlin
data class FatiaTag(val tag: Tag?, val centavos: Long, val share: Float, val deltaPercent: Int?)   // tag == null → sem tag
data class Padroes(val porDiaDaSemana: Map<DayOfWeek, Long>, val diaMaisCaro: DayOfWeek?, val avulsasPorDiaMes: Long?, val mediaDiaria30: Long)   // avulsasPorDiaMes null = future month
data class ParaOndeFoi(val saidasCentavos: Long, val fatias: List<FatiaTag>, val barra: List<FatiaTag>, val maioresGastos: List<Movimentacao>, val padroes: Padroes)
data class PontoMes(val mes: YearMonth, val entradas: Long, val saidas: Long, val sobrou: Long, val reservaAcumulada: Long, val taxaPoupanca: Int?)
data class ItemFuturo(val data: LocalDate, val item: ItemDia)
data class ACaminho(val saemCentavos: Long, val entramCentavos: Long, val itens: List<ItemFuturo>, val mesEncerrado: Boolean)
data class ResumoRecorrencias(val ativas: List<Recorrencia>, val encerradas: List<Recorrencia>, val entramMes: Long, val saemMes: Long)
```

`fatias` = every tag + sem tag (for the list); `barra` = top 4 + outras + sem tag
(for the bar; first-tag attribution). `sobrou` = `TotaisMes.sobrouCentavos`.

## Error handling & edge cases

- All math in integer centavos; shares as `Float` for drawing only.
- Delta % base = previous month's value; base 0 → `deltaPercent = null` ("novo").
- Trend months before `saldoInicialData` → zeros; the chart never crashes on
  all-zero data (max = 1 guard).
- Weekday averages: fewer than 14 days between `max(saldoInicialData,
  hoje-83)` and `hoje` → `diaMaisCaro = null` ("ainda sem padrão").
- a caminho excludes today (`data > hoje`) and includes the last day of the
  month; a month fully in the past → `mesEncerrado = true`, empty list.
- Recorrências: `entramMes`/`saemMes` sum only templates active in the viewed
  month; ended templates never count.
- Charts respect the privacy mask (labels masked, shapes kept).

## Testing

- **JVM** — `InsightsEngineTest`: breakdown shares sum to 100 % on the bar with
  first-tag attribution; a two-tag movimentação counts in both list rows; sem tag
  present/absent; deltas (+, −, =, novo); top-5 ordering and cap; weekday
  averages (window, denominator, "ainda sem padrão" under 14 days); avulsas/dia
  for a current vs past month; trend: 6 points, sobrou equals `deltaNoMes`,
  reserva accumulates, taxa with and without entradas, months before
  saldoInicialData are zero; a caminho: today excluded, month end included,
  fatura on vencimento, entradas separated, past month encerrado; recorrências:
  active vs ended, monthly totals. Chart layout math (bar heights, line points,
  segment widths with the 2 % floor) as pure functions with unit tests.
- **Instrumented** — `TotaisContentTest` extended: the three segments render
  their sections from a fabricated state, the tag row callback fires, the
  a-caminho row callback fires; `RecorrenciasScreen` lists templates and the
  tap opens the sheet (through the real container). Emulator pass: the three
  segments and the recorrências screen, light + dark, masked + revealed.

## Risks / notes

- `tendencia` runs `ProjectionEngine.totais` six times per emission; each is a
  full expansion — fine at today's data sizes (hundreds of rows), and it runs on
  `Dispatchers.Default`. If it ever shows, memoize per (input, mês) inside the
  engine call.
- Multi-tag movimentações make the list's shares non-additive by design; the bar
  uses first-tag attribution — the caption says "por tag" not "por categoria"
  and the spec accepts the difference.
