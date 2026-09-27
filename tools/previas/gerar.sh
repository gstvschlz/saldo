#!/usr/bin/env bash
# Gera as prévias do seletor de widgets (claro e escuro) a partir de previas.html, com o Chrome
# headless. Rodar com `mise run previas` depois de mudar o desenho de um widget.
set -euo pipefail
cd "$(dirname "$0")/../.."

chrome="${CHROME:-}"
for c in "/c/Program Files/Google/Chrome/Application/chrome.exe" \
         "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome" \
         "$(command -v google-chrome || true)" "$(command -v chromium || true)"; do
  [ -z "$chrome" ] && [ -n "$c" ] && [ -x "$c" ] && chrome="$c"
done
[ -n "$chrome" ] || { echo "Chrome não encontrado; defina CHROME" >&2; exit 1; }

pagina="$(pwd)/tools/previas/previas.html"
case "$(uname -s)" in MINGW*|MSYS*) pagina="$(cygpath -m "$pagina")";; esac
res=app/src/main/res
mkdir -p "$res/drawable-nodpi" "$res/drawable-night-nodpi"

# provider  tipo-no-desenho  colunas  linhas — o tamanho com que cada widget nasce
while read -r nome tipo cols linhas; do
  w=$((cols * 92 - 12)); h=$((linhas * 108 - 12))
  for escuro in 0 1; do
    pasta=drawable-nodpi; [ "$escuro" = 1 ] && pasta=drawable-night-nodpi
    saida="$(pwd)/$res/$pasta/previa_$nome.png"
    case "$(uname -s)" in MINGW*|MSYS*) saida="$(cygpath -w "$saida")";; esac
    "$chrome" --headless=new --disable-gpu --hide-scrollbars --force-device-scale-factor=2 \
      --default-background-color=00000000 --virtual-time-budget=4000 \
      --window-size="$w,$h" --screenshot="$saida" \
      "file:///$pagina#kind=$tipo&cols=$cols&rows=$linhas&dark=$escuro" 2>/dev/null
    echo "$pasta/previa_$nome.png"
  done
done <<'LISTA'
board mes 4 2
poupanca guardou 4 2
ritmo ritmo 4 1
teto hoje 4 1
acaminho acaminho 4 2
paraondefoi paraondefoi 4 2
lancar lancar 2 1
LISTA
