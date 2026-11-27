# Claude Tablet

🇭🇺 **Magyar** · 🇬🇧 [English](README.en.md)

Érintésre tervezett felület a [Claude Code](https://docs.anthropic.com/en/docs/claude-code)-hoz Android tableten.
A Claude Code a tableten fut (Termux + Ubuntu), az app pedig kényelmes, magyar (vagy angol) kezelőfelületet ad hozzá:
hangos diktálás, felolvasás, lebegő Claude-gomb bármelyik app fölött, élő kamera, tabletvezérlés, és a PC-n futó
Claude Code beszélgetései is elérhetők.

> Nem hivatalos, hobbi projekt. Nem az Anthropic terméke, és nem áll kapcsolatban vele.

**Letöltés:** [claude-tablet.apk](https://github.com/fferi16/claude-tablet/raw/main/dist/claude-tablet.apk)

---

## Tartalom

1. [Mit tud?](#mit-tud)
2. [Mi kell hozzá?](#mi-kell-hozzá)
3. [Telepítés lépésenként](#telepítés-lépésenként)
4. [Első indítás és engedélyek](#első-indítás-és-engedélyek)
5. [Használat](#használat)
6. [PC-s beszélgetések a tableten](#pc-s-beszélgetések-a-tableten)
7. [Frissítés](#frissítés)
8. [Hibaelhárítás](#hibaelhárítás)
9. [Xiaomi / HyperOS beállítások](#xiaomi--hyperos-beállítások)
10. [Biztonság](#biztonság)
11. [Saját APK fordítása](#saját-apk-fordítása)
12. [Hogyan épül fel?](#hogyan-épül-fel)
13. [Eltávolítás](#eltávolítás)

---

## Mit tud?

| Funkció | Leírás |
|---|---|
| 💬 **Chat** | Claude Code beszélgetések projektenként, korábbi beszélgetések folytatása, modellválasztó, használati limit kijelzése |
| 📎 **Csatolmányok** | Kép, PDF, bármilyen fájl csatolása; fájlböngésző a tablet fájljaihoz |
| 🎤 **Diktálás** | Beszédfelismerés a beviteli mezőben, opcionálisan automatikus küldéssel |
| 🔊 **Felolvasás** | A válaszokat hangosan felolvassa (kapcsolható) |
| ✳ **Lebegő Claude-gomb** | Bármelyik app fölött: koppintás = kérdezés gépelve, hosszú nyomás = diktálás; látja a képernyőt, gyors gombok (Foglald össze, Fordítsd le, …) |
| ✂ **Szövegkijelölés** | Bármely appban kijelölt szöveg → *Lebegő Claude* menüpont |
| 🖼 **Megosztás** | Képet bármelyik appból megoszthatsz a *Lebegő Claude*-dal |
| ⚡ **Gyorsbeállítás csempe** | A lehúzható menüből egy koppintással diktálhatsz |
| 📷 **Élő kamera** | A kamerát valamire ráirányítod, Claude megmondja, mi az, lefordítja, felolvassa, vagy nyílászárót elemez; élő módban magától szól, ha változik a kép |
| 🤖 **Tabletvezérlés** | Kérésre Claude képernyőképet készít, koppint, gépel, appot nyit |
| 📱 **Kép a képben** | Ha Claude megnyit egy appot, a Claude Tablet kis ablakba ugrik |
| 🖥 **PC-motor** | A PC-n futó Claude Code beszélgetései a tableten, ugyanazon a Wi-Fin |
| 🌐 **Két nyelv** | Magyar és angol felület (automatikus, vagy kézzel választható) |

---

## Mi kell hozzá?

- **Android tablet vagy telefon**, Android 8.0 vagy újabb (a képernyőképhez és az élő kamerához Android 11+ ajánlott).
  Legalább 6 GB RAM és ~3 GB szabad hely ajánlott.
- **Claude előfizetés** (Pro / Max) vagy **Anthropic API-kulcs** – a Claude Code ezzel jelentkezik be.
- **Wi-Fi / internet** a telepítéshez és a használathoz.
- *Opcionális:* számítógép, ha a PC-s beszélgetéseket is el akarod érni a tabletről.

---

## Telepítés lépésenként

A telepítés kb. 20–30 perc. A parancsokat pontosan így másold be. Ahol azt írja, **Termux**, ott a Termux
alkalmazás saját parancssorában vagy; ahol **Ubuntu**, ott a Termuxon belül indított Ubuntuban.

### 1. lépés – Termux telepítése

1. Telepítsd a **Termux** appot az [F-Droidról](https://f-droid.org/packages/com.termux/) vagy a
   [GitHubról](https://github.com/termux/termux-app/releases).
   ⚠️ A Google Play-es Termux elavult, azzal **nem működik**.
2. Nyisd meg a Termuxot, és frissítsd a csomagokat (a kérdésekre nyomj Entert):
   ```sh
   pkg update && pkg upgrade
   ```

### 2. lépés – Termux beállítása (Termux)

Engedélyezd, hogy a Claude Tablet app parancsot indíthasson a Termuxban:
```sh
mkdir -p ~/.termux
echo "allow-external-apps=true" >> ~/.termux/termux.properties
termux-reload-settings
```

### 3. lépés – Ubuntu telepítése (Termux)

```sh
pkg install proot-distro curl
proot-distro install ubuntu
proot-distro login ubuntu
```
Az utolsó parancs után már az **Ubuntuban** vagy (a sor elején `root@localhost` áll).
Később ugyanígy lépsz be: `proot-distro login ubuntu`. Kilépés: `exit`.

### 4. lépés – Node.js, Git és Claude Code (Ubuntu)

```sh
apt update && apt install -y nodejs npm git
node --version        # legalább v18 legyen
npm install -g @anthropic-ai/claude-code
```

### 5. lépés – Bejelentkezés a Claude Code-ba (Ubuntu)

```sh
claude
```
Kövesd a képernyőt: válaszd a Claude-fiókos bejelentkezést, nyisd meg a kiírt linket, jelentkezz be, és ha kódot kér,
másold vissza. Ha a Claude Code elindult és válaszol, lépj ki belőle: `/exit`.

### 6. lépés – A Claude Tablet letöltése (Ubuntu)

A repónak pontosan a `/root/claude-tablet` mappába kell kerülnie:
```sh
git clone https://github.com/fferi16/claude-tablet /root/claude-tablet
sh /root/claude-tablet/install.sh
```
Az `install.sh` a Termux oldalára másolja az indító szkripteket, és létrehozza a `~/bin/tablet` parancsot.

### 7. lépés – Az app telepítése

1. A tableten nyisd meg ezt a linket, és töltsd le az APK-t:
   **https://github.com/fferi16/claude-tablet/raw/main/dist/claude-tablet.apk**
2. Nyisd meg a letöltött fájlt. Ha a rendszer kérdezi, engedélyezd a böngészőnek (vagy fájlkezelőnek)
   az **ismeretlen forrásból származó appok telepítését**.
3. Telepítés → Megnyitás.

### 8. lépés – Termux engedély az appnak

Beállítások → Alkalmazások → **Claude Tablet** → Engedélyek → (További engedélyek) →
**Parancsok futtatása a Termux környezetben** → Engedélyezés.
(Az app első indításkor magától is kéri.)

Kész – az app elindítja a motort, és megjelenik a chat. 🎉

---

## Első indítás és engedélyek

Első indításkor a **Beállítás-ellenőrző** megmutatja, mi van már rendben, és mi hiányzik. Később a menüből (☰) is elérhető.

| Engedély | Mire kell | Hol adod meg |
|---|---|---|
| Termux parancsok futtatása | A motor indítása | Az app kéri / Alkalmazások → Claude Tablet → Engedélyek |
| Mikrofon | Diktálás | Az app kéri első diktáláskor |
| Kamera | Élő kamera | Az app kéri első megnyitáskor |
| Értesítések | Jelzés, ha kész a válasz | Az app kéri |
| Megjelenítés más appok fölött | Lebegő Claude-gomb | Az app a beállításokhoz visz |
| Kisegítő lehetőségek: *Claude Tablet – tabletvezérlés* | Képernyőkép, koppintás, gépelés Claude kérésére | Beállítások → Kisegítő lehetőségek → Letöltött appok |

**Ha a kisegítő kapcsoló szürke** (Android 13+): Beállítások → Alkalmazások → Claude Tablet → ⋮ (jobb felül) →
*Korlátozott beállítások engedélyezése*, utána már bekapcsolható.

**Opcionális (számítógépről, ADB-vel):** ha engedélyezed, az app magától bekapcsolja a vezeték nélküli hibakeresést,
így Claude ADB-n át is vezérelheti a tabletet, és a frissítések után sem kell kézzel beállítani semmit:
```sh
adb shell pm grant hu.claudetablet android.permission.WRITE_SECURE_SETTINGS
adb shell appops set hu.claudetablet SYSTEM_ALERT_WINDOW allow
```

---

## Használat

### Chat
- **☰ menü:** projektek, korábbi beszélgetések, nyelv, beállítások, PC-motorok, élő kamera.
- **＋** új beszélgetés. A projekt (mappa) kiválasztása után Claude abban a mappában dolgozik.
- Alul a **gyorsgombokkal** (Folytasd, Hibakeresés, Commit, …) egy koppintással küldhetsz gyakori kéréseket.
- Lent balra a **modellválasztó**, jobbra a használati limit és a kontextus telítettsége.
- 📎 = fájl csatolása, 🎤 = diktálás, ➤ = küldés. Futás közben a küldés gomb leállítja a választ.

### Diktálás és felolvasás
- A 🎤 gombbal diktálsz; a szöveg a mezőbe kerül.
- Menü → Beállítások: **Diktálás után** (automatikus küldés) és **Válasz felolvasása** (TTS).

### Lebegő Claude-gomb
- Bekapcsolás: menü → Beállítások → **Lebegő Claude-gomb**.
- **Koppintás:** kis panel nyílik, beírod a kérdést. **Hosszú nyomás:** diktálás.
- Claude látja, mi van a képernyőn (képernyőkép), és ha kéred, cselekszik is (megnyit, koppint, gépel).
- **Gyors gombok** a panelen: Foglald össze · Fordítsd le · Magyarázd el · Javítsd · Mi ez? · 📋 másolás.
  Saját gombok: menü → **⚡ Lebegő gomb: gyors gombok** (soronként `Felirat | utasítás`).
- 📎 a panelen: kép vagy fájl csatolása.
- Egy rövid jegyzetfüzetet is vezet: ha azt mondod, „jegyezd meg, hogy…”, a következő kérdéseknél is tudni fogja.

### Szövegkijelölés, megosztás, gyorscsempe
- Bármely appban jelölj ki szöveget → a felugró menüben (⋮) **Lebegő Claude** → a kijelölt szöveg a következő kérdéshez csatolódik.
- Képet bármelyik appból (Galéria, Chrome…) **Megosztás → Lebegő Claude**.
- Gyorsbeállítások (lehúzható menü) → ceruza/szerkesztés → húzd be a **Lebegő Claude** csempét → koppintásra diktálhatsz.

### 📷 Élő kamera
1. Menü (☰) → **📷 Élő kamera**. Első alkalommal engedélyezd a kamerát.
2. Fordítsd a kamerát valami felé, és válassz:
   - **Mi ez?** – leírja, mit lát
   - **Fordítsd** – lefordítja a látott szöveget (táblák, étlap, használati utasítás)
   - **Olvasd fel** – szó szerint felolvassa a szöveget
   - **Nyílászáró** – szakmai elemzés: profil, vasalat, üvegezés, állapot, hibák, felismerhető gyártó
3. A **nagy kerek gomb** vagy a kép megérintése az aktuális módban kérdez. Futás közben ugyanez leállítja.
4. **🎤** – hangon kérdezel a képről („mennyibe kerülhet ez?”, „hogyan kell beállítani?”).
5. **◉ Élő mód** – folyamatosan figyeli a képet, és csak akkor szól, ha érdemben változott valami.
6. Felül: **⚡ Gyors / 🧠 Okos** modell (Haiku / Sonnet), 🔊 felolvasás ki/be, **＋ Új** beszélgetés, ⟲ kameraváltás.

A válasz Gyors módban általában 5–10 másodperc alatt megjön. Az élő kamera csak a tableten futó motorral működik
(PC-motornál a böngészőmotor nem ad kamerát sima HTTP-kapcsolaton).

### Tabletvezérlés a parancssorból
Claude (vagy te az Ubuntuban) a `tablet` paranccsal vezérelheti a tabletet:
```sh
tablet shot                 # képernyőkép (~/tablet-shots/now.png)
tablet text                 # a képernyő szövegei koordinátákkal
tablet clicktext "Mentés"   # koppintás felirat alapján
tablet tap 800 1200         # koppintás koordinátára
tablet type "árvíztűrő"     # gépelés (ékezetekkel)
tablet key BACK|HOME|ENTER
tablet open chrome          # app megnyitása (a Claude Tablet kis ablakba ugrik)
```
Először az app kisegítő szolgáltatását használja; ha az nem fut, ADB-re vált.

---

## PC-s beszélgetések a tableten

A motor a PC-n is futhat; a tablet appban a menü tetején válthatsz *Ez a tablet* és a PC között. Ilyenkor a PC
beszélgetéseit látod, és a PC fájljaival, a PC-n futó Claude Code-dal dolgozol.

1. **A PC-n:** telepítsd a [Node.js](https://nodejs.org) 18+ verziót és a Claude Code-ot, és jelentkezz be:
   ```sh
   npm install -g @anthropic-ai/claude-code
   claude
   ```
2. Töltsd le a repót: `git clone https://github.com/fferi16/claude-tablet` (vagy a GitHubon *Code → Download ZIP*).
3. Indítsd a motort:
   - **Windows:** dupla kattintás a `start-pc.cmd` fájlra. Első indításkor a tűzfalnál engedélyezd (*Magánhálózat*).
   - **Mac / Linux:** `./start-pc.sh`
   
   Kiírja a **címet** (pl. `192.168.1.50:8765`) és a **párosító kódot**.
4. **A tableten:** menü (☰) → a motorválasztó melletti **＋** → név, cím, kód → *Hozzáadás*.
5. A menü tetején válts a PC-re. Vissza: *Ez a tablet*.

A két eszköz legyen ugyanazon a Wi-Fin. A kód megmarad a PC-n (`~/.claude-tablet.json`), nem kell újra megadni.
Más port: `start-pc.cmd 8800`.

---

## Frissítés

Ha új verzió érhető el, az app tetején **frissítési sáv** jelenik meg.

1. **Motor (Ubuntu):**
   ```sh
   cd /root/claude-tablet && git pull && sh install.sh
   ```
   Utána az appban: menü (☰) → **Motor újraindítása** (vagy zárd be és nyisd meg újra az appot).
2. **App:** a frissítési sávra koppintva töltsd le és telepítsd az új APK-t (a régi fölé, adatvesztés nélkül).
3. Ha ADB-vel adtál engedélyeket, azokat újratelepítés után add meg újra (lásd [Első indítás](#első-indítás-és-engedélyek)).

A felület (chat, élő kamera) a motorral együtt frissül, ehhez nem kell új APK.

---

## Hibaelhárítás

| Tünet | Megoldás |
|---|---|
| „Claude motor indítása…” sokáig, majd hiba | Ellenőrizd a 2. és 8. lépést (`allow-external-apps`, Termux engedély). Nyisd meg a Termuxot egyszer, majd vissza az appba. Napló: Ubuntuban `tail -50 /root/.claude-tablet.log` |
| A motor időnként nem válaszol | A rendszer lefagyasztja a háttérben a Termuxot – lásd [Xiaomi / HyperOS](#xiaomi--hyperos-beállítások) |
| „A `claude` parancs nem található” | Ubuntuban: `npm install -g @anthropic-ai/claude-code` |
| Bejelentkezési hiba | Ubuntuban futtasd: `claude`, és jelentkezz be újra |
| Nem működik a diktálás | Telepítsd/frissítsd a Google appot vagy a *Speech Services by Google*-t, és engedélyezd a mikrofont |
| Élő kamera: „Nem érem el a kamerát” | Alkalmazások → Claude Tablet → Engedélyek → Kamera → Engedélyezés, majd *Újra* |
| A lebegő gomb nem látja a képernyőt | Kapcsold be (vagy ki-be) a *Claude Tablet – tabletvezérlés* kisegítő szolgáltatást |
| A kisegítő kapcsoló szürke | App-infó → ⋮ → *Korlátozott beállítások engedélyezése* |
| „Ez a beszélgetés most máshol is nyitva van” | A beszélgetést az elmúlt 2 percben egy másik hely (pl. a PC-n a Claude Code) írta. Ha biztosan nincs máshol nyitva, nyugodtan folytasd |
| A letöltött APK nem látszik a Letöltésekben | Nyisd meg közvetlenül a böngésző letöltéseiből, vagy indítsd újra a fájlkezelőt |
| PC-motor nem érhető el | Ugyanazon a Wi-Fin vagy? Engedélyezted a tűzfalban? Fut a `start-pc` ablak? Jó a cím és a kód? |

---

## Xiaomi / HyperOS beállítások

A HyperOS (és a MIUI) a háttérben lévő appokat – így a Termuxot és benne a motort – lefagyasztja. Az app ezt figyeli és
feloldja, de a tartós megoldás a **Termux** beállításainál:

- Akkumulátor-kímélő → **Nincs korlátozás**
- Automatikus indítás → **BE**
- A legutóbbi appok listájában a Termuxot **zárold** (hosszú nyomás → lakat)

Hasznos még (ADB-vel): `adb shell settings put global settings_enable_monitor_phantom_procs false`

Más gyártóknál (Samsung, Huawei, OnePlus) hasonló akkumulátor-beállításokat keress a Termuxnál.

---

## Biztonság

A motor Claude-on keresztül parancsot futtat, és a tabletet is vezérelheti, ezért védett:

- **Más appok ellen:** az app első indításkor saját titkot generál, és csak a Termuxon át adja át a motornak
  (`~/.claude-tablet/secret`, csak a Termux olvashatja). A motor csak az ezt ismerő kéréseket fogadja.
- **Idegen weboldalak ellen:** a motor elutasítja a más oldalról érkező kéréseket (`Origin`, `Sec-Fetch-Site`) és a
  nem `127.0.0.1`/`localhost` `Host` fejlécet (DNS-rebinding).
- **Kamera:** a WebView csak a saját, helyi oldalnak ad kameraengedélyt.
- **PC-s mód:** 10 karakteres párosító kód, percenként legfeljebb 10 hibás próbálkozás IP-nként. A forgalom sima
  HTTP – **csak megbízható (otthoni) Wi-Fin** használd.
- **ADB:** ha megadod a `WRITE_SECURE_SETTINGS` engedélyt, az app bekapcsolja a vezeték nélküli hibakeresést.
  Ez a helyi hálózaton teljes hozzáférést adhat a tablethez – idegen Wi-Fin ne hagyd bekapcsolva.
- **Böngészőből** (app nélkül): `http://127.0.0.1:8765/?token=<titok>` – a titok Termuxban: `cat ~/.claude-tablet/secret`.

---

## Saját APK fordítása

Gradle nincs; a szkriptek közvetlenül az `aapt2` / `javac` / `d8` / `apksigner` eszközöket hívják.

- **Tableten (Ubuntu):**
  ```sh
  apt install -y openjdk-21-jdk-headless aapt apksigner zipalign
  sh /root/claude-tablet/android/build.sh
  ```
  Az `android.jar` és az `r8.jar` helye a szkriptben állítható. Kimenet: `android/build/claude-tablet.apk`.
- **Windows PC-n (Git Bash):** Android SDK build-tools + Android Studio JBR kell, majd `bash android/build-pc.sh`.

A repó **nem tartalmaz aláírókulcsot**; ha nincs `android/debug.keystore`, a build generál egyet. Telepített app fölé
csak ugyanazzal a kulccsal lehet frissíteni: ha a letöltött APK-ról saját buildre váltasz, előbb töröld a régi appot.
Lambdát a `build.sh` nem tud fordítani – névtelen osztályokat használj.

---

## Hogyan épül fel?

```
Android app (hu.claudetablet)          Termux                         PRoot Ubuntu
┌──────────────────────────┐   RUN_COMMAND   ┌──────────────────┐   ┌─────────────────────────┐
│ WebView + natív híd      │ ──────────────► │ start-server.sh  │──►│ node server.js (:8765)  │
│ (hang, kamera, PiP,      │                 │ stop/restart/    │   │   └─ claude -p ...       │
│  lebegő gomb, vezérlés)  │ ◄────── HTTP ── │ wake-server.sh   │   │      (stream-json)      │
└──────────────────────────┘  127.0.0.1:8765 └──────────────────┘   └─────────────────────────┘
```

| Fájl / mappa | Szerep |
|---|---|
| `server.js` | Függőség nélküli Node.js motor: `claude -p` futtatása és streamelése, beszélgetések, feltöltés, fájlböngésző, lebegő gomb, élő kamera (`/api/live`), tabletvezérlés közvetítése |
| `public/index.html` | A teljes chat felület egy fájlban |
| `public/live.html` | Élő kamera |
| `android/` | Gradle nélküli Android app: WebView-héj, Termux-indítás, hang, kamera, PiP, lebegő gomb (`BubbleService`), kisegítő szolgáltatás (`AgentService`), gyorscsempe |
| `termux/` | Termux oldali indító / leállító / újraindító / ébresztő szkriptek |
| `bin/tablet` | Tabletvezérlő parancs |
| `start-pc.cmd`, `start-pc.sh` | Motor indítása PC-n |
| `dist/` | Kész APK és verzióinformáció (frissítési sáv) |

---

## Eltávolítás

1. Töröld a **Claude Tablet** appot.
2. Ubuntuban: `rm -rf /root/claude-tablet /root/.claude-tablet.json /root/.claude-tablet.log ~/bin/tablet ~/bin/tablet-findport`
3. Termuxban: `rm -rf ~/.claude-tablet`
4. Ha az egész Ubuntut törölnéd: Termuxban `proot-distro remove ubuntu`.

---

## Licenc

[MIT](LICENSE)
