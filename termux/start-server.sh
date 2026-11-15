#!/data/data/com.termux/files/usr/bin/bash
# Claude Tablet motor indítása – Termux oldalon fut (NEM a proot-ban).
# Az Android app a Termux RUN_COMMAND intenttel hívja. Ha a motor már fut, azonnal kilép (0).
# Kilépési kód: 0 = fut, 1 = nem sikerült elindítani (lásd /root/.claude-tablet.log a proot-ban).
PREFIX=/data/data/com.termux/files/usr
export PATH="$PREFIX/bin:$PATH"
PORT="${CT_PORT:-8765}"
HEALTH="http://127.0.0.1:$PORT/api/health"
STATE_DIR=/data/data/com.termux/files/home/.claude-tablet
PIDFILE="$STATE_DIR/server.pid"
PD_LIB="$PREFIX/var/lib/proot-distro"
mkdir -p "$STATE_DIR"

# Az app saját titka (1. argumentum): ezzel azonosítja magát a motornál, így a tableten lévő más appok
# nem vezérelhetik. Csak a Termux (és a benne futó proot) olvashatja. A motor menet közben is észreveszi.
if [ -n "$1" ]; then
  if printf %s "$1" | grep -qE '^[0-9a-f]{32,128}$'; then
    if [ "$(cat "$STATE_DIR/secret" 2>/dev/null)" != "$1" ]; then
      ( umask 077; printf %s "$1" > "$STATE_DIR/secret.tmp" ) && mv -f "$STATE_DIR/secret.tmp" "$STATE_DIR/secret"
    fi
  else
    echo "Érvénytelen titok-formátum – figyelmen kívül hagyva." >&2
  fi
fi

alive() { curl -fsS -m 2 "$HEALTH" >/dev/null 2>&1; }

if alive; then echo "Claude Tablet motor már fut."; exit 0; fi

# Disztribúció neve: CT_DISTRO, vagy automatikus felismerés (új: containers/, régi: installed-rootfs/)
DISTRO="${CT_DISTRO:-}"
if [ -z "$DISTRO" ]; then
  for d in "$PD_LIB/containers" "$PD_LIB/installed-rootfs"; do
    [ -d "$d/ubuntu" ] && { DISTRO=ubuntu; break; }
  done
fi
if [ -z "$DISTRO" ]; then
  for d in "$PD_LIB/containers" "$PD_LIB/installed-rootfs"; do
    [ -d "$d" ] || continue
    DISTRO=$(ls -1 "$d" 2>/dev/null | head -n1)
    [ -n "$DISTRO" ] && break
  done
fi
DISTRO="${DISTRO:-ubuntu}"

command -v termux-wake-lock >/dev/null 2>&1 && termux-wake-lock >/dev/null 2>&1 &

INNER="export PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin:\$PATH; \
echo \$\$ > '$PIDFILE'; cd /root/claude-tablet && PORT=$PORT exec node server.js >> /root/.claude-tablet.log 2>&1"

setsid nohup proot-distro login "$DISTRO" -- bash -lc "$INNER" </dev/null >/dev/null 2>&1 &

# Várunk, amíg válaszol (max ~30 mp)
for _ in $(seq 1 60); do
  sleep 0.5
  if alive; then echo "Claude Tablet motor elindult ($DISTRO, port $PORT)."; exit 0; fi
done
echo "A motor nem válaszol. Napló a proot-ban: /root/.claude-tablet.log" >&2
exit 1
