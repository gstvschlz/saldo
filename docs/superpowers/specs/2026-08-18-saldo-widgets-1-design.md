# saldo — widgets 1: quatro tipos na tela inicial

**Data:** 2026-08-18 · **Branch:** `widgets-1`, criada de `redesign-b`

## Objetivo

Hoje o app tem **um** widget: saldo projetado + delta do mês, mascarado por padrão,
com um `+`. Este projeto acrescenta **três tipos novos** e mais um tamanho para o
existente, cada tipo aparecendo com nome próprio no seletor do launcher.

O que torna isto barato: a insights-1 deixou motores puros e testados
(`InsightsEngine.aCaminho`, `paraOndeFoi`, `recorrencias`, `ProjectionEngine`) que já
calculam tudo que os widgets precisam. **Nenhuma conta nova é escrita neste projeto.**

## Decisões do usuário

| Decisão | Escolha | Consequência |
|---|---|---|
| Forma | **Um provider por tipo** | Cada tipo tem nome e preview no seletor; nada de activity de configuração nem estado por widget-id. |
| Escopo | **Os quatro numa tacada** | Mais superfície nova antes do primeiro feedback; aceito conscientemente. |
| Privacidade | **Máscara só de dinheiro** | Descrições e nomes de tag aparecem sempre, inclusive na tela de bloqueio. Decisão deliberada, não descuido. |

## Os quatro tipos

| tipo | tamanho | mostra | toque |
|---|---|---|---|
| `saldo` (existe) | 2×1, 4×1, **+ 2×2 novo** | saldo projetado, delta, `+` | valor → `Saldos(mês)`, `+` → `NovaMovimentacao` |
| `a caminho` | 4×2 | "ainda saem R$ X · até 31 ago" + até 3 linhas datadas | linha → `Saldos(mês, dia)`, cabeçalho → `Totais(mês)` |
| `para onde foi` | 4×2 | barra 100% por tag + top 4 | → `Totais(mês)` |
| `lançar` | 2×1 | dois botões: − saída, + entrada | → `NovaMovimentacao(saida)` |

Estados de cada tipo continuam os três de hoje: `SemOnboarding`, `Pronto`, `Falha`.
Mês vazio em `a caminho` → "nada agendado até o fim do mês"; mês encerrado (o widget
só olha o mês corrente, então só no último dia) → "mês encerrado".

## Duas restrições que moldam o desenho

**1. `lançar` exige mexer na navegação.** `Destino` hoje é `Saldos(mes, dia?)`,
`NovaMovimentacao` e `Totais(mes)` — não há como dizer "abre a sheet já como saída".
`NovaMovimentacao` vira `data class NovaMovimentacao(val saida: Boolean? = null)`,
viajando num extra novo. É uma extensão pequena e coberta: o par
`paraPares()` ⇄ `de()` já tem teste de ida e volta na JVM, e virar `data object` em
`data class` faz o compilador apontar os cinco call sites.

**2. `para onde foi` não pode reusar o `SegmentedBar`.** Aquele gráfico é um `Canvas`
do Compose, e o Glance não tem `Canvas` — RemoteViews não hospedam um. A barra é
refeita com `Row` + `defaultWeight()` + `background`. Para não virar duas verdades, a
**matemática continua sendo a mesma**: `ChartMath.larguras` (já testado) calcula as
frações; só o desenho difere.

## Estrutura

Um substrato compartilhado e quatro providers finos:

| arquivo | responsabilidade |
|---|---|
| `widget/CoresWidget.kt` (extrair) | hoje é `private` dentro de `SaldoWidgetContent`. Vira compartilhado, carregando `sobreTint` — a tinta que se lê sobre o `tint` — para essa decisão morar num lugar só. |
| `widget/WidgetDados.kt` (novo) | um carregador `suspend`: UM snapshot do repositório → o estado de que cada tipo precisa. Substitui quatro cópias de `carregar()`. |
| `widget/WidgetRefresher.kt` (modificar) | `updateAll` dos quatro providers, não só do `SaldoWidget`. |
| 4 × (`XWidget` + `XReceiver` + `res/xml/x_info.xml` + entrada no manifest) | um por tipo. |

Fluxo de dados igual ao de hoje: `provideGlance` tira um snapshot (`first()`), não
coleta. Quem atualiza é o `WidgetRefresher` (processo vivo), o `updatePeriodMillis` de
6 h e o worker dos lembretes.

## Testes

O ferramental **já existe e está provado**: `SaldoWidgetContentTest` tem 6 casos JVM
com `runGlanceAppWidgetUnitTest`, e o `build.gradle.kts` já carrega
`glance-testing` + `glance-appwidget-testing` com o `isReturnDefaultValues` que essa
API exige. Cada tipo novo ganha casos no mesmo molde: estado mascarado, estado
revelado, estado vazio, e os extras do deep link.

**Limite honesto:** o teste do Glance assere semântica (texto, testTag,
contentDescription), **não cor**. Ele não pega regressão de contraste — foi
exatamente assim que o `+` branco sobre o tint claro passou. A mitigação é
estrutural, não de teste: `sobreTint` num arquivo só, revisado por olho.

## Fora de escopo

Widget de recorrências; configuração por widget-id; dynamic color; qualquer conta
nova de domínio.
