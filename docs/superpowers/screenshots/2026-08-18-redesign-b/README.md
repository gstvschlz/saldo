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
| `09-tags-claro.png` | tags, já com o título grande à esquerda | claro |
| `10-mais-claro.png` | mais, já com o título grande à esquerda | claro |
| `11-teclado-onboarding-claro.png` | teclado do M3: discos de 72dp sobre `surface` | claro |
| `12-totais-fonte-1.5-claro.png` | totais a **escala de fonte 1.5** | claro |
| `13-chips-fonte-2.0-claro.png` | os chips a **escala 2.0** — o rótulo quebra dentro do chip | claro |
| `14-ledger-fonte-1.5-claro.png` | ledger inteiro a **escala 1.5** | claro |
| `15-totais-escuro.png` | totais › mês | escuro |
| `16-a-caminho-escuro.png` | totais › a caminho (estado vazio) | escuro |
| `17-tags-escuro.png` | tags | escuro |
| `18-mais-escuro.png` | mais (repare no Switch: verde do esquema, sem domar) | escuro |

## O que ainda NÃO foi conferido

O Step 2 da Task 9 pede a caminhada à mão nos dois temas, em escala de fonte
1.0 **e 1.5**, em toda tela. Falta:

- escala de fonte 1.5/2.0 nas telas que **não** são ledger e totais (as duas
  estão acima e passam);
- **tema escuro** de: tendência, recorrências, lembretes, sheet, teclado e
  onboarding (ledger, totais › mês, a caminho, tags e mais já estão acima);
- **lembretes e a sheet de lançamento** em qualquer tema (tags, mais e o
  teclado já estão acima, no claro);
- dos três pontos que o plano manda vigiar de perto:
  - **o chip de variação a 1.5**: não conferido com valor revelado (o emulador
    estava com a máscara ligada); a 1.5 mascarado, o chip do hero cabe.
  - **a pill de saldo com valor mascarado**: não conferido — foi preciso um mês
    com movimentações e o emulador só tinha saldo inicial.
  - **o FAB contra a barra de três botões**: não conferido — o AVD usa navegação
    por gestos.

## O que os testes de fonte grande já mostraram

A **1.5** o ledger e a aba totais ficam inteiros: os três chips cabem numa linha
só (com ~57dp de folga), o card herói não estoura e os rótulos da barra de
navegação não cortam. A **2.0** os chips não cortam nem somem — o rótulo quebra
DENTRO do chip ("a / caminho") e o chip cresce em altura. Fica feio, mas nada se
perde, então `FiltroChips` continua um `Row` simples, sem `FlowRow`.

As capturas acima saíram de testes instrumentados temporários
(`captureToImage`), já removidos. A receita está em `.superpowers/sdd/progress.md`
— inclusive o detalhe de que `mise run test-device` desinstala os dois APKs no
fim, então a captura precisa ser rodada por `adb shell am instrument`.
