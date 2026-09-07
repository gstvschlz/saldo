# saldo — uso diário Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** O app para de surpreender quem o usa todo dia: Voltar volta, o `+` lança no dia aberto, a lista do mês e a busca existem, uma recorrência nasce e para pela sheet (e pausa na tela dela), uma tag apagada volta e muda de cor, as telas vazias explicam, e girar o aparelho não perde nada.

**Architecture:** Duas funções puras novas em `domain/` (`Busca`, `PaletaTags`), seis métodos novos no repositório (recorrência: converter, encerrar, pausar, retomar; tag: snapshot/restaurar, recolorir) cobertos por `RepositoryTest`, e o resto é UI: um `BackHandler` só em `SaldoApp`, o estado da aba `saldos` vira um enum de três vistas, a `SaldoTopBar` aprende três coisas (seta desabilitada, ação de lista, modo busca), e o estado de navegação dos ViewModels vai para `SavedStateHandle`. **Nenhuma mudança de schema do Room.**

**Tech Stack:** Kotlin 2.2, Compose + Material 3, Room 2.8 (sem migração), `SavedStateHandle` (lifecycle 2.11, já no classpath via `lifecycle-viewmodel-compose`), JUnit4. **Nenhuma dependência nova.**

## Global Constraints

- **Spec:** `docs/superpowers/specs/2026-09-07-saldo-uso-diario-design.md`. Onde plano e spec discordarem, o plano ganha.
- **Baseline:** branch `uso-diario-1`, criada de `main` em `8aca28f`.
- **Nenhuma migração do Room.** `ativa`, `criadaEm`, `editadaManualmente` já existem; nada novo em `Entities.kt`.
- **Dinheiro em `Long` de centavos.** Nunca `Float`/`Double` para valor.
- **Copy em pt-BR, minúsculas**, como o resto do app ("nova movimentação", "ver como lista").
- **"repetir: não" numa recorrência encerra a série** — sem diálogo de escopo (decisão 1 do spec).
- **Pausar não apaga o passado nem inventa o meio** (decisão 3 do spec).
- **Totais continua sem limite para o futuro**; só o board ganha a seta desabilitada.
- **Cada task termina verde:** `mise run test` e `mise exec -- ./gradlew lintDebug` com 0 erros; as tasks com Android de verdade acrescentam `mise run test-device` (emulador `saldo_test` ligado: `mise exec -- emulator -avd saldo_test -no-window -no-audio &`).
- **Gradle no PowerShell pendura com pipe.** Rode com `Start-Process -RedirectStandardOutput` ou pelo Bash (`mise exec -- ./gradlew …`), nunca `gradlew … | tee`. Ver memória `saldo-gradle-shell-gotcha`.
- **`connectedDebugAndroidTest` não aceita `--tests`**: filtre com `-Pandroid.testInstrumentationRunnerArguments.class=com.scholze.saldo.X`.
- O repositório tem `core.autocrlf=true`. Não reescreva arquivos por causa de fim de linha; confira com `od -c | head` se a Edit tool trocar LF por CRLF.
- Cliques de dígito nos testes ficam escopados em `TAG_TECLADO` (a sheet fica por cima do board e o número do dia é um dígito legítimo). Nós repetidos distinguem-se por contagem ou `hasClickAction`.

## File structure

| File | Responsibility |
|---|---|
| `domain/PaletaTags.kt` (create) | as seis cores e `proxima(usadas)` — substitui as duas listas |
| `domain/Busca.kt` (create) | `Busca.filtrar(efetivas, consulta, hoje)` — normalização e casamento |
| `domain/InsightsEngine.kt` (modify) | `recorrencias`: pausada continua na lista, fora das somas |
| `data/db/Daos.kt` (modify) | `TagDao.recolor`, `insertComId`, `movimentacoesDaTag`, `recorrenciasDaTag`; `MovimentacaoDao.desligarDaRecorrencia`, `ligarARecorrencia`; `RecorrenciaDao.definirAtiva`, `todos` |
| `data/SaldoRepository.kt` (modify) | `converterEmRecorrencia`, `encerrarRecorrencia`, `pausar`, `retomar`, `excluirTag` → `TagSnapshot`, `restaurarTag`, `recolorirTag` |
| `ui/components/M3.kt` (modify) | `SaldoTopBar`: `podeAvancar`, `busca` (campo no lugar da barra) |
| `ui/components/Icons.kt` (modify) | `SaldoIcon.LISTA`, `SaldoIcon.LUPA` |
| `ui/board/BoardViewModel.kt` (modify) | `SavedStateHandle`; `fecharDia` sai |
| `ui/board/BoardScreen.kt` (modify) | seta desabilitada, ação "ver como lista", vazio |
| `ui/ledger/LedgerViewModel.kt` (modify) | `SavedStateHandle`, `busca`, `resultados`, `abrirResultado` |
| `ui/ledger/LedgerScreen.kt` (modify) | lupa, `ResultadosBusca`, vazio da busca |
| `ui/SaldoApp.kt` (modify) | `BackHandler`, `VistaSaldos`, FAB com dia aberto, snackbar de tag, diálogo de exportar |
| `ui/entry/AmountKeypadScreen.kt` (modify) | tecla `00` |
| `ui/entry/EntryViewModel.kt` (modify) | `repetirOriginal`, os três casos em `salvar`, dia do template |
| `ui/entry/NewEntrySheet.kt` (modify) | repetir editável na edição, `rememberSaveable`, "cancelar" no diálogo de tags |
| `ui/totais/RecorrenciasViewModel.kt` (modify) | `pausar`, `retomar` |
| `ui/totais/RecorrenciasScreen.kt` (modify) | switch "pausada" |
| `ui/totais/SegmentoTendencia.kt` (modify) | "precisa de mais um mês" |
| `ui/tags/TagsViewModel.kt` (modify) | `exclusoes`, `recolorir`, cor pela paleta |
| `ui/tags/TagsScreen.kt` (modify) | cor no renomear, vazio, `IconeRedondo` |
| `ui/privacy/Privacy.kt` (modify) | `Saver` + `rememberSaveable` |
| `ui/totais/TotaisViewModel.kt` (modify) | `SavedStateHandle` |
| `ui/mais/MaisScreen.kt` (modify) | `rememberSaveable` |

---

### Task 1: `PaletaTags` — uma paleta só

**Files:**
- Create: `app/src/main/kotlin/com/scholze/saldo/domain/PaletaTags.kt`
- Test: `app/src/test/kotlin/com/scholze/saldo/domain/PaletaTagsTest.kt` (create)
- Modify: `app/src/main/kotlin/com/scholze/saldo/ui/entry/EntryViewModel.kt:35,159-160`
- Modify: `app/src/main/kotlin/com/scholze/saldo/ui/tags/TagsScreen.kt:37,131`

**Interfaces:**
- Produces: `object PaletaTags { val cores: List<Long>; fun proxima(usadas: List<Long>): Long }`
- Consumes: nada.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.scholze.saldo.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class PaletaTagsTest {

    @Test fun `sao seis cores`() = assertEquals(6, PaletaTags.cores.size)

    @Test fun `sem tag nenhuma a primeira cor e a primeira da paleta`() =
        assertEquals(PaletaTags.cores[0], PaletaTags.proxima(emptyList()))

    @Test fun `a proxima e a menos usada`() {
        val c = PaletaTags.cores
        // as cores 0 e 1 já foram usadas; 2 é a primeira livre
        assertEquals(c[2], PaletaTags.proxima(listOf(c[0], c[1])))
    }

    @Test fun `com todas usadas uma vez volta a primeira`() =
        assertEquals(PaletaTags.cores[0], PaletaTags.proxima(PaletaTags.cores))

    @Test fun `desempate pela ordem da paleta`() {
        val c = PaletaTags.cores
        // 0 usada duas vezes, 1..5 uma vez cada: todas as cinco empatam, e a 1 vem antes
        assertEquals(c[1], PaletaTags.proxima(listOf(c[0], c[0]) + c.drop(1)))
    }

    @Test fun `cor fora da paleta nao conta`() =
        assertEquals(PaletaTags.cores[0], PaletaTags.proxima(listOf(0xFF000000L)))
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mise exec -- ./gradlew :app:testDebugUnitTest --tests "com.scholze.saldo.domain.PaletaTagsTest"`
Expected: FAIL — `Unresolved reference: PaletaTags`.

- [ ] **Step 3: Write minimal implementation**

`app/src/main/kotlin/com/scholze/saldo/domain/PaletaTags.kt`:

```kotlin
package com.scholze.saldo.domain

/**
 * As cores de tag. Uma lista só, para a sheet e a aba tags: antes eram duas (cinco e seis
 * cores) e uma tag criada num lugar tirava de um rodízio diferente da criada no outro.
 *
 * [proxima] devolve a cor menos usada entre as seis; empate vai pela ordem da paleta. Uma
 * cor que não está na paleta (importada, ou de uma versão antiga) não conta.
 */
object PaletaTags {
    val cores: List<Long> = listOf(0xFFA6486BL, 0xFFB95A2EL, 0xFF2A7A86L, 0xFF4B4BC4L, 0xFF14663AL, 0xFFE58A5AL)

    fun proxima(usadas: List<Long>): Long {
        val contagem = usadas.filter { it in cores }.groupingBy { it }.eachCount()
        return cores.minBy { contagem[it] ?: 0 }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mise exec -- ./gradlew :app:testDebugUnitTest --tests "com.scholze.saldo.domain.PaletaTagsTest"`
Expected: PASS (6 tests).

- [ ] **Step 5: Use it in the two creation paths**

`EntryViewModel.kt`: remove a linha `private val CORES_TAG = listOf(...)` (linha 35) e troque, em `criarTagInline`:

```kotlin
                val cor = PaletaTags.proxima(state.value.todasTags.map { it.cor })
```

(adicione `import com.scholze.saldo.domain.PaletaTags`).

`TagsScreen.kt`: remove `private val CORES = listOf(...)` (linha 37) e troque, no diálogo "nova tag":

```kotlin
                    if (nome.isNotBlank()) vm.criar(nome.trim(), PaletaTags.proxima(state.tags.map { it.first.cor }))
```

(adicione `import com.scholze.saldo.domain.PaletaTags`).

- [ ] **Step 6: Run the JVM suite and lint**

Run: `mise run test` e `mise exec -- ./gradlew lintDebug`
Expected: verde, 0 erros de lint.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/kotlin/com/scholze/saldo/domain/PaletaTags.kt app/src/test/kotlin/com/scholze/saldo/domain/PaletaTagsTest.kt app/src/main/kotlin/com/scholze/saldo/ui/entry/EntryViewModel.kt app/src/main/kotlin/com/scholze/saldo/ui/tags/TagsScreen.kt
git commit -m "feat: uma paleta só para as tags, e a próxima cor é a menos usada"
```

---

### Task 2: `Busca` — o filtro puro

**Files:**
- Create: `app/src/main/kotlin/com/scholze/saldo/domain/Busca.kt`
- Test: `app/src/test/kotlin/com/scholze/saldo/domain/BuscaTest.kt` (create)

**Interfaces:**
- Produces: `object Busca { fun normalizar(s: String): String; fun filtrar(efetivas: List<Movimentacao>, consulta: String, hoje: LocalDate): List<Movimentacao> }`
- Consumes: `Movimentacao`, `Tag` (`domain/Modelos.kt`).

- [ ] **Step 1: Write the failing test**

```kotlin
package com.scholze.saldo.domain

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class BuscaTest {

    private val hoje = LocalDate.parse("2026-09-07")
    private val mercado = Tag(id = 1, nome = "mercado", cor = 1L)

    private fun mov(id: Long, descricao: String, centavos: Long, dia: String, vararg tags: Tag) = Movimentacao(
        id = id, descricao = descricao, valorCentavos = centavos, data = LocalDate.parse(dia),
        natureza = Natureza.DIARIO, tags = tags.toList(),
    )

    private val todas = listOf(
        mov(1, "Pão de Açúcar", -340_00, "2026-04-12", mercado),
        mov(2, "uber", -23_90, "2026-09-01"),
        mov(3, "salário", 7_400_00, "2026-09-05"),
        mov(4, "aluguel", -1_690_00, "2026-09-10"),      // futuro: não entra
        mov(5, "farmácia", -16_90, "2026-08-20"),
    )

    private fun busca(q: String) = Busca.filtrar(todas, q, hoje).map { it.id }

    @Test fun `consulta em branco devolve vazio`() = assertEquals(emptyList<Long>(), busca("   "))

    @Test fun `descricao sem acento casa com acento`() = assertEquals(listOf(1L), busca("pao de acucar"))

    @Test fun `maiusculas nao importam`() = assertEquals(listOf(1L), busca("PÃO"))

    @Test fun `nome da tag casa`() = assertEquals(listOf(1L), busca("merc"))

    @Test fun `valor em reais casa`() = assertEquals(listOf(1L), busca("340"))

    @Test fun `valor com virgula casa`() = assertEquals(listOf(5L), busca("16,90"))

    @Test fun `so digitos casa reais e centavos`() {
        // 1690 lido como reais = 169000 (nada); lido como centavos = 1690 → farmácia
        assertEquals(listOf(5L), busca("1690"))
    }

    @Test fun `o futuro nao entra`() = assertEquals(emptyList<Long>(), busca("aluguel"))

    @Test fun `ordem decrescente por data`() = assertEquals(listOf(3L, 2L, 5L, 1L), busca("a"))

    @Test fun `normalizar tira acento e caixa`() = assertEquals("pao de acucar", Busca.normalizar(" Pão de Açúcar "))
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mise exec -- ./gradlew :app:testDebugUnitTest --tests "com.scholze.saldo.domain.BuscaTest"`
Expected: FAIL — `Unresolved reference: Busca`.

- [ ] **Step 3: Write minimal implementation**

`app/src/main/kotlin/com/scholze/saldo/domain/Busca.kt`:

```kotlin
package com.scholze.saldo.domain

import java.text.Normalizer
import java.time.LocalDate
import kotlin.math.abs

/**
 * A busca do ledger: descrição, nome da tag ou valor, sobre as movimentações *efetivas* (as
 * linhas do banco mais as ocorrências virtuais) até hoje. Só fato consumado, como o board.
 *
 * Uma consulta só de dígitos (com vírgula ou ponto opcional) é lida como dinheiro dos dois
 * jeitos — "340" acha R$ 340,00, e "1690" acha tanto R$ 1.690,00 quanto R$ 16,90 —, porque
 * quem digita um número lembra do valor, não de como o teclado o formatou.
 */
object Busca {

    private val marcas = Regex("\\p{M}+")
    private val dinheiro = Regex("^\\d{1,9}([.,]\\d{1,2})?$")

    fun normalizar(s: String): String =
        Normalizer.normalize(s.trim().lowercase(), Normalizer.Form.NFD).replace(marcas, "")

    fun filtrar(efetivas: List<Movimentacao>, consulta: String, hoje: LocalDate): List<Movimentacao> {
        val q = normalizar(consulta)
        if (q.isEmpty()) return emptyList()
        val centavos = centavosDe(q)
        return efetivas
            .asSequence()
            .filter { it.data <= hoje }
            .filter { m ->
                normalizar(m.descricao).contains(q) ||
                    m.tags.any { normalizar(it.nome).contains(q) } ||
                    (centavos.isNotEmpty() && abs(m.valorCentavos) in centavos)
            }
            .sortedByDescending { it.data }
            .toList()
    }

    /** Os valores em centavos que uma consulta numérica pode significar; vazio se não é número. */
    private fun centavosDe(q: String): Set<Long> {
        if (!dinheiro.matches(q)) return emptySet()
        val partes = q.split(',', '.')
        val reais = partes[0].toLong()
        val fracao = partes.getOrNull(1)
        return if (fracao != null) {
            setOf(reais * 100 + fracao.padEnd(2, '0').toLong())
        } else {
            setOf(reais * 100, reais)
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mise exec -- ./gradlew :app:testDebugUnitTest --tests "com.scholze.saldo.domain.BuscaTest"`
Expected: PASS (10 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/scholze/saldo/domain/Busca.kt app/src/test/kotlin/com/scholze/saldo/domain/BuscaTest.kt
git commit -m "feat: o motor da busca — descrição, tag ou valor, sobre as efetivas até hoje"
```

---

### Task 3: Repositório — converter, encerrar, pausar, retomar

**Files:**
- Modify: `app/src/main/kotlin/com/scholze/saldo/data/db/Daos.kt`
- Modify: `app/src/main/kotlin/com/scholze/saldo/data/SaldoRepository.kt`
- Test: `app/src/androidTest/kotlin/com/scholze/saldo/data/RepositoryTest.kt` (append)

**Interfaces:**
- Produces (em `SaldoRepository`):
  - `suspend fun converterEmRecorrencia(mov: Movimentacao, diaDoMes: Int)`
  - `suspend fun encerrarRecorrencia(mov: Movimentacao)`
  - `suspend fun pausar(recorrenciaId: Long, hoje: LocalDate)`
  - `suspend fun retomar(recorrenciaId: Long, hoje: LocalDate)`
- Consumes: `RecurrenceExpander.ocorrenciaNoMes`, `materializar` (privado, já existe).

- [ ] **Step 1: Write the failing tests** (append ao fim de `RepositoryTest`, dentro da classe)

```kotlin
    // ---- converter / encerrar / pausar / retomar (uso-diario-1) ----

    private suspend fun linhas() = repo.ledger.first().movimentacoes.sortedBy { it.data }
    private suspend fun templates() = repo.ledger.first().recorrencias

    @Test
    fun converterEmRecorrenciaLigaALinhaESemeiaOsMesesAbertosDepois() = runBlocking {
        repo.criar(mov("2026-07-10", -80_00), RepetirOpcao.Nao)
        repo.abrirMes(YearMonth.of(2026, 8))   // agosto aberto ANTES de converter
        val avulsa = linhas().single()
        repo.converterEmRecorrencia(avulsa, diaDoMes = 10)

        val t = templates().single()
        assertEquals(10, t.diaDoMes)
        assertEquals(YearMonth.of(2026, 7), t.inicio)
        val julho = linhas().first { it.data == LocalDate.parse("2026-07-10") }
        assertEquals(t.id, julho.recorrenciaId)
        assertEquals(avulsa.id, julho.id)                       // a mesma linha, não uma cópia
        assertEquals(
            listOf("2026-07-10", "2026-08-10"),
            linhas().map { it.data.toString() },
        )
    }

    @Test
    fun converterComDiaDiferenteDaDataMarcaALinhaComoEditada() = runBlocking {
        repo.criar(mov("2026-07-10", -80_00), RepetirOpcao.Nao)
        repo.converterEmRecorrencia(linhas().single(), diaDoMes = 31)
        val julho = linhas().single()
        assertEquals(true, julho.editadaManualmente)
        assertEquals(31, templates().single().diaDoMes)
    }

    @Test
    fun encerrarRecorrenciaDesligaALinhaEApagaAsFuturas() = runBlocking {
        repo.criar(mov("2026-07-15", -50_00), RepetirOpcao.TodoMes(15))
        repo.abrirMes(YearMonth.of(2026, 8))
        repo.abrirMes(YearMonth.of(2026, 9))
        val agosto = linhas().first { it.data == LocalDate.parse("2026-08-15") }
        repo.encerrarRecorrencia(agosto)

        val t = templates().single()
        assertEquals(YearMonth.of(2026, 7), t.fim)
        val restantes = linhas()
        assertEquals(listOf("2026-07-15", "2026-08-15"), restantes.map { it.data.toString() })
        val ago = restantes.first { it.data == LocalDate.parse("2026-08-15") }
        assertEquals(null, ago.recorrenciaId)                   // virou avulsa
        assertEquals(false, ago.editadaManualmente)
    }

    @Test
    fun encerrarNoPrimeiroMesApagaOTemplate() = runBlocking {
        repo.criar(mov("2026-07-15", -50_00), RepetirOpcao.TodoMes(15))
        repo.encerrarRecorrencia(linhas().single())
        assertEquals(0, templates().size)
        val unica = linhas().single()
        assertEquals(null, unica.recorrenciaId)
    }

    @Test
    fun pausarMantemOMesCorrenteEApagaAsFuturasNaoEditadas() = runBlocking {
        repo.criar(mov("2026-07-15", -50_00), RepetirOpcao.TodoMes(15))
        repo.abrirMes(YearMonth.of(2026, 8))
        repo.abrirMes(YearMonth.of(2026, 9))
        // setembro editada à mão: sobrevive à pausa
        val set = linhas().first { it.data == LocalDate.parse("2026-09-15") }
        repo.editar(set.copy(valorCentavos = -99_00), EscopoEdicao.SO_ESTE_MES)

        repo.pausar(templates().single().id, hoje)              // hoje = 2026-07-20

        assertEquals(false, templates().single().ativa)
        assertEquals(listOf("2026-07-15", "2026-09-15"), linhas().map { it.data.toString() })
    }

    @Test
    fun retomarDeixaOIntervaloVazioESemeiaOMesCorrente() = runBlocking {
        repo.criar(mov("2026-05-15", -50_00), RepetirOpcao.TodoMes(15))
        val id = templates().single().id
        repo.pausar(id, LocalDate.parse("2026-05-20"))
        // julho aberto enquanto pausada: nada da recorrência entra
        repo.abrirMes(YearMonth.of(2026, 7))
        assertEquals(listOf("2026-05-15"), linhas().map { it.data.toString() })

        repo.retomar(id, hoje)                                  // hoje = 2026-07-20

        assertEquals(true, templates().single().ativa)
        val input = repo.ledger.first()
        // junho ficou materializado (vazio para esta recorrência) e julho ganhou a instância
        assertEquals(true, YearMonth.of(2026, 6) in input.mesesMaterializados)
        assertEquals(listOf("2026-05-15", "2026-07-15"), linhas().map { it.data.toString() })
        // e a projeção não inventa junho
        val junho = ProjectionEngine.movimentacoesDoMes(input, YearMonth.of(2026, 6))
        assertEquals(0, junho.size)
    }
```

Acrescente os imports `com.scholze.saldo.domain.ProjectionEngine` no topo do arquivo.

- [ ] **Step 2: Run tests to verify they fail**

Run: `mise exec -- ./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.scholze.saldo.data.RepositoryTest`
Expected: compilação falha — `Unresolved reference: converterEmRecorrencia`.

- [ ] **Step 3: DAO methods**

Em `Daos.kt`, dentro de `MovimentacaoDao`, depois de `countInstancias`:

```kotlin
    /** A linha vira avulsa: sem template e sem a marca de editada (que só faz sentido numa instância). */
    @Query("UPDATE movimentacoes SET recorrenciaId = NULL, editadaManualmente = 0 WHERE id = :id")
    suspend fun desligarDaRecorrencia(id: Long)

    @Query("UPDATE movimentacoes SET recorrenciaId = :recorrenciaId, editadaManualmente = :editada WHERE id = :id")
    suspend fun ligarARecorrencia(id: Long, recorrenciaId: Long, editada: Boolean)
```

Dentro de `RecorrenciaDao`, depois de `observeAll`:

```kotlin
    /** Leitura única para dentro de transações — um Flow não participa da transação. */
    @Transaction
    @Query("SELECT * FROM recorrencias ORDER BY diaDoMes")
    suspend fun todos(): List<RecorrenciaComTags>

    @Query("UPDATE recorrencias SET ativa = :ativa WHERE id = :id")
    suspend fun definirAtiva(id: Long, ativa: Boolean)
```

- [ ] **Step 4: Repository interface**

Em `SaldoRepository.kt`, na interface, depois de `excluirRecorrencia`:

```kotlin
    /**
     * Uma avulsa vira mensal: cria o template a partir dela (começa no mês dela, no dia
     * [diaDoMes]), liga a linha e semeia todo mês já materializado depois. A linha fica
     * marcada como editada se a data dela não cai em [diaDoMes] — é o mesmo sinal que uma
     * instância movida de dia carrega.
     */
    suspend fun converterEmRecorrencia(mov: Movimentacao, diaDoMes: Int)

    /**
     * "repetir: não" numa instância: a série acaba no mês anterior ao de [mov], a linha vira
     * avulsa, as instâncias não editadas do mês seguinte em diante somem. Se a série acabaria
     * antes de começar, o template é apagado.
     */
    suspend fun encerrarRecorrencia(mov: Movimentacao)

    /** Desliga o template e apaga as instâncias não editadas do mês seguinte a [hoje] em diante. */
    suspend fun pausar(recorrenciaId: Long, hoje: LocalDate)

    /**
     * Liga o template de volta. Os meses entre a pausa e [hoje] que nunca foram abertos são
     * materializados ANTES de religar — ficam vazios para este template, e é isso que
     * "pausada" quer dizer. O mês de [hoje], se já estava materializado sem instância, ganha
     * a dele.
     */
    suspend fun retomar(recorrenciaId: Long, hoje: LocalDate)
```

- [ ] **Step 5: Repository implementation**

Em `RoomSaldoRepository`, depois de `excluirRecorrencia`:

```kotlin
    override suspend fun converterEmRecorrencia(mov: Movimentacao, diaDoMes: Int) = db.withTransaction {
        require(mov.id != 0L) { "movimentação virtual — abra o mês antes de converter" }
        require(mov.recorrenciaId == null) { "a movimentação já é de uma recorrência" }
        val inicio = YearMonth.from(mov.data)
        val recId = recDao.insert(
            Recorrencia(
                descricao = mov.descricao, valorCentavos = mov.valorCentavos,
                natureza = mov.natureza, diaDoMes = diaDoMes, inicio = inicio,
            ).toEntity(),
        )
        recDao.setTags(recId, mov.tags.map { it.id })
        movDao.ligarARecorrencia(mov.id, recId, editada = mov.data.dayOfMonth != diaDoMes)
        val template = Recorrencia(
            id = recId, descricao = mov.descricao, valorCentavos = mov.valorCentavos,
            natureza = mov.natureza, diaDoMes = diaDoMes, inicio = inicio, tags = mov.tags,
        )
        val marcados = mesDao.todos().map { it.toYearMonth() }.toSet()
        marcados.filter { it > inicio }.forEach { m ->
            RecurrenceExpander.ocorrenciaNoMes(template, m)?.let { insertComTags(it) }
        }
        // Mesma razão de `criar`: o mês da linha precisa ficar materializado, e marcar um mês
        // nunca aberto desliga a expansão virtual das outras recorrências nele.
        if (inicio !in marcados) materializar(inicio, excetoRecorrenciaId = recId)
    }

    override suspend fun encerrarRecorrencia(mov: Movimentacao) = db.withTransaction {
        require(mov.id != 0L) { "movimentação virtual — abra o mês antes de encerrar" }
        val recId = requireNotNull(mov.recorrenciaId) { "a movimentação não é de uma recorrência" }
        val mes = YearMonth.from(mov.data)
        val template = recDao.todos().first { it.rec.id == recId }.toDomain()
        movDao.desligarDaRecorrencia(mov.id)
        movDao.deleteInstanciasNaoEditadasAPartirDe(recId, mes.plusMonths(1).atDay(1).toEpochDay())
        val fim = mes.minusMonths(1)
        if (fim < template.inicio) recDao.deleteById(recId)
        else recDao.update(template.copy(fim = fim).toEntity())
    }

    override suspend fun pausar(recorrenciaId: Long, hoje: LocalDate) = db.withTransaction {
        val template = recDao.todos().first { it.rec.id == recorrenciaId }.toDomain()
        val mesHoje = YearMonth.from(hoje)
        // Congela o passado ANTES de desligar, como `editar(DAQUI_EM_DIANTE)` faz: um mês entre
        // o início e hoje que nunca foi aberto ainda é expandido virtualmente, e com o template
        // inativo a expansão sumiria — a academia de fevereiro deixaria de ter existido.
        // Materializado agora, com o template ativo, ele guarda a ocorrência.
        congelarAte(template.inicio, mesHoje)
        movDao.deleteInstanciasNaoEditadasAPartirDe(recorrenciaId, mesHoje.plusMonths(1).atDay(1).toEpochDay())
        recDao.definirAtiva(recorrenciaId, false)
    }

    override suspend fun retomar(recorrenciaId: Long, hoje: LocalDate) = db.withTransaction {
        val template = recDao.todos().first { it.rec.id == recorrenciaId }.toDomain()
        val mesHoje = YearMonth.from(hoje)
        // O passado até a pausa já está congelado (ver `pausar`); o que sobra sem abrir é o
        // intervalo da pausa. Materializá-lo com o template AINDA inativo deixa esses meses
        // vazios para ele — as outras recorrências ganham as suas linhas — e é isso que
        // "pausada" quer dizer.
        congelarAte(template.inicio, mesHoje.minusMonths(1))
        recDao.definirAtiva(recorrenciaId, true)
        val marcados = mesDao.todos().map { it.toYearMonth() }.toSet()
        val ativo = template.copy(ativa = true)
        if (mesHoje in marcados) {
            val existentes = movDao.countInstancias(
                recorrenciaId, mesHoje.atDay(1).toEpochDay(), mesHoje.atEndOfMonth().toEpochDay(),
            )
            if (existentes == 0) RecurrenceExpander.ocorrenciaNoMes(ativo, mesHoje)?.let { insertComTags(it) }
        }
    }

    /** Materializa todo mês de [de] a [ate] (inclusive) que ainda não foi aberto, com os templates como estão. */
    private suspend fun congelarAte(de: YearMonth, ate: YearMonth) {
        val marcados = mesDao.todos().map { it.toYearMonth() }.toSet()
        var m = de
        while (m <= ate) {
            if (m !in marcados) materializar(m)
            m = m.plusMonths(1)
        }
    }
```

E troque, em `excluirRecorrencia` e em `materializar`, `recDao.observeAll().first()` por `recDao.todos()` (leitura de transação). Em `editar(DAQUI_EM_DIANTE)`, o laço `while (passado < mesInicio) { … }` vira `congelarAte(templateAntigo.inicio, mesInicio.minusMonths(1))` — é a mesma coisa que ele já fazia. Acrescente `import java.time.LocalDate` se ainda não houver.

Acrescente também este teste ao bloco do Step 1 — é o caso que a versão ingênua de `pausar` erraria:

```kotlin
    @Test
    fun pausarCongelaOsMesesNuncaAbertosAntesDeDesligar() = runBlocking {
        repo.criar(mov("2026-04-15", -50_00), RepetirOpcao.TodoMes(15))
        // maio e junho nunca abertos: hoje só existem como expansão virtual
        repo.pausar(templates().single().id, hoje)              // hoje = 2026-07-20
        val input = repo.ledger.first()
        assertEquals(
            listOf("2026-04-15", "2026-05-15", "2026-06-15", "2026-07-15"),
            input.movimentacoes.map { it.data.toString() }.sorted(),
        )
        assertEquals(true, YearMonth.of(2026, 5) in input.mesesMaterializados)
    }
```

- [ ] **Step 6: Run the repository tests**

Run: `mise exec -- ./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.scholze.saldo.data.RepositoryTest`
Expected: PASS, incluindo os seis novos. Se `retomarDeixaOIntervaloVazio…` falhar em `junho.size`, confira que `materializar(m)` rodou com `ativa = false` ainda gravado (a ordem `materializar` → `definirAtiva` é o que garante o vazio).

- [ ] **Step 7: Run JVM tests and lint**

Run: `mise run test` e `mise exec -- ./gradlew lintDebug`
Expected: verde.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/kotlin/com/scholze/saldo/data/db/Daos.kt app/src/main/kotlin/com/scholze/saldo/data/SaldoRepository.kt app/src/androidTest/kotlin/com/scholze/saldo/data/RepositoryTest.kt
git commit -m "feat: uma avulsa vira mensal, uma mensal para, e uma recorrência pausa e retoma"
```

---

### Task 4: Repositório — tag com desfazer e cor

**Files:**
- Modify: `app/src/main/kotlin/com/scholze/saldo/data/db/Daos.kt` (`TagDao`)
- Modify: `app/src/main/kotlin/com/scholze/saldo/data/SaldoRepository.kt`
- Modify: `app/src/main/kotlin/com/scholze/saldo/domain/Modelos.kt` (`TagSnapshot`)
- Test: `app/src/androidTest/kotlin/com/scholze/saldo/data/RepositoryTest.kt` (append)

**Interfaces:**
- Produces: `data class TagSnapshot(val tag: Tag, val movimentacaoIds: List<Long>, val recorrenciaIds: List<Long>)`; `SaldoRepository.excluirTag(id): TagSnapshot`, `restaurarTag(snapshot)`, `recolorirTag(id, cor)`.
- Consumes: `TagEntity`, `MovimentacaoTagCross`, `RecorrenciaTagCross`.

- [ ] **Step 1: Write the failing tests** (append em `RepositoryTest`)

```kotlin
    // ---- tags: desfazer e cor (uso-diario-1) ----

    @Test
    fun excluirTagDevolveOSnapshotComOsVinculos() = runBlocking {
        val id = repo.criarTag("mercado", 0xFF112233L)
        val tag = Tag(id = id, nome = "mercado", cor = 0xFF112233L)
        repo.criar(mov("2026-07-10", -80_00).copy(tags = listOf(tag)), RepetirOpcao.Nao)
        repo.criar(mov("2026-07-15", -50_00).copy(tags = listOf(tag)), RepetirOpcao.TodoMes(15))

        val snapshot = repo.excluirTag(id)

        assertEquals(tag, snapshot.tag)
        assertEquals(2, snapshot.movimentacaoIds.size)     // a avulsa e a instância de julho
        assertEquals(1, snapshot.recorrenciaIds.size)
        assertEquals(0, repo.tags.first().size)
        assertEquals(true, linhas().all { it.tags.isEmpty() })
    }

    @Test
    fun restaurarTagTrazAMesmaTagEOsVinculosDeVolta() = runBlocking {
        val id = repo.criarTag("mercado", 0xFF112233L)
        val tag = Tag(id = id, nome = "mercado", cor = 0xFF112233L)
        repo.criar(mov("2026-07-10", -80_00).copy(tags = listOf(tag)), RepetirOpcao.Nao)
        repo.criar(mov("2026-07-15", -50_00).copy(tags = listOf(tag)), RepetirOpcao.TodoMes(15))
        val snapshot = repo.excluirTag(id)

        repo.restaurarTag(snapshot)

        assertEquals(listOf(tag), repo.tags.first())
        assertEquals(true, linhas().all { it.tags == listOf(tag) })
        assertEquals(listOf(tag), templates().single().tags)
    }

    @Test
    fun recolorirTagTrocaSoACor() = runBlocking {
        val id = repo.criarTag("mercado", 0xFF112233L)
        repo.recolorirTag(id, 0xFF445566L)
        assertEquals(Tag(id = id, nome = "mercado", cor = 0xFF445566L), repo.tags.first().single())
    }
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mise exec -- ./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.scholze.saldo.data.RepositoryTest`
Expected: compilação falha — `excluirTag` devolve `Unit`, `restaurarTag` não existe.

- [ ] **Step 3: The snapshot type**

Em `domain/Modelos.kt`, depois de `data class Tag(...)`:

```kotlin
/** O que o "desfazer" de uma tag apagada precisa reinserir: a tag e as duas listas de vínculos. */
data class TagSnapshot(val tag: Tag, val movimentacaoIds: List<Long>, val recorrenciaIds: List<Long>)
```

- [ ] **Step 4: DAO methods** (em `TagDao`)

```kotlin
    @Query("UPDATE tags SET cor = :cor WHERE id = :id") suspend fun recolor(id: Long, cor: Long)

    @Query("SELECT movimentacaoId FROM movimentacao_tags WHERE tagId = :tagId")
    suspend fun movimentacoesDaTag(tagId: Long): List<Long>

    @Query("SELECT recorrenciaId FROM recorrencia_tags WHERE tagId = :tagId")
    suspend fun recorrenciasDaTag(tagId: Long): List<Long>

    /** Reinsere com o id do snapshot — os vínculos apontam para ele. Conflito = já voltou. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertComId(tag: TagEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMovCross(cross: MovimentacaoTagCross)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertRecCross(cross: RecorrenciaTagCross)
```

- [ ] **Step 5: Repository**

Interface: troque `suspend fun excluirTag(id: Long)` por

```kotlin
    /** Apaga a tag (os vínculos caem por CASCADE) e devolve o que [restaurarTag] precisa. */
    suspend fun excluirTag(id: Long): TagSnapshot
    suspend fun restaurarTag(snapshot: TagSnapshot)
    suspend fun recolorirTag(id: Long, cor: Long)
```

Implementação (substitui `override suspend fun excluirTag(id: Long) = tagDao.deleteById(id)`):

```kotlin
    override suspend fun excluirTag(id: Long): TagSnapshot = db.withTransaction {
        val tag = tagDao.observeAll().first().first { it.id == id }.toDomain()
        val snapshot = TagSnapshot(
            tag = tag,
            movimentacaoIds = tagDao.movimentacoesDaTag(id),
            recorrenciaIds = tagDao.recorrenciasDaTag(id),
        )
        tagDao.deleteById(id)
        snapshot
    }

    override suspend fun restaurarTag(snapshot: TagSnapshot) = db.withTransaction {
        tagDao.insertComId(snapshot.tag.toEntity())
        snapshot.movimentacaoIds.forEach { tagDao.insertMovCross(MovimentacaoTagCross(it, snapshot.tag.id)) }
        snapshot.recorrenciaIds.forEach { tagDao.insertRecCross(RecorrenciaTagCross(it, snapshot.tag.id)) }
    }

    override suspend fun recolorirTag(id: Long, cor: Long) = tagDao.recolor(id, cor)
```

Imports novos: `com.scholze.saldo.domain.TagSnapshot`, `com.scholze.saldo.data.db.MovimentacaoTagCross`, `com.scholze.saldo.data.db.RecorrenciaTagCross`. Acrescente `suspend fun todas(): List<TagEntity>` (`@Query("SELECT * FROM tags ORDER BY nome")`) em `TagDao` e use-o no lugar de `tagDao.observeAll().first()` acima — leitura de transação.

- [ ] **Step 6: Run the repository tests**

Run: `mise exec -- ./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.scholze.saldo.data.RepositoryTest`
Expected: PASS. Um `restaurarTag` que devolva a tag mas não os vínculos indica que o `Movimentacao.tags` lido vem de `MovimentacaoComTags` — o `@Relation` lê `movimentacao_tags`, que é onde `insertMovCross` grava; confira o nome da tabela.

- [ ] **Step 7: Fix the only caller** — `TagsViewModel.excluir` ainda compila (`escrever` ignora o retorno). Rode `mise run test` e `lintDebug`.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/kotlin/com/scholze/saldo/domain/Modelos.kt app/src/main/kotlin/com/scholze/saldo/data/db/Daos.kt app/src/main/kotlin/com/scholze/saldo/data/SaldoRepository.kt app/src/androidTest/kotlin/com/scholze/saldo/data/RepositoryTest.kt
git commit -m "feat: apagar uma tag devolve o snapshot para desfazer, e a cor muda"
```

---

### Task 5: `InsightsEngine.recorrencias` — pausada fica na lista

**Files:**
- Modify: `app/src/main/kotlin/com/scholze/saldo/domain/InsightsEngine.kt:263-272`
- Test: `app/src/test/kotlin/com/scholze/saldo/domain/InsightsEngineTest.kt` (append)

**Interfaces:**
- Produces: `ResumoRecorrencias` inalterado em forma; `ativas` passa a incluir templates com `ativa = false` cujo `fim` não passou; `entramMes`/`saemMes` só somam `ativa && inicio <= mes`.
- Consumes: nada novo.

- [ ] **Step 1: Write the failing test** (append em `InsightsEngineTest`; use os helpers de construção de `LedgerInput` que o arquivo já tem — abra-o e siga o padrão dos testes de `recorrencias` existentes)

```kotlin
    @Test
    fun `recorrencia pausada continua na lista mas fora das somas`() {
        val pausada = Recorrencia(
            id = 7, descricao = "academia", valorCentavos = -120_00, natureza = Natureza.DIARIO,
            diaDoMes = 5, inicio = YearMonth.of(2026, 1), ativa = false,
        )
        val ativa = Recorrencia(
            id = 8, descricao = "aluguel", valorCentavos = -1_690_00, natureza = Natureza.DIARIO,
            diaDoMes = 10, inicio = YearMonth.of(2026, 1),
        )
        val input = inputCom(recorrencias = listOf(pausada, ativa))   // helper do arquivo
        val r = InsightsEngine.recorrencias(input, YearMonth.of(2026, 9))
        assertEquals(listOf(7L, 8L), r.ativas.map { it.id })
        assertEquals(emptyList<Recorrencia>(), r.encerradas)
        assertEquals(1_690_00L, r.saemMes)
    }
```

Se o arquivo não tiver um helper `inputCom`, crie-o no topo da classe:

```kotlin
    private fun inputCom(recorrencias: List<Recorrencia>) = LedgerInput(
        saldoInicialCentavos = 0, saldoInicialData = LocalDate.parse("2026-01-01"),
        movimentacoes = emptyList(), recorrencias = recorrencias, mesesMaterializados = emptySet(),
        cartao = CartaoConfig(), hoje = LocalDate.parse("2026-09-07"),
    )
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mise exec -- ./gradlew :app:testDebugUnitTest --tests "com.scholze.saldo.domain.InsightsEngineTest"`
Expected: FAIL — `ativas` = `[8]`, `encerradas` = `[7]`.

- [ ] **Step 3: Implementation**

```kotlin
    fun recorrencias(input: LedgerInput, mes: YearMonth): ResumoRecorrencias {
        // Pausada (`ativa = false`) não é encerrada: continua na lista, com o interruptor, e só
        // sai das somas. Encerrada é a que tem `fim` antes do mês visto.
        val (ativas, encerradas) = input.recorrencias.partition { r -> r.fim?.let { it >= mes } ?: true }
        val vigentes = ativas.filter { it.ativa && it.inicio <= mes }
        return ResumoRecorrencias(
            ativas = ativas.sortedBy { it.diaDoMes },
            encerradas = encerradas.sortedBy { it.diaDoMes },
            entramMes = vigentes.filter { it.valorCentavos > 0 }.sumOf { it.valorCentavos },
            saemMes = -vigentes.filter { it.valorCentavos < 0 }.sumOf { it.valorCentavos },
        )
    }
```

- [ ] **Step 4: Run tests**

Run: `mise exec -- ./gradlew :app:testDebugUnitTest --tests "com.scholze.saldo.domain.InsightsEngineTest"`
Expected: PASS. Se um teste antigo assumia "inativa = encerrada", ajuste-o para usar `fim` — é o que "encerrada" passa a significar.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/scholze/saldo/domain/InsightsEngine.kt app/src/test/kotlin/com/scholze/saldo/domain/InsightsEngineTest.kt
git commit -m "feat: recorrência pausada fica na lista, fora das somas"
```

---

### Task 6: `SaldoTopBar` — seta desabilitada, ação de lista, modo busca

**Files:**
- Modify: `app/src/main/kotlin/com/scholze/saldo/ui/components/Icons.kt` (`SaldoIcon.LISTA`, `SaldoIcon.LUPA`, `SaldoIcon.FECHAR`)
- Modify: `app/src/main/kotlin/com/scholze/saldo/ui/components/M3.kt:134-175`
- Test: `app/src/androidTest/kotlin/com/scholze/saldo/ui/components/M3Test.kt` (append)

**Interfaces:**
- Produces:
  ```kotlin
  @Composable fun SaldoTopBar(
      titulo: String, onAnterior: () -> Unit, onProximo: () -> Unit,
      modifier: Modifier = Modifier, mostrarSetas: Boolean = true, podeAvancar: Boolean = true,
      busca: BuscaTopBar? = null, acoes: @Composable (() -> Unit)? = null,
  )
  data class BuscaTopBar(val texto: String, val onTexto: (String) -> Unit, val onFechar: () -> Unit)
  const val TAG_CAMPO_BUSCA = "topbar:busca"
  @Composable fun IconeRedondo(icon, descricao, onClick, modifier = Modifier, habilitado: Boolean = true)
  ```
  Com `busca != null` a barra inteira (setas, ações e título) dá lugar a um campo de texto com foco automático e um `×` ("fechar busca").
- Consumes: `IconeRedondo`, `SaldoGlyph`.

- [ ] **Step 1: Write the failing tests** (append em `M3Test`; siga o `rule`/`setContent` que o arquivo já usa)

```kotlin
    @Test
    fun setaDeAvancarDesabilitadaNaoTemClique() {
        var avancos = 0
        rule.setContent {
            SaldoTheme { SaldoTopBar(titulo = "setembro 2026", onAnterior = {}, onProximo = { avancos++ }, podeAvancar = false) }
        }
        rule.onNodeWithContentDescription("próximo mês").assertIsNotEnabled()
        rule.onNodeWithContentDescription("próximo mês").performClick()
        assertEquals(0, avancos)
    }

    @Test
    fun modoBuscaTrocaABarraPeloCampo() {
        var texto = ""
        var fechou = false
        rule.setContent {
            SaldoTheme {
                SaldoTopBar(
                    titulo = "setembro 2026", onAnterior = {}, onProximo = {},
                    busca = BuscaTopBar(texto = texto, onTexto = { texto = it }, onFechar = { fechou = true }),
                )
            }
        }
        rule.onNodeWithText("setembro 2026").assertDoesNotExist()
        rule.onAllNodesWithContentDescription("mês anterior").assertCountEquals(0)
        rule.onNodeWithTag(TAG_CAMPO_BUSCA).assertIsFocused()
        rule.onNodeWithTag(TAG_CAMPO_BUSCA).performTextInput("uber")
        assertEquals("uber", texto)
        rule.onNodeWithContentDescription("fechar busca").performClick()
        assertEquals(true, fechou)
    }
```

Imports: `androidx.compose.ui.test.assertIsNotEnabled`, `assertIsFocused`, `assertCountEquals`, `onAllNodesWithContentDescription`, `performTextInput`, `onNodeWithTag`, `com.scholze.saldo.ui.components.BuscaTopBar`, `TAG_CAMPO_BUSCA`.

- [ ] **Step 2: Run to verify they fail**

Run: `mise exec -- ./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.scholze.saldo.ui.components.M3Test`
Expected: compilação falha — `podeAvancar`, `BuscaTopBar` não existem.

- [ ] **Step 3: Icons**

Em `Icons.kt`, acrescente `LISTA, LUPA, FECHAR` ao enum e os três desenhos no `when` (mesmo estilo dos outros: `w`, `h`, `sw`, `stroke`):

```kotlin
            // Três linhas iguais: uma lista.
            SaldoIcon.LISTA -> {
                listOf(0.30f, 0.50f, 0.70f).forEach { fy ->
                    drawLine(tint, Offset(w * 0.18f, h * fy), Offset(w * 0.82f, h * fy), sw, StrokeCap.Round)
                }
            }
            // Um círculo e o cabo: a lupa.
            SaldoIcon.LUPA -> {
                val r = w * 0.26f
                val c = Offset(w * 0.44f, h * 0.44f)
                drawCircle(tint, radius = r, center = c, style = stroke)
                val d = r * 0.7071f
                drawLine(tint, Offset(c.x + d, c.y + d), Offset(w * 0.82f, h * 0.82f), sw, StrokeCap.Round)
            }
            // Duas diagonais: o ×.
            SaldoIcon.FECHAR -> {
                drawLine(tint, Offset(w * 0.25f, h * 0.25f), Offset(w * 0.75f, h * 0.75f), sw, StrokeCap.Round)
                drawLine(tint, Offset(w * 0.75f, h * 0.25f), Offset(w * 0.25f, h * 0.75f), sw, StrokeCap.Round)
            }
```

- [ ] **Step 4: The top bar**

Substitua `SaldoTopBar` e `IconeRedondo` em `M3.kt`:

```kotlin
/** O campo de busca que toma a barra; os testes o acham por aqui. */
const val TAG_CAMPO_BUSCA = "topbar:busca"

/** O que a barra precisa para virar um campo de busca: o texto, quem o muda, e o `×`. */
data class BuscaTopBar(val texto: String, val onTexto: (String) -> Unit, val onFechar: () -> Unit)

/**
 * A barra superior grande do M3: título alinhado à ESQUERDA e em corpo grande, que é
 * a diferença mais visível de todas contra a barra centrada do HIG.
 *
 * [acoes] entra entre as duas setas — é onde o olho da privacidade e o toggle de vista
 * moram. Como o bloco é invocado dentro da própria `Row`, dois ícones nele já saem lado
 * a lado, sem nenhuma ginástica de layout.
 *
 * [podeAvancar] `false` desenha a seta direita esmaecida e sem clique: o board para em hoje,
 * e um controle que não faz nada precisa ao menos parecer que não faz.
 *
 * [busca] não nulo troca a barra inteira por um campo de texto com foco: é a busca do ledger.
 */
@Composable
fun SaldoTopBar(
    titulo: String,
    onAnterior: () -> Unit,
    onProximo: () -> Unit,
    modifier: Modifier = Modifier,
    mostrarSetas: Boolean = true,
    podeAvancar: Boolean = true,
    busca: BuscaTopBar? = null,
    acoes: @Composable (() -> Unit)? = null,
) {
    val colors = SaldoTheme.colors
    if (busca != null) {
        val foco = remember { FocusRequester() }
        LaunchedEffect(Unit) { foco.requestFocus() }
        Row(
            modifier.fillMaxWidth().background(colors.background).padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = busca.texto,
                onValueChange = busca.onTexto,
                modifier = Modifier.weight(1f).focusRequester(foco).testTag(TAG_CAMPO_BUSCA),
                placeholder = { Text("descrição, tag ou valor") },
                singleLine = true,
            )
            IconeRedondo(SaldoIcon.FECHAR, "fechar busca", busca.onFechar)
        }
        return
    }
    Column(modifier.fillMaxWidth().background(colors.background)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (mostrarSetas) IconeRedondo(SaldoIcon.CHEVRON_LEFT, "mês anterior", onAnterior)
            Box(Modifier.weight(1f))
            acoes?.invoke()
            if (mostrarSetas) IconeRedondo(SaldoIcon.CHEVRON_RIGHT, "próximo mês", onProximo, habilitado = podeAvancar)
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
fun IconeRedondo(
    icon: SaldoIcon,
    descricao: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    habilitado: Boolean = true,
) {
    val colors = SaldoTheme.colors
    Box(
        modifier
            .size(44.dp)
            .clip(CircleShape)
            .clickable(enabled = habilitado, onClick = onClick)
            .semantics { contentDescription = descricao },
        contentAlignment = Alignment.Center,
    ) {
        SaldoGlyph(
            icon,
            if (habilitado) colors.secondaryLabel else colors.separator,
            size = 22.dp,
            strokeWidth = 2.dp,
        )
    }
}
```

Imports novos em `M3.kt`: `androidx.compose.material3.OutlinedTextField`, `androidx.compose.runtime.LaunchedEffect`, `androidx.compose.runtime.remember`, `androidx.compose.ui.focus.FocusRequester`, `androidx.compose.ui.focus.focusRequester`, `androidx.compose.ui.platform.testTag`.

- [ ] **Step 5: Run the M3 tests**

Run: `mise exec -- ./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.scholze.saldo.ui.components.M3Test`
Expected: PASS. `assertIsNotEnabled` exige a semântica `Disabled`, que `clickable(enabled = false)` põe.

- [ ] **Step 6: Lint + JVM**

Run: `mise run test` e `mise exec -- ./gradlew lintDebug`.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/kotlin/com/scholze/saldo/ui/components/Icons.kt app/src/main/kotlin/com/scholze/saldo/ui/components/M3.kt app/src/androidTest/kotlin/com/scholze/saldo/ui/components/M3Test.kt
git commit -m "feat: a barra aprende a seta desabilitada e o modo busca"
```

---

### Task 7: Board — `SavedStateHandle`, seta, "ver como lista", vazio

**Files:**
- Create: `app/src/test/kotlin/com/scholze/saldo/RepositorioFixo.kt`
- Modify: `app/src/main/kotlin/com/scholze/saldo/ui/board/BoardViewModel.kt`
- Modify: `app/src/main/kotlin/com/scholze/saldo/ui/board/BoardScreen.kt`
- Modify: `app/src/main/kotlin/com/scholze/saldo/ui/SaldoApp.kt` (só `onVerLista = {}` por ora)
- Test: `app/src/test/kotlin/com/scholze/saldo/ui/board/BoardViewModelTest.kt` (create)
- Test: `app/src/androidTest/kotlin/com/scholze/saldo/ui/board/BoardScreenTest.kt` (modify + append)

**Interfaces:**
- Produces: `BoardViewModel(repo, savedState: SavedStateHandle = SavedStateHandle())` com `mesAtualAgora`, `diaAbertoAgora`; `BoardScreen(..., onVerLista: () -> Unit, ...)`; `fecharDia` removido; `TAG_BOARD_VAZIO`; `RepositorioFixo` (teste).
- Consumes: `SaldoTopBar.podeAvancar` (Task 6), `SaldoIcon.LISTA`.

- [ ] **Step 1: The fake repository for JVM tests**

`app/src/test/kotlin/com/scholze/saldo/RepositorioFixo.kt`:

```kotlin
package com.scholze.saldo

import com.scholze.saldo.data.SaldoRepository
import com.scholze.saldo.domain.EscopoEdicao
import com.scholze.saldo.domain.EscopoExclusao
import com.scholze.saldo.domain.LedgerInput
import com.scholze.saldo.domain.Movimentacao
import com.scholze.saldo.domain.RepetirOpcao
import com.scholze.saldo.domain.Tag
import com.scholze.saldo.domain.TagSnapshot
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** Um repositório que só devolve um ledger fixo; as escritas não fazem nada. */
class RepositorioFixo(input: LedgerInput, tags: List<Tag> = emptyList()) : SaldoRepository {
    override val ledger: Flow<LedgerInput> = flowOf(input)
    override val tags: Flow<List<Tag>> = flowOf(tags)
    override suspend fun abrirMes(mes: YearMonth) = Unit
    override suspend fun criar(mov: Movimentacao, repetir: RepetirOpcao) = Unit
    override suspend fun editar(mov: Movimentacao, escopo: EscopoEdicao) = Unit
    override suspend fun excluir(mov: Movimentacao): Movimentacao = mov
    override suspend fun restaurar(mov: Movimentacao) = Unit
    override suspend fun excluirRecorrencia(recorrenciaId: Long, aPartirDe: YearMonth, escopo: EscopoExclusao) = Unit
    override suspend fun converterEmRecorrencia(mov: Movimentacao, diaDoMes: Int) = Unit
    override suspend fun encerrarRecorrencia(mov: Movimentacao) = Unit
    override suspend fun pausar(recorrenciaId: Long, hoje: LocalDate) = Unit
    override suspend fun retomar(recorrenciaId: Long, hoje: LocalDate) = Unit
    override suspend fun criarTag(nome: String, cor: Long): Long = 1
    override suspend fun renomearTag(id: Long, nome: String) = Unit
    override suspend fun excluirTag(id: Long): TagSnapshot = TagSnapshot(Tag(id, "", 0), emptyList(), emptyList())
    override suspend fun restaurarTag(snapshot: TagSnapshot) = Unit
    override suspend fun recolorirTag(id: Long, cor: Long) = Unit
}
```

- [ ] **Step 2: JVM test for the saved state**

`app/src/test/kotlin/com/scholze/saldo/ui/board/BoardViewModelTest.kt`:

```kotlin
package com.scholze.saldo.ui.board

import androidx.lifecycle.SavedStateHandle
import com.scholze.saldo.RepositorioFixo
import com.scholze.saldo.domain.CartaoConfig
import com.scholze.saldo.domain.LedgerInput
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BoardViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val input = LedgerInput(
        saldoInicialCentavos = 0, saldoInicialData = LocalDate.parse("2026-01-01"),
        movimentacoes = emptyList(), recorrencias = emptyList(), mesesMaterializados = emptySet(),
        cartao = CartaoConfig(), hoje = LocalDate.parse("2026-09-07"),
    )

    @Before fun setMain() = Dispatchers.setMain(dispatcher)
    @After fun resetMainDispatcher() = Dispatchers.resetMain()

    @Test
    fun oMesEODiaAbertoSobrevivemNoSavedState() = runTest(dispatcher) {
        val saved = SavedStateHandle()
        val vm = BoardViewModel(RepositorioFixo(input), saved)
        vm.irPara(YearMonth.of(2026, 5), dia = 12)
        advanceUntilIdle()                                      // os coletores gravam no handle

        val outro = BoardViewModel(RepositorioFixo(input), saved)   // "processo novo", mesmo handle
        assertEquals(YearMonth.of(2026, 5), outro.mesAtualAgora)
        assertEquals(LocalDate.parse("2026-05-12"), outro.diaAbertoAgora)
    }

    @Test
    fun fecharODiaTambemSobrevive() = runTest(dispatcher) {
        val saved = SavedStateHandle()
        val vm = BoardViewModel(RepositorioFixo(input), saved)
        vm.alternarDia(LocalDate.now())                         // hoje estava aberto: fecha
        advanceUntilIdle()
        val outro = BoardViewModel(RepositorioFixo(input), saved)
        assertEquals(null, outro.diaAbertoAgora)
    }

    @Test
    fun semSavedStateAbreNoMesCorrenteComHojeAberto() {
        val vm = BoardViewModel(RepositorioFixo(input), SavedStateHandle())
        assertEquals(YearMonth.now(), vm.mesAtualAgora)
        assertEquals(LocalDate.now(), vm.diaAbertoAgora)
    }
}
```

- [ ] **Step 3: Run to verify it fails**

Run: `mise exec -- ./gradlew :app:testDebugUnitTest --tests "com.scholze.saldo.ui.board.BoardViewModelTest"`
Expected: compilação falha — o construtor não aceita `SavedStateHandle`; `mesAtualAgora` não existe.

- [ ] **Step 4: The view model**

Substitua em `BoardViewModel.kt` a declaração da classe até o `init`:

```kotlin
class BoardViewModel(
    private val repo: SaldoRepository,
    private val savedState: SavedStateHandle = SavedStateHandle(),
) : ViewModel() {

    // Mês e dia aberto no SavedStateHandle: sobrevivem à morte do processo, e não só à
    // rotação. `YearMonth`/`LocalDate` como Long (o handle só aceita o que vai num Bundle).
    private val mesAtual = MutableStateFlow(savedState.get<Long>(KEY_MES)?.toYearMonth() ?: YearMonth.now())

    // O app abre respondendo "o que eu gastei hoje": no mês corrente, hoje já vem aberto.
    // `contains` distingue "nunca gravado" (primeira abertura) de "gravado como nenhum".
    private val diaAberto = MutableStateFlow<LocalDate?>(
        if (savedState.contains(KEY_DIA)) savedState.get<Long>(KEY_DIA)?.let { LocalDate.ofEpochDay(it) }
        else LocalDate.now(),
    )

    /** Leitura síncrona: o `+` da barra lança no dia aberto, e os testes conferem o saved state. */
    val mesAtualAgora: YearMonth get() = mesAtual.value
    val diaAbertoAgora: LocalDate? get() = diaAberto.value
```

No `init`, depois de `abrir(mesAtual.value)`:

```kotlin
        viewModelScope.launch { mesAtual.collect { savedState[KEY_MES] = it.toLongChave() } }
        viewModelScope.launch { diaAberto.collect { savedState[KEY_DIA] = it?.toEpochDay() } }
```

Apague `fun fecharDia()`. No `companion object`, acrescente:

```kotlin
        private const val KEY_MES = "board.mes"
        private const val KEY_DIA = "board.dia"
```

e a factory passa a `initializer { BoardViewModel(container.repository, createSavedStateHandle()) }`.

Os dois conversores vivem num arquivo novo `app/src/main/kotlin/com/scholze/saldo/ui/SavedStateChaves.kt` (pacote `com.scholze.saldo.ui`), reaproveitado pelo ledger e por totais:

```kotlin
package com.scholze.saldo.ui

import java.time.YearMonth

/** `YearMonth` ↔ `Long` para o `SavedStateHandle`: ano × 12 + (mês − 1), como o banco já faz. */
fun YearMonth.toLongChave(): Long = year * 12L + (monthValue - 1)
fun Long.toYearMonth(): YearMonth = YearMonth.of((this / 12).toInt(), (this % 12).toInt() + 1)
```

Imports em `BoardViewModel.kt`: `androidx.lifecycle.SavedStateHandle`, `androidx.lifecycle.createSavedStateHandle`, `com.scholze.saldo.ui.toLongChave`, `com.scholze.saldo.ui.toYearMonth`.

- [ ] **Step 5: Run the JVM test**

Run: `mise exec -- ./gradlew :app:testDebugUnitTest --tests "com.scholze.saldo.ui.board.BoardViewModelTest"`
Expected: PASS.

- [ ] **Step 6: Screen tests** (em `BoardScreenTest`)

Troque `asSetasPedemOMesVizinho` — no mês corrente a seta direita não é clicável — por estes, e acrescente os de lista e vazio:

```kotlin
    @Test
    fun aSetaEsquerdaPedeOMesAnterior() {
        mesesAndados = 0
        montar()
        rule.onNodeWithContentDescription("mês anterior").performClick()
        assertEquals(-1, mesesAndados)
    }

    /** No mês corrente a seta direita está lá, esmaecida, e não faz nada. */
    @Test
    fun noMesCorrenteASetaDireitaEstaDesabilitada() {
        mesesAndados = 0
        montar()
        rule.onNodeWithContentDescription("próximo mês").assertIsNotEnabled()
        rule.onNodeWithContentDescription("próximo mês").performClick()
        assertEquals(0, mesesAndados)
    }

    @Test
    fun numMesPassadoASetaDireitaAvanca() {
        mesesAndados = 0
        montar(mesVisto = YearMonth.of(2026, 8))
        rule.onNodeWithContentDescription("próximo mês").assertIsEnabled()
        rule.onNodeWithContentDescription("próximo mês").performClick()
        assertEquals(1, mesesAndados)
    }

    @Test
    fun verComoListaAvisaQuemMontou() {
        pediuLista = false
        montar()
        rule.onNodeWithContentDescription("ver como lista").performClick()
        assertEquals(true, pediuLista)
    }

    @Test
    fun semMovimentacaoNoMesAGradeConvidaALancarOPrimeiro() {
        montar(entrada = input.copy(movimentacoes = emptyList()))
        rule.onNodeWithTag(TAG_BOARD_GRADE).performScrollToNode(hasTestTag(TAG_BOARD_VAZIO))
        rule.onNodeWithText("toque em + para lançar o primeiro").assertIsDisplayed()
        rule.onAllNodesWithTag(TAG_BOARD_LEGENDA).assertCountEquals(0)
    }

    @Test
    fun comMovimentacaoALegendaVoltaNoLugarDoConvite() {
        montar()
        rule.onNodeWithTag(TAG_BOARD_GRADE).performScrollToNode(hasTestTag(TAG_BOARD_LEGENDA))
        rule.onAllNodesWithTag(TAG_BOARD_VAZIO).assertCountEquals(0)
    }
```

`montar` ganha `mesVisto: YearMonth = YearMonth.from(input.hoje)`, e `estado` recebe-o e usa-o em `mesAtual`, em `BoardEngine.board(entrada, mesVisto)` e em `ProjectionEngine.mes(entrada, mesVisto, …)`. Acrescente `private var pediuLista = false` e `onVerLista = { pediuLista = true }` na chamada de `BoardScreen`. Imports: `assertIsNotEnabled`, `assertIsEnabled`, `assertCountEquals`, `onAllNodesWithTag`, `TAG_BOARD_VAZIO`.

- [ ] **Step 7: The screen**

Em `BoardScreen.kt`:

- assinatura: acrescente `onVerLista: () -> Unit,` depois de `onProximoMes`;
- `SaldoTopBar`: `podeAvancar = state.podeAvancar,` e, em `acoes`, ANTES do olho:
  ```kotlin
                    IconeRedondo(SaldoIcon.LISTA, "ver como lista", onVerLista)
  ```
- constante nova ao lado de `TAG_BOARD_LEGENDA`:
  ```kotlin
  /** O convite do mês sem nada — no lugar da régua, que não teria o que explicar. */
  const val TAG_BOARD_VAZIO = "board:vazio"
  ```
- no fim do `Column` rolável, troque `Legenda(board.unidadeCentavos)` por:
  ```kotlin
                // Mês sem uma movimentação sequer: a régua explicaria a cor de células que
                // não têm cor. No lugar dela, o convite — some com o primeiro lançamento.
                if (mes.dias.all { it.itens.isEmpty() }) BoardVazio() else Legenda(board.unidadeCentavos)
  ```
- o composable, ao lado de `Legenda`:
  ```kotlin
  @Composable
  private fun BoardVazio() {
      val colors = SaldoTheme.colors
      Column(
          Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 24.dp).testTag(TAG_BOARD_VAZIO),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(6.dp),
      ) {
          Text("nada lançado neste mês", style = SaldoTheme.type.row, color = colors.secondaryLabel)
          Text("toque em + para lançar o primeiro", style = SaldoTheme.type.footnote, color = colors.secondaryLabel)
      }
  }
  ```

Em `SaldoApp.kt`, na chamada de `BoardScreen`, passe `onVerLista = {},` — a Task 9 liga de verdade.

- [ ] **Step 8: Run the board tests**

Run: `mise exec -- ./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.scholze.saldo.ui.board.BoardScreenTest`
Expected: PASS. `VistaSaldosTest.aSetaDeAvancarNaoPassaDoMesCorrente` continua passando: clicar num nó desabilitado não faz nada e o título fica.

- [ ] **Step 9: Lint + JVM, commit**

```bash
git add app/src/main/kotlin/com/scholze/saldo/ui/board app/src/main/kotlin/com/scholze/saldo/ui/SavedStateChaves.kt app/src/androidTest/kotlin/com/scholze/saldo/ui/board app/src/test/kotlin/com/scholze/saldo app/src/main/kotlin/com/scholze/saldo/ui/SaldoApp.kt
git commit -m "feat: o board guarda o mês no saved state, desabilita a seta e convida no mês vazio"
```

---

### Task 8: Ledger — `SavedStateHandle`, busca e resultados

**Files:**
- Modify: `app/src/main/kotlin/com/scholze/saldo/ui/ledger/LedgerViewModel.kt`
- Modify: `app/src/main/kotlin/com/scholze/saldo/ui/ledger/LedgerScreen.kt`
- Modify: `app/src/main/kotlin/com/scholze/saldo/ui/SaldoApp.kt` (só os parâmetros novos)
- Test: `app/src/test/kotlin/com/scholze/saldo/ui/ledger/LedgerViewModelTest.kt` (create)
- Test: `app/src/androidTest/kotlin/com/scholze/saldo/LedgerScreenTest.kt` (append, `@Ignore` até a Task 9)

**Interfaces:**
- Produces:
  - `LedgerViewModel(repo, savedState = SavedStateHandle())`; `val busca: StateFlow<String?>` (`null` = fechada); `abrirBusca()`, `fecharBusca()`, `definirBusca(texto)`, `definirTagFiltroId(id)`, `abrirResultado(mov, onPronta)`; `mesAtualAgora`, `filtroAgora`, `tagFiltroIdAgora`; `LedgerUiState.resultados: List<Movimentacao>?`.
  - `LedgerScreen(..., busca: String?, onAbrirBusca, onFecharBusca, onBusca: (String) -> Unit, onAbrirResultado: (Movimentacao) -> Unit, ...)`; `TAG_RESULTADOS`; `DayRow(..., mostrarSaldo: Boolean = true)`.
- Consumes: `Busca.filtrar` (Task 2), `ProjectionEngine.movimentacoesAte`, `BuscaTopBar` (Task 6), `SaldoIcon.LUPA`, `toLongChave`/`toYearMonth` (Task 7).

- [ ] **Step 1: JVM test**

`app/src/test/kotlin/com/scholze/saldo/ui/ledger/LedgerViewModelTest.kt`:

```kotlin
package com.scholze.saldo.ui.ledger

import androidx.lifecycle.SavedStateHandle
import com.scholze.saldo.RepositorioFixo
import com.scholze.saldo.domain.CartaoConfig
import com.scholze.saldo.domain.FiltroLedger
import com.scholze.saldo.domain.LedgerInput
import com.scholze.saldo.domain.Movimentacao
import com.scholze.saldo.domain.Natureza
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LedgerViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val hoje = LocalDate.parse("2026-09-07")
    private val input = LedgerInput(
        saldoInicialCentavos = 0, saldoInicialData = LocalDate.parse("2026-01-01"),
        movimentacoes = listOf(
            Movimentacao(id = 1, descricao = "uber", valorCentavos = -23_90, data = LocalDate.parse("2026-09-01"), natureza = Natureza.DIARIO),
            Movimentacao(id = 2, descricao = "uber", valorCentavos = -31_00, data = LocalDate.parse("2026-04-03"), natureza = Natureza.DIARIO),
        ),
        recorrencias = emptyList(), mesesMaterializados = emptySet(), cartao = CartaoConfig(), hoje = hoje,
    )

    @Before fun setMain() = Dispatchers.setMain(dispatcher)
    @After fun resetMainDispatcher() = Dispatchers.resetMain()

    @Test
    fun aBuscaAchaEmTodosOsMeses() = runTest(dispatcher) {
        val vm = LedgerViewModel(RepositorioFixo(input), SavedStateHandle())
        vm.abrirBusca()
        vm.definirBusca("uber")
        val estado = vm.state.first { it.resultados != null }
        assertEquals(listOf(1L, 2L), estado.resultados!!.map { it.id })
    }

    @Test
    fun buscaFechadaNaoTemResultados() = runTest(dispatcher) {
        val vm = LedgerViewModel(RepositorioFixo(input), SavedStateHandle())
        val estado = vm.state.first { it.mes != null }
        assertNull(estado.resultados)
    }

    @Test
    fun mesFiltroTagEBuscaSobrevivemNoSavedState() = runTest(dispatcher) {
        val saved = SavedStateHandle()
        val vm = LedgerViewModel(RepositorioFixo(input), saved)
        vm.irPara(YearMonth.of(2026, 4))
        vm.definirFiltro(FiltroLedger.FIXAS)
        vm.definirTagFiltroId(9L)
        vm.abrirBusca()
        vm.definirBusca("ub")
        advanceUntilIdle()

        val outro = LedgerViewModel(RepositorioFixo(input), saved)
        assertEquals(YearMonth.of(2026, 4), outro.mesAtualAgora)
        assertEquals(FiltroLedger.FIXAS, outro.filtroAgora)
        assertEquals(9L, outro.tagFiltroIdAgora)
        assertEquals("ub", outro.busca.value)
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `mise exec -- ./gradlew :app:testDebugUnitTest --tests "com.scholze.saldo.ui.ledger.LedgerViewModelTest"`
Expected: compilação falha.

- [ ] **Step 3: The view model**

Substitua `LedgerUiState` e o corpo de `LedgerViewModel` até `limparAlvo` (mantendo `excluir`, `desfazerExclusao`, `abrir` e o `companion` como estão, com as chaves novas):

```kotlin
data class LedgerUiState(
    /** `null` enquanto o primeiro `LedgerInput` não chegou do banco. */
    val mes: MesLedger?,
    val mesAtual: YearMonth,
    val filtro: FiltroLedger,
    val hoje: LocalDate,
    /** Etiqueta escolhida na aba tags; `null` = o mês inteiro. */
    val tagFiltro: Tag? = null,
    /** Os resultados da busca, mais recentes primeiro; `null` = busca fechada ou em branco. */
    val resultados: List<Movimentacao>? = null,
    /** Os templates, para a shell achar o dia da recorrência de uma linha ao abrir a sheet. */
    val recorrencias: List<Recorrencia> = emptyList(),
)

/** Dia para o qual o ledger deve rolar assim que [mes] estiver na tela — pedido por um deep link. */
data class AlvoLedger(val mes: YearMonth, val dia: Int)

class LedgerViewModel(
    private val repo: SaldoRepository,
    private val savedState: SavedStateHandle = SavedStateHandle(),
) : ViewModel() {

    // Mês, filtro, tag e busca no SavedStateHandle: sobrevivem à morte do processo. Sem isto,
    // voltar ao app depois de um tempo mostrava a subtela da tag sem tag — e sem o × para sair.
    private val mesAtual = MutableStateFlow(savedState.get<Long>(KEY_MES)?.toYearMonth() ?: YearMonth.now())
    private val filtro = MutableStateFlow(
        savedState.get<String>(KEY_FILTRO)?.let { FiltroLedger.valueOf(it) } ?: FiltroLedger.TODAS,
    )
    // Guarda o id, não a Tag: renomear ou apagar a etiqueta na aba tags tem de chegar
    // aqui, e um snapshot da Tag deixaria o chip preso ao nome antigo (ou o ledger preso
    // a uma etiqueta que já não existe, filtrando tudo para fora sem saída visível).
    private val tagFiltroId = MutableStateFlow<Long?>(savedState.get<Long>(KEY_TAG))

    /** O texto da busca; `null` = busca fechada, "" = aberta sem nada digitado. */
    private val _busca = MutableStateFlow<String?>(savedState.get<String>(KEY_BUSCA))
    val busca: StateFlow<String?> = _busca

    private val _eventoExclusao = MutableSharedFlow<Movimentacao>(extraBufferCapacity = 1)

    /** Snapshot da linha apagada, para o "desfazer" do snackbar. */
    val eventoExclusao: SharedFlow<Movimentacao> = _eventoExclusao

    private val _alvo = MutableStateFlow<AlvoLedger?>(null)

    /** Consumido pela tela (`limparAlvo`) depois de rolar; separado de [state] para não engordar o combine. */
    val alvo: StateFlow<AlvoLedger?> = _alvo

    /** Leituras síncronas para a shell e para os testes do saved state. */
    val mesAtualAgora: YearMonth get() = mesAtual.value
    val filtroAgora: FiltroLedger get() = filtro.value
    val tagFiltroIdAgora: Long? get() = tagFiltroId.value

    /** Os cinco controles do usuário, combinados uma vez: o `combine` de seis fluxos perde os tipos. */
    private data class Controles(val mes: YearMonth, val filtro: FiltroLedger, val tagId: Long?, val busca: String?)

    private val controles = combine(mesAtual, filtro, tagFiltroId, _busca) { m, f, t, b -> Controles(m, f, t, b) }

    val state: StateFlow<LedgerUiState> =
        combine(repo.ledger, repo.tags, controles) { input, tags, c ->
            val tag = c.tagId?.let { id -> tags.firstOrNull { it.id == id } }
            LedgerUiState(
                mes = ProjectionEngine.mes(input, c.mes, c.filtro, tagId = tag?.id),
                mesAtual = c.mes,
                filtro = c.filtro,
                hoje = input.hoje,
                tagFiltro = tag,
                resultados = c.busca?.takeIf { it.isNotBlank() }?.let { q ->
                    Busca.filtrar(ProjectionEngine.movimentacoesAte(input, YearMonth.from(input.hoje)), q, input.hoje)
                },
                recorrencias = input.recorrencias,
            )
        }
            // A projeção do mês inteiro roda fora da main thread.
            .flowOn(Dispatchers.Default)
            // Uma exceção subindo do banco cancelaria o StateFlow e a tela ficaria
            // congelada para sempre, sem nem um crash que explicasse. Registrar e parar
            // de emitir preserva o último estado renderizado.
            .catch { Log.e(TAG, "fluxo do ledger falhou", it) }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                LedgerUiState(mes = null, mesAtual = mesAtual.value, filtro = filtro.value, hoje = LocalDate.now()),
            )

    init {
        abrir(mesAtual.value)
        viewModelScope.launch { mesAtual.collect { savedState[KEY_MES] = it.toLongChave() } }
        viewModelScope.launch { filtro.collect { savedState[KEY_FILTRO] = it.name } }
        viewModelScope.launch { tagFiltroId.collect { savedState[KEY_TAG] = it } }
        viewModelScope.launch { _busca.collect { savedState[KEY_BUSCA] = it } }
    }

    fun mesAnterior() = irPara(mesAtual.value.minusMonths(1))
    fun proximoMes() = irPara(mesAtual.value.plusMonths(1))

    /** Navega para [mes]; com [dia], o ledger rola até ele quando o mês chegar (deep link). */
    fun irPara(mes: YearMonth, dia: Int? = null) {
        mesAtual.value = mes
        _alvo.value = dia?.let { AlvoLedger(mes, it) }
        abrir(mes)
    }

    fun limparAlvo() { _alvo.value = null }

    fun definirFiltro(f: FiltroLedger) { filtro.value = f }

    fun definirTagFiltro(tag: Tag?) = definirTagFiltroId(tag?.id)
    fun definirTagFiltroId(id: Long?) { tagFiltroId.value = id }

    fun abrirBusca() { if (_busca.value == null) _busca.value = "" }
    fun fecharBusca() { _busca.value = null }
    fun definirBusca(texto: String) { _busca.value = texto }

    /**
     * Abre um resultado da busca. Uma ocorrência virtual (`id == 0`) precisa do mês
     * materializado antes: [SaldoRepository.abrirMes] e a releitura do ledger trazem a linha
     * com id, que é o que a sheet precisa para gravar — o mesmo caminho de
     * `RecorrenciasViewModel.abrirOcorrencia`.
     */
    fun abrirResultado(mov: Movimentacao, onPronta: (Movimentacao) -> Unit) {
        if (mov.id != 0L) { onPronta(mov); return }
        val m = YearMonth.from(mov.data)
        viewModelScope.launch {
            try {
                repo.abrirMes(m)
                val linha = repo.ledger.first().movimentacoes.firstOrNull {
                    it.recorrenciaId == mov.recorrenciaId && it.data == mov.data
                }
                if (linha != null && linha.id != 0L) onPronta(linha)
                else Log.w(TAG, "resultado virtual sem linha depois de abrir $m")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "abrirResultado falhou", e)
            }
        }
    }
```

No `companion object`: `KEY_MES = "ledger.mes"`, `KEY_FILTRO = "ledger.filtro"`, `KEY_TAG = "ledger.tag"`, `KEY_BUSCA = "ledger.busca"`, e `initializer { LedgerViewModel(container.repository, createSavedStateHandle()) }`. Imports: `androidx.lifecycle.SavedStateHandle`, `androidx.lifecycle.createSavedStateHandle`, `com.scholze.saldo.domain.Busca`, `com.scholze.saldo.domain.Recorrencia`, `com.scholze.saldo.ui.toLongChave`, `com.scholze.saldo.ui.toYearMonth`, `kotlinx.coroutines.flow.first`.

- [ ] **Step 4: Run the JVM test**

Run: `mise exec -- ./gradlew :app:testDebugUnitTest --tests "com.scholze.saldo.ui.ledger.LedgerViewModelTest"`
Expected: PASS.

- [ ] **Step 5: The screen**

Em `LedgerScreen.kt`:

- constante ao lado de `TAG_SALDO_PROJETADO`:
  ```kotlin
  /** A lista de resultados da busca (ou o "nada com …"). */
  const val TAG_RESULTADOS = "ledger:resultados"
  ```
- assinatura: acrescente, depois de `onLimparTag`,
  ```kotlin
      busca: String? = null,
      onAbrirBusca: () -> Unit = {},
      onFecharBusca: () -> Unit = {},
      onBusca: (String) -> Unit = {},
      onAbrirResultado: (Movimentacao) -> Unit = onItemClick,
  ```
- `SaldoTopBar`: passe `busca = busca?.let { BuscaTopBar(it, onBusca, onFecharBusca) },` e, em `acoes`, ANTES do `GRADE`:
  ```kotlin
                    IconeRedondo(SaldoIcon.LUPA, "buscar", onAbrirBusca)
  ```
- logo depois da `SaldoTopBar`, antes de `if (mes == null)`:

```kotlin
            // Busca com texto: os resultados tomam o lugar do mês. A pill de "hoje" e o
            // diálogo da fatura ficam no Box de fora e não atrapalham — a pill depende da
            // lista do mês, que não está composta.
            if (busca != null && busca.isNotBlank()) {
                ResultadosBusca(
                    consulta = busca,
                    resultados = state.resultados.orEmpty(),
                    hoje = state.hoje,
                    onItemClick = onAbrirResultado,
                    onExcluir = onExcluir,
                    contentPadding = contentPadding,
                )
                return@Column
            }
```

- o composable, no fim do arquivo:

```kotlin
/**
 * Os resultados da busca: um cabeçalho por mês, mais recentes primeiro, e as mesmas linhas
 * do ledger dentro. Sem saldo do dia — um resultado é uma linha solta, não um dia.
 */
@Composable
private fun ResultadosBusca(
    consulta: String,
    resultados: List<Movimentacao>,
    hoje: LocalDate,
    onItemClick: (Movimentacao) -> Unit,
    onExcluir: (Movimentacao) -> Unit,
    contentPadding: PaddingValues,
) {
    val colors = SaldoTheme.colors
    if (resultados.isEmpty()) {
        Column(
            Modifier.fillMaxWidth().padding(32.dp).testTag(TAG_RESULTADOS),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("nada com \"$consulta\"", style = SaldoTheme.type.row, color = colors.secondaryLabel)
        }
        return
    }
    val porMes = remember(resultados) { resultados.groupBy { YearMonth.from(it.data) } }
    LazyColumn(Modifier.fillMaxSize().testTag(TAG_RESULTADOS), contentPadding = contentPadding) {
        porMes.forEach { (mes, itens) ->
            item(key = "mes-$mes") {
                Text(
                    mes.format(tituloMes),
                    Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 6.dp),
                    style = SaldoTheme.type.sectionHeader, color = colors.secondaryLabel,
                )
            }
            items(itens, key = { "${it.id}-${it.data.toEpochDay()}-${it.recorrenciaId}" }) { mov ->
                DayRow(
                    dia = DiaRow(data = mov.data, itens = listOf(ItemDia.Mov(mov)), saldoCentavos = 0L),
                    faixa = 0L..0L,
                    hoje = hoje,
                    onItemClick = onItemClick,
                    onExcluir = onExcluir,
                    onFaturaClick = {},
                    mostrarSaldo = false,
                )
            }
        }
    }
}
```

`DayRow` ganha `mostrarSaldo: Boolean = true` como último parâmetro; com `false`, o bloco que desenha a pill do saldo do dia (a `SaldoPill` com `tagSaldoDoDia`) fica dentro de `if (mostrarSaldo) { … }`. Imports: `androidx.compose.foundation.lazy.items`, `androidx.compose.runtime.remember`, `com.scholze.saldo.domain.DiaRow`, `com.scholze.saldo.domain.ItemDia`, `java.time.YearMonth`, `com.scholze.saldo.ui.components.BuscaTopBar`.

- [ ] **Step 6: Wire SaldoApp** (mínimo, para compilar): na chamada de `LedgerScreen`, acrescente
```kotlin
                            busca = buscaLedger,
                            onAbrirBusca = ledgerVm::abrirBusca,
                            onFecharBusca = ledgerVm::fecharBusca,
                            onBusca = ledgerVm::definirBusca,
                            onAbrirResultado = { ledgerVm.abrirResultado(it, abrirMovimentacao) },
```
com `val buscaLedger by ledgerVm.busca.collectAsState()` ao lado de `alvoLedger`.

- [ ] **Step 7: Screen tests** (append em `LedgerScreenTest`, marcados `@Ignore("liga na Task 9")` até o toggle existir)

```kotlin
    /** Semeia direto no repositório da activity — é o mesmo objeto que a tela lê. */
    private fun semear(descricao: String, centavos: Long, data: LocalDate) = runBlocking {
        ApplicationProvider.getApplicationContext<SaldoApplication>().container.repository.criar(
            Movimentacao(descricao = descricao, valorCentavos = centavos, data = data, natureza = Natureza.DIARIO),
            RepetirOpcao.Nao,
        )
    }

    /** Onboarding (R$ 1.000,00), o board, e o toggle para a lista. */
    private fun abrirLista() {
        rule.onNodeWithText("qual seu saldo hoje?").assertIsDisplayed()
        "100000".forEach { rule.onNodeWithText(it.toString()).performClick() }
        rule.onNodeWithText("começar").performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithTag(TAG_BOARD_GRADE).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithContentDescription("ver como lista").performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithContentDescription("buscar").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun aLupaAbreOCampoEOsResultadosVemAgrupadosPorMes() {
        val cincoMesesAtras = LocalDate.now().minusMonths(5).withDayOfMonth(3)
        semear("uber", -23_90, LocalDate.now())
        semear("uber", -31_00, cincoMesesAtras)
        abrirLista()
        rule.onNodeWithContentDescription("buscar").performClick()
        rule.onNodeWithTag(TAG_CAMPO_BUSCA).performTextInput("uber")
        rule.waitUntil(5_000) { rule.onAllNodesWithText("uber").fetchSemanticsNodes().size == 2 }
        val titulo = YearMonth.from(cincoMesesAtras).format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.forLanguageTag("pt-BR")))
        rule.onNodeWithTag(TAG_RESULTADOS).performScrollToNode(hasText(titulo))
        rule.onNodeWithText(titulo).assertIsDisplayed()
    }

    @Test
    fun buscaSemResultadoDizQueNaoAchou() {
        abrirLista()
        rule.onNodeWithContentDescription("buscar").performClick()
        rule.onNodeWithTag(TAG_CAMPO_BUSCA).performTextInput("zzz")
        rule.onNodeWithText("nada com \"zzz\"").assertIsDisplayed()
    }

    @Test
    fun fecharABuscaVoltaAoMes() {
        abrirLista()
        rule.onNodeWithContentDescription("buscar").performClick()
        rule.onNodeWithContentDescription("fechar busca").performClick()
        rule.onNodeWithText(YearMonth.now().format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.forLanguageTag("pt-BR")))).assertIsDisplayed()
    }
```

O arquivo usa `createAndroidComposeRule<MainActivity>`, então a activity já está no ar quando o corpo roda: semear pelo repositório antes de `abrirLista()` funciona porque o ledger é um `Flow` do Room e a tela atualiza sozinha. Imports: `com.scholze.saldo.ui.board.TAG_BOARD_GRADE`, `com.scholze.saldo.ui.components.TAG_CAMPO_BUSCA`, `com.scholze.saldo.ui.ledger.TAG_RESULTADOS`, `androidx.compose.ui.test.performScrollToNode`, `hasText`, `performTextInput`, `onNodeWithTag`, `onAllNodesWithTag`, `onAllNodesWithContentDescription`.

- [ ] **Step 8: Run the ledger tests**

Run: `mise exec -- ./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.scholze.saldo.LedgerScreenTest`
Expected: os antigos PASS; os três novos `@Ignore`.

- [ ] **Step 9: Lint + JVM, commit**

```bash
git add app/src/main/kotlin/com/scholze/saldo/ui/ledger app/src/test/kotlin/com/scholze/saldo/ui/ledger app/src/androidTest/kotlin/com/scholze/saldo/LedgerScreenTest.kt app/src/main/kotlin/com/scholze/saldo/ui/SaldoApp.kt
git commit -m "feat: a busca do ledger — descrição, tag ou valor, o histórico inteiro por mês"
```

---

### Task 9: `SaldoApp` — Voltar, as três vistas, o `+` no dia aberto, o diálogo de exportar

**Files:**
- Modify: `app/src/main/kotlin/com/scholze/saldo/ui/SaldoApp.kt`
- Test: `app/src/androidTest/kotlin/com/scholze/saldo/SaldoAppBackTest.kt` (create)
- Test: `app/src/androidTest/kotlin/com/scholze/saldo/VistaSaldosTest.kt` (append)
- Test: `app/src/androidTest/kotlin/com/scholze/saldo/LedgerScreenTest.kt` (tirar os `@Ignore`)

**Interfaces:**
- Produces: `enum class VistaSaldos { BOARD, LISTA, TAG }` (privado de `SaldoApp.kt`); o `BackHandler`; `onVerLista`/`onVerBoard` ligados; FAB com `boardVm.diaAbertoAgora`.
- Consumes: `LedgerViewModel.busca/fecharBusca/irPara/definirTagFiltro`, `BoardViewModel.irPara/diaAbertoAgora/mesAtualAgora`.

- [ ] **Step 1: The back test**

`app/src/androidTest/kotlin/com/scholze/saldo/SaldoAppBackTest.kt`:

```kotlin
package com.scholze.saldo

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.scholze.saldo.ui.board.TAG_BOARD_GRADE
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * O botão Voltar do sistema: subtela → aba → saldos → sair. Antes, saía do app de qualquer
 * lugar que não tivesse `BackHandler` próprio — do ledger da tag, de totais, de tags.
 */
@RunWith(AndroidJUnit4::class)
class SaldoAppBackTest {

    @get:Rule(order = 0)
    val estadoLimpo = EstadoLimpo()

    @get:Rule(order = 1)
    val rule = createEmptyComposeRule()

    private fun app() {
        val app = ApplicationProvider.getApplicationContext<SaldoApplication>()
        runBlocking { app.container.settings.definirSaldoInicial(100_000_00, LocalDate.now()) }
    }

    private fun esperarBoard() = rule.waitUntil(5_000) {
        rule.onAllNodesWithTag(TAG_BOARD_GRADE).fetchSemanticsNodes().isNotEmpty()
    }

    private fun esperarTexto(t: String) = rule.waitUntil(5_000) {
        rule.onAllNodesWithText(t, substring = true).fetchSemanticsNodes().isNotEmpty()
    }

    /** Espresso.pressBack lança se a activity fechar; o `runCatching` distingue "saiu" de "ficou". */
    private fun voltar(): Boolean = runCatching { Espresso.pressBack() }.isSuccess

    @Test
    fun deUmaAbaOVoltarVaiParaSaldos() {
        app()
        ActivityScenario.launch(MainActivity::class.java).use {
            esperarBoard()
            rule.onNodeWithText("totais").performClick()
            esperarTexto("totais")
            assertEquals(true, voltar())
            esperarBoard()
            rule.onAllNodesWithTag(TAG_BOARD_GRADE).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun daListaOVoltarVaiParaOBoard() {
        app()
        ActivityScenario.launch(MainActivity::class.java).use {
            esperarBoard()
            rule.onNodeWithContentDescription("ver como lista").performClick()
            esperarTexto("todas")
            assertEquals(true, voltar())
            esperarBoard()
        }
    }

    @Test
    fun deRecorrenciasOVoltarVaiParaTotaisEDepoisParaSaldos() {
        app()
        ActivityScenario.launch(MainActivity::class.java).use {
            esperarBoard()
            rule.onNodeWithText("totais").performClick()
            rule.onNodeWithText("a caminho").performClick()
            rule.onNodeWithText("recorrências", substring = true).performClick()
            esperarTexto("‹ totais")
            assertEquals(true, voltar())
            esperarTexto("a caminho")
            assertEquals(true, voltar())
            esperarBoard()
        }
    }

    @Test
    fun noBoardOVoltarSaiDoApp() {
        app()
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        esperarBoard()
        voltar()
        rule.waitUntil(5_000) { scenario.state == androidx.lifecycle.Lifecycle.State.DESTROYED }
        assertEquals(androidx.lifecycle.Lifecycle.State.DESTROYED, scenario.state)
    }
}
```

(Se "recorrências" no segmento *a caminho* tiver outro rótulo, use o texto que `SegmentoACaminho` mostra para `onAbrirRecorrencias`.)

- [ ] **Step 2: Run to verify it fails**

Run: `mise exec -- ./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.scholze.saldo.SaldoAppBackTest`
Expected: `deUmaAbaOVoltarVaiParaSaldos` e `daListaOVoltarVaiParaOBoard` falham (o app fecha); `noBoardOVoltarSaiDoApp` passa.

- [ ] **Step 3: The three views and the back chain**

Em `SaldoApp.kt`, fora do composable (fim do arquivo ou logo antes de `fun SaldoApp`):

```kotlin
/**
 * O que a aba `saldos` mostra. O board é a home; a lista é o mesmo mês em linhas (o
 * ledger com os chips); a tag é a lista filtrada por uma etiqueta, onde a aba tags e o
 * "ver tag" de totais aterrissam.
 */
private enum class VistaSaldos { BOARD, LISTA, TAG }
```

Troque `var abrindoTag by rememberSaveable { mutableStateOf(false) }` por:

```kotlin
    var vistaSaldos by rememberSaveable { mutableStateOf(VistaSaldos.BOARD) }
```

e cada uso: `abrindoTag = false` → `vistaSaldos = VistaSaldos.BOARD`; `abrindoTag = true` (nos dois `onVerTag`/`onTagClick`) → `vistaSaldos = VistaSaldos.TAG`; `if (!abrindoTag)` → `if (vistaSaldos == VistaSaldos.BOARD)`. O `enum` entra no `rememberSaveable` sem `Saver` (é `Serializable`).

Depois de `LaunchedEffect(tab) { … }`, o handler:

```kotlin
    // Voltar: subtela → aba → saldos → sair. Um handler só, aqui, porque é aqui que as vistas
    // moram; a sheet e as subtelas de `mais` têm os seus e ganham por estarem mais fundo na
    // composição. Desabilitado no board para o sistema fechar o app — e só ele.
    val buscaAberta = buscaLedger != null
    BackHandler(
        enabled = !sheetAberto &&
            (abrindoRecorrencias || buscaAberta || vistaSaldos != VistaSaldos.BOARD || tab != SaldoTab.SALDOS),
    ) {
        when {
            abrindoRecorrencias -> abrindoRecorrencias = false
            buscaAberta -> ledgerVm.fecharBusca()
            vistaSaldos != VistaSaldos.BOARD -> {
                ledgerVm.definirTagFiltro(null)
                boardVm.irPara(ledgerVm.mesAtualAgora.coerceAtMost(YearMonth.now()))
                vistaSaldos = VistaSaldos.BOARD
            }
            else -> tab = SaldoTab.SALDOS
        }
    }
```

(`buscaLedger` já existe desde a Task 8; mova a linha `val buscaLedger by …` para antes do handler.) Import: `androidx.activity.compose.BackHandler`, `java.time.YearMonth` (já importado — remova a anotação de "unused" se houver).

- [ ] **Step 4: The list toggle, shared month, and the FAB**

Na chamada de `BoardScreen`:

```kotlin
                            onVerLista = {
                                ledgerVm.irPara(boardVm.mesAtualAgora)
                                vistaSaldos = VistaSaldos.LISTA
                            },
```

Na chamada de `LedgerScreen`:

```kotlin
                            onLimparTag = { ledgerVm.definirTagFiltro(null); vistaSaldos = VistaSaldos.LISTA },
                            onVerBoard = {
                                ledgerVm.definirTagFiltro(null)
                                boardVm.irPara(ledgerVm.mesAtualAgora.coerceAtMost(YearMonth.now()))
                                vistaSaldos = VistaSaldos.BOARD
                            },
```

Na `SaldoTabBar`:

```kotlin
                onSelect = { novo ->
                    if (novo == SaldoTab.SALDOS) vistaSaldos = VistaSaldos.BOARD
                    tab = novo
                },
                // O `+` lança no dia aberto do board — é o dia que o usuário está olhando.
                // Fora do board (lista, outras abas) é hoje, como sempre foi.
                onAdd = {
                    val dia = if (tab == SaldoTab.SALDOS && vistaSaldos == VistaSaldos.BOARD) boardVm.diaAbertoAgora else null
                    entryVm.iniciarNova(dia ?: LocalDate.now())
                    sheetAberto = true
                },
```

`onLimparTag` volta à LISTA e não ao board: quem tira o filtro quer ver o mês inteiro em linhas, que é o que estava vendo.

- [ ] **Step 5: The export dialog**

Substitua o `AlertDialog` de `escolhendoFormato`:

```kotlin
        if (escolhendoFormato) {
            AlertDialog(
                onDismissRequest = { escolhendoFormato = false },
                title = { Text("exportar dados") },
                // Os dois formatos no corpo, como linhas: "json" no slot de cancelar lia como
                // cancelar, e não havia cancelar de verdade.
                text = {
                    Column {
                        InsetRow(
                            label = "csv",
                            value = "abre em planilha",
                            onClick = { escolhendoFormato = false; exportarCsv.launch("saldo-export.csv") },
                        )
                        InsetRow(
                            label = "json",
                            value = "o dump completo",
                            onClick = { escolhendoFormato = false; exportarJson.launch("saldo-export.json") },
                        )
                    }
                },
                confirmButton = {},
                dismissButton = { TextButton(onClick = { escolhendoFormato = false }) { Text("cancelar") } },
            )
        }
```

`escolhendoFormato` vira `rememberSaveable`. Import: `com.scholze.saldo.ui.components.InsetRow`.

- [ ] **Step 6: FAB test** (append em `VistaSaldosTest`)

```kotlin
    /** Com um dia aberto na grade, o `+` lança naquele dia — e a sheet diz a data. */
    @Test
    fun oMaisLancaNoDiaAbertoDoBoard() {
        app()
        ActivityScenario.launch(MainActivity::class.java).use {
            esperarBoard()
            val dia1 = LocalDate.now().withDayOfMonth(1)
            rule.onNodeWithTag(TAG_BOARD_GRADE).performScrollToNode(hasTestTag(tagCelula(dia1)))
            rule.onNodeWithTag(tagCelula(dia1), useUnmergedTree = true).performClick()
            rule.onNodeWithTag(TAG_ADD).performClick()
            val rotulo = dia1.format(DateTimeFormatter.ofPattern("EEE, d MMM", Locale.forLanguageTag("pt-BR"))).replace(".", "")
            rule.onNodeWithText(rotulo, substring = true).assertIsDisplayed()
        }
    }
```

Imports: `com.scholze.saldo.ui.board.tagCelula`, `com.scholze.saldo.ui.nav.TAG_ADD`, `androidx.compose.ui.test.performScrollToNode`, `hasTestTag`. Se hoje for dia 1, o dia aberto já é hoje e o rótulo começa com "hoje, " — o `substring = true` cobre.

- [ ] **Step 7: Un-ignore the ledger search tests** da Task 8 e rode os quatro arquivos:

Run: `mise exec -- ./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.scholze.saldo.SaldoAppBackTest,com.scholze.saldo.VistaSaldosTest,com.scholze.saldo.LedgerScreenTest,com.scholze.saldo.EntryFlowTest`
Expected: PASS. Os três testes ponta-a-ponta que "trocam para a lista antes de exercitar o ledger" (`EntryFlowTest`, `LedgerScreenTest`, `SwipeDeleteTest`) podem trocar o caminho antigo pelo toque em "ver como lista" — faça isso se o caminho antigo quebrou.

- [ ] **Step 8: Full device suite, lint, commit**

Run: `mise run test-device` (duas vezes se algum `Activity never becomes DESTROYED` aparecer — é flake de emulador cansado) e `mise exec -- ./gradlew lintDebug`.

```bash
git add app/src/main/kotlin/com/scholze/saldo/ui/SaldoApp.kt app/src/androidTest/kotlin/com/scholze/saldo
git commit -m "feat: Voltar volta, a aba saldos tem três vistas, e o + lança no dia aberto"
```

---

### Task 10: Teclado — a tecla `00`

**Files:**
- Modify: `app/src/main/kotlin/com/scholze/saldo/ui/entry/AmountKeypadScreen.kt:87-90,103-135,137-141,160-175`
- Test: `app/src/androidTest/kotlin/com/scholze/saldo/EntryFlowTest.kt` (append)

**Interfaces:**
- Produces: a tecla `00` no lugar da `,`; `Key.DuploZero` substitui `Key.Comma`.
- Consumes: nada.

- [ ] **Step 1: Write the failing test** (append em `EntryFlowTest`)

```kotlin
    /** `5`, `0`, `00` = R$ 50,00: a tecla do ponto de venda que a vírgula inerte ocupava. */
    @Test
    fun aTeclaDuploZeroAnexaDoisZeros() {
        abrirBoardRevelado()
        rule.onNodeWithTag(TAG_ADD).performClick()
        rule.onNodeWithText("0,00").performClick()
        listOf("5", "0", "00").forEach { d ->
            rule.onNode(hasAnyAncestor(hasTestTag(TAG_TECLADO)) and hasText(d)).performClick()
        }
        rule.onNodeWithText("R$ 50,00").assertIsDisplayed()
        rule.onAllNodesWithText(",").assertCountEquals(0)
    }
```

- [ ] **Step 2: Run to verify it fails**

Run: `mise exec -- ./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.scholze.saldo.EntryFlowTest`
Expected: FAIL — não há nó "00".

- [ ] **Step 3: Implementation**

Em `AmountKeypadScreen.kt`:

- `Keypad` recebe `onDuploZero: () -> Unit`; a última linha vira `listOf(Key.DuploZero, Key.Digit(0), Key.Backspace)` e o `when` do clique ganha `Key.DuploZero -> onDuploZero()` (apague o ramo `Key.Comma -> Unit` e o comentário sobre a vírgula);
- a chamada: 
  ```kotlin
        Keypad(
            onDigit = { d -> centavos = (centavos * 10 + d).coerceAtMost(TETO) },
            // "00" é a tecla do ponto de venda: com os centavos sempre nos dois últimos
            // dígitos, é ela que faz R$ 50 sair em três toques. No zero não faz nada.
            onDuploZero = { centavos = (centavos * 100).coerceAtMost(TETO) },
            onBackspace = { centavos /= 10 },
        )
  ```
  com `private const val TETO = 99_999_999_99L` no topo do arquivo;
- `sealed interface Key`: `data object DuploZero : Key` no lugar de `Comma`;
- `KeyButton`: `Key.DuploZero -> KeyLabel("00")`.

- [ ] **Step 4: Run the test**

Run: `mise exec -- ./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.scholze.saldo.EntryFlowTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/scholze/saldo/ui/entry/AmountKeypadScreen.kt app/src/androidTest/kotlin/com/scholze/saldo/EntryFlowTest.kt
git commit -m "feat: a tecla 00 no lugar da vírgula inerte"
```

---

### Task 11: A sheet — "repetir" editável na edição

**Files:**
- Modify: `app/src/main/kotlin/com/scholze/saldo/ui/entry/EntryViewModel.kt`
- Modify: `app/src/main/kotlin/com/scholze/saldo/ui/entry/NewEntrySheet.kt`
- Test: `app/src/test/kotlin/com/scholze/saldo/ui/entry/EntryViewModelTest.kt` (create)
- Test: `app/src/androidTest/kotlin/com/scholze/saldo/ui/entry/NewEntrySheetTest.kt` (create)

**Interfaces:**
- Produces: `EntryUiState.repetirOriginal: RepetirOpcao` (o que a linha era ao abrir); `EntryViewModel.salvar` decide entre `criar`, `editar`, `converterEmRecorrencia`, `encerrarRecorrencia`; `iniciarEdicao(mov, diaDoTemplate: Int? = null)`; `EntryUiState.precisaEscopo`.
- Consumes: `SaldoRepository.converterEmRecorrencia/encerrarRecorrencia` (Task 3).

- [ ] **Step 1: JVM test of the decision**

`app/src/test/kotlin/com/scholze/saldo/ui/entry/EntryViewModelTest.kt`:

```kotlin
package com.scholze.saldo.ui.entry

import com.scholze.saldo.RepositorioFixo
import com.scholze.saldo.data.SaldoRepository
import com.scholze.saldo.domain.CartaoConfig
import com.scholze.saldo.domain.EscopoEdicao
import com.scholze.saldo.domain.LedgerInput
import com.scholze.saldo.domain.Movimentacao
import com.scholze.saldo.domain.Natureza
import com.scholze.saldo.domain.RepetirOpcao
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EntryViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val input = LedgerInput(
        saldoInicialCentavos = 0, saldoInicialData = LocalDate.parse("2026-01-01"),
        movimentacoes = emptyList(), recorrencias = emptyList(), mesesMaterializados = emptySet(),
        cartao = CartaoConfig(), hoje = LocalDate.parse("2026-09-07"),
    )

    /** Anota qual escrita foi chamada. */
    private class RepoEspiao(input: LedgerInput) : SaldoRepository by RepositorioFixo(input) {
        val chamadas = mutableListOf<String>()
        override suspend fun editar(mov: Movimentacao, escopo: EscopoEdicao) { chamadas += "editar:$escopo" }
        override suspend fun converterEmRecorrencia(mov: Movimentacao, diaDoMes: Int) { chamadas += "converter:$diaDoMes" }
        override suspend fun encerrarRecorrencia(mov: Movimentacao) { chamadas += "encerrar" }
        override suspend fun criar(mov: Movimentacao, repetir: RepetirOpcao) { chamadas += "criar" }
    }

    private val avulsa = Movimentacao(id = 5, descricao = "luz", valorCentavos = -120_00, data = LocalDate.parse("2026-09-10"), natureza = Natureza.DIARIO)
    private val instancia = avulsa.copy(id = 6, recorrenciaId = 3)

    @Before fun setMain() = Dispatchers.setMain(dispatcher)
    @After fun resetMainDispatcher() = Dispatchers.resetMain()

    @Test
    fun avulsaQueViraMensalConverte() = runTest(dispatcher) {
        val repo = RepoEspiao(input)
        val vm = EntryViewModel(repo)
        vm.iniciarEdicao(avulsa)
        vm.definirRepetir(RepetirOpcao.TodoMes(10))
        vm.salvar(EscopoEdicao.SO_ESTE_MES) {}
        advanceUntilIdle()
        assertEquals(listOf("editar:SO_ESTE_MES", "converter:10"), repo.chamadas)
    }

    @Test
    fun mensalQueParaEncerra() = runTest(dispatcher) {
        val repo = RepoEspiao(input)
        val vm = EntryViewModel(repo)
        vm.iniciarEdicao(instancia, diaDoTemplate = 10)
        vm.definirRepetir(RepetirOpcao.Nao)
        vm.salvar(EscopoEdicao.SO_ESTE_MES) {}
        advanceUntilIdle()
        assertEquals(listOf("editar:SO_ESTE_MES", "encerrar"), repo.chamadas)
    }

    @Test
    fun mensalQueContinuaMensalSoEdita() = runTest(dispatcher) {
        val repo = RepoEspiao(input)
        val vm = EntryViewModel(repo)
        vm.iniciarEdicao(instancia, diaDoTemplate = 10)
        vm.definirCentavos(130_00)
        vm.salvar(EscopoEdicao.DAQUI_EM_DIANTE) {}
        advanceUntilIdle()
        assertEquals(listOf("editar:DAQUI_EM_DIANTE"), repo.chamadas)
    }

    @Test
    fun oDiaMostradoEODoTemplateNaoODaData() {
        val vm = EntryViewModel(RepoEspiao(input))
        vm.iniciarEdicao(instancia.copy(data = LocalDate.parse("2026-02-28")), diaDoTemplate = 31)
        assertEquals(RepetirOpcao.TodoMes(31), vm.formAgora.repetir)
        assertEquals(RepetirOpcao.TodoMes(31), vm.formAgora.repetirOriginal)
    }

    @Test
    fun soPedeEscopoQuandoContinuaMensal() {
        val vm = EntryViewModel(RepoEspiao(input))
        vm.iniciarEdicao(instancia, diaDoTemplate = 10)
        assertEquals(true, vm.formAgora.precisaEscopo)
        vm.definirRepetir(RepetirOpcao.Nao)
        assertEquals(false, vm.formAgora.precisaEscopo)
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `mise exec -- ./gradlew :app:testDebugUnitTest --tests "com.scholze.saldo.ui.entry.EntryViewModelTest"`
Expected: compilação falha — `diaDoTemplate`, `formAgora`, `repetirOriginal`, `precisaEscopo`.

- [ ] **Step 3: The view model**

`EntryUiState` ganha, depois de `repetir`:

```kotlin
    /** O que a linha era ao abrir a edição; é a diferença para [repetir] que decide a escrita. */
    val repetirOriginal: RepetirOpcao = RepetirOpcao.Nao,
```

e, no corpo:

```kotlin
    /** Editando uma instância que CONTINUA mensal: só aí "só este mês / daqui em diante" faz sentido. */
    val precisaEscopo: Boolean
        get() = editandoId != null && recorrenciaId != null && repetir is RepetirOpcao.TodoMes
```

Em `EntryViewModel`:

```kotlin
    /** O formulário como está — a shell decide o escopo por ele, e os testes o leem. */
    val formAgora: EntryUiState get() = form.value

    /**
     * [diaDoTemplate] é o dia da recorrência, quando a linha é uma instância: a data da linha
     * pode estar clamped (dia 31 num mês de 30) e ler o dia dela devolveria o dia errado ao
     * template na primeira edição de fevereiro.
     */
    fun iniciarEdicao(mov: Movimentacao, diaDoTemplate: Int? = null) {
        original.value = Original(mov.valorCentavos, mov.data, mov.natureza)
        val repetir = if (mov.recorrenciaId != null) RepetirOpcao.TodoMes(diaDoTemplate ?: mov.data.dayOfMonth) else RepetirOpcao.Nao
        form.value = EntryUiState(
            editandoId = mov.id,
            recorrenciaId = mov.recorrenciaId,
            saida = mov.valorCentavos < 0,
            natureza = mov.natureza,
            centavos = kotlin.math.abs(mov.valorCentavos),
            descricao = mov.descricao,
            data = mov.data,
            repetir = repetir,
            repetirOriginal = repetir,
            tagsSelecionadas = mov.tags,
        )
    }
```

e `salvar`:

```kotlin
    /** [escopo] só é consultado quando se edita uma instância que continua mensal. */
    fun salvar(escopo: EscopoEdicao, onDone: () -> Unit) {
        val f = form.value
        if (!f.podeSalvar) return
        escrever("salvar", "não foi possível salvar", onDone) {
            val mov = Movimentacao(
                id = f.editandoId ?: 0,
                descricao = f.descricao.trim(),
                valorCentavos = f.valorAssinado,
                data = f.data,
                natureza = f.natureza,
                recorrenciaId = f.recorrenciaId,
                tags = f.tagsSelecionadas,
            )
            when {
                f.editandoId == null -> repo.criar(mov, f.repetir)
                // Os campos gravam primeiro, sempre SÓ nesta linha; depois a recorrência muda
                // de estado. Uma avulsa que vira mensal leva os valores novos para o template.
                f.repetirOriginal is RepetirOpcao.Nao && f.repetir is RepetirOpcao.TodoMes -> {
                    repo.editar(mov, EscopoEdicao.SO_ESTE_MES)
                    repo.converterEmRecorrencia(mov, f.repetir.dia)
                }
                f.repetirOriginal is RepetirOpcao.TodoMes && f.repetir is RepetirOpcao.Nao -> {
                    repo.editar(mov, EscopoEdicao.SO_ESTE_MES)
                    repo.encerrarRecorrencia(mov)
                }
                else -> repo.editar(mov, escopo)
            }
        }
    }
```

- [ ] **Step 4: Run the JVM test**

Run: `mise exec -- ./gradlew :app:testDebugUnitTest --tests "com.scholze.saldo.ui.entry.EntryViewModelTest"`
Expected: PASS.

- [ ] **Step 5: The sheet**

Em `NewEntrySheet.kt`:

- os sete `remember { mutableStateOf(...) }` de `pedindoEscopo`, `pedindoExclusao`, `escolhendoData`, `escolhendoRepetir`, `escolhendoTags`, `editandoDescricao` viram `rememberSaveable { mutableStateOf(...) }` (`editandoValor` já é);
- `salvar`:
  ```kotlin
    val salvar: () -> Unit = {
        if (state.precisaEscopo) pedindoEscopo = true
        else vm.salvar(EscopoEdicao.SO_ESTE_MES) { onFechar() }
    }
  ```
- a linha "repetir" deixa de ter o `if (state.editandoId == null)`: é sempre
  ```kotlin
                InsetRow(
                    label = "repetir",
                    value = rotuloRepetir(state.repetir),
                    onClick = { escolhendoRepetir = true },
                )
  ```
  (apague o comentário "Numa edição a recorrência não se liga nem desliga por aqui");
- o diálogo `escolhendoRepetir` ganha um `dismissButton = { TextButton(onClick = { escolhendoRepetir = false }) { Text("cancelar") } }` e, quando a linha já é mensal, a opção "não repete" passa a dizer o que faz:
  ```kotlin
                    Text(
                        if (state.repetirOriginal is RepetirOpcao.TodoMes) "parar de repetir a partir deste mês" else "não repete",
                        …
  ```
- o diálogo `escolhendoTags` ganha `dismissButton = { TextButton(onClick = { escolhendoTags = false }) { Text("cancelar") } }` e o `confirmButton` "ok" fica;
- o texto "excluir recorrência"/"excluir movimentação" e `pedindoExclusao` continuam olhando `ehRecorrente = state.recorrenciaId != null` — não muda.

- [ ] **Step 6: Pass the template day from the shell**

Em `SaldoApp.kt`, `abrirMovimentacao`:

```kotlin
    val abrirMovimentacao: (Movimentacao) -> Unit = {
        if (it.id != 0L) {
            // O dia do template, não o da data: a data pode estar clamped (31 → 28 em fevereiro).
            val dia = it.recorrenciaId?.let { id -> ledgerState.recorrencias.firstOrNull { r -> r.id == id }?.diaDoMes }
            entryVm.iniciarEdicao(it, diaDoTemplate = dia)
            sheetAberto = true
        }
    }
```

`LedgerUiState.recorrencias` já existe desde a Task 8.

- [ ] **Step 7: Sheet test**

`app/src/androidTest/kotlin/com/scholze/saldo/ui/entry/NewEntrySheetTest.kt`:

```kotlin
package com.scholze.saldo.ui.entry

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNode
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.scholze.saldo.SaldoApplication
import com.scholze.saldo.domain.Movimentacao
import com.scholze.saldo.domain.Natureza
import com.scholze.saldo.ui.theme.SaldoTheme
import java.time.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NewEntrySheetTest {

    @get:Rule val rule = createComposeRule()

    private fun vm() = EntryViewModel(ApplicationProvider.getApplicationContext<SaldoApplication>().container.repository)

    private val instancia = Movimentacao(
        id = 6, descricao = "luz", valorCentavos = -120_00, data = LocalDate.parse("2026-02-28"),
        natureza = Natureza.DIARIO, recorrenciaId = 3,
    )

    @Test
    fun naEdicaoRepetirEUmControleEMostraODiaDoTemplate() {
        val vm = vm()
        vm.iniciarEdicao(instancia, diaDoTemplate = 31)
        rule.setContent { SaldoTheme { NewEntrySheet(vm = vm, onFechar = {}) } }
        rule.onNode(hasText("todo mês no dia 31") and hasClickAction()).assertIsDisplayed()
        rule.onNodeWithText("todo mês no dia 31").performClick()
        rule.onNodeWithText("parar de repetir a partir deste mês").assertIsDisplayed()
        rule.onNodeWithText("cancelar").performClick()
    }

    @Test
    fun aAvulsaOfereceTodoMesNaEdicao() {
        val vm = vm()
        vm.iniciarEdicao(instancia.copy(recorrenciaId = null, data = LocalDate.parse("2026-09-10")))
        rule.setContent { SaldoTheme { NewEntrySheet(vm = vm, onFechar = {}) } }
        rule.onNodeWithText("não repete").performClick()
        rule.onNodeWithText("todo mês no dia 10").assertIsDisplayed()
    }

    @Test
    fun oDialogoDeTagsTemCancelar() {
        val vm = vm()
        vm.iniciarNova(LocalDate.now())
        rule.setContent { SaldoTheme { NewEntrySheet(vm = vm, onFechar = {}) } }
        rule.onNodeWithText("tags").performClick()
        rule.onNodeWithText("cancelar").assertIsDisplayed()
    }
}
```

- [ ] **Step 8: Run device tests**

Run: `mise exec -- ./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.scholze.saldo.ui.entry.NewEntrySheetTest,com.scholze.saldo.EntryFlowTest`
Expected: PASS. Se "cancelar" achar dois nós no primeiro teste (o da nav bar da sheet e o do diálogo), use `onAllNodesWithText("cancelar").onLast()`.

- [ ] **Step 9: Lint + JVM, commit**

```bash
git add app/src/main/kotlin/com/scholze/saldo/ui/entry app/src/main/kotlin/com/scholze/saldo/ui/ledger/LedgerViewModel.kt app/src/main/kotlin/com/scholze/saldo/ui/SaldoApp.kt app/src/test/kotlin/com/scholze/saldo/ui/entry/EntryViewModelTest.kt app/src/androidTest/kotlin/com/scholze/saldo/ui/entry
git commit -m "feat: repetir é editável na edição — avulsa vira mensal, mensal para"
```

---

### Task 12: Recorrências — pausar e retomar

**Files:**
- Modify: `app/src/main/kotlin/com/scholze/saldo/ui/totais/RecorrenciasViewModel.kt`
- Modify: `app/src/main/kotlin/com/scholze/saldo/ui/totais/RecorrenciasScreen.kt`
- Test: `app/src/androidTest/kotlin/com/scholze/saldo/ui/totais/RecorrenciasScreenTest.kt` (append)

**Interfaces:**
- Produces: `RecorrenciasViewModel.alternarPausa(rec: Recorrencia)`; na tela, um `Switch` por linha com `contentDescription = "pausar <descrição>"` / `"retomar <descrição>"`.
- Consumes: `SaldoRepository.pausar/retomar` (Task 3), `ResumoRecorrencias` (Task 5).

- [ ] **Step 1: Screen test** (append em `RecorrenciasScreenTest`; siga o padrão do arquivo para montar a tela com o repositório do container)

```kotlin
    @Test
    fun oInterruptorPausaEALinhaDizPausada() {
        val app = ApplicationProvider.getApplicationContext<SaldoApplication>()
        val hoje = LocalDate.now()
        runBlocking {
            app.container.settings.definirSaldoInicial(100_000_00, hoje)
            app.container.repository.criar(
                Movimentacao(descricao = "academia", valorCentavos = -120_00, data = hoje.withDayOfMonth(5), natureza = Natureza.DIARIO),
                RepetirOpcao.TodoMes(5),
            )
        }
        ActivityScenario.launch<MainActivity>(MainActivity.intent(app, Destino.Totais(YearMonth.from(hoje)))).use {
            rule.waitUntil(5_000) { rule.onAllNodesWithText("a caminho").fetchSemanticsNodes().isNotEmpty() }
            rule.onNodeWithText("a caminho").performClick()
            rule.onNodeWithText("recorrências").performScrollTo().performClick()
            rule.waitUntil(5_000) { rule.onAllNodesWithContentDescription("pausar academia").fetchSemanticsNodes().isNotEmpty() }

            rule.onNodeWithContentDescription("pausar academia").performClick()
            rule.waitUntil(5_000) { rule.onAllNodesWithText("pausada").fetchSemanticsNodes().isNotEmpty() }
            rule.onNodeWithContentDescription("retomar academia").assertIsDisplayed()
            // e continua na lista, não em "encerradas"
            rule.onAllNodesWithText("encerradas", substring = true).assertCountEquals(0)
        }
    }
```

- [ ] **Step 2: Run to verify it fails**

Run: `mise exec -- ./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.scholze.saldo.ui.totais.RecorrenciasScreenTest`
Expected: FAIL — nenhum nó "pausar academia".

- [ ] **Step 3: View model**

```kotlin
    /** Pausar/retomar, pelo `ativa` do template — ver `SaldoRepository.pausar` para o que fica e o que some. */
    fun alternarPausa(rec: Recorrencia) {
        viewModelScope.launch {
            try {
                if (rec.ativa) repo.pausar(rec.id, LocalDate.now()) else repo.retomar(rec.id, LocalDate.now())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "alternarPausa(${rec.id}) falhou", e)
            }
        }
    }
```

Import: `java.time.LocalDate`.

- [ ] **Step 4: Screen**

`LinhaRecorrencia` ganha `onPausa: (() -> Unit)?` e desenha o `Switch` à direita do valor:

```kotlin
@Composable
private fun LinhaRecorrencia(rec: Recorrencia, mes: YearMonth, onClick: (() -> Unit)?, onPausa: (() -> Unit)? = null) {
    val colors = SaldoTheme.colors
    val base = Modifier.fillMaxWidth()
    Row(
        (if (onClick != null) base.clickable(onClick = onClick) else base).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("dia ${rec.diaDoMes}", Modifier.width(50.dp), style = SaldoTheme.type.footnote, color = colors.secondaryLabel)
        Column(Modifier.weight(1f)) {
            DescricaoTexto(rec.descricao)
            val nota = when {
                rec.inicio > mes -> "começa em " + rec.inicio.rotuloCurto()
                !rec.ativa -> "pausada"
                rec.fim != null -> "até " + rec.fim.rotuloCurto()
                else -> null
            }
            if (nota != null) {
                Text(nota, style = SaldoTheme.type.caption, color = colors.secondaryLabel)
            }
        }
        MoneyText(
            centavos = rec.valorCentavos,
            style = SaldoTheme.type.body,
            color = if (!rec.ativa) colors.secondaryLabel else if (rec.valorCentavos > 0) colors.positive else colors.label,
            formato = FormatoMoney.ASSINADO,
        )
        if (onPausa != null) {
            Switch(
                checked = rec.ativa,
                onCheckedChange = { onPausa() },
                modifier = Modifier.semantics {
                    contentDescription = (if (rec.ativa) "pausar " else "retomar ") + rec.descricao.descricaoVisivel()
                },
            )
        }
    }
}
```

(mantenha o resto do corpo da linha como está — o trecho acima substitui só o que difere; se o `MoneyText` original tinha outra cor, preserve-a e só acrescente o caso `!rec.ativa`). Na lista de `r.ativas`, passe `onPausa = { vm.alternarPausa(rec) }`; nas encerradas, nada. Imports: `androidx.compose.material3.Switch`, `androidx.compose.ui.semantics.contentDescription`, `androidx.compose.ui.semantics.semantics`, `com.scholze.saldo.domain.descricaoVisivel`.

- [ ] **Step 5: Run the test**

Run: `mise exec -- ./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.scholze.saldo.ui.totais.RecorrenciasScreenTest`
Expected: PASS.

- [ ] **Step 6: Lint + JVM, commit**

```bash
git add app/src/main/kotlin/com/scholze/saldo/ui/totais/RecorrenciasViewModel.kt app/src/main/kotlin/com/scholze/saldo/ui/totais/RecorrenciasScreen.kt app/src/androidTest/kotlin/com/scholze/saldo/ui/totais/RecorrenciasScreenTest.kt
git commit -m "feat: pausar e retomar uma recorrência pela tela dela"
```

---

### Task 13: Tags — desfazer, cor, botões de 48 dp, vazio

**Files:**
- Modify: `app/src/main/kotlin/com/scholze/saldo/ui/tags/TagsViewModel.kt`
- Modify: `app/src/main/kotlin/com/scholze/saldo/ui/tags/TagsScreen.kt`
- Modify: `app/src/main/kotlin/com/scholze/saldo/ui/SaldoApp.kt` (snackbar)
- Modify: `app/src/main/kotlin/com/scholze/saldo/ui/components/Icons.kt` (`SaldoIcon.LAPIS`, `SaldoIcon.LIXEIRA`)
- Test: `app/src/androidTest/kotlin/com/scholze/saldo/ui/tags/TagsScreenTest.kt` (create)

**Interfaces:**
- Produces: `TagsViewModel.exclusoes: SharedFlow<TagSnapshot>`, `desfazerExclusao(snapshot)`, `recolorir(tag, cor)`; `TagsScreen` com botões `IconeRedondo` ("editar <nome>", "excluir <nome>"), linha de cores no renomear, vazio; `SaldoApp` mostra "tag excluída · desfazer".
- Consumes: `SaldoRepository.excluirTag/restaurarTag/recolorirTag` (Task 4), `PaletaTags` (Task 1).

- [ ] **Step 1: Test**

`app/src/androidTest/kotlin/com/scholze/saldo/ui/tags/TagsScreenTest.kt`:

```kotlin
package com.scholze.saldo.ui.tags

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.scholze.saldo.EstadoLimpo
import com.scholze.saldo.SaldoApplication
import com.scholze.saldo.domain.PaletaTags
import com.scholze.saldo.domain.TagSnapshot
import com.scholze.saldo.ui.theme.SaldoTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TagsScreenTest {

    @get:Rule(order = 0) val estadoLimpo = EstadoLimpo()
    @get:Rule(order = 1) val rule = createComposeRule()

    private val container get() = ApplicationProvider.getApplicationContext<SaldoApplication>().container

    private fun montar(vm: TagsViewModel = TagsViewModel(container.repository)): TagsViewModel {
        rule.setContent { SaldoTheme { TagsScreen(vm = vm, onTagClick = {}) } }
        return vm
    }

    @Test
    fun semTagsATelaExplicaOQueEUmaTag() {
        montar()
        rule.onNodeWithText("uma tag é uma etiqueta", substring = true).assertIsDisplayed()
        rule.onAllNodesWithText("toque numa tag para ver só ela no ledger").assertCountEquals(0)
    }

    @Test
    fun excluirEmiteOSnapshotParaDesfazer() {
        runBlocking { container.repository.criarTag("mercado", PaletaTags.cores[0]) }
        val vm = montar()
        var snapshot: TagSnapshot? = null
        val escopo = CoroutineScope(Dispatchers.Main)
        val coleta = escopo.launch { snapshot = vm.exclusoes.first() }
        rule.waitUntil(5_000) { rule.onAllNodesWithContentDescription("excluir mercado").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithContentDescription("excluir mercado").performClick()
        rule.onNodeWithText("excluir").performClick()                // o botão do diálogo
        rule.waitUntil(5_000) { snapshot != null }
        assertEquals("mercado", snapshot!!.tag.nome)
        coleta.cancel()
    }

    @Test
    fun renomearTambemTrocaACor() {
        runBlocking { container.repository.criarTag("mercado", PaletaTags.cores[0]) }
        val vm = montar()
        rule.waitUntil(5_000) { rule.onAllNodesWithContentDescription("editar mercado").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithContentDescription("editar mercado").performClick()
        rule.onNodeWithContentDescription("cor 3").performClick()
        rule.onNodeWithText("salvar").performClick()
        rule.waitUntil(5_000) { runBlocking { container.repository.tags.first().single().cor } == PaletaTags.cores[2] }
    }

    @Test
    fun osBotoesDaLinhaTem48dp() {
        runBlocking { container.repository.criarTag("mercado", PaletaTags.cores[0]) }
        montar()
        rule.waitUntil(5_000) { rule.onAllNodesWithContentDescription("editar mercado").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithContentDescription("editar mercado").assertHeightIsAtLeast(44.dp)
        rule.onNodeWithContentDescription("excluir mercado").assertHeightIsAtLeast(44.dp)
    }
}
```

(`IconeRedondo` mede 44 dp — é o alvo do M3 para ícone; o spec fala em 48 dp para linhas de texto, e aqui o alvo redondo do resto do app é o padrão a seguir.) Imports extras: `kotlinx.coroutines.CoroutineScope`, `kotlinx.coroutines.Dispatchers`, `kotlinx.coroutines.launch`, `kotlinx.coroutines.cancel`.

- [ ] **Step 2: Run to verify it fails**

Run: `mise exec -- ./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.scholze.saldo.ui.tags.TagsScreenTest`
Expected: compilação falha — `exclusoes` não existe.

- [ ] **Step 3: View model**

```kotlin
    private val _exclusoes = MutableSharedFlow<TagSnapshot>(extraBufferCapacity = 1)

    /** A tag apagada, para o "desfazer" do snackbar — o mesmo caminho das movimentações. */
    val exclusoes: SharedFlow<TagSnapshot> = _exclusoes

    fun criar(nome: String, cor: Long) = escrever("criarTag") { repo.criarTag(nome, cor) }
    fun renomear(tag: Tag, nome: String) = escrever("renomearTag") { repo.renomearTag(tag.id, nome) }
    fun recolorir(tag: Tag, cor: Long) = escrever("recolorirTag") { repo.recolorirTag(tag.id, cor) }
    fun excluir(tag: Tag) = escrever("excluirTag") { _exclusoes.emit(repo.excluirTag(tag.id)) }
    fun desfazerExclusao(snapshot: TagSnapshot) = escrever("restaurarTag") { repo.restaurarTag(snapshot) }
```

Imports: `com.scholze.saldo.domain.TagSnapshot`, `kotlinx.coroutines.flow.MutableSharedFlow`, `kotlinx.coroutines.flow.SharedFlow`.

- [ ] **Step 4: Icons** (`SaldoIcon.LAPIS`, `SaldoIcon.LIXEIRA`)

```kotlin
            // Uma diagonal com a ponta: o lápis.
            SaldoIcon.LAPIS -> {
                drawLine(tint, Offset(w * 0.22f, h * 0.78f), Offset(w * 0.70f, h * 0.30f), sw, StrokeCap.Round)
                drawLine(tint, Offset(w * 0.70f, h * 0.30f), Offset(w * 0.78f, h * 0.22f), sw, StrokeCap.Round)
                drawLine(tint, Offset(w * 0.22f, h * 0.78f), Offset(w * 0.20f, h * 0.80f), sw, StrokeCap.Round)
            }
            // A tampa e o balde: a lixeira.
            SaldoIcon.LIXEIRA -> {
                drawLine(tint, Offset(w * 0.22f, h * 0.30f), Offset(w * 0.78f, h * 0.30f), sw, StrokeCap.Round)
                drawLine(tint, Offset(w * 0.30f, h * 0.30f), Offset(w * 0.34f, h * 0.78f), sw, StrokeCap.Round)
                drawLine(tint, Offset(w * 0.70f, h * 0.30f), Offset(w * 0.66f, h * 0.78f), sw, StrokeCap.Round)
                drawLine(tint, Offset(w * 0.34f, h * 0.78f), Offset(w * 0.66f, h * 0.78f), sw, StrokeCap.Round)
            }
```

- [ ] **Step 5: Screen**

Em `TagsScreen.kt`:

- a linha da tag: troque os dois `Text("editar"…)`/`Text("excluir"…)` por
  ```kotlin
                        IconeRedondo(SaldoIcon.LAPIS, "editar ${tag.nome}", onClick = { renomeandoId = tag.id })
                        IconeRedondo(SaldoIcon.LIXEIRA, "excluir ${tag.nome}", onClick = { excluindoId = tag.id })
  ```
  e reduza o `padding(vertical = 12.dp)` da `Row` para `vertical = 4.dp` (os 44 dp do ícone já dão a altura);
- `criando`, `renomeando`, `excluindo` viram `rememberSaveable` (`Tag` não é `Saveable`: guarde o **id** — `var renomeandoId by rememberSaveable { mutableStateOf<Long?>(null) }` e resolva `state.tags.firstOrNull { it.first.id == renomeandoId }?.first` na hora de desenhar; idem `excluindoId`);
- o vazio, antes do `InsetGroup`, quando `state.tags.isEmpty()`:
  ```kotlin
            if (state.tags.isEmpty()) {
                Text(
                    "uma tag é uma etiqueta: mercado, casa, lazer. Toque numa tag para ver só ela.",
                    style = SaldoTheme.type.footnote, color = colors.secondaryLabel,
                )
            }
  ```
  e o rodapé "toque numa tag para ver só ela no ledger" só quando `state.tags.isNotEmpty()`;
- o diálogo de renomear ganha a cor — o `text`:
  ```kotlin
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(value = nome, onValueChange = { nome = it }, singleLine = true)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        PaletaTags.cores.forEachIndexed { i, c ->
                            Box(
                                Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(Color(c))
                                    .then(if (c == cor) Modifier.border(3.dp, colors.label, CircleShape) else Modifier)
                                    .clickable { cor = c }
                                    .semantics { contentDescription = "cor ${i + 1}" },
                            )
                        }
                    }
                }
            },
  ```
  com `var cor by remember(tag) { mutableStateOf(tag.cor) }` ao lado de `nome`, e o "salvar":
  ```kotlin
                TextButton(onClick = {
                    if (nome.isNotBlank() && nome.trim() != tag.nome) vm.renomear(tag, nome.trim())
                    if (cor != tag.cor) vm.recolorir(tag, cor)
                    renomeandoId = null
                }) { Text("salvar") }
  ```
  O título vira "editar tag". Imports: `androidx.compose.foundation.border`, `androidx.compose.ui.semantics.*`, `com.scholze.saldo.ui.components.IconeRedondo`, `com.scholze.saldo.ui.components.SaldoIcon`.

- [ ] **Step 6: The snackbar in SaldoApp**

Depois do `LaunchedEffect(Unit)` das exclusões de movimentação, acrescente (o `TagsViewModel` precisa existir fora do `when` da aba: troque `viewModel(factory = tagsFactory)` dentro de `SaldoTab.TAGS` por uma `val tagsVm: TagsViewModel = viewModel(factory = tagsFactory)` ao lado dos outros e passe `vm = tagsVm`):

```kotlin
    LaunchedEffect(Unit) {
        tagsVm.exclusoes.collect { snapshot ->
            val resultado = snackbar.showSnackbar(message = "tag excluída", actionLabel = "desfazer")
            if (resultado == SnackbarResult.ActionPerformed) tagsVm.desfazerExclusao(snapshot)
        }
    }
```

- [ ] **Step 7: Run the tests**

Run: `mise exec -- ./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.scholze.saldo.ui.tags.TagsScreenTest`
Expected: PASS.

- [ ] **Step 8: Lint + JVM, commit**

```bash
git add app/src/main/kotlin/com/scholze/saldo/ui/tags app/src/main/kotlin/com/scholze/saldo/ui/SaldoApp.kt app/src/main/kotlin/com/scholze/saldo/ui/components/Icons.kt app/src/androidTest/kotlin/com/scholze/saldo/ui/tags
git commit -m "feat: tag apagada tem desfazer, muda de cor, e a aba vazia explica"
```

---

### Task 14: Gráficos — "precisa de mais um mês"

**Files:**
- Modify: `app/src/main/kotlin/com/scholze/saldo/ui/totais/SegmentoTendencia.kt`
- Test: `app/src/androidTest/kotlin/com/scholze/saldo/ui/totais/TotaisContentTest.kt` (append)

**Interfaces:**
- Produces: em `SegmentoTendencia`, com menos de dois meses com movimento, `TrendChart` + legenda e `ReservaLine` + `PoupancaBars` dão lugar a `PrecisaDeMaisUmMes()`; `const val TAG_PRECISA_MAIS_UM_MES`.
- Consumes: `PontoMes`.

- [ ] **Step 1: Test** (append em `TotaisContentTest`; use o helper que o arquivo tem para montar o segmento de tendência com uma lista de `PontoMes` — se ele monta `TotaisScreen` inteira com um `LedgerInput`, monte um input com movimentação só no mês corrente)

```kotlin
    @Test
    fun comUmMesSoATendenciaPedeMaisUmMes() {
        montarTendencia(pontosComMovimentoEm = 1)                // helper: N meses com entradas/saídas ≠ 0
        rule.onAllNodesWithTag(TAG_PRECISA_MAIS_UM_MES).assertCountEquals(2)   // 6 meses e poupança
        rule.onAllNodesWithText("saídas").assertCountEquals(0)                 // a legenda some junto
    }

    @Test
    fun comDoisMesesOsGraficosVoltam() {
        montarTendencia(pontosComMovimentoEm = 2)
        rule.onAllNodesWithTag(TAG_PRECISA_MAIS_UM_MES).assertCountEquals(0)
        rule.onNodeWithText("saídas").assertIsDisplayed()
    }
```

Escreva `montarTendencia(n)` no arquivo: seis `PontoMes` terminando no mês corrente, os últimos `n` com `entradas = 1_000_00, saidas = 600_00, sobrou = 400_00`, os outros zerados, `reservaAcumulada = 0`, `taxaPoupanca = null`; `setContent { SaldoTheme { Column { SegmentoTendencia(pontos, YearMonth.now(), onMes = {}) } } }`.

- [ ] **Step 2: Run to verify it fails**

Run: `mise exec -- ./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.scholze.saldo.ui.totais.TotaisContentTest`
Expected: compilação falha — `TAG_PRECISA_MAIS_UM_MES`.

- [ ] **Step 3: Implementation**

No topo de `SegmentoTendencia.kt`:

```kotlin
/** O aviso que ocupa o lugar de um gráfico sem dois meses para comparar. */
const val TAG_PRECISA_MAIS_UM_MES = "totais:precisaMaisUmMes"

/** Meses com alguma movimentação; abaixo de dois, nenhuma tendência é uma tendência. */
private fun List<PontoMes>.mesesComMovimento(): Int = count { it.entradas != 0L || it.saidas != 0L }

@Composable
private fun PrecisaDeMaisUmMes(altura: Dp) {
    Box(
        Modifier.fillMaxWidth().height(altura).padding(horizontal = 16.dp).testTag(TAG_PRECISA_MAIS_UM_MES),
        contentAlignment = Alignment.Center,
    ) {
        Text("precisa de mais um mês", style = SaldoTheme.type.row, color = SaldoTheme.colors.secondaryLabel)
    }
}
```

No corpo: `val poucosMeses = pontos.mesesComMovimento() < 2`. No `InsetGroup` de "6 MESES", `if (poucosMeses) PrecisaDeMaisUmMes(120.dp) else { TrendChart(...); Row(legenda) }`. No de "POUPANÇA", a `InsetRow` de "reserva acumulada" fica; `ReservaLine` e `PoupancaBars` (com os seus rótulos) ficam dentro de `if (!poucosMeses) { … } else PrecisaDeMaisUmMes(96.dp)`. Imports: `androidx.compose.foundation.layout.Box`, `height`, `androidx.compose.ui.Alignment`, `androidx.compose.ui.platform.testTag`, `androidx.compose.ui.unit.Dp`.

- [ ] **Step 4: "sem tags neste período"** — em `SegmentoMes.kt`, o bloco `if (noTempo != null && noTempo.grupos.isNotEmpty()) { … }` ganha um `else if (noTempo != null)` que desenha `Text("sem tags neste período", Modifier.padding(start = 16.dp, top = 14.dp, bottom = 12.dp), style = SaldoTheme.type.caption, color = colors.secondaryLabel)`. Sem teste próprio: é uma linha de texto num ramo que o `TotaisContentTest` de "para onde foi" já cobre com grupos vazios — acrescente lá `rule.onNodeWithText("sem tags neste período").assertIsDisplayed()` se o caso existir; senão, deixe.

- [ ] **Step 5: Run tests, lint, commit**

Run: o mesmo comando do Step 2. Expected: PASS.

```bash
git add app/src/main/kotlin/com/scholze/saldo/ui/totais/SegmentoTendencia.kt app/src/main/kotlin/com/scholze/saldo/ui/totais/SegmentoMes.kt app/src/androidTest/kotlin/com/scholze/saldo/ui/totais/TotaisContentTest.kt
git commit -m "feat: sem dois meses, os gráficos de tendência pedem mais um mês"
```

---

### Task 15: O que sobrevive à rotação — privacidade, diálogos, totais

**Files:**
- Modify: `app/src/main/kotlin/com/scholze/saldo/ui/privacy/Privacy.kt:24-39`
- Modify: `app/src/main/kotlin/com/scholze/saldo/ui/totais/TotaisViewModel.kt` (`SavedStateHandle`)
- Modify: `app/src/main/kotlin/com/scholze/saldo/ui/ledger/LedgerScreen.kt:112`, `ui/board/BoardScreen.kt:116`, `ui/mais/MaisScreen.kt:95` (`rememberSaveable`)
- Test: `app/src/androidTest/kotlin/com/scholze/saldo/ui/privacy/PrivacyTest.kt` (create)
- Test: `app/src/test/kotlin/com/scholze/saldo/ui/totais/TotaisViewModelTest.kt` (create)

**Interfaces:**
- Produces: `PrivacyState.Saver`; `rememberPrivacyState` via `rememberSaveable`; `TotaisViewModel(repo, savedState = SavedStateHandle())` com `mesAtualAgora`.
- Consumes: `toLongChave`/`toYearMonth` (Task 7).

- [ ] **Step 1: Privacy test**

`app/src/androidTest/kotlin/com/scholze/saldo/ui/privacy/PrivacyTest.kt`:

```kotlin
package com.scholze.saldo.ui.privacy

import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PrivacyTest {

    @get:Rule val rule = createComposeRule()

    /** Escondeu, girou: continua escondido. Antes, a rotação revelava o que se acabava de esconder. */
    @Test
    fun ocultoSobreviveARestauracaoDeEstado() {
        val restaurador = StateRestorationTester(rule)
        restaurador.setContent {
            val privacidade = rememberPrivacyState(ocultoInicial = false)
            Text(if (privacidade.oculto) "oculto" else "visível", modifier = Modifier.clickable { privacidade.alternar() })
        }
        rule.onNodeWithText("visível").performClick()
        rule.onNodeWithText("oculto").assertIsDisplayed()
        restaurador.emulateSavedInstanceStateRestore()
        rule.onNodeWithText("oculto").assertIsDisplayed()
    }
}
```

Imports extras: `androidx.compose.foundation.clickable`, `androidx.compose.ui.Modifier`.

- [ ] **Step 2: Run to verify it fails**

Run: `mise exec -- ./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.scholze.saldo.ui.privacy.PrivacyTest`
Expected: FAIL — depois da restauração o texto é "visível".

- [ ] **Step 3: The Saver**

Em `Privacy.kt`:

```kotlin
/** Visibilidade dos valores. Escolha de sessão: sobrevive à rotação, não a uma abertura nova. */
class PrivacyState(ocultoInicial: Boolean) {
    var oculto by mutableStateOf(ocultoInicial)
        private set

    fun alternar() { oculto = !oculto }

    companion object {
        val Saver: Saver<PrivacyState, Boolean> = Saver(save = { it.oculto }, restore = { PrivacyState(it) })
    }
}

val LocalPrivacy = staticCompositionLocalOf { PrivacyState(ocultoInicial = false) }

/**
 * Sobrevive à rotação e à morte do processo com a activity viva: esconder é uma decisão, e
 * girar o aparelho não pode desfazê-la. [ocultoInicial] só é lido na primeira composição.
 */
@Composable
fun rememberPrivacyState(ocultoInicial: Boolean): PrivacyState =
    rememberSaveable(saver = PrivacyState.Saver) { PrivacyState(ocultoInicial) }
```

Imports: `androidx.compose.runtime.saveable.Saver`, `androidx.compose.runtime.saveable.rememberSaveable`; remova `androidx.compose.runtime.remember` se ficar sem uso.

- [ ] **Step 4: Run the privacy test** — Expected: PASS.

- [ ] **Step 5: Totais saved state**

`app/src/test/kotlin/com/scholze/saldo/ui/totais/TotaisViewModelTest.kt`:

```kotlin
package com.scholze.saldo.ui.totais

import androidx.lifecycle.SavedStateHandle
import com.scholze.saldo.RepositorioFixo
import com.scholze.saldo.domain.CartaoConfig
import com.scholze.saldo.domain.LedgerInput
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TotaisViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val input = LedgerInput(
        saldoInicialCentavos = 0, saldoInicialData = LocalDate.parse("2026-01-01"),
        movimentacoes = emptyList(), recorrencias = emptyList(), mesesMaterializados = emptySet(),
        cartao = CartaoConfig(), hoje = LocalDate.parse("2026-09-07"),
    )

    @Before fun setMain() = Dispatchers.setMain(dispatcher)
    @After fun resetMainDispatcher() = Dispatchers.resetMain()

    @Test
    fun oMesVistoSobreviveNoSavedState() = runTest(dispatcher) {
        val saved = SavedStateHandle()
        val vm = TotaisViewModel(RepositorioFixo(input), saved)
        vm.irPara(YearMonth.of(2026, 12))
        advanceUntilIdle()
        val outro = TotaisViewModel(RepositorioFixo(input), saved)
        assertEquals(YearMonth.of(2026, 12), outro.mesAtualAgora)
    }
}
```

Em `TotaisViewModel.kt`: o construtor ganha `private val savedState: SavedStateHandle = SavedStateHandle()`; `mesAtual` inicia de `savedState.get<Long>(KEY_MES)?.toYearMonth() ?: YearMonth.now()`; no `init` (crie um se não houver) `viewModelScope.launch { mesAtual.collect { savedState[KEY_MES] = it.toLongChave() } }`; `val mesAtualAgora: YearMonth get() = mesAtual.value`; `KEY_MES = "totais.mes"`; factory com `createSavedStateHandle()`. Se o segmento selecionado também vive no ViewModel, guarde-o em `KEY_SEGMENTO` pelo `name`; se vive na tela como `remember`, troque por `rememberSaveable`.

Run: `mise exec -- ./gradlew :app:testDebugUnitTest --tests "com.scholze.saldo.ui.totais.TotaisViewModelTest"` — PASS.

- [ ] **Step 6: The remaining dialog flags**

`rememberSaveable` no lugar de `remember` em: `faturaAberta` (`LedgerScreen.kt:112`, `BoardScreen.kt:116`) — `Fatura` não é `Saveable`: guarde `faturaAbertaVencimento: Long?` (epoch day) e resolva a fatura pelo `mes.faturas`/`DiaRow` na hora de desenhar, ou, mais simples, mantenha `remember` e anote no código que o diálogo da fatura é só leitura e fechar na rotação é aceitável. **Escolha a segunda** (uma linha de comentário) — o spec pede os diálogos de *edição* abertos, e a fatura não edita nada. `editandoSaldo` (`MaisScreen.kt:95`) vira `rememberSaveable`.

- [ ] **Step 7: Rotation test of the sheet's date picker** (append em `NewEntrySheetTest`)

```kotlin
    @Test
    fun oSeletorDeDataSobreviveARestauracaoDeEstado() {
        val vm = vm()
        vm.iniciarNova(LocalDate.now())
        val restaurador = androidx.compose.ui.test.junit4.StateRestorationTester(rule)
        restaurador.setContent { SaldoTheme { NewEntrySheet(vm = vm, onFechar = {}) } }
        rule.onNodeWithText("data").performClick()
        rule.onNodeWithText("ok").assertIsDisplayed()
        restaurador.emulateSavedInstanceStateRestore()
        rule.onNodeWithText("ok").assertIsDisplayed()
    }
```

- [ ] **Step 8: Full suites, lint, commit**

Run: `mise run test`, `mise run test-device`, `mise exec -- ./gradlew lintDebug`.

```bash
git add app/src/main/kotlin/com/scholze/saldo/ui/privacy/Privacy.kt app/src/main/kotlin/com/scholze/saldo/ui/totais/TotaisViewModel.kt app/src/main/kotlin/com/scholze/saldo/ui/mais/MaisScreen.kt app/src/main/kotlin/com/scholze/saldo/ui/ledger/LedgerScreen.kt app/src/main/kotlin/com/scholze/saldo/ui/board/BoardScreen.kt app/src/androidTest/kotlin/com/scholze/saldo/ui/privacy app/src/androidTest/kotlin/com/scholze/saldo/ui/entry app/src/test/kotlin/com/scholze/saldo/ui/totais
git commit -m "feat: oculto continua oculto, e os diálogos sobrevivem à rotação"
```

---

### Task 16: README, capturas e a nota de execução

**Files:**
- Modify: `README.md`
- Create: `docs/superpowers/screenshots/2026-09-07-uso-diario/README.md` + capturas
- Modify: `app/build.gradle.kts` (`versionCode = 6`, `versionName = "0.5.0"`)

- [ ] **Step 1: README** — no parágrafo do board, depois de "toque de novo e fecham":

> O ícone de lista na barra abre o mesmo mês em linhas, com os chips `todas/diários/fixas`, e a lupa busca no histórico inteiro — descrição, tag ou valor (`340` acha R$ 340,00). O `+` lança no dia que estiver aberto na grade. Voltar fecha a lista, depois vai para `saldos`, e só então sai do app.

No parágrafo de recorrências (ou logo depois de "Lançar cobra só o valor"):

> Na edição, "repetir" continua um controle: uma avulsa vira mensal, e uma mensal para a partir daquele mês. Em `totais → a caminho → recorrências`, cada uma tem um interruptor para pausar e retomar — os meses pausados ficam vazios. Uma tag apagada tem "desfazer" e muda de cor em "editar". A tecla `00` do teclado anexa dois zeros.

- [ ] **Step 2: Screenshots** no emulador (tema claro), nomeadas `01-board-lista.png`, `02-busca.png`, `03-sheet-repetir-edicao.png`, `04-recorrencias-pausada.png`, `05-tags-cor.png`, `06-board-vazio.png`, com um `README.md` de uma linha por captura dizendo o que olhar. Capture com `mise exec -- adb exec-out screencap -p > arquivo.png` (pelo Bash; no PowerShell o `>` corrompe binário).

- [ ] **Step 3: Version bump** — `versionCode = 6`, `versionName = "0.5.0"`.

- [ ] **Step 4: Final green**

Run: `mise run test`, `mise run test-device`, `mise exec -- ./gradlew lintDebug`, `mise run build`. Anote as contagens (JVM e instrumentados) na nota de execução.

- [ ] **Step 5: Commit**

```bash
git add README.md docs/superpowers/screenshots/2026-09-07-uso-diario app/build.gradle.kts
git commit -m "chore: versão 0.5.0 — uso diário: voltar, lista, busca, repetir, desfazer"
```

Depois: `superpowers:finishing-a-development-branch` — merge em `main`, tag `v0.5.0`, release no GitHub com o APK (como as anteriores), branch apagada.
