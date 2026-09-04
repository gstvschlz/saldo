# saldo — captura de notificações Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Quando um app que o usuário marcou emite uma notificação com valor em reais, o saldo emite a sua própria notificação com um botão que lança a movimentação sem abrir o app.

**Architecture:** Duas camadas puras embaixo e uma casca fina de Android em cima, que é a mesma divisão que o `LembretesEngine` já provou. `DetectorValor` extrai o valor de um texto; `SugestaoEngine` decide o que fazer com uma detecção dado o que já existe. O `NotificationListenerService` não decide nada — lê, pergunta e obedece. A persistência é uma tabela só, sem texto nenhum, varrida a cada 24 h.

**Tech Stack:** Kotlin 2.2, Compose, Room (schema v2 → **v3**), DataStore, JUnit4. **Nenhuma dependência nova.**

## Global Constraints

- **Spec:** `docs/superpowers/specs/2026-09-04-saldo-notificacoes-design.md`. Onde plano e spec discordarem, o plano ganha.
- **Baseline:** branch `notificacoes-1`, criada de `main` em `5e3e305`.
- **Nunca persistir texto de notificação.** Título e corpo existem em memória durante a avaliação e não chegam ao disco. A tabela guarda pacote, centavos, timestamp, chave e um booleano.
- **Desligado por padrão**, como os quatro lembretes.
- **Toda sugestão é saída, natureza `DIARIO`.** Sem heurística de sinal e sem heurística de conta × cartão — foi decisão explícita do usuário, com o risco de contagem dobrada aceito.
- **O primeiro valor do texto é o valor.**
- **Dinheiro em `Long` de centavos**, sem `Float`/`Double` em lugar nenhum.
- **Cada task termina verde:** `mise run test` e `mise exec -- ./gradlew lintDebug` com 0 erros; as tasks com Android de verdade acrescentam `mise run test-device`.
- Depois do bump de schema, rode `connectedDebugAndroidTest` **duas vezes** localmente (gotcha conhecido deste projeto).
- O repositório tem `core.autocrlf=true`. Não reescreva arquivos por causa de fim de linha.

## File structure

| File | Responsibility |
|---|---|
| `domain/DetectorValor.kt` (create) | `primeiroValorEmCentavos(texto): Long?` — o regex de R$ mora aqui e em nenhum outro lugar |
| `domain/Captura.kt` (create) | `Deteccao`, `Sugestao`, `CapturaConfig` — os tipos que as duas camadas trocam |
| `domain/SugestaoEngine.kt` (create) | `avaliar(...)` — marcado? janela? já lançado? |
| `data/db/Entities.kt` (modify) | `DeteccaoEntity` + conversões |
| `data/db/Daos.kt` (modify) | `DeteccaoDao` |
| `data/db/SaldoDatabase.kt` (modify) | v3 + `AutoMigration(2, 3)` |
| `data/SettingsStore.kt` (modify) | `CapturaConfig` dentro de `Settings` |
| `captura/NotificacaoSugestao.kt` (create) | canal "sugestões" e o construtor da notificação com os dois botões |
| `captura/AcoesSugestao.kt` (create) | o `BroadcastReceiver` de "lançar" e "ignorar" |
| `captura/EscutaNotificacoes.kt` (create) | o `NotificationListenerService`, casca fina |
| `ui/nav/Destino.kt` (modify) | `NovaMovimentacao` passa a carregar valor e descrição |
| `ui/entry/EntryViewModel.kt` (modify) | `iniciarNova` aceita valor e descrição |
| `ui/SaldoApp.kt` (modify) | repassa os dois ao abrir a sheet |
| `ui/mais/CapturaScreen.kt` (create) | liga/desliga, o botão do acesso do sistema, a lista de apps vistos |
| `ui/mais/MaisScreen.kt` (modify) | a linha que leva à tela nova |
| `AndroidManifest.xml` (modify) | o service e o receiver |

---

### Task 1: `DetectorValor` — o regex de R$

**Files:**
- Create: `app/src/main/kotlin/com/scholze/saldo/domain/DetectorValor.kt`
- Test: `app/src/test/kotlin/com/scholze/saldo/domain/DetectorValorTest.kt` (create)

**Interfaces:**
- Produces: `DetectorValor.primeiroValorEmCentavos(texto: String): Long?`
- Consumes: nada.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.scholze.saldo.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DetectorValorTest {

    private fun v(texto: String) = DetectorValor.primeiroValorEmCentavos(texto)

    @Test fun `valor simples com centavos`() = assertEquals(3_290L, v("Compra aprovada de R$ 32,90"))
    @Test fun `sem espaco depois do cifrao`() = assertEquals(3_290L, v("R$32,90 no crédito"))
    @Test fun `minusculo tambem vale`() = assertEquals(500L, v("pagamento de r$ 5,00"))
    @Test fun `milhar com ponto`() = assertEquals(123_456L, v("R$ 1.234,56 debitado"))
    @Test fun `varios milhares`() = assertEquals(123_456_789L, v("R$ 1.234.567,89"))
    @Test fun `sem centavos vale reais inteiros`() = assertEquals(123_400L, v("R$ 1.234 transferidos"))

    /** O primeiro é a transação; o segundo costuma ser o saldo da conta. */
    @Test fun `com dois valores fica o primeiro`() =
        assertEquals(3_290L, v("Compra de R$ 32,90 · saldo R$ 1.204,00"))

    @Test fun `dolar nao conta`() = assertNull(v("Assinatura de US$ 5,99"))
    @Test fun `cifrao sem numero nao conta`() = assertNull(v("seu limite em R$ mudou"))
    @Test fun `texto sem valor`() = assertNull(v("Você tem uma nova mensagem"))
    @Test fun `zero nao vira sugestao`() = assertNull(v("tarifa de R$ 0,00"))
    @Test fun `numero absurdo nao estoura`() = assertNull(v("R$ 99.999.999.999.999.999,99"))
    @Test fun `texto vazio`() = assertNull(v(""))
}
```

- [ ] **Step 2: Run it and watch it fail**

Run: `mise exec -- ./gradlew testDebugUnitTest --tests '*DetectorValorTest*'`
Expected: FAIL — `Unresolved reference: DetectorValor`.

- [ ] **Step 3: Implement**

```kotlin
package com.scholze.saldo.domain

/**
 * O valor em reais dentro de um texto de notificação.
 *
 * **O primeiro valor é o valor.** Um aviso de banco costuma trazer dois — "compra aprovada
 * de R$ 32,90 · saldo R$ 1.204,00" — e o primeiro é a transação. Pegar o maior acertaria a
 * compra grande e erraria todas as pequenas.
 *
 * Dólar é ignorado de propósito: sem rede não há cotação, e uma cotação velha mentiria no
 * saldo. `US$` nem casa com o padrão, que exige o `R$`.
 */
object DetectorValor {

    /**
     * `R$`, espaço opcional, milhares separados por ponto (ou nenhum separador) e centavos
     * opcionais. O grupo de milhar é `\d{1,3}(\.\d{3})*` e não `[\d.]+` para "1.2345" não
     * passar por milhar.
     */
    private val REGEX = Regex(
        """R\$\s*(\d{1,3}(?:\.\d{3})*|\d+)(?:,(\d{2}))?""",
        RegexOption.IGNORE_CASE,
    )

    /** `null` quando não há valor, quando o valor é zero, ou quando não cabe num `Long`. */
    fun primeiroValorEmCentavos(texto: String): Long? {
        val m = REGEX.find(texto) ?: return null
        val reais = m.groupValues[1].replace(".", "").toLongOrNull() ?: return null
        val centavos = m.groupValues[2].ifEmpty { "0" }.toLong()
        // 92 quatrilhões de centavos é o teto do Long; qualquer coisa perto disso é lixo de
        // parse, não dinheiro. Multiplicar sem checar daria um número negativo.
        if (reais > Long.MAX_VALUE / 100 - 1) return null
        val total = reais * 100 + centavos
        return total.takeIf { it > 0 }
    }
}
```

- [ ] **Step 4: Green**

Run: `mise exec -- ./gradlew testDebugUnitTest --tests '*DetectorValorTest*'` → PASS (13 testes).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/scholze/saldo/domain/DetectorValor.kt app/src/test/kotlin/com/scholze/saldo/domain/DetectorValorTest.kt
git commit -m "feat: detector do valor em reais numa notificação"
```

---

### Task 2: `SugestaoEngine` — a decisão

**Files:**
- Create: `app/src/main/kotlin/com/scholze/saldo/domain/Captura.kt`
- Create: `app/src/main/kotlin/com/scholze/saldo/domain/SugestaoEngine.kt`
- Test: `app/src/test/kotlin/com/scholze/saldo/domain/SugestaoEngineTest.kt` (create)

**Interfaces:**
- Produces:
  - `data class Deteccao(val id: Long = 0, val pacote: String, val chave: String, val centavos: Long, val emMillis: Long, val resolvida: Boolean = false)`
  - `data class CapturaConfig(val ligada: Boolean = false, val marcados: Set<String> = emptySet(), val vistos: Set<String> = emptySet())`
  - `sealed interface Sugestao { data class Nova(...); data class JaLancado(...); data class Repetida(val existente: Deteccao); data object Ignorar }`
  - `SugestaoEngine.avaliar(candidata, config, recentes, valoresDeHojeCentavos): Sugestao`
  - `SugestaoEngine.JANELA_MILLIS`
- Consumes: nada.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.scholze.saldo.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SugestaoEngineTest {

    private val agora = 1_788_000_000_000L
    private val config = CapturaConfig(ligada = true, marcados = setOf("com.nubank"))

    private fun candidata(
        pacote: String = "com.nubank",
        centavos: Long = 3_290,
        chave: String = "k1",
        em: Long = agora,
    ) = Deteccao(pacote = pacote, chave = chave, centavos = centavos, emMillis = em)

    private fun avaliar(
        c: Deteccao = candidata(),
        cfg: CapturaConfig = config,
        recentes: List<Deteccao> = emptyList(),
        hoje: List<Long> = emptyList(),
    ) = SugestaoEngine.avaliar(c, cfg, recentes, hoje)

    @Test fun `app marcado e valor novo vira sugestao nova`() {
        assertTrue(avaliar() is Sugestao.Nova)
    }

    @Test fun `app nao marcado e ignorado`() {
        assertEquals(Sugestao.Ignorar, avaliar(c = candidata(pacote = "com.whatsapp")))
    }

    @Test fun `captura desligada ignora ate o app marcado`() {
        assertEquals(Sugestao.Ignorar, avaliar(cfg = config.copy(ligada = false)))
    }

    @Test fun `valor zero ou negativo e ignorado`() {
        assertEquals(Sugestao.Ignorar, avaliar(c = candidata(centavos = 0)))
    }

    @Test fun `mesmo app e valor dentro da janela e repetida`() {
        val antes = candidata(chave = "k0", em = agora - 9 * 60_000).copy(id = 7)
        val r = avaliar(recentes = listOf(antes))
        assertEquals(Sugestao.Repetida(antes), r)
    }

    @Test fun `fora da janela volta a ser nova`() {
        val antes = candidata(chave = "k0", em = agora - 11 * 60_000).copy(id = 7)
        assertTrue(avaliar(recentes = listOf(antes)) is Sugestao.Nova)
    }

    @Test fun `no limite exato da janela ainda e repetida`() {
        val antes = candidata(chave = "k0", em = agora - SugestaoEngine.JANELA_MILLIS).copy(id = 7)
        assertEquals(Sugestao.Repetida(antes), avaliar(recentes = listOf(antes)))
    }

    @Test fun `mesmo valor em app diferente nao e repetida`() {
        val outro = candidata(pacote = "com.itau", chave = "k0", em = agora - 60_000).copy(id = 7)
        assertTrue(avaliar(recentes = listOf(outro)) is Sugestao.Nova)
    }

    @Test fun `deteccao ja resolvida na janela nao volta a sugerir`() {
        val antes = candidata(chave = "k0", em = agora - 60_000).copy(id = 7, resolvida = true)
        assertEquals(Sugestao.Ignorar, avaliar(recentes = listOf(antes)))
    }

    @Test fun `valor ja lancado hoje avisa em vez de sugerir do zero`() {
        val r = avaliar(hoje = listOf(3_290L))
        assertTrue(r is Sugestao.JaLancado)
    }

    @Test fun `valor parecido mas diferente nao conta como lancado`() {
        assertTrue(avaliar(hoje = listOf(3_291L)) is Sugestao.Nova)
    }

    /** A janela vence antes do ledger: repetida é repetida, mesmo com o valor já lançado. */
    @Test fun `repetida ganha de ja lancado`() {
        val antes = candidata(chave = "k0", em = agora - 60_000).copy(id = 7)
        assertEquals(Sugestao.Repetida(antes), avaliar(recentes = listOf(antes), hoje = listOf(3_290L)))
    }
}
```

- [ ] **Step 2: Run it and watch it fail**

Run: `mise exec -- ./gradlew testDebugUnitTest --tests '*SugestaoEngineTest*'` → FAIL.

- [ ] **Step 3: Implement `Captura.kt`**

```kotlin
package com.scholze.saldo.domain

/**
 * Uma notificação com valor que o saldo viu.
 *
 * O que NÃO está aqui é o ponto: nem título, nem corpo, nem nome do app. Texto de
 * notificação existe em memória durante a avaliação e nunca chega ao disco — o que se
 * guarda é o suficiente para deduplicar e para lembrar o que já foi resolvido.
 *
 * [chave] é a identidade da notificação no Android (`StatusBarNotification.key`), usada para
 * cancelar a sugestão certa depois.
 */
data class Deteccao(
    val id: Long = 0,
    val pacote: String,
    val chave: String,
    val centavos: Long,
    val emMillis: Long,
    val resolvida: Boolean = false,
)

/**
 * [marcados] são os apps de que o usuário quer que o saldo leia; [vistos] são os que já
 * emitiram alguma notificação com valor e por isso aparecem na tela esperando a marcação.
 *
 * A lista é descoberta e não enumerada porque listar os apps instalados no Android 11+ exige
 * `QUERY_ALL_PACKAGES`, permissão sensível da Play que contradiz a postura do app.
 */
data class CapturaConfig(
    val ligada: Boolean = false,
    val marcados: Set<String> = emptySet(),
    val vistos: Set<String> = emptySet(),
)

sealed interface Sugestao {
    /** Notificar do zero. */
    data class Nova(val deteccao: Deteccao) : Sugestao

    /** Notificar, mas avisando que um valor igual já foi lançado hoje. */
    data class JaLancado(val deteccao: Deteccao) : Sugestao

    /** A mesma compra de novo: atualiza a sugestão que já está na barra, não cria outra. */
    data class Repetida(val existente: Deteccao) : Sugestao

    /** Não é da nossa conta. */
    data object Ignorar : Sugestao
}
```

- [ ] **Step 4: Implement `SugestaoEngine.kt`**

```kotlin
package com.scholze.saldo.domain

/**
 * O que fazer com uma notificação que trouxe um valor.
 *
 * Duas defesas contra lançar a mesma compra duas vezes, e a ordem entre elas importa. A
 * **janela** pega o caso barulhento — o banco repostando o mesmo aviso, a loja avisando
 * junto — e vence primeiro, porque é a mais específica: se já existe uma sugestão viva para
 * esta compra, o certo é atualizá-la, não perguntar de novo. O **ledger** pega o caso lento:
 * o valor já foi lançado hoje, à mão ou por uma sugestão anterior, e aí a pergunta muda de
 * "lançar?" para "lançar mesmo assim?".
 */
object SugestaoEngine {

    /** Dez minutos. Duas notificações do mesmo app com o mesmo valor aqui dentro são a mesma compra. */
    const val JANELA_MILLIS = 10 * 60 * 1000L

    fun avaliar(
        candidata: Deteccao,
        config: CapturaConfig,
        recentes: List<Deteccao>,
        valoresDeHojeCentavos: List<Long>,
    ): Sugestao {
        if (!config.ligada) return Sugestao.Ignorar
        if (candidata.pacote !in config.marcados) return Sugestao.Ignorar
        if (candidata.centavos <= 0) return Sugestao.Ignorar

        val naJanela = recentes.firstOrNull {
            it.pacote == candidata.pacote &&
                it.centavos == candidata.centavos &&
                candidata.emMillis - it.emMillis in 0..JANELA_MILLIS
        }
        if (naJanela != null) {
            // Já resolvida quer dizer que o usuário decidiu: não ressuscita.
            return if (naJanela.resolvida) Sugestao.Ignorar else Sugestao.Repetida(naJanela)
        }

        return if (valoresDeHojeCentavos.any { it == candidata.centavos }) {
            Sugestao.JaLancado(candidata)
        } else {
            Sugestao.Nova(candidata)
        }
    }
}
```

- [ ] **Step 5: Green + commit**

```bash
mise exec -- ./gradlew testDebugUnitTest --tests '*SugestaoEngineTest*'
git add app/src/main/kotlin/com/scholze/saldo/domain/Captura.kt app/src/main/kotlin/com/scholze/saldo/domain/SugestaoEngine.kt app/src/test/kotlin/com/scholze/saldo/domain/SugestaoEngineTest.kt
git commit -m "feat: motor da sugestão — janela de dez minutos e conferência no ledger"
```

---

### Task 3: Schema v3 — a tabela `deteccoes`

**Files:**
- Modify: `app/src/main/kotlin/com/scholze/saldo/data/db/Entities.kt` (acrescentar ao fim)
- Modify: `app/src/main/kotlin/com/scholze/saldo/data/db/Daos.kt` (acrescentar ao fim)
- Modify: `app/src/main/kotlin/com/scholze/saldo/data/db/SaldoDatabase.kt`
- Modify: `app/src/androidTest/kotlin/com/scholze/saldo/data/db/MigrationTest.kt`

**Interfaces:**
- Produces: `DeteccaoEntity`, `DeteccaoEntity.toDomain()`, `Deteccao.toEntity()`, `DeteccaoDao`, `SaldoDatabase.deteccaoDao()`.

- [ ] **Step 1: Entity**

Em `Entities.kt`, antes das funções de extensão do fim:

```kotlin
/**
 * `emMillis` indexado: toda leitura é uma janela de tempo — as recentes para deduplicar e as
 * velhas para apagar.
 */
@Entity(tableName = "deteccoes", indices = [Index("emMillis")])
data class DeteccaoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val pacote: String,
    val chave: String,
    val centavos: Long,
    val emMillis: Long,
    val resolvida: Boolean = false,
)
```

E as conversões, junto das outras:

```kotlin
fun DeteccaoEntity.toDomain() = Deteccao(id, pacote, chave, centavos, emMillis, resolvida)
fun Deteccao.toEntity() = DeteccaoEntity(id, pacote, chave, centavos, emMillis, resolvida)
```

Acrescente `import com.scholze.saldo.domain.Deteccao` ao topo.

- [ ] **Step 2: DAO**

Ao fim de `Daos.kt`:

```kotlin
@Dao
interface DeteccaoDao {
    @Insert suspend fun insert(d: DeteccaoEntity): Long

    @Query("SELECT * FROM deteccoes WHERE emMillis >= :desde ORDER BY emMillis")
    suspend fun desde(desde: Long): List<DeteccaoEntity>

    @Query("SELECT * FROM deteccoes WHERE id = :id")
    suspend fun porId(id: Long): DeteccaoEntity?

    @Query("UPDATE deteccoes SET resolvida = 1 WHERE id = :id")
    suspend fun resolver(id: Long)

    /** A varredura das 24 h. Chamada a cada detecção: é um DELETE indexado, sai barato. */
    @Query("DELETE FROM deteccoes WHERE emMillis < :antesDe")
    suspend fun limpar(antesDe: Long)
}
```

- [ ] **Step 3: Bump da versão**

Em `SaldoDatabase.kt`: acrescente `DeteccaoEntity::class` à lista de entidades, troque `version = 2` por `version = 3`, acrescente `AutoMigration(from = 2, to = 3)` com o comentário `// 2 -> 3: tabela deteccoes (captura de notificações). Tabela nova, migração gerada basta.` e declare `abstract fun deteccaoDao(): DeteccaoDao`.

- [ ] **Step 4: MigrationTest**

Acrescente o caso 2→3 seguindo exatamente a forma do 1→2 que já existe no arquivo: abre o banco na v2, fecha, roda a migração para a v3 e valida.

- [ ] **Step 5: Build, testes e commit**

```bash
mise run build && mise exec -- ./gradlew connectedDebugAndroidTest --tests '*MigrationTest*'
git add app/src/main/kotlin/com/scholze/saldo/data/db app/schemas app/src/androidTest/kotlin/com/scholze/saldo/data/db/MigrationTest.kt
git commit -m "feat: schema v3 — a tabela de detecções, sem uma linha de texto"
```

---

### Task 4: `CapturaConfig` no `SettingsStore`

**Files:**
- Modify: `app/src/main/kotlin/com/scholze/saldo/data/SettingsStore.kt`
- Test: `app/src/androidTest/kotlin/com/scholze/saldo/data/SettingsStoreTest.kt` (acrescentar casos)

**Interfaces:**
- Produces: `Settings.captura: CapturaConfig`; `SettingsStore.definirCapturaLigada(Boolean)`, `definirAppMarcado(pacote, marcado)`, `registrarAppVisto(pacote)`, `lerCaptura(): CapturaConfig`.

- [ ] **Step 1: Chaves e leitura**

Em `Keys`: `val capturaLigada = booleanPreferencesKey("captura_ligada")`, `val capturaMarcados = stringSetPreferencesKey("captura_marcados")`, `val capturaVistos = stringSetPreferencesKey("captura_vistos")`. Importe `stringSetPreferencesKey`.

Em `Settings`, acrescente `val captura: CapturaConfig = CapturaConfig()`, e no `map` do fluxo `captura = p.captura()`, com:

```kotlin
private fun Preferences.captura(): CapturaConfig = CapturaConfig(
    ligada = this[Keys.capturaLigada] ?: false,
    marcados = this[Keys.capturaMarcados].orEmpty(),
    vistos = this[Keys.capturaVistos].orEmpty(),
)
```

- [ ] **Step 2: Escritas**

```kotlin
    suspend fun definirCapturaLigada(v: Boolean) {
        dataStore.edit { it[Keys.capturaLigada] = v }
    }

    /** Marcar um app também o tira da lista de vistos: ele saiu da fila e entrou na lista. */
    suspend fun definirAppMarcado(pacote: String, marcado: Boolean) {
        dataStore.edit { p ->
            val marcados = p[Keys.capturaMarcados].orEmpty()
            p[Keys.capturaMarcados] = if (marcado) marcados + pacote else marcados - pacote
        }
    }

    /**
     * Anota que [pacote] emitiu uma notificação com valor, para ele aparecer na tela
     * esperando a marcação. Só o nome do pacote — nada do que veio na notificação.
     * Não regrava se já está lá: uma escrita no DataStore por notificação seria absurdo.
     */
    suspend fun registrarAppVisto(pacote: String) {
        val atuais = dataStore.data.first()[Keys.capturaVistos].orEmpty()
        if (pacote in atuais) return
        dataStore.edit { it[Keys.capturaVistos] = atuais + pacote }
    }

    /** Sem engolir IOException, pela mesma razão de [lerLembretes]: o service precisa saber. */
    suspend fun lerCaptura(): CapturaConfig = dataStore.data.first().captura()
```

- [ ] **Step 3: Testes + commit**

Casos: o padrão é desligado e com os dois conjuntos vazios; ligar e ler de volta; marcar e desmarcar um app; `registrarAppVisto` sendo idempotente.

```bash
git commit -m "feat: preferências da captura — ligada, apps marcados e apps vistos"
```

---

### Task 5: A notificação de sugestão e os seus botões

**Files:**
- Create: `app/src/main/kotlin/com/scholze/saldo/captura/NotificacaoSugestao.kt`
- Create: `app/src/main/kotlin/com/scholze/saldo/captura/AcoesSugestao.kt`
- Modify: `app/src/main/kotlin/com/scholze/saldo/SaldoApplication.kt` (criar o canal novo)
- Modify: `app/src/main/AndroidManifest.xml` (o receiver)

**Interfaces:**
- Produces: `NotificacaoSugestao.CANAL`, `criarCanal(context)`, `idDe(deteccaoId): Int`, `construir(context, deteccao, rotulo, jaLancado): Notification`, `mostrar(...)`, `cancelar(context, deteccaoId)`; `AcoesSugestao` com `ACAO_LANCAR` e `ACAO_IGNORAR`.

**Notas de desenho:**
- Canal **separado** do canal `lembretes`, para o usuário poder silenciar um sem o outro.
- O id da notificação é derivado do id da detecção (`(deteccaoId % 100_000).toInt() + 2000`), fora da faixa dos lembretes (1001–1004).
- O rótulo do app viaja como extra: o receiver não pode consultar o `PackageManager` sobre um app que talvez ele nem enxergue.
- `PendingIntent.FLAG_IMMUTABLE` em tudo, como nos lembretes.

- [ ] **Step 1: `NotificacaoSugestao`**

Título: `"$rotulo · R$ 32,90"`. Texto: `"lançar como saída de hoje?"`, ou `"já lançado hoje · lançar mesmo assim?"` quando `jaLancado`. Versão pública (tela de bloqueio) só com `"sugestão de lançamento"` — o valor **não** aparece na tela bloqueada, que é mais conservador que os lembretes e é o certo para um dado que chegou de outro app.

Duas `addAction`: "lançar" e "ignorar", ambas `PendingIntent.getBroadcast` para `AcoesSugestao` com `deteccaoId`, `centavos` e `rotulo` nos extras, e request code derivado do id para não colidirem. `setContentIntent` abre `MainActivity.intent(context, Destino.NovaMovimentacao(saida = true, centavos = ..., descricao = rotulo))`. `setAutoCancel(true)`.

- [ ] **Step 2: `AcoesSugestao`**

```kotlin
class AcoesSugestao : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(EXTRA_DETECCAO, -1L).takeIf { it >= 0 } ?: return
        val container = (context.applicationContext as SaldoApplication).container
        val pending = goAsync()
        container.scope.launch {
            try {
                when (intent.action) {
                    ACAO_LANCAR -> { /* criar movimentação + resolver */ }
                    ACAO_IGNORAR -> container.database.deteccaoDao().resolver(id)
                }
                NotificacaoSugestao.cancelar(context, id)
            } catch (e: Exception) {
                Log.e("saldo", "ação da sugestão falhou", e)
            } finally {
                pending.finish()
            }
        }
    }
}
```

O ramo `ACAO_LANCAR` cria `Movimentacao(descricao = rotulo, valorCentavos = -centavos, data = LocalDate.now(), natureza = Natureza.DIARIO)` via `container.repository.criar(mov, RepetirOpcao.Nao)`, resolve a detecção e chama `container.widgetRefresher` como qualquer outra escrita faz.

No manifest, `<receiver android:name=".captura.AcoesSugestao" android:exported="false" />`.

- [ ] **Step 3: Canal**

Em `SaldoApplication.onCreate`, ao lado de `Notificacoes.criarCanal(this)`, chame `NotificacaoSugestao.criarCanal(this)`.

- [ ] **Step 4: Commit**

```bash
git commit -m "feat: a notificação de sugestão, com lançar e ignorar na barra"
```

---

### Task 6: `Destino` com valor e descrição

**Files:**
- Modify: `app/src/main/kotlin/com/scholze/saldo/ui/nav/Destino.kt`
- Modify: `app/src/main/kotlin/com/scholze/saldo/ui/entry/EntryViewModel.kt`
- Modify: `app/src/main/kotlin/com/scholze/saldo/ui/SaldoApp.kt`
- Test: `app/src/test/kotlin/com/scholze/saldo/ui/nav/DestinoTest.kt` (acrescentar ida e volta)

**Interfaces:**
- Produces: `Destino.NovaMovimentacao(saida: Boolean?, centavos: Long? = null, descricao: String? = null)`; `EntryViewModel.iniciarNova(hoje, centavos: Long = 0, descricao: String = "")`.

**Nota:** `paraPares()` já aceita `Any` e `aplicarEm` grava `Int` como int e o resto como string. `centavos` viaja como string (é `Long`) e `descricao` como string; `de()` volta com `toLongOrNull()`, então um extra corrompido vira `null` em vez de crash. O teste de ida e volta que já existe cobre o par novo.

- [ ] **Steps:** estender o `data class`, os pares, o `de()`/`deIntent()`, o `iniciarNova` e a chamada em `SaldoApp`; acrescentar dois casos ao `DestinoTest` (ida e volta com valor e descrição; extra corrompido virando `null`); rodar `mise run test`; commit.

```bash
git commit -m "feat: deep link que abre a sheet já com valor e descrição"
```

---

### Task 7: `EscutaNotificacoes` — o service

**Files:**
- Create: `app/src/main/kotlin/com/scholze/saldo/captura/EscutaNotificacoes.kt`
- Modify: `app/src/main/AndroidManifest.xml`

**Interfaces:**
- Consumes: `DetectorValor`, `SugestaoEngine`, `DeteccaoDao`, `SettingsStore`, `NotificacaoSugestao`.

**O corpo de `onNotificationPosted`, na ordem:**

1. Ignora a própria notificação do saldo (`sbn.packageName == packageName`) — senão o app se lê e entra em laço.
2. Ignora grupo/sumário (`sbn.notification.flags and FLAG_GROUP_SUMMARY != 0`) — é o resumo, não a compra.
3. Lê `config = settings.lerCaptura()`; se `!config.ligada`, sai.
4. Monta o texto de `extras`: `EXTRA_TITLE`, `EXTRA_TEXT`, `EXTRA_BIG_TEXT`, juntos por `" · "`. **Este texto é uma variável local e não sai daqui.**
5. `DetectorValor.primeiroValorEmCentavos(texto)`; sem valor, sai.
6. Se o pacote não está em `marcados`: `settings.registrarAppVisto(sbn.packageName)` e sai. É assim que a lista é descoberta.
7. `dao.limpar(agora - 24h)`, depois `recentes = dao.desde(agora - JANELA_MILLIS)`.
8. `valoresDeHoje` = movimentações de hoje do `repository.ledger.first()`, em módulo.
9. `SugestaoEngine.avaliar(...)` e obedece: `Nova`/`JaLancado` inserem e notificam; `Repetida` só renotifica com o id existente; `Ignorar` não faz nada.

Tudo em `container.scope.launch` — `onNotificationPosted` roda na main thread do service e não pode tocar em Room.

No manifest:

```xml
<service
    android:name=".captura.EscutaNotificacoes"
    android:exported="false"
    android:label="@string/app_name"
    android:permission="android.permission.BIND_NOTIFICATION_LISTENER_SERVICE">
    <intent-filter>
        <action android:name="android.service.notification.NotificationListenerService" />
    </intent-filter>
</service>
```

- [ ] **Steps:** escrever o service, o manifest, `mise run build`, instalar, ligar o acesso à mão no emulador, disparar uma notificação de teste com `adb shell cmd notification post`, conferir a sugestão, commit.

```bash
git commit -m "feat: o listener que lê a notificação e sugere o lançamento"
```

---

### Task 8: `mais › notificações`

**Files:**
- Create: `app/src/main/kotlin/com/scholze/saldo/ui/mais/CapturaScreen.kt`
- Modify: `app/src/main/kotlin/com/scholze/saldo/ui/mais/MaisScreen.kt`
- Modify: `app/src/main/kotlin/com/scholze/saldo/ui/mais/MaisViewModel.kt`

**A tela, de cima para baixo:**

1. Um parágrafo curto que diz a verdade inteira: *"ligado o acesso, o Android entrega ao saldo o texto de toda notificação do aparelho. O saldo só lê os apps que você marcar aqui, e não guarda o texto de nenhuma."*
2. O estado real do acesso do sistema (`NotificationManagerCompat.getEnabledListenerPackages(context).contains(packageName)`) e um botão que abre `Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS`. O botão diz "abrir configurações do sistema", não "permitir": o app não pode conceder isso.
3. O interruptor mestre da captura, desabilitado enquanto o acesso do sistema não estiver ligado.
4. A lista de apps: os marcados em cima, os vistos-ainda-não-marcados embaixo, cada um com um `Switch`. Vazia, o texto é *"nenhum app ainda — quando um app mandar uma notificação com valor, ele aparece aqui"*.

O rótulo de cada app sai do `PackageManager` **por pacote** (`getApplicationInfo`), que não exige `QUERY_ALL_PACKAGES`; se o pacote sumiu (app desinstalado), mostra o próprio nome do pacote.

- [ ] **Steps:** tela, entrada em `MaisScreen`, teste instrumentado do estado vazio e do interruptor desabilitado sem acesso, commit.

```bash
git commit -m "feat: mais › notificações — o acesso, o interruptor e a lista de apps"
```

---

### Task 9: README, capturas e o fechamento

- [ ] Parágrafo no `README.md` explicando a feature, que ela é desligada por padrão e que o acesso se liga nas configurações do sistema.
- [ ] Capturas: a tela `mais › notificações` e uma sugestão na barra de status, nos dois temas, em `docs/superpowers/screenshots/2026-09-04-notificacoes/` com um `README.md`.
- [ ] Nota de execução ao fim deste plano, como a do board.
- [ ] Gate final: `mise run test`, `mise run test-device` (duas vezes, por causa do bump de schema), `lintDebug`.

```bash
git commit -m "docs: captura de notificações no README, com capturas"
```

## Self-Review

**Cobertura do spec:** notificação com botões → Task 5; escopo por app marcado → Tasks 4, 7, 8; dólar fora → Task 1 (o regex exige `R$`); natureza sempre diária → Task 5 (o ramo `ACAO_LANCAR`); dedup por janela + ledger → Task 2 e Task 7; armazenamento mínimo com limpeza de 24 h → Tasks 3 e 7; lista descoberta → Tasks 4 (`registrarAppVisto`) e 7 (passo 6); toda sugestão é saída → Task 5; primeiro valor é o valor → Task 1; corpo abre a sheet preenchida → Task 6; o texto que diz a verdade sobre o acesso → Task 8.

**Fora de escopo confirmado:** dólar e conversão, heurística de sinal e de conta × cartão, ler SMS ou e-mail, categorizar sozinho, e backfill do que chegou antes da marcação.
