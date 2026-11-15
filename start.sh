#!/bin/bash
# Claude Tablet indítása. Használat:  ./start.sh          (csak ezen a tableten)
#                                     HOST=0.0.0.0 ./start.sh  (más eszközről is, tokennel)
cd "$(dirname "$0")"
URL="http://localhost:${PORT:-8765}/"
( sleep 1; command -v termux-open-url >/dev/null && termux-open-url "$URL" || \
  command -v am >/dev/null && am start -a android.intent.action.VIEW -d "$URL" >/dev/null 2>&1 ) &
exec node server.js
