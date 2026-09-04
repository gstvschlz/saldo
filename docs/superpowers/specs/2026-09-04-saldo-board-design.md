# saldo — board: a grade do ano como vista padrão

**Data:** 2026-09-04 · **Branch:** `board-1`, criada de `main` (`79ec5f3`)

## Objetivo

Hoje o app abre no ledger: a lista do mês corrente, dia a dia, com o saldo correndo ao
lado. É uma vista **de mês** e **de lista** — para enxergar o próprio comportamento ao
longo do ano não existe nada.

Este projeto acrescenta o **board**: uma grade de calendário em que cada dia é um
quadradinho colorido pelo saldo daquele dia, cobrindo os últimos 12 meses. O board vira
o que o app mostra quando abre; o ledger continua inteiro, a um toque de distância.

O que torna isto barato: nenhuma conta nova de dinheiro é inventada. O board soma as
mesmas `movimentacoes` efetivas que o `ProjectionEngine` já expande, e o anel de fatura
sai do `FaturaCalculator` que já existe. O que se escreve de novo é o **agrupamento por
dia** e a **régua de cor**.

## Decisões do usuário

| Decisão | Escolha | Consequência |
|---|---|---|
| O que a célula mede | **Saldo do dia (entrou − saiu)** | Escala divergente de dois lados, não uma rampa só. Exige resolver o outlier do salário (ver *Escala*). |
| Onde o board mora | **Tela cheia, grade em pé** | Sete colunas (seg…dom), semanas empilhadas, rolando na vertical. Não briga com o arrasto horizontal que a aba já usa para trocar de mês. |
| Board × ledger | **Toggle no cabeçalho** | A aba `saldos` passa a ter duas vistas; o app sempre abre no board. Barra de navegação intocada, quatro abas. |
| Compra no cartão | **Colore o dia da compra; a fatura marca** | A cor conta *quando você gastou*; o dia do vencimento ganha um anel, sem cor própria. |
| Escala de cor | **Múltiplos de um dia típico** | Faixas em ½×, 1× e 2× da mediana; a legenda diz o valor do dia típico em reais. |
| Janela | **12 meses até hoje** | Só fato consumado. Nenhuma célula de futuro, nenhum código visual de "previsto". |

## A tela

```
┌──────────────────────────────────────┐
│  seus dias              ▦  ☰  👁     │   top bar: título + toggle + privacidade
├──────────────────────────────────────┤
│  ┌────────────────────────────────┐  │
│  │ saldo projetado · setembro     │  │   o MESMO hero do ledger
│  │ R$ 6.506,38                    │  │
│  └────────────────────────────────┘  │
│      s   t   q   q   s   s   d       │   cabeçalho de colunas
│  ago ▫   ▫   ▪   ▪   ▪   ▪   ▫       │   calha do mês + 7 células
│      ▪   ▫   ▪   ▪   ▓   █   ▪       │
│  set ▪   ▪   ⊙   ▫   ▪   ▓   ▪       │   ⊙ = fatura vence
│      █   ▪   ▫   ▪   ▪   ▓   ▪       │
│      ▪   ▫   ▪   ⬚   …               │   ⬚ = hoje; depois de hoje, nada
├──────────────────────────────────────┤
│  saiu ███ ▓▓ ░░ ▫ ░░ ▓▓ ███ entrou   │   régua
│  um dia típico = R$ 86 · ⊙ fatura    │
├──────────────────────────────────────┤
│  saldos   totais   ＋   tags   mais  │
└──────────────────────────────────────┘
```

- **Ordem:** semana mais antiga em cima, mais recente embaixo; a tela abre já rolada no
  fim, com hoje visível.
- **Calha do mês:** o rótulo (`ago`, `set`) aparece na linha da semana que contém o dia 1
  daquele mês, e na primeira linha visível do board.
- **Célula:** quadrado de lado `(largura − calha − 6×gap) / 7`, canto 8 dp, com o número
  do dia centralizado. Hoje leva um contorno externo na cor do texto; o dia de vencimento
  da fatura leva um anel interno de 2 dp na cor rosa mais forte. Um dia pode ser os dois.
- **Toque:** troca para o ledger, no mês daquele dia, rolado até ele — exatamente o que
  um deep link `Destino.Saldos(mes, dia)` já faz hoje.
- **Toggle:** `▦` (board, ativo) / `☰` (lista). O ledger mantém sua top bar de sempre
  (`‹ setembro 2026 ›`) mais o mesmo par de ícones, para o caminho de volta existir.

## O dado

**Valor do dia** = soma de `valorCentavos` de todas as movimentações **efetivas**
(linhas materializadas + ocorrências virtuais de recorrência) cuja `data` é aquele dia,
**incluindo as de natureza `CARTAO`**.

Isto é deliberadamente *diferente* do ledger, e a diferença tem precedente no código: o
`ProjectionEngine.movimentacoesDoMes` já documenta que "uma compra no cartão conta no dia
em que foi feita, não no vencimento da fatura", e é sobre essa regra que o "para onde
foi" da insights-1 é construído. O board é uma vista de comportamento, não de caixa.

**Consequência aceita:** somar as células **não** reconstrói o saldo projetado do hero. O
board responde "quando eu gastei", o hero responde "quanto vai sobrar". São duas
perguntas.

**Anel de fatura:** as datas de `vencimento` das faturas dentro da janela, vindas de
`ProjectionEngine.faturasAte`. A fatura não contribui com valor nenhum para a célula.

## A escala

Sete estados: três tons de rosa (saiu mais), neutro, três tons de verde (entrou mais).

1. **Unidade** = mediana de `|valor|` entre os dias da janela **com movimento**
   (`valor != 0`). Dias zerados não entram no cálculo — senão a mediana de uma pessoa
   normal seria zero.
2. **Faixas**, para cada lado, sobre `|valor| / unidade`: `< 0,5` → tom 1; `< 1,5` → tom
   2; `>= 1,5` → tom 3.
3. **Zero** → neutro.
4. **Fora da janela de registro** (antes de `saldoInicialData`) → apagado, sem número.
5. **Sem nenhum dia com movimento** (usuário novo): unidade indefinida, tudo neutro, e a
   legenda diz "ainda sem um dia típico".

A mediana é robusta ao salário por construção: um dia trinta vezes maior que os outros
muda a mediana em nada e simplesmente satura no tom 3. É esse o ponto da escolha, contra
uma normalização pelo máximo, que deixaria o board inteiro pálido.

A legenda mostra a unidade em reais — "um dia típico = R$ 86" — para a cor ter tradução.

## Privacidade, fonte grande, acessibilidade

- **Olho fechado:** as cores **ficam**, os números somem — o número do dia continua
  (é data, não dinheiro), mas o valor no hero, na legenda e nas descrições de
  acessibilidade é mascarado pelo `MoneyText`/`FormatoMoney` de sempre. Um board achatado
  seria inútil, e a cor entrega padrão, não valor.
- **Fonte grande:** a partir de `fontScale >= 1.3` o número do dia é omitido — não cabe
  na célula, e espremê-lo o tornaria ilegível de qualquer jeito. Cor, anel, contorno de
  hoje e o toque continuam. A célula nunca encolhe abaixo de 40 dp de alvo de toque.
- **`contentDescription` por célula:** `"3 de setembro, saiu R$ 152,30"` /
  `"12 de agosto, sem movimentação"` / `"5 de setembro, saiu R$ 40,00, fatura vence"`,
  com o valor mascarado quando a privacidade está ligada.

## Estrutura

| arquivo | responsabilidade |
|---|---|
| `domain/BoardEngine.kt` (novo) | puro e testável na JVM: `board(input) → Board` (o `LedgerInput` já carrega `hoje`). Agrupa por dia, calcula a unidade e o nível de cada dia, marca os vencimentos. |
| `domain/ProjectionEngine.kt` (modificar) | expor `movimentacoesAte(input, ateMes)` — o `efetivas` privado. Sem isso o board chamaria `movimentacoesDoMes` treze vezes e reexpandiria as recorrências treze vezes. |
| `ui/board/BoardScreen.kt` (novo) | a grade, a calha, a régua, o hero e o cabeçalho. |
| `ui/board/BoardViewModel.kt` (novo) | fluxo próprio sobre `repository.ledger` + `settings`, cálculo fora da main thread. |
| `ui/SaldoApp.kt` (modificar) | `rememberSaveable` da vista corrente; deep link força a lista. |
| `ui/theme/Color.kt` (modificar) | sete tokens novos por tema — 3 rosas, 3 verdes, 1 neutro. A borda do neutro reusa `separator` e o anel de fatura reusa o rosa mais forte. |
| `ui/components/M3.kt` (modificar) | `SaldoTopBar` passa a aceitar mais de uma ação. |

**Fronteiras:** o `BoardEngine` não conhece Compose nem cores — devolve `nivel: Int` em
−3..3, e a tela decide que tom isso é. O `BoardScreen` não conhece Room — recebe um
`Board` pronto. É a mesma divisão que o `InsightsEngine` e os segmentos de totais já
seguem.

## Fora de escopo

- Widget do board (o seletor já tem quatro; um quinto é outro projeto).
- Board como fonte de comparação ano a ano.
- Qualquer célula de futuro / "a caminho" na grade.
- A captura de notificações com valores em R$/USD, que é o **spec 2** deste par e tem
  interview própria ainda por fazer.

## Testes

**JVM (`BoardEngine`)** — a mediana com zero, um e dois dias de movimento; as faixas nos
limites exatos (0,5 e 1,5 da unidade, dos dois lados); o salário saturando no tom 3 sem
alterar o lado rosa; compra `CARTAO` caindo no dia da compra; o anel no vencimento certo
com fechamento 28 / vencimento 5; dias anteriores ao `saldoInicialData` marcados fora da
janela; recorrência virtual de um mês não materializado entrando na soma do dia.

**Instrumentado** — o app abre no board; o toggle troca para o ledger e volta; tocar num
dia abre o ledger naquele mês rolado até o dia; um deep link `Destino.Saldos` continua
caindo na lista; a privacidade mascara o hero e a legenda mas não apaga as cores; a
grade sobrevive a fonte 1.5 e 2.0.
