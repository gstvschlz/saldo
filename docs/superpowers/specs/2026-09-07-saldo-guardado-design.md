# saldo — guardado: quanto do que entrou foi para algo produtivo

**Data:** 2026-09-07 · **Branch:** `guardado-1`, a criar de `main` (`7376b16`, v0.5.0)
· fatia curta encaixada **antes** de `dados-1`.

## Objetivo

O usuário, com as palavras dele: *"a ideia não é que o app acompanhe o quanto tenho de
valor investido, mas sim o quanto que estou conseguindo economizar — verificar
entrada/saída e quanto dessa saída é pra algo produtivo (economia) versus para gastos do
dia a dia"*.

O app já tem os números: `totais → mês` lista entradas, saídas por natureza e a reserva
acumulada; `totais → tendência` desenha a taxa de poupança de seis meses. O que falta é a
leitura, no lugar onde se olha todo dia: **do que entrou neste mês, quanto foi guardado**.

## Decisões do usuário

| Decisão | Escolha | Consequência |
|---|---|---|
| O que medir | **Só o percentual guardado** | Nada de saldo investido, ajuste de rendimento, resgate ou reserva inicial — as duas primeiras perguntas da interview partiam dessa premissa e foram descartadas. |
| Onde | **No hero e no widget de saldo** | Uma pill "guardou 20%" ao lado de "R$ X no mês", no board e na lista; a mesma linha no widget, mascarada junto com o valor. |

## Decisões que eu tomei

**1. Uma conta só, em um lugar só.** `taxaGuardada = round(economia × 100 ÷ entradas)`,
com `economia` = as saídas de natureza `ECONOMIA` do mês e `entradas` = tudo que entrou no
mês. A tendência já faz essa divisão para "taxa de poupança", truncando; passa a chamar a
mesma função, que arredonda. Os dois lugares não podem discordar por um por cento.

**2. Mais de cem por cento é verdade, não erro.** Guardar mais do que entrou (de saldo
antigo) mostra "guardou 120%". Sem entrada no mês, a taxa é nula e a pill some — dividir
por zero não vira "0%".

**3. O percentual não é número de conta.** A privacidade mascara dinheiro; "20%" não
entrega quanto. No hero a pill fica visível com os valores ocultos. No widget o usuário
escolheu mascarar junto: "guardou ••%".

**4. Tocar na pill leva à tendência.** É onde a poupança mês a mês já está. Para isso o
segmento de `totais` sobe da tela para o `TotaisViewModel` (com `SavedStateHandle`, como o
mês) — o que `dados-1` já previa para calcular só o segmento visível.

## Comportamento

- `domain/ProjectionEngine.kt`: `fun taxaGuardada(entradasCentavos: Long, economiaCentavos: Long): Int?`
  (nulo com entradas ≤ 0; `Math.round`). `MesLedger` ganha `val taxaGuardada: Int?`, calculado
  em `mes()` a partir das mesmas efetivas do mês (entradas = valores positivos; economia =
  |valores negativos com natureza ECONOMIA|). `InsightsEngine.tendencia` usa a função.
- `ui/ledger/LedgerScreen.kt` `BalanceHero`: à direita da pill "no mês", `mes.taxaGuardada?.let`
  → pill "guardou N%" (`SaldoPill` com a mesma forma), `contentDescription = "guardou N% do que entrou"`,
  clicável → `onVerGuardado()`. `BalanceHero` ganha `onVerGuardado: () -> Unit`; o board e
  a lista repassam.
- `ui/SaldoApp.kt`: `onVerGuardado = { totaisVm.irPara(mesVisto); totaisVm.selecionarSegmento(SegmentoTotais.TENDENCIA); tab = SaldoTab.TOTAIS }`
  onde `mesVisto` é o mês do board ou da lista, conforme a vista.
- `ui/totais/TotaisViewModel.kt`: `segmento: StateFlow<SegmentoTotais>` com `KEY_SEGMENTO`
  no `SavedStateHandle`; `selecionarSegmento(s)`. `TotaisScreen` lê do ViewModel em vez do
  `rememberSaveable`; `SegmentoTotais` muda de arquivo para `TotaisViewModel.kt` (ou um
  `Segmentos.kt`) para o ViewModel não importar a tela.
- `widget/WidgetEstado.kt`: `Pronto.taxaGuardada: Int?`; `widget/WidgetDados.kt` preenche
  de `MesLedger.taxaGuardada`. `widget/SaldoWidgetContent.kt`: nos layouts largos (4×1 e
  2×2, onde o delta já aparece) uma linha "guardou N%" ou "guardou ••%" quando
  `!mostrarValores`; some quando nulo. O 2×1 continua só com o valor — não há espaço.

## Fora de escopo

Saldo investido, ajuste de rendimento, resgate, reserva inicial, "gastou X%" ao lado,
barra segmentada, widget próprio. Nenhuma mudança de schema, nenhum ajuste novo, nada no
export.

## Testes

**JVM**
- `ProjectionEngineTest`: `taxaGuardada(0, x)` nulo; `(1000, 200)` = 20; `(1000, 205)`
  arredonda para 21 (20,5 → 21); `(1000, 1200)` = 120; `mes()` preenche `MesLedger.taxaGuardada`
  com uma entrada e uma economia no mês, e nulo num mês sem entrada.
- `InsightsEngineTest`: `taxaPoupanca` da tendência bate com `taxaGuardada` (o teste
  existente que assumia truncamento, se houver, passa a esperar o arredondado).
- `SaldoWidgetContentTest` (Glance JVM, já existe): largo com `taxaGuardada = 20` mostra
  "guardou 20%"; mascarado mostra "guardou ••%"; nulo não mostra a linha; compacto não
  mostra.
- `TotaisViewModelTest`: o segmento sobrevive no `SavedStateHandle`.

**Instrumentados**
- `BoardScreenTest` e `LedgerScreenTest`: a pill "guardou N%" com entrada e economia no
  mês; ausente sem entrada; visível com a privacidade ligada.
- `VistaSaldosTest`: tocar na pill abre `totais` no segmento tendência
  (`TAG_SEGMENTO_TOTAIS` com "tendência" selecionado).
