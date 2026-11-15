#!/bin/bash
# A Termux-oldali indító/leállító szkriptek telepítése (a proot-ból futtatva).
set -e
SRC="$(cd "$(dirname "$0")" && pwd)/termux"
DEST=/data/data/com.termux/files/home/.claude-tablet
if [ ! -d /data/data/com.termux/files/home ]; then
  echo "Hiba: a Termux home (/data/data/com.termux/files/home) nem látható innen." >&2
  echo "Futtasd Termuxból: mkdir -p ~/.claude-tablet && cp $SRC/*.sh ~/.claude-tablet/ (a proot rootfs-ből)." >&2
  exit 1
fi
mkdir -p "$DEST"
cp "$SRC"/*.sh "$DEST/"
chmod 755 "$DEST"/*.sh
echo "Telepítve: $(cd "$SRC" && ls *.sh | sed "s|^|$DEST/|" | tr '\n' ' ')"
cat <<'MSG'

KÉZI LÉPÉS (egyszer, a Termux alkalmazásban – nem a proot-ban!):
  1) Engedélyezd, hogy más appok (a Claude Tablet app) parancsot futtassanak:
       mkdir -p ~/.termux
       echo 'allow-external-apps=true' >> ~/.termux/termux.properties
       termux-reload-settings
  2) Az Android appnak meg kell adni a "Run commands in Termux environment"
     (com.termux.permission.RUN_COMMAND) engedélyt: Beállítások → Alkalmazások →
     Claude Tablet → Engedélyek → További engedélyek.
  Kézi teszt Termuxban:  ~/.claude-tablet/start-server.sh  /  ~/.claude-tablet/stop-server.sh
MSG
