# board — capturas

Emulador `saldo_test` (1080×2400), banco semeado com 12 meses de movimentação plausível:
salário no dia 5, aluguel no 10, assinatura no 20, e gasto avulso em ~65 % dos dias úteis
e ~85 % dos fins de semana, metade deles no cartão. A máscara de privacidade foi desligada
nas duas capturas — por padrão o app abre com ela ligada.

| arquivo | o quê |
|---|---|
| `01-board-claro.png` | tema claro |
| `02-board-escuro.png` | tema escuro |

O que as capturas provam, ponto a ponto:

- **A grade abre em hoje** (4 set, com contorno) e o hero fica parado acima dela — foi
  justamente isso que quebrou na primeira versão, em que o hero rolava junto e sumia.
- **A escala se comporta.** O salário do dia 5 satura no verde mais forte sem achatar o
  resto: os dias rosa continuam usando os três tons, e a régua diz o dia típico em reais
  (R$ 101,45) em vez de deixar a cor sem tradução.
- **O anel da fatura** está no dia 5 de setembro, por cima de uma célula verde — a compra
  no cartão pintou o dia em que foi feita, o vencimento só ganhou a marca.
- **A calha do mês** nomeia `ago` e `set` nas semanas que contêm um dia 1.
- **Os dois temas** trocam a rampa em vez de inverter o significado: no claro o tom mais
  forte é escuro, no escuro é claro, e o lado rosa continua sendo "saiu mais" nos dois.
