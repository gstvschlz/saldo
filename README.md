# saldo
um app 100% local para controle dos seus próprios gastos

## como rodar

Ferramentas via [mise](https://mise.jdx.dev): `mise install` instala JDK, Gradle e Android SDK cmdline-tools.

- `mise run sdk-setup` — pacotes do Android SDK (uma vez)
- `mise run build` — APK debug em `app/build/outputs/apk/debug/`
- `mise run test` — testes unitários
- `mise run install` — instala num device/emulador conectado

Os testes instrumentados precisam de um device ou emulador ligado:
`mise exec -- ./gradlew connectedDebugAndroidTest`.

Os dados ficam 100% no aparelho (sem backup em nuvem nem transferência entre aparelhos).
Exporte em `mais → exportar dados`, em csv ou json.

Lançar cobra só o valor: a **descrição é opcional**, e a linha sem nome aparece como "sem
descrição" nas listas. Os exports guardam o campo vazio como ele é.

A aba `saldos` é o **board**, e só ele: o mês em quadradinhos, um por dia — do dia 1 até
hoje, sem passar disso, porque uma célula pintada quer dizer "este dia aconteceu". A cor é o
saldo do dia: rosa saiu mais do que entrou, verde o contrário, e a intensidade sai de
múltiplos de um dia típico (a mediana dos dias com movimento). Uma compra no cartão pinta o
dia da compra; o anel marca o dia em que a fatura vence.

Toque num dia e os lançamentos dele abrem embaixo da grade, com o swipe-pra-apagar de
sempre; toque de novo e fecham. As setas do cabeçalho e o arrasto horizontal trocam de mês —
um mês passado aparece inteiro, e a seta de avançar para no mês corrente, porque o que ainda
vai acontecer tem tela própria em `totais → a caminho`. A lista do mês inteiro sobrou só como
"os lançamentos desta etiqueta", que é onde a aba `tags` aterrissa.

Três gráficos de performance em `totais`: **ritmo do mês** (quanto já saiu contra o costume
dos três meses anteriores na mesma altura do mês), **poupança mês a mês** (a taxa dos seis
meses em barra) e **para onde foi ao longo do tempo** (as tags empilhadas, seis colunas).

Sugestão a partir de notificação, em `mais → notificações`, **desligada por padrão**: quando
um app que você marcou emite uma notificação com valor em reais, o saldo manda a sua própria
notificação com os botões "lançar" e "ignorar" — o lançamento entra como saída do dia, em
diários, e o corpo abre a sheet preenchida para trocar sinal, natureza ou tag. Requer ligar o
acesso a notificações à mão em *Configurações → Acesso a notificações*: o Android não tem
diálogo para isso, e com ele ligado o sistema entrega ao app o texto de toda notificação do
aparelho. O saldo só lê os apps marcados, e não guarda o texto de nenhum — do que detecta,
ficam só pacote, valor e hora, apagados em 24 h.

**Sete widgets** de tela inicial: saldo, a caminho, para onde foi, lançar, board do mês,
ritmo e poupança. Os que mostram dinheiro vêm mascarados por padrão — ligue em `mais →
mostrar valores no widget`. O do board e o da poupança não têm o que mascarar: cor e
porcentagem não são número de conta. Lembretes locais (fatura vence amanhã, recorrência hoje,
registrar gastos, fechamento do mês) em `mais → lembretes`; todos desligados por padrão.
