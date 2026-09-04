# saldo — notificações: ler o valor e sugerir lançar

**Data:** 2026-09-04 · **Branch:** `notificacoes-1`, a criar de `board-1`

## Objetivo

O saldo depende inteiramente de você lembrar de registrar. É o atrito central do app, e é
o que os quatro lembretes locais tentaram atacar de fora — cutucando no horário certo, sem
saber de nada.

Este projeto ataca por dentro: quando um app que você escolheu emite uma notificação com
um valor em reais, o saldo emite a **sua própria** notificação, com um botão que lança a
movimentação sem abrir o app.

Nenhum dado sai do aparelho. Nenhuma rede é usada. A promessa do "100 % local" continua
literal — o que muda é a superfície de permissão, e ela muda bastante (ver *O custo*).

## Decisões do usuário

| Decisão | Escolha | Consequência |
|---|---|---|
| Ao detectar | **Notificação com botões** "lançar" e "ignorar" | Resolve na barra de status. Tocar no corpo abre a sheet já preenchida, para tag e descrição. |
| Escopo | **Só os apps que você marcar** | Notificação de app não marcado não vira sugestão nem toca o disco; o único vestígio é o nome do pacote entrando na lista de "vistos". |
| Dólar | **Ignorado; só R$** | Sem rede não há cotação, e uma cotação velha mentiria no saldo. O pedido original citava USD; ele optou por cortar. |
| Natureza | **Sempre `DIARIO`** | Um toque, sem heurística. **Risco aceito e explicitado:** uma compra no cartão lançada como diária sai do saldo hoje *e de novo* no vencimento da fatura. |
| Duplicata | **Janela de 10 min + confere no ledger** | A mesma dupla (app, valor) em 10 min atualiza a sugestão. E se já existe movimentação do mesmo valor hoje, a notificação muda de texto em vez de fingir que é nova. |
| Armazenamento | **O mínimo, sem texto, apagado em 24 h** | Pacote, centavos, timestamp e id da notificação. Nunca o título nem o corpo. |

## Três decisões que eu tomei

**1. A lista de apps é descoberta, não enumerada.** O caminho óbvio — mostrar os apps
instalados para você marcar — exige `QUERY_ALL_PACKAGES` no Android 11+, que é uma
permissão sensível da Play e contradiz frontalmente a postura do app. Em vez disso: com o
acesso ligado, um app que emite notificação com valor tem **só o nome do pacote** anotado
numa lista de "vistos", e aparece em `mais › notificações` esperando a sua marcação. Nada
dele é lido enquanto você não marcar. O preço é que o primeiro gasto de cada app novo passa
batido, e é ele que traz o app para a lista.

**2. Toda sugestão é saída.** Não há heurística de sinal: o valor detectado vira uma saída.
"Você recebeu um Pix de R$ 50" viraria −R$ 50 se você tocasse em "lançar" — por isso o
corpo da notificação abre a sheet, onde o sinal se troca num toque. É a mesma lógica da
decisão de natureza: nenhuma adivinhação de texto, correção barata depois.

**3. O primeiro valor da notificação é o valor.** Um aviso de banco costuma trazer dois
("compra aprovada de R$ 32,90 · saldo R$ 1.204,00"), e o primeiro é a transação. Pegar o
maior acertaria a compra grande e erraria todas as pequenas.

## O fluxo

```
notificação chega
   │
   ├─ app não marcado? ─────────────► descarta (e anota o pacote se tinha "R$")
   │
   ├─ sem valor em R$? ─────────────► descarta
   │
   ├─ (app, valor) visto nos últimos 10 min? ──► atualiza a sugestão existente
   │
   ├─ já existe movimentação desse valor hoje?
   │        sim ──► notifica "R$ 32,90 já lançado hoje · lançar mesmo assim?"
   │        não ──► notifica "Nubank · R$ 32,90 · lançar?"
   │
   └─ botões:  [lançar] grava DIARIO, saída, hoje, descrição = nome do app
               [ignorar] marca a detecção como resolvida (não volta se repostar)
               corpo → abre a sheet preenchida (valor, descrição, data de hoje)
```

## Estrutura

| arquivo | responsabilidade |
|---|---|
| `notificacoes/EscutaNotificacoes.kt` (novo) | o `NotificationListenerService`. Filtra por app marcado, extrai, deduplica e dispara. Fino de propósito: a decisão toda mora no motor. |
| `domain/DetectorValor.kt` (novo) | puro e testável na JVM: `primeiroValorEmCentavos(texto: String): Long?`. É onde o regex de R$ vive. |
| `domain/SugestaoEngine.kt` (novo) | puro: dada uma detecção, a janela e o ledger do dia, decide `Nova`, `Repetida`, `JaLancado` ou `Ignorar`. |
| `data/db/DeteccaoEntity.kt` + DAO (novo) | tabela `deteccoes` — pacote, centavos, epochMillis, chave da notificação, resolvida. **Schema v3**, com `AutoMigration` e `MigrationTest`. |
| `data/AppsMarcados.kt` (novo) | os pacotes marcados e os vistos, no DataStore que já existe. |
| `ui/mais/NotificacoesScreen.kt` (novo) | liga/desliga, o botão que abre o acesso a notificações do sistema, e a lista de apps vistos com seus interruptores. |
| `lembretes/Notificacoes.kt` (modificar) | canal novo "sugestões", separado do canal dos lembretes para você poder silenciar um sem o outro. |
| `AndroidManifest.xml` (modificar) | o service com `BIND_NOTIFICATION_LISTENER_SERVICE` e o intent-filter. |

**Fronteiras:** o service não decide nada — lê, pergunta ao `SugestaoEngine` e obedece. O
`DetectorValor` não conhece Android. É a mesma divisão do `LembretesEngine`, que já provou
funcionar: o motor puro testado na JVM, o `Worker`/`Service` como casca.

## O custo, dito por inteiro

- **A permissão não tem diálogo.** `BIND_NOTIFICATION_LISTENER_SERVICE` só se liga em
  *Configurações › Notificações › Acesso a notificações*. O app pode abrir essa tela
  (`ACTION_NOTIFICATION_LISTENER_SETTINGS`), não pode pedir por diálogo. O onboarding tem
  de explicar isso, e a tela `mais › notificações` precisa mostrar o estado real do acesso.
- **O serviço recebe tudo.** Ligado o acesso, o Android entrega ao saldo o texto de toda
  notificação do aparelho — WhatsApp incluído. O filtro por app é o que o saldo *faz* com o
  que chega, não o que ele recebe. Isso precisa estar escrito na tela, não só aqui.
- **A Play trata isso como uso sensível.** Publicar com um `NotificationListenerService`
  exige declaração e uma funcionalidade principal que o justifique, e costuma puxar uma
  revisão mais demorada. Toca direto o pipeline de release ainda não construído.
- **Desligado por padrão**, como os quatro lembretes.

## Fora de escopo

- Dólar e qualquer conversão de moeda.
- Heurística de entrada × saída e de conta × cartão.
- Ler SMS, e-mail ou extrato.
- Categorizar sozinho (escolher tag pela descrição).
- Backfill: notificação que chegou antes de você marcar o app está perdida, e tudo bem.

## Testes

**JVM (`DetectorValor`)** — `R$ 32,90`, `R$32,90`, `R$ 1.234,56`, `R$ 1.234`, `r$ 5,00`,
dois valores no mesmo texto (fica o primeiro), texto sem valor, valor em USD (ignorado),
`R$` solto sem número, e um número gigante que não cabe em `Int`.

**JVM (`SugestaoEngine`)** — a janela de 10 min pegando e deixando passar nos limites
exatos; a mesma dupla de app e valor em apps diferentes não deduplicando; o ledger do dia
com o mesmo valor virando `JaLancado`; uma detecção já resolvida não voltando.

**Instrumentado** — o service descarta app não marcado; o botão "lançar" grava uma
movimentação `DIARIO` negativa de hoje; o botão "ignorar" marca resolvida; o corpo abre a
sheet com valor e descrição preenchidos; a migração v2→v3; e a limpeza de 24 h apagando o
que passou e preservando o que não passou.
