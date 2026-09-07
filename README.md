# saldo

um app 100% local para controle dos seus próprios gastos.

Todo app de finanças, pago ou não, esbarra no mesmo problema: depende de você lembrar de
lançar cada gasto. O saldo lê o valor nas notificações do seu banco e sugere o lançamento —
você só confirma. Depois disso, é categorizar.

Nada sai do aparelho: sem conta, sem nuvem, sem rede.

## como rodar

Ferramentas via [mise](https://mise.jdx.dev): `mise install` instala JDK, Gradle e Android SDK.

- `mise run sdk-setup` — pacotes do Android SDK (uma vez)
- `mise run build` — APK debug em `app/build/outputs/apk/debug/`
- `mise run test` — testes unitários
- `mise run test-device` — testes instrumentados (precisa de um device ou emulador ligado)
- `mise run install` — instala num device conectado

## o que tem

- **lançar** — o `+` cobra só o valor; descrição, tag e recorrência são opcionais.
- **notificações** — `mais → notificações`: marque os apps do banco e o saldo sugere lançar cada valor que chegar. Desligado por padrão; nenhum texto de notificação vai para o disco.
- **saldos** — o mês em quadradinhos, um por dia, colorido pelo saldo do dia. Lista, busca e recorrências pela barra.
- **totais** — o mês, a tendência e o que ainda vem.
- **tags** — categorize e veja para onde foi.
- **widgets e lembretes** — opcionais, desligados por padrão.

Exporte tudo em `mais → exportar dados` (csv ou json).
