# Claude Tablet

🇭🇺 [Magyar](README.md) · 🇬🇧 **English**

A touch-first interface for [Claude Code](https://docs.anthropic.com/en/docs/claude-code) on Android tablets.
Claude Code runs on the tablet itself (Termux + Ubuntu), and the app adds a comfortable English (or Hungarian) UI:
voice dictation, read-aloud, a floating Claude button over any app, live camera, tablet control, and access to
Claude Code conversations running on your PC.

> Unofficial hobby project. Not made by, or affiliated with, Anthropic.

**Download:** [claude-tablet.apk](https://github.com/fferi16/claude-tablet/raw/main/dist/claude-tablet.apk)

---

## Contents

1. [Features](#features)
2. [Requirements](#requirements)
3. [Step-by-step installation](#step-by-step-installation)
4. [First launch and permissions](#first-launch-and-permissions)
5. [Usage](#usage)
6. [PC conversations on the tablet](#pc-conversations-on-the-tablet)
7. [Updating](#updating)
8. [Troubleshooting](#troubleshooting)
9. [Xiaomi / HyperOS settings](#xiaomi--hyperos-settings)
10. [Security](#security)
11. [Building the APK yourself](#building-the-apk-yourself)
12. [Architecture](#architecture)
13. [Uninstalling](#uninstalling)

---

## Features

| Feature | Description |
|---|---|
| 💬 **Chat** | Claude Code conversations per project, resume earlier chats, model picker, usage limit display |
| 📎 **Attachments** | Attach images, PDFs or any file; file browser for the tablet's files |
| 🎤 **Dictation** | Speech recognition in the input box, optionally sending automatically |
| 🔊 **Read aloud** | Replies are spoken aloud (toggleable) |
| ✳ **Floating Claude button** | Over any app: tap = type a question, long-press = dictate; sees the screen; quick actions (Summarize, Translate, …) |
| ✂ **Text selection** | Select text in any app → *Floating Claude* menu item |
| 🖼 **Share** | Share an image from any app to *Floating Claude* |
| ⚡ **Quick Settings tile** | Dictate with one tap from the notification shade |
| 📷 **Live camera** | Point the camera at something: Claude tells you what it is, translates or reads the text, or inspects windows/doors; in live mode it speaks up when the scene changes |
| 🤖 **Tablet control** | On request, Claude takes screenshots, taps, types and opens apps |
| 📱 **Picture-in-picture** | When Claude opens an app, Claude Tablet shrinks to a small window |
| 🖥 **PC engine** | Claude Code conversations from your PC on the tablet, over the same Wi-Fi |
| 🌐 **Two languages** | English and Hungarian UI (automatic, or choose manually) |

---

## Requirements

- **Android tablet or phone**, Android 8.0 or newer (Android 11+ recommended for screenshots and the live camera).
  At least 6 GB RAM and ~3 GB free storage recommended.
- A **Claude subscription** (Pro / Max) or an **Anthropic API key** – Claude Code signs in with it.
- **Wi-Fi / internet** for installation and use.
- *Optional:* a computer, if you also want your PC's conversations on the tablet.

---

## Step-by-step installation

Installation takes about 20–30 minutes. Copy the commands exactly. **Termux** means the Termux app's own command
line; **Ubuntu** means the Ubuntu started inside Termux.

### Step 1 – Install Termux

1. Install **Termux** from [F-Droid](https://f-droid.org/packages/com.termux/) or
   [GitHub](https://github.com/termux/termux-app/releases).
   ⚠️ The Google Play version of Termux is outdated and **will not work**.
2. Open Termux and update its packages (press Enter at the prompts):
   ```sh
   pkg update && pkg upgrade
   ```

### Step 2 – Configure Termux (Termux)

Allow the Claude Tablet app to run commands in Termux:
```sh
mkdir -p ~/.termux
echo "allow-external-apps=true" >> ~/.termux/termux.properties
termux-reload-settings
```

### Step 3 – Install Ubuntu (Termux)

```sh
pkg install proot-distro curl
proot-distro install ubuntu
proot-distro login ubuntu
```
After the last command you are **inside Ubuntu** (the prompt starts with `root@localhost`).
Enter it the same way later: `proot-distro login ubuntu`. Leave with `exit`.

### Step 4 – Node.js, Git and Claude Code (Ubuntu)

```sh
apt update && apt install -y nodejs npm git
node --version        # must be v18 or newer
npm install -g @anthropic-ai/claude-code
```

### Step 5 – Sign in to Claude Code (Ubuntu)

```sh
claude
```
Follow the prompts: choose sign-in with your Claude account, open the link shown, sign in, and paste back the code if
asked. Once Claude Code starts and answers, leave it with `/exit`.

### Step 6 – Download Claude Tablet (Ubuntu)

The repository must live exactly at `/root/claude-tablet`:
```sh
git clone https://github.com/fferi16/claude-tablet /root/claude-tablet
sh /root/claude-tablet/install.sh
```
`install.sh` copies the start scripts to the Termux side and creates the `~/bin/tablet` command.

### Step 7 – Install the app

1. On the tablet, open this link and download the APK:
   **https://github.com/fferi16/claude-tablet/raw/main/dist/claude-tablet.apk**
2. Open the downloaded file. If asked, allow your browser (or file manager) to **install unknown apps**.
3. Install → Open.

### Step 8 – Termux permission for the app

Settings → Apps → **Claude Tablet** → Permissions → (Additional permissions) →
**Run commands in Termux environment** → Allow.
(The app also asks for this on first launch.)

Done – the app starts the engine and the chat appears. 🎉

---

## First launch and permissions

On first launch the **Setup check** shows what is ready and what is missing. You can open it later from the menu (☰).

| Permission | Needed for | Where |
|---|---|---|
| Run commands in Termux | Starting the engine | The app asks / Apps → Claude Tablet → Permissions |
| Microphone | Dictation | Asked on first dictation |
| Camera | Live camera | Asked when you first open it |
| Notifications | Alert when a reply is ready | The app asks |
| Display over other apps | Floating Claude button | The app takes you to the setting |
| Accessibility: *Claude Tablet – tablet control* | Screenshots, taps, typing on Claude's request | Settings → Accessibility → Downloaded apps |

**If the accessibility toggle is greyed out** (Android 13+): Settings → Apps → Claude Tablet → ⋮ (top right) →
*Allow restricted settings*; then you can turn it on.

**Optional (from a computer, via ADB):** lets the app turn on wireless debugging by itself, so Claude can also control
the tablet over ADB, and nothing has to be set manually after updates:
```sh
adb shell pm grant hu.claudetablet android.permission.WRITE_SECURE_SETTINGS
adb shell appops set hu.claudetablet SYSTEM_ALERT_WINDOW allow
```

---

## Usage

### Chat
- **☰ menu:** projects, earlier conversations, language, settings, PC engines, live camera.
- **＋** starts a new conversation. After choosing a project (folder), Claude works in that folder.
- The **quick buttons** at the bottom (Continue, Debug, Commit, …) send common requests with one tap.
- Bottom left: **model picker**; bottom right: usage limit and context usage.
- 📎 = attach a file, 🎤 = dictate, ➤ = send. While a reply runs, the send button stops it.

### Dictation and read-aloud
- Dictate with 🎤; the text goes into the input box.
- Menu → Settings: **After dictation** (send automatically) and **Read replies aloud** (TTS).

### Floating Claude button
- Turn on: menu → Settings → **Floating Claude button**.
- **Tap:** a small panel opens, type your question. **Long-press:** dictate.
- Claude sees what is on the screen (screenshot) and, if you ask, acts (opens, taps, types).
- **Quick actions** in the panel: Summarize · Translate · Explain · Fix · What is this? · 📋 copy.
  Your own: menu → **⚡ Floating button: quick actions** (one `Label | instruction` per line).
- 📎 in the panel: attach an image or file.
- It keeps a small notebook: say "remember that…" and it will know next time.

### Text selection, sharing, Quick Settings tile
- Select text in any app → in the popup menu (⋮) choose **Floating Claude** → the selection is attached to your next question.
- Share an image from any app (Gallery, Chrome…) → **Share → Floating Claude**.
- Quick Settings (notification shade) → pencil/edit → drag in the **Floating Claude** tile → tap it to dictate.

### 📷 Live camera
1. Menu (☰) → **📷 Live camera**. Allow camera access the first time.
2. Point the camera at something and choose:
   - **What is it?** – describes what it sees
   - **Translate** – translates visible text (signs, menus, manuals)
   - **Read aloud** – reads the text out word for word
   - **Window/door** – expert check: profile, hardware, glazing, condition, defects, recognisable manufacturer
3. The **big round button** or tapping the image asks in the current mode. While running, the same button stops it.
4. **🎤** – ask about the image by voice ("how much might this cost?", "how do I adjust it?").
5. **◉ Live mode** – keeps watching and only speaks when something meaningful changes.
6. Top bar: **⚡ Fast / 🧠 Smart** model (Haiku / Sonnet), 🔊 speech on/off, **＋ New** conversation, ⟲ switch camera.

In Fast mode a reply usually arrives in 5–10 seconds. The live camera only works with the engine running on the
tablet (with a PC engine the WebView does not grant camera access over plain HTTP).

### Tablet control from the command line
Claude (or you, in Ubuntu) can control the tablet with the `tablet` command:
```sh
tablet shot                 # screenshot (~/tablet-shots/now.png)
tablet text                 # on-screen text with coordinates
tablet clicktext "Save"     # tap by label
tablet tap 800 1200         # tap at coordinates
tablet type "héllo"         # type text (accents supported)
tablet key BACK|HOME|ENTER
tablet open chrome          # open an app (Claude Tablet shrinks to a small window)
```
It uses the app's accessibility service first and falls back to ADB if that is not running.

---

## PC conversations on the tablet

The engine can also run on your PC; in the tablet app, switch between *This tablet* and the PC at the top of the menu.
You then see the PC's conversations and work with the PC's files and the Claude Code installed there.

1. **On the PC:** install [Node.js](https://nodejs.org) 18+ and Claude Code, and sign in:
   ```sh
   npm install -g @anthropic-ai/claude-code
   claude
   ```
2. Get the repository: `git clone https://github.com/fferi16/claude-tablet` (or *Code → Download ZIP* on GitHub).
3. Start the engine:
   - **Windows:** double-click `start-pc.cmd`. On first start, allow it in the firewall (*Private networks*).
   - **Mac / Linux:** `./start-pc.sh`
   
   It prints the **address** (e.g. `192.168.1.50:8765`) and a **pairing code**.
4. **On the tablet:** menu (☰) → **＋** next to the engine picker → name, address, code → *Add*.
5. Switch to the PC at the top of the menu. Back: *This tablet*.

Both devices must be on the same Wi-Fi. The code is kept on the PC (`~/.claude-tablet.json`), so you don't need to
enter it again. Different port: `start-pc.cmd 8800`.

---

## Updating

When a new version is available, an **update banner** appears at the top of the app.

1. **Engine (Ubuntu):**
   ```sh
   cd /root/claude-tablet && git pull && sh install.sh
   ```
   Then in the app: menu (☰) → **Restart engine** (or close and reopen the app).
2. **App:** tap the update banner, download and install the new APK (over the old one, no data is lost).
3. If you granted permissions via ADB, grant them again after reinstalling (see [First launch](#first-launch-and-permissions)).

The UI (chat, live camera) updates together with the engine; no new APK needed for that.

---

## Troubleshooting

| Symptom | Fix |
|---|---|
| "Starting Claude engine…" for a long time, then an error | Check steps 2 and 8 (`allow-external-apps`, Termux permission). Open Termux once, then return to the app. Log: in Ubuntu `tail -50 /root/.claude-tablet.log` |
| The engine stops responding now and then | The system freezes Termux in the background – see [Xiaomi / HyperOS](#xiaomi--hyperos-settings) |
| "`claude` command not found" | In Ubuntu: `npm install -g @anthropic-ai/claude-code` |
| Sign-in error | In Ubuntu run `claude` and sign in again |
| Dictation does not work | Install/update the Google app or *Speech Services by Google*, and allow the microphone |
| Live camera: "Cannot access the camera" | Apps → Claude Tablet → Permissions → Camera → Allow, then *Retry* |
| The floating button cannot see the screen | Turn the *Claude Tablet – tablet control* accessibility service on (or off and on again) |
| Accessibility toggle is greyed out | App info → ⋮ → *Allow restricted settings* |
| "This conversation is open somewhere else" | The chat was written by another place (e.g. Claude Code on the PC) in the last 2 minutes. If it is surely not open elsewhere, just continue |
| The downloaded APK is not visible in Downloads | Open it directly from the browser's downloads, or restart the file manager |
| PC engine unreachable | Same Wi-Fi? Allowed in the firewall? Is the `start-pc` window running? Correct address and code? |

---

## Xiaomi / HyperOS settings

HyperOS (and MIUI) freezes background apps – including Termux and the engine inside it. The app detects and recovers
from this, but the lasting fix is in **Termux**'s settings:

- Battery saver → **No restrictions**
- Autostart → **ON**
- **Lock** Termux in the recent apps list (long-press → lock)

Also useful (via ADB): `adb shell settings put global settings_enable_monitor_phantom_procs false`

On other brands (Samsung, Huawei, OnePlus) look for similar battery settings for Termux.

---

## Security

The engine runs commands through Claude and can control the tablet, so it is protected:

- **Against other apps:** on first launch the app generates its own secret and passes it to the engine only through
  Termux (`~/.claude-tablet/secret`, readable only by Termux). The engine only accepts requests that know it.
- **Against foreign websites:** the engine rejects cross-site requests (`Origin`, `Sec-Fetch-Site`) and any `Host`
  header other than `127.0.0.1`/`localhost` (DNS rebinding).
- **Camera:** the WebView grants camera access only to its own local page.
- **PC mode:** 10-character pairing code, at most 10 failed attempts per minute per IP. Traffic is plain HTTP – use it
  **only on a trusted (home) Wi-Fi**.
- **ADB:** if you grant `WRITE_SECURE_SETTINGS`, the app turns on wireless debugging. That can give full access to the
  tablet on the local network – don't leave it on on untrusted Wi-Fi.
- **From a browser** (without the app): `http://127.0.0.1:8765/?token=<secret>` – the secret, in Termux: `cat ~/.claude-tablet/secret`.

---

## Building the APK yourself

No Gradle; the scripts call `aapt2` / `javac` / `d8` / `apksigner` directly.

- **On the tablet (Ubuntu):**
  ```sh
  apt install -y openjdk-21-jdk-headless aapt apksigner zipalign
  sh /root/claude-tablet/android/build.sh
  ```
  The paths of `android.jar` and `r8.jar` can be set in the script. Output: `android/build/claude-tablet.apk`.
- **On a Windows PC (Git Bash):** Android SDK build-tools + Android Studio JBR, then `bash android/build-pc.sh`.

The repository **contains no signing key**; if there is no `android/debug.keystore`, the build generates one. An
installed app can only be updated with an APK signed by the same key: when switching from the downloaded APK to your
own build, uninstall the old app first. `build.sh` cannot compile lambdas – use anonymous classes.

---

## Architecture

```
Android app (hu.claudetablet)          Termux                         PRoot Ubuntu
┌──────────────────────────┐   RUN_COMMAND   ┌──────────────────┐   ┌─────────────────────────┐
│ WebView + native bridge  │ ──────────────► │ start-server.sh  │──►│ node server.js (:8765)  │
│ (voice, camera, PiP,     │                 │ stop/restart/    │   │   └─ claude -p ...       │
│  floating button, ctrl)  │ ◄────── HTTP ── │ wake-server.sh   │   │      (stream-json)      │
└──────────────────────────┘  127.0.0.1:8765 └──────────────────┘   └─────────────────────────┘
```

| File / folder | Role |
|---|---|
| `server.js` | Dependency-free Node.js engine: runs and streams `claude -p`, conversations, uploads, file browser, floating button, live camera (`/api/live`), tablet-control relay |
| `public/index.html` | The whole chat UI in one file |
| `public/live.html` | Live camera |
| `android/` | Gradle-less Android app: WebView shell, Termux launch, voice, camera, PiP, floating button (`BubbleService`), accessibility service (`AgentService`), Quick Settings tile |
| `termux/` | Termux-side start / stop / restart / wake scripts |
| `bin/tablet` | Tablet control command |
| `start-pc.cmd`, `start-pc.sh` | Start the engine on a PC |
| `dist/` | Prebuilt APK and version info (update banner) |

---

## Uninstalling

1. Uninstall the **Claude Tablet** app.
2. In Ubuntu: `rm -rf /root/claude-tablet /root/.claude-tablet.json /root/.claude-tablet.log ~/bin/tablet ~/bin/tablet-findport`
3. In Termux: `rm -rf ~/.claude-tablet`
4. To remove Ubuntu entirely: in Termux `proot-distro remove ubuntu`.

---

## License

[MIT](LICENSE)
