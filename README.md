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

O app abre no **board**: o mês corrente em quadradinhos, um por dia — do dia 1 até hoje,
sem passar disso, porque uma célula pintada quer dizer "este dia aconteceu". A cor é o saldo
do dia: rosa saiu mais do que entrou, verde o contrário, e a intensidade sai de múltiplos de
um dia típico (a mediana dos dias com movimento). Uma compra no cartão pinta o dia da compra;
o anel marca o dia em que a fatura vence. Toque num dia para abrir o mês naquele dia, ou use
o ícone de lista no cabeçalho para o ledger de sempre.

Sugestão a partir de notificação, em `mais → notificações`, **desligada por padrão**: quando
um app que você marcou emite uma notificação com valor em reais, o saldo manda a sua própria
notificação com os botões "lançar" e "ignorar" — o lançamento entra como saída do dia, em
diários, e o corpo abre a sheet preenchida para trocar sinal, natureza ou tag. Requer ligar o
acesso a notificações à mão em *Configurações → Acesso a notificações*: o Android não tem
diálogo para isso, e com ele ligado o sistema entrega ao app o texto de toda notificação do
aparelho. O saldo só lê os apps marcados, e não guarda o texto de nenhum — do que detecta,
ficam só pacote, valor e hora, apagados em 24 h.

Widget de tela inicial (saldo projetado + novo lançamento) mascarado por padrão — ligue em
`mais → mostrar valores no widget`. Lembretes locais (fatura vence amanhã, recorrência hoje,
registrar gastos, fechamento do mês) em `mais → lembretes`; todos desligados por padrão.
