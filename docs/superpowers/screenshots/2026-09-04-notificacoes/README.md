# captura de notificações — capturas

Emulador `saldo_test` (1080×2400), com o acesso a notificações concedido, a captura ligada e
dois apps na lista: `com.android.shell` marcado (é quem `adb shell cmd notification post`
dispara, e aparece como "Shell") e `com.android.vending` apenas visto.

| arquivo | o quê |
|---|---|
| `01-tela-claro.png` | `mais → notificações`, tema claro |
| `02-tela-escuro.png` | a mesma tela, tema escuro |
| `03-sugestao-claro.png` | a sugestão na gaveta, logo acima da notificação que a gerou |

O que as capturas provam:

- **A tela diz a verdade antes de tudo.** O primeiro parágrafo é o que o Android entrega ao
  app, e o último é o que o app guarda — os dois acima e abaixo dos controles, não escondidos
  num "sobre".
- **O acesso é do sistema, não do app.** A linha mostra o estado real; sem ele o interruptor
  fica desabilitado e a linha vira "abrir configurações do sistema".
- **A lista é descoberta.** "Shell" está marcado e "license checker"
  (`com.android.vending`) apareceu sozinho por ter emitido uma notificação com valor — nenhum
  dos dois foi enumerado, porque enumerar exigiria `QUERY_ALL_PACKAGES`.
- **A cadeia inteira funciona.** Na terceira captura a notificação falsa
  "Compra aprovada · R$ 32,90 em MERCADO CENTRAL" está logo abaixo da sugestão que ela
  gerou — "Shell · R$ 32,90 · lançar como saída de hoje?" — com os botões `lançar` e
  `ignorar`.

## Como reproduzir

```bash
adb shell cmd notification allow_listener \
  com.scholze.saldo/com.scholze.saldo.captura.EscutaNotificacoes
adb shell cmd notification post -S bigtext -t 'Compra aprovada' tag 'R$ 32,90 em MERCADO'
```

Marque "Shell" em `mais → notificações` antes de disparar, senão o app só é anotado como
visto. E note que `adb shell am broadcast` **não** aciona os botões: o receiver é
`exported="false"` de propósito, e o shell é de fora — quem exercita esse caminho é o
`AcoesSugestaoTest`, pelo `PendingIntent` da própria notificação.
