#!/usr/bin/env bash
# render.sh <page.html> <out.png> <width> <height> — renders an HTML page to PNG with headless Chrome.
CHROME="/c/Program Files/Google/Chrome/Application/chrome.exe"
in="$(cygpath -w "$(realpath "$1")")"; out="$(cygpath -w "$(realpath -m "$2")")"
"$CHROME" --headless=new --disable-gpu --hide-scrollbars --virtual-time-budget=8000 --force-device-scale-factor=1 \
  --default-background-color=00000000 --user-data-dir="$TEMP/bt-chrome" \
  --screenshot="$out" --window-size="$3,$4" "file:///${in//\//}" >/dev/null 2>&1
