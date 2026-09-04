# saldo — board (a grade do ano como vista padrão) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fazer o app abrir numa grade de calendário de 12 meses em que cada dia é um quadradinho colorido pelo saldo daquele dia, mantendo o ledger inteiro a um toque de distância.

**Architecture:** Três camadas, nesta ordem. (1) Um motor puro `BoardEngine` que agrupa as movimentações efetivas por dia, calcula a régua de cor e marca os vencimentos de fatura — nada de Compose, nada de Room, testado na JVM. (2) Tokens de cor e um `SaldoTopBar` capaz de hospedar duas ações. (3) A tela e a ligação na `SaldoApp`, onde um `rememberSaveable` decide qual das duas vistas da aba `saldos` está no ar.

**Tech Stack:** Kotlin 2.2, Compose (BOM 2026.08.00), Material 3, Room/DataStore intocados, JUnit4. **Nenhuma dependência nova.**

## Global Constraints

- **Spec:** `docs/superpowers/specs/2026-09-04-saldo-board-design.md`. Quando o plano e o spec discordarem, o plano ganha.
- **Baseline:** branch `board-1`, criada de `main` em `79ec5f3`. Spec commitada em `df5e80d`.
- **Sem schema novo, sem setting novo, sem permissão nova.** 100 % local inalterado.
- **Todo dinheiro renderiza por `MoneyText`** (`ui/privacy/Privacy.kt`) para a máscara continuar valendo. O número do **dia** não é dinheiro e não passa por lá.
- **Strings em pt-BR minúsculas**, como o resto do app.
- **`BoardEngine` não conhece cor:** devolve `nivel: Int` em −3..3 e a tela decide o tom.
- **Aritmética de dinheiro em `Long` de centavos**, sem `Float`/`Double` em nenhuma comparação de faixa.
- **Cada task termina verde:** `mise run test` (JVM) e `mise exec -- ./gradlew lintDebug` com 0 erros. As tasks 6 e 7 acrescentam `mise run test-device` num emulador ligado (`saldo_test`).
- Commit por task, com as duas linhas de trailer de sempre.
- O repositório tem `core.autocrlf=true`; o working tree legitimamente guarda CRLF. **Não** reescrever arquivos por causa de fim de linha.

## File structure

| File | Responsibility |
|---|---|
| `app/src/main/kotlin/com/scholze/saldo/domain/BoardEngine.kt` (create) | `Board`, `DiaBoard`, `BoardEngine.board()` e `BoardEngine.nivelDe()` |
| `app/src/main/kotlin/com/scholze/saldo/domain/ProjectionEngine.kt` (modify) | expor `movimentacoesAte(input, ateMes)` |
| `app/src/main/kotlin/com/scholze/saldo/ui/theme/Color.kt` (modify) | 7 tokens de board por tema + `tomDoBoard(nivel)` |
| `app/src/main/kotlin/com/scholze/saldo/ui/components/Icons.kt` (modify) | glifo `GRADE` |
| `app/src/main/kotlin/com/scholze/saldo/ui/components/M3.kt` (modify) | `SaldoTopBar` com `acoes` e `mostrarSetas` |
| `app/src/main/kotlin/com/scholze/saldo/ui/board/BoardViewModel.kt` (create) | `BoardUiState` sobre `repo.ledger` |
| `app/src/main/kotlin/com/scholze/saldo/ui/board/BoardScreen.kt` (create) | grade, calha do mês, régua, hero, cabeçalho |
| `app/src/main/kotlin/com/scholze/saldo/ui/ledger/LedgerScreen.kt` (modify) | `BalanceHero` vira `internal`; a top bar ganha o toggle |
| `app/src/main/kotlin/com/scholze/saldo/ui/SaldoApp.kt` (modify) | `VistaSaldos`, o `rememberSaveable`, o deep link forçando a lista |
| `app/src/test/kotlin/com/scholze/saldo/domain/BoardEngineTest.kt` (create) | 14 casos de motor |
| `app/src/test/kotlin/com/scholze/saldo/ui/theme/SaldoColorsTest.kt` (modify) | os tons novos existem e são distintos |
| `app/src/androidTest/kotlin/com/scholze/saldo/ui/board/BoardScreenTest.kt` (create) | grade, toque, privacidade, fonte grande |
| `app/src/androidTest/kotlin/com/scholze/saldo/VistaSaldosTest.kt` (create) | abre no board, toggle, deep link cai na lista |

---

### Task 1: `BoardEngine` — o motor puro

**Files:**
- Create: `app/src/main/kotlin/com/scholze/saldo/domain/BoardEngine.kt`
- Modify: `app/src/main/kotlin/com/scholze/saldo/domain/ProjectionEngine.kt` (acrescentar `movimentacoesAte` ao lado de `movimentacoesDoMes`)
- Test: `app/src/test/kotlin/com/scholze/saldo/domain/BoardEngineTest.kt` (create)

**Interfaces:**
- Consumes: `LedgerInput`, `Movimentacao`, `Natureza`, `CartaoConfig` (já existem em `domain/Modelos.kt` e `domain/ProjectionEngine.kt`); `ProjectionEngine.faturasAte(input, ateMes): List<Fatura>` (já público).
- Produces:
  - `data class DiaBoard(val data: LocalDate, val valorCentavos: Long, val nivel: Int, val venceFatura: Boolean, val dentroDaJanela: Boolean)`
  - `data class Board(val dias: List<DiaBoard>, val unidadeCentavos: Long, val inicio: LocalDate, val fim: LocalDate)`
  - `BoardEngine.board(input: LedgerInput): Board`
  - `BoardEngine.nivelDe(valorCentavos: Long, unidadeCentavos: Long): Int`
  - `ProjectionEngine.movimentacoesAte(input: LedgerInput, ateMes: YearMonth): List<Movimentacao>`

- [ ] **Step 1: Write the failing test**

Create `app/src/test/kotlin/com/scholze/saldo/domain/BoardEngineTest.kt`:

```kotlin
package com.scholze.saldo.domain

import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BoardEngineTest {

    private val hoje = LocalDate.of(2026, 9, 4)

    private fun mov(dia: LocalDate, centavos: Long, natureza: Natureza = Natureza.DIARIO) =
        Movimentacao(id = 1, descricao = "x", valorCentavos = centavos, data = dia, natureza = natureza)

    private fun input(
        movs: List<Movimentacao>,
        recorrencias: List<Recorrencia> = emptyList(),
        inicial: LocalDate = LocalDate.of(2025, 1, 1),
        cartao: CartaoConfig = CartaoConfig(fechamentoDia = 28, vencimentoDia = 5),
    ) = LedgerInput(
        saldoInicialCentavos = 100_000,
        saldoInicialData = inicial,
        movimentacoes = movs,
        recorrencias = recorrencias,
        mesesMaterializados = emptySet(),
        cartao = cartao,
        hoje = hoje,
    )

    private fun Board.dia(d: LocalDate) = dias.first { it.data == d }

    // ---- janela ----

    @Test fun `janela vai de 12 meses atras ate hoje`() {
        val b = BoardEngine.board(input(emptyList()))
        assertEquals(LocalDate.of(2025, 9, 5), b.inicio)
        assertEquals(hoje, b.fim)
        assertEquals(365, b.dias.size)
        assertEquals(b.inicio, b.dias.first().data)
        assertEquals(b.fim, b.dias.last().data)
    }

    @Test fun `dias sao contiguos e sem buraco`() {
        val b = BoardEngine.board(input(listOf(mov(hoje.minusDays(3), -5_000))))
        b.dias.zipWithNext { a, c -> assertEquals(a.data.plusDays(1), c.data) }
    }

    @Test fun `dia anterior ao saldo inicial fica fora da janela`() {
        val inicial = LocalDate.of(2026, 1, 10)
        val b = BoardEngine.board(input(emptyList(), inicial = inicial))
        assertFalse(b.dia(LocalDate.of(2026, 1, 9)).dentroDaJanela)
        assertTrue(b.dia(inicial).dentroDaJanela)
    }

    // ---- valor do dia ----

    @Test fun `valor do dia soma entradas e saidas do mesmo dia`() {
        val d = hoje.minusDays(2)
        val b = BoardEngine.board(input(listOf(mov(d, -3_000), mov(d, 10_000))))
        assertEquals(7_000, b.dia(d).valorCentavos)
    }

    @Test fun `compra no cartao conta no dia da compra`() {
        val compra = LocalDate.of(2026, 8, 12)
        val b = BoardEngine.board(input(listOf(mov(compra, -20_000, Natureza.CARTAO))))
        assertEquals(-20_000, b.dia(compra).valorCentavos)
    }

    @Test fun `fatura nao vira valor no dia do vencimento`() {
        val compra = LocalDate.of(2026, 8, 12)
        val b = BoardEngine.board(input(listOf(mov(compra, -20_000, Natureza.CARTAO))))
        assertEquals(0, b.dia(LocalDate.of(2026, 9, 5)).valorCentavos)
    }

    @Test fun `ocorrencia virtual de recorrencia entra na soma do dia`() {
        val rec = Recorrencia(
            id = 1, descricao = "aluguel", valorCentavos = -150_000,
            natureza = Natureza.DIARIO, diaDoMes = 10, inicio = YearMonth.of(2026, 1),
        )
        val b = BoardEngine.board(input(emptyList(), recorrencias = listOf(rec)))
        assertEquals(-150_000, b.dia(LocalDate.of(2026, 8, 10)).valorCentavos)
    }

    // ---- anel de fatura ----

    @Test fun `vencimento da fatura marca o dia`() {
        val compra = LocalDate.of(2026, 8, 12)   // ciclo ago, fecha 28/ago, vence 5/set
        val b = BoardEngine.board(input(listOf(mov(compra, -20_000, Natureza.CARTAO))))
        assertTrue(b.dia(LocalDate.of(2026, 9, 5)).venceFatura)
        assertFalse(b.dia(compra).venceFatura)
    }

    @Test fun `sem compra no cartao nenhum dia marca vencimento`() {
        val b = BoardEngine.board(input(listOf(mov(hoje.minusDays(1), -5_000))))
        assertTrue(b.dias.none { it.venceFatura })
    }

    // ---- unidade e niveis ----

    @Test fun `unidade e a mediana dos dias com movimento`() {
        val b = BoardEngine.board(
            input(
                listOf(
                    mov(hoje.minusDays(1), -1_000),
                    mov(hoje.minusDays(2), -3_000),
                    mov(hoje.minusDays(3), -10_000),
                ),
            ),
        )
        assertEquals(3_000, b.unidadeCentavos)
    }

    @Test fun `dias zerados nao entram na mediana`() {
        val b = BoardEngine.board(input(listOf(mov(hoje.minusDays(1), -8_000))))
        assertEquals(8_000, b.unidadeCentavos)
    }

    @Test fun `sem nenhum movimento nao ha dia tipico`() {
        val b = BoardEngine.board(input(emptyList()))
        assertEquals(0, b.unidadeCentavos)
        assertTrue(b.dias.all { it.nivel == 0 })
    }

    @Test fun `faixas caem em meia e uma vez e meia a unidade`() {
        val u = 10_000L
        assertEquals(0, BoardEngine.nivelDe(0, u))
        assertEquals(-1, BoardEngine.nivelDe(-4_999, u))
        assertEquals(-2, BoardEngine.nivelDe(-5_000, u))
        assertEquals(-2, BoardEngine.nivelDe(-14_999, u))
        assertEquals(-3, BoardEngine.nivelDe(-15_000, u))
        assertEquals(1, BoardEngine.nivelDe(4_999, u))
        assertEquals(2, BoardEngine.nivelDe(5_000, u))
        assertEquals(3, BoardEngine.nivelDe(15_000, u))
    }

    @Test fun `salario satura sem mexer no lado rosa`() {
        val movs = (1..10).map { mov(hoje.minusDays(it.toLong()), -5_000) } +
            mov(hoje.minusDays(11), 740_000)
        val b = BoardEngine.board(input(movs))
        assertEquals(5_000, b.unidadeCentavos)
        assertEquals(3, b.dia(hoje.minusDays(11)).nivel)
        assertEquals(-2, b.dia(hoje.minusDays(1)).nivel)
    }

    @Test fun `unidade zero deixa todo dia neutro`() {
        assertEquals(0, BoardEngine.nivelDe(-9_999, 0))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mise exec -- ./gradlew testDebugUnitTest --tests '*BoardEngineTest*'`
Expected: FAIL — `Unresolved reference: BoardEngine`.

- [ ] **Step 3: Expose `movimentacoesAte` on `ProjectionEngine`**

Em `app/src/main/kotlin/com/scholze/saldo/domain/ProjectionEngine.kt`, logo **abaixo** de `movimentacoesDoMes`, acrescente:

```kotlin
    /**
     * As movimentações efetivas do saldo inicial até o fim de [ateMes] — linhas
     * materializadas e expansões virtuais, sem faturas. É a mesma lista que
     * [movimentacoesDoMes] recorta num mês; o board precisa de treze meses de uma vez e
     * chamar aquela treze vezes reexpandiria as recorrências treze vezes.
     */
    fun movimentacoesAte(input: LedgerInput, ateMes: YearMonth): List<Movimentacao> =
        efetivas(input, ateMes)
```

- [ ] **Step 4: Write `BoardEngine`**

Create `app/src/main/kotlin/com/scholze/saldo/domain/BoardEngine.kt`:

```kotlin
package com.scholze.saldo.domain

import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.abs

/**
 * Um dia da grade.
 *
 * [nivel] vai de −3 a 3: negativo saiu mais do que entrou, positivo o contrário, 0 é dia
 * sem movimento. É deliberadamente um número, não uma cor — quem pinta é a tela.
 *
 * [dentroDaJanela] é falso nos dias anteriores ao saldo inicial: não é "nada aconteceu",
 * é "não há registro", e a grade os apaga em vez de mostrá-los como dia parado.
 */
data class DiaBoard(
    val data: LocalDate,
    val valorCentavos: Long,
    val nivel: Int,
    val venceFatura: Boolean,
    val dentroDaJanela: Boolean,
)

/**
 * A grade inteira. [dias] é contíguo de [inicio] a [fim], sem buraco — a tela conta com
 * isso para montar as semanas.
 *
 * [unidadeCentavos] é o "dia típico" que dá escala às cores; `0` significa que ainda não
 * há nenhum dia com movimento e a legenda deve dizer isso em vez de mostrar R$ 0,00.
 */
data class Board(
    val dias: List<DiaBoard>,
    val unidadeCentavos: Long,
    val inicio: LocalDate,
    val fim: LocalDate,
)

/**
 * O board: o saldo de cada dia dos últimos 12 meses, em sete tons.
 *
 * Duas escolhas separam este motor do [ProjectionEngine], e as duas são deliberadas:
 *
 * 1. **Uma compra no cartão conta no dia da compra**, como no "para onde foi", e não no
 *    vencimento da fatura como no ledger. O board é uma vista de comportamento — o dia
 *    caro é a sexta em que se gastou, não o dia 5 em que o banco cobrou. O vencimento
 *    ainda aparece, mas como marca ([DiaBoard.venceFatura]), sem valor nenhum.
 * 2. **A escala é a mediana**, não o máximo. Um salário é umas trinta vezes um dia
 *    comum; normalizado pelo máximo ele desbotaria o board inteiro. Contra a mediana ele
 *    simplesmente satura no tom 3 e não move mais nada.
 */
object BoardEngine {

    /** Quantos meses a grade cobre, terminando em `input.hoje`. */
    private const val MESES = 12L

    fun board(input: LedgerInput): Board {
        val fim = input.hoje
        val inicio = fim.minusMonths(MESES).plusDays(1)
        val ateMes = YearMonth.from(fim)

        val porDia = ProjectionEngine.movimentacoesAte(input, ateMes)
            .filter { it.data in inicio..fim }
            .groupBy { it.data }
            .mapValues { (_, movs) -> movs.sumOf { it.valorCentavos } }

        val vencimentos = ProjectionEngine.faturasAte(input, ateMes)
            .map { it.vencimento }
            .filter { it in inicio..fim }
            .toSet()

        val unidade = mediana(porDia.values.filter { it != 0L }.map { abs(it) })

        val dias = buildList {
            var d = inicio
            while (d <= fim) {
                val valor = porDia[d] ?: 0L
                add(
                    DiaBoard(
                        data = d,
                        valorCentavos = valor,
                        nivel = nivelDe(valor, unidade),
                        venceFatura = d in vencimentos,
                        dentroDaJanela = d >= input.saldoInicialData,
                    ),
                )
                d = d.plusDays(1)
            }
        }
        return Board(dias = dias, unidadeCentavos = unidade, inicio = inicio, fim = fim)
    }

    /**
     * Em que tom [valorCentavos] cai, dada a unidade do dia típico.
     *
     * Os cortes são ½× e 1½× a unidade, e a conta é feita em inteiros — `|v| * 2` contra
     * `unidade` e contra `unidade * 3` — para não existir um `Double` decidindo de que
     * cor um dia é.
     */
    fun nivelDe(valorCentavos: Long, unidadeCentavos: Long): Int {
        if (valorCentavos == 0L || unidadeCentavos <= 0L) return 0
        val escala = abs(valorCentavos) * 2
        val tom = when {
            escala < unidadeCentavos -> 1
            escala < unidadeCentavos * 3 -> 2
            else -> 3
        }
        return if (valorCentavos < 0) -tom else tom
    }

    /** `0` numa lista vazia — é o sinal de "ainda não há dia típico". */
    private fun mediana(valores: List<Long>): Long {
        if (valores.isEmpty()) return 0L
        val s = valores.sorted()
        val meio = s.size / 2
        return if (s.size % 2 == 1) s[meio] else (s[meio - 1] + s[meio]) / 2
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `mise exec -- ./gradlew testDebugUnitTest --tests '*BoardEngineTest*'`
Expected: PASS, 14 testes.

- [ ] **Step 6: Full JVM suite + lint**

Run: `mise run test` then `mise exec -- ./gradlew lintDebug`
Expected: tudo verde, 0 erros de lint.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/kotlin/com/scholze/saldo/domain/BoardEngine.kt \
        app/src/main/kotlin/com/scholze/saldo/domain/ProjectionEngine.kt \
        app/src/test/kotlin/com/scholze/saldo/domain/BoardEngineTest.kt
git commit -m "feat: motor do board — saldo do dia em sete tons"
```

---

### Task 2: Tokens de cor do board

**Files:**
- Modify: `app/src/main/kotlin/com/scholze/saldo/ui/theme/Color.kt`
- Test: `app/src/test/kotlin/com/scholze/saldo/ui/theme/SaldoColorsTest.kt` (modify)

**Interfaces:**
- Produces: `SaldoColors.boardZero/boardNeg1/boardNeg2/boardNeg3/boardPos1/boardPos2/boardPos3`, e `fun SaldoColors.tomDoBoard(nivel: Int): Color` mapeando −3..3.
- Consumes: nada novo.

- [ ] **Step 1: Write the failing test**

Acrescente ao fim de `app/src/test/kotlin/com/scholze/saldo/ui/theme/SaldoColorsTest.kt`, dentro da classe:

```kotlin
    @Test
    fun `tomDoBoard cobre os sete niveis nos dois temas`() {
        listOf(LightSaldoColors, DarkSaldoColors).forEach { c ->
            val tons = (-3..3).map { c.tomDoBoard(it) }
            assertEquals("sete níveis, sete tons distintos", 7, tons.toSet().size)
            assertEquals(c.boardZero, c.tomDoBoard(0))
            assertEquals(c.boardNeg3, c.tomDoBoard(-3))
            assertEquals(c.boardPos3, c.tomDoBoard(3))
        }
    }

    @Test
    fun `nivel fora da faixa satura em vez de estourar`() {
        assertEquals(LightSaldoColors.boardNeg3, LightSaldoColors.tomDoBoard(-9))
        assertEquals(LightSaldoColors.boardPos3, LightSaldoColors.tomDoBoard(9))
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mise exec -- ./gradlew testDebugUnitTest --tests '*SaldoColorsTest*'`
Expected: FAIL — `Unresolved reference: tomDoBoard`.

- [ ] **Step 3: Add the tokens**

Em `Color.kt`, dentro de `data class SaldoColors`, **antes** de `val isDark: Boolean`:

```kotlin
    /**
     * Os sete tons da grade do board: três rosas (saiu mais), o neutro do dia parado e
     * três verdes (entrou mais). Rosa e verde são as duas famílias que o app já usa —
     * `categoryVariable` e `tint` — esticadas em rampa.
     */
    val boardZero: Color,
    val boardNeg1: Color,
    val boardNeg2: Color,
    val boardNeg3: Color,
    val boardPos1: Color,
    val boardPos2: Color,
    val boardPos3: Color,
```

Em `LightSaldoColors`, antes de `isDark = false`:

```kotlin
    boardZero = Color(0xFFE7EBE4),
    boardNeg1 = Color(0xFFF0DDE3),
    boardNeg2 = Color(0xFFD7A9B7),
    boardNeg3 = Color(0xFF8C4F63),
    boardPos1 = Color(0xFFCBE7D2),
    boardPos2 = Color(0xFF79C293),
    boardPos3 = Color(0xFF2F6A45),
```

Em `DarkSaldoColors`, antes de `isDark = true`:

```kotlin
    boardZero = Color(0xFF232A24),
    boardNeg1 = Color(0xFF3A2830),
    boardNeg2 = Color(0xFF6B3C4C),
    boardNeg3 = Color(0xFFC98FA4),
    boardPos1 = Color(0xFF223A2A),
    boardPos2 = Color(0xFF3E7A55),
    boardPos3 = Color(0xFF7FD79B),
```

E ao fim do arquivo:

```kotlin
/**
 * O tom da célula para um nível de −3 a 3. Satura nas pontas em vez de estourar: o motor
 * promete a faixa, mas um `nivel` fora dela não pode virar crash de renderização.
 */
fun SaldoColors.tomDoBoard(nivel: Int): Color = when (nivel.coerceIn(-3, 3)) {
    -3 -> boardNeg3
    -2 -> boardNeg2
    -1 -> boardNeg1
    1 -> boardPos1
    2 -> boardPos2
    3 -> boardPos3
    else -> boardZero
}

/**
 * A tinta que se lê em cima de [tomDoBoard]. Só os tons 3 são escuros o bastante (claro)
 * ou claros o bastante (escuro) para exigirem o contraste invertido.
 */
fun SaldoColors.textoSobreBoard(nivel: Int): Color = when {
    abs(nivel) >= 3 -> if (isDark) background else Color(0xFFFFFFFF)
    else -> secondaryLabel
}
```

Acrescente `import kotlin.math.abs` ao topo de `Color.kt`.

- [ ] **Step 4: Run test to verify it passes**

Run: `mise exec -- ./gradlew testDebugUnitTest --tests '*SaldoColorsTest*'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/scholze/saldo/ui/theme/Color.kt \
        app/src/test/kotlin/com/scholze/saldo/ui/theme/SaldoColorsTest.kt
git commit -m "feat: tons do board — três rosas, o neutro e três verdes"
```

---

### Task 3: `SaldoTopBar` com duas ações e sem setas

**Files:**
- Modify: `app/src/main/kotlin/com/scholze/saldo/ui/components/Icons.kt` (enum + glifo `GRADE`)
- Modify: `app/src/main/kotlin/com/scholze/saldo/ui/components/M3.kt:130-160` (a `SaldoTopBar`)
- Modify: `app/src/main/kotlin/com/scholze/saldo/ui/ledger/LedgerScreen.kt:187-198` (renomeia `acao` → `acoes`)
- Modify: `app/src/main/kotlin/com/scholze/saldo/ui/totais/TotaisScreen.kt:88` (idem, se passar `acao`)
- Test: `app/src/androidTest/kotlin/com/scholze/saldo/ui/components/M3Test.kt` (acrescentar um caso)

**Interfaces:**
- Produces: `SaldoIcon.GRADE`; `SaldoTopBar(titulo, onAnterior, onProximo, modifier, mostrarSetas: Boolean = true, acoes: @Composable (() -> Unit)? = null)`.
- Consumes: `IconeRedondo`, `SaldoGlyph` (já existem).

**Nota de desenho:** o parâmetro antigo já era invocado dentro de uma `Row`, então **duas** `IconeRedondo` dentro dele sempre funcionaram lado a lado; a mudança de nome é honestidade, não capacidade nova. O que é capacidade nova é `mostrarSetas`, porque a janela do board é de 12 meses e não tem mês anterior nem próximo.

- [ ] **Step 1: Write the failing test**

Acrescente a `M3Test.kt`:

```kotlin
    @Test
    fun topBarSemSetasEsconde_asDuasSetas() {
        composeRule.setContent {
            SaldoTheme {
                SaldoTopBar(
                    titulo = "seus dias",
                    onAnterior = {},
                    onProximo = {},
                    mostrarSetas = false,
                    acoes = { IconeRedondo(SaldoIcon.GRADE, "ver como lista") {} },
                )
            }
        }
        composeRule.onNodeWithContentDescription("mês anterior").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("próximo mês").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("ver como lista").assertExists()
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mise exec -- ./gradlew connectedDebugAndroidTest --tests '*M3Test*'`
Expected: FAIL na compilação — `mostrarSetas`/`acoes`/`GRADE` não existem.

- [ ] **Step 3: Add the `GRADE` glyph**

Em `Icons.kt`, no enum:

```kotlin
enum class SaldoIcon {
    SALDOS, TOTAIS, TAGS, MAIS, PLUS, CHEVRON_LEFT, CHEVRON_RIGHT, BACKSPACE, RECORRENTE,
    OLHO, OLHO_RISCADO, CHECK, GRADE,
}
```

E no `when (icon)` do `SaldoGlyph`, antes do bloco `OLHO`:

```kotlin
            // Quatro quadradinhos: a grade do board.
            SaldoIcon.GRADE -> {
                val lado = w * 0.34f
                val gap = w * 0.10f
                val x0 = (w - lado * 2 - gap) / 2
                val y0 = (h - lado * 2 - gap) / 2
                listOf(0f to 0f, 1f to 0f, 0f to 1f, 1f to 1f).forEach { (cx, cy) ->
                    drawRoundRect(
                        color = tint,
                        topLeft = Offset(x0 + cx * (lado + gap), y0 + cy * (lado + gap)),
                        size = Size(lado, lado),
                        cornerRadius = CornerRadius(sw),
                        style = stroke,
                    )
                }
            }
```

Se `Size` e `CornerRadius` ainda não estiverem importados em `Icons.kt`, acrescente
`import androidx.compose.ui.geometry.CornerRadius` e `import androidx.compose.ui.geometry.Size`.

- [ ] **Step 4: Change `SaldoTopBar`**

Em `M3.kt`, substitua a assinatura e o corpo da `Row` interna:

```kotlin
@Composable
fun SaldoTopBar(
    titulo: String,
    onAnterior: () -> Unit,
    onProximo: () -> Unit,
    modifier: Modifier = Modifier,
    mostrarSetas: Boolean = true,
    acoes: @Composable (() -> Unit)? = null,
) {
    val colors = SaldoTheme.colors
    Column(modifier.fillMaxWidth().background(colors.background)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (mostrarSetas) IconeRedondo(SaldoIcon.CHEVRON_LEFT, "mês anterior", onAnterior)
            Box(Modifier.weight(1f))
            acoes?.invoke()
            if (mostrarSetas) IconeRedondo(SaldoIcon.CHEVRON_RIGHT, "próximo mês", onProximo)
        }
        Text(
            titulo,
            Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 14.dp),
            style = SaldoTheme.type.navTitle,
            color = colors.label,
        )
    }
}
```

Atualize o doc-comment: `[acoes] entra entre as duas setas — é onde o olho da privacidade e o toggle de vista moram.`

- [ ] **Step 5: Rename at the call sites**

Em `LedgerScreen.kt:190` e em `TotaisScreen.kt:88`, troque `acao =` por `acoes =`. Nenhuma outra mudança.

- [ ] **Step 6: Run tests**

Run: `mise run test` e `mise exec -- ./gradlew connectedDebugAndroidTest --tests '*M3Test*'`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/kotlin/com/scholze/saldo/ui/components/Icons.kt \
        app/src/main/kotlin/com/scholze/saldo/ui/components/M3.kt \
        app/src/main/kotlin/com/scholze/saldo/ui/ledger/LedgerScreen.kt \
        app/src/main/kotlin/com/scholze/saldo/ui/totais/TotaisScreen.kt \
        app/src/androidTest/kotlin/com/scholze/saldo/ui/components/M3Test.kt
git commit -m "feat: top bar sem setas e com duas ações, mais o glifo da grade"
```

---

### Task 4: `BoardViewModel`

**Files:**
- Create: `app/src/main/kotlin/com/scholze/saldo/ui/board/BoardViewModel.kt`

**Interfaces:**
- Consumes: `SaldoRepository.ledger: Flow<LedgerInput>`, `BoardEngine.board`, `ProjectionEngine.mes`, `AppContainer`.
- Produces: `data class BoardUiState(val board: Board?, val mes: MesLedger?, val hoje: LocalDate)`; `BoardViewModel.state: StateFlow<BoardUiState>`; `BoardViewModel.factory(container): ViewModelProvider.Factory`.

**Nota:** o hero do board é o **mesmo** `MesLedger` do mês corrente que o ledger mostra, para o número não mudar quando a vista troca. Por isso o estado carrega os dois.

- [ ] **Step 1: Write the ViewModel**

Create `app/src/main/kotlin/com/scholze/saldo/ui/board/BoardViewModel.kt`:

```kotlin
package com.scholze.saldo.ui.board

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.scholze.saldo.AppContainer
import com.scholze.saldo.data.SaldoRepository
import com.scholze.saldo.domain.Board
import com.scholze.saldo.domain.BoardEngine
import com.scholze.saldo.domain.FiltroLedger
import com.scholze.saldo.domain.MesLedger
import com.scholze.saldo.domain.ProjectionEngine
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * [board] e [mes] são `null` só enquanto o primeiro `LedgerInput` não chegou do banco.
 *
 * [mes] é o mês corrente inteiro, e existe por um motivo só: o hero do board é o MESMO
 * saldo projetado que o ledger mostra. Recalculá-lo aqui em vez de reusar o do
 * `LedgerViewModel` mantém as duas vistas independentes, e a conta é a mesma função pura.
 */
data class BoardUiState(val board: Board?, val mes: MesLedger?, val hoje: LocalDate)

class BoardViewModel(repo: SaldoRepository) : ViewModel() {

    val state: StateFlow<BoardUiState> =
        repo.ledger
            .map { input ->
                BoardUiState(
                    board = BoardEngine.board(input),
                    mes = ProjectionEngine.mes(input, YearMonth.from(input.hoje), FiltroLedger.TODAS),
                    hoje = input.hoje,
                )
            }
            // 365 dias de agrupamento mais a projeção do mês: fora da main thread.
            .flowOn(Dispatchers.Default)
            // Mesma razão do LedgerViewModel: uma exceção do banco cancelaria o StateFlow
            // e a tela congelaria sem crash que explicasse.
            .catch { Log.e(TAG, "fluxo do board falhou", it) }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                BoardUiState(board = null, mes = null, hoje = LocalDate.now()),
            )

    companion object {
        private const val TAG = "saldo"

        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { BoardViewModel(container.repository) }
        }
    }
}
```

- [ ] **Step 2: Compile**

Run: `mise run build`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/kotlin/com/scholze/saldo/ui/board/BoardViewModel.kt
git commit -m "feat: viewmodel do board"
```

---

### Task 5: `BoardScreen` — a grade

**Files:**
- Create: `app/src/main/kotlin/com/scholze/saldo/ui/board/BoardScreen.kt`
- Modify: `app/src/main/kotlin/com/scholze/saldo/ui/ledger/LedgerScreen.kt` (`private fun BalanceHero` → `internal fun BalanceHero`)

**Interfaces:**
- Consumes: `BoardUiState`, `Board`, `DiaBoard`, `SaldoColors.tomDoBoard/textoSobreBoard`, `SaldoTopBar(mostrarSetas=false, acoes=…)`, `BalanceHero(mes, onTogglePrivacidade)`, `MoneyText`, `LocalPrivacy`.
- Produces: `BoardScreen(state, onDiaClick, onVerLista, onTogglePrivacidade, modifier)`; as constantes de teste `TAG_BOARD_GRADE = "board:grade"`, `fun tagCelula(data: LocalDate): String = "board:celula:${data.toEpochDay()}"`, `TAG_BOARD_LEGENDA = "board:legenda"`.

**Notas de desenho:**
- As semanas correm de segunda a domingo; a primeira semana é completada à esquerda com células vazias para o dia 1 da janela cair na coluna certa.
- A calha da esquerda tem 22 dp e mostra o rótulo do mês na semana que contém um dia 1, e também na primeira linha da grade.
- Célula: `weight(1f).aspectRatio(1f)`, canto 8 dp. Hoje ganha uma borda externa de 2 dp em `colors.label` num invólucro de canto 11 dp; o vencimento da fatura ganha uma borda **interna** de 2 dp em `colors.boardNeg3`. Um dia pode ter as duas.
- Com `fontScale >= 1.3` o número do dia não é desenhado.

- [ ] **Step 1: Open `BalanceHero` for reuse**

Em `LedgerScreen.kt`, troque `private fun BalanceHero(` por `internal fun BalanceHero(` e acrescente ao doc:

```kotlin
/** O hero do saldo projetado. `internal` porque o board mostra exatamente o mesmo card. */
```

- [ ] **Step 2: Write `BoardScreen`**

Create `app/src/main/kotlin/com/scholze/saldo/ui/board/BoardScreen.kt`:

```kotlin
package com.scholze.saldo.ui.board

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.scholze.saldo.domain.DiaBoard
import com.scholze.saldo.ui.components.IconeRedondo
import com.scholze.saldo.ui.components.SaldoIcon
import com.scholze.saldo.ui.components.SaldoTopBar
import com.scholze.saldo.ui.ledger.BalanceHero
import com.scholze.saldo.ui.money.centavosComSimbolo
import com.scholze.saldo.ui.privacy.FormatoMoney
import com.scholze.saldo.ui.privacy.LocalPrivacy
import com.scholze.saldo.ui.privacy.MASCARA_PRIVACIDADE
import com.scholze.saldo.ui.privacy.MoneyText
import com.scholze.saldo.ui.theme.SaldoTheme
import com.scholze.saldo.ui.theme.textoSobreBoard
import com.scholze.saldo.ui.theme.tomDoBoard
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val ptBr = Locale.forLanguageTag("pt-BR")
private val mesCurto = DateTimeFormatter.ofPattern("MMM", ptBr)
private val diaLongo = DateTimeFormatter.ofPattern("d 'de' MMMM", ptBr)

const val TAG_BOARD_GRADE = "board:grade"
const val TAG_BOARD_LEGENDA = "board:legenda"

fun tagCelula(data: LocalDate): String = "board:celula:${data.toEpochDay()}"

/** A calha do mês à esquerda da grade. */
private val CALHA = 22.dp

/** Acima disto o número do dia não cabe na célula e some. */
private const val ESCALA_SEM_NUMERO = 1.3f

private val DIAS_SEMANA = listOf("s", "t", "q", "q", "s", "s", "d")

/**
 * O board: os últimos 12 meses em sete colunas de dia da semana, semanas empilhadas,
 * rolando na vertical.
 *
 * Em pé e não deitado como o do GitHub por uma razão prática: a aba `saldos` já gasta o
 * arrasto horizontal trocando de mês, e uma grade que rolasse para o lado brigaria com
 * esse gesto todo dia. Em pé ela também cabe num telefone sem espremer a célula.
 */
@Composable
fun BoardScreen(
    state: BoardUiState,
    onDiaClick: (LocalDate) -> Unit,
    onVerLista: () -> Unit,
    onTogglePrivacidade: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SaldoTheme.colors
    val board = state.board
    val listState = rememberLazyListState()

    val semanas = remember(board) { board?.let { semanasDe(it.dias) }.orEmpty() }

    // A grade abre no fim: hoje é a última linha, e é ela que interessa ao abrir o app.
    LaunchedEffect(semanas.size) {
        if (semanas.isNotEmpty()) listState.scrollToItem(semanas.size + 1)
    }

    Column(modifier.fillMaxSize().background(colors.background)) {
        SaldoTopBar(
            titulo = "seus dias",
            onAnterior = {},
            onProximo = {},
            mostrarSetas = false,
            acoes = {
                IconeRedondo(SaldoIcon.SALDOS, "ver como lista", onVerLista)
                IconeRedondo(
                    if (LocalPrivacy.current.oculto) SaldoIcon.OLHO_RISCADO else SaldoIcon.OLHO,
                    "alternar privacidade",
                    onTogglePrivacidade,
                )
            },
        )

        if (board == null || state.mes == null) {
            Box(Modifier.fillMaxSize())
            return@Column
        }

        LazyColumn(Modifier.weight(1f).testTag(TAG_BOARD_GRADE), state = listState) {
            item(key = "hero") { BalanceHero(state.mes, onTogglePrivacidade) }
            item(key = "cabecalho") { CabecalhoColunas() }
            itemsIndexed(semanas, key = { _, s -> s.filterNotNull().first().data.toEpochDay() }) { i, semana ->
                LinhaSemana(semana, state.hoje, primeiraLinha = i == 0, onDiaClick = onDiaClick)
            }
            item(key = "legenda") { Legenda(board.unidadeCentavos) }
        }
    }
}

/**
 * Quebra a lista contígua de dias em semanas de segunda a domingo, completando a primeira
 * com `null` para o primeiro dia cair na coluna certa. A última semana também é
 * completada — sem isso as células do fim esticariam para preencher a linha.
 */
internal fun semanasDe(dias: List<DiaBoard>): List<List<DiaBoard?>> {
    if (dias.isEmpty()) return emptyList()
    val antes = dias.first().data.dayOfWeek.value - DayOfWeek.MONDAY.value
    val depois = DayOfWeek.SUNDAY.value - dias.last().data.dayOfWeek.value
    val padded: List<DiaBoard?> = List(antes) { null } + dias + List(depois) { null }
    return padded.chunked(7)
}

@Composable
private fun CabecalhoColunas() {
    val colors = SaldoTheme.colors
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Spacer(Modifier.width(CALHA))
        DIAS_SEMANA.forEach { d ->
            Text0(d, Modifier.weight(1f), colors.secondaryLabel)
        }
    }
}

@Composable
private fun LinhaSemana(
    semana: List<DiaBoard?>,
    hoje: LocalDate,
    primeiraLinha: Boolean,
    onDiaClick: (LocalDate) -> Unit,
) {
    val colors = SaldoTheme.colors
    // O rótulo do mês aparece na semana que contém um dia 1 — e na primeira linha da
    // grade, que quase nunca contém um e ficaria sem nome nenhum.
    val marco = semana.filterNotNull().firstOrNull { it.data.dayOfMonth == 1 }
        ?: semana.filterNotNull().firstOrNull().takeIf { primeiraLinha }
    val rotulo = marco?.data?.format(mesCurto)?.removeSuffix(".").orEmpty()

    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(CALHA)) {
            if (rotulo.isNotEmpty()) Text0(rotulo, Modifier, colors.secondaryLabel)
        }
        semana.forEach { dia ->
            if (dia == null) Spacer(Modifier.weight(1f).aspectRatio(1f))
            else Celula(dia, hoje, Modifier.weight(1f), onDiaClick)
        }
    }
}

@Composable
private fun Celula(dia: DiaBoard, hoje: LocalDate, modifier: Modifier, onDiaClick: (LocalDate) -> Unit) {
    val colors = SaldoTheme.colors
    val oculto = LocalPrivacy.current.oculto
    val forma = RoundedCornerShape(8.dp)
    val ehHoje = dia.data == hoje
    val mostraNumero = LocalDensity.current.fontScale < ESCALA_SEM_NUMERO

    val fundo = if (!dia.dentroDaJanela) colors.surface else colors.tomDoBoard(dia.nivel)
    val tinta = if (!dia.dentroDaJanela) colors.separator else colors.textoSobreBoard(dia.nivel)

    Box(
        modifier
            .aspectRatio(1f)
            .then(if (ehHoje) Modifier.border(2.dp, colors.label, RoundedCornerShape(11.dp)) else Modifier)
            .padding(2.dp),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .clip(forma)
                .background(fundo)
                .then(if (dia.venceFatura) Modifier.border(2.dp, colors.boardNeg3, forma) else Modifier)
                .clickable(enabled = dia.dentroDaJanela) { onDiaClick(dia.data) }
                .testTag(tagCelula(dia.data))
                .semantics { contentDescription = descricaoDe(dia, oculto) },
            contentAlignment = Alignment.Center,
        ) {
            if (mostraNumero && dia.dentroDaJanela) {
                Text0(
                    dia.data.dayOfMonth.toString(),
                    Modifier,
                    tinta,
                    if (ehHoje) FontWeight.Bold else FontWeight.Normal,
                )
            }
        }
    }
}

/**
 * A frase que o leitor de tela ouve. O valor respeita a privacidade — a cor não entrega
 * número, mas a descrição entregaria.
 */
internal fun descricaoDe(dia: DiaBoard, oculto: Boolean): String {
    val data = dia.data.format(diaLongo).removeSuffix(".")
    val valor = when {
        oculto -> MASCARA_PRIVACIDADE
        else -> (if (dia.valorCentavos < 0) -dia.valorCentavos else dia.valorCentavos).centavosComSimbolo()
    }
    val corpo = when {
        !dia.dentroDaJanela -> "sem registro"
        dia.valorCentavos == 0L -> "sem movimentação"
        dia.valorCentavos < 0 -> "saiu $valor"
        else -> "entrou $valor"
    }
    return if (dia.venceFatura) "$data, $corpo, fatura vence" else "$data, $corpo"
}

@Composable
private fun Legenda(unidadeCentavos: Long) {
    val colors = SaldoTheme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 28.dp)
            .testTag(TAG_BOARD_LEGENDA),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text0("saiu", Modifier, colors.secondaryLabel)
            listOf(-3, -2, -1, 0, 1, 2, 3).forEach { n ->
                Box(
                    Modifier
                        .size(12.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(colors.tomDoBoard(n)),
                )
            }
            Text0("entrou", Modifier, colors.secondaryLabel)
        }
        if (unidadeCentavos > 0) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text0("um dia típico =", Modifier, colors.secondaryLabel)
                MoneyText(
                    centavos = unidadeCentavos,
                    style = SaldoTheme.type.caption,
                    color = colors.secondaryLabel,
                    formato = FormatoMoney.COM_SIMBOLO,
                )
            }
        } else {
            Text0("ainda sem um dia típico", Modifier, colors.secondaryLabel)
        }
    }
}

/** Um `Text` de legenda — o mesmo estilo em oito lugares desta tela. */
@Composable
private fun Text0(
    texto: String,
    modifier: Modifier = Modifier,
    cor: androidx.compose.ui.graphics.Color,
    peso: FontWeight = FontWeight.Normal,
) {
    androidx.compose.material3.Text(
        text = texto,
        modifier = modifier,
        style = SaldoTheme.type.caption,
        color = cor,
        fontWeight = peso,
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
    )
}
```

- [ ] **Step 3: Compile**

Run: `mise run build`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/kotlin/com/scholze/saldo/ui/board/BoardScreen.kt \
        app/src/main/kotlin/com/scholze/saldo/ui/ledger/LedgerScreen.kt
git commit -m "feat: a grade do board — 12 meses em sete colunas"
```

---

### Task 6: Ligar na `SaldoApp`

**Files:**
- Modify: `app/src/main/kotlin/com/scholze/saldo/ui/SaldoApp.kt`
- Modify: `app/src/main/kotlin/com/scholze/saldo/ui/ledger/LedgerScreen.kt` (a top bar ganha o botão de volta ao board)

**Interfaces:**
- Produces: `enum class VistaSaldos { BOARD, LISTA }` em `ui/board/BoardScreen.kt`.
- Consumes: `BoardViewModel.factory`, `BoardScreen`, `LedgerViewModel.irPara`.

**Regras:**
- `var vista by rememberSaveable { mutableStateOf(VistaSaldos.BOARD) }` — sobrevive a rotação e a morte do processo, e volta ao board a cada abertura fria. É isso que "sempre abre no board" quer dizer.
- Tocar num dia: `ledgerVm.irPara(YearMonth.from(data), data.dayOfMonth); vista = VistaSaldos.LISTA`.
- `Destino.Saldos` (widget, lembrete) força `vista = VistaSaldos.LISTA`: quem chega por ali pediu um dia, não um panorama.
- Sair da aba `saldos` **não** reseta a vista — voltar para a aba mostra onde você estava, igual ao que `abrindoRecorrencias` faz ao contrário para totais.

- [ ] **Step 1: Add the enum**

Ao fim de `BoardScreen.kt`:

```kotlin
/** Qual das duas vistas da aba `saldos` está no ar. O app sempre abre em [BOARD]. */
enum class VistaSaldos { BOARD, LISTA }
```

- [ ] **Step 2: Add the toggle to the ledger's top bar**

Em `LedgerScreen.kt`, acrescente o parâmetro `onVerBoard: () -> Unit` à assinatura de
`LedgerScreen` (logo depois de `onTogglePrivacidade`) e troque o bloco `acoes`:

```kotlin
                acoes = {
                    IconeRedondo(SaldoIcon.GRADE, "ver como grade", onVerBoard)
                    IconeRedondo(
                        if (LocalPrivacy.current.oculto) SaldoIcon.OLHO_RISCADO else SaldoIcon.OLHO,
                        "alternar privacidade",
                        onTogglePrivacidade,
                    )
                },
```

- [ ] **Step 3: Wire `SaldoApp`**

Em `SaldoApp.kt`:

1. Imports novos:

```kotlin
import com.scholze.saldo.ui.board.BoardScreen
import com.scholze.saldo.ui.board.BoardViewModel
import com.scholze.saldo.ui.board.VistaSaldos
import java.time.YearMonth
```

2. Ao lado das outras factories:

```kotlin
    val boardVm: BoardViewModel = viewModel(factory = remember(container) { BoardViewModel.factory(container) })
    val boardState by boardVm.state.collectAsState()
```

3. Ao lado de `abrindoRecorrencias`:

```kotlin
    // Qual vista da aba saldos está no ar. `rememberSaveable` e não DataStore: a escolha
    // sobrevive a rotação e à morte do processo, mas uma abertura fria volta ao board —
    // que é o que "o app abre no board" quer dizer.
    var vista by rememberSaveable { mutableStateOf(VistaSaldos.BOARD) }
```

4. No `LaunchedEffect(destino)`, no ramo `is Destino.Saldos`, acrescente `vista = VistaSaldos.LISTA` antes de trocar a aba:

```kotlin
            is Destino.Saldos -> {
                // Quem chega por widget ou lembrete pediu um dia, não um panorama.
                vista = VistaSaldos.LISTA
                ledgerVm.irPara(destino.mes, destino.dia)
                tab = SaldoTab.SALDOS
            }
```

5. No `when (tab)`, troque o ramo `SaldoTab.SALDOS` por:

```kotlin
                    SaldoTab.SALDOS -> if (vista == VistaSaldos.BOARD) {
                        BoardScreen(
                            state = boardState,
                            onDiaClick = { data ->
                                ledgerVm.irPara(YearMonth.from(data), data.dayOfMonth)
                                vista = VistaSaldos.LISTA
                            },
                            onVerLista = { vista = VistaSaldos.LISTA },
                            onTogglePrivacidade = privacidade::alternar,
                        )
                    } else {
                        LedgerScreen(
                            // … os mesmos argumentos de hoje …
                            onVerBoard = { vista = VistaSaldos.BOARD },
                        )
                    }
```

- [ ] **Step 4: Compile and install**

Run: `mise run build` e depois `mise run install` num emulador ligado.
Expected: BUILD SUCCESSFUL, app abre na grade.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/scholze/saldo/ui/SaldoApp.kt \
        app/src/main/kotlin/com/scholze/saldo/ui/board/BoardScreen.kt \
        app/src/main/kotlin/com/scholze/saldo/ui/ledger/LedgerScreen.kt
git commit -m "feat: o board vira a vista padrão da aba saldos"
```

---

### Task 7: Testes instrumentados

**Files:**
- Create: `app/src/androidTest/kotlin/com/scholze/saldo/ui/board/BoardScreenTest.kt`
- Create: `app/src/androidTest/kotlin/com/scholze/saldo/VistaSaldosTest.kt`

**Interfaces:**
- Consumes: `TAG_BOARD_GRADE`, `tagCelula`, `TAG_BOARD_LEGENDA`, `BoardUiState`, `EstadoLimpo` (o helper que os outros testes instrumentados já usam).

- [ ] **Step 1: Write `BoardScreenTest`**

Casos, um `@Test` cada, montando `BoardScreen` com um `BoardUiState` fabricado à mão
(sem banco):

1. `grade_desenhaUmaCelulaPorDia` — 365 nós com tag `board:celula:*` existem.
2. `celula_temDescricaoDeAcessibilidade` — o dia com saída diz "saiu R$ …".
3. `celula_semMovimentacaoDizSemMovimentacao`.
4. `celula_comFaturaAnunciaOVencimento` — descrição termina em "fatura vence".
5. `privacidade_mascaraOValorMasNaoApagaAGrade` — com `PrivacyState(oculto = true)` a
   descrição traz `MASCARA_PRIVACIDADE` e os 365 nós continuam lá.
6. `legenda_semDiaTipicoDizAinda` — `unidadeCentavos = 0` → texto "ainda sem um dia típico".
7. `toqueNoDia_chamaOnDiaClickComADataCerta`.
8. `fonteGrande_naoQuebraAGrade` — `LocalDensity(fontScale = 2f)` e as células continuam.

- [ ] **Step 2: Write `VistaSaldosTest`**

Sobre a `MainActivity` inteira, no estilo do `DeepLinkTest` que já existe:

1. `appAbreNoBoard` — `TAG_BOARD_GRADE` existe no arranque.
2. `toggleVaiParaAListaEVolta` — toca "ver como lista", o hero do ledger aparece; toca
   "ver como grade", a grade volta.
3. `deepLinkDeDiaCaiNaLista` — um Intent `Destino.Saldos(mes, dia)` abre a lista, não a
   grade.

- [ ] **Step 3: Run the device suite**

Run: `mise exec -- emulator -avd saldo_test -no-window -no-audio -no-snapshot &` e depois
`mise run test-device`
Expected: PASS. **Rode duas vezes** se a suíte tiver acabado de reinstalar o app.

- [ ] **Step 4: Commit**

```bash
git add app/src/androidTest/kotlin/com/scholze/saldo/ui/board/BoardScreenTest.kt \
        app/src/androidTest/kotlin/com/scholze/saldo/VistaSaldosTest.kt
git commit -m "test: board na tela — grade, toque, privacidade e fonte grande"
```

---

### Task 8: README e capturas

**Files:**
- Modify: `README.md`
- Create: `docs/superpowers/screenshots/2026-09-04-board/` (capturas claro e escuro)

- [ ] **Step 1: Update the README**

Acrescente, depois do parágrafo de exportação:

```markdown
O app abre no **board**: os últimos 12 meses em quadradinhos, um por dia, coloridos pelo
saldo daquele dia — rosa saiu mais, verde entrou mais, e a intensidade sai de múltiplos de
um dia típico. O anel marca o dia em que a fatura vence. Toque num dia para abrir o mês
naquele dia; o ícone de lista no cabeçalho leva ao ledger de sempre.
```

- [ ] **Step 2: Capture both themes**

No emulador, com dados de exemplo: uma captura da grade no tema claro e uma no escuro,
salvas como `01-board-claro.png` e `02-board-escuro.png`.

- [ ] **Step 3: Commit**

```bash
git add README.md docs/superpowers/screenshots/2026-09-04-board/
git commit -m "docs: board no README, com capturas dos dois temas"
```

---

## Self-Review

**Cobertura do spec:** decisão de célula → Task 1; grade em pé → Task 5; toggle no
cabeçalho → Tasks 3 e 6; cartão colorindo a compra e anel no vencimento → Task 1
(`valorCentavos`, `venceFatura`) e Task 5 (a borda); escala por múltiplos do dia típico →
Task 1 (`nivelDe`, `mediana`); janela de 12 meses → Task 1; privacidade → Task 5
(`descricaoDe`) e Task 7 caso 5; fonte grande → Task 5 (`ESCALA_SEM_NUMERO`) e Task 7 caso
8; acessibilidade → Task 5 e Task 7 casos 2–4; deep link intocado → Task 6 e Task 7 caso 3.

**Fora de escopo confirmado:** widget do board, comparação ano a ano, células de futuro, e
a captura de notificações (spec 2, interview ainda por fazer).
