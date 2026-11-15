package hu.claudetablet;

import android.Manifest;
import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.PictureInPictureParams;
import android.content.res.Configuration;
import android.util.Rational;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.Settings;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Locale;
import org.json.JSONObject;

public class MainActivity extends Activity {

    static final String BASE = "http://127.0.0.1:8765/";
    static final String PING = BASE + "api/projects";
    static final String START_SCRIPT = "/data/data/com.termux/files/home/.claude-tablet/start-server.sh";
    static final String WAKE_SCRIPT = "/data/data/com.termux/files/home/.claude-tablet/wake-server.sh";
    /** ennyi idő után (ms) hozzuk előtérbe a Termuxot, ha a motor nem válaszol (HyperOS fagyasztás) */
    static final long WAKE_AFTER_MS = 5000;
    static final String PERM_RUN = "com.termux.permission.RUN_COMMAND";
    static final String PERM_NOTIF = "android.permission.POST_NOTIFICATIONS";
    static final int REQ_RUN = 1, REQ_NOTIF = 2, REQ_FILE = 3, REQ_MIC = 4;
    static final String PERM_MIC = Manifest.permission.RECORD_AUDIO;
    static final long POLL_MS = 700, TIMEOUT_MS = 25000;

    static final int C_BG = 0xff1a1915, C_FG = 0xffece9e1, C_DIM = 0xffa6a39a,
            C_ACCENT = 0xffd97757, C_CARD = 0xff26251f, C_CODE = 0xff121110;

    final Handler ui = new Handler(Looper.getMainLooper());
    FrameLayout root;
    WebView web;
    View splash, help;
    TextView status;

    boolean webShown = false, polling = false, startTried = false, wakeTried = false, foreground = false;
    boolean resumeCheck = false;
    long pollStart;
    String pendingShare;
    String failReason;
    ValueCallback<Uri[]> fileCb;
    int notifId = 100;

    // ------------------------------------------------------------------ életciklus

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().getDecorView().setBackgroundColor(C_BG);
        root = new FrameLayout(this);
        root.setBackgroundColor(C_BG);
        setContentView(root);

        createChannel();
        buildWebView();
        splash = buildSplash();
        root.addView(splash);

        takeShare(getIntent());
        takeBubble(getIntent());
        startPolling();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        takeBubble(intent);
        if (takeShare(intent) && webShown) {
            web.loadUrl(shareUrl());
            pendingShare = null;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        foreground = true;
        if (web != null) web.onResume();
        ensureAdbWifi();
        if (!BubbleService.running && getSharedPreferences("bubble", MODE_PRIVATE).getBoolean("on", false)
                && Settings.canDrawOverlays(this)) {
            try { BubbleService.start(this); } catch (Exception e) {}
        }
        ui.removeCallbacks(adbWatch); ui.postDelayed(adbWatch, 30000);
        // a súgóképernyőről visszatérve (pl. beállítások) automatikusan újrapróbál
        if (help != null && help.getVisibility() == View.VISIBLE && hasRunPerm() && failReason != null
                && failReason.startsWith("perm")) {
            retry();
        }
        checkServerOnResume();
    }

    // ---- Őrszem: a HyperOS akkor is lefagyaszthatja a Termuxot (és vele a motort), amikor ez az app
    // van előtérben. Amíg látszik a felület, időnként ellenőrizzük; ha a motor nem válaszol, egy háttér-
    // parancs a Termuxnak (start-server.sh – fut motornál azonnal kilép) feloldja a fagyasztást, látható
    // váltás nélkül. Ha ez sem segít, jön a teljes indító/ébresztő folyamat (serverLost).
    static final long WATCH_MS = 8000;
    int watchFails = 0;
    final Runnable watchdog = new Runnable() {
        public void run() { checkServerOnResume(); }
    };

    /** Vezeték nélküli hibakeresés automatikus bekapcsolása (Claude ADB-vezérléséhez).
     *  Egyszer kell engedélyezni:  adb shell pm grant hu.claudetablet android.permission.WRITE_SECURE_SETTINGS */
    final Runnable adbWatch = new Runnable() { public void run() {
        if (ensureAdbWifi()) ui.postDelayed(this, 30000);
    } };

    boolean ensureAdbWifi() {
        if (checkSelfPermission("android.permission.WRITE_SECURE_SETTINGS") != PackageManager.PERMISSION_GRANTED)
            return false;
        try {
            android.content.ContentResolver cr = getContentResolver();
            if (Settings.Global.getInt(cr, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) != 1)
                Settings.Global.putInt(cr, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 1);
            if (Settings.Global.getInt(cr, Settings.Global.ADB_ENABLED, 0) != 1)
                Settings.Global.putInt(cr, Settings.Global.ADB_ENABLED, 1);
            if (Settings.Global.getInt(cr, "adb_wifi_enabled", 0) != 1)
                Settings.Global.putInt(cr, "adb_wifi_enabled", 1);
            return true;
        } catch (Exception e) { return false; }
    }

    void scheduleWatch() {
        ui.removeCallbacks(watchdog);
        if (foreground && webShown) ui.postDelayed(watchdog, WATCH_MS);
    }

    /** Visszatéréskor és időnként: ha a motor leállt vagy lefagyott, ne akadjon meg a felület. */
    void checkServerOnResume() {
        if (!webShown || resumeCheck) { scheduleWatch(); return; }
        if (Build.VERSION.SDK_INT >= 24 && isInPictureInPictureMode()) { scheduleWatch(); return; }
        resumeCheck = true;
        new Thread(new Runnable() {
            public void run() {
                final boolean up = ping() || ping(); // egy ismétlés: rövid akadás ne számítson leállásnak
                ui.post(new Runnable() {
                    public void run() {
                        resumeCheck = false;
                        if (!webShown || isFinishing()) return;
                        if (up) { watchFails = 0; scheduleWatch(); return; }
                        watchFails++;
                        if (watchFails == 1 && termuxInstalled() && hasRunPerm()) {
                            nudgeTermux();   // csendes feloldás
                            ui.removeCallbacks(watchdog);
                            ui.postDelayed(watchdog, 3000);
                        } else {
                            watchFails = 0;
                            serverLost();    // splash + indítás + szükség esetén Termux előtérbe
                        }
                    }
                });
            }
        }).start();
    }

    /** Háttérparancs a Termuxnak: a szolgáltatásindítás feloldja a fagyasztást, a szkript pedig
     *  csak akkor indít motort, ha tényleg nem fut. */
    void nudgeTermux() {
        Intent i = new Intent("com.termux.RUN_COMMAND");
        i.setClassName("com.termux", "com.termux.app.RunCommandService");
        i.putExtra("com.termux.RUN_COMMAND_PATH", START_SCRIPT);
        i.putExtra("com.termux.RUN_COMMAND_BACKGROUND", true);
        try {
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(i);
            else startService(i);
        } catch (Exception ignored) {}
    }

    @Override
    protected void onPause() {
        foreground = false;
        ui.removeCallbacks(watchdog);
        // kép a képben: a WebView látszik, ne álljon le a kirajzolás
        boolean pip = Build.VERSION.SDK_INT >= 24 && isInPictureInPictureMode();
        if (web != null && !pip) web.onPause();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (sr != null) { sr.destroy(); sr = null; }
        if (tts != null) { tts.shutdown(); tts = null; }
        polling = false;
        ui.removeCallbacksAndMessages(null);
        if (web != null) web.destroy();
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (!webShown) { super.onBackPressed(); return; }
        web.evaluateJavascript(
                "(function(){var a=document.getElementById('app');" +
                "if(a&&a.classList.contains('open')&&window.drawer){drawer(false);return 1}return 0})()",
                new ValueCallback<String>() {
                    @Override public void onReceiveValue(String v) {
                        if ("1".equals(v)) return;
                        if (web.canGoBack()) web.goBack();
                        else finish();
                    }
                });
    }

    // ------------------------------------------------------------------ kép a képben + app indítás

    @Override
    public void onPictureInPictureModeChanged(boolean pip, Configuration cfg) {
        super.onPictureInPictureModeChanged(pip, cfg);
        if (web != null) {
            if (pip) web.onResume();
            web.evaluateJavascript("window.onPip&&onPip(" + pip + ")", null);
        }
    }

    /** am start -n hu.claudetablet/.MainActivity --ez bubble true|false */
    void takeBubble(Intent i) {
        if (i != null && i.hasExtra("bubble")) { toggleBubble(i.getBooleanExtra("bubble", true)); i.removeExtra("bubble"); }
    }

    void toggleBubble(boolean on) {
        if (!on) { if (BubbleService.running) BubbleService.stop(this); else getSharedPreferences("bubble", MODE_PRIVATE).edit().putBoolean("on", false).apply(); return; }
        if (!Settings.canDrawOverlays(this)) {
            toast("Engedélyezd a „Megjelenítés más alkalmazások felett” jogot, majd kapcsold be újra");
            try { startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()))); } catch (Exception e) {}
            return;
        }
        if (checkSelfPermission(PERM_MIC) != PackageManager.PERMISSION_GRANTED) requestPermissions(new String[]{PERM_MIC}, REQ_MIC);
        BubbleService.start(this);
    }

    void launchPackage(String pkg) {
        final Intent i = pkg == null ? null : getPackageManager().getLaunchIntentForPackage(pkg);
        if (i == null) {
            web.evaluateJavascript("window.add&&add(" + JSONObject.quote("Nincs ilyen app a tableten: " + pkg) + ",'err')", null);
            return;
        }
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        boolean pip = false;
        if (Build.VERSION.SDK_INT >= 26 && !isInPictureInPictureMode()) {
            try {
                pip = enterPictureInPictureMode(new PictureInPictureParams.Builder()
                        .setAspectRatio(new Rational(3, 4)).build());
            } catch (Exception e) { pip = false; }
        }
        // PiP-be ugrás után nyitjuk az appot, így az kerül alá teljes képernyőn
        ui.postDelayed(new Runnable() { public void run() {
            try { startActivity(i); } catch (Exception e) { toast("Nem indítható: " + e.getMessage()); }
        } }, pip ? 350 : 0);
    }

    // ------------------------------------------------------------------ megosztás

    boolean takeShare(Intent i) {
        if (i == null || !Intent.ACTION_SEND.equals(i.getAction())) return false;
        CharSequence t = i.getCharSequenceExtra(Intent.EXTRA_TEXT);
        String s = i.getStringExtra(Intent.EXTRA_SUBJECT);
        String text = t == null ? "" : t.toString();
        if (s != null && !s.isEmpty() && !text.contains(s)) text = text.isEmpty() ? s : s + "\n" + text;
        if (text.isEmpty()) return false;
        pendingShare = text;
        return true;
    }

    String shareUrl() {
        try {
            return BASE + "?share=" + URLEncoder.encode(pendingShare, "UTF-8").replace("+", "%20");
        } catch (Exception e) {
            return BASE;
        }
    }

    // ------------------------------------------------------------------ WebView

    void buildWebView() {
        web = new WebView(this);
        web.setBackgroundColor(C_BG);
        web.setVisibility(View.INVISIBLE);
        WebSettings ws = web.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setDatabaseEnabled(true);
        ws.setMediaPlaybackRequiresUserGesture(false);
        ws.setAllowFileAccess(false);
        ws.setSupportZoom(false);
        ws.setTextZoom(100);
        ws.setUserAgentString(ws.getUserAgentString() + " ClaudeTabletApp/1.0");
        web.addJavascriptInterface(new Bridge(), "ClaudeApp");

        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                Uri u = r.getUrl();
                if (isLocal(u)) return false;
                openExternal(u);
                return true;
            }

            @Override
            public void onReceivedError(WebView v, WebResourceRequest r, WebResourceError e) {
                // a szerver leállt: vissza az indítóképernyőre
                if (r.isForMainFrame() && isLocal(r.getUrl())) {
                    ui.post(new Runnable() { public void run() { serverLost(); } });
                }
            }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onConsoleMessage(android.webkit.ConsoleMessage m) {
                android.util.Log.i("ClaudeTabletJS", m.message() + " @" + m.lineNumber());
                return true;
            }
            @Override
            public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> cb, FileChooserParams p) {
                android.util.Log.i("ClaudeTablet", "fileChooser " + java.util.Arrays.toString(p.getAcceptTypes()) + " mode=" + p.getMode());
                if (fileCb != null) fileCb.onReceiveValue(null);
                fileCb = cb;
                try {
                    startActivityForResult(p.createIntent(), REQ_FILE);
                } catch (Exception e) {
                    android.util.Log.e("ClaudeTablet", "fileChooser hiba", e);
                    fileCb = null;
                    return false;
                }
                return true;
            }
        });
        root.addView(web, new FrameLayout.LayoutParams(-1, -1));
    }

    static boolean isLocal(Uri u) {
        String h = u.getHost();
        String sch = u.getScheme();
        if (sch != null && (sch.equals("javascript") || sch.equals("about") || sch.equals("data") || sch.equals("blob")))
            return true;
        return h != null && (h.equals("127.0.0.1") || h.equals("localhost"));
    }

    void openExternal(Uri u) {
        try {
            Intent i = new Intent(Intent.ACTION_VIEW, u);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch (Exception e) {
            toast("Nem nyitható meg: " + u);
        }
    }

    void showWeb() {
        if (webShown) return;
        webShown = true;
        polling = false;
        String url = pendingShare != null ? shareUrl() : BASE;
        pendingShare = null;
        web.loadUrl(url);
        web.setVisibility(View.VISIBLE);
        web.setAlpha(0f);
        web.animate().alpha(1f).setDuration(250).start();
        if (splash != null) splash.setVisibility(View.GONE);
        if (help != null) help.setVisibility(View.GONE);
        web.requestFocus();
        askNotificationPermission();
        watchFails = 0;
        scheduleWatch();
    }

    void serverLost() {
        if (!webShown) return;
        webShown = false;
        ui.removeCallbacks(watchdog);
        web.setVisibility(View.INVISIBLE);
        web.stopLoading();
        startTried = false;
        wakeTried = false;
        splash.setVisibility(View.VISIBLE);
        startPolling();
    }

    // ------------------------------------------------------------------ szerver keresés / indítás

    void startPolling() {
        if (help != null) help.setVisibility(View.GONE);
        splash.setVisibility(View.VISIBLE);
        setStatus("Claude motor indítása…");
        polling = true;
        pollStart = System.currentTimeMillis();
        pollOnce();
    }

    void pollOnce() {
        if (!polling) return;
        new Thread(new Runnable() {
            public void run() {
                final boolean up = ping();
                ui.post(new Runnable() {
                    public void run() { onPoll(up); }
                });
            }
        }).start();
    }

    static boolean ping() {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(PING).openConnection();
            c.setConnectTimeout(600);
            c.setReadTimeout(1500);
            c.setUseCaches(false);
            int code = c.getResponseCode();
            return code > 0 && code < 500;
        } catch (Exception e) {
            return false;
        } finally {
            if (c != null) c.disconnect();
        }
    }

    void onPoll(boolean up) {
        if (!polling || isFinishing()) return;
        if (up) { showWeb(); return; }
        if (!startTried) {
            startTried = true;
            if (!tryStartServer()) return; // engedélykérés vagy hiba: onRequestPermissionsResult folytatja
        }
        long el = System.currentTimeMillis() - pollStart;
        // A motor nem válaszol: valószínűleg a rendszer lefagyasztotta a háttérben lévő Termuxot.
        // Előtérbe hozzuk (ez feloldja), a wake-server.sh pedig visszahozza ezt az appot.
        if (!wakeTried && el > WAKE_AFTER_MS && termuxInstalled() && hasRunPerm()) {
            wakeTried = true;
            if (wakeTermux()) {
                pollStart = System.currentTimeMillis(); // új esély a teljes időkorlátra
                ui.postDelayed(new Runnable() { public void run() { pollOnce(); } }, POLL_MS);
                return;
            }
        }
        if (el > TIMEOUT_MS) {
            showHelp("timeout");
            return;
        }
        if (el > 6000) setStatus("Claude motor indítása… (" + (el / 1000) + " mp)");
        ui.postDelayed(new Runnable() { public void run() { pollOnce(); } }, POLL_MS);
    }

    boolean termuxInstalled() {
        try {
            getPackageManager().getPackageInfo("com.termux", 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    boolean hasRunPerm() {
        return checkSelfPermission(PERM_RUN) == PackageManager.PERMISSION_GRANTED;
    }

    /** @return true, ha a lekérdezés folytatódhat */
    boolean tryStartServer() {
        if (!termuxInstalled()) { showHelp("notermux"); return false; }
        if (!hasRunPerm()) {
            setStatus("Engedély szükséges a Termux parancs futtatásához…");
            requestPermissions(new String[]{PERM_RUN}, REQ_RUN);
            return false;
        }
        return sendRunCommand();
    }

    boolean sendRunCommand() {
        Intent i = new Intent("com.termux.RUN_COMMAND");
        i.setClassName("com.termux", "com.termux.app.RunCommandService");
        i.putExtra("com.termux.RUN_COMMAND_PATH", START_SCRIPT);
        i.putExtra("com.termux.RUN_COMMAND_BACKGROUND", true);
        i.putExtra("com.termux.RUN_COMMAND_SESSION_ACTION", "0");
        try {
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(i);
            else startService(i);
            setStatus("Claude motor indítása…");
            return true;
        } catch (SecurityException e) {
            showHelp("perm");
        } catch (Exception e) {
            showHelp("error:" + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
        return false;
    }

    /** Termux előtérbe hozása + ébresztő szkript. @return true, ha sikerült elküldeni */
    boolean wakeTermux() {
        Intent launch = getPackageManager().getLaunchIntentForPackage("com.termux");
        if (launch == null) return false;
        setStatus("A Termux felébresztése…\nHa nem tér vissza magától, lépj vissza a Claude Tablet appba.");
        Intent i = new Intent("com.termux.RUN_COMMAND");
        i.setClassName("com.termux", "com.termux.app.RunCommandService");
        i.putExtra("com.termux.RUN_COMMAND_PATH", WAKE_SCRIPT);
        i.putExtra("com.termux.RUN_COMMAND_BACKGROUND", true);
        try {
            // előbb az activity: így a Termux előtérbe kerül és feloldódik, a parancs utána fut le
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(launch);
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(i);
            else startService(i);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public void onRequestPermissionsResult(int req, String[] perms, int[] res) {
        if (req == REQ_MIC) {
            if (res.length > 0 && res[0] == PackageManager.PERMISSION_GRANTED) startRec(pendingLang);
            else voiceEvent("error", "Nincs mikrofon-engedély. Beállítások → Alkalmazások → Claude Tablet → Engedélyek.");
            return;
        }
        if (req == REQ_RUN) {
            boolean ok = res.length > 0 && res[0] == PackageManager.PERMISSION_GRANTED;
            if (!ok) { showHelp("perm-denied"); return; }
            if (sendRunCommand()) {
                pollStart = System.currentTimeMillis();
                polling = true;
                pollOnce();
            }
        }
    }

    @Override
    protected void onActivityResult(int req, int code, Intent data) {
        if (req == REQ_FILE) {
            Uri[] res = null;
            if (code == RESULT_OK && data != null) {
                // a Photo Picker több kép esetén ClipData-ban ad vissza – ezt a parseResult nem kezeli
                ClipData cd = data.getClipData();
                if (cd != null && cd.getItemCount() > 0) {
                    res = new Uri[cd.getItemCount()];
                    for (int i = 0; i < res.length; i++) res[i] = cd.getItemAt(i).getUri();
                } else if (data.getData() != null) {
                    res = new Uri[]{ data.getData() };
                }
            }
            android.util.Log.i("ClaudeTablet", "fileResult code=" + code + " n=" + (res == null ? 0 : res.length) + " cb=" + (fileCb != null));
            if (fileCb != null) {
                fileCb.onReceiveValue(res);
                fileCb = null;
            }
            return;
        }
        super.onActivityResult(req, code, data);
    }

    void retry() {
        startTried = false;
        wakeTried = false;
        startPolling();
    }

    // ------------------------------------------------------------------ értesítések

    void createChannel() {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationChannel ch = new NotificationChannel("claude", "Claude válaszok",
                NotificationManager.IMPORTANCE_HIGH);
        ch.setDescription("Értesítés, amikor Claude befejezte a választ");
        ch.enableVibration(true);
        ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(ch);
    }

    void askNotificationPermission() {
        if (Build.VERSION.SDK_INT < 33) return;
        if (checkSelfPermission(PERM_NOTIF) == PackageManager.PERMISSION_GRANTED) return;
        SharedPreferences p = getSharedPreferences("app", MODE_PRIVATE);
        if (p.getBoolean("notifAsked", false)) return;
        p.edit().putBoolean("notifAsked", true).apply();
        requestPermissions(new String[]{PERM_NOTIF}, REQ_NOTIF);
    }

    void postNotification(String title, String body) {
        if (foreground) return;
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(PERM_NOTIF) != PackageManager.PERMISSION_GRANTED)
            return;
        Intent open = new Intent(this, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        open.setAction(Intent.ACTION_MAIN);
        open.addCategory(Intent.CATEGORY_LAUNCHER);
        PendingIntent pi = PendingIntent.getActivity(this, 0, open,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, "claude")
                : new Notification.Builder(this);
        b.setSmallIcon(R.drawable.ic_stat_claude)
                .setColor(C_ACCENT)
                .setContentTitle(title == null || title.isEmpty() ? "Claude" : title)
                .setContentText(body == null ? "" : body)
                .setStyle(new Notification.BigTextStyle().bigText(body == null ? "" : body))
                .setAutoCancel(true)
                .setContentIntent(pi);
        if (Build.VERSION.SDK_INT < 26) b.setPriority(Notification.PRIORITY_HIGH).setDefaults(Notification.DEFAULT_ALL);
        ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).notify(notifId++, b.build());
    }

    void doVibrate() {
        Vibrator v = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        if (v == null || !v.hasVibrator()) return;
        if (Build.VERSION.SDK_INT >= 26) v.vibrate(VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE));
        else v.vibrate(30);
    }

    // ------------------------------------------------------------------ Hang: beszédfelismerés + felolvasás
    SpeechRecognizer sr;
    TextToSpeech tts;
    boolean ttsReady;
    String pendingLang = "hu-HU", pendingSpeak;
    long lastLevel;

    void voiceEvent(String type, String text) {
        if (web == null) return;
        web.evaluateJavascript("window.onVoice&&onVoice(" + JSONObject.quote(type) + "," + JSONObject.quote(text == null ? "" : text) + ")", null);
    }

    void startRec(String lang) {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            voiceEvent("error", "Nincs beszédfelismerő a tableten. Telepítsd/frissítsd a Google appot vagy a Speech Services-t.");
            return;
        }
        if (sr == null) {
            sr = SpeechRecognizer.createSpeechRecognizer(this);
            sr.setRecognitionListener(new RecognitionListener() {
                public void onReadyForSpeech(Bundle b) { voiceEvent("ready", ""); }
                public void onBeginningOfSpeech() { voiceEvent("begin", ""); }
                public void onRmsChanged(float db) {
                    long now = System.currentTimeMillis();
                    if (now - lastLevel > 90) { lastLevel = now; voiceEvent("level", String.valueOf(db)); }
                }
                public void onBufferReceived(byte[] b) {}
                public void onEndOfSpeech() { voiceEvent("endspeech", ""); }
                public void onError(int e) { voiceEvent("error", recError(e)); }
                public void onResults(Bundle b) { voiceEvent("final", first(b)); }
                public void onPartialResults(Bundle b) { voiceEvent("partial", first(b)); }
                public void onEvent(int t, Bundle b) {}
            });
        }
        Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, lang);
        i.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        i.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
        i.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, getPackageName());
        i.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2500L);
        i.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L);
        try { sr.cancel(); sr.startListening(i); }
        catch (Exception ex) { voiceEvent("error", "Nem indítható a felismerés: " + ex.getMessage()); }
    }

    static String first(Bundle b) {
        ArrayList<String> r = b == null ? null : b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        return r == null || r.isEmpty() ? "" : r.get(0);
    }

    static String recError(int e) {
        switch (e) {
            case SpeechRecognizer.ERROR_NO_MATCH: return "Nem értettem, próbáld újra.";
            case SpeechRecognizer.ERROR_SPEECH_TIMEOUT: return "Nem hallottam semmit.";
            case SpeechRecognizer.ERROR_NETWORK:
            case SpeechRecognizer.ERROR_NETWORK_TIMEOUT: return "Hálózati hiba a felismerésnél.";
            case SpeechRecognizer.ERROR_AUDIO: return "Mikrofonhiba.";
            case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS: return "Nincs mikrofon-engedély.";
            case SpeechRecognizer.ERROR_RECOGNIZER_BUSY: return "A felismerő foglalt, próbáld újra.";
            case SpeechRecognizer.ERROR_CLIENT: return "cancel";
            default: return "Felismerési hiba (" + e + ").";
        }
    }

    void speakNow(String text) {
        if (tts == null) {
            pendingSpeak = text;
            tts = new TextToSpeech(this, new TextToSpeech.OnInitListener() {
                public void onInit(int st) {
                    ttsReady = st == TextToSpeech.SUCCESS;
                    if (!ttsReady) { voiceEvent("error", "A felolvasó nem érhető el."); return; }
                    tts.setLanguage(new Locale("hu", "HU"));
                    if (pendingSpeak != null) { String t = pendingSpeak; pendingSpeak = null; speakNow(t); }
                }
            });
            return;
        }
        if (!ttsReady) { pendingSpeak = text; return; }
        tts.stop();
        int max = Math.max(500, TextToSpeech.getMaxSpeechInputLength() - 10);
        for (int i = 0; i < text.length(); i += max)
            tts.speak(text.substring(i, Math.min(text.length(), i + max)), TextToSpeech.QUEUE_ADD, null, "ct" + i);
    }

    /** JavaScript híd: window.ClaudeApp */
    class Bridge {
        @JavascriptInterface public boolean isApp() { return true; }

        @JavascriptInterface public void notify(final String title, final String body) {
            ui.post(new Runnable() { public void run() { postNotification(title, body); } });
        }

        @JavascriptInterface public boolean hasVoice() { return SpeechRecognizer.isRecognitionAvailable(MainActivity.this); }

        @JavascriptInterface public void startListening(final String lang) {
            ui.post(new Runnable() { public void run() {
                pendingLang = lang == null || lang.isEmpty() ? "hu-HU" : lang;
                if (Build.VERSION.SDK_INT >= 23 && checkSelfPermission(PERM_MIC) != PackageManager.PERMISSION_GRANTED)
                    requestPermissions(new String[]{PERM_MIC}, REQ_MIC);
                else startRec(pendingLang);
            } });
        }

        @JavascriptInterface public void stopListening() {
            ui.post(new Runnable() { public void run() { if (sr != null) sr.stopListening(); } });
        }

        @JavascriptInterface public void cancelListening() {
            ui.post(new Runnable() { public void run() { if (sr != null) sr.cancel(); } });
        }

        @JavascriptInterface public void speak(final String text) {
            ui.post(new Runnable() { public void run() { if (text != null && !text.trim().isEmpty()) speakNow(text); } });
        }

        @JavascriptInterface public void stopSpeaking() {
            ui.post(new Runnable() { public void run() { if (tts != null) tts.stop(); } });
        }

        @JavascriptInterface public void launchApp(final String pkg) {
            ui.post(new Runnable() { public void run() { launchPackage(pkg); } });
        }

        @JavascriptInterface public boolean bubbleOn() { return BubbleService.running; }
        @JavascriptInterface public void setBubble(final boolean on) {
            ui.post(new Runnable() { public void run() { toggleBubble(on); } });
        }
        @JavascriptInterface public void vibrate() {
            ui.post(new Runnable() { public void run() { doVibrate(); } });
        }
    }

    // ------------------------------------------------------------------ UI építés

    int dp(float v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }

    void setStatus(String s) { if (status != null) status.setText(s); }

    void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }

    View buildSplash() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setGravity(Gravity.CENTER);
        l.setBackgroundColor(C_BG);
        l.setClickable(true);

        ImageView icon = new ImageView(this);
        icon.setImageResource(R.drawable.splash_icon);
        l.addView(icon, new LinearLayout.LayoutParams(dp(112), dp(112)));

        TextView name = text("Claude Tablet", 26, C_FG, true);
        name.setPadding(0, dp(20), 0, dp(28));
        l.addView(name);

        ProgressBar pb = new ProgressBar(this);
        pb.setIndeterminateTintList(ColorStateList.valueOf(C_ACCENT));
        l.addView(pb, new LinearLayout.LayoutParams(dp(40), dp(40)));

        status = text("Claude motor indítása…", 16, C_DIM, false);
        status.setPadding(dp(24), dp(18), dp(24), 0);
        status.setGravity(Gravity.CENTER);
        l.addView(status);
        return l;
    }

    TextView text(String s, float sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    GradientDrawable round(int color, float r) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(r));
        return g;
    }

    Button button(String s, boolean primary, View.OnClickListener cl) {
        Button b = new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextSize(15);
        b.setTextColor(primary ? Color.WHITE : C_FG);
        b.setBackground(round(primary ? C_ACCENT : C_CARD, 10));
        b.setPadding(dp(18), dp(10), dp(18), dp(10));
        b.setOnClickListener(cl);
        return b;
    }

    View code(final String cmd) {
        TextView t = text(cmd, 14, C_ACCENT, false);
        t.setTypeface(Typeface.MONOSPACE);
        t.setTextIsSelectable(true);
        t.setBackground(round(C_CODE, 8));
        t.setPadding(dp(12), dp(10), dp(12), dp(10));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, dp(6), 0, dp(4));
        t.setLayoutParams(lp);
        t.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                cm.setPrimaryClip(ClipData.newPlainText("parancs", cmd));
                toast("Parancs vágólapra másolva");
            }
        });
        return t;
    }

    void step(LinearLayout parent, String num, String title, String body) {
        TextView h = text(num + "  " + title, 17, C_FG, true);
        h.setPadding(0, dp(18), 0, dp(2));
        parent.addView(h);
        if (body != null) {
            TextView b = text(body, 14, C_DIM, false);
            b.setLineSpacing(0, 1.2f);
            parent.addView(b);
        }
    }

    void showHelp(String reason) {
        polling = false;
        failReason = reason;
        if (help != null) root.removeView(help);
        splash.setVisibility(View.GONE);

        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(C_BG);
        sv.setFillViewport(true);
        FrameLayout center = new FrameLayout(this);
        sv.addView(center);
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(28), dp(28), dp(28), dp(28));
        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(Math.min(dp(640),
                getResources().getDisplayMetrics().widthPixels), -2, Gravity.CENTER_HORIZONTAL);
        center.addView(c, clp);

        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        ImageView icon = new ImageView(this);
        icon.setImageResource(R.drawable.splash_icon);
        head.addView(icon, new LinearLayout.LayoutParams(dp(52), dp(52)));
        TextView t = text("Nem sikerült elindítani a Claude motort", 21, C_FG, true);
        t.setPadding(dp(16), 0, 0, 0);
        head.addView(t, new LinearLayout.LayoutParams(0, -2, 1));
        c.addView(head);

        String why;
        if (reason.equals("notermux")) why = "A Termux alkalmazás nincs telepítve ezen az eszközön.";
        else if (reason.startsWith("perm")) why = "Az alkalmazásnak nincs engedélye parancsot futtatni a Termuxban.";
        else if (reason.equals("timeout")) why = "A szerver 25 másodperc alatt sem válaszolt (127.0.0.1:8765). " +
                "Lehet, hogy a Termux nem engedi külső alkalmazások parancsait, vagy hiányzik az indító szkript.";
        else why = "Hiba a Termux hívásakor: " + reason.replaceFirst("^error:", "");
        TextView w = text(why, 15, C_DIM, false);
        w.setPadding(0, dp(16), 0, dp(4));
        w.setLineSpacing(0, 1.2f);
        c.addView(w);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(round(C_CARD, 14));
        card.setPadding(dp(20), dp(4), dp(20), dp(20));
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(-1, -2);
        cardLp.setMargins(0, dp(16), 0, 0);
        c.addView(card, cardLp);

        step(card, "1.", "Termux telepítve", "Telepítsd a Termuxot (F-Droid vagy GitHub verzió), és benne a PRoot Ubuntut a Claude-dal.");
        if (!termuxInstalled()) {
            card.addView(button("Termux letöltése (F-Droid)", false, new View.OnClickListener() {
                public void onClick(View v) { openExternal(Uri.parse("https://f-droid.org/packages/com.termux/")); }
            }));
        }
        step(card, "2.", "Külső alkalmazások engedélyezése a Termuxban", "Futtasd a Termuxban (koppintás = másolás):");
        card.addView(code("echo \"allow-external-apps=true\" >> ~/.termux/termux.properties"));
        card.addView(code("termux-reload-settings"));
        step(card, "3.", "Engedély megadása",
                "Claude Tablet → Engedélyek → További engedélyek → „Parancsok futtatása a Termux környezetben” → Engedélyezés.");
        LinearLayout row3 = new LinearLayout(this);
        row3.setPadding(0, dp(8), 0, 0);
        row3.addView(button("Alkalmazás-beállítások megnyitása", false, new View.OnClickListener() {
            public void onClick(View v) {
                Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", getPackageName(), null));
                startActivity(i);
            }
        }));
        card.addView(row3);
        step(card, "4.", "Újrapróbálás", null);

        LinearLayout row = new LinearLayout(this);
        row.setPadding(0, dp(10), 0, 0);
        row.addView(button("Újrapróbálás", true, new View.OnClickListener() {
            public void onClick(View v) { retry(); }
        }));
        View gap = new View(this);
        row.addView(gap, new LinearLayout.LayoutParams(dp(10), 1));
        if (termuxInstalled()) {
            row.addView(button("Termux megnyitása", false, new View.OnClickListener() {
                public void onClick(View v) {
                    Intent i = getPackageManager().getLaunchIntentForPackage("com.termux");
                    if (i != null) startActivity(i); else toast("A Termux nem indítható");
                }
            }));
        }
        card.addView(row);

        TextView mh = text("Kézi indítás", 17, C_FG, true);
        mh.setPadding(0, dp(24), 0, dp(2));
        c.addView(mh);
        TextView mb = text("Ha az automatikus indítás nem megy, indítsd el kézzel a Termuxban, majd koppints az Újrapróbálás gombra:",
                14, C_DIM, false);
        mb.setLineSpacing(0, 1.2f);
        c.addView(mb);
        c.addView(code("~/.claude-tablet/start-server.sh"));

        help = sv;
        root.addView(help, new FrameLayout.LayoutParams(-1, -1));
    }
}
