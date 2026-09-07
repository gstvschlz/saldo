# uso diário — capturas

Emulador `saldo_test` (1080×2400), tema claro, build debug instalado do zero (`pm clear` +
onboarding com saldo inicial de R$ 1.000,00) e duas movimentações lançadas à mão: `mercado`
(avulsa, −R$ 20,00) e `netflix` (repete todo mês no dia 7, −R$ 49,90).

| arquivo | o quê |
|---|---|
| `01-board-lista.png` | `saldos`, ícone `ver como lista`: o mês em linhas, com os chips `todas/diários/fixas` e o dia 7 mostrando as duas movimentações |
| `02-busca.png` | a lupa da lista com a busca `mercado`, resultado agrupado pelo mês (`setembro 2026`) |
| `03-sheet-repetir-edicao.png` | a movimentação `netflix` aberta em `editar movimentação`, com o diálogo de `repetir` no ar oferecendo `parar de repetir a partir deste mês` |
| `04-recorrencias-pausada.png` | `totais → a caminho → recorrências`, com o interruptor da `netflix` desligado e a linha marcada `pausada` |
| `05-tags-cor.png` | `tags`, diálogo `editar tag` da tag `lazer` com a fileira de cores |
| `06-board-vazio.png` | o board recém-instalado (antes de qualquer lançamento), com "toque em + para lançar o primeiro" |

O que as capturas provam:

- **A lista é o mesmo mês, só em outra forma.** Os chips e os totais batem com o board — não é
  uma tela separada com dados próprios.
- **Busca cobre descrição, tag e valor**, e agrupa por mês mesmo quando só um mês tem resultado.
- **`repetir` continua editável depois de criada.** O diálogo troca de opções conforme o estado:
  numa nova movimentação oferece "todo mês", numa já recorrente oferece "parar de repetir".
- **Pausar não apaga.** A recorrência pausada continua listada, só sai da projeção do mês.
- **Tag tem cor, e a cor se edita** no mesmo diálogo que renomeia.
- **O board vazio tem instrução, não silêncio** — "toque em + para lançar o primeiro" some assim
  que a primeira entra.
