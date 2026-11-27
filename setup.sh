#!/data/data/com.termux/files/usr/bin/bash
# Claude Tablet – one-command installer. Run it in the TERMUX app (not inside Ubuntu!):
#
#   curl -fsSLo setup.sh https://raw.githubusercontent.com/fferi16/claude-tablet/main/setup.sh && bash setup.sh
#
# What it does (safe to run again – finished steps are skipped; also used for updating):
#   1. Updates Termux packages, allows external apps (allow-external-apps)
#   2. Installs proot-distro + Ubuntu
#   3. Inside Ubuntu: Node.js (18+), npm, git, Claude Code
#   4. Downloads/updates the Claude Tablet engine (/root/claude-tablet) + runs install.sh
#   5. Claude Code login (if not signed in yet)
#   6. Opens the APK download
#
# Language: follows the device language (Hungarian or English).
# Force it with:  CT_LANG=en bash setup.sh   or   CT_LANG=hu bash setup.sh
set -e

REPO=https://github.com/fferi16/claude-tablet
APK=$REPO/raw/main/dist/claude-tablet.apk
DISTRO=ubuntu

# --- Language ------------------------------------------------------------
LNG=${CT_LANG:-}
if [ -z "$LNG" ]; then
  loc=$(getprop persist.sys.locale 2>/dev/null || true)
  [ -z "$loc" ] && loc=$(getprop ro.product.locale 2>/dev/null || true)
  case "$loc" in hu*) LNG=hu ;; *) LNG=en ;; esac
fi
t() { if [ "$LNG" = hu ]; then printf '%s' "$1"; else printf '%s' "$2"; fi; }

c_ok="\033[1;32m"; c_info="\033[1;36m"; c_warn="\033[1;33m"; c_err="\033[1;31m"; c_off="\033[0m"
say()  { printf "${c_info}» %s${c_off}\n" "$*"; }
ok()   { printf "${c_ok}✓ %s${c_off}\n" "$*"; }
warn() { printf "${c_warn}! %s${c_off}\n" "$*"; }
die()  { printf "${c_err}✗ %s${c_off}\n" "$*" >&2; exit 1; }
ERRMSG=$(t "Hiba történt (fent látod, hol). Futtasd újra: bash setup.sh" \
           "Something went wrong (see the lines above). Run it again: bash setup.sh")
trap 'printf "${c_err}✗ %s${c_off}\n" "$ERRMSG" >&2' ERR

printf "\n${c_info}=== %s ===${c_off}\n\n" "$(t "Claude Tablet telepítő" "Claude Tablet installer")"

# --- 0. Are we in Termux? ----------------------------------------------------
[ -n "$PREFIX" ] && [ -d /data/data/com.termux ] || \
  die "$(t "Ezt a Termux appban kell futtatni (nem az Ubuntuban, és nem PC-n)." \
           "Run this in the Termux app (not inside Ubuntu, and not on a PC).")"
case "$(uname -m)" in
  aarch64|arm64|x86_64) ;;
  *) warn "$(t "Nem 64 bites processzor ($(uname -m)) – a Claude Code valószínűleg nem fog futni." \
               "Not a 64-bit CPU ($(uname -m)) – Claude Code will most likely not run.")" ;;
esac

# --- 1. Termux -------------------------------------------------------------
say "$(t "Termux csomagok frissítése (ez pár percig tarthat)…" "Updating Termux packages (this can take a few minutes)…")"
pkg update -y
pkg upgrade -y -o Dpkg::Options::="--force-confnew"
ok "$(t "Termux naprakész" "Termux is up to date")"

mkdir -p ~/.termux
if ! grep -qs '^allow-external-apps *= *true' ~/.termux/termux.properties; then
  echo "allow-external-apps=true" >> ~/.termux/termux.properties
  termux-reload-settings 2>/dev/null || true
fi
ok "$(t "Külső appok engedélyezve (allow-external-apps)" "External apps allowed (allow-external-apps)")"

# --- 2. Ubuntu -------------------------------------------------------------
say "$(t "proot-distro és curl telepítése…" "Installing proot-distro and curl…")"
pkg install -y proot-distro curl
# proot-distro 5.x stores containers elsewhere – so check by logging in, not by folder.
in_ubuntu() { proot-distro login $DISTRO -- "$@" </dev/null >/dev/null 2>&1; }
if in_ubuntu true; then
  ok "$(t "Ubuntu már telepítve" "Ubuntu is already installed")"
else
  say "$(t "Ubuntu telepítése (~500 MB letöltés)…" "Installing Ubuntu (~500 MB download)…")"
  log=$(mktemp)
  if proot-distro install $DISTRO 2>&1 | tee "$log"; [ "${PIPESTATUS[0]}" -eq 0 ]; then
    ok "$(t "Ubuntu kész" "Ubuntu ready")"
  elif grep -qi "already exists" "$log"; then
    ok "$(t "Ubuntu már telepítve" "Ubuntu is already installed")"
  else
    rm -f "$log"
    die "$(t "Az Ubuntu telepítése nem sikerült (lásd fent)." "Ubuntu installation failed (see above).")"
  fi
  rm -f "$log"
fi

# --- 3–4. Inside Ubuntu: Node, Claude Code, engine ----------------------------
say "$(t "Ubuntu: Node.js, git, Claude Code és a Claude Tablet motor…" \
         "Ubuntu: Node.js, git, Claude Code and the Claude Tablet engine…")"
proot-distro login $DISTRO -- env REPO="$REPO" LNG="$LNG" bash -s <<'UBUNTU'
set -e
t() { if [ "$LNG" = hu ]; then printf '%s' "$1"; else printf '%s' "$2"; fi; }
export DEBIAN_FRONTEND=noninteractive
apt-get update -y
apt-get install -y nodejs npm git curl ca-certificates

major=$(node -v 2>/dev/null | sed 's/^v//; s/\..*//')
if [ -z "$major" ] || [ "$major" -lt 18 ]; then
  echo "» $(t "A Node.js túl régi" "Node.js is too old") ($(node -v 2>/dev/null || echo -)) – $(t "Node 20 telepítése…" "installing Node 20…")"
  curl -fsSL https://deb.nodesource.com/setup_20.x | bash -
  apt-get install -y nodejs
fi
echo "✓ Node.js $(node -v)"

if command -v claude >/dev/null 2>&1; then
  echo "✓ $(t "Claude Code már telepítve – frissítés…" "Claude Code already installed – updating…")"
fi
npm install -g @anthropic-ai/claude-code
echo "✓ Claude Code $(claude --version 2>/dev/null | head -1)"

if [ -d /root/claude-tablet/.git ]; then
  echo "» $(t "Motor frissítése…" "Updating the engine…")"
  git -C /root/claude-tablet pull --ff-only
else
  rm -rf /root/claude-tablet
  git clone "$REPO" /root/claude-tablet
fi
sh /root/claude-tablet/install.sh >/dev/null
echo "✓ $(t "Claude Tablet motor" "Claude Tablet engine"): /root/claude-tablet"
UBUNTU
ok "$(t "Ubuntu oldal kész" "Ubuntu side done")"

# --- 5. Login ----------------------------------------------------------------
if in_ubuntu test -s /root/.claude/.credentials.json; then
  ok "$(t "Claude Code-ba már be vagy jelentkezve" "Already signed in to Claude Code")"
else
  printf "\n${c_warn}%s${c_off}\n" "$(t "Most jelentkezz be a Claude Code-ba:" "Now sign in to Claude Code:")"
  echo "  1. $(t "Válaszd a Claude-fiókos bejelentkezést." "Choose the Claude account login.")"
  echo "  2. $(t "Nyisd meg a kiírt linket, jelentkezz be, és ha kódot kér, másold vissza." \
                 "Open the printed link, sign in, and paste the code back if asked.")"
  echo "  3. $(t "Ha a Claude válaszol, írd be:" "When Claude replies, type:")  /exit"
  printf "%s " "$(t "Nyomj Entert a kezdéshez…" "Press Enter to start…")"; read -r _ </dev/tty
  proot-distro login $DISTRO -- claude </dev/tty || true
  if in_ubuntu test -s /root/.claude/.credentials.json; then
    ok "$(t "Bejelentkezve" "Signed in")"
  else
    warn "$(t "Nem látom a bejelentkezést. Később: proot-distro login ubuntu, majd: claude (API-kulccsal ez normális)" \
              "Can't see a login. Later: proot-distro login ubuntu, then: claude (this is normal with an API key)")"
  fi
fi

# --- 6. App ----------------------------------------------------------------
printf "\n${c_ok}=== %s ===${c_off}\n\n" "$(t "A parancssori rész kész!" "The command-line part is done!")"
echo "$(t "Még 2 dolog van hátra:" "2 things left:")"
echo "  1. $(t "Telepítsd az appot – most megnyitom a letöltést:" "Install the app – opening the download now:")"
echo "       $APK"
echo "     $(t "(Ha kérdezi: engedélyezd az ismeretlen forrásból származó appok telepítését.)" \
               "(If asked: allow installing apps from unknown sources.)")"
echo "  2. $(t "Beállítások → Alkalmazások → Claude Tablet → Engedélyek → További engedélyek →" \
               "Settings → Apps → Claude Tablet → Permissions → Additional permissions →")"
echo "     $(t "'Parancsok futtatása a Termux környezetben' → Engedélyezés (az app maga is kéri)." \
               "'Run commands in Termux environment' → Allow (the app also asks for it).")"
echo
echo "$(t "Utána nyisd meg a Claude Tablet appot – elindítja a motort, és megjelenik a chat." \
          "Then open the Claude Tablet app – it starts the engine and the chat appears.")"
echo "$(t "Frissítés később: ugyanez a parancs (bash setup.sh)." "To update later: run the same command (bash setup.sh).")"
termux-open-url "$APK" 2>/dev/null || true
