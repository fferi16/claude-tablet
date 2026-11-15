#!/data/data/com.termux/files/usr/bin/bash
# Claude Tablet motor leállítása – Termux oldalon fut.
PREFIX=/data/data/com.termux/files/usr
export PATH="$PREFIX/bin:$PATH"
PORT="${CT_PORT:-8765}"
PIDFILE=/data/data/com.termux/files/home/.claude-tablet/server.pid

if [ -f "$PIDFILE" ]; then
  PID=$(cat "$PIDFILE" 2>/dev/null)
  if [ -n "$PID" ] && kill -0 "$PID" 2>/dev/null; then
    kill -TERM "$PID" 2>/dev/null
    for _ in $(seq 1 20); do kill -0 "$PID" 2>/dev/null || break; sleep 0.25; done
    kill -0 "$PID" 2>/dev/null && kill -KILL "$PID" 2>/dev/null
  fi
  rm -f "$PIDFILE"
fi
# Tartalék, ha a motor még válaszol: név szerint (a proot alatti folyamatok a Termuxból is láthatók)
if curl -fsS -m 2 "http://127.0.0.1:$PORT/api/health" >/dev/null 2>&1; then
  pkill -f 'node server.js' 2>/dev/null; sleep 1
fi

command -v termux-wake-unlock >/dev/null 2>&1 && termux-wake-unlock >/dev/null 2>&1
if curl -fsS -m 2 "http://127.0.0.1:$PORT/api/health" >/dev/null 2>&1; then
  echo "A motor még válaszol." >&2; exit 1
fi
echo "Claude Tablet motor leállítva."
