# capturas — a home nova (board no lugar do ledger)

`01-home-board.png` — a aba `saldos` com a grade do mês, hoje com o anel do tint e o painel
do dia aberto embaixo. `02-totais-ritmo.png` — `totais › mês` com o bloco do ritmo.

## como recapturar

O `connectedAndroidTest` do Gradle **desinstala** o app no fim e leva junto o arquivo, então
as capturas saem por `adb`, sem o Gradle no meio:

```sh
mise exec -- ./gradlew assembleDebug assembleDebugAndroidTest
mise exec -- adb install -r app/build/outputs/apk/debug/app-debug.apk
mise exec -- adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
mise exec -- adb shell am instrument -w -e class com.scholze.saldo.CapturaHomeTest   com.scholze.saldo.test/androidx.test.runner.AndroidJUnitRunner
MSYS_NO_PATHCONV=1 mise exec -- adb pull   /sdcard/Android/data/com.scholze.saldo/files/01-home-board.png .
```

O `CapturaHomeTest` é escrito na hora e apagado depois — ele semeia saldo inicial e um mês
de lançamentos, revela os valores no hero e chama `uiAutomation.takeScreenshot()`. Um teste
que não afirma nada não fica no meio dos 136 que afirmam.

No Git Bash o `MSYS_NO_PATHCONV=1` é obrigatório: sem ele o `/sdcard/...` vira caminho
Windows e o `adb pull` erra dizendo que o arquivo não existe.
