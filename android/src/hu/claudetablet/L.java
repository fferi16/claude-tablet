package hu.claudetablet;

import android.content.Context;
import java.util.HashMap;
import java.util.Locale;

/** Egyszerű kétnyelvűség: magyar rendszernyelven magyar, máshol angol (az app menüjében átállítható). */
public final class L {
    static boolean en = false;
    static final HashMap<String, String> M = new HashMap<>();

    /** "auto" | "hu" | "en" – a SharedPreferences("app") "lang" kulcsából */
    static void init(Context c) {
        String v = c.getSharedPreferences("app", Context.MODE_PRIVATE).getString("lang", "auto");
        en = "en".equals(v) || ("auto".equals(v) && !"hu".equals(Locale.getDefault().getLanguage()));
    }

    static String t(String hu) {
        if (!en) return hu;
        String e = M.get(hu);
        return e == null ? hu : e;
    }

    /** beszédfelismerés / felolvasás nyelve */
    static Locale locale() {
        if (!en) return new Locale("hu", "HU");
        Locale d = Locale.getDefault();
        return "hu".equals(d.getLanguage()) ? Locale.US : d;
    }
    static String tag() { return locale().toLanguageTag(); }

    static String defaultChips() {
        return !en ? BubbleService.DEFAULT_CHIPS :
            "Summarize | Summarize briefly.\n" +
            "Translate | Translate it into English (if it is already English, into my system language). Return only the translation.\n" +
            "Explain | Explain simply and briefly.\n" +
            "Fix | Fix spelling and wording. Return only the corrected text, nothing else.\n" +
            "What is this? | What is this? Briefly.";
    }

    static {
        M.put("Engedélyezd: Megjelenítés más alkalmazások felett", "Allow: Display over other apps");
        M.put("Lebegő Claude-gomb", "Floating Claude button");
        M.put("Párosítás a motorral…", "Pairing with the engine…");
        M.put("Frissítsd a Termux szkripteket: sh /root/claude-tablet/install.sh", "Update the Termux scripts: sh /root/claude-tablet/install.sh");
        M.put("A motor nem fogadja el az app kulcsát. Frissítsd a Termux szkripteket (sh /root/claude-tablet/install.sh), majd próbáld újra.", "The engine does not accept the app's key. Update the Termux scripts (sh /root/claude-tablet/install.sh), then retry.");
        M.put("Koppints a gombra bármelyik appban", "Tap the button in any app");
        M.put("Kikapcsolás", "Turn off");
        M.put("Új beszélgetés", "New chat");
        M.put("Ki", "Off");
        M.put("Írj, vagy diktálj a 🎤-val…", "Type, or dictate with 🎤…");
        M.put("Nem nyitható a képválasztó", "Cannot open image picker");
        M.put("Nincs beszédfelismerő", "No speech recognizer");
        M.put("🎤 Hallgatlak…", "🎤 Listening…");
        M.put("Feldolgozás…", "Processing…");
        M.put("Nincs mikrofon-engedély", "No microphone permission");
        M.put("Diktálási hiba (", "Dictation error (");
        M.put("Nem értettem", "Didn't catch that");
        M.put("Diktálási hiba: ", "Dictation error: ");
        M.put(" (A képernyőn látható tartalomról van szó.)", " (This is about what is on the screen.)");
        M.put("Kijelölt szöveg – mit csináljak vele?", "Selected text – what should I do with it?");
        M.put("Kép csatolva – mit csináljak vele?", "Image attached – what should I do with it?");
        M.put("🖼 kép  ", "🖼 image  ");
        M.put("Nincs mit másolni", "Nothing to copy");
        M.put("Vágólapra másolva ✓", "Copied ✓");
        M.put("Mi ez? Röviden.", "What is this? Briefly.");
        M.put("Mit gondolsz erről?", "What do you think about this?");
        M.put("\n\nKijelölt szöveg:\n\"\"\"\n", "\n\nSelected text:\n\"\"\"\n");
        M.put("  (✂ kijelölt szöveg)", "  (✂ selected text)");
        M.put("\n\nCsatolt kép (nézd meg Read-dal): ", "\n\nAttached image (view it with Read): ");
        M.put("  (🖼 kép)", "  (🖼 image)");
        M.put("📷 Képernyőkép…", "📷 Screenshot…");
        M.put("Gondolkodom…", "Thinking…");
        M.put("Hiba: ", "Error: ");
        M.put("A motor nem fut – indítom…", "Engine not running – starting it…");
        M.put("Gondolkodom… (látom a képernyőt)", "Thinking… (I can see the screen)");
        M.put("Gondolkodom… (képernyőkép nélkül)", "Thinking… (no screenshot)");
        M.put("⚠ eszközhiba, próbálkozom…", "⚠ tool error, retrying…");
        M.put("Hiba történt", "An error occurred");
        M.put("Kész", "Done");
        M.put("Olvasom: ", "Reading: ");
        M.put("Keresés a weben…", "Searching the web…");
        M.put("Leállítás…", "Stopping…");
        M.put("Nincs ilyen app: ", "No such app: ");
        M.put("Nem indítható: ", "Cannot start: ");
        M.put("A motor nem fut. Nyisd meg a Claude Tablet appot.", "The engine is not running. Open the Claude Tablet app.");
        M.put("Kapcsold be a lebegő Claude-gombot a Claude Tablet appban", "Turn on the floating Claude button in the Claude Tablet app");
        M.put("Kép választása", "Choose image");
        M.put("Kép küldése Claude-nak…", "Sending image to Claude…");
        M.put("A kép nem küldhető – fut a motor?", "Could not send the image – is the engine running?");
        M.put("Tabletvezérlés bekapcsolva", "Tablet control enabled");
        M.put("Kapcsold be: Telepített alkalmazások → Claude Tablet – tabletvezérlés", "Turn on: Installed apps → Claude Tablet – tablet control");
        M.put("Engedélyezd a „Megjelenítés más alkalmazások felett” jogot, majd kapcsold be újra", "Allow “Display over other apps”, then turn it on again");
        M.put("Nincs ilyen app a tableten: ", "No such app on this device: ");
        M.put("Nem nyitható meg: ", "Cannot open: ");
        M.put("Claude motor indítása…", "Starting Claude engine…");
        M.put("Claude motor indítása… (", "Starting Claude engine… (");
        M.put("Engedély szükséges a Termux parancs futtatásához…", "Permission needed to run Termux commands…");
        M.put("A Termux felébresztése…\nHa nem tér vissza magától, lépj vissza a Claude Tablet appba.", "Waking Termux…\nIf it does not return by itself, switch back to the Claude Tablet app.");
        M.put("Nincs mikrofon-engedély. Beállítások → Alkalmazások → Claude Tablet → Engedélyek.", "No microphone permission. Settings → Apps → Claude Tablet → Permissions.");
        M.put("Claude válaszok", "Claude replies");
        M.put("Értesítés, amikor Claude befejezte a választ", "Notification when Claude finishes a reply");
        M.put("Nincs beszédfelismerő a tableten. Telepítsd/frissítsd a Google appot vagy a Speech Services-t.", "No speech recognizer on this device. Install/update the Google app or Speech Services.");
        M.put("Nem indítható a felismerés: ", "Cannot start recognition: ");
        M.put("Nem értettem, próbáld újra.", "Didn't catch that, try again.");
        M.put("Hálózati hiba a felismerésnél.", "Network error during recognition.");
        M.put("Nincs mikrofon-engedély.", "No microphone permission.");
        M.put("A felismerő foglalt, próbáld újra.", "Recognizer busy, try again.");
        M.put("Felismerési hiba (", "Recognition error (");
        M.put("A felolvasó nem érhető el.", "Text-to-speech not available.");
        M.put("Parancs vágólapra másolva", "Command copied");
        M.put("Nem sikerült elindítani a Claude motort", "Could not start the Claude engine");
        M.put("A Termux alkalmazás nincs telepítve ezen az eszközön.", "Termux is not installed on this device.");
        M.put("Az alkalmazásnak nincs engedélye parancsot futtatni a Termuxban.", "The app has no permission to run commands in Termux.");
        M.put("A szerver 25 másodperc alatt sem válaszolt (127.0.0.1:8765). ", "The server did not respond within 25 seconds (127.0.0.1:8765). ");
        M.put("Lehet, hogy a Termux nem engedi külső alkalmazások parancsait, vagy hiányzik az indító szkript.", "Termux may not allow commands from other apps, or the start script is missing.");
        M.put("Hiba a Termux hívásakor: ", "Error calling Termux: ");
        M.put("Termux telepítve", "Termux installed");
        M.put("Telepítsd a Termuxot (F-Droid vagy GitHub verzió), és benne a PRoot Ubuntut a Claude-dal.", "Install Termux (F-Droid or GitHub version) and PRoot Ubuntu with Claude inside it.");
        M.put("Termux letöltése (F-Droid)", "Download Termux (F-Droid)");
        M.put("Külső alkalmazások engedélyezése a Termuxban", "Allow external apps in Termux");
        M.put("Futtasd a Termuxban (koppintás = másolás):", "Run in Termux (tap = copy):");
        M.put("Engedély megadása", "Grant permission");
        M.put("Claude Tablet → Engedélyek → További engedélyek → „Parancsok futtatása a Termux környezetben” → Engedélyezés.", "Claude Tablet → Permissions → Additional permissions → “Run commands in Termux environment” → Allow.");
        M.put("Alkalmazás-beállítások megnyitása", "Open app settings");
        M.put("Újrapróbálás", "Retry");
        M.put("Termux megnyitása", "Open Termux");
        M.put("A Termux nem indítható", "Cannot start Termux");
        M.put("Kézi indítás", "Manual start");
        M.put("Ha az automatikus indítás nem megy, indítsd el kézzel a Termuxban, majd koppints az Újrapróbálás gombra:", "If automatic start fails, start it manually in Termux, then tap Retry:");
        M.put("Nem érem el: ", "Can't reach: ");
        M.put("Fut a motor a gépen (start-pc), és ugyanazon a Wi-Fin vagytok? Első indításkor a Windows tűzfalon engedélyezni kell.", "Is the engine running on that computer (start-pc), and are you on the same Wi-Fi? On first start, allow it in the Windows firewall.");
        M.put("Vissza a tabletre", "Back to this tablet");
        M.put("Hibás cím", "Invalid address");
        M.put("Hiányzik a kód", "Code is missing");
        M.put("Panel hiba: ", "Panel error: ");
        M.put("Előbb jelölj ki szöveget (kijelölés → Claude)", "Select some text first (selection → Claude)");
    }
}
