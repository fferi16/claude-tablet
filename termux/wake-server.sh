#!/data/data/com.termux/files/usr/bin/bash
# Claude Tablet motor felébresztése – Termux oldalon fut.
# Az Android app hívja (RUN_COMMAND), amikor a motor nem válaszol, és közben előtérbe hozza a Termuxot:
# a HyperOS/MIUI a háttérben lefagyasztja a Termuxot (és vele a motort), előtérben viszont feloldja.
# A szkript megvárja / elindítja a motort, aztán visszahozza a Claude Tablet appot.
PREFIX=/data/data/com.termux/files/usr
export PATH="$PREFIX/bin:$PATH"
DIR=/data/data/com.termux/files/home/.claude-tablet
LOG="$DIR/wake.log"
mkdir -p "$DIR"
echo "$(date '+%F %T') ébresztés kérve" >>"$LOG"
bash "$DIR/start-server.sh" >>"$LOG" 2>&1
rc=$?
echo "$(date '+%F %T') start-server kilépési kód: $rc" >>"$LOG"
# vissza az appba (a Termux most előtérben van, így indíthat activityt)
am start -n hu.claudetablet/.MainActivity >/dev/null 2>&1
exit $rc
