# saldo — widgets e acessibilidade: o seletor que mostra, o widget que amanhece, e o app que fala

**Data:** 2026-09-07 · **Branch:** `widgets-a11y-1`, a criar de `main` depois do merge de
`captura-2` · **Fatia 4 de 4** da rodada de endurecimento.

## Objetivo

Sete widgets é a manchete do app, e os sete aparecem no seletor com o mesmo ícone, sem
nenhuma imagem: nenhum `previewLayout` nem `previewImage` em nenhum `*_widget_info.xml`.
Com os lembretes desligados (o padrão) nenhum worker roda, então de madrugada, com o
processo morto, o widget mostra o dia de ontem até o `updatePeriodMillis` de 6 h resolver
acordar. E o `WidgetRefresher` coleta o ledger — e mantém o ticker de um minuto vivo — pelo
tempo de vida do processo mesmo sem widget na tela.

Do lado da acessibilidade, a auditoria de 2026-09-07 achou: o `+` sem nome para o
TalkBack; os seis gráficos invisíveis para ele; as abas que não anunciam "selecionada"; o
olho da privacidade com a mesma descrição ligado e desligado; o board que some com os
números do dia acima de 1,3× de fonte (justo para quem aumenta a fonte); o cabeçalho de
dias da semana "s t q q s s d"; hoje e dia aberto distinguidos só por cor; e alvos de toque
bem abaixo de 48 dp, alguns destrutivos.

## Decisões do usuário

| Decisão | Escolha | Consequência |
|---|---|---|
| Prévia no seletor | **`previewLayout` XML, Android 12+** | Contra a minha recomendação (vetor `previewImage` para todas as versões). Sete layouts estáticos renderizados pelo launcher com o tema; abaixo do 12 fica o ícone. |
| Dias da semana | **seg ter qua qui sex sáb dom** | Três letras, minúsculas como o resto do app. |

## Decisões que eu tomei

**1. O board nunca esconde o número do dia.** A constante `ESCALA_SEM_NUMERO = 1.3f` sai.
Em 360 dp de largura a célula tem ~48 dp; dois dígitos a 2× de 11 sp medem ~30 dp. Cabe. O
que muda com a fonte grande é a célula ficar um pouco mais alta, e a grade rolar — que ela
já faz.

**2. Hoje e dia aberto sem depender de matiz.** Hoje ganha um ponto de 4 dp abaixo do
número (na cor `label`); o dia aberto ganha a borda de 3 dp (hoje é 2). As cores de borda
continuam como estão; o que muda é que a forma também diferencia.

**3. Um só helper de atualização.** `WidgetAcoes.atualizarTodos(context)` chama
`updateAll` nos sete providers. `LembretesWorker` (que hoje só atualiza o `SaldoWidget`),
`WidgetRefresher` e o worker novo de meia-noite usam o mesmo helper.

**4. O worker de meia-noite é independente dos lembretes.** `PeriodicWorkRequest` de 24 h
com atraso inicial até 00:05 locais, `ExistingPeriodicWorkPolicy.UPDATE`, sem
constraints, enfileirado em `SaldoApplication.onCreate` **e** em `onEnabled` de cada
receiver. O worker sai na hora se `GlanceAppWidgetManager.getGlanceIds` for vazio para os
sete. Inexato por natureza (Doze), mas um widget que amanhece errado por dez minutos é
diferente de um que amanhece errado por seis horas.

**5. O refresher só roda com widget na tela.** `WidgetRefresher` passa a ser iniciado e
parado por um `MutableStateFlow<Boolean>` "há widget" no `AppContainer`, ligado em
`onEnabled` e desligado em `onDisabled` dos sete receivers (Glance expõe os dois), e
consultado em `SaldoApplication.onCreate` pelo `getGlanceIds`. Sem widget, o ledger não
tem coletor de processo e o `diaAtual()` de um minuto morre com a última tela.

**6. As frases dos gráficos são funções puras.** `ui/totais/charts/Descricoes.kt` produz
uma `String` por gráfico a partir dos mesmos dados que o desenho recebe; com a privacidade
ligada, os valores viram "valor oculto". São testáveis na JVM e ficam ao lado da matemática
dos gráficos (`ChartMath`), que já é pura.

## Comportamento

### Prévias

Sete layouts em `res/layout/preview_<widget>.xml` (views clássicas: `LinearLayout`,
`TextView`, `View` com `shape` drawable de canto arredondado), cada um no tamanho padrão
do widget, com conteúdo fixo:

| widget | prévia |
|---|---|
| saldo (2×1) | "saldo" · `R$ •••••` |
| a caminho (4×2) | "a caminho" · `R$ •••••` · duas linhas: "aluguel · 10" e "internet · 15" |
| para onde foi (4×2) | "para onde foi" · barra segmentada de três cores · "mercado · casa · lazer" |
| lançar (2×1) | os dois botões "− saída" e "+ entrada" |
| board (4×2) | cinco semanas de células nos sete tons, com o anel de fatura num dia |
| ritmo (4×1) | "ritmo" · a barra do acumulado sobre a do costume |
| poupança (4×1) | "poupança" · seis barras com "12 %" na última |

As cores vêm de `res/values/colors.xml` (que já existe) em pares `values`/`values-night`
espelhando `CoresWidget`: `widget_fundo`, `widget_label`, `widget_secundario`,
`widget_tint`, `widget_positivo`, `widget_negativo`, `widget_board_0..6`. Um comentário em
`CoresWidget` aponta para elas, e um teste instrumentado compara os valores lidos do
resource com os do `ColorProvider` nos dois modos — para as duas fontes não se
desencontrarem em silêncio.

Cada `*_widget_info.xml` ganha `android:previewLayout="@layout/preview_<widget>"`.
`previewImage` não entra (decisão do usuário).

### Atualização

- `widget/WidgetAcoes.kt`: `atualizarTodos(context)`.
- `widget/AtualizacaoDiariaWorker.kt`: o worker de meia-noite (decisão 4); nome único
  `"widgets-meia-noite"`.
- `LembretesWorker` chama `atualizarTodos` no lugar de `SaldoWidget().updateAll`.
- `WidgetRefresher`: `iniciar()`/`parar()` controlados pelo fluxo "há widget" (decisão 5);
  o resto (debounce, `catch`, `CancellationException`) como está.
- Os sete receivers ganham `onEnabled`/`onDisabled` via uma classe base
  `SaldoWidgetReceiver` comum que faz as duas coisas (ligar o fluxo, enfileirar o worker).

### TalkBack

| onde | hoje | passa a |
|---|---|---|
| `AddButton` (`SaldoTabBar`) | só `testTag` | `semantics { contentDescription = "nova movimentação"; role = Role.Button }` |
| tecla ⌫ do teclado | sem descrição | `"apagar"` |
| tecla `00` (de `uso-diario-1`) | — | `"zero zero"` |
| abas (`SaldoTabBar`) | `clickable` | `selectable(selected, role = Role.Tab)` |
| olho da privacidade (board e ledger) | `"alternar privacidade"` | `"mostrar valores"` / `"ocultar valores"` conforme o estado |
| `TrendChart` | nada | "tendência: em julho entrou R$ 5.000, saiu R$ 4.200, sobrou R$ 800; em agosto …" |
| `RitmoChart` | nada | "ritmo: R$ 1.200 até hoje; o costume neste ponto do mês é R$ 1.500" |
| `PoupancaBars` | nada | "poupança: abril 12 %, maio 8 %, … setembro sem taxa" |
| `ReservaLine` | nada | "reserva: de R$ 2.000 em abril a R$ 3.400 em setembro" |
| `WeekdayBars` | nada | "por dia da semana: segunda R$ 80, terça R$ 120, …" |
| `SegmentedBar` / `TagsStack` | nada | "para onde foi: mercado 40 %, casa 30 %, sem tag 30 %" |
| swatches da legenda do board | `Box` sem semântica | um por tom: "saiu, três vezes um dia típico" … "entrou, três vezes um dia típico"; o neutro "sem movimento" |

Os gráficos recebem a frase em `Modifier.semantics { contentDescription = … }` no `Canvas`;
com `LocalPrivacy.current.oculto`, `Descricoes` recebe `oculto = true` e escreve "valor
oculto" no lugar de cada dinheiro.

### Visão

- `DIAS_SEMANA = listOf("seg", "ter", "qua", "qui", "sex", "sáb", "dom")` em
  `ui/components/Semana.kt`, usado pelo cabeçalho do board e pelo `WeekdayBars` (que hoje
  reimplementa a mesma lista); o rótulo a 11 sp cabe nos ~48 dp da coluna.
- Board: números em qualquer escala (decisão 1); ponto de hoje e borda do dia aberto
  (decisão 2).
- Alvos de toque a 48 dp mínimos: `cancelar`/`salvar` da sheet (o `clickable` vai para a
  `Row` com padding, não para o `Text`), "excluir movimentação", os chips de natureza, as
  linhas do diálogo de tags — todos por `Modifier.defaultMinSize(minHeight = 48.dp)`, o
  mesmo que `InsetRow` já usa. Os botões da linha de tag já foram em `uso-diario-1`.
- `LinhaValor` (`TotaisScreen`) e `LinhaMes` (`RecorrenciasScreen`) viram um componente só
  em `ui/components`, porque a passada de alvos de toque toca os dois.

## Estrutura

```
res/layout/preview_*.xml                sete, novos
res/values/colors.xml, values-night/    widget_* espelhando CoresWidget
res/xml/*_widget_info.xml               previewLayout
widget/WidgetAcoes.kt                   atualizarTodos
widget/AtualizacaoDiariaWorker.kt       novo
widget/SaldoWidgetReceiver.kt           classe base com onEnabled/onDisabled (os sete herdam)
widget/WidgetRefresher.kt               iniciar/parar pelo fluxo "há widget"
lembretes/LembretesWorker.kt            atualizarTodos
SaldoApplication.kt / AppContainer      fluxo "há widget", enfileira o worker
ui/nav/SaldoTabBar.kt                   semântica do + e das abas
ui/entry/AmountKeypadScreen.kt          descrições das teclas
ui/board/BoardScreen.kt                 números sempre, ponto/borda, DIAS_SEMANA, legenda falada, olho
ui/ledger/LedgerScreen.kt               olho
ui/components/Semana.kt                 novo
ui/components/Components.kt             LinhaValor unificada
ui/totais/charts/Descricoes.kt          novo — frases puras
ui/totais/charts/Charts.kt              semantics em cada Canvas; WeekdayBars usa Semana
ui/entry/NewEntrySheet.kt               alvos de 48 dp
```

## Fora de escopo

- `previewImage` para Android 11 e anteriores.
- Widget na tela de bloqueio, configuração de widget, tamanhos novos.
- Navegação por teclado físico, `stateDescription` para além do que está na tabela,
  contraste do tema (o redesign B já mediu).
- Extrair strings para `strings.xml`.

## O que só você pode verificar

Nenhum widget deste app foi visto numa tela inicial de verdade (nota de 2026-08-18), e
colocar um exige um arrastar que o `adb` não reproduz. Esta fatia não muda isso: o teste
infla cada `preview_*.xml` e compara cores, mas **o seletor com as sete prévias e o widget
amanhecendo certo são coisas para você olhar no aparelho** — a nota de execução vai pedir
isso explicitamente, com o que olhar em cada um.

## Testes

**JVM**
- `DescricoesTest`: uma frase por gráfico com dados de exemplo; com `oculto = true`
  nenhum "R$" aparece; lista vazia → "sem dados".
- `SemanaTest`: sete rótulos, segunda primeiro, `sáb` com acento.
- `AtualizacaoDiariaWorkerTest` (lógica pura do atraso): de 23:59 o atraso é 6 min; de
  00:05 é 24 h.

**Instrumentados**
- `PreviewLayoutsTest`: cada `preview_*.xml` infla sem exceção; `colors.xml` bate com
  `CoresWidget` nos dois modos.
- `AtualizacaoDiariaWorkerTest` (WorkManager `TestListenableWorkerBuilder`): com um glance
  id, chama `atualizarTodos`; sem nenhum, termina sem tocar em widget.
- `WidgetRefresherTest`: com "há widget" falso, o ledger não tem coletor (contador de
  assinaturas num repositório falso); ligar o fluxo inicia, desligar para.
- `SaldoTabBarTest`: o `+` tem `contentDescription` "nova movimentação"; a aba selecionada
  tem `Selected` verdadeiro e `Role.Tab`.
- `BoardScreenTest`: com `fontScale = 2f`, o número do dia de hoje está na árvore; a
  legenda tem sete nós com descrição; o olho anuncia "ocultar valores" e depois "mostrar
  valores"; cabeçalho "seg … dom".
- `TotaisContentTest`: cada gráfico tem `contentDescription` não vazia; com privacidade
  ligada, nenhuma contém "R$".
- `NewEntrySheetTest`: `cancelar`, `salvar`, `excluir movimentação` e os chips têm altura
  ≥ 48 dp (`assertHeightIsAtLeast`).
