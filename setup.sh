#!/data/data/com.termux/files/usr/bin/bash
# Claude Tablet – telepítés egy paranccsal. A TERMUX appban futtasd (nem az Ubuntuban!):
#
#   curl -fsSLo setup.sh https://raw.githubusercontent.com/fferi16/claude-tablet/main/setup.sh && bash setup.sh
#
# Mit csinál (többször is futtatható, a kész lépéseket átugorja):
#   1. Termux csomagok frissítése, külső appok engedélyezése (allow-external-apps)
#   2. proot-distro + Ubuntu telepítése
#   3. Ubuntuban: Node.js (18+), npm, git, Claude Code
#   4. Claude Tablet motor letöltése/frissítése (/root/claude-tablet) + install.sh
#   5. Claude Code bejelentkezés (ha még nincs)
#   6. Megnyitja az APK letöltését
set -e

REPO=https://github.com/fferi16/claude-tablet
APK=$REPO/raw/main/dist/claude-tablet.apk
DISTRO=ubuntu

c_ok="\033[1;32m"; c_info="\033[1;36m"; c_warn="\033[1;33m"; c_err="\033[1;31m"; c_off="\033[0m"
say()  { printf "${c_info}» %s${c_off}\n" "$*"; }
ok()   { printf "${c_ok}✓ %s${c_off}\n" "$*"; }
warn() { printf "${c_warn}! %s${c_off}\n" "$*"; }
die()  { printf "${c_err}✗ %s${c_off}\n" "$*" >&2; exit 1; }
trap 'printf "${c_err}✗ Hiba történt (fent látod, hol). Futtasd újra: bash setup.sh${c_off}\n" >&2' ERR

printf "\n${c_info}=== Claude Tablet telepítő ===${c_off}\n\n"

# --- 0. Ellenőrzés: Termuxban fut-e? ---------------------------------------
[ -n "$PREFIX" ] && [ -d /data/data/com.termux ] || \
  die "Ezt a Termux appban kell futtatni (nem az Ubuntuban, és nem PC-n)."
case "$(uname -m)" in
  aarch64|arm64|x86_64) ;;
  *) warn "Nem 64 bites processzor ($(uname -m)) – a Claude Code valószínűleg nem fog futni." ;;
esac

# --- 1. Termux -------------------------------------------------------------
say "Termux csomagok frissítése (ez pár percig tarthat)…"
pkg update -y
pkg upgrade -y -o Dpkg::Options::="--force-confnew"
ok "Termux naprakész"

mkdir -p ~/.termux
if ! grep -qs '^allow-external-apps *= *true' ~/.termux/termux.properties; then
  echo "allow-external-apps=true" >> ~/.termux/termux.properties
  termux-reload-settings 2>/dev/null || true
fi
ok "Külső appok engedélyezve (allow-external-apps)"

# --- 2. Ubuntu -------------------------------------------------------------
say "proot-distro és curl telepítése…"
pkg install -y proot-distro curl
# A proot-distro 5.x új helyre teszi a konténert – ezért a mappa helyett belépéssel ellenőrzünk.
in_ubuntu() { proot-distro login $DISTRO -- "$@" </dev/null >/dev/null 2>&1; }
if in_ubuntu true; then
  ok "Ubuntu már telepítve"
else
  say "Ubuntu telepítése (~500 MB letöltés)…"
  log=$(mktemp)
  if proot-distro install $DISTRO 2>&1 | tee "$log"; [ "${PIPESTATUS[0]}" -eq 0 ]; then
    ok "Ubuntu kész"
  elif grep -qi "already exists" "$log"; then
    ok "Ubuntu már telepítve"
  else
    rm -f "$log"; die "Az Ubuntu telepítése nem sikerült (lásd fent)."
  fi
  rm -f "$log"
fi

# --- 3–4. Ubuntu belseje: Node, Claude Code, motor --------------------------
say "Ubuntu: Node.js, git, Claude Code és a Claude Tablet motor…"
proot-distro login $DISTRO -- env REPO="$REPO" bash -s <<'UBUNTU'
set -e
export DEBIAN_FRONTEND=noninteractive
apt-get update -y
apt-get install -y nodejs npm git curl ca-certificates

major=$(node -v 2>/dev/null | sed 's/^v//; s/\..*//')
if [ -z "$major" ] || [ "$major" -lt 18 ]; then
  echo "» A Node.js túl régi ($(node -v 2>/dev/null || echo nincs)) – Node 20 telepítése…"
  curl -fsSL https://deb.nodesource.com/setup_20.x | bash -
  apt-get install -y nodejs
fi
echo "✓ Node.js $(node -v)"

if command -v claude >/dev/null 2>&1; then
  echo "✓ Claude Code már telepítve – frissítés…"
fi
npm install -g @anthropic-ai/claude-code
echo "✓ Claude Code $(claude --version 2>/dev/null | head -1)"

if [ -d /root/claude-tablet/.git ]; then
  echo "» Motor frissítése…"
  git -C /root/claude-tablet pull --ff-only
else
  rm -rf /root/claude-tablet
  git clone "$REPO" /root/claude-tablet
fi
sh /root/claude-tablet/install.sh >/dev/null
echo "✓ Claude Tablet motor: /root/claude-tablet"
UBUNTU
ok "Ubuntu oldal kész"

# --- 5. Bejelentkezés ------------------------------------------------------
if in_ubuntu test -s /root/.claude/.credentials.json; then
  ok "Claude Code-ba már be vagy jelentkezve"
else
  printf "\n${c_warn}Most jelentkezz be a Claude Code-ba:${c_off}\n"
  echo "  1. Válaszd a Claude-fiókos bejelentkezést."
  echo "  2. Nyisd meg a kiírt linket, jelentkezz be, és ha kódot kér, másold vissza."
  echo "  3. Ha a Claude válaszol, írd be:  /exit"
  printf "Nyomj Entert a kezdéshez… "; read -r _ </dev/tty
  proot-distro login $DISTRO -- claude </dev/tty || true
  in_ubuntu test -s /root/.claude/.credentials.json && ok "Bejelentkezve" || \
    warn "Nem látom a bejelentkezést. Később: proot-distro login ubuntu, majd: claude (API-kulccsal ez normális)"
fi

# --- 6. App ----------------------------------------------------------------
printf "\n${c_ok}=== A parancssori rész kész! ===${c_off}\n\n"
echo "Még 2 dolog van hátra:"
echo "  1. Telepítsd az appot – most megnyitom a letöltést:"
echo "       $APK"
echo "     (Ha kérdezi: engedélyezd az ismeretlen forrásból származó appok telepítését.)"
echo "  2. Beállítások → Alkalmazások → Claude Tablet → Engedélyek → További engedélyek →"
echo "     'Parancsok futtatása a Termux környezetben' → Engedélyezés (az app maga is kéri)."
echo
echo "Utána nyisd meg a Claude Tablet appot – elindítja a motort, és megjelenik a chat."
echo "Frissítés később: ugyanez a parancs (bash setup.sh)."
termux-open-url "$APK" 2>/dev/null || true
