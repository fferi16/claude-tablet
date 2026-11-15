#!/data/data/com.termux/files/usr/bin/bash
# Claude Tablet motor újraindítása – Termux oldalon fut (a szerver az `am` paranccsal indítja).
# Megvárja, amíg nincs futó Claude-kérés (max 30 perc), aztán leállítja és újraindítja a motort.
PREFIX=/data/data/com.termux/files/usr
export PATH="$PREFIX/bin:$PATH"
PORT="${CT_PORT:-8765}"
DIR=/data/data/com.termux/files/home/.claude-tablet
LOG="$DIR/restart.log"
exec >>"$LOG" 2>&1
echo "$(date '+%F %T') újraindítás kérve"
idle=0
for _ in $(seq 1 900); do
  r=$(curl -fsS -m 3 "http://127.0.0.1:$PORT/api/health" 2>/dev/null | sed -n 's/.*"runs":\([0-9]*\).*/\1/p')
  if [ -z "$r" ] || [ "$r" = "0" ]; then idle=$((idle+1)); else idle=0; fi
  [ $idle -ge 2 ] && break
  sleep 2
done
echo "$(date '+%F %T') motor szabad – leállítás"
bash "$DIR/stop-server.sh"
sleep 1
bash "$DIR/start-server.sh" && echo "$(date '+%F %T') elindult" || echo "$(date '+%F %T') HIBA az indításnál"
