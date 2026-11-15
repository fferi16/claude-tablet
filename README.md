# Claude Tablet

> 🇬🇧 **English:** the app UI switches to English automatically on non-Hungarian devices (or choose it in the menu: *Language / Nyelv*). Download: [claude-tablet.apk](https://github.com/fferi16/claude-tablet/raw/main/dist/claude-tablet.apk).

Érintésbarát, magyar nyelvű felület a [Claude Code](https://docs.anthropic.com/en/docs/claude-code)-hoz Android tableten –
Termux + PRoot Ubuntu alatt futó helyi szerverrel és natív Android héjjal.

> Nem hivatalos, hobbi projekt. Nem az Anthropic terméke, és nem áll kapcsolatban vele.

## Hogyan épül fel

```
Android app (hu.claudetablet)          Termux                         PRoot Ubuntu
┌──────────────────────────┐   RUN_COMMAND   ┌──────────────────┐   ┌─────────────────────────┐
│ WebView + natív híd      │ ──────────────► │ start-server.sh  │──►│ node server.js (:8765)  │
│ (hang, értesítés, PiP,   │                 │ stop/restart/    │   │   └─ claude -p ...       │
│  lebegő buborék)         │ ◄────── HTTP ── │ wake-server.sh   │   │      (stream-json)      │
└──────────────────────────┘  127.0.0.1:8765 └──────────────────┘   └─────────────────────────┘
```

- **`server.js`** – függőség nélküli Node.js szerver. A `claude -p --output-format stream-json` kimenetét NDJSON-ként
  streameli a felületnek; kezeli a munkameneteket (a Claude Code saját `.jsonl` fájljaiból), a fájlfeltöltést,
  a fájlböngészést, a használati limitet és az újraindítást.
- **`public/index.html`** – az egész felület egy fájlban (chat, projektválasztó, csatolmányok, diktálás, felolvasás).
- **`android/`** – Gradle nélküli Android app: WebView-héj, Termux-indítás, értesítések, beszédfelismerés/felolvasás,
  kép a képben mód, lebegő buborék, „Claude” a szövegkijelölés menüben.
- **`termux/`** – a Termux oldalon futó indító / leállító / újraindító / ébresztő szkriptek.

## Telepítés

1. **Termux** (F-Droid vagy GitHub build), benne PRoot Ubuntu, abban Node.js ≥ 18 és a Claude Code:
   ```sh
   pkg install proot-distro && proot-distro install ubuntu && proot-distro login ubuntu
   # az Ubuntuban:
   apt install nodejs npm && npm install -g @anthropic-ai/claude-code && claude   # bejelentkezés
   ```
2. A repó a proot-ban a `/root/claude-tablet` mappába kerüljön, majd:
   ```sh
   sh /root/claude-tablet/install.sh
   ```
3. A **Termuxban** (nem a proot-ban) egyszer:
   ```sh
   echo "allow-external-apps=true" >> ~/.termux/termux.properties
   termux-reload-settings
   ```
4. Az app telepítése: töltsd le a kész APK-t a tabletre –
   **[claude-tablet.apk](https://github.com/fferi16/claude-tablet/raw/main/dist/claude-tablet.apk)** (`dist/` mappa) –,
   és nyisd meg (engedélyezd az „ismeretlen forrásból” telepítést). Saját buildhez lásd lent. Első indításkor engedélyezd a „Parancsok futtatása a Termux környezetben”
   engedélyt, a lebegő buborékhoz a „Megjelenítés más appok fölött” engedélyt is.
5. *Opcionális:* ha azt szeretnéd, hogy az app induláskor bekapcsolja a vezeték nélküli hibakeresést (hogy Claude
   ADB-n át vezérelhesse a tabletet), egyszer add meg számítógépről:
   `adb shell pm grant hu.claudetablet android.permission.WRITE_SECURE_SETTINGS`

**Tabletvezérlés (opcionális):** Beállítások → Kisegítő lehetőségek → *Claude Tablet – tabletvezérlés*. Ezzel Claude
a kérésedre képernyőképet készít, kiolvassa a képernyő szövegét, koppint és gépel – ADB nélkül (Android 11+ kell a
képernyőképhez). Ha a kapcsoló szürke: App-infó → ⋮ → *Korlátozott beállítások engedélyezése*. Az app menüjében lévő
**Beállítás-ellenőrző** megmutatja, mi hiányzik még.

Csak böngészőből, app nélkül: `./start.sh`, majd `http://127.0.0.1:8765/`.

## PC-s beszélgetések a tableten

A motor a PC-n is futhat; a tablet appban a menü tetején válthatsz *Ez a tablet* és a PC között. PC-n a PC beszélgetéseit
látod, és ott folytatod őket – a PC fájljaival, a PC-n futó Claude Code-dal.

1. A PC-n: Node.js 18+ és Claude Code (bejelentkezve), majd klónozd ezt a repót.
2. Indítsd: **Windows:** `start-pc.cmd` (dupla kattintás) · **Mac/Linux:** `./start-pc.sh`.
   Kiírja a címet és a **kódot**. Windowson az első indításkor engedélyezd a tűzfalban (*Magánhálózat*).
3. Tablet app → Menü → **＋** → név, cím (pl. `192.168.1.50:8765`), kód → *Hozzáadás*.

A két eszköz legyen ugyanazon a Wi-Fin. A kód megmarad a PC-n (`~/.claude-tablet.json`), újraindítás után sem kell újra megadni.

## APK build

Gradle nincs, a szkriptek közvetlenül hívják az `aapt2` / `javac` / `d8` / `apksigner` eszközöket.

- **Tableten (PRoot Ubuntu):** `apt install openjdk-21-jdk-headless aapt apksigner zipalign`, majd `sh android/build.sh`
  (az `android.jar` + `r8.jar` helye a szkriptben állítható).
- **Windows PC-n (Git Bash):** Android SDK build-tools + Android Studio JBR kell, majd `bash android/build-pc.sh`.
  A tabletes `r8.jar`/`android.jar` a `TSDK` környezeti változóval adható meg.

A repó **nem tartalmaz aláírókulcsot**; ha nincs `android/debug.keystore`, a build generál egyet. Egy már telepített
app fölé csak ugyanazzal a kulccsal lehet frissíteni – a `dist/` APK-ra tehát csak egy újabb `dist/` APK telepíthető
frissítésként; ha saját buildre váltasz, előbb töröld a régi appot.

## Xiaomi / HyperOS

A HyperOS a háttérben lévő appokat – így a Termuxot és benne a szervert – lefagyasztja, akkor is, ha előtérszolgáltatásuk
van. Ilyenkor a szerver nem válaszol. Az app ezt figyeli, és egy háttérparanccsal (szükség esetén a Termux
előtérbe hozásával) feloldja, de a tartós megoldás a Termuxnál:

- Akkumulátor-kímélő → **Nincs korlátozás**
- Automatikus indítás → **BE**
- Zárolás a legutóbbi appok között

Hasznos még (adb): `adb shell settings put global settings_enable_monitor_phantom_procs false`

## Biztonság

A szerver alapból csak a `127.0.0.1`-en figyel, **token nélkül** – így a tableten futó bármely más app is elérheti
(és Claude-on keresztül parancsot futtathat). Csak olyan eszközön használd, amelyen megbízol a telepített appokban.
`HOST=0.0.0.0` esetén a szerver véletlen tokent kér, amit induláskor kiír.

Ha megadod a `WRITE_SECURE_SETTINGS` engedélyt, az app bekapcsolja a vezeték nélküli hibakeresést (ADB). Ez a helyi
hálózaton párosítás után teljes hozzáférést ad a tablethez – idegen Wi-Fi-n ne hagyd bekapcsolva.

## Licenc

[MIT](LICENSE)
