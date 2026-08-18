# saldo — redesign B (material 3 expressive) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move saldo off its transcribed Apple-HIG dress onto Material 3 Expressive with a pinned green seed — retokenizing `ui/theme/`, re-skinning the shared components in place, migrating every screen, and authoring the two still-unbuilt insights-1 screens (*a caminho*, *recorrências*) natively in the new vocabulary.

**Architecture:** Three layers, in order. (1) `Color.kt` / `Type.kt` / `Theme.kt` are retokenized while every call site still compiles — the app renders half-migrated for a few commits and that is expected. (2) The shared components in `ui/components/Components.kt` are re-skinned **under their existing names**, so `MaisScreen`, `TagsScreen` and the totais value lists migrate without touching their call sites; `HairlineDivider` and `SegmentedControl` are the only two that die, replaced by spacing and by filter chips. (3) Screens migrate one per task, each ending green.

**Tech Stack:** Kotlin 2.2, Compose (BOM 2026.08.00), Material 3, Room/DataStore untouched, JUnit4. **No new dependencies** — in particular no `ui-text-google-fonts`.

## Global Constraints

- **Design source of truth:** the canvas at <https://claude.ai/code/artifact/dd3fa10a-fc3d-4ca5-a727-286051509947>, page **"B · sistema"**. Every hex and type value in this plan is copied from it. When this plan and the canvas disagree, this plan wins (it carries the platform-sans decision the canvas does not).
- **Typeface:** `FontFamily.SansSerif` throughout — the platform sans, which on Android is Roboto, M3's own face. The canvas renders Manrope; that is a mockup convenience. Do **not** add a font dependency or bundle TTFs in this plan.
- **Pinned seed, no dynamic color.** No `dynamicLightColorScheme` / `dynamicDarkColorScheme`, no wallpaper extraction, no opt-in setting.
- **Preserve every `testTag` and every visible string**, with exactly one exception: the three tracked all-caps section headers lose their caps (`"PARA ONDE FOI"` → `"para onde foi"`, `"MAIORES GASTOS"` → `"maiores gastos"`, `"PADRÕES"` → `"padrões"`) and so do the ledger column heads, which disappear entirely with the column grid. Every other pt-BR string is byte-for-byte unchanged, U+2212 minus included.
- **Money still renders only through `MoneyText`** (`ui/privacy/Privacy.kt`) so the privacy mask keeps applying, and every money style keeps `.tabular`.
- No schema change, no new settings, 100 % local unchanged.
- **Every task ends green:** `mise run test` (139 today), `mise run test-device` on a connected device/emulator (66 today), `mise exec -- ./gradlew lintDebug` 0 errors. Commit per task with the message given, plus the usual Claude trailer lines.
- **Baseline:** branch `redesign-b` created from `insights-1` at `122f7eb`. `insights-1` is 13 commits ahead of `main` and unmerged; its tasks 1–5 are complete and its tasks 6–8 are superseded by Tasks 6, 7 and 9 of *this* plan. Do not run the old `i1-task-6/7/8` briefs.
- This repo has `core.autocrlf=true`. The working tree legitimately holds CRLF while git stores LF. Do **not** rewrite files for line endings.

## File structure

| File | Responsibility |
|---|---|
| `app/src/main/kotlin/com/scholze/saldo/ui/theme/Color.kt` (modify) | retokenized `SaldoColors`; 3 new fields, 3 retired |
| `app/src/main/kotlin/com/scholze/saldo/ui/theme/Type.kt` (modify) | M3 ramp on the platform sans |
| `app/src/main/kotlin/com/scholze/saldo/ui/theme/Theme.kt` (modify) | real M3 role mapping; the defensive pinning is deleted |
| `app/src/main/kotlin/com/scholze/saldo/ui/components/Components.kt` (modify) | `InsetGroup`/`InsetRow`/`FilledActionButton` re-skinned; `HairlineDivider`/`SegmentedControl` deleted in Task 8 |
| `app/src/main/kotlin/com/scholze/saldo/ui/components/M3.kt` (create) | `FiltroChips`, `SaldoPill`, `DiaBadge`, `SaldoTopBar`, `LinhaDia` |
| `app/src/main/kotlin/com/scholze/saldo/ui/nav/SaldoTabBar.kt` (modify) | M3 navigation bar + docked FAB |
| `app/src/main/kotlin/com/scholze/saldo/ui/ledger/LedgerScreen.kt` (modify) | list items replace the column grid |
| `app/src/main/kotlin/com/scholze/saldo/ui/totais/TotaisScreen.kt` (modify) | chips replace the segmented control |
| `app/src/main/kotlin/com/scholze/saldo/ui/totais/SegmentoMes.kt` (modify) | lowercase section headers, tonal cards |
| `app/src/main/kotlin/com/scholze/saldo/ui/totais/SegmentoTendencia.kt` (modify) | tonal cards |
| `app/src/main/kotlin/com/scholze/saldo/ui/totais/SegmentoACaminho.kt` (create) | Task 6 — the *a caminho* segment, authored in M3 |
| `app/src/main/kotlin/com/scholze/saldo/ui/totais/RecorrenciasScreen.kt` (create) | Task 7 |
| `app/src/main/kotlin/com/scholze/saldo/ui/totais/RecorrenciasViewModel.kt` (create) | Task 7 |
| `app/src/main/kotlin/com/scholze/saldo/ui/tags/TagsScreen.kt` (modify) | drop `HairlineDivider` calls |
| `app/src/main/kotlin/com/scholze/saldo/ui/mais/MaisScreen.kt` (modify) | drop `HairlineDivider` calls |
| `app/src/main/kotlin/com/scholze/saldo/ui/entry/NewEntrySheet.kt` (modify) | M3 sheet shape |
| `app/src/main/kotlin/com/scholze/saldo/ui/entry/AmountKeypadScreen.kt` (modify) | M3 keypad |
| `app/src/main/kotlin/com/scholze/saldo/widget/SaldoWidgetContent.kt` (no change) | reads `Light/DarkSaldoColors` directly — retokenizing carries it |
| tests | `ui/theme/SaldoColorsTest.kt` (JVM, create), `ui/components/M3Test.kt` (instrumented, create), plus edits to `LedgerDayGridTest`, `TotaisContentTest`, `LembretesScreenTest` |

---

### Task 1: Retokenize the theme

Everything still compiles after this task. The app will look half-migrated — HIG layout wearing M3 colors — and that is the intended intermediate state.

**Files:**
- Modify: `app/src/main/kotlin/com/scholze/saldo/ui/theme/Color.kt` (whole file)
- Modify: `app/src/main/kotlin/com/scholze/saldo/ui/theme/Type.kt:36-88` (the `saldoTypography` value)
- Modify: `app/src/main/kotlin/com/scholze/saldo/ui/theme/Theme.kt:26-101` (the `SaldoTheme` composable)
- Test: `app/src/test/kotlin/com/scholze/saldo/ui/theme/SaldoColorsTest.kt` (create)

**Interfaces:**
- Produces: `SaldoColors` gains `primaryContainer: Color`, `onPrimaryContainer: Color`, `secondaryContainer: Color`. It **keeps** `segmentedTrack`, `segmentedThumb` and `separator` for now (Task 8 removes them, once their last readers are gone). All other field names are unchanged; only their values move.
- Consumes: nothing new.

- [x] **Step 1: Write the failing test**

Create `app/src/test/kotlin/com/scholze/saldo/ui/theme/SaldoColorsTest.kt`:

```kotlin
package com.scholze.saldo.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the seed, not every hex: a token sheet is a design document, and asserting
 * all 20 values here would just be the file typed twice. What is pinned is what a
 * careless edit would silently break — the three new roles existing in both
 * schemes, the heat ramp keeping its direction, and no HIG blue surviving.
 */
class SaldoColorsTest {

    @Test
    fun aSementeVerdeSubstituiOAzulDoHig() {
        assertNotEquals(Color(0xFF007AFF), LightSaldoColors.tint)
        assertNotEquals(Color(0xFF0A84FF), DarkSaldoColors.tint)
        assertEquals(Color(0xFF2F6A45), LightSaldoColors.tint)
        assertEquals(Color(0xFF99D5AC), DarkSaldoColors.tint)
    }

    @Test
    fun osTresPapeisNovosExistemNosDoisEsquemas() {
        assertEquals(Color(0xFFB4F1C7), LightSaldoColors.primaryContainer)
        assertEquals(Color(0xFF00210F), LightSaldoColors.onPrimaryContainer)
        assertEquals(Color(0xFFD6E8D8), LightSaldoColors.secondaryContainer)
        assertEquals(Color(0xFF1E5133), DarkSaldoColors.primaryContainer)
        assertEquals(Color(0xFFB4F1C7), DarkSaldoColors.onPrimaryContainer)
        assertEquals(Color(0xFF33463A), DarkSaldoColors.secondaryContainer)
    }

    /**
     * O claro clareia com o saldo; o escuro ESCURECE o fundo e ganha verde. As duas
     * rampas sobem — o que não pode acontecer é uma delas ser a outra invertida.
     */
    @Test
    fun aRampaDeCalorSobeNosDoisEsquemas() {
        assertTrue(luminancia(LightSaldoColors.balanceTint1) < luminancia(LightSaldoColors.balanceTint3))
        assertTrue(luminancia(DarkSaldoColors.balanceTint1) < luminancia(DarkSaldoColors.balanceTint3))
    }

    @Test
    fun oEsquemaEscuroNaoEPretoPuro() {
        assertNotEquals(Color(0xFF000000), DarkSaldoColors.background)
        assertEquals(Color(0xFF101410), DarkSaldoColors.background)
    }

    @Test
    fun isDarkContinuaCoerente() {
        assertEquals(false, LightSaldoColors.isDark)
        assertEquals(true, DarkSaldoColors.isDark)
    }

    private fun luminancia(c: Color): Float = 0.2126f * c.red + 0.7152f * c.green + 0.0722f * c.blue
}
```

- [x] **Step 2: Run the test to verify it fails**

```bash
mise exec -- ./gradlew testDebugUnitTest --tests '*SaldoColorsTest*'
```

Expected: FAIL to **compile**, with `Unresolved reference: primaryContainer` (and `onPrimaryContainer`, `secondaryContainer`) — the fields do not exist yet.

- [x] **Step 3: Rewrite `Color.kt`**

Replace the whole of `app/src/main/kotlin/com/scholze/saldo/ui/theme/Color.kt` with:

```kotlin
package com.scholze.saldo.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * Tokens for the Material 3 Expressive direction (canvas "B · sistema"), on a
 * pinned green seed — no dynamic color. The field names are the ones the app
 * already used under the HIG dress; most kept their name and changed value.
 *
 * `segmentedTrack`, `segmentedThumb` and `separator` are on the way out — the
 * segmented control became filter chips and M3 separates by tone and space, not
 * by hairlines. They stay until their last readers are gone.
 */
@Immutable
data class SaldoColors(
    val background: Color,
    val surface: Color,
    val separator: Color,
    val label: Color,
    val secondaryLabel: Color,
    val tint: Color,
    /** Positive deltas, e.g. "+R$ 6.506,38". */
    val positive: Color,
    /** The running-balance figure, now inside the saldo pill. */
    val balance: Color,
    /** Heat tints for the saldo pill, lightest to strongest. */
    val balanceTint1: Color,
    val balanceTint2: Color,
    val balanceTint3: Color,
    val navBar: Color,
    val segmentedTrack: Color,
    val segmentedThumb: Color,
    /** The hero card, and the text on it. */
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    /** Day badge and the nav bar's pill indicator. */
    val secondaryContainer: Color,
    /** Category dots. */
    val categoryVariable: Color,
    val categoryFixed: Color,
    /** "para onde foi": as tags além do top 4 agrupadas, e o que não tem tag. */
    val insightOutras: Color,
    val insightSemTag: Color,
    val isDark: Boolean,
)

val LightSaldoColors = SaldoColors(
    background = Color(0xFFF8FAF5),
    surface = Color(0xFFF2F5EE),
    separator = Color(0xFFC1C9BF),
    label = Color(0xFF191D18),
    secondaryLabel = Color(0xFF414941),
    tint = Color(0xFF2F6A45),
    positive = Color(0xFF2F6A45),
    balance = Color(0xFF10281A),
    balanceTint1 = Color(0xFFE2EFE4),
    balanceTint2 = Color(0xFFCBE7D2),
    balanceTint3 = Color(0xFFB4F1C7),
    navBar = Color(0xFFECEFE8),
    segmentedTrack = Color(0x1F767680),
    segmentedThumb = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFB4F1C7),
    onPrimaryContainer = Color(0xFF00210F),
    secondaryContainer = Color(0xFFD6E8D8),
    categoryVariable = Color(0xFF8C4F63),
    categoryFixed = Color(0xFF7A5A2E),
    insightOutras = Color(0xFF5C5F66),
    insightSemTag = Color(0xFFB9C0B5),
    isDark = false,
)

val DarkSaldoColors = SaldoColors(
    background = Color(0xFF101410),
    surface = Color(0xFF191F1A),
    separator = Color(0xFF414941),
    label = Color(0xFFE0E4DC),
    secondaryLabel = Color(0xFFBFC9BD),
    tint = Color(0xFF99D5AC),
    positive = Color(0xFF7FD79B),
    balance = Color(0xFFB4F1C7),
    balanceTint1 = Color(0xFF1C2C21),
    balanceTint2 = Color(0xFF243A2B),
    balanceTint3 = Color(0xFF2E4C37),
    navBar = Color(0xFF1D231E),
    segmentedTrack = Color(0x3D767680),
    segmentedThumb = Color(0xFF636366),
    primaryContainer = Color(0xFF1E5133),
    onPrimaryContainer = Color(0xFFB4F1C7),
    secondaryContainer = Color(0xFF33463A),
    categoryVariable = Color(0xFFD493A8),
    categoryFixed = Color(0xFFD9BC8A),
    insightOutras = Color(0xFFC0C6CC),
    insightSemTag = Color(0xFF4A524A),
    isDark = true,
)
```

- [x] **Step 4: Run the test to verify it passes**

```bash
mise exec -- ./gradlew testDebugUnitTest --tests '*SaldoColorsTest*'
```

Expected: PASS, 5 tests.

- [x] **Step 5: Rewrite the type ramp**

In `app/src/main/kotlin/com/scholze/saldo/ui/theme/Type.kt`, replace the `saldoTypography` value (from `val saldoTypography = SaldoTypography(` to its closing `)`) with:

```kotlin
val saldoTypography = SaldoTypography(
    largeTitle = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 38.sp,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = (-1.1).sp,
        lineHeightStyle = trim,
    ),
    navTitle = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 30.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.6).sp,
    ),
    body = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 15.sp,
        fontWeight = FontWeight.Medium,
    ),
    row = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 15.sp,
        fontWeight = FontWeight.Medium,
    ),
    subhead = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
    ),
    footnote = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    caption = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
    ),
    // Deixa de ser caixa-alta destacada: no M3 o cabeçalho de seção é um título de
    // card em caixa baixa. As três strings que o usavam perderam as maiúsculas.
    sectionHeader = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 15.sp,
        fontWeight = FontWeight.Bold,
    ),
)
```

Also update the KDoc above `SaldoTypography` — replace the paragraph starting `The HIG type scale used by the canvas.` with:

```kotlin
/**
 * The Material 3 Expressive ramp (canvas "B · sistema"), on the platform sans —
 * which on Android is Roboto, M3's own face, so the app carries no font asset.
 * The HIG's negative tracking survives only on display sizes; M3 does not tighten
 * body text.
 */
```

- [x] **Step 6: Rewrite `Theme.kt`'s scheme mapping**

In `app/src/main/kotlin/com/scholze/saldo/ui/theme/Theme.kt`, replace everything from `val material = if (darkTheme) {` through the closing `}` of that `if/else` (currently lines 32–90) with:

```kotlin
    // Antes, cada papel de container do Material era preso na mesma `surface` chapada
    // para o Material não vazar lavanda por baixo do HIG. Agora os papéis VALEM: o
    // esquema é um M3 de verdade, e diálogo, snackbar, switch e campo de texto herdam
    // dele em vez de precisarem ser domados um a um.
    val material = if (darkTheme) {
        darkColorScheme(
            primary = colors.tint,
            onPrimary = Color(0xFF003919),
            primaryContainer = colors.primaryContainer,
            onPrimaryContainer = colors.onPrimaryContainer,
            secondaryContainer = colors.secondaryContainer,
            onSecondaryContainer = colors.onPrimaryContainer,
            background = colors.background,
            onBackground = colors.label,
            surface = colors.background,
            onSurface = colors.label,
            surfaceContainerLowest = Color(0xFF0B0F0B),
            surfaceContainerLow = colors.surface,
            surfaceContainer = colors.navBar,
            surfaceContainerHigh = Color(0xFF262C26),
            surfaceContainerHighest = Color(0xFF313830),
            onSurfaceVariant = colors.secondaryLabel,
            outline = Color(0xFF8A938A),
            outlineVariant = colors.separator,
        )
    } else {
        lightColorScheme(
            primary = colors.tint,
            onPrimary = Color.White,
            primaryContainer = colors.primaryContainer,
            onPrimaryContainer = colors.onPrimaryContainer,
            secondaryContainer = colors.secondaryContainer,
            onSecondaryContainer = colors.onPrimaryContainer,
            background = colors.background,
            onBackground = colors.label,
            surface = colors.background,
            onSurface = colors.label,
            surfaceContainerLowest = Color(0xFFFFFFFF),
            surfaceContainerLow = colors.surface,
            surfaceContainer = colors.navBar,
            surfaceContainerHigh = Color(0xFFE6E9E2),
            surfaceContainerHighest = Color(0xFFE0E4DB),
            onSurfaceVariant = colors.secondaryLabel,
            outline = Color(0xFF717970),
            outlineVariant = colors.separator,
        )
    }
```

Then update the `SaldoTheme` KDoc — replace the paragraph starting `Material 3 is kept underneath only so that plumbing` with:

```kotlin
/**
 * The app theme. Material 3 is the real scheme now, not a thing to be contained:
 * [SaldoTheme.colors] and the `MaterialTheme.colorScheme` are two views of the same
 * pinned green seed, so Material's own surfaces land where the design wants them.
 */
```

- [x] **Step 7: Run the full JVM suite and lint**

```bash
mise run test
mise exec -- ./gradlew lintDebug
```

Expected: 144 tests pass (139 existing + 5 new), lint 0 errors.

> **Desvios da Task 1 (registrados na execução, 2026-08-18):**
>
> 1. **Os papéis `inverse*` continuam mapeados.** O Step 6 deste plano os deixava cair,
>    mas `lightColorScheme`/`darkColorScheme` não os derivam de `primary` — sem o mapa
>    o Snackbar volta ao lavanda de fábrica, que é exatamente o bug que o código antigo
>    corrigia (há 4 `showSnackbar` vivos em `SaldoApp.kt`). Foram mantidos e revalorados
>    na semente verde: no claro `2D322C`/`EFF2EB`/`99D5AC`, no escuro `E0E4DC`/`2D322C`/`2F6A45`
>    (`inversePrimary` é sempre o tint do esquema oposto, que é o que lê sobre eles).
>
> 2. **`aRampaDeCalorSobeNosDoisEsquemas` estava errado e virou `aRampaDeCalorGanhaVerdeNosDoisEsquemas`.**
>    O teste do Step 1 media luminância e afirmava que as duas rampas sobem. Os hexes deste
>    mesmo plano dizem outra coisa: no claro a luminância é .923 → .877 → .882 — cai e ainda
>    por cima **não é monótona** —, no escuro é .156 → .209 → .267. Nenhuma asserção de
>    luminância consegue fixar a rampa clara. O invariante real, monótono nos dois esquemas e
>    nos três degraus, é o **verdor** (verde menos a média de vermelho e azul):
>    claro .047 → .096 → .202, escuro .053 → .073 → .100. O teste agora percorre os três
>    degraus com `zipWithNext`, então um degrau fora de ordem também quebra.

- [ ] **Step 8: Run the instrumented suite**

```bash
mise run test-device
```

Expected: 66/66 pass. Nothing asserts on a hex, so a pure token swap must not move a single test. **If any instrumented test fails here, stop** — it means something asserted on appearance, and that is a finding for the plan, not a thing to paper over.

- [ ] **Step 9: Commit**

```bash
git add app/src/main/kotlin/com/scholze/saldo/ui/theme/ app/src/test/kotlin/com/scholze/saldo/ui/theme/
git commit -m "feat: tema — semente verde M3 fixa no lugar das cores do HIG"
```

---

### Task 2: Re-skin the shared components, add the M3 vocabulary

**Files:**
- Modify: `app/src/main/kotlin/com/scholze/saldo/ui/components/Components.kt` (`InsetGroup`, `InsetRow`, `FilledActionButton`)
- Create: `app/src/main/kotlin/com/scholze/saldo/ui/components/M3.kt`
- Test: `app/src/androidTest/kotlin/com/scholze/saldo/ui/components/M3Test.kt` (create)

**Interfaces:**
- Consumes: `SaldoTheme.colors` (incl. Task 1's `primaryContainer`, `secondaryContainer`), `SaldoTheme.type`, `MoneyText`, `FormatoMoney`.
- Produces:
  - `FiltroChips(opcoes: List<String>, selecionado: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier)`
  - `SaldoPill(centavos: Long, nivel: Int, modifier: Modifier = Modifier)` — `nivel` is 0, 1 or 2, mapping to `balanceTint1/2/3`
  - `DiaBadge(dia: Int, diaSemana: String, destacado: Boolean, modifier: Modifier = Modifier)`
  - `SaldoTopBar(titulo: String, onAnterior: () -> Unit, onProximo: () -> Unit, modifier: Modifier = Modifier, acao: @Composable (() -> Unit)? = null)`
  - `InsetGroup` and `InsetRow` keep their exact signatures; only their internals change.
- Retires nothing yet: `HairlineDivider` and `SegmentedControl` still compile and still have callers until Task 8.

- [x] **Step 1: Write the failing test**

Create `app/src/androidTest/kotlin/com/scholze/saldo/ui/components/M3Test.kt`:

```kotlin
package com.scholze.saldo.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.scholze.saldo.ui.theme.SaldoTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class M3Test {

    @get:Rule val rule = createComposeRule()

    @Test
    fun chipSelecionadoNaoRedispara() {
        var escolhido = -1
        rule.setContent {
            SaldoTheme(darkTheme = false) {
                FiltroChips(
                    opcoes = listOf("todas", "diários", "fixas"),
                    selecionado = 0,
                    onSelect = { escolhido = it },
                )
            }
        }
        rule.onNodeWithText("fixas").performClick()
        assertEquals(2, escolhido)
    }

    @Test
    fun pillMostraOValorEMascaraJuntoComOResto() {
        rule.setContent {
            SaldoTheme(darkTheme = false) {
                Column { SaldoPill(centavos = 781_245, nivel = 2) }
            }
        }
        rule.onNodeWithText("7.812,45").assertIsDisplayed()
    }

    /**
     * O badge carrega DOIS nós de texto (dia e dia-da-semana) e ambos têm de existir:
     * um badge que perdesse o dia da semana passaria despercebido num screenshot.
     */
    @Test
    fun badgeMostraDiaEDiaDaSemana() {
        rule.setContent {
            SaldoTheme(darkTheme = false) {
                DiaBadge(dia = 8, diaSemana = "sáb", destacado = true)
            }
        }
        rule.onNodeWithText("08").assertIsDisplayed()
        rule.onNodeWithText("sáb").assertIsDisplayed()
    }

    @Test
    fun topBarNavegaNosDoisSentidos() {
        var anterior = 0
        var proximo = 0
        rule.setContent {
            SaldoTheme(darkTheme = false) {
                SaldoTopBar(titulo = "agosto 2026", onAnterior = { anterior++ }, onProximo = { proximo++ })
            }
        }
        rule.onNodeWithText("agosto 2026").assertIsDisplayed()
        rule.onNodeWithContentDescription("mês anterior").performClick()
        rule.onNodeWithContentDescription("próximo mês").performClick()
        assertEquals(1, anterior)
        assertEquals(1, proximo)
    }
}
```

Add these imports at the top of the file, with the others:

```kotlin
import androidx.compose.ui.test.onNodeWithContentDescription
```

- [x] **Step 2: Run the test to verify it fails**

```bash
mise exec -- ./gradlew connectedDebugAndroidTest --tests '*M3Test*'
```

Expected: FAIL to compile — `Unresolved reference: FiltroChips`.

- [x] **Step 3: Create `M3.kt`**

Create `app/src/main/kotlin/com/scholze/saldo/ui/components/M3.kt`:

```kotlin
package com.scholze.saldo.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.scholze.saldo.ui.privacy.FormatoMoney
import com.scholze.saldo.ui.privacy.MoneyText
import com.scholze.saldo.ui.theme.SaldoTheme

private val PILL = RoundedCornerShape(percent = 50)

/**
 * M3 filter chips — the replacement for the HIG segmented control.
 *
 * Unlike the segmented control there is no sliding thumb: selection is carried by
 * the fill plus a leading check, which is what makes it read as Material and not as
 * a repainted iOS control.
 */
@Composable
fun FiltroChips(
    opcoes: List<String>,
    selecionado: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SaldoTheme.colors
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        opcoes.forEachIndexed { index, rotulo ->
            val ativo = index == selecionado
            Row(
                Modifier
                    .clip(PILL)
                    .then(
                        if (ativo) Modifier.background(colors.secondaryContainer)
                        else Modifier.border(1.dp, colors.separator, PILL),
                    )
                    .clickable { onSelect(index) }
                    // 32dp de altura + 12dp de padding vertical = 56dp de alvo: acima
                    // do mínimo de 44dp mesmo com o chip visualmente baixo.
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (ativo) {
                    SaldoGlyph(SaldoIcon.CHECK, colors.onPrimaryContainer, size = 15.dp, strokeWidth = 2.6.dp)
                }
                Text(
                    text = rotulo,
                    style = SaldoTheme.type.footnote,
                    color = if (ativo) colors.onPrimaryContainer else colors.secondaryLabel,
                )
            }
        }
    }
}

/**
 * O saldo corrido do dia. A escala de calor sobreviveu à morte da coluna: [nivel]
 * 0/1/2 são os mesmos três baldes de `heatTint`, agora pintando o fundo da pill.
 */
@Composable
fun SaldoPill(centavos: Long, nivel: Int, modifier: Modifier = Modifier) {
    val colors = SaldoTheme.colors
    val fundo = when (nivel) {
        0 -> colors.balanceTint1
        1 -> colors.balanceTint2
        else -> colors.balanceTint3
    }
    Box(
        modifier
            .clip(PILL)
            .background(fundo)
            .padding(horizontal = 12.dp, vertical = 5.dp),
    ) {
        MoneyText(
            centavos = centavos,
            style = SaldoTheme.type.footnote,
            color = colors.balance,
            formato = FormatoMoney.VALOR,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** O disco do dia na linha do ledger: número em cima, dia da semana embaixo. */
@Composable
fun DiaBadge(dia: Int, diaSemana: String, destacado: Boolean, modifier: Modifier = Modifier) {
    val colors = SaldoTheme.colors
    Column(
        modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(if (destacado) colors.tint else colors.secondaryContainer),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            dia.toString().padStart(2, '0'),
            style = SaldoTheme.type.row.copy(fontWeight = FontWeight.Bold),
            color = if (destacado) Color.White else colors.onPrimaryContainer,
        )
        Text(
            diaSemana,
            style = SaldoTheme.type.caption.copy(fontSize = 9.sp, fontWeight = FontWeight.SemiBold),
            color = if (destacado) colors.primaryContainer else colors.secondaryLabel,
        )
    }
}

/**
 * A barra superior grande do M3: título alinhado à ESQUERDA e em corpo grande, que é
 * a diferença mais visível de todas contra a barra centrada do HIG.
 *
 * [acao] entra entre as duas setas — é onde o olho da privacidade mora no ledger.
 */
@Composable
fun SaldoTopBar(
    titulo: String,
    onAnterior: () -> Unit,
    onProximo: () -> Unit,
    modifier: Modifier = Modifier,
    acao: @Composable (() -> Unit)? = null,
) {
    val colors = SaldoTheme.colors
    Column(modifier.fillMaxWidth().background(colors.background)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconeRedondo(SaldoIcon.CHEVRON_LEFT, "mês anterior", onAnterior)
            Box(Modifier.weight(1f))
            acao?.invoke()
            IconeRedondo(SaldoIcon.CHEVRON_RIGHT, "próximo mês", onProximo)
        }
        Text(
            titulo,
            Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 14.dp),
            style = SaldoTheme.type.navTitle,
            color = colors.label,
        )
    }
}

/** Um alvo redondo de 44dp — o tamanho de toque do M3 para ícone sem rótulo. */
@Composable
fun IconeRedondo(icon: SaldoIcon, descricao: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = SaldoTheme.colors
    Box(
        modifier
            .size(44.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .semantics { contentDescription = descricao },
        contentAlignment = Alignment.Center,
    ) {
        SaldoGlyph(icon, colors.secondaryLabel, size = 22.dp, strokeWidth = 2.dp)
    }
}
```

Add the missing `sp` import to that file, with the other `unit` imports:

```kotlin
import androidx.compose.ui.unit.sp
```

- [x] **Step 4: Add the `CHECK` glyph**

In `app/src/main/kotlin/com/scholze/saldo/ui/components/Icons.kt`, add `CHECK` to the enum (line 20-22):

```kotlin
enum class SaldoIcon {
    SALDOS, TOTAIS, TAGS, MAIS, PLUS, CHEVRON_LEFT, CHEVRON_RIGHT, BACKSPACE, RECORRENTE,
    OLHO, OLHO_RISCADO, CHECK,
}
```

and add this branch to the `when (icon)` inside `SaldoGlyph`, next to the other stroke glyphs:

```kotlin
            // O tique do chip selecionado.
            SaldoIcon.CHECK -> {
                drawLine(tint, Offset(w * 0.20f, h * 0.52f), Offset(w * 0.42f, h * 0.74f), sw, StrokeCap.Round)
                drawLine(tint, Offset(w * 0.42f, h * 0.74f), Offset(w * 0.80f, h * 0.28f), sw, StrokeCap.Round)
            }
```

- [x] **Step 5: Re-skin `InsetGroup`, `InsetRow` and `FilledActionButton`**

In `app/src/main/kotlin/com/scholze/saldo/ui/components/Components.kt`, replace the three composables (leaving `HairlineDivider` and `SegmentedControl` untouched for now) with:

```kotlin
/** An M3 tonal card: 28dp corners, `surfaceContainerLow`, rows separated by space. */
@Composable
fun InsetGroup(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(SaldoTheme.colors.surface)
            .padding(vertical = 6.dp),
        content = content,
    )
}

/** A label/value row inside an [InsetGroup]. */
@Composable
fun InsetRow(
    label: String,
    modifier: Modifier = Modifier,
    value: String? = null,
    valueColor: Color? = null,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    val colors = SaldoTheme.colors
    val base = modifier
        .fillMaxWidth()
        .defaultMinSize(minHeight = 48.dp)

    Row(
        (if (onClick != null) base.clickable(onClick = onClick) else base)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text = label, style = SaldoTheme.type.body, color = colors.label)
        Box(Modifier.weight(1f))
        if (value != null) {
            Text(
                text = value,
                style = SaldoTheme.type.body.copy(fontWeight = FontWeight.Bold),
                color = valueColor ?: colors.secondaryLabel,
            )
        }
        trailing?.invoke()
    }
}

/** A filled, full-width M3 action button. */
@Composable
fun FilledActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = SaldoTheme.colors
    Box(
        modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(percent = 50))
            .background(if (enabled) colors.tint else colors.surface)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = SaldoTheme.type.body.copy(fontWeight = FontWeight.Bold),
            color = if (enabled) Color.White else colors.secondaryLabel,
        )
    }
}
```

- [x] **Step 6: Run the new test to verify it passes**

```bash
mise exec -- ./gradlew connectedDebugAndroidTest --tests '*M3Test*'
```

Expected: PASS, 4 tests.

- [x] **Step 7: Run everything green**

```bash
mise run test
mise run test-device
mise exec -- ./gradlew lintDebug
```

Expected: JVM 144, instrumented 70 (66 + 4 new), lint 0 errors. `LembretesScreenTest` walks `InsetRow`'s sibling structure — if it fails here, that is Task 8's problem arriving early; note it and fix it in this task rather than leaving the suite red.

> **Desvio da Task 2 — e um ACHADO que sobra para as Tasks 3, 4, 8 e para o widget
> (registrado na execução, 2026-08-18):**
>
> Retokenizar o tema (Task 1) quebrou **todo `Color.White` pintado sobre `colors.tint`**.
> Sob o HIG o tint era escuro nos dois esquemas (`007AFF` / `0A84FF`), então branco em
> cima funcionava sempre. O tint do M3 é um verde **claro** no escuro (`99D5AC`): branco
> sobre ele dá **1,68:1**, contra o mínimo de 4,5:1. O papel certo é `onPrimary`, que a
> Task 1 já definiu (branco no claro, `003919` no escuro) e que dá **7,8:1**.
>
> Corrigidos nesta task: `FilledActionButton` (Components.kt) e `DiaBadge` (M3.kt) — os
> dois trocaram `Color.White` por `MaterialTheme.colorScheme.onPrimary`. O código destes
> dois no plano trazia o branco fixo; foi por isso que o desvio existe.
>
> **Ainda quebrados, cada um na task que reescreve o arquivo:**
>
> | onde | o que | task |
> |---|---|---|
> | `SaldoTabBar.kt:123` | glifo `PLUS` do FAB, branco sobre `tint` | Task 3 |
> | `LedgerScreen.kt:258` | pilula "hoje", branco sobre `tint` | Task 4 |
> | `LedgerScreen.kt:505` | "excluir" do swipe, branco sobre `categoryVariable` — **2,46:1** no escuro; aqui `onPrimary` nao serve, o fundo nao e o primary | Task 4 |
> | `NewEntrySheet.kt:169` | chip de natureza selecionado, branco sobre `tint` | Task 8 |
> | `SaldoWidgetContent.kt:136` | "+" do widget, branco sobre `CoresWidget.tint` | **nenhuma** |
>
> A ultima linha corrige uma afirmacao das restricoes globais e da memoria do projeto: o
> widget **nao** sai de graca. `SaldoWidgetContent` monta seus `ColorProvider` a partir de
> `Light/DarkSaldoColors`, entao retokenizar carrega as cores — e carrega junto esta
> regressao. Nenhuma task do plano abre esse arquivo. Precisa de um `onPrimary` proprio
> (o Glance nao enxerga o `MaterialTheme.colorScheme`), e nenhum teste cobre isso.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/kotlin/com/scholze/saldo/ui/components/ app/src/androidTest/kotlin/com/scholze/saldo/ui/components/
git commit -m "feat: componentes — chips, pill de saldo, badge do dia e barra grande do M3"
```

---

### Task 3: The navigation bar and the docked FAB

**Files:**
- Modify: `app/src/main/kotlin/com/scholze/saldo/ui/nav/SaldoTabBar.kt` (whole file)
- Test: `app/src/androidTest/kotlin/com/scholze/saldo/ui/nav/SaldoTabBarTest.kt` (create)

**Interfaces:**
- Consumes: `SaldoTab`, `SaldoGlyph`, `SaldoTheme`.
- Produces: `SaldoTabBar(selected, onSelect, onAdd, modifier)` — **signature unchanged**, so `SaldoApp.kt` is not touched. `TAG_ADD` keeps its value `"tab-add"`.

- [x] **Step 1: Write the failing test**

Create `app/src/androidTest/kotlin/com/scholze/saldo/ui/nav/SaldoTabBarTest.kt`:

```kotlin
package com.scholze.saldo.ui.nav

import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.scholze.saldo.ui.theme.SaldoTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SaldoTabBarTest {

    @get:Rule val rule = createComposeRule()

    private fun montar(onSelect: (SaldoTab) -> Unit = {}, onAdd: () -> Unit = {}) {
        rule.setContent {
            SaldoTheme(darkTheme = false) {
                SaldoTabBar(selected = SaldoTab.SALDOS, onSelect = onSelect, onAdd = onAdd)
            }
        }
    }

    @Test
    fun asQuatroAbasEstaoLaEONemUmaVezMais() {
        montar()
        listOf("saldos", "totais", "tags", "mais").forEach {
            rule.onNodeWithText(it).assertExists()
        }
    }

    @Test
    fun tocarNumaAbaSeleciona() {
        var escolhida: SaldoTab? = null
        montar(onSelect = { escolhida = it })
        rule.onNodeWithText("tags").performClick()
        assertEquals(SaldoTab.TAGS, escolhida)
    }

    @Test
    fun oMaisContinuaSendoUmBotao() {
        var adds = 0
        montar(onAdd = { adds++ })
        rule.onNodeWithTag(TAG_ADD).performClick()
        assertEquals(1, adds)
    }

    /** O FAB do M3 é 64dp; um alvo menor que isso significa que o docking quebrou. */
    @Test
    fun oFabTemOTamanhoDoM3() {
        montar()
        rule.onNodeWithTag(TAG_ADD).assertHeightIsAtLeast(56.dp)
    }
}
```

Add this import with the others:

```kotlin
import androidx.compose.ui.test.assertExists
```

- [x] **Step 2: Run the test to verify it fails**

```bash
mise exec -- ./gradlew connectedDebugAndroidTest --tests '*SaldoTabBarTest*'
```

Expected: FAIL — `oFabTemOTamanhoDoM3` fails, because the current add button is 46dp.

- [x] **Step 3: Rewrite `SaldoTabBar.kt`**

Replace the whole of `app/src/main/kotlin/com/scholze/saldo/ui/nav/SaldoTabBar.kt` with:

```kotlin
package com.scholze.saldo.ui.nav

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.scholze.saldo.ui.components.SaldoGlyph
import com.scholze.saldo.ui.components.SaldoIcon
import com.scholze.saldo.ui.theme.SaldoTheme

enum class SaldoTab(val rotulo: String, val icon: SaldoIcon) {
    SALDOS("saldos", SaldoIcon.SALDOS),
    TOTAIS("totais", SaldoIcon.TOTAIS),
    TAGS("tags", SaldoIcon.TAGS),
    MAIS("mais", SaldoIcon.MAIS),
}

/**
 * The M3 navigation bar — four destinations, the active one carrying a pill
 * indicator behind its icon, with the add FAB docked over the bar's top edge.
 *
 * The information architecture is exactly what it was: four tabs and a centre add
 * button that is a button, not a destination, and never becomes "selected".
 */
@Composable
fun SaldoTabBar(
    selected: SaldoTab,
    onSelect: (SaldoTab) -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SaldoTheme.colors
    Column(modifier.fillMaxWidth()) {
        // O FAB avança 28dp para dentro da barra. `offset` em vez de padding negativo
        // porque só o desenho desce: o alvo de toque acompanha o deslocamento.
        Box(Modifier.fillMaxWidth().height(36.dp), contentAlignment = Alignment.Center) {
            AddButton(onAdd, Modifier.offset(y = 4.dp))
        }
        Row(
            Modifier
                .fillMaxWidth()
                .background(colors.navBar)
                .navigationBarsPadding()
                .height(84.dp)
                .padding(bottom = 14.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            TabItem(SaldoTab.SALDOS, selected, onSelect, Modifier.weight(1f))
            TabItem(SaldoTab.TOTAIS, selected, onSelect, Modifier.weight(1f))
            // O vão do FAB.
            Box(Modifier.width(72.dp))
            TabItem(SaldoTab.TAGS, selected, onSelect, Modifier.weight(1f))
            TabItem(SaldoTab.MAIS, selected, onSelect, Modifier.weight(1f))
        }
    }
}

@Composable
private fun TabItem(
    tab: SaldoTab,
    selected: SaldoTab,
    onSelect: (SaldoTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SaldoTheme.colors
    val active = tab == selected

    Column(
        modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onSelect(tab) },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            Modifier
                .size(width = 64.dp, height = 32.dp)
                .clip(RoundedCornerShape(percent = 50))
                .background(if (active) colors.primaryContainer else Color.Transparent),
            contentAlignment = Alignment.Center,
        ) {
            SaldoGlyph(
                tab.icon,
                if (active) colors.onPrimaryContainer else colors.secondaryLabel,
                size = 22.dp,
                strokeWidth = if (active) 2.4.dp else 2.dp,
            )
        }
        Text(
            tab.rotulo,
            style = SaldoTheme.type.caption.copy(
                fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
            ),
            color = if (active) colors.label else colors.secondaryLabel,
        )
    }
}

/** O `+` central, para os testes: é um glifo desenhado, sem nó de texto para procurar. */
const val TAG_ADD = "tab-add"

@Composable
private fun AddButton(onAdd: () -> Unit, modifier: Modifier = Modifier) {
    val colors = SaldoTheme.colors
    Box(
        modifier
            .size(64.dp)
            .shadow(6.dp, RoundedCornerShape(22.dp))
            .clip(RoundedCornerShape(22.dp))
            .background(colors.tint)
            .clickable(onClick = onAdd)
            .testTag(TAG_ADD),
        contentAlignment = Alignment.Center,
    ) {
        SaldoGlyph(SaldoIcon.PLUS, if (colors.isDark) Color(0xFF003919) else Color.White, size = 28.dp, strokeWidth = 2.6.dp)
    }
}
```

- [x] **Step 4: Run the test to verify it passes**

```bash
mise exec -- ./gradlew connectedDebugAndroidTest --tests '*SaldoTabBarTest*'
```

Expected: PASS, 4 tests.

- [x] **Step 5: Run everything green**

```bash
mise run test
mise run test-device
mise exec -- ./gradlew lintDebug
```

Expected: JVM 144, instrumented 74, lint 0 errors.

> **Desvios da Task 3 (registrados na execucao, 2026-08-18) — tres, e so um foi pego por teste:**
>
> 1. **O FAB saia com 36dp, nao 64.** `Modifier.size(64.dp)` se deixa espremer pela
>    restricao maxima do pai, e a faixa que hospeda o FAB tem 36dp. Trocado por
>    `requiredSize(64.dp)`, que ignora a restricao — que e o que faz o botao TRANSBORDAR
>    para dentro da barra em vez de caber nela. Pego pelo `oFabTemOTamanhoDoM3` do proprio
>    plano: TDD funcionando como devia.
>
> 2. **O FAB saia cortado ao meio.** Num `Column` a `Row` da barra e declarada depois da
>    faixa do FAB, entao o `background(navBar)` dela pinta por cima da metade que
>    transborda. A estrutura virou um `Box` com a coluna primeiro e o FAB por ultimo, que
>    e o que "docked over the bar's top edge" exige. **Nenhum dos quatro testes pegou
>    isto** — `assertHeightIsAtLeast` mede bounds nao-clipados, entao passou com o botao
>    visivelmente cortado. Foi um screenshot que pegou. Vale a licao para as Tasks 4-8:
>    teste de Compose nao ve ordem de pintura.
>
> 3. **O snackbar ficava POR DENTRO da barra.** `SaldoApp.kt:262` fixava
>    `padding(bottom = 70.dp)`, que era a altura da barra do HIG; a barra do M3 mede
>    120dp acima do inset. O plano dizia que `SaldoApp.kt` nao seria tocado — verdade
>    quanto a *assinatura* de `SaldoTabBar`, falso quanto a *altura*. A barra agora
>    exporta `ALTURA_FAIXA_FAB` e `ALTURA_BARRA`, e o snackbar deriva o offset delas em
>    vez de repetir um numero magico.
>
> Tambem: o glifo `PLUS` usa `MaterialTheme.colorScheme.onPrimary` em vez do
> `if (colors.isDark) Color(0xFF003919) else Color.White` do plano — mesmo resultado, mas
> o hex fica so no Theme.kt.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/kotlin/com/scholze/saldo/ui/nav/ app/src/androidTest/kotlin/com/scholze/saldo/ui/nav/
git commit -m "feat: navegação — barra do M3 com indicador em pill e FAB ancorado"
```

---

### Task 4: The ledger screen

The screen the whole redesign was decided on. The three-column grid (`DIA | MOVIMENTAÇÕES | SALDO`) is replaced by M3 list items; `heatTint`'s three buckets survive as the pill's level.

**Files:**
- Modify: `app/src/main/kotlin/com/scholze/saldo/ui/ledger/LedgerScreen.kt`
- Test: `app/src/androidTest/kotlin/com/scholze/saldo/ui/ledger/LedgerDayGridTest.kt` (extend)

**Interfaces:**
- Consumes: `SaldoTopBar`, `FiltroChips`, `SaldoPill`, `DiaBadge` (Task 2); `LedgerUiState`, `MesLedger`, `DiaRow`, `ItemDia` unchanged.
- Produces: `TAG_SALDO_PROJETADO` and `tagSaldoDoDia(dia)` keep their exact values — they move onto the hero and the pill. `heatTint` changes return type from `Color` to `Int` and is renamed `nivelDeCalor`.

- [x] **Step 1: Replace `heatTint` with `nivelDeCalor`**

In `app/src/main/kotlin/com/scholze/saldo/ui/ledger/LedgerScreen.kt`, replace the `heatTint` function (currently the last function in the file, lines 596–605) with:

```kotlin
/**
 * Which of the three heat buckets a day's balance falls in — 0, 1 or 2.
 *
 * The thresholds are the ones the heat-tinted column used; only what consumes them
 * changed (a pill background instead of a column fill), so a month that read as
 * "thin at the end" still does.
 */
private fun nivelDeCalor(saldo: Long, faixa: ClosedRange<Long>): Int {
    if (faixa.endInclusive <= faixa.start) return 1
    val ratio = (saldo - faixa.start).toDouble() / (faixa.endInclusive - faixa.start).toDouble()
    return when {
        ratio < 0.34 -> 0
        ratio < 0.67 -> 1
        else -> 2
    }
}
```

- [x] **Step 2: Replace the nav bar and hero**

Replace `MonthNavBar` (lines 292–348, up to and including its trailing `HairlineDivider()`) with a call-through to the shared bar. Delete the whole `MonthNavBar` function and change its call site inside `LedgerScreen` from:

```kotlin
            MonthNavBar(state, onMesAnterior, onProximoMes, onTogglePrivacidade)
```

to:

```kotlin
            SaldoTopBar(
                titulo = state.mesAtual.format(tituloMes),
                onAnterior = onMesAnterior,
                onProximo = onProximoMes,
                acao = {
                    IconeRedondo(
                        if (LocalPrivacy.current.oculto) SaldoIcon.OLHO_RISCADO else SaldoIcon.OLHO,
                        "alternar privacidade",
                        onTogglePrivacidade,
                    )
                },
            )
```

Then replace `BalanceHero` (lines 350–401) with:

```kotlin
@Composable
private fun BalanceHero(mes: MesLedger, onTogglePrivacidade: () -> Unit) {
    val colors = SaldoTheme.colors
    Column(
        Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(colors.primaryContainer)
            .clickable(onClick = onTogglePrivacidade)   // tocar no hero também alterna
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            "saldo projetado · " + mes.projetadoEm.format(diaCurto).removeSuffix("."),
            style = SaldoTheme.type.footnote, color = colors.onPrimaryContainer.copy(alpha = 0.72f),
        )
        // Contagem até o valor novo em vez de troca seca — de mês para mês, e quando uma
        // movimentação entra ou sai. Mascarado o número nem aparece, então a animação
        // simplesmente não se vê; o alvo continua sendo o valor real.
        val animado by animateFloatAsState(
            targetValue = mes.saldoProjetadoCentavos.toFloat(),
            animationSpec = tween(durationMillis = 450),
            label = "saldoCountUp",
        )
        MoneyText(
            centavos = animado.toLong(),
            modifier = Modifier.testTag(TAG_SALDO_PROJETADO),
            style = SaldoTheme.type.largeTitle, color = colors.onPrimaryContainer,
        )
        Row(
            Modifier
                .padding(top = 6.dp)
                .clip(RoundedCornerShape(percent = 50))
                .background(colors.onPrimaryContainer.copy(alpha = 0.10f))
                .padding(horizontal = 11.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MoneyText(
                centavos = mes.deltaNoMesCentavos,
                style = SaldoTheme.type.subhead,
                color = colors.onPrimaryContainer,
                formato = FormatoMoney.ASSINADO_COM_SIMBOLO,
            )
            Text("no mês", style = SaldoTheme.type.subhead, color = colors.onPrimaryContainer)
        }
        if (mes.estimativaCentavos > 0) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("inclui estimativa de", style = SaldoTheme.type.caption, color = colors.onPrimaryContainer.copy(alpha = 0.7f))
                MoneyText(
                    centavos = mes.estimativaCentavos,
                    style = SaldoTheme.type.caption, color = colors.onPrimaryContainer.copy(alpha = 0.7f),
                )
                Text("em diários", style = SaldoTheme.type.caption, color = colors.onPrimaryContainer.copy(alpha = 0.7f))
            }
        }
    }
}
```

- [x] **Step 3: Delete the column header and swap the filter control**

Delete the whole `ColumnHeader` composable (lines 402–411) and its `item(key = "header") { ColumnHeader(); HairlineDivider() }` entry in the `LazyColumn`.

**This changes `cabecalhos`.** In `LedgerScreen`, the index arithmetic at line 63 must drop from 3/4 to 2/3:

```kotlin
    // Índice do item de hoje na LazyColumn: 2 headers antes dos dias (hero, chips), 3
    // quando o chip de tag entra. O cabeçalho de colunas sumiu com a grade.
    val cabecalhos = if (state.tagFiltro != null) 3 else 2
```

Replace the `item(key = "filtro")` block with:

```kotlin
                    item(key = "filtro") {
                        FiltroChips(
                            opcoes = FiltroLedger.entries.map { it.rotulo },
                            selecionado = state.filtro.ordinal,
                            onSelect = { onFiltro(FiltroLedger.entries[it]) },
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 12.dp),
                        )
                    }
```

- [x] **Step 4: Rewrite `DayRow` as an M3 list item**

Replace `DayRow` (lines 433–594) with:

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DayRow(
    dia: DiaRow,
    faixa: ClosedRange<Long>,
    hoje: LocalDate,
    onItemClick: (Movimentacao) -> Unit,
    onExcluir: (Movimentacao) -> Unit,
    onFaturaClick: (Fatura) -> Unit,
) {
    val colors = SaldoTheme.colors
    val ehHoje = dia.data == hoje

    Row(
        Modifier
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(if (ehHoje) colors.secondaryContainer else colors.surface)
            .then(if (ehHoje) Modifier.border(2.dp, colors.tint, RoundedCornerShape(20.dp)) else Modifier)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        DiaBadge(
            dia = dia.data.dayOfMonth,
            diaSemana = dia.data.format(diaSemanaCurto),
            destacado = ehHoje,
        )

        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            if (dia.itens.isEmpty()) {
                Text("sem movimentações", style = SaldoTheme.type.row, color = colors.secondaryLabel)
            } else {
                dia.itens.forEach { item ->
                    // Exaustivo na interface selada: cada ramo sabe exatamente com que tipo
                    // de item está lidando, sem cast nenhum (nem seguro nem inseguro).
                    when (item) {
                        is ItemDia.Mov -> key(item.mov.id, item.descricao) {
                            // A chave prende o `rememberSwipeToDismissBoxState` ao item, não à
                            // posição: sem ela, apagar o primeiro de dois itens do mesmo dia faria
                            // o segundo herdar o slot do primeiro.
                            val mov = item.mov
                            val dismissState = rememberSwipeToDismissBoxState()
                            // Reagir à TRANSIÇÃO de currentValue, não a um confirmValueChange:
                            // aquele callback é chamado mais de uma vez no mesmo gesto.
                            LaunchedEffect(dismissState.currentValue) {
                                if (dismissState.currentValue == SwipeToDismissBoxValue.EndToStart) {
                                    onExcluir(mov)
                                    dismissState.reset()
                                }
                            }
                            SwipeToDismissBox(
                                state = dismissState,
                                enableDismissFromStartToEnd = false,
                                backgroundContent = {
                                    Box(
                                        Modifier
                                            .fillMaxSize()
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(colors.categoryVariable),
                                        contentAlignment = Alignment.CenterEnd,
                                    ) {
                                        Text(
                                            "excluir",
                                            Modifier.padding(end = 12.dp),
                                            style = SaldoTheme.type.footnote,
                                            color = Color.White,
                                        )
                                    }
                                },
                            ) {
                                // Base opaca: o SwipeToDismissBox mantém o backgroundContent
                                // ("excluir", vermelho) sempre desenhado atrás do conteúdo, então
                                // sem ela o vermelho vazaria através da linha mesmo parada.
                                Box(Modifier.background(if (ehHoje) colors.secondaryContainer else colors.surface)) {
                                    LinhaMov(
                                        descricao = item.descricao,
                                        centavos = item.valorCentavos,
                                        recorrente = item.recorrente,
                                        natureza = mov.natureza,
                                        onClick = { onItemClick(mov) },
                                    )
                                }
                            }
                        }

                        is ItemDia.FaturaDia -> LinhaMov(
                            descricao = item.descricao,
                            centavos = item.valorCentavos,
                            recorrente = true,
                            natureza = Natureza.CARTAO,
                            onClick = { onFaturaClick(item.fatura) },
                        )
                    }
                }
            }
        }

        SaldoPill(
            centavos = dia.saldoCentavos,
            nivel = nivelDeCalor(dia.saldoCentavos, faixa),
            modifier = Modifier.testTag(tagSaldoDoDia(dia.data.dayOfMonth)),
        )
    }
}

/** Uma movimentação dentro da linha do dia: marcador, descrição, valor. */
@Composable
private fun LinhaMov(
    descricao: String,
    centavos: Long,
    recorrente: Boolean,
    natureza: Natureza,
    onClick: () -> Unit,
) {
    val colors = SaldoTheme.colors
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (recorrente) {
            SaldoGlyph(SaldoIcon.RECORRENTE, colors.secondaryLabel, size = 11.dp, strokeWidth = 1.3.dp)
        } else {
            Box(
                Modifier.size(6.dp).background(
                    when (natureza) {
                        Natureza.ECONOMIA -> colors.categoryFixed
                        else -> colors.categoryVariable
                    },
                    CircleShape,
                ),
            )
        }
        Text(descricao, style = SaldoTheme.type.row, color = colors.label)
        MoneyText(
            centavos = centavos,
            modifier = Modifier.weight(1f),
            style = SaldoTheme.type.row, color = colors.secondaryLabel,
            formato = FormatoMoney.ASSINADO, textAlign = TextAlign.End,
        )
    }
}
```

- [x] **Step 5: Fix the imports, add the weekday formatter, drop the dead constants**

Add these imports to `LedgerScreen.kt`:

```kotlin
import androidx.compose.foundation.border
import com.scholze.saldo.ui.components.DiaBadge
import com.scholze.saldo.ui.components.FiltroChips
import com.scholze.saldo.ui.components.IconeRedondo
import com.scholze.saldo.ui.components.SaldoPill
import com.scholze.saldo.ui.components.SaldoTopBar
```

Near the other formatters at the top of the file (lines 78–83), add:

```kotlin
private val diaSemanaCurto = DateTimeFormatter.ofPattern("EEE", ptBr)
```

and delete these two now-unused constants:

```kotlin
private val DAY_COLUMN = 34.dp
private val SALDO_COLUMN = 118.dp
```

Remove the `HairlineDivider` import and every remaining `HairlineDivider()` call in this file (the one after each `DayRow` in the `itemsIndexed` block, and the one in `MonthNavBar` which is gone with it) — the M3 rows separate by their own 4dp vertical padding.

- [x] **Step 6: Extend the grid test**

In `app/src/androidTest/kotlin/com/scholze/saldo/ui/ledger/LedgerDayGridTest.kt`, add these two tests inside the class:

```kotlin
    /**
     * A coluna morreu, a escala de calor não: a pill do dia mais rico e a do mais pobre
     * do mesmo mês têm de ser níveis diferentes. O teste bate no que dá para observar
     * — os dois valores continuam legíveis e distintos — porque a cor de fundo de um
     * Box não é exposta na árvore de semântica.
     */
    @Test
    fun aPillCarregaOSaldoDeCadaDia() {
        montar()
        rule.onNodeWithTag(tagSaldoDoDia(3)).assertTextEquals("2.600,00")
        rule.onNodeWithTag(tagSaldoDoDia(6)).assertTextEquals("2.110,10")
    }

    /** O dia da semana entrou junto com o badge — 06/07/2026 é uma segunda. */
    @Test
    fun oBadgeDoDiaMostraODiaDaSemana() {
        montar()
        rule.onNodeWithText("seg").assertExists()
    }
```

Add this import with the others:

```kotlin
import androidx.compose.ui.test.assertExists
```

- [x] **Step 7: Run the ledger tests**

```bash
mise exec -- ./gradlew connectedDebugAndroidTest --tests '*LedgerDayGridTest*' --tests '*LedgerScreenTest*' --tests '*SwipeDeleteTest*'
```

Expected: PASS. The pre-existing `diaMostraDescricaoDoItemESaldoNaColuna` and `colunaDeSaldoDoDiaMascaraQuandoOculto` must pass **unchanged** — that is the point of moving `tagSaldoDoDia` onto the pill.

- [x] **Step 8: Run everything green**

```bash
mise run test
mise run test-device
mise exec -- ./gradlew lintDebug
```

Expected: JVM 144, instrumented 76, lint 0 errors.

> **Desvios da Task 4 (registrados na execucao, 2026-08-18):**
>
> 1. **`SaldoPill` nao carregava texto no no do testTag.** O `modifier` (com a tag) fica no
>    `Box`, e o `MoneyText` e filho — `assertTextEquals` batia num no vazio e QUATRO testes
>    ja existentes quebraram junto. Resolvido com `semantics(mergeDescendants = true)` na
>    pill, que tambem e o certo para leitor de tela: a pill e um elemento, nao uma moldura
>    mais um numero soltos. **Isto e da Task 2** — o `SaldoPill` do plano nasceu assim.
>
> 2. **O dia da semana vinha com ponto.** `DateTimeFormatter.ofPattern("EEE", ptBr)` rende
>    `"qua."`, `"seg."` — CLDR pt-BR poe o ponto. O `oBadgeDoDiaMostraODiaDaSemana` do plano
>    procura `"seg"` exato e falhava. O arquivo ja usa `.removeSuffix(".")` em toda data
>    formatada (`diaCurto`), entao o badge passou a fazer o mesmo: corrige a aparencia
>    (`"qua."` num badge de 44dp fica errado) e o teste passa como escrito.
>
> 3. **O "excluir" do swipe usa `inverseOnSurface`, nao `Color.White`.** Era o quinto item
>    da tabela de contraste da Task 2 — 2,46:1 no escuro. `categoryVariable` inverte de
>    claridade entre os esquemas igual ao tint, e `inverseOnSurface` tem exatamente a
>    polaridade certa: 5,44:1 no claro, 5,32:1 no escuro. (E um uso dos papeis `inverse*`
>    que a Task 1 salvou de serem apagados.) A pilula "hoje" tambem trocou branco por
>    `onPrimary`.
>
> 4. **O chip de tag saiu do `segmentedTrack`.** Ficava colado nos `FiltroChips` novos
>    pintado com o cinza translucido do HIG, em outra lingua visual. Virou
>    `secondaryContainer` com cantos de pilula. A Task 8 ia ter de fazer isso de qualquer
>    forma, ao apagar os tres campos aposentados.
>
> Verificado por captura real (o `captureToImage` de um teste temporario, ja removido): as
> linhas, o badge com dia-da-semana, a rampa de calor na pill e a linha de hoje com borda
> `tint` e badge preenchido. Screenshots em `.superpowers/sdd/shots/rb-task4-*.png`.

- [ ] **Step 9: Commit**

```bash
git add app/src/main/kotlin/com/scholze/saldo/ui/ledger/ app/src/androidTest/kotlin/com/scholze/saldo/ui/ledger/
git commit -m "feat: ledger — linhas do M3 com badge do dia e pill de saldo no lugar da grade"
```

---

### Task 5: Totais — *mês* and *tendência*

**Files:**
- Modify: `app/src/main/kotlin/com/scholze/saldo/ui/totais/TotaisScreen.kt`
- Modify: `app/src/main/kotlin/com/scholze/saldo/ui/totais/SegmentoMes.kt`
- Modify: `app/src/main/kotlin/com/scholze/saldo/ui/totais/SegmentoTendencia.kt`
- Test: `app/src/androidTest/kotlin/com/scholze/saldo/ui/totais/TotaisContentTest.kt` (edit)

**Interfaces:**
- Consumes: `SaldoTopBar`, `FiltroChips`, `InsetGroup`/`InsetRow` (re-skinned in Task 2), the four chart composables from insights-1 Task 3 — `SegmentedBar(shares, cores, modifier, altura)`, `TrendChart(pontos, mesDestacado, onMes, modifier, altura)`, `WeekdayBars(porDia, destaque, modifier, altura)`, `ReservaLine(valores, modifier, altura)` — all unchanged.
- Produces: `TAG_SEGMENTO_TOTAIS` keeps its value and moves onto the chip row. `SegmentoTotais` is unchanged (Task 6 adds `A_CAMINHO`).

- [x] **Step 1: Swap the top bar and the segmented control**

In `TotaisScreen.kt`, replace the nav `Row` and the `HairlineDivider()` after it (lines 80–99) with:

```kotlin
        SaldoTopBar(
            titulo = "totais · " + state.mesAtual.rotuloCurto(),
            onAnterior = onMesAnterior,
            onProximo = onProximoMes,
        )
```

and replace the `SegmentedControl(...)` call (lines 132–137) with:

```kotlin
            FiltroChips(
                opcoes = SegmentoTotais.entries.map { it.rotulo },
                selecionado = segmento.ordinal,
                onSelect = { segmento = SegmentoTotais.entries[it] },
                modifier = Modifier.testTag(TAG_SEGMENTO_TOTAIS),
            )
```

- [x] **Step 2: Promote the performance line into a hero card**

Replace the `Column` holding `"performance"` (lines 115–130) with:

```kotlin
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp))
                    .background(colors.primaryContainer)
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    if (t.sobrouCentavos >= 0) "sobrou dinheiro" else "faltou dinheiro",
                    style = SaldoTheme.type.footnote, color = colors.onPrimaryContainer.copy(alpha = 0.72f),
                )
                MoneyText(
                    centavos = t.sobrouCentavos,
                    style = SaldoTheme.type.navTitle,
                    color = colors.onPrimaryContainer,
                    formato = FormatoMoney.ASSINADO_COM_SIMBOLO,
                )
            }
```

- [x] **Step 3: Drop every `HairlineDivider` from the totais package**

In `TotaisScreen.kt`, `SegmentoMes.kt` and `SegmentoTendencia.kt`, delete every `HairlineDivider(...)` call and the `HairlineDivider` import. The re-skinned `InsetGroup` already spaces its rows.

- [x] **Step 4: Lowercase the three section headers**

In `SegmentoMes.kt`, change the three header strings:

```kotlin
            Text("para onde foi", Modifier.weight(1f), style = SaldoTheme.type.sectionHeader, color = colors.label)
```

```kotlin
            Text("maiores gastos", style = SaldoTheme.type.sectionHeader, color = colors.label)
```

```kotlin
            Text("padrões", style = SaldoTheme.type.sectionHeader, color = colors.label)
```

Note the color also moves from `secondaryLabel` to `label`: at 15sp bold in sentence case these are card titles, and a muted card title reads as disabled.

- [x] **Step 5: Update the three assertions that named them**

In `TotaisContentTest.kt`, replace every occurrence of the old strings:

```bash
sed -i 's/"PARA ONDE FOI"/"para onde foi"/g; s/"MAIORES GASTOS"/"maiores gastos"/g; s/"PADRÕES"/"padrões"/g' \
  app/src/androidTest/kotlin/com/scholze/saldo/ui/totais/TotaisContentTest.kt
```

- [x] **Step 6: Chain `performScrollTo()` on the mid-fold assertions**

The rows moved down — `InsetGroup` grew from 10dp corners to 28dp with 6dp of internal padding, and the hero card is taller than the old two-line performance block. The scroll fragility already flagged in insights-1 Tasks 4 and 5 now bites for real.

In `TotaisContentTest.kt`, every assertion or click on a node below the first fold must chain `performScrollTo()` first:

```kotlin
rule.onNodeWithText("maiores gastos").performScrollTo().assertIsDisplayed()
```

Apply the same to `mercado`, `−489,90`, `padrões` and `"sábado é o dia mais caro"`.

- [x] **Step 7: Run the totais tests**

```bash
mise exec -- ./gradlew connectedDebugAndroidTest --tests '*TotaisContentTest*'
```

Expected: PASS. If a node is still not found, it is genuinely off-screen — scroll it, do not delete the assertion.

- [x] **Step 8: Run everything green**

```bash
mise run test
mise run test-device
mise exec -- ./gradlew lintDebug
```

Expected: JVM 144, instrumented 76, lint 0 errors.

> **Desvio da Task 5 (registrado na execucao, 2026-08-18):**
>
> **O Step 2 muda uma string visivel que as restricoes globais diziam nao mudar.** O card
> heroi passou o `sobrouCentavos` de `FormatoMoney.ASSINADO` para `ASSINADO_COM_SIMBOLO`,
> ou seja `+5.440,00` virou `+R$ 5.440,00`. As restricoes globais deste plano dizem que a
> UNICA excecao seriam os tres cabecalhos em caixa alta. O `mostraPerformanceEBlocos`
> quebrou nisso.
>
> Mantido o formato do plano — um numero heroi com simbolo casa com o hero do ledger, e a
> insights-1 ja tinha levantado um Important justamente sobre a mesma metrica aparecer em
> dois formatos em segmentos vizinhos. Foi a asserçao que se ajustou. No teste de
> privacidade ficaram as DUAS formas (`+R$ 5.440,00` e `+5.440,00`): a mascara nao pode
> deixar passar nenhuma das duas.
>
> O Step 6 ja estava quase todo feito — a rodada de correcao da insights-1 Task 5 tinha
> encadeado `performScrollTo()` em quase tudo. Faltava so o `"para onde foi"`, que o card
> heroi mais alto empurrou para baixo da primeira dobra.
>
> Conferido por captura: `.superpowers/sdd/shots/rb-task5-totais*.png`.

- [ ] **Step 9: Commit**

```bash
git add app/src/main/kotlin/com/scholze/saldo/ui/totais/ app/src/androidTest/kotlin/com/scholze/saldo/ui/totais/
git commit -m "feat: totais — cards tonais, chips e cabeçalhos de seção em caixa baixa"
```

---

### Task 6: The *a caminho* segment

This supersedes insights-1's `i1-task-6-brief.md`. The behaviour and copy are that brief's; the components are this plan's. Read `docs/superpowers/specs/2026-08-17-saldo-insights-1-design.md` for the *a caminho* semantics before starting.

**Files:**
- Create: `app/src/main/kotlin/com/scholze/saldo/ui/totais/SegmentoACaminho.kt`
- Modify: `app/src/main/kotlin/com/scholze/saldo/ui/totais/TotaisScreen.kt` (enum + `when` branch)
- Modify: `app/src/main/kotlin/com/scholze/saldo/ui/totais/TotaisViewModel.kt` (state gains `aCaminho`)
- Test: `app/src/androidTest/kotlin/com/scholze/saldo/ui/totais/TotaisContentTest.kt` (extend)

**Interfaces:**
- Consumes: `InsightsEngine.aCaminho(input, mes)` — already built and tested in insights-1 Task 2, returning `ACaminho(itens: List<ItemDia>, totalCentavos: Long, mesEncerrado: Boolean)`. Verify the exact shape with `grep -n "aCaminho" app/src/main/kotlin/com/scholze/saldo/domain/InsightsEngine.kt` before writing against it.
- Produces: `SegmentoTotais` gains `A_CAMINHO("a caminho")`; `TotaisUiState` gains `aCaminho: ACaminho?`.

- [x] **Step 1: Confirm the engine's shape**

```bash
grep -n "aCaminho\|class ACaminho\|data class ACaminho" app/src/main/kotlin/com/scholze/saldo/domain/InsightsEngine.kt
```

Expected: the function and its return type. **Write the composable against what this prints, not against this plan's recollection of it.**

- [x] **Step 2: Add the enum case and watch it fail to compile**

In `TotaisScreen.kt`:

```kotlin
enum class SegmentoTotais(val rotulo: String) { MES("mês"), TENDENCIA("tendência"), A_CAMINHO("a caminho") }
```

```bash
mise exec -- ./gradlew compileDebugKotlin
```

Expected: FAIL — `'when' expression must be exhaustive, add necessary 'A_CAMINHO' branch`. The `when` in `TotaisContent` has no `else`, which is exactly why this is safe.

- [x] **Step 3: Write the segment**

Create `app/src/main/kotlin/com/scholze/saldo/ui/totais/SegmentoACaminho.kt` with a header card (total still to come, or the `mesEncerrado` empty state), then one `InsetGroup` listing the items by day. Use `InsetRow` for each line and `MoneyText(formato = FormatoMoney.ASSINADO)` for every value. Copy, verbatim from the spec: `"a caminho até 31 ago"` for the header, `"mês encerrado"` when `mesEncerrado`, `"nada a caminho"` when the list is empty and the month is open.

- [x] **Step 4: Wire the branch, the ViewModel and the tests**

Add the `SegmentoTotais.A_CAMINHO -> state.aCaminho?.let { SegmentoACaminho(it, state.mesAtual) }` branch; add `aCaminho` to `TotaisUiState` and compute it inside the **existing** `flowOn` (do not add a second one). Extend `TotaisContentTest` with: the chip switching to the segment, the empty state on a closed month, and one masked value under `LocalPrivacy`.

> **Desvios da Task 6 (registrados na execucao, 2026-08-18):**
>
> 1. **A forma do `ACaminho` no plano estava errada** — e o proprio plano mandava conferir.
>    Nao e `ACaminho(itens, totalCentavos, mesEncerrado)`: e
>    `ACaminho(saemCentavos, entramCentavos, itens, mesEncerrado)` com `itens: List<ItemFuturo>`.
>    Escrito contra o que o `grep` do Step 1 imprimiu, como o plano manda.
>
> 2. **A copy do Step 3 nao era a da spec, apesar de dizer "verbatim from the spec".** O plano
>    lista `"a caminho ate 31 ago"` e `"nada a caminho"`; a spec e o `i1-task-6-brief.md` dizem
>    `"ainda saem"` + `"ate 31 jul"` e `"nada agendado ate o fim do mes"`. Valeu a spec/brief,
>    que e o que a instrucao apontava e o que os testes do brief assertam.
>
> 3. **O cabecalho de secao "A CAMINHO" do brief foi removido, nao so minusculado.** Com a
>    regra da Task 5 (cabecalhos em caixa baixa) ele viraria `"a caminho"` — exatamente o
>    texto do chip logo acima, a poucos dp. Alem de repetitivo, daria DOIS nos com o mesmo
>    texto e o `onNodeWithText("a caminho").performClick()` que o brief usa para trocar de
>    segmento passaria a falhar com "expected exactly 1 node".
>
> 4. **O bloco "ainda saem" virou card tonal**, como os herois do mes e do ledger, em vez de
>    linhas dentro de um `InsetGroup`; e os `HairlineDivider` do brief sairam (a Task 5 ja os
>    tinha tirado do pacote inteiro).
>
> A linha "recorrencias ›" e renderizada aqui mas fica **inerte ate a Task 7** —
> `onAbrirRecorrencias` mantem o default `{}` em `TotaisScreen`, e o teste a exercita direto
> no `TotaisContent`. Captura: `.superpowers/sdd/shots/rb-task6-acaminho*.png`.
>
> **Para o olho do usuario na Task 9:** o segmento empilha DOIS cards verdes grandes (o heroi
> de performance da aba e o "ainda saem" do segmento). Funciona, mas e bastante verde junto.

- [x] **Step 5: Run everything green and commit**

```bash
mise run test
mise run test-device
mise exec -- ./gradlew lintDebug
git add app/src/main/kotlin/com/scholze/saldo/ui/totais/ app/src/androidTest/kotlin/com/scholze/saldo/ui/totais/
git commit -m "feat: totais — segmento a caminho"
```

---

### Task 7: `RecorrenciasScreen` + `RecorrenciasViewModel`

This supersedes insights-1's `i1-task-7-brief.md` — same behaviour, this plan's components. Follow that brief for the `ativas` / `encerradas` split and the copy; build the lists from `InsetGroup`/`InsetRow` and the header from `SaldoTopBar`.

- [x] **Step 1: Read the superseded brief for behaviour**

```bash
sed -n '1,80p' .superpowers/sdd/i1-task-7-brief.md
```

- [x] **Step 2–5:** implement, test, run green, commit as:


> **Desvios da Task 7 (registrados na execucao, 2026-08-18):**
>
> 1. **O cabecalho NAO usa `SaldoTopBar`,** apesar da instrucao desta task. Aquele componente
>    existe para navegar meses: sao duas setas com `contentDescription` "mes anterior"/"proximo
>    mes", e esta tela nao navega mes nenhum — so volta. Ficou a MESMA estrutura (linha de acao
>    em cima, titulo grande e a esquerda embaixo), com "‹ totais" no lugar das setas, que e o
>    texto que o teste do brief clica.
>
> 2. **O `voltaParaTotais` do brief esperava `"A CAMINHO"` de volta em totais** — cabecalho que
>    a Task 6 removeu. Passou a esperar o chip `"a caminho"`, que sempre existe na aba.
>
> 3. **`ptBr`/`mesAbrev`/`rotuloCurto()` do brief nao foram redeclarados:** `Formatos.kt` (do
>    mesmo pacote, criado na insights-1 Task 5) ja expoe `rotuloCurto()` como `internal`, e
>    redeclarar daria erro de redeclaracao. Mesma razao pela qual `fixas(n)` vem da Task 6.
>
> 4. Os `HairlineDivider` do brief sairam (a Task 5 ja os tinha tirado do pacote) e os
>    cabecalhos seguem a regra da Task 5: `sectionHeader` na cor `label`.
>
> A rota de edicao virou um `abrirMovimentacao` unico em `SaldoApp`, usado pelo ledger e pela
> tela nova — mesma guarda de `id != 0`, uma implementacao so.
> Captura: `.superpowers/sdd/shots/rb-task7-recorrencias*.png`.

```bash
git commit -m "feat: recorrências — visão geral de ativas e encerradas"
```

---

### Task 8: The remaining screens, then the demolition

**Files:**
- Modify: `ui/tags/TagsScreen.kt`, `ui/mais/MaisScreen.kt`, `ui/entry/NewEntrySheet.kt`, `ui/entry/AmountKeypadScreen.kt`
- Modify: `ui/components/Components.kt` (delete `HairlineDivider`, `SegmentedControl`)
- Modify: `ui/theme/Color.kt` (delete `separator`, `segmentedTrack`, `segmentedThumb`)
- Test: `app/src/androidTest/kotlin/com/scholze/saldo/ui/mais/LembretesScreenTest.kt` (fix the sibling walk)

- [x] **Step 1: Strip `HairlineDivider` from `tags` and `mais`**

Both screens are built almost entirely from `InsetGroup`/`InsetRow`, which Task 2 already re-skinned. Delete every `HairlineDivider(...)` call and the import from both files. Nothing else in either screen changes.

- [x] **Step 2: Migrate the entry sheet and the keypad**

`NewEntrySheet`: corner radius to 28dp, `FilledActionButton` already re-skinned. `AmountKeypadScreen`: keys become 72dp circles on `surface` with `label` digits; the confirm button is already the shared one.

- [x] **Step 3: Fix `LembretesScreenTest`'s sibling walk**

`LembretesScreenTest.kt:54-55` finds each switch as "the sibling right after the label" because `InsetGroup`'s clip made the three switches share a semantic parent. The re-skinned group has the same shape, so this **should** still hold — run it first and only rewrite it if it fails:

```bash
mise exec -- ./gradlew connectedDebugAndroidTest --tests '*LembretesScreenTest*'
```

If it fails, replace the sibling walk with `onNodeWithText(<rótulo>).onParent().onChildren().filter(isToggleable())`.

- [x] **Step 4: Delete the two dead components**

```bash
grep -rn "HairlineDivider\|SegmentedControl" app/src/main app/src/androidTest app/src/test
```

Expected: no hits outside `Components.kt` itself. Then delete both composables from `Components.kt`, along with the now-unused imports (`animateDpAsState`, `spring`, `Spring`, `BoxWithConstraints`, `offset`, `shadow`, `LocalDensity`, `TextAlign`).

- [x] **Step 5: Delete the three retired colour fields**

```bash
grep -rn "\.separator\|segmentedTrack\|segmentedThumb" app/src/main
```

Expected: only `Theme.kt`'s `outlineVariant = colors.separator`. Keep `separator` (it has a real M3 job as the chip border and `outlineVariant`) and delete only `segmentedTrack` and `segmentedThumb` from `SaldoColors` and both schemes. Update `SaldoColorsTest` if it named them.

> **Desvios da Task 8 (registrados na execucao, 2026-08-18):**
>
> 1. **`LembretesScreen.kt` tambem usava `HairlineDivider`** (6 chamadas) e nao esta na lista
>    de arquivos desta task, que so cita `tags` e `mais`. Sem ele o Step 4 nunca daria "no hits".
>
> 2. **O sibling walk do `LembretesScreenTest` sobreviveu**, como o Step 3 esperava — rodado
>    antes de qualquer edicao, passou. Nada foi reescrito.
>
> 3. **`NewEntrySheet` precisou de mais do que "corner radius to 28dp":** ele era o ultimo
>    usuario do `SegmentedControl` (entrada|saida -> `FiltroChips`) e um dos dois ultimos do
>    `segmentedTrack` (o disco da tag e o `TagPill` -> `secondaryContainer`). Sem isso os
>    Steps 4 e 5 nao compilariam. O chip de natureza tambem trocou branco fixo por
>    `onPrimary` — era o quarto item da tabela de contraste da Task 2.
>
> 4. **O `+` do widget foi corrigido aqui**, apesar de nenhuma task do plano abrir esse arquivo.
>    Era o ultimo item da tabela de contraste e a unica regressao desta branch que ficaria
>    visivel ao usuario final: `CoresWidget.branco` sobre `CoresWidget.tint` da 1,68:1 no
>    escuro. Virou `CoresWidget.sobreTint` (branco no dia, `#003919` na noite) — o mesmo papel
>    que o app chama de `onPrimary`, repetido porque o Glance nao enxerga o
>    `MaterialTheme.colorScheme`. **Continua sem teste**: nada cobre o widget.
>
> Lint caiu de 14 para 13 avisos: o `UseOfNonLambdaOffsetOverload` morreu junto com o
> `SegmentedControl`. Nova linha de base para a Task 9: **0 erros / 13 avisos / 1 hint**.

- [x] **Step 6: Run everything green and commit**

```bash
mise run test
mise run test-device
mise exec -- ./gradlew lintDebug
git add app/src/main/kotlin/com/scholze/saldo/ui/ app/src/androidTest/kotlin/com/scholze/saldo/ui/
git commit -m "feat: tags, mais e lançamento no M3; segmented control e hairline aposentados"
```

---

### Task 9: Emulator pass, README, spec sync

This supersedes insights-1's `i1-task-8-brief.md`.

- [ ] **Step 1: Full instrumented run on a clean install**

```bash
mise exec -- ./gradlew uninstallDebug
mise run install
mise run test-device
```

Expected: the whole suite green on one run, not accumulated across `--tests` filters.

- [ ] **Step 2: Walk both themes by hand**

Every tab, in light and dark, at font scale 1.0 and 1.5: ledger (with and without a tag filter, on an empty month), totais (all three segments), recorrências, tags, mais, lembretes, the entry sheet, the keypad, and the onboarding keypad on first run. Capture one screenshot per screen per theme into `docs/superpowers/screenshots/2026-08-18-redesign-b/`.

Watch specifically for: the delta chip at font scale 1.5 (it was fixed-width once and regressed), the balance pill with a masked value (`R$ •••••` is wider than most figures), and the FAB's docked overlap against the three-button navigation bar.

- [ ] **Step 3: Update the README and the memory of the old canvas**

The README says nothing about the visual direction, so it needs no change unless Step 2 found a behaviour difference. Update `docs/superpowers/specs/2026-08-17-saldo-insights-1-design.md` only where it describes the segmented control or the saldo column.

- [ ] **Step 4: Final commit and branch handoff**

```bash
git add docs/
git commit -m "docs: redesign B — screenshots dos dois temas e sync do spec"
```

Then use `superpowers:finishing-a-development-branch` to decide how `redesign-b` and the unmerged `insights-1` reach `main`.

---

## Self-review notes

- **Coverage.** Every field on the canvas's token sheet has a home: colours and type in Task 1, the shape scale and five components in Task 2, the nav bar in Task 3. The sheet's "o que isso custa em código" panel is Task 8.
- **The sheet is now wrong in one place.** It lists `InsetGroup` and `InsetRow` among the components that disappear. They do not — Tasks 2 and 8 re-skin them in place, which is what makes `tags` and `mais` nearly free. Correct the canvas panel when convenient.
- **Type consistency.** `nivelDeCalor` returns `Int` and `SaldoPill` takes `nivel: Int`; `heatTint` returning `Color` exists nowhere after Task 4. `SaldoTopBar`'s `acao` slot is the only route for the privacy eye, and only the ledger passes one.
- **Known thin spots.** Tasks 6 and 7 are deliberately lighter than 1–5: their behaviour, copy and tests are already fully specified in `i1-task-6-brief.md` / `i1-task-7-brief.md` and in the insights-1 spec, and restating that here would fork the source of truth. Both tasks open by reading the superseded brief. Task 8's keypad step names sizes but not full code — the keypad is a 12-cell grid whose structure does not change, only its cell shape.
