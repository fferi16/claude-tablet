# Claude Tablet – Android alkalmazás

Natív WebView-héj a helyi Claude Tablet szerverhez (`http://127.0.0.1:8765/`).
Indításkor megnézi, fut-e a szerver; ha nem, a Termux `RUN_COMMAND` hívásával
elindítja a `~/.claude-tablet/start-server.sh` szkriptet.

## Build (PRoot Ubuntuban)

```sh
apt install openjdk-21-jdk-headless aapt apksigner zipalign   # ha még nincs
sh /root/claude-tablet/android/build.sh
```

A `/root/projekt/sdk` (android.jar, r8.jar) és a `/root/projekt/debug.keystore` fájlokat használja.
Eredmény: `build/claude-tablet.apk`, másolat: Termux home `~/claude-tablet.apk`.
Ikonok újragenerálása: `python3 gen_icons.py`.

## Telepítés

1. Termuxban: `termux-open ~/claude-tablet.apk` (vagy fájlkezelővel megnyitva) → Telepítés.
2. Termuxban egyszer:
   ```sh
   echo "allow-external-apps=true" >> ~/.termux/termux.properties
   termux-reload-settings
   ```
3. Első indításkor engedélyezd a „Parancsok futtatása a Termux környezetben” és az értesítés engedélyt.

Ha az automatikus indítás nem megy: Termuxban `~/.claude-tablet/start-server.sh`, majd *Újrapróbálás*.

## JS híd (`window.ClaudeApp`)

- `ClaudeApp.isApp()` → `true`
- `ClaudeApp.notify(cím, szöveg)` – értesítés, csak ha az app háttérben van
- `ClaudeApp.vibrate()` – rövid rezgés

Megosztás (szöveg) → `http://127.0.0.1:8765/?share=<URL-kódolt szöveg>`.
