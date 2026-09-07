# saldo — captura 2: três botões, o listener que volta, e o primeiro gasto

**Data:** 2026-09-07 · **Branch:** `captura-2`, a criar de `main` depois do merge de
`dados-1` · **Fatia 3 de 4** da rodada de endurecimento.

## Objetivo

A captura de notificações (v0.2.0 a v0.4.0) lê o valor e sugere lançar. A auditoria de
2026-09-07 achou cinco jeitos de ela lançar dinheiro errado ou não lançar nada, todos
conferidos no fonte:

- **toda sugestão é saída** — "você recebeu um Pix de R$ 200" e "estorno de R$ 89,90"
  viram −R$ 200 e −R$ 89,90 num toque; "R$ -50,00" nem é lido (a regex exige dígito logo
  depois do `R$`), e "R$ 100,00" com espaço duro (U+00A0) também não;
- **o digest lê uma linha só** — `TextoNotificacao` junta as linhas do `InboxStyle`, mas
  `primeiroValorEmCentavos` para no primeiro valor; três compras viram uma sugestão;
- **o título vem antes do corpo** — um banco que põe o saldo da conta no título ganha uma
  sugestão de lançar o saldo como gasto;
- **o listener morre calado** — sem `onListenerDisconnected`, sem `requestRebind`, sem
  reler o que ficou na barra ao reconectar; a tela diz "ligado" enquanto nada é lido;
- **o primeiro gasto só cadastra o app** — e a sugestão só vem no segundo; no Android 13+
  a captura nunca pede a permissão de notificação, então pode estar tudo ligado e nada
  aparecer.

## Decisões do usuário

| Decisão | Escolha | Consequência |
|---|---|---|
| Direção do valor | **Três botões: saída / entrada / ignorar** | Contra a minha recomendação (detectar palavras de entrada). Nenhuma heurística de sinal; o usuário decide na barra. |
| Digest | **Uma sugestão por linha, até 5** | Cada linha com valor vira a sua notificação, com o seu estabelecimento. |
| Descoberta | **Pergunta uma vez, na barra** | "vi um gasto de R$ 16,90 no Nubank. Ler este app?" com ler / não; "ler" marca e já sugere. |
| Natureza por app | **Não; continua diário para todos** | O risco de contagem dobrada no cartão segue aceito (decisão de 2026-09-04). |

## Decisões que eu tomei

**1. O sinal no texto é lido e descartado.** A regex aceita `-`/`+`/`−` antes do `R$` ou
do número para que "R$ -50,00" vire 5000 centavos em vez de nada. O sinal **não**
pré-seleciona botão: o usuário escolheu decidir sempre, e uma notificação com três botões
onde um está "sugerido" seria a heurística voltando pela janela.

**2. O campo com palavra de transação ganha.** Numa notificação simples (sem linhas de
digest), o candidato vem do primeiro campo, na ordem *corpo, texto expandido, subtítulo,
título*, que contenha uma palavra de transação: `compra`, `pix`, `débito`, `debitado`,
`pagamento`, `pago`, `transferência`, `saque`, `estorno`, `recebeu`, `recebido`. Se nenhum
campo tem, vale a ordem acima (corpo antes do título — a inversão de hoje). Só o primeiro
valor do campo escolhido conta, pela mesma razão de sempre: o segundo costuma ser o saldo.

**3. A janela de duplicata é por valor, sem pacote.** Banco e carteira do mesmo banco são
dois pacotes avisando a mesma compra ("R$ 16,90" e "BRL 16.90", o par que
`DetectorValorTest` já documenta). Duas compras *diferentes* do mesmo valor em dez minutos
vindas de dois apps marcados é raro o bastante para perder.

**4. Ao reconectar, o listener relê a barra.** `onListenerConnected` passa por
`activeNotifications` as postadas nas últimas 24 h, pelo mesmo caminho de
`onNotificationPosted`, pulando toda `chave` que já esteja na tabela de detecções (que
vive 24 h justamente). É o que recupera a compra feita enquanto o app estava morto por
atualização ou por OEM agressivo — sem reprocessar o que o usuário já resolveu.

**5. A descoberta continua atrás do interruptor.** Com a captura desligada o serviço não
anota pacote nenhum: "desligado" quer dizer "não faz nada". O que muda é que o primeiro
gasto de um app não marcado, com a captura ligada, deixa de ser perdido — vira a pergunta.

## O fluxo

```
notificação chega (ou é relida ao conectar)
   │
   ├─ do próprio saldo, ou sumário de grupo? ──────────► descarta
   ├─ captura desligada? ───────────────────────────────► descarta
   ├─ sem valor em nenhum campo? ────────────────────────► descarta
   │
   ├─ candidatos = linhas do digest com valor (até 5),
   │               ou o campo escolhido (decisão 2), 1 candidato
   │
   ├─ app não marcado?
   │     ├─ já perguntado ou recusado? ──────────────────► anota "visto", descarta
   │     └─ senão ────────────────────────────────────────► notificação "ler este app?"
   │                                                         (candidatos viajam no intent)
   │
   └─ para cada candidato:
         ├─ valor visto nos últimos 10 min (qualquer app)? ► atualiza a sugestão existente
         ├─ já existe movimentação desse valor hoje? ──────► "já lançado hoje · lançar mesmo assim?"
         └─ senão ─────────────────────────────────────────► sugestão nova

sugestão: "R$ 16,90 · VMT*CAROLINA"   [saída] [entrada] [ignorar]   toque no corpo → sheet
```

## Comportamento

### Leitura

`captura/TextoNotificacao.kt` devolve `CamposDaNotificacao(titulo, corpo, expandido,
subtitulo, linhas: List<String>)` em vez de uma `String`. `DetectorValor` ganha
`valoresEmCentavos(texto): List<Long>` (todos os valores, na ordem) e a regex passa a ser

```
(?:[-+−]\s*)?(?:(?:R\$|\bBRL)[\s  ]*[-+−]?[\s  ]*(\d[\d.,]*)|(\d[\d.,]*)[\s  ]*BRL\b)
```

`primeiroValorEmCentavos` continua existindo como `valoresEmCentavos(...).firstOrNull()`.

`domain/Candidatos.kt`, puro: `Candidatos.de(campos): List<Candidato>` com
`Candidato(indice, centavos, estabelecimento)`. Digest: uma linha com valor → um candidato,
estabelecimento por `DetectorDescricao.descricao(linha)`, no máximo 5 (as primeiras).
Sem linhas: o campo da decisão 2, primeiro valor, estabelecimento do mesmo campo.

### Sugestão com três botões

`NotificacaoSugestao.mostrar(deteccao, indice, estabelecimento, jaLancado)`: id da
notificação = `hash(chave, indice)` estável; título "R$ 16,90 · VMT*CAROLINA" (ou "R$ 16,90 ·
Nubank" sem estabelecimento); texto "já lançado hoje · lançar mesmo assim?" quando for o
caso; ações **saída**, **entrada**, **ignorar**. O corpo abre `Destino.NovaMovimentacao(centavos,
descricao, saida = null)`.

`AcoesSugestao` recebe `EXTRA_DIRECAO ∈ {SAIDA, ENTRADA}`; grava `valorCentavos = ±centavos`,
`Natureza.DIARIO`, data de hoje, descrição = estabelecimento ou nome do app — nada mais
muda no lançamento. `ignorar` marca a detecção como resolvida, como hoje.

Cada candidato grava a sua própria `Deteccao` (mesma `chave`, centavos próprios); o id da
linha viaja no `PendingIntent` de cada ação, então o índice do digest não precisa de
coluna. Nenhuma mudança de schema do Room nesta fatia — nem em nenhuma da rodada.

`SugestaoEngine.avaliar`: a janela compara só `centavos` e `emMillis`, sem `pacote`.
`valoresDeHoje` passa a vir de `MovimentacaoDao.valoresAbsolutosNoDia(epochDay)` — uma
consulta, sem expandir o ledger.

### O listener

```kotlin
override fun onListenerConnected() {
    conectado.value = true
    reler()   // activeNotifications, postTime >= agora - 24 h, pulando chaves já detectadas
}
override fun onListenerDisconnected() {
    conectado.value = false
    requestRebind(ComponentName(this, EscutaNotificacoes::class.java))
}
```

`conectado` é um `MutableStateFlow<Boolean>` no `AppContainer` (memória, nunca disco).
`CapturaScreen` mostra três estados na linha de acesso:

| acesso | conectado | linha |
|---|---|---|
| não | — | "sem acesso · abrir ajustes" (como hoje) |
| sim | não | "permitido, mas desconectado · religar" → `requestRebind` |
| sim | sim | "conectado" |

### Descoberta

`CapturaConfig` ganha `perguntados: Set<String>` e `recusados: Set<String>` (DataStore, e
o export/restore de `dados-1` já os prevê). Primeiro gasto de um app não marcado nem
perguntado nem recusado, com a captura ligada:

- `NotificacaoSugestao.perguntar(pacote, rotulo, candidatos)`: "vi um gasto de R$ 16,90 no
  Nubank. Ler este app?" com **ler** / **não**; id fixo por pacote (uma pergunta viva por
  app); os candidatos viajam serializados no `PendingIntent` (centavos, estabelecimento,
  indice, chave), nunca no disco;
- **ler**: `marcar(pacote)` + `perguntados += pacote`, e cada candidato do intent passa por
  `avaliar` como se tivesse acabado de chegar;
- **não**: `recusados += pacote`, `vistos += pacote`; a tela lista o app com o texto
  "recusado · marcar mesmo assim", que marca e tira de recusados;
- ao perguntar, `perguntados += pacote` na hora (não só no "ler"), para que uma segunda
  compra antes da resposta não empilhe perguntas.

### Permissão e manifesto

- `CapturaScreen`: ligar o interruptor no Android 13+ sem `POST_NOTIFICATIONS` pede a
  permissão (mesmo `rememberLauncherForActivityResult` de `LembretesScreen`); negada, o
  interruptor liga mesmo assim (o listener não depende dela) e a linha abaixo diz "sem
  permissão de notificar, as sugestões não aparecem · abrir ajustes".
- `AndroidManifest.xml` ganha `<queries><intent><action MAIN/><category LAUNCHER/></intent></queries>`:
  apps com ícone na gaveta passam a resolver o rótulo no Android 11+.
- O texto de rodapé da tela ganha a frase: "a sugestão na barra é uma notificação como
  qualquer outra: outro app com acesso a notificações também a lê".

## Estrutura

```
domain/Candidatos.kt                    novo — campos → candidatos
domain/DetectorValor.kt                 valoresEmCentavos, sinal e espaço duro
domain/Captura.kt                       CapturaConfig.perguntados/recusados
domain/SugestaoEngine.kt                janela por valor
data/db/Daos.kt                         MovimentacaoDao.valoresAbsolutosNoDia
data/SettingsStore.kt                   perguntados, recusados, marcar/recusar
captura/TextoNotificacao.kt             CamposDaNotificacao
captura/EscutaNotificacoes.kt           candidatos, reler, conectado, rebind, pergunta
captura/NotificacaoSugestao.kt          três ações, perguntar, ids por (chave, indice)
captura/AcoesSugestao.kt                EXTRA_DIRECAO; ler/não da pergunta
SaldoApplication.kt / AppContainer      conectado
ui/mais/CapturaScreen.kt                três estados, permissão, recusados, rodapé
AndroidManifest.xml                     <queries>
README.md                               três botões, pergunta, digest
```

## Fora de escopo

- Detectar entrada por palavra (decisão do usuário), natureza por app (idem), dólar.
- Agrupar pacotes "irmãos" à mão; a janela por valor cobre o caso comum.
- Ler notificações de antes de o acesso ser concedido (o Android não entrega).
- Sugerir pelo histórico ("você costuma lançar R$ 16,90 como mercado").

## Testes

**JVM**
- `DetectorValorTest`: `R$ -50,00` → 5000; `+R$ 50,00` → 5000; `R$ 100,00` → 10000;
  `R$ 1.234,56` → 123456; `valoresEmCentavos("R$ 10 e R$ 20")` → `[1000, 2000]`.
- `CandidatosTest`: digest de 3 linhas → 3 candidatos com o estabelecimento de cada linha;
  7 linhas → 5; título "saldo R$ 1.204,00" + corpo "compra R$ 32,90" → um candidato de
  3290; sem palavra de transação, o corpo vence o título; linha sem valor é pulada.
- `SugestaoEngineTest`: mesmo valor de pacotes diferentes em 10 min → `Repetida`;
  resolvida não ressuscita.
- `DescobertaTest` (função pura `Descoberta.decidir(config, pacote)`): não marcado e nunca
  perguntado → perguntar; perguntado → só anotar visto; recusado → só anotar visto; marcado
  → sugerir.
- `ReleituraTest` (função pura sobre a lista de `(chave, postTime)` e as chaves já
  detectadas): pula chave conhecida; pula `postTime` com mais de 24 h.

**Instrumentados**
- `TextoNotificacaoTest`: os campos saem separados; `InboxStyle` preenche `linhas`.
- `AcoesSugestaoTest`: **saída** grava negativo, **entrada** grava positivo, **ignorar**
  resolve — pelo `PendingIntent` de verdade, como a nota de 2026-09-04 manda; a pergunta
  com **ler** marca o app e posta a sugestão; com **não** anota recusado.
- `NotificacaoSugestaoTest`: ids distintos por linha do digest; três ações presentes.
- `CapturaScreenTest`: os três estados da linha de acesso (`CapturaConteudo` com o acesso
  e o `conectado` hoisted, como já é); ligar sem permissão mostra a linha de aviso;
  "recusado · marcar mesmo assim" marca.
- Rótulo de pacote: `rotuloDe("com.android.settings")` devolve "Configurações" (ou o
  rótulo do emulador), não o nome do pacote.
