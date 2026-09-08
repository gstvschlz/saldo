# saldo — dados: restaurar, apagar, e o motor sem surpresas

**Data:** 2026-09-07 · **Branch:** `dados-1`, a criar de `main` depois do merge de
`uso-diario-1` · **Fatia 2 de 5** da rodada de endurecimento.
**Emendado em 2026-09-08** com o *backup automático*: ele grava o mesmo arquivo desta
fatia, nos mesmos arquivos de código, e separá-lo em fatia própria só faria duas mãos
editarem `Exporters.kt` e `mais/` na mesma semana.

## Objetivo

O saldo é um ralo de mão única: exporta, não importa. Com backup em nuvem desligado de
propósito, perder o aparelho é perder o histórico inteiro — e o JSON de hoje nem daria
para ler de volta, porque não tem ids nem o vínculo de recorrência. Esta fatia fecha o
ciclo (exportar → restaurar), dá ao usuário o "apagar dados" que faltava, e conserta o que
a auditoria de 2026-09-07 achou no motor: a recorrência de dia 31 que vira dia 28 para
sempre, a fatura de fevereiro sem carência, a compra no cartão anterior ao saldo inicial
que some da fatura, a estimativa de dezembro que conta cem dias, o saldo inicial que
apaga a história sem avisar, e as telas que congelam em silêncio quando o banco falha.

A emenda de 2026-09-08 fecha o resto do mesmo buraco: um ciclo que só funciona quando o
usuário lembra de exportar não é backup, é boa intenção. O **backup automático** grava o
arquivo desta fatia sozinho, numa pasta que ele escolhe — e se essa pasta for sincronizada,
o histórico sobrevive ao aparelho sem o app jamais tocar a rede.

## Decisões do usuário

| Decisão | Escolha | Consequência |
|---|---|---|
| Restaurar | **Substitui tudo; só schema 2** | Um diálogo avisa, o banco e os ajustes são trocados numa transação; o JSON de hoje (schema 1) é recusado com mensagem. Sem merge. |
| CSV | **O que o app mostra** | Movimentações efetivas do saldo inicial até hoje, recorrências expandidas mesmo em mês nunca aberto. O JSON continua o dump fiel, porque é o que restaurar precisa. |
| Saldo inicial | **Avisa e deixa escolher a data** | O diálogo mostra quantos lançamentos saem das contas com a data escolhida; manter a data original custa zero. |
| Apagar dados | **Sim, com confirmação digitada** | Escrever "apagar" libera o botão; tudo some e o app volta ao onboarding. |
| Backup | **Auto-export para uma pasta escolhida** | O app grava o JSON sozinho na pasta que você apontar uma vez. Se ela for sincronizada, o arquivo chega à conta do Google sem o app tocar a rede. Sem notificação de falha. |

## Decisões que eu tomei

**1. O CSV vai até hoje, não até o fim do mês.** "O que o app mostra" tem dois candidatos:
o board (até hoje, fato consumado) e totais (o mês inteiro, projetado). Escolhi o board: uma
planilha com linhas que ainda não aconteceram mistura fato e previsão sem nenhuma coluna
que os separe. Quem quer o futuro tem `a caminho` na tela e o JSON no arquivo.

**2. Restaurar grava o banco antes dos ajustes.** Room e DataStore não compartilham
transação. A ordem é: validar tudo em memória → transação do Room (apaga e insere) →
ajustes. Se a transação falhar, nada mudou. Se os ajustes falharem depois do commit (caso
raro: disco cheio), o banco já é o do arquivo e a mensagem diz "dados restaurados, ajustes
não — confira saldo inicial e cartão em mais".

**3. Nenhuma migração do Room.** O schema do arquivo (2) e o schema do banco (3) são
números diferentes de coisas diferentes. Nada nesta fatia acrescenta coluna: `ativa`,
`criadaEm` e `editadaManualmente` já existem. A cadeia de migração 1→3 ganha um teste,
não uma migração.

**4. Compra no cartão anterior ao saldo inicial conta na fatura que vence depois dele.**
O saldo inicial é "quanto tenho hoje", e a fatura aberta ainda não foi paga — as compras
dela pesam no vencimento, tenham sido feitas antes ou depois da âncora. Diário e economia
continuam cortados pela data da movimentação. Com a data escolhível no diálogo, este caso
deixa de ser teórico.

**5. O erro de leitura vira estado, não log.** Cada ViewModel que hoje faz
`.catch { Log.e }` passa a emitir `erro` no seu estado; a tela mostra uma linha e um
"tentar de novo" que reassina o fluxo. Carregando continua um retângulo, mas com um
`CircularProgressIndicator` depois de 300 ms — o suficiente para não piscar num aparelho
rápido e para dizer "estou vivo" num lento.

**6. O backup é um export automatizado, não um backup novo.** Ele grava exatamente o
`Exporters.json` desta fatia, na pasta que o usuário apontou com `OpenDocumentTree` e cuja
permissão o app persistiu. `allowBackup="false"` e os três atributos do manifesto ficam
onde estão: o app não fala com a rede, não conhece conta nenhuma, e quem sincroniza a
pasta — se alguém sincroniza — é o aparelho. A frase do README continua verdadeira letra
por letra, e é por isso que este caminho foi escolhido no lugar do Auto Backup do Android.

**7. Sete arquivos datados, e a rotação só apaga os seus.** `saldo-AAAA-MM-DD.json`, um por
dia em que o backup rodar; ao gravar, o worker apaga o que passar de sete — e só o que casa
com esse padrão exato. Um nome fixo sobrescrito seria mais simples e deixaria um export
corrompido comer o único arquivo bom; apagar qualquer coisa que esteja na pasta seria o app
se achando dono de uma pasta que é do usuário.

**8. Grava com sufixo e renomeia.** O JSON inteiro é montado em memória (já é uma `String`),
escrito em `saldo-AAAA-MM-DD.json.parcial` e só então renomeado. Um backup pela metade que
*parece* válido é pior que backup nenhum — e é o que uma escrita interrompida deixa.

**9. A falha mora na tela, não numa notificação** — a escolha foi dele. A linha de `backup
automático` mostra a data do último sucesso e, quando a última tentativa falhou, o motivo em
uma linha. Erro de I/O é `Result.retry()`. `SecurityException` (permissão da pasta revogada,
pasta apagada) é diferente: desliga o backup e troca a linha por "escolha a pasta de novo",
porque um agendamento que não pode gravar não deve tentar para sempre em silêncio.

## Comportamento

### Exportar — schema 2

`Exporters.json` passa a escrever:

```
{
  "schema": 2,
  "exportadoEm": "2026-09-07T10:12:00-03:00",
  "app": "0.5.0",
  "settings": {
    saldoInicialCentavos, saldoInicialData, cartaoNome, cartaoFechamentoDia, cartaoVencimentoDia,
    comecarOculto, tema, widgetMostrarValores,
    lembretes: { faturaAmanha, recorrenciaHoje, registrarGastos, fechamentoMes, horaInformativos, horaNudge },
    captura: { ligada, marcados: [...], vistos: [...], perguntados: [...], recusados: [...] }
  },
  "tags": [ { id, nome, cor } ],
  "recorrencias": [ { id, descricao, valorCentavos, natureza, diaDoMes, inicio, fim, ativa, tagIds } ],
  "movimentacoes": [ { id, descricao, valorCentavos, data, natureza, recorrenciaId, editadaManualmente, criadaEm, tagIds } ],
  "mesesMaterializados": [ "2026-08", "2026-09" ]
}
```

`perguntados` e `recusados` chegam com `captura-2`; até lá o exportador escreve listas
vazias e o leitor aceita a chave ausente. A tabela de detecções (24 h) não entra. A leitura
é `Importers.json(texto): Dump` no mesmo `data/` que os exportadores, com `org.json`, e é
a **única** função que conhece o formato do arquivo.

`Exporters.csv` recebe `ProjectionEngine.movimentacoesAte(input, YearMonth.from(hoje))`
filtrado por `data <= hoje` — a função existe e hoje não tem chamador — em vez de
`input.movimentacoes`. Colunas iguais às de hoje.

### Backup automático

`mais → exportar dados` ganha a vizinha `backup automático`, com o resumo na própria linha:
`semanal · último em 07/09`, `desligado`, ou `falhou em 07/09`. A subtela tem três controles
e um estado:

- **pasta** — `ActivityResultContracts.OpenDocumentTree`; na volta,
  `takePersistableUriPermission` com `FLAG_GRANT_WRITE_URI_PERMISSION`, e o `Uri` guardado
  como texto no DataStore. A linha mostra o nome legível da pasta.
- **quando** — `diário`, `semanal` (padrão) ou `mensal`, às 03:00.
- **agora** — grava na hora. Sem isto o usuário liga o backup e fica uma semana sem saber se
  funciona; com isto ele vê o arquivo aparecer antes de precisar confiar nele.
- **estado** — último sucesso, e o último erro quando houve.

Sem pasta escolhida, `quando` e `agora` ficam desabilitados e nada é agendado.

O agendamento copia o `LembretesScheduler` à risca: um `OneTimeWorkRequest` para a próxima
ocorrência, reagendado pelo próprio worker ao terminar (`APPEND_OR_REPLACE`, porque roda de
dentro do próprio unique work), inexato de propósito, sem alarme exato e sem receiver de
boot — o WorkManager sobrevive ao reboot sozinho.

`BackupWorker`, na ordem: lê ledger e ajustes uma vez → monta o `Exporters.json` → grava
`saldo-AAAA-MM-DD.json.parcial` → renomeia para `saldo-AAAA-MM-DD.json` → apaga os arquivos
do próprio padrão além dos sete mais recentes → grava `backupUltimoSucesso`, limpa
`backupUltimoErro` → reagenda.

O acesso à árvore fica atrás de `PastaBackup` (criar, renomear, listar, apagar) — uma
interface, com `PastaSaf` por cima do `DocumentsContract` e uma implementação de pasta
temporária nos testes. Sem ela o worker só seria testável num aparelho com uma árvore SAF de
verdade, que é exatamente o tipo de teste que ninguém roda.

### Restaurar

`mais → restaurar dados`, linha logo abaixo de "exportar dados". `OpenDocument("application/json")`.
Fluxo:

1. Ler o `Uri` em `Dispatchers.IO`; até 16 MB, acima disso "arquivo grande demais".
2. `Importers.json`: `schema != 2` → "este arquivo é de uma versão antiga do saldo; exporte
   de novo na versão atual". Campo obrigatório ausente, natureza desconhecida, data que não
   lê, `tagId`/`recorrenciaId` que não existe no próprio arquivo, id repetido → "arquivo
   inválido: <o quê>". Qualquer falha aqui não toca em nada.
3. Diálogo: "substituir tudo neste aparelho por 412 lançamentos, 6 recorrências e 9 tags,
   exportados em 7 de setembro de 2026?" com cancelar / substituir.
4. `SaldoRepository.substituirTudo(dump)` em `db.withTransaction`: `clearAllTables` não
   funciona dentro de transação, então é `deleteAll` em cada DAO (ordem: cruzamentos,
   movimentações, recorrências, tags, meses, detecções) e depois `insert` com os ids do
   arquivo (Room respeita `id != 0` em `@Insert`). Falha → rollback → "não deu para
   restaurar: <motivo>".
5. `SettingsStore.substituir(dump.settings)` (um `edit` que limpa e grava tudo).
6. `LembretesScheduler.sincronizar()` e `NotificacaoSugestao.cancelarTodas()`; os widgets
   se atualizam pelo fluxo do ledger.
7. Snackbar "dados restaurados". O onboarding não aparece porque o saldo inicial veio no
   arquivo.

### Apagar dados

`mais → apagar dados`, última linha da tela, em vermelho. Diálogo: "isto apaga todos os
lançamentos, recorrências, tags e ajustes deste aparelho. Escreva **apagar** para
confirmar." Campo de texto; o botão "apagar" só habilita quando o texto, aparado e em
minúsculas, é `apagar`. Ao confirmar: `repository.apagarTudo()` (mesma transação de
`deleteAll` acima), `settings.limpar()`, `LembretesScheduler.cancelarTudo()`,
`NotificationManager.cancelAll()`. O `SaldoApp` vê `saldoInicialCentavos == null` e volta
ao teclado de onboarding sozinho.

### Saldo inicial

Em `MaisScreen`, depois do teclado de "saldo inicial" (`editandoSaldo`), em vez de gravar
direto abre um diálogo:

> saldo de **R$ 1.204,00** a partir de [ 07/09/2026 ]
> 37 lançamentos anteriores a 7 de setembro saem das contas.

A data abre o `DatePicker` do M3; o padrão é hoje. A contagem vem de
`MaisViewModel.anterioresA(data)` = quantidade de linhas de `input.movimentacoes` com
`data < escolhida` (só linhas reais; as virtuais não têm o que perder), recalculada a cada
troca de data; com zero, a linha some. `definirSaldoInicial(centavos, data)` já aceita a
data. Cancelar não grava.

### Motor e repositório

Cada item tem o seu teste (ver *Testes*); todos são funções puras ou métodos de
repositório já cobertos por `RepositoryTest`.

1. **Dia 31.** `editar(DAQUI_EM_DIANTE)` recebe `diaDoMes` explícito do `EntryViewModel`
   (que já mostra o dia do template desde `uso-diario-1`); o repositório não deriva mais
   de `mov.data.dayOfMonth`. Ao salvar, o ViewModel manda o dia do formulário — igual ao
   do template se o usuário não mexeu.
2. **Carência de fevereiro.** `FaturaCalculator` compara `vencimento` e `fechamento` já
   clamped ao mês; com fechamento 30 / vencimento 31 em fevereiro, os dois caem no dia 28
   e a fatura vence no mês seguinte, como nos outros meses.
3. **Fatura antes da âncora.** `ProjectionEngine.efetivas` corta `DIARIO` e `ECONOMIA`
   por `data >= saldoInicialData` e `CARTAO` por `vencimento(fatura) >= saldoInicialData`.
4. **Estimativa.** `estimativaDoMes` usa `max(hoje, mes.atDay(1))` como início da janela.
5. **Tags pelo motor.** `TagsViewModel` soma `ProjectionEngine.movimentacoesDoMes` em vez
   de `input.movimentacoes`.
6. **Snapshot de exclusão.** `EntryViewModel` guarda a `Movimentacao` recebida em
   `iniciarEdicao` e é ela que vai para `_exclusoes`, não o formulário.
7. **Cartão na leitura.** `SettingsStore` clampa `fechamentoDia` e `vencimentoDia` a
   `1..31` ao ler, como `hora()` já faz; fora da faixa cai no padrão.
8. **Leituras dentro de transação.** `RecorrenciaDao.todos()` e `MesMaterializadoDao.todos()`
   (já existe) substituem `observeAll().first()` dentro de `withTransaction`; em
   `editar(DAQUI_EM_DIANTE)` os templates são lidos uma vez antes do laço de meses.

### Erro e carregamento

`LedgerViewModel`, `BoardViewModel`, `TotaisViewModel`, `RecorrenciasViewModel`,
`TagsViewModel` e `MaisViewModel`: o `.catch` passa a emitir `state.copy(erro = mensagem)`
e o fluxo é reconstruído por um `MutableStateFlow<Int>` de tentativas que o "tentar de
novo" incrementa (`flatMapLatest`). Componente único `ErroDeLeitura(mensagem, onTentar)` em
`ui/components`, com a linha "algo deu errado ao ler os dados" e o botão. Carregando:
`Carregando()` em `ui/components`, um `Box` que só mostra o indicador depois de 300 ms
(`LaunchedEffect` + `delay`).

### Recompute

- `RoomSaldoRepository.ledger`: `combine` sobre `settingsStore.settings.map { AjustesDoLedger(saldoInicial, data, cartao) }.distinctUntilChanged()`
  e `.distinctUntilChanged()` no resultado.
- `LedgerInput` ganha `val efetivas: List<Movimentacao> by lazy { ProjectionEngine.expandir(this) }`
  (calculada uma vez por emissão, até o último mês materializado ou o mês de `hoje` mais
  doze — o teto que `movimentacoesAte` já usa); `mes`, `totais`, `faturasAte`,
  `movimentacoesDoMes` e `movimentacoesAte` leem dela em vez de re-expandir.
- `TotaisViewModel`: `state` deriva de `combine(ledger, mesAtual, segmento)` e só calcula
  o segmento selecionado; os outros dois ficam `null` até serem escolhidos.

## Estrutura

```
data/Importers.kt                       novo — Importers.json(texto): Dump, validação
data/Exporters.kt                       schema 2; csv a partir das efetivas
data/Dump.kt                            novo — o modelo do arquivo (settings + listas)
data/SaldoRepository.kt                 substituirTudo, apagarTudo, todos(), dia explícito no DAQUI_EM_DIANTE
data/SettingsStore.kt                   substituir(dump), clamp do cartão, limpar() completo
data/db/Daos.kt                         deleteAll por DAO, RecorrenciaDao.todos, insert com id
domain/ProjectionEngine.kt              expandir, efetivas via LedgerInput, corte por vencimento, estimativa
domain/FaturaCalculator.kt              comparação clamped
ui/SaldoApp.kt                          OpenDocument, diálogo de restaurar, snackbar
ui/mais/MaisScreen.kt                   restaurar, apagar, diálogo do saldo inicial
ui/mais/MaisViewModel.kt                anterioresA, apagarTudo, restaurar
ui/components/Estados.kt                novo — ErroDeLeitura, Carregando
ui/*/…ViewModel.kt                      erro + tentar de novo; TotaisViewModel por segmento
ui/tags/TagsViewModel.kt                soma pelo motor
ui/entry/EntryViewModel.kt              snapshot original; dia do template no salvar
lembretes/LembretesScheduler.kt         cancelarTudo, sincronizar
captura/NotificacaoSugestao.kt          cancelarTodas
backup/PastaBackup.kt                   novo — interface (criar, renomear, listar, apagar) + PastaSaf
backup/BackupScheduler.kt               novo — próxima ocorrência por cadência, unique work
backup/BackupWorker.kt                  novo — monta, grava .parcial, renomeia, rotaciona, reagenda
ui/mais/BackupScreen.kt                 novo — pasta, quando, agora, último sucesso/erro
```

## Fora de escopo

- Merge de dois arquivos, leitura de schema 1, importação de CSV.
- Backup em nuvem *pelo app* — conta, rede, API do Drive: continua fora, e é o que o
  auto-export existe para não precisar. Restaurar automático (o app achar o arquivo sozinho
  e oferecer) também fica fora: restaurar substitui tudo, e isso é um ato deliberado.
- Lembrete de "exporte de vez em quando": o backup automático o torna desnecessário.
- Desfazer para `editar(DAQUI_EM_DIANTE)`.
- Paginação do ledger e o índice em `dataEpochDay`: o `distinctUntilChanged` e a expansão
  única resolvem o custo que existe hoje; janela de consulta por data e índice (que
  exigiria migração) só quando um histórico real ficar lento.
- Segunda conta ou segundo cartão.

## Testes

**JVM**
- `ExportersTest`: schema 2 com todos os campos; `criadaEm`/`editadaManualmente`
  preservados; CSV a partir das efetivas inclui a recorrência de um mês nunca aberto e
  exclui `data > hoje`.
- `ImportersTest`: ida e volta (`json → Dump → json` igual); schema 1 recusado com a
  mensagem certa; `tagId` inexistente, `recorrenciaId` inexistente, id repetido, natureza
  desconhecida, data inválida, campo ausente — cada um com a sua mensagem; chaves
  `perguntados`/`recusados` ausentes aceitas.
- `FaturaCalculatorTest`: fechamento 30 / vencimento 31 em fevereiro vence em março;
  `vencimento == fechamento` depois do clamp.
- `ProjectionEngineTest`: compra no cartão antes da âncora com fatura vencendo depois
  conta; a mesma compra com fatura vencida antes da âncora não conta; diário antes da
  âncora não conta; estimativa de dezembro vista de setembro cobre só dezembro;
  `efetivas` calculada uma vez por `LedgerInput` (contador no expansor).
- `RecurrenceExpanderTest`: dia 29 em fevereiro bissexto e não bissexto; `fim == mes`
  inclusivo.
- `SettingsStoreTest` (JVM com `DataStore` em arquivo temporário): dia de cartão 0 e 32
  caem no padrão.
- `TagsViewModelTest`: total de uma tag inclui a recorrência virtual e exclui o que é
  anterior à âncora.

- `BackupSchedulerTest`: a próxima ocorrência de cada cadência a partir de uma hora fixa
  (diário atravessando a meia-noite, semanal, mensal no dia 31); sem pasta, nada é agendado.
- `RotacaoTest`: nove arquivos do padrão mais dois de outro nome — sobram os sete mais
  recentes, e os dois estranhos ficam intactos.

**Instrumentados**
- `RepositoryTest`: `substituirTudo` deixa exatamente o conteúdo do dump, com os ids;
  um dump com vínculo quebrado no meio da inserção faz rollback e o banco fica como estava;
  `apagarTudo` esvazia todas as tabelas; `editar(DAQUI_EM_DIANTE)` com dia 31 editado em
  fevereiro mantém 31 no template.
- `MigrationTest`: a cadeia 1→3 num só banco (hoje só há os pares 1→2 e 2→3).
- `MaisScreenTest`: restaurar com arquivo schema 1 mostra a mensagem e não abre o diálogo;
  o diálogo do saldo inicial recalcula a contagem ao trocar a data; "apagar" só habilita
  com o texto certo e leva ao onboarding.
- `EstadosTest`: `ErroDeLeitura` aparece quando o fluxo falha (repositório falso que lança)
  e "tentar de novo" reassina.
- `EntryFlowTest`: editar o valor e depois excluir; desfazer traz o valor original.
- `BackupWorkerTest` (`work-testing` + `PastaBackup` de pasta temporária): grava o arquivo do
  dia e o `.parcial` some; o conteúdo volta pelo `Importers` idêntico ao dump; uma falha de
  escrita deixa o último sucesso intacto e devolve `retry`; `SecurityException` desliga o
  backup e não reagenda; rodar duas vezes no mesmo dia deixa um arquivo só.
- `BackupScreenTest`: sem pasta, `quando` e `agora` desabilitados; com pasta, o resumo mostra
  a data do último sucesso; um erro gravado aparece na linha.
