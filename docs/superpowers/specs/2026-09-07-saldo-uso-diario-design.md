# saldo — uso diário: voltar, listar, buscar, repetir e desfazer

**Data:** 2026-09-07 · **Branch:** `uso-diario-1`, a criar de `main` (`f64ebcf`)
· **Fatia 1 de 4** da rodada de endurecimento (depois: `dados-1`, `captura-2`,
`widgets-a11y-1`).

## Objetivo

Uma auditoria de 2026-09-07 (três varreduras do código, cada achado conferido no fonte)
listou o que atrapalha quem usa o saldo todo dia. Nada aqui é feature nova de produto: é o
app parando de surpreender. O botão Voltar fecha o app no meio de uma tarefa; o `+` lança
em hoje mesmo com outro dia aberto no board; a seta de avançar mês não faz nada e não diz
por quê; a tecla `,` do teclado vibra e não faz nada; não existe busca; o mês inteiro em
lista sumiu junto com o ledger; uma recorrência não pode nascer nem parar pela sheet; uma
tag apagada não volta; telas vazias não explicam nada; e girar o aparelho fecha o seletor
de data e revela valores que você tinha acabado de esconder.

## Decisões do usuário

| Decisão | Escolha | Consequência |
|---|---|---|
| Ordem das fatias | **Uso diário primeiro**, depois dados, captura, widgets + acessibilidade | Contra a minha recomendação (dados primeiro). O que se toca todo dia vem antes. |
| Voltar | **Subtela → aba → saldos → sair** | Padrão Android de barra inferior. Sem histórico de abas. |
| O `+` | **Usa o dia aberto no board, senão hoje** | A linha de data da sheet mostra a data escolhida; nada fica escondido. |
| Lista do mês | **Toggle lista/grade na barra do board** | O espelho do "ver como grade" que o ledger já tem. O board continua a vista padrão. |
| Busca | **Lupa no ledger, histórico inteiro** | Descrição, nome da tag ou valor; resultados agrupados por mês. |
| Duplicar lançamento | **Não entra** | O teclado é rápido o bastante. |
| Recorrência | **"repetir" editável + pausar/retomar** | Avulsa vira mensal; mensal para; recorrência pausa sem apagar. |
| Tags | **Desfazer na exclusão + recolorir + uma paleta só** | O mesmo snackbar dos lançamentos; a cor deixa de ser para sempre. |
| Telas vazias | **Board, tags e os três gráficos** | Cada uma diz o que falta para ter conteúdo. |

## Decisões que eu tomei

**1. "repetir: não" numa recorrência tem um significado só.** Parar de repetir é encerrar a
série a partir deste mês: o template ganha `fim` no mês anterior, esta linha vira um
lançamento avulso e as ocorrências futuras não editadas somem. Não há diálogo de escopo aqui
porque "só este mês parar de repetir" não quer dizer nada que o usuário reconheça — e a
alternativa (desligar a linha do template e deixar a série seguir) reabriria o buraco do
re-semeio de `DAQUI_EM_DIANTE`, que reinsere ocorrência em mês materializado sem instância.

**2. A tecla `,` vira `00`.** O teclado é de ponto de venda: os centavos são sempre os dois
últimos dígitos, e a vírgula é implícita. A tecla que faz sentido nesse esquema é o `00`,
que anexa dois zeros (`R$ 50` = `5`, `0`, `00`). Mesma posição, mesma cor, e passa a fazer
alguma coisa.

**3. Pausar não apaga o passado e não inventa o meio.** Pausar desliga `ativa` e apaga as
ocorrências *não editadas* do mês seguinte em diante nos meses já materializados; a do mês
corrente fica, porque já aconteceu. Retomar materializa os meses do intervalo com o
template ainda inativo (os outros templates ganham as suas linhas, este não), liga `ativa`
e semeia o mês corrente se ele já estava materializado sem instância. O intervalo fica
vazio para sempre, que é o que "pausada" quer dizer.

**4. Totais continua andando para o futuro.** O board é fato consumado e por isso a seta
para na data de hoje; totais projeta, e projetar dezembro em setembro é exatamente para o
que ele serve. Só o board ganha a seta desabilitada.

**5. A busca cobre o que o app mostra.** Os resultados vêm das movimentações *efetivas* do
saldo inicial até hoje — recorrências expandidas mesmo em mês nunca aberto — e não das
linhas cruas do banco. Tocar num resultado virtual (id 0) materializa o mês antes de abrir
a sheet, como o ledger já faz ao navegar.

## Comportamento

### Voltar

Um único `BackHandler` em `SaldoApp`, ativo enquanto a sheet está fechada (a sheet tem o
seu). Ordem de prioridade, a primeira que vale ganha:

1. `abrindoRecorrencias` → volta para totais.
2. Busca aberta → fecha a busca (limpa o texto).
3. Lista ou etiqueta aberta na aba saldos → volta para o board (limpa o filtro de tag).
4. Aba diferente de saldos → vai para saldos.
5. No board: o handler fica desabilitado e o sistema sai do app.

`CapturaScreen`, `LembretesScreen` e `RecorrenciasScreen` já têm `BackHandler` próprio e
continuam assim; o item 1 cobre o caso em que o handler da subtela não está composto.

### O board e a lista

`SaldoTopBar` do board ganha uma ação "ver como lista" (ícone de lista), o espelho do "ver
como grade" do ledger. O estado `abrindoTag: Boolean` de `SaldoApp` vira
`vistaSaldos: Board | Lista | Tag` (`rememberSaveable`): `Lista` é o `LedgerScreen` com os
chips `todas/diários/fixas` e sem filtro; `Tag` é o mesmo ledger com filtro. As duas vistas
compartilham o mês: ao alternar, `LedgerViewModel.irPara(boardState.mesAtual)` e, na
volta, `BoardViewModel.irPara(ledgerState.mesAtual)`. O mês do board continua limitado a
hoje; se a lista estava num mês futuro ao voltar para a grade, o board mostra o mês
corrente.

`SaldoTopBar` ganha `podeAvancar: Boolean = true`: com `false` a seta direita é desenhada
com a cor desabilitada do tema e sem `clickable`. O board passa `state.podeAvancar`, que já
existe em `BoardUiState` e hoje ninguém lê. `BoardViewModel.fecharDia`, sem chamadores,
sai.

O `+` da barra: `entryVm.iniciarNova(boardState.diaAberto ?: LocalDate.now())` quando a
vista é o board; hoje nas outras vistas e abas.

### Busca

Uma lupa na `SaldoTopBar` da vista `Lista` e `Tag`. Ao tocar, a barra vira um campo de
texto (foco e teclado automáticos, `×` para fechar). O texto vive em
`LedgerViewModel.busca: StateFlow<String>` via `SavedStateHandle`. Com texto não vazio,
`LedgerUiState.resultados: List<MesDeResultados>` substitui a lista do mês, e a navegação
de mês some da barra.

`domain/Busca.kt`, função pura `Busca.filtrar(efetivas: List<Movimentacao>, consulta: String,
hoje: LocalDate): List<Movimentacao>`:

- consulta normalizada: minúsculas, sem acento (`Normalizer.NFD` e remoção de
  `\p{M}`), espaços aparados;
- casa se a descrição normalizada contém a consulta, ou alguma tag da linha contém, ou a
  consulta é só dígitos/vírgula/ponto e o valor absoluto em centavos é igual a ela lida
  como reais (`340` → 34000; `16,90` → 1690; `1690` → 169000 **e** 1690);
- só linhas com `data <= hoje` (fato consumado, como o board);
- resultado ordenado por data decrescente.

A tela agrupa por `YearMonth` com um cabeçalho por mês ("setembro 2026") e reaproveita
`DayRow` (mesmas pills, mesmo swipe-para-apagar, mesma guarda de id 0 no delete). Tocar
numa linha com `id != 0` abre a sheet; com `id == 0`, `LedgerViewModel.abrirResultado(mov)`
chama `repo.abrirMes(YearMonth.from(mov.data))`, espera a próxima emissão do ledger, acha a
linha materializada por `(recorrenciaId, data)` e emite `alvo` para a sheet abrir.

Sem resultados: "nada com \"<consulta>\"".

### Recorrência pela sheet

Na edição, a linha "repetir" volta a ser um controle (hoje é um rótulo congelado,
`NewEntrySheet.kt:252-260`). Os três casos ao salvar, decididos em `EntryViewModel.salvar`
comparando `form.repetir` com o estado original:

| era | virou | o que acontece |
|---|---|---|
| `Nao` | `TodoMes(dia)` | `repo.converterEmRecorrencia(mov, dia)`: cria o template (`inicio = YearMonth.from(mov.data)`, `diaDoMes = dia`, demais campos da linha), liga a linha (`recorrenciaId`, `editadaManualmente = mov.data.dayOfMonth != dia`) e semeia todo mês já materializado `> inicio` que não tenha instância. |
| `TodoMes` | `Nao` | `repo.encerrarRecorrencia(mov)`: `template.fim = mês anterior` (se `fim` cair antes de `inicio`, o template é apagado), a linha vira avulsa (`recorrenciaId = null`, `editadaManualmente = false`) e `deleteInstanciasNaoEditadasAPartirDe(recId, mês seguinte)`. Sem diálogo de escopo. |
| `TodoMes(a)` | `TodoMes(b)` | Caminho existente `editar(DAQUI_EM_DIANTE)`, que agora recebe o dia pelo template e não pela data (ver `dados-1`, dia 31). O diálogo de escopo continua para os outros campos. |

`iniciarEdicao` passa a mostrar o dia do *template* (lido de `LedgerInput.recorrencias`
por `recorrenciaId`), não o dia da data materializada.

### Pausar e retomar

`RecorrenciasScreen`: cada linha ganha um `Switch` "pausada" (rótulo à esquerda, estado à
direita, mesma linha do valor). `SaldoRepository.pausar(id)` e `retomar(id, hoje)` com a
semântica da decisão 3. Uma recorrência pausada aparece na lista com o valor esmaecido e
o texto "pausada" no lugar do próximo vencimento; o motor (`RecurrenceExpander`) já pula
`ativa = false`. Export e restauração carregam `ativa` (já carregam).

### Tags

- `SaldoRepository.excluirTag(id): TagSnapshot` devolve a tag e as duas listas de vínculos
  (ids de movimentações e de recorrências); `restaurarTag(snapshot)` reinsere a tag com o
  mesmo id e os vínculos. `TagsViewModel.exclusoes: SharedFlow<TagSnapshot>`; `SaldoApp`
  mostra "tag excluída · desfazer" no mesmo `SnackbarHost`.
- O diálogo de renomear ganha uma linha de seis círculos de cor (o escolhido com anel);
  `SaldoRepository.recolorirTag(id, cor)` e `TagDao.recolor`.
- `ui/theme/PaletaTags.kt`: **uma** lista de seis cores (a do `TagsScreen`, que já tem
  seis) usada pela sheet e pela aba. A próxima cor de uma tag nova é a menos usada entre as
  seis, desempate pela ordem.
- Os botões `editar`/`excluir` da linha da tag viram `IconeRedondo` de 48 dp (isto também
  fecha parte do item de alvos de toque de `widgets-a11y-1`; fica aqui porque a linha é
  reescrita de qualquer jeito).

### Telas vazias

- **Board**, enquanto todo dia do mês está no tom neutro e não há movimentação no mês:
  abaixo da grade, no lugar da legenda, "toque em + para lançar o primeiro". A legenda
  volta assim que houver um dia pintado.
- **Tags**, sem nenhuma tag: acima de "nova tag", "uma tag é uma etiqueta: mercado, casa,
  lazer. Toque numa tag para ver só ela." O texto de rodapé atual some nesse estado.
- **Gráficos**: `TrendChart`, `PoupancaBars` e `ReservaLine` com menos de dois meses de
  dados dão lugar a "precisa de mais um mês" (mesma altura do canvas), e a legenda do
  segmento some junto. `RitmoChart` já tem "sem mês anterior". `TagsStack` sem tag no
  período: "sem tags neste período".

### Restauração de estado

- `BoardViewModel` (`mesAtual`, `diaAberto`), `LedgerViewModel` (`mesAtual`, `filtro`,
  `tagFiltroId`, `busca`) e `TotaisViewModel` (`mesAtual`, `segmento`) passam a receber
  `SavedStateHandle` (`createSavedStateHandle()` no `viewModelFactory`). `YearMonth` e
  `LocalDate` guardados como `Long`.
- `NewEntrySheet` (`pedindoEscopo`, `escolhendoData`, `escolhendoRepetir`,
  `escolhendoTags`, `editandoDescricao`, `pedindoExclusao`, `editandoValor`),
  `LedgerScreen`/`BoardScreen` (`faturaAberta`), `TagsScreen` (`criando`, `renomeando`,
  `excluindo`), `MaisScreen` (`editandoSaldo`) e `SaldoApp` (`escolhendoFormato`) trocam
  `remember` por `rememberSaveable`.
- `PrivacyState` ganha um `Saver` e `rememberPrivacyState` usa `rememberSaveable`: oculto
  continua oculto depois de girar. O valor inicial continua vindo de `comecarOculto` só na
  primeira composição.

### Diálogos

- Exportar: o corpo lista "csv — abre em planilha" e "json — o dump completo" como duas
  linhas clicáveis (`InsetRow`); `dismissButton` vira "cancelar"; `confirmButton` some.
- O diálogo de tags da sheet (`confirmButton = {}`) ganha "cancelar" como `dismissButton`
  (o toque fora já fecha; o botão é para o TalkBack e para quem não sabe).

## Estrutura

```
domain/Busca.kt                         novo — filtro puro
domain/PaletaTags.kt                    novo — as seis cores (substitui as duas listas)
data/SaldoRepository.kt                 converterEmRecorrencia, encerrarRecorrencia, pausar, retomar,
                                        excluirTag devolve snapshot, restaurarTag, recolorirTag
data/db/Daos.kt                         TagDao.recolor, MovimentacaoDao.desligarDaRecorrencia,
                                        vínculos por tag (para o snapshot)
ui/SaldoApp.kt                          BackHandler, vistaSaldos, FAB com dia aberto, snackbar de tag,
                                        diálogo de exportar
ui/components/M3.kt                     SaldoTopBar: podeAvancar, ação de lista, modo busca
ui/board/BoardScreen.kt                 toggle lista, vazio, seta desabilitada
ui/board/BoardViewModel.kt              SavedStateHandle; fecharDia sai
ui/ledger/LedgerScreen.kt               lupa, resultados agrupados, vazio da busca
ui/ledger/LedgerViewModel.kt            busca, resultados, abrirResultado, SavedStateHandle
ui/entry/AmountKeypadScreen.kt          tecla 00
ui/entry/NewEntrySheet.kt               repetir editável na edição, rememberSaveable, cancelar no diálogo
ui/entry/EntryViewModel.kt              os três casos de repetir; dia do template
ui/totais/RecorrenciasScreen.kt         switch pausada
ui/totais/RecorrenciasViewModel.kt      pausar/retomar
ui/totais/TotaisViewModel.kt            SavedStateHandle
ui/totais/charts/Charts.kt              estados vazios
ui/tags/TagsScreen.kt                   cor no renomear, vazio, botões de 48 dp
ui/tags/TagsViewModel.kt                exclusoes, recolorir
ui/privacy/Privacy.kt                   Saver
ui/mais/MaisScreen.kt                   rememberSaveable
```

Nenhuma mudança de schema do Room: `ativa` já existe, a busca lê o que já está em memória,
o snapshot de tag é montado por consulta.

## Fora de escopo

- Duplicar lançamento (decisão do usuário), seleção múltipla, deep links por URL.
- Desfazer para `editar(DAQUI_EM_DIANTE)` e para pausar (a retomada é o desfazer de pausar).
- Onboarding em cartões / dicas de gesto: o toggle de lista e o `00` resolvem o que mais
  confundia; o resto fica para quando houver mais de um usuário.
- Extrair strings para `strings.xml` — o app é pt-BR só, e isto seria uma fatia própria.
- Os alvos de toque da sheet e das abas, TalkBack e escala de fonte: `widgets-a11y-1`.

## Testes

**JVM (`app/src/test`)**
- `BuscaTest`: descrição sem acento casa com acento; nome da tag; valor em reais e em
  centavos; `data > hoje` não entra; ordem decrescente; consulta em branco devolve vazio.
- `PaletaTagsTest`: próxima cor é a menos usada; desempate pela ordem; seis cores.
- `RecurrenceExpanderTest`: template inativo não expande (já existe); o caminho de
  retomar semeia só o mês corrente (teste do repositório, abaixo).

**Instrumentados (`app/src/androidTest`)**
- `RepositoryTest`: `converterEmRecorrencia` liga a linha e semeia meses materializados
  posteriores; `encerrarRecorrencia` põe `fim`, desliga a linha e apaga futuras não
  editadas, e apaga o template quando `fim < inicio`; `pausar` apaga futuras e mantém a do
  mês; `retomar` deixa o intervalo vazio e semeia o mês corrente; `excluirTag` +
  `restaurarTag` devolvem os vínculos com o mesmo id; `recolorirTag`.
- `EntryFlowTest` (existente): `5`, `0`, `00` → R$ 50,00; `00` no zero continua zero; o
  teto de 99.999.999,99 continua respeitado.
- `SaldoAppBackTest`: recorrências → totais → saldos → (handler desabilitado); tag → board.
- `BoardScreenTest`: com dia 4 aberto, o `+` abre a sheet no dia 4; sem dia aberto, hoje;
  seta direita sem `hasClickAction` no mês corrente; vazio mostra "toque em +".
- `LedgerScreenTest`: lupa abre o campo; resultados agrupados por mês; tocar num resultado
  virtual materializa e abre a sheet.
- `NewEntrySheetTest`: repetir editável na edição; rotação (recreate) mantém o seletor de
  data aberto.
- `TagsScreenTest`: excluir mostra "desfazer" e desfazer traz a tag de volta; renomear
  troca a cor; vazio mostra a explicação.
- `PrivacyTest`: oculto sobrevive a `recreate()`.

A regra das notas anteriores continua: cliques de dígito escopados em `TAG_TECLADO`, nós
duplicados distinguidos por contagem ou `hasClickAction`, e os três testes ponta-a-ponta
que esperam "saldo projetado" trocam para a lista antes de exercitar o ledger — agora pelo
toggle novo, o que os simplifica.
