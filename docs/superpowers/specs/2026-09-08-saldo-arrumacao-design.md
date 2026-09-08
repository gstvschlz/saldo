# saldo — arrumação: a fila sem tag, a meta que pinta, a assinatura que se denuncia, e a paleta afinada

**Data:** 2026-09-08 · **Branch:** `arrumacao-1`, a criar de `main` depois do merge de
`dados-1` · **Fatia 3 de 5** da rodada de endurecimento (antes de `captura-2` e
`widgets-a11y-1`).

## Objetivo

Três buracos que o app abriu sozinho, cada um no rastro de uma fatia anterior.

A **captura** derrubou a barreira de lançar e empurrou o trabalho para a frente: as linhas
chegam com o nome do estabelecimento e nenhuma etiqueta. O app mostra o buraco em todo
gráfico — `GrupoGasto.SemTag` está na barra, na série do tempo e no widget — e não dá a pá:
não existe caminho nenhum para ver *quais* linhas estão sem etiqueta, muito menos para
etiquetá-las em série.

O **guardado** (v0.6.0) pôs "guardou N%" no hero e no widget. Um número sem alvo é um placar
sem jogo: 14% é bom ou ruim? A resposta é do usuário, e hoje ele não tem onde escrevê-la.

E as **recorrências** só existem quando alguém as cadastra à mão. A assinatura que sai todo
mês há um ano fica avulsa, some da tela de recorrências, não entra em "a caminho" e não
aparece na projeção do mês que vem — enquanto o próprio banco de dados já tem, em texto
puro, a prova de que ela se repete.

E a **paleta das tags** nunca foi afinada como conjunto. Medida contra o validador de cor
categórica (2026-09-08): duas das seis são a mesma matiz (laranja 44°, pêssego 48°), o teal
está abaixo do piso de croma e lê como cinza, três cores ficam abaixo de 3:1 contra o fundo de
um dos dois temas, o pior par sob daltonismo está em ΔE 4,8 (chão 6) e o pior par em visão
normal em 10,9 (piso 15). Pior: **o verde da paleta é a matiz 154 — exatamente a do `tint`, a
do hero e a do próprio ícone**; uma tag verde não lê como categoria, lê como elemento do
sistema.

Os três primeiros não inventam dado novo: leem o que já está no `LedgerInput`. O quarto não
toca dado nenhum — troca seis constantes e repinta o que ainda estiver nas cores velhas.

## Decisões do usuário

| Decisão | Escolha | Consequência |
|---|---|---|
| Fila de sem tag | **Chip na barra do ledger** | Vira o quarto filtro, com a contagem. Nenhuma tela nova; etiquetar acontece onde o lançamento já mora. |
| Meta de guardar | **Configurável, padrão 20%** | Uma linha em `mais`; `0` = sem meta. |
| Meta no hero | **A pill muda de cor quando bate** | Sem texto novo na pill. O número da meta mora em `mais` e na leitura do TalkBack. |
| Assinatura | **Seção no topo de recorrências** | "parece assinatura", com `tornar mensal` e `dispensar`. |
| Auto-etiquetar | **Fora** | Sugerir a tag pelo histórico da descrição foi recusado nesta rodada. A fila é manual de propósito. |
| Segundo cartão | **Fora** | `CartaoConfig` continua singular. |
| Paleta das tags | **Proposta A — as seis, afinadas** | Seis cores continuam seis; matizes a cada 60°, croma no teto de 0,16, luminosidade dentro da faixa que serve aos dois temas. |

## Decisões que eu tomei

**1. A fila só mostra linha de verdade, e só até hoje.** `SEM_TAG` filtra por
`mov.id != 0L && mov.tags.isEmpty() && mov.data <= hoje`. Uma ocorrência virtual — a
expansão de uma recorrência num mês não materializado — não tem linha no banco para
receber etiqueta, e materializar o mês inteiro só para etiquetar seria criar linhas que o
usuário não pediu. O futuro entra na fila quando virar presente. `passaFiltro` passa a
receber `hoje`.

**2. O chip aparece quando há trabalho e some quando não há** — com uma exceção. Um chip
permanente anunciando uma tarefa é ruído nos meses em que está tudo etiquetado. A exceção:
se a fila zerar **com o filtro `sem tag` selecionado**, o chip fica (com `0`) e a lista
mostra "tudo etiquetado neste mês" até o usuário sair do filtro — a interface não se puxa
debaixo do próprio toque.

**3. A fila é do mês visto, não do histórico.** Todo chip daquela barra é do mês; um deles
varrendo o histórico inteiro seria uma exceção invisível. Quem quer o que ficou para trás
troca de mês, e a busca (a lupa) continua sendo o caminho do histórico completo.

**4. Etiquetar é um toque, e tem desfazer.** Sob `sem tag`, cada linha ganha embaixo uma
fileira de chips: até seis etiquetas — as mais usadas nos últimos 90 dias primeiro, depois
alfabética — mais `+`, que abre a sheet de edição (onde criar etiqueta na hora já existe,
`criarTagInline`). Um toque aplica e a linha sai da lista; o snackbar traz `desfazer`, como
em toda remoção do app. Sem o desfazer, um toque errado tira a linha da única tela em que
ela era fácil de achar. A fileira de chips **só** existe sob esse filtro.

**5. `definirTags` no repositório, em vez de passar por `editar`.** `MovimentacaoDao.setTags`
já existe; `editar(mov, escopo)` reescreveria a linha inteira a partir de um objeto de UI só
para trocar o vínculo de tag — e carrega as precondições de escopo de recorrência, que não
têm nada a ver com etiquetar. Método novo, estreito: `definirTags(movId: Long, tagIds: List<Long>)`.

**6. Verde sobre verde não se vê: a pill inverte.** O hero é um cartão `primaryContainer` —
verde — e a pill é `onPrimaryContainer` a 10% de alfa. Pintar de verde o que já está sobre
verde não muda nada. Batida a meta, a pill inverte: fundo `onPrimaryContainer`, texto
`primaryContainer`. É mudança de luminância, não de matiz — sobrevive ao daltonismo e ao
tema escuro (onde os dois tokens já trocam de lado), e lê como "acendeu" à distância de um
olhar. Continua sendo "muda de cor"; só não é o verde, porque o verde é o fundo.

**7. A cor sozinha não diz o número, então o TalkBack diz.** O `contentDescription` da pill
passa a ser `"guardou 14%, meta 20%"` e, quando bate, `"guardou 22%, meta de 20% batida"`.
O número visível da meta mora em `mais`; e a linha tracejada da meta entra no gráfico de
poupança de `totais → tendência`, que é exatamente onde o toque na pill já leva
(`SaldoApp.kt:166`).

**8. No widget, valor escondido é meta escondida.** `SaldoWidgetContent` mascara o percentual
junto com o dinheiro (`guardou ••%`). Se a cor mudasse mesmo mascarado, o widget contaria
pela cor o que escondeu no número. Com `mostrarValores = false`, a cor não muda.

**9. Assinatura é uma cobrança por mês — duas quebram o padrão.** É o discriminador mais
barato que existe contra o falso positivo óbvio: iFood, posto, mercado saem várias vezes no
mesmo mês. Um mês com duas ocorrências do mesmo grupo derruba a candidata.

**10. O critério, inteiro.** Sobre as **avulsas** (`recorrenciaId == null`) de saída
(`valorCentavos < 0`) com descrição não vazia, agrupadas por `Busca.normalizar(descricao)`,
nos **últimos seis meses fechados mais o corrente**. É candidata quando:
uma ocorrência por mês, em **três meses consecutivos ou mais**; valores dentro de **±10%**
da mediana (assinatura reajusta); e dias do mês dentro de **±5 dias** da mediana (cobrança
cai em dia útil). O valor sugerido é a **última** ocorrência (é o preço de hoje), o dia
sugerido é a **mediana** dos dias.

**11. `dispensar` é por descrição, e é para sempre.** A chave normalizada entra num
`stringSetPreferencesKey("assinaturas_dispensadas")` e não volta a ser sugerida — mesmo que
o valor mude depois. O contrário (voltar a perguntar quando o preço muda) transformaria
"dispensar" em "adiar", que não é o que a palavra promete.

**12. `tornar mensal` reusa o que existe.** `repo.converterEmRecorrencia(ocorrenciaMaisRecente,
diaDoMes = mediana)` já faz tudo: cria o template a partir da linha, liga a linha a ele e
semeia os meses materializados seguintes. Nenhuma API nova no repositório, e o comportamento
é idêntico ao de "repetir: todo mês" na sheet — inclusive na marcação de editada quando o dia
não bate.

**13. Até três candidatas, as de maior valor.** A seção é um empurrão, não uma caixa de
entrada. O que sobrar continua lá no mês seguinte.

**14. A paleta A, e por que ela ainda tem um roxo e um verde.** Seis cores separadas o
bastante exigem 60° entre matizes vizinhas; 6 × 60 fecha o círculo, então **uma cor cai na
faixa do roxo e outra na do verde, sempre** — não é escolha, é o círculo. O que a rotação
escolhe é *qual* roxo e *qual* verde, e a rotação 5° gasta a proximidade inevitável com a
matiz da casa no lugar certo:

| slot | hoje | A | o que muda |
|---|---|---|---|
| vinho | `#A6486B` H359 | `#B63C62` H5 | fica na família — de propósito (ver abaixo) |
| âmbar | `#B95A2E` H44 | `#9A5A00` H64 | sai de cima do pêssego |
| verde | `#14663A` H154 | `#719503` H125 | **29° para longe do `tint`**, e L 0,452 → 0,620 |
| teal | `#2A7A86` H209 C0,078 | `#079E92` H185 C0,110 | deixa de ler como cinza |
| azul | — | `#036EAE` H245 | (era o roxo escuro que ocupava esta região) |
| roxo | `#4B4BC4` H277 C0,184 L0,487 | `#A672DC` H305 C0,160 L0,649 | mais claro, menos saturado, e 55° da família vinho em vez de índigo solto |

A proximidade com a casa não some: com 60° de passo, alguma cor está sempre a ≤30° de
qualquer matiz dada. A rotação 5° gasta esse encontro no **vinho** (5° do `categoryVariable`,
`#8C4F63` H360) e não no **verde** — porque o verde da casa é o acento (tint, hero, FAB,
estado selecionado, ícone) e uma tag nele lê como sistema, enquanto o vinho da casa é o
pontinho de 6 dp da avulsa e a rampa negativa do board: uma tag na família vinho lê como
"deste app", não como "do sistema".

**15. Repintar é UPDATE de linha, não migração.** `TagDao.recolor(id, cor)` já existe. No
arranque, uma vez, guardado por `paleta_v2_aplicada` no DataStore: toda tag cuja `cor` seja
exatamente um dos seis valores antigos recebe o novo da **mesma posição**; qualquer outra cor
(importada, de versão antiga, escolhida à mão) fica intacta. Sem isso, `PaletaTags.proxima`
passaria a ignorar as tags existentes — ele só conta cores que estão em `cores` — e as
primeiras tags novas sairiam todas na primeira cor.

**16. Nenhuma migração do Room.** As fatias leem o que já está gravado; o que é novo
(`metaGuardarPercent`, `assinaturas_dispensadas`, `paleta_v2_aplicada`) mora no DataStore, e
as duas primeiras entram no dump do schema 2 como mais duas chaves de `settings`
(`paleta_v2_aplicada` não: é estado de aparelho, não dado do usuário).

## Comportamento

### A fila de sem tag

`FiltroLedger` ganha `SEM_TAG("sem tag")`, quarto e último. `ProjectionEngine.passaFiltro`
ganha o ramo e o parâmetro `hoje`:

```
FiltroLedger.SEM_TAG -> mov.id != 0L && mov.tags.isEmpty() && mov.data <= hoje
```

A coluna de saldo corrente sob esse filtro corre sobre o conjunto filtrado, como já corre
sob `diários` e `fixas` — nenhuma exceção nova.

`MesLedger` ganha `val semTag: Int`: quantas linhas do mês passam nesse teste, contado uma
vez dentro de `mes()` a partir de `doMes` (que já está particionado ali), sem varredura
extra. Faturas nunca entram: uma fatura é agregado de ciclo, não carrega etiqueta.

`FiltroChips` ganha `contagens: List<Int?> = emptyList()` — um numeral menor e mais apagado
dentro do chip, com `contentDescription` `"sem tag, 3 lançamentos"`. Os três chips antigos
passam `null` e ficam idênticos ao que são hoje.

A linha, sob `sem tag`, ganha embaixo a fileira de chips de etiqueta (regra da decisão 4).
Um toque chama `LedgerViewModel.etiquetar(movId, tagId)` → `repo.definirTags(movId, listOf(tagId))`
e emite o evento de snackbar com `desfazer` → `repo.definirTags(movId, emptyList())`.

`sem tag` e o filtro de etiqueta da aba tags são mutuamente exclusivos — a interseção é
sempre vazia. Escolher `sem tag` limpa `tagFiltro`; entrar numa etiqueta pela aba tags volta
o filtro para `todas`.

### A meta de guardar

`Settings` ganha `metaGuardarPercent: Int` (chave `meta_guardar_percent`, padrão **20**),
clampado a `0..100` na leitura como `hora()` já faz; `0` = sem meta.

`mais` ganha, no grupo do saldo inicial, a linha `meta de guardar` com o valor (`20%` ou
`sem meta`), abrindo um diálogo com `−`/`+` de um em um e `salvar`.

O hero (`BalanceHero`): com `metaGuardarPercent > 0 && taxaGuardada >= meta`, a pill inverte
(decisão 6) e o `contentDescription` muda (decisão 7). Sem meta, tudo fica exatamente como
está hoje. `taxaGuardada == null` (mês sem entrada) continua não desenhando pill nenhuma.

O widget (`SaldoWidgetContent`): mesma regra, respeitando a máscara (decisão 8).
`WidgetEstado.Pronto` ganha `metaGuardarPercent: Int`, preenchido em `WidgetDados` junto com
`taxaGuardada`.

`totais → tendência`, no gráfico de poupança: uma linha tracejada horizontal na altura da
meta, com o rótulo `meta 20%` na ponta. Sem meta, nenhuma linha.

### A assinatura que se denuncia

`domain/AssinaturasEngine.kt`, puro e determinístico como os outros motores:

```kotlin
data class Assinatura(
    val chave: String,              // Busca.normalizar(descricao)
    val descricao: String,          // como aparece na última ocorrência
    val valorCentavos: Long,        // a última (o preço de hoje)
    val valorAnteriorCentavos: Long?, // != null só quando a última mudou > 1%
    val diaDoMes: Int,              // mediana
    val meses: Int,                 // quantos meses seguidos
    val ocorrenciaMaisRecente: Movimentacao,
)

fun candidatas(input: LedgerInput, hoje: LocalDate, dispensadas: Set<String>): List<Assinatura>
```

O critério inteiro está na decisão 10. `dispensadas` sai antes de qualquer conta.

`RecorrenciasScreen` ganha o bloco no topo, só quando a lista não é vazia: título
`parece assinatura`, e por candidata uma linha com descrição, valor, `N meses`, e — quando
`valorAnteriorCentavos != null` — a segunda linha `subiu de R$ 39,90 para R$ 44,90`. Duas
ações por linha: `tornar mensal` (decisão 12, e a tela rola até a recorrência recém-criada)
e `dispensar`.

`RecorrenciasViewModel` combina `repo.ledger` com as dispensadas do `SettingsStore`; o
cálculo roda no `Dispatchers.Default` que o `flowOn` da tela já estabelece.

### A paleta afinada

```kotlin
val cores: List<Long> = listOf(
    0xFFB63C62L,  // vinho
    0xFF9A5A00L,  // âmbar
    0xFF719503L,  // oliva
    0xFF079E92L,  // teal
    0xFF036EAEL,  // azul
    0xFFA672DCL,  // lilás
)
```

Medida contra as duas superfícies reais (`#F2F5EE` e `#191F1A`): faixa de luminosidade,
piso de croma e contraste ≥ 3:1 **passam nos dois temas**; o pior par sob protanopia/
deuteranopia é ΔE 8,4 (alvo 8). O único piso que fica de fora é o de visão normal — teal ↔
oliva em ΔE 13,9, contra 15 —, e ele é inalcançável para *qualquer* conjunto de seis nessas
condições: o melhor que existe é 13,9. É aceitável aqui porque a cor nunca aparece sozinha:
toda bolinha tem o nome ao lado, na lista de fatias, na tela de tags, no seletor da sheet e
na fila de sem tag desta mesma fatia. Cinco cores passariam limpo (ΔE 16,2), ao custo de a
sexta tag repetir a cor da primeira em silêncio — recusado.

O remapeamento (decisão 15) roda no `SaldoApplication`, antes da primeira composição, e é
idempotente.

## Estrutura

```
domain/AssinaturasEngine.kt             novo — Assinatura, candidatas()
domain/PaletaTags.kt                    as seis cores afinadas + o mapa antigo→novo
data/SaldoRepository.kt                 aplicarPaletaV2() — recolor por posição, uma vez
SaldoApplication.kt                     dispara o remapeamento no arranque
domain/Modelos.kt                       FiltroLedger.SEM_TAG
domain/ProjectionEngine.kt              passaFiltro(hoje), MesLedger.semTag
data/SaldoRepository.kt                 definirTags(movId, tagIds)
data/SettingsStore.kt                   metaGuardarPercent, assinaturasDispensadas
data/Exporters.kt / data/Importers.kt   as duas chaves novas no schema 2
ui/components/M3.kt                     FiltroChips com contagem
ui/ledger/LedgerScreen.kt               fileira de chips sob sem tag; pill invertida no hero
ui/ledger/LedgerViewModel.kt            etiquetar/desetiquetar, exclusividade com tagFiltro
ui/mais/MaisScreen.kt                   linha e diálogo da meta
ui/mais/MaisViewModel.kt                definirMetaGuardar
ui/totais/SegmentoTendencia.kt          linha tracejada da meta
ui/totais/RecorrenciasScreen.kt         bloco "parece assinatura"
ui/totais/RecorrenciasViewModel.kt      candidatas + dispensar
widget/WidgetEstado.kt                  metaGuardarPercent
widget/WidgetDados.kt                   preenche a meta
widget/SaldoWidgetContent.kt            cor da meta, respeitando a máscara
```

## Fora de escopo

- **Auto-etiquetar** — sugerir a tag pela descrição já vista. Recusado nesta rodada; a fila
  existe justamente para tornar o trabalho manual barato. Se ela mostrar que o trabalho ainda
  é grande, o argumento para automatizar volta com dados.
- Etiquetar em lote (selecionar várias linhas e aplicar uma tag de uma vez): um toque por
  linha já é barato; multi-seleção traz modo de seleção, e modo de seleção traz um estado
  novo em toda a tela.
- Meta por etiqueta ou limite de gasto ("no máximo R$ 300 em delivery"): é outro produto —
  teto, não piso — e merece a sua própria conversa.
- Meta diferente por mês, e histórico de metas.
- Detectar assinatura **semanal, anual ou quinzenal**; detectar reajuste de uma recorrência
  **já cadastrada**; detectar entrada recorrente (salário).
- "ver dispensadas" e desfazer um `dispensar`.
- Segundo cartão, segunda conta.
- Cor de tag escolhida à mão (fora da paleta) e cor de tag por tema — a segunda foi medida e
  descartada: mesmo com um tom por tema, o claro trava em ΔE 13,5, porque exigir 3:1 contra um
  fundo quase branco é o que aperta a faixa.

## Testes

**JVM**

- `ProjectionEngineTest`: `SEM_TAG` inclui a linha sem etiqueta de ontem; exclui a de amanhã,
  a virtual (`id == 0`), a que tem etiqueta e a fatura; `semTag` conta exatamente essas; a
  coluna de saldo sob `SEM_TAG` soma só o conjunto filtrado.
- `SaldoColorsTest`: os dois tokens da pill invertida têm contraste suficiente nos dois temas.
- `AssinaturasEngineTest`, um caso por regra: três meses seguidos com valor igual é candidata;
  dois meses não é; três meses com um buraco no meio não é; duas ocorrências num dos meses
  não é; variação de 8% é candidata e de 30% não; dia 3, 5 e 4 é candidata e dia 3, 12 e 27
  não; a que já tem `recorrenciaId` nunca entra; entrada (valor positivo) nunca entra;
  descrição vazia nunca entra; `dispensadas` remove; `valorAnteriorCentavos` só aparece quando
  a última mudou mais de 1%; a ordenação é por valor, e a lista corta em três.
- `SettingsStoreTest`: meta `-5` e `120` caem no padrão; `0` significa sem meta e sobrevive à
  releitura (não é confundido com ausente).
- `ExportersTest`/`ImportersTest`: as duas chaves novas na ida e na volta; ausentes, aceitas.
- `LedgerViewModelTest`: escolher `sem tag` limpa `tagFiltro`, e escolher uma etiqueta volta
  o filtro para `todas`; `etiquetar` chama `definirTags` com a lista certa; o desfazer
  devolve a lista vazia.
- `SaldoWidgetContentTest`: com meta batida e valores visíveis, a cor muda; com meta batida e
  valores mascarados, não muda; sem meta, não muda.
- `PaletaTagsTest`: as seis cores novas; `proxima` continua devolvendo a menos usada; uma cor
  fora da paleta continua sendo ignorada na contagem; o mapa antigo→novo tem as seis entradas
  e nenhuma colisão.

**Instrumentados**

- `LedgerScreenTest`: o chip aparece com a contagem quando há linha sem etiqueta e some
  quando não há; selecionado, sobrevive ao zerar (com "tudo etiquetado neste mês") e some ao
  sair; a fileira de chips só aparece sob `sem tag`; um toque etiqueta, a linha sai, e o
  desfazer do snackbar a traz de volta sem etiqueta.
- `RepositoryTest`: `definirTags` substitui os vínculos e não toca em nenhum outro campo da
  linha; lista vazia limpa. `aplicarPaletaV2` repinta só as tags que estão numa cor antiga,
  preserva a cor de uma tag fora da paleta, e rodar duas vezes não muda nada na segunda.
- `MaisScreenTest`: o diálogo da meta salva, e `0` mostra `sem meta`.
- `RecorrenciasScreenTest`: com uma candidata, o bloco aparece com o número de meses;
  `tornar mensal` cria a recorrência e a candidata some da seção; `dispensar` a remove e ela
  não volta depois de recompor; sem candidata, nenhum bloco.
- `M3Test`: `FiltroChips` com contagem lê `"sem tag, 3 lançamentos"` no TalkBack.

## O que só você pode verificar

- A pill invertida no hero de verdade, nos dois temas — é a decisão 6, a única em que eu
  troquei o que você escolheu ("verde") pelo que o cartão verde permite.
- Se a fila do mês é suficiente, ou se você sente falta do histórico inteiro nela (decisão 3).
- A paleta afinada num aparelho de verdade, nos dois temas: o comparativo medido está em
  <https://claude.ai/code/artifact/952a3879-ff74-470f-9c77-e881f6dc63ca>, com simulação dos
  três tipos de daltonismo.
