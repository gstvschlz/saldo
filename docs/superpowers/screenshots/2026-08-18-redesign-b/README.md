# redesign B — capturas

Capturas feitas durante a execução das Tasks 1–9 do plano
`docs/superpowers/plans/2026-08-18-saldo-redesign-b.md`, no AVD `saldo_test`
(1080×2400, densidade 2.625, escala de fonte 1.0).

| arquivo | tela | tema |
|---|---|---|
| `01-navbar-fab-claro.png` | barra de navegação do M3 + FAB ancorado | claro |
| `02-ledger-linhas-claro.png` | ledger: linhas com badge do dia e pill de saldo | claro |
| `03-ledger-hoje-claro.png` | ledger: a linha de hoje (borda `tint`, badge preenchido) | claro |
| `04-totais-mes-claro.png` | totais › mês: card herói, chips, cards tonais | claro |
| `05-totais-para-onde-foi-claro.png` | totais › mês: "para onde foi" em caixa baixa | claro |
| `06-totais-a-caminho-claro.png` | totais › a caminho + atalho de recorrências | claro |
| `07-recorrencias-claro.png` | tela de recorrências (valores mascarados) | claro |
| `08-ledger-escuro.png` | ledger completo no esquema escuro | escuro |

## O que ainda NÃO foi conferido

O Step 2 da Task 9 pede a caminhada à mão nos dois temas, em escala de fonte
1.0 **e 1.5**, em toda tela. Falta:

- **escala de fonte 1.5** em qualquer tela — nada aqui foi capturado a 1.5;
- **tema escuro** de tudo que não seja o ledger (totais nos três segmentos,
  recorrências, tags, mais, lembretes, sheet, teclado, onboarding);
- **tags, mais, lembretes, sheet de lançamento, teclado e onboarding** em
  qualquer tema;
- os três pontos que o plano manda vigiar de perto: o chip de variação a 1.5, a
  pill de saldo com valor mascarado (`R$ •••••` é mais largo que quase todo
  número), e a sobreposição do FAB contra a barra de navegação de três botões.

As capturas acima saíram de testes instrumentados temporários
(`captureToImage`), já removidos. A receita está em `.superpowers/sdd/progress.md`
— inclusive o detalhe de que `mise run test-device` desinstala os dois APKs no
fim, então a captura precisa ser rodada por `adb shell am instrument`.
