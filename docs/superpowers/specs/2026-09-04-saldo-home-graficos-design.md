# saldo — a home nova, os gráficos de performance e os widgets

**Data:** 2026-09-04 · **Branch:** direto na `main`, a partir de `ed1c193`

## Objetivo

Três trabalhos que o usuário pediu juntos e mandou tratar numa spec só:

1. **O board substitui o ledger.** A aba `saldos` passa a ter uma vista só — a grade do mês
   — e a lista de lançamentos vira o que aparece ao tocar num dia.
2. **Gráficos de performance** dentro do app: ritmo do mês, poupança mês a mês, e "para onde
   foi" ao longo do tempo.
3. **Três widgets novos**: o board do mês, o ritmo do mês e a poupança mês a mês.

Eu recomendei separar em três specs e ele escolheu uma só, ciente de que a branch demora
mais a fechar. O plano compensa isso em três fases que fecham verdes de forma independente:
a fase 1 pode ir para o aparelho sem que a 2 e a 3 existam.

## Decisões do usuário

| Decisão | Escolha | Consequência |
|---|---|---|
| O que é a home | **O board substitui o ledger** | Uma vista só na aba `saldos`. O ícone de lista some. A lista do mês inteiro deixa de existir como tela — aceito explicitamente. |
| Mês passado | **Setas no cabeçalho + arrasto horizontal** | O board mostra um mês por vez. O mês corrente para em hoje; um mês passado aparece inteiro. |
| Quais gráficos | **Ritmo do mês, poupança mês a mês, "para onde foi" ao longo do tempo** | Ele recusou "saldo realizado × projetado". |
| Quais widgets | **Os três**: board do mês, ritmo, poupança | Sete widgets no total. |
| Escopo | **Uma spec só para os três** | Contra a minha recomendação; registrado. |

## Decisões que eu tomei

Levantei estas pontas na interview, ele mandou começar sem decidi-las. Ficam aqui explícitas
para poderem ser derrubadas depois.

| Ponta | Decisão | Razão |
|---|---|---|
| Os filtros `todas / diários / fixas` | **Somem** | Eles filtravam a lista do mês, que deixou de existir. O painel do dia tem poucas linhas e não pede filtro; "fixas" já tem a aba de recorrências inteira. **É a única perda real desta spec** — se doer, volta como filtro da grade, pintando só o que passa no filtro. |
| Swipe-pra-apagar com desfazer | **Vai para o painel do dia** | É a mesma `LinhaMov`; muda de hospedeiro, não de comportamento. O snackbar de desfazer continua na `SaldoApp`. |
| Deep links (`Destino.Saldos(mes, dia)`) | **Caem no board**, naquele mês, com aquele dia aberto no painel | Notificação, widget e lembrete apontam para um dia; hoje eles forçam a lista, que some. |
| Navegar para meses futuros | **Bloqueado**: a seta `›` para no mês corrente | Um mês futuro seria uma grade inteira de "ainda não aconteceu". O futuro já tem tela própria: `totais › a caminho`. |
| Onde os gráficos moram | **Em `totais`**, cada um no segmento que já responde àquela pergunta | Ele escolheu a home *sem* gráficos (a opção com gráficos estava na mesa e não foi a escolhida). `totais` já tem mês, tendência e para onde foi — os três caem exatamente neles, sem aba nova e sem duplicar conta. |
| Dia aberto por padrão | **Hoje**, quando o mês na tela é o corrente | O app abre respondendo "o que eu gastei hoje". Num mês passado nada vem aberto, e o rodapé mostra a régua do dia típico como hoje. |

## A tela

```
┌──────────────────────────────────────┐
│  ‹   setembro   ›              👁    │   sem toggle de lista
├──────────────────────────────────────┤
│  ┌────────────────────────────────┐  │
│  │ saldo projetado · 30 set       │  │   o hero de sempre
│  │ R$ 6.506,38      +1.204 no mês │  │
│  └────────────────────────────────┘  │
│   s  t  q  q  s  s  d                │
│         ▓  ▓  ▓  ░  ░                │   dia 1 na primeira linha
│   ▓  ▓  ▓  ▓  █  ░  ░                │
│   ▓  █  ▓ [▓]                        │   [hoje] · para em hoje
├──────────────────────────────────────┤
│  4 de setembro            −152,30    │   painel do dia
│  • mercado                −128,40    │   swipe apaga, toque edita
│  • uber                    −23,90    │
└──────────────────────────────────────┘
```

Num mês passado a grade vem inteira e o painel começa fechado, com a régua ("um dia típico =
R$ 48,00") no lugar dele. O anel de fatura continua marcando o vencimento, e tocar num dia
com anel abre o diálogo da fatura — o mesmo que o ledger abria.

## Os três gráficos

Nenhum inventa conta nova de dinheiro: os três somam as mesmas movimentações efetivas que o
`ProjectionEngine` já expande.

**Ritmo do mês** — `totais › mês`. O gasto acumulado dia a dia do mês corrente contra a mesma
altura dos meses anteriores: "no dia 4 você já tinha gasto R$ 152; em agosto, no dia 4, eram
R$ 380". Responde *estou indo rápido demais?*, que é a pergunta que um app de saldo responde
melhor do que qualquer outro. Duas linhas: o mês na cor do saldo, a média dos meses
anteriores em cinza.

**Poupança mês a mês** — `totais › tendência`. Uma barra por mês com o que sobrou (entrou −
saiu) e a taxa de poupança em cima. O `InsightsEngine.tendencia` já devolve `PontoMes` para
6 meses; o gráfico é a leitura visual do que já se calcula.

**Para onde foi, ao longo do tempo** — `totais › para onde foi`. As tags empilhadas mês a
mês, para ver uma categoria crescendo. Hoje esse segmento mostra as fatias de um mês só, sem
comparação nenhuma entre meses.

## Os três widgets

Glance desenha com caixas, então grade e barras saem nativas — nenhum vira imagem.

**Board do mês** (4×2) — a própria grade, sem número nenhum. É o widget mais barato de todos
e o único que não precisa de máscara: cor não entrega valor.

**Ritmo do mês** (4×1) — "R$ 1.240 · dia 12" com uma barra de acumulado contra o mês passado
na mesma altura. Mascarado por padrão, como os outros.

**Poupança mês a mês** (4×2) — seis barrinhas, uma por mês, com a do mês corrente destacada.
Mascarado por padrão.

## Arquitetura

Três camadas, como no board:

1. **Motores puros** em `domain/`, sem Compose e sem Room, testados na JVM: `BoardEngine`
   ganha um parâmetro de mês; nasce `RitmoEngine`; `InsightsEngine` ganha a série de tags por
   mês.
2. **Componentes de gráfico** em `ui/totais/charts/`, ao lado de `TrendChart`, `WeekdayBars`
   e `ReservaLine`, que já existem.
3. **Telas e widgets**, que só ligam os dois.

O `LedgerScreen` não é apagado: a `LinhaMov`, o swipe e o diálogo da fatura viram o painel do
dia. O que morre é o andaime do mês — a lista de dias, os chips de filtro e a top bar dele.

## Testes

Cada fase fecha verde em `mise run test` e `mise run test-device`, e cada motor novo nasce
com teste JVM antes da tela. Os três testes ponta-a-ponta (`EntryFlowTest`,
`LedgerScreenTest`, `SwipeDeleteTest`) trocam para a lista depois do onboarding e **vão
quebrar de novo** quando a lista sumir — é a terceira vez que a vista padrão os morde, e
desta vez eles passam a exercitar o painel do dia.

## Fora de escopo

Filtro na grade, exportar gráfico como imagem, escolher quais meses o gráfico cobre, e
qualquer widget redimensionável além dos tamanhos acima.
