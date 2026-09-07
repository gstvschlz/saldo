# saldo — guardado Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Do que entrou no mês, quanto foi guardado — uma pill "guardou N%" no hero (board e lista) e uma linha no widget de saldo, calculadas por uma função só.

**Architecture:** `ProjectionEngine.taxaGuardada(entradas, economia)` é a única conta; `MesLedger` carrega o resultado para o hero e para o widget, e `InsightsEngine.tendencia` passa a usá-la. O segmento de `totais` sobe da tela para o `TotaisViewModel` (com `SavedStateHandle`) para a pill poder abrir a tendência. Nenhum schema, nenhum ajuste, nenhuma dependência.

**Tech Stack:** Kotlin 2.2, Compose + M3, Glance, JUnit4.

## Global Constraints

- **Spec:** `docs/superpowers/specs/2026-09-07-saldo-guardado-design.md`. Onde plano e spec discordarem, o plano ganha.
- **Baseline:** branch `guardado-1`, criada de `main` em `de76c01`.
- `taxaGuardada` arredonda (`Math.round`), devolve `null` com entradas ≤ 0, e pode passar de 100.
- A pill do hero **não** é mascarada pela privacidade; a linha do widget **é** ("guardou ••%").
- Copy em pt-BR, minúsculas: "guardou 20%", "guardou 20% do que entrou" (descrição).
- Cada task termina verde: `mise run test`, `mise exec -- ./gradlew lintDebug`; as tasks com Android acrescentam as classes instrumentadas indicadas. Gradle sempre pelo Bash com a saída redirecionada para arquivo, nunca com pipe (pendura). `connectedDebugAndroidTest` filtra com `-Pandroid.testInstrumentationRunnerArguments.class=…`.
- Stage com config padrão, commit com `git -c core.autocrlf=false commit -F <msg>`; conferir `git show --stat HEAD`.

## File structure

| File | Responsibility |
|---|---|
| `domain/ProjectionEngine.kt` (modify) | `taxaGuardada(...)`; `MesLedger.taxaGuardada`; `mes()` preenche |
| `domain/InsightsEngine.kt` (modify) | `tendencia` usa `taxaGuardada` |
| `ui/totais/TotaisViewModel.kt` (modify) | `SegmentoTotais` mora aqui; `segmento` no `SavedStateHandle`; `selecionarSegmento` |
| `ui/totais/TotaisScreen.kt` (modify) | `TotaisContent` recebe `segmento`/`onSegmento` em vez do `rememberSaveable` |
| `ui/ledger/LedgerScreen.kt` (modify) | `BalanceHero(mes, onTogglePrivacidade, onVerGuardado)` com a pill; `TAG_PILL_GUARDADO` |
| `ui/board/BoardScreen.kt` (modify) | repassa `onVerGuardado` |
| `ui/SaldoApp.kt` (modify) | `onVerGuardado` → totais no mês, segmento tendência |
| `widget/WidgetEstado.kt`, `widget/SaldoWidget.kt`, `widget/SaldoWidgetContent.kt` (modify) | `Pronto.taxaGuardada`; a linha "guardou N%"; `TAG_WIDGET_GUARDADO` |

---

### Task 1: `taxaGuardada` — a conta, em `MesLedger` e na tendência

**Files:**
- Modify: `app/src/main/kotlin/com/scholze/saldo/domain/ProjectionEngine.kt`
- Modify: `app/src/main/kotlin/com/scholze/saldo/domain/InsightsEngine.kt:222-236`
- Test: `app/src/test/kotlin/com/scholze/saldo/domain/ProjectionEngineTest.kt` (append), `InsightsEngineTest.kt` (ajustar)

**Interfaces:**
- Produces: `ProjectionEngine.taxaGuardada(entradasCentavos: Long, economiaCentavos: Long): Int?`; `MesLedger.taxaGuardada: Int?`.
- Consumes: nada novo.

- [ ] **Step 1: Write the failing tests** (append em `ProjectionEngineTest`; use o helper de `LedgerInput` que o arquivo já tem, ou construa um inline como os outros testes do arquivo)

```kotlin
    @Test
    fun `taxaGuardada e nula sem entrada`() {
        assertNull(ProjectionEngine.taxaGuardada(0, 200_00))
        assertNull(ProjectionEngine.taxaGuardada(-10, 200_00))
    }

    @Test
    fun `taxaGuardada arredonda e passa de cem`() {
        assertEquals(20, ProjectionEngine.taxaGuardada(1_000_00, 200_00))
        assertEquals(21, ProjectionEngine.taxaGuardada(1_000_00, 205_00))   // 20,5 → 21
        assertEquals(120, ProjectionEngine.taxaGuardada(1_000_00, 1_200_00))
        assertEquals(0, ProjectionEngine.taxaGuardada(1_000_00, 0))
    }

    @Test
    fun `mes carrega a taxa guardada do proprio mes`() {
        val set = YearMonth.of(2026, 9)
        val input = LedgerInput(
            saldoInicialCentavos = 0, saldoInicialData = LocalDate.parse("2026-01-01"),
            movimentacoes = listOf(
                Movimentacao(id = 1, descricao = "salário", valorCentavos = 5_000_00, data = LocalDate.parse("2026-09-05"), natureza = Natureza.DIARIO),
                Movimentacao(id = 2, descricao = "cdb", valorCentavos = -1_000_00, data = LocalDate.parse("2026-09-06"), natureza = Natureza.ECONOMIA),
                Movimentacao(id = 3, descricao = "mercado", valorCentavos = -300_00, data = LocalDate.parse("2026-09-07"), natureza = Natureza.DIARIO),
                Movimentacao(id = 4, descricao = "cdb", valorCentavos = -500_00, data = LocalDate.parse("2026-08-06"), natureza = Natureza.ECONOMIA),
            ),
            recorrencias = emptyList(), mesesMaterializados = emptySet(), cartao = CartaoConfig(),
            hoje = LocalDate.parse("2026-09-07"),
        )
        assertEquals(20, ProjectionEngine.mes(input, set, FiltroLedger.TODAS).taxaGuardada)
        // agosto: economia sem entrada → nulo, não 0
        assertNull(ProjectionEngine.mes(input, YearMonth.of(2026, 8), FiltroLedger.TODAS).taxaGuardada)
    }
```

Em `InsightsEngineTest`, o teste que espera `taxaPoupanca` truncado (linhas ~255-270: `assertEquals(20, pMai.taxaPoupanca)` etc.) continua valendo se as frações forem exatas; se algum caso tiver fração ≥ 0,5, ajuste o esperado para o arredondado e diga qual.

- [ ] **Step 2: Run to verify it fails**

Run: `mise exec -- ./gradlew :app:testDebugUnitTest --tests "com.scholze.saldo.domain.ProjectionEngineTest"`
Expected: compilação falha — `taxaGuardada`.

- [ ] **Step 3: Implementation**

`MesLedger` ganha, depois de `projetadoEm`:

```kotlin
    /** Quanto do que entrou no mês foi para economia, em %; `null` num mês sem entrada. */
    val taxaGuardada: Int? = null,
```

Em `ProjectionEngine`, ao lado de `mes()`:

```kotlin
    /**
     * A leitura "guardou N%": as saídas de natureza economia do mês sobre tudo que entrou.
     * Arredonda; sem entrada não há proporção (nulo, não zero); guardar mais do que entrou
     * — de saldo antigo — passa de cem, porque é verdade. É a única conta desta razão: a
     * tendência e o hero leem daqui e não podem discordar por um por cento.
     */
    fun taxaGuardada(entradasCentavos: Long, economiaCentavos: Long): Int? {
        if (entradasCentavos <= 0) return null
        return Math.round(economiaCentavos * 100.0 / entradasCentavos).toInt()
    }
```

No `mes(...)` privado, antes do `return MesLedger(...)`:

```kotlin
        // Sempre sobre o mês inteiro (sem filtro): a pill do hero não muda com os chips.
        val doMes = efetivas.filter { YearMonth.from(it.data) == mes }
        val entradas = doMes.filter { it.natureza != Natureza.CARTAO && it.valorCentavos > 0 }.sumOf { it.valorCentavos }
        val economia = -doMes.filter { it.natureza == Natureza.ECONOMIA && it.valorCentavos < 0 }.sumOf { it.valorCentavos }
```

e `taxaGuardada = taxaGuardada(entradas, economia),` no construtor. (A definição de `entradas` é a mesma de `totais()`: positivos que não são cartão.)

Em `InsightsEngine.tendencia`: `taxaPoupanca = ProjectionEngine.taxaGuardada(t.entradasCentavos, economia),`.

- [ ] **Step 4: Run tests**

Run: `mise exec -- ./gradlew :app:testDebugUnitTest --tests "com.scholze.saldo.domain.*"` — PASS. Depois `mise run test` e `lintDebug`.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/scholze/saldo/domain app/src/test/kotlin/com/scholze/saldo/domain
git commit -m "feat: a taxa guardada — uma conta só para o hero, o widget e a tendência"
```

---

### Task 2: O segmento de totais sobe para o ViewModel

**Files:**
- Modify: `app/src/main/kotlin/com/scholze/saldo/ui/totais/TotaisViewModel.kt`
- Modify: `app/src/main/kotlin/com/scholze/saldo/ui/totais/TotaisScreen.kt:48-56,64-71,130-138,172-186`
- Test: `app/src/test/kotlin/com/scholze/saldo/ui/totais/TotaisViewModelTest.kt` (append), `app/src/androidTest/kotlin/com/scholze/saldo/ui/totais/TotaisContentTest.kt` (`montar`)

**Interfaces:**
- Produces: `enum class SegmentoTotais` em `TotaisViewModel.kt`; `TotaisViewModel.segmento: StateFlow<SegmentoTotais>`, `selecionarSegmento(s)`; `TotaisContent(..., segmento: SegmentoTotais, onSegmento: (SegmentoTotais) -> Unit, ...)`.
- Consumes: `SavedStateHandle` já no ViewModel.

- [ ] **Step 1: Failing JVM test** (append em `TotaisViewModelTest`)

```kotlin
    @Test
    fun oSegmentoSobreviveNoSavedState() {
        val saved = SavedStateHandle()
        val vm = TotaisViewModel(RepositorioFixo(input), saved)
        vm.selecionarSegmento(SegmentoTotais.TENDENCIA)
        val outro = TotaisViewModel(RepositorioFixo(input), saved)
        assertEquals(SegmentoTotais.TENDENCIA, outro.segmento.value)
    }
```

- [ ] **Step 2: Run to verify it fails** — `Unresolved reference: selecionarSegmento`.

- [ ] **Step 3: Implementation**

Mova `enum class SegmentoTotais(val rotulo: String) { … }` (com o KDoc) de `TotaisScreen.kt` para `TotaisViewModel.kt`, mesmo pacote — nenhum import muda. No ViewModel:

```kotlin
    private val _segmento = MutableStateFlow(
        savedState.get<String>(KEY_SEGMENTO)?.let { s -> SegmentoTotais.entries.firstOrNull { it.name == s } } ?: SegmentoTotais.MES,
    )

    /** O segmento visto; estado de navegação, como o mês — sobrevive à morte do processo. */
    val segmento: StateFlow<SegmentoTotais> = _segmento

    fun selecionarSegmento(s: SegmentoTotais) {
        _segmento.value = s
        savedState[KEY_SEGMENTO] = s.name
    }
```

com `private const val KEY_SEGMENTO = "totais.segmento"` no companion. Em `TotaisScreen`: `val segmento by vm.segmento.collectAsState()` e passe `segmento = segmento, onSegmento = vm::selecionarSegmento` a `TotaisContent`, que ganha os dois parâmetros (sem default) no lugar do `var segmento by rememberSaveable`; `onSelect = { onSegmento(SegmentoTotais.entries[it]) }`; o `segmento = SegmentoTotais.MES` do `onMes` da tendência vira `onSegmento(SegmentoTotais.MES)`.

`TotaisContentTest.montar`: dentro do `setContent`, `var segmento by rememberSaveable { mutableStateOf(SegmentoTotais.MES) }` e passe `segmento = segmento, onSegmento = { segmento = it }`. Imports: `androidx.compose.runtime.*`, `rememberSaveable`.

- [ ] **Step 4: Run** — JVM `TotaisViewModelTest`, `mise run test`, `lintDebug`, e device `TotaisContentTest` (PASS, 28 testes ou o total atual).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/scholze/saldo/ui/totais app/src/test/kotlin/com/scholze/saldo/ui/totais app/src/androidTest/kotlin/com/scholze/saldo/ui/totais
git commit -m "feat: o segmento de totais vive no ViewModel, no saved state"
```

---

### Task 3: A pill "guardou N%" no hero

**Files:**
- Modify: `app/src/main/kotlin/com/scholze/saldo/ui/ledger/LedgerScreen.kt` (`BalanceHero`)
- Modify: `app/src/main/kotlin/com/scholze/saldo/ui/board/BoardScreen.kt` (assinatura + repasse)
- Modify: `app/src/main/kotlin/com/scholze/saldo/ui/SaldoApp.kt`
- Test: `app/src/androidTest/kotlin/com/scholze/saldo/ui/board/BoardScreenTest.kt` (append), `VistaSaldosTest.kt` (append)

**Interfaces:**
- Produces: `BalanceHero(mes, onTogglePrivacidade, onVerGuardado: () -> Unit)`; `BoardScreen(..., onVerGuardado: () -> Unit, ...)`; `LedgerScreen(..., onVerGuardado: () -> Unit = {}, ...)`; `const val TAG_PILL_GUARDADO = "hero:guardado"`.
- Consumes: `MesLedger.taxaGuardada` (Task 1), `TotaisViewModel.selecionarSegmento` (Task 2).

- [ ] **Step 1: Failing tests**

`BoardScreenTest` (append; o `input` do arquivo tem salário 7.400 em 01/set e nenhuma economia — acrescente no fixture do teste uma `Movimentacao(id = 5, descricao = "cdb", valorCentavos = -1_480_00, data = 2026-09-02, natureza = ECONOMIA)` só via `input.copy(movimentacoes = input.movimentacoes + …)`):

```kotlin
    @Test
    fun oHeroDizQuantoGuardouDoQueEntrou() {
        val comEconomia = input.copy(
            movimentacoes = input.movimentacoes + Movimentacao(
                id = 5, descricao = "cdb", valorCentavos = -1_480_00,
                data = LocalDate.parse("2026-09-02"), natureza = Natureza.ECONOMIA,
            ),
        )
        montar(entrada = comEconomia)
        rule.onNodeWithTag(TAG_PILL_GUARDADO).assertIsDisplayed()
        rule.onNodeWithText("guardou 20%").assertIsDisplayed()
    }

    @Test
    fun semEntradaNoMesNaoHaPill() {
        montar(entrada = input.copy(movimentacoes = input.movimentacoes.filter { it.valorCentavos < 0 }))
        rule.onAllNodesWithTag(TAG_PILL_GUARDADO).assertCountEquals(0)
    }

    @Test
    fun aPillFicaVisivelComAPrivacidadeLigada() {
        val comEconomia = input.copy(
            movimentacoes = input.movimentacoes + Movimentacao(
                id = 5, descricao = "cdb", valorCentavos = -1_480_00,
                data = LocalDate.parse("2026-09-02"), natureza = Natureza.ECONOMIA,
            ),
        )
        montar(entrada = comEconomia, oculto = true)
        rule.onNodeWithText("guardou 20%").assertIsDisplayed()
    }

    @Test
    fun tocarNaPillPedeATendencia() {
        pediuGuardado = false
        montar(entrada = input.copy(movimentacoes = input.movimentacoes + Movimentacao(
            id = 5, descricao = "cdb", valorCentavos = -1_480_00,
            data = LocalDate.parse("2026-09-02"), natureza = Natureza.ECONOMIA,
        )))
        rule.onNodeWithTag(TAG_PILL_GUARDADO).performClick()
        assertEquals(true, pediuGuardado)
    }
```

com `private var pediuGuardado = false` e `onVerGuardado = { pediuGuardado = true }` em `montar`. (7.400 de entrada, 1.480 de economia = 20 %.)

`VistaSaldosTest` (append):

```kotlin
    @Test
    fun aPillDoHeroAbreTotaisNaTendencia() {
        val app = ApplicationProvider.getApplicationContext<SaldoApplication>()
        runBlocking {
            app.container.settings.definirSaldoInicial(100_000_00, LocalDate.now().withDayOfMonth(1))
            app.container.repository.criar(Movimentacao(descricao = "salário", valorCentavos = 5_000_00, data = LocalDate.now(), natureza = Natureza.DIARIO), RepetirOpcao.Nao)
            app.container.repository.criar(Movimentacao(descricao = "cdb", valorCentavos = -1_000_00, data = LocalDate.now(), natureza = Natureza.ECONOMIA), RepetirOpcao.Nao)
        }
        ActivityScenario.launch(MainActivity::class.java).use {
            esperarBoard()
            rule.waitUntil(5_000) { rule.onAllNodesWithTag(TAG_PILL_GUARDADO).fetchSemanticsNodes().isNotEmpty() }
            rule.onNodeWithTag(TAG_PILL_GUARDADO).performClick()
            rule.waitUntil(5_000) { rule.onAllNodesWithTag(TAG_SEGMENTO_TOTAIS).fetchSemanticsNodes().isNotEmpty() }
            rule.onNode(hasAnyAncestor(hasTestTag(TAG_SEGMENTO_TOTAIS)) and hasText("tendência")).assertIsSelected()
        }
    }
```

(Se `FiltroChips` não expõe `Selected`, leia o chip selecionado pela cor não dá; então assegure por conteúdo: `rule.onNodeWithText("6 MESES").assertIsDisplayed()`, que só a tendência mostra.)

- [ ] **Step 2: Run to verify they fail** — compilação: `TAG_PILL_GUARDADO`, `onVerGuardado`.

- [ ] **Step 3: Implementation**

`LedgerScreen.kt`: `const val TAG_PILL_GUARDADO = "hero:guardado"` ao lado de `TAG_SALDO_PROJETADO`. `BalanceHero` ganha `onVerGuardado: () -> Unit` e a `Row` da pill "no mês" vira uma `Row` externa com as duas pills:

```kotlin
        Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier
                    .clip(RoundedCornerShape(percent = 50))
                    .background(colors.onPrimaryContainer.copy(alpha = 0.10f))
                    .padding(horizontal = 11.dp, vertical = 5.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MoneyText(centavos = mes.deltaNoMesCentavos, style = SaldoTheme.type.subhead, color = colors.onPrimaryContainer, formato = FormatoMoney.ASSINADO_COM_SIMBOLO)
                Text("no mês", style = SaldoTheme.type.subhead, color = colors.onPrimaryContainer)
            }
            // Do que entrou, quanto foi guardado. Não é dinheiro: a privacidade não a esconde.
            mes.taxaGuardada?.let { taxa ->
                Text(
                    "guardou $taxa%",
                    Modifier
                        .clip(RoundedCornerShape(percent = 50))
                        .background(colors.onPrimaryContainer.copy(alpha = 0.10f))
                        .clickable(onClick = onVerGuardado)
                        .padding(horizontal = 11.dp, vertical = 5.dp)
                        .testTag(TAG_PILL_GUARDADO)
                        .semantics { contentDescription = "guardou $taxa% do que entrou" },
                    style = SaldoTheme.type.subhead, color = colors.onPrimaryContainer,
                )
            }
        }
```

(O `clickable` do hero inteiro alterna a privacidade; o da pill vem depois na hierarquia e ganha o toque.) `LedgerScreen` ganha `onVerGuardado: () -> Unit = {}` e passa a `BalanceHero`; `BoardScreen` ganha `onVerGuardado: () -> Unit` (sem default, para a shell não esquecer) e passa. Imports: `androidx.compose.ui.semantics.*` se faltarem.

`SaldoApp.kt`: nas chamadas de `BoardScreen` e `LedgerScreen`:

```kotlin
                            onVerGuardado = { verGuardado(boardVm.mesAtualAgora) },      // board
                            onVerGuardado = { verGuardado(ledgerVm.mesAtualAgora) },     // lista
```

com, perto de `voltarAoBoard`:

```kotlin
    // A pill "guardou N%" leva à poupança mês a mês, que mora em totais → tendência.
    val verGuardado: (YearMonth) -> Unit = { mes ->
        totaisVm.irPara(mes)
        totaisVm.selecionarSegmento(SegmentoTotais.TENDENCIA)
        abrindoRecorrencias = false
        tab = SaldoTab.TOTAIS
    }
```

Import `com.scholze.saldo.ui.totais.SegmentoTotais`.

- [ ] **Step 4: Run** — device `BoardScreenTest,VistaSaldosTest,LedgerScreenTest,EntryFlowTest` PASS; `mise run test`; `lintDebug`.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/scholze/saldo/ui app/src/androidTest/kotlin/com/scholze/saldo
git commit -m "feat: o hero diz quanto do que entrou foi guardado, e leva à tendência"
```

---

### Task 4: A linha do widget de saldo

**Files:**
- Modify: `app/src/main/kotlin/com/scholze/saldo/widget/WidgetEstado.kt:10-16`
- Modify: `app/src/main/kotlin/com/scholze/saldo/widget/SaldoWidget.kt:29-37`
- Modify: `app/src/main/kotlin/com/scholze/saldo/widget/SaldoWidgetContent.kt:84-95`
- Test: `app/src/test/kotlin/com/scholze/saldo/widget/SaldoWidgetContentTest.kt` (append + fixture)

**Interfaces:**
- Produces: `WidgetEstado.Pronto.taxaGuardada: Int? = null`; `const val TAG_WIDGET_GUARDADO = "widget:guardado"`.
- Consumes: `MesLedger.taxaGuardada`.

- [ ] **Step 1: Failing tests** (append; o fixture `pronto` do arquivo ganha `taxaGuardada = 20`)

```kotlin
    @Test
    fun largoReveladoDizQuantoGuardou() = runGlanceAppWidgetUnitTest {
        setAppWidgetSize(SaldoWidget.LARGO)
        provideComposable { SaldoWidgetContent(pronto.copy(mostrarValores = true)) }
        onNode(hasTestTag(TAG_WIDGET_GUARDADO)).assertHasText("guardou 20%")
    }

    @Test
    fun largoMascaradoEscondeOPercentual() = runGlanceAppWidgetUnitTest {
        setAppWidgetSize(SaldoWidget.LARGO)
        provideComposable { SaldoWidgetContent(pronto) }
        onNode(hasTestTag(TAG_WIDGET_GUARDADO)).assertHasText("guardou ••%")
    }

    @Test
    fun semTaxaNaoHaLinha() = runGlanceAppWidgetUnitTest {
        setAppWidgetSize(SaldoWidget.LARGO)
        provideComposable { SaldoWidgetContent(pronto.copy(mostrarValores = true, taxaGuardada = null)) }
        onNode(hasTestTag(TAG_WIDGET_GUARDADO)).assertDoesNotExist()
    }

    @Test
    fun compactoNaoTemALinha() = runGlanceAppWidgetUnitTest {
        setAppWidgetSize(SaldoWidget.COMPACTO)
        provideComposable { SaldoWidgetContent(pronto.copy(mostrarValores = true)) }
        onNode(hasTestTag(TAG_WIDGET_GUARDADO)).assertDoesNotExist()
    }
```

- [ ] **Step 2: Run to verify they fail** — compilação.

- [ ] **Step 3: Implementation**

`WidgetEstado.Pronto` ganha `val taxaGuardada: Int? = null,` (com KDoc "quanto do que entrou no mês foi para economia; nulo sem entrada"). `SaldoWidget.kt`: `taxaGuardada = mes.taxaGuardada,`. `SaldoWidgetContent.kt`: `const val TAG_WIDGET_GUARDADO = "widget:guardado"` ao lado das outras tags, e logo depois do bloco do delta (`if (largo && estado.mostrarValores) { … }`):

```kotlin
                    // Do que entrou, quanto foi guardado. Mascarado junto com o valor: o
                    // usuário escolheu esconder o percentual no widget, ao contrário do hero.
                    val taxa = estado.taxaGuardada
                    if (largo && taxa != null) {
                        Text(
                            if (estado.mostrarValores) "guardou $taxa%" else "guardou ••%",
                            modifier = GlanceModifier.semantics { testTag = TAG_WIDGET_GUARDADO },
                            style = TextStyle(color = CoresWidget.secundario, fontSize = 12.sp),
                            maxLines = 1,
                        )
                    }
```

Confira o layout 2×2 (`SaldoWidget.QUADRADO` ou como se chame — leia `SaldoWidget.kt`): `largo` deve ser verdadeiro nele também; se o 2×2 usa outra flag, inclua-a na condição.

- [ ] **Step 4: Run** — `mise exec -- ./gradlew :app:testDebugUnitTest --tests "com.scholze.saldo.widget.*"` PASS; `mise run test`; `lintDebug`.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/scholze/saldo/widget app/src/test/kotlin/com/scholze/saldo/widget
git commit -m "feat: o widget de saldo diz quanto guardou, mascarado junto com o valor"
```

---

### Task 5: Versão 0.6.0, captura e fechamento

**Files:**
- Modify: `app/build.gradle.kts` (`versionCode = 7`, `versionName = "0.6.0"`)
- Create: `docs/superpowers/screenshots/2026-09-07-guardado/01-hero-guardou.png` + `README.md` (uma linha)

- [ ] **Step 1:** bump; captura do hero com a pill no emulador (onboarding, um salário e uma economia pelo app); README de uma linha.
- [ ] **Step 2:** final green — `mise run test`, `mise run test-device` (inteiro), `lintDebug`, `mise run build`. Anote as contagens.
- [ ] **Step 3:** commit `chore: versão 0.6.0 — guardou N% no hero e no widget`. Depois merge fast-forward em `main`, tag `v0.6.0`, release no GitHub com `saldo-0.6.0-debug.apk` e sha256, como a v0.5.0.
