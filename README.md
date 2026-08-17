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

Widget de tela inicial (saldo projetado + novo lançamento) mascarado por padrão — ligue em
`mais → mostrar valores no widget`. Lembretes locais (fatura vence amanhã, recorrência hoje,
registrar gastos, fechamento do mês) em `mais → lembretes`; todos desligados por padrão.
