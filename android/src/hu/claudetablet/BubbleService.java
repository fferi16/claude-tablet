package hu.claudetablet;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.Settings;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Locale;
import org.json.JSONObject;

/** Lebegő Claude-gomb: minden app fölött ott van; koppintás = diktálás, a válasz egy kis panelben jelenik meg
 *  (és felolvasható). Kéréskor képernyőképet készít (ADB, a szerveren át), így Claude „látja”, mi van a képernyőn. */
public class BubbleService extends Service {

    static final String BASE = MainActivity.BASE;
    static final String CH = "bubble";
    static final String ACTION_STOP = "hu.claudetablet.STOP";
    static final String ACTION_TEXT = "hu.claudetablet.TEXT";
    static final String ACTION_IMAGE = "hu.claudetablet.IMAGE";
    static final String ACTION_LISTEN = "hu.claudetablet.LISTEN";
    /** gyors gombok alapértéke – soronként: Felirat | utasítás (a beállításokban szerkeszthető) */
    static final String DEFAULT_CHIPS =
            "Foglald össze | Foglald össze röviden.\n" +
            "Fordítsd le | Fordítsd le magyarra (ha már magyar, akkor angolra). Csak a fordítást add vissza.\n" +
            "Magyarázd el | Magyarázd el egyszerűen, röviden.\n" +
            "Javítsd | Javítsd ki a helyesírást és a fogalmazást. Csak a javított szöveget add vissza, semmi mást.\n" +
            "Mi ez? | Mi ez? Röviden.";
    static volatile boolean running = false;
    static volatile BubbleService inst;

    static final int C_BG = 0xff1a1915, C_FG = 0xffece9e1, C_DIM = 0xffa6a39a, C_ACCENT = 0xffd97757,
            C_CARD = 0xff26251f, C_REC = 0xffe05252;

    final Handler ui = new Handler(Looper.getMainLooper());
    WindowManager wm;
    SharedPreferences prefs;
    TextView bubble;
    WindowManager.LayoutParams bp, pp;
    LinearLayout panel;
    TextView status, answer, bScreen, bTts, bMic, bSend;
    EditText input;
    ScrollView scroll;
    boolean panelShown = false, listening = false, busy = false, newNext = false;
    String runId;
    SpeechRecognizer sr;
    TextToSpeech tts;
    boolean ttsReady = false;
    String pendingSpeak;
    StringBuilder all = new StringBuilder(), block = new StringBuilder();
    String lastBlock = "";
    /** más appban kijelölt szöveg (szövegkijelölés menü → Claude) */
    String sel;
    /** megosztott kép (a szerverre feltöltve) */
    String img;
    TextView selView;
    LinearLayout chips;
    String chipsShown;

    // ------------------------------------------------------------------ életciklus

    @Override public IBinder onBind(Intent i) { return null; }

    @Override
    public void onCreate() {
        super.onCreate();
        inst = this;
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        prefs = getSharedPreferences("bubble", MODE_PRIVATE);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) { stopAll(); return START_NOT_STICKY; }
        goForeground();
        if (!Settings.canDrawOverlays(this)) {
            toast("Engedélyezd: Megjelenítés más alkalmazások felett");
            Intent s = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, android.net.Uri.parse("package:" + getPackageName()));
            s.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try { startActivity(s); } catch (Exception e) {}
            stopAll();
            return START_NOT_STICKY;
        }
        if (bubble == null) { buildBubble(); buildPanel(); }
        running = true;
        if (intent != null && ACTION_TEXT.equals(intent.getAction())) setSelection(intent.getStringExtra("text"));
        if (intent != null && ACTION_IMAGE.equals(intent.getAction())) setImage(intent.getStringExtra("path"));
        if (intent != null && ACTION_LISTEN.equals(intent.getAction())) { showPanel(); if (!busy && !listening) startRec(); }
        prefs.edit().putBoolean("on", true).apply();
        return START_STICKY;
    }

    void stopAll() {
        prefs.edit().putBoolean("on", false).apply();
        running = false;
        stopForeground(true);
        stopSelf();
    }

    @Override
    public void onDestroy() {
        running = false;
        inst = null;
        try { if (bubble != null) wm.removeView(bubble); } catch (Exception e) {}
        try { if (panelShown) wm.removeView(panel); } catch (Exception e) {}
        if (sr != null) sr.destroy();
        if (tts != null) tts.shutdown();
        super.onDestroy();
    }

    void goForeground() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26 && nm.getNotificationChannel(CH) == null) {
            NotificationChannel c = new NotificationChannel(CH, "Lebegő Claude-gomb", NotificationManager.IMPORTANCE_MIN);
            c.setShowBadge(false);
            nm.createNotificationChannel(c);
        }
        Intent stop = new Intent(this, BubbleService.class).setAction(ACTION_STOP);
        PendingIntent ps = PendingIntent.getService(this, 1, stop, PendingIntent.FLAG_IMMUTABLE);
        PendingIntent open = PendingIntent.getActivity(this, 2, new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, CH) : new Notification.Builder(this);
        b.setSmallIcon(R.drawable.ic_stat_claude).setContentTitle("Lebegő Claude-gomb")
                .setContentText("Koppints a gombra bármelyik appban").setContentIntent(open).setOngoing(true)
                .addAction(new Notification.Action.Builder(null, "Kikapcsolás", ps).build());
        if (Build.VERSION.SDK_INT >= 29) startForeground(7, b.build(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
        else startForeground(7, b.build());
    }

    // ------------------------------------------------------------------ lebegő gomb

    int dp(float v) { return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics()); }
    int type() { return Build.VERSION.SDK_INT >= 26 ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : WindowManager.LayoutParams.TYPE_PHONE; }

    GradientDrawable round(int color, float radiusDp) {
        GradientDrawable g = new GradientDrawable(); g.setColor(color); g.setCornerRadius(dp(radiusDp)); return g;
    }

    void buildBubble() {
        bubble = new TextView(this);
        bubble.setText("✳");
        bubble.setTextColor(Color.WHITE);
        bubble.setTextSize(28);
        bubble.setGravity(Gravity.CENTER);
        GradientDrawable g = new GradientDrawable(); g.setShape(GradientDrawable.OVAL); g.setColor(C_ACCENT);
        g.setStroke(dp(2), 0x55ffffff);
        bubble.setBackground(g);
        bubble.setElevation(dp(6));
        int s = dp(60);
        bp = new WindowManager.LayoutParams(s, s, type(),
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        bp.gravity = Gravity.TOP | Gravity.START;
        DisplayMetrics m = getResources().getDisplayMetrics();
        bp.x = prefs.getInt("x", m.widthPixels - s - dp(8));
        bp.y = prefs.getInt("y", m.heightPixels / 2);
        bubble.setOnTouchListener(new View.OnTouchListener() {
            float sx, sy; int ox, oy; boolean moved; long t0;
            final Runnable longPress = new Runnable() { public void run() { if (!moved) { moved = true; vibrate(); onBubbleLong(); } } };
            public boolean onTouch(View v, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        sx = e.getRawX(); sy = e.getRawY(); ox = bp.x; oy = bp.y; moved = false; t0 = System.currentTimeMillis();
                        ui.postDelayed(longPress, 550);
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        float dx = e.getRawX() - sx, dy = e.getRawY() - sy;
                        if (!moved && Math.hypot(dx, dy) > dp(10)) { moved = true; ui.removeCallbacks(longPress); }
                        if (moved && System.currentTimeMillis() - t0 > 0) {
                            bp.x = ox + (int) dx; bp.y = oy + (int) dy;
                            try { wm.updateViewLayout(bubble, bp); } catch (Exception ex) {}
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                        ui.removeCallbacks(longPress);
                        if (!moved) onBubbleTap();
                        else snapToEdge();
                        return true;
                    case MotionEvent.ACTION_CANCEL:
                        ui.removeCallbacks(longPress); return true;
                }
                return false;
            }
        });
        wm.addView(bubble, bp);
    }

    void snapToEdge() {
        DisplayMetrics m = getResources().getDisplayMetrics();
        int s = bp.width;
        bp.x = bp.x + s / 2 < m.widthPixels / 2 ? dp(4) : m.widthPixels - s - dp(4);
        bp.y = Math.max(dp(24), Math.min(bp.y, m.heightPixels - s - dp(24)));
        try { wm.updateViewLayout(bubble, bp); } catch (Exception e) {}
        prefs.edit().putInt("x", bp.x).putInt("y", bp.y).apply();
        if (panelShown) placePanel();
    }

    /** koppintás: panel gépeléshez (nem indul a diktálás); ha a panel nyitva és nem fut semmi: bezárja */
    void onBubbleTap() {
        if (listening) { stopRec(); return; }
        if (panelShown && !busy) { hidePanel(); return; }
        showPanel();
        if (!busy) focusInput(true);
    }

    /** hosszú nyomás: panel + azonnal diktálás */
    void onBubbleLong() {
        showPanel();
        if (!busy) startRec();
    }

    // ------------------------------------------------------------------ panel

    TextView btn(String label, View.OnClickListener l) {
        TextView b = new TextView(this);
        b.setText(label); b.setTextColor(C_FG); b.setTextSize(18); b.setGravity(Gravity.CENTER);
        b.setBackground(round(C_CARD, 10));
        b.setPadding(dp(10), dp(6), dp(10), dp(6));
        b.setOnClickListener(l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, dp(42));
        lp.leftMargin = dp(6);
        b.setLayoutParams(lp);
        return b;
    }

    void buildPanel() {
        panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackground(round(C_BG, 18));
        panel.setPadding(dp(12), dp(10), dp(12), dp(12));
        panel.setElevation(dp(10));

        // fejléc: állapot + gombok (a fejléc húzásával a panel mozgatható)
        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        status = new TextView(this);
        status.setTextColor(C_DIM); status.setTextSize(14);
        status.setText("Claude");
        head.addView(status, new LinearLayout.LayoutParams(0, -2, 1));
        bScreen = btn("", new View.OnClickListener() { public void onClick(View v) { prefs.edit().putBoolean("screen", !prefs.getBoolean("screen", true)).apply(); updateToggles(); } });
        bTts = btn("", new View.OnClickListener() { public void onClick(View v) {
            boolean on = !prefs.getBoolean("tts", true);
            prefs.edit().putBoolean("tts", on).apply();
            if (!on && tts != null) tts.stop();
            updateToggles();
        } });
        head.addView(bScreen); head.addView(bTts);
        head.addView(btn("＋", new View.OnClickListener() { public void onClick(View v) { newNext = true; all.setLength(0); answer.setText(""); setStatus("Új beszélgetés"); } }));
        head.addView(btn("Ki", new View.OnClickListener() { public void onClick(View v) { stopAll(); } }));
        head.addView(btn("✕", new View.OnClickListener() { public void onClick(View v) { hidePanel(); } }));
        head.setOnTouchListener(new View.OnTouchListener() {
            float sy; int oy;
            public boolean onTouch(View v, MotionEvent e) {
                if (e.getActionMasked() == MotionEvent.ACTION_DOWN) { sy = e.getRawY(); oy = pp.y; return true; }
                if (e.getActionMasked() == MotionEvent.ACTION_MOVE) {
                    pp.y = Math.max(0, oy + (int) (e.getRawY() - sy));
                    try { wm.updateViewLayout(panel, pp); } catch (Exception ex) {}
                    return true;
                }
                return e.getActionMasked() == MotionEvent.ACTION_UP;
            }
        });
        panel.addView(head);

        answer = new TextView(this);
        answer.setTextColor(C_FG); answer.setTextSize(16);
        answer.setLineSpacing(dp(2), 1f);
        answer.setTextIsSelectable(true);
        answer.setPadding(dp(4), dp(8), dp(4), dp(8));
        scroll = new ScrollView(this);
        scroll.addView(answer);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(-1, 0, 1);
        panel.addView(scroll, slp);

        // kijelölt szöveg előnézete (koppintásra törlődik)
        selView = new TextView(this);
        selView.setTextColor(C_DIM); selView.setTextSize(13);
        selView.setBackground(round(C_CARD, 10));
        selView.setPadding(dp(10), dp(6), dp(10), dp(6));
        selView.setMaxLines(3);
        selView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        selView.setVisibility(View.GONE);
        selView.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { img = null; setSelection(null); } });
        LinearLayout.LayoutParams svl = new LinearLayout.LayoutParams(-1, -2);
        svl.topMargin = dp(4);
        panel.addView(selView, svl);

        // gyors gombok
        chips = new LinearLayout(this);
        chips.setGravity(Gravity.CENTER_VERTICAL);
        android.widget.HorizontalScrollView hs = new android.widget.HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        hs.addView(chips);
        LinearLayout.LayoutParams hl = new LinearLayout.LayoutParams(-1, -2);
        hl.topMargin = dp(6);
        panel.addView(hs, hl);
        buildChips();
        TextView copy = btn("📋", new View.OnClickListener() { public void onClick(View v) { copyAnswer(); } });
        copyBtn = copy;

        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        input = new EditText(this);
        input.setTextColor(C_FG); input.setHintTextColor(C_DIM); input.setTextSize(16);
        input.setHint("Írj, vagy diktálj a 🎤-val…");
        input.setBackground(round(C_CARD, 12));
        input.setPadding(dp(12), dp(8), dp(12), dp(8));
        input.setMaxLines(4);
        input.setImeOptions(EditorInfo.IME_ACTION_SEND);
        input.setOnEditorActionListener(new TextView.OnEditorActionListener() { public boolean onEditorAction(TextView v, int id, android.view.KeyEvent ev) { sendInput(); return true; } });
        input.setOnTouchListener(new View.OnTouchListener() { public boolean onTouch(View v, MotionEvent e) { if (e.getActionMasked() == MotionEvent.ACTION_DOWN) focusInput(true); return false; } });
        row.addView(input, new LinearLayout.LayoutParams(0, -2, 1));
        row.addView(btn("📎", new View.OnClickListener() { public void onClick(View v) {
            if (busy) return;
            focusInput(false);
            Intent i = new Intent(BubbleService.this, ProcessTextActivity.class).setAction(ProcessTextActivity.ACTION_PICK);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try { startActivity(i); } catch (Exception e) { setStatus("Nem nyitható a képválasztó"); }
        } }));
        bMic = btn("🎤", new View.OnClickListener() { public void onClick(View v) { if (listening) stopRec(); else startRec(); } });
        bSend = btn("➤", new View.OnClickListener() { public void onClick(View v) { if (busy) stopRun(); else sendInput(); } });
        row.addView(bMic); row.addView(bSend);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(-1, -2);
        rlp.topMargin = dp(6);
        panel.addView(row, rlp);

        pp = new WindowManager.LayoutParams(dp(440), dp(360), type(),
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                PixelFormat.TRANSLUCENT);
        pp.gravity = Gravity.TOP | Gravity.START;
        pp.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE;
        // kívülre koppintva a billentyűzet-fókusz visszakerül az alatta lévő appra
        panel.setOnTouchListener(new View.OnTouchListener() { public boolean onTouch(View v, MotionEvent e) {
            if (e.getActionMasked() == MotionEvent.ACTION_OUTSIDE) focusInput(false);
            return false;
        } });
        updateToggles();
    }

    void updateToggles() {
        boolean sc = prefs.getBoolean("screen", true), t = prefs.getBoolean("tts", true);
        bScreen.setText(sc ? "📷" : "📷̸");
        bScreen.setAlpha(sc ? 1f : 0.4f);
        bTts.setText(t ? "🔊" : "🔇");
    }

    void placePanel() {
        DisplayMetrics m = getResources().getDisplayMetrics();
        pp.width = Math.min(dp(460), (int) (m.widthPixels * 0.9));
        pp.height = Math.min(dp(420), (int) (m.heightPixels * 0.55));
        boolean right = bp.x + bp.width / 2 > m.widthPixels / 2;
        pp.x = right ? Math.max(0, bp.x - pp.width - dp(8)) : bp.x + bp.width + dp(8);
        pp.y = Math.max(dp(24), Math.min(bp.y - pp.height / 3, m.heightPixels - pp.height - dp(24)));
        if (panelShown) try { wm.updateViewLayout(panel, pp); } catch (Exception e) {}
    }

    void showPanel() {
        if (chips != null) buildChips();
        if (panelShown) return;
        placePanel();
        try { wm.addView(panel, pp); panelShown = true; } catch (Exception e) { toast("Panel hiba: " + e.getMessage()); }
    }

    void hidePanel() {
        if (!panelShown) return;
        if (listening) cancelRec();
        focusInput(false);
        try { wm.removeView(panel); } catch (Exception e) {}
        panelShown = false;
    }

    void focusInput(boolean on) {
        if (!panelShown) return;
        boolean was = (pp.flags & WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE) == 0;
        if (on == was) return;
        if (on) pp.flags &= ~WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        else pp.flags |= WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        try { wm.updateViewLayout(panel, pp); } catch (Exception e) {}
        InputMethodManager im = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (on) { input.requestFocus(); ui.postDelayed(new Runnable() { public void run() { im.showSoftInput(input, 0); } }, 150); }
        else im.hideSoftInputFromWindow(input.getWindowToken(), 0);
    }

    /** a lebegő elemek elrejtése (képernyőkép idejére) */
    void setOverlayVisible(boolean v) {
        int vis = v ? View.VISIBLE : View.INVISIBLE;
        if (bubble != null) bubble.setVisibility(vis);
        if (panel != null) panel.setVisibility(vis);
    }

    void setStatus(String s) { status.setText(s); }

    void setBubbleColor(int c) {
        GradientDrawable g = (GradientDrawable) bubble.getBackground(); g.setColor(c);
    }

    // ------------------------------------------------------------------ diktálás

    void startRec() {
        if (busy) return;
        if (!SpeechRecognizer.isRecognitionAvailable(this)) { setStatus("Nincs beszédfelismerő"); return; }
        if (tts != null) tts.stop();
        if (sr == null) {
            sr = SpeechRecognizer.createSpeechRecognizer(this);
            sr.setRecognitionListener(new RecognitionListener() {
                public void onReadyForSpeech(Bundle b) { setStatus("🎤 Hallgatlak…"); }
                public void onBeginningOfSpeech() {}
                public void onRmsChanged(float db) { bubble.setScaleX(1f + Math.max(0, db) / 40f); bubble.setScaleY(bubble.getScaleX()); }
                public void onBufferReceived(byte[] b) {}
                public void onEndOfSpeech() { setStatus("Feldolgozás…"); }
                public void onError(int e) {
                    recDone();
                    setStatus(e == SpeechRecognizer.ERROR_NO_MATCH || e == SpeechRecognizer.ERROR_SPEECH_TIMEOUT ? "Nem hallottam semmit"
                            : e == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ? "Nincs mikrofon-engedély" : "Diktálási hiba (" + e + ")");
                }
                public void onResults(Bundle b) {
                    recDone();
                    String t = first(b);
                    if (t.isEmpty()) { setStatus("Nem értettem"); return; }
                    input.setText(t);
                    sendInput();
                }
                public void onPartialResults(Bundle b) { String t = first(b); if (!t.isEmpty()) input.setText(t); }
                public void onEvent(int t, Bundle b) {}
            });
        }
        Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hu-HU");
        i.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        i.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L);
        listening = true;
        input.setText("");
        setBubbleColor(C_REC); bMic.setText("■");
        try { sr.startListening(i); } catch (Exception e) { recDone(); setStatus("Diktálási hiba: " + e.getMessage()); }
    }

    void stopRec() { if (sr != null) sr.stopListening(); }
    void cancelRec() { if (sr != null) sr.cancel(); recDone(); }
    void recDone() {
        listening = false;
        bubble.setScaleX(1f); bubble.setScaleY(1f);
        setBubbleColor(C_ACCENT); bMic.setText("🎤");
    }

    static String first(Bundle b) {
        ArrayList<String> r = b == null ? null : b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        return r == null || r.isEmpty() || r.get(0) == null ? "" : r.get(0).trim();
    }

    // ------------------------------------------------------------------ küldés a szervernek

    TextView copyBtn;

    /** gyors gombok újraépítése a beállításokból (prefs "chips") */
    void buildChips() {
        String cfg = prefs.getString("chips", DEFAULT_CHIPS);
        if (cfg.equals(chipsShown)) return;
        chipsShown = cfg;
        chips.removeAllViews();
        for (String line : cfg.split("\n")) {
            int i = line.indexOf('|');
            String label = (i < 0 ? line : line.substring(0, i)).trim();
            String prompt = (i < 0 ? line : line.substring(i + 1)).trim();
            if (label.isEmpty() || prompt.isEmpty()) continue;
            chip(chips, label, prompt);
        }
        if (copyBtn == null) copyBtn = btn("📋", new View.OnClickListener() { public void onClick(View v) { copyAnswer(); } });
        chips.addView(copyBtn);
    }

    void chip(LinearLayout row, String label, final String prompt) {
        TextView b = btn(label, new View.OnClickListener() { public void onClick(View v) {
            if (busy) return;
            // kijelölt szöveg / kép van: arra vonatkozik; különben a képernyőre
            if (sel != null || img != null) ask(prompt, false);
            else ask(prompt + " (A képernyőn látható tartalomról van szó.)", true);
        } });
        b.setTextSize(14);
        row.addView(b);
    }

    void setSelection(String t) {
        sel = t == null || t.trim().isEmpty() ? null : t.trim();
        showAttach();
        if (sel != null) setStatus("Kijelölt szöveg – mit csináljak vele?");
    }

    void setImage(String path) {
        img = path == null || path.isEmpty() ? null : path;
        showAttach();
        if (img != null) setStatus("Kép csatolva – mit csináljak vele?");
    }

    void showAttach() {
        if (sel == null && img == null) { selView.setVisibility(View.GONE); return; }
        String t = (img != null ? "🖼 kép  " : "") + (sel != null ? "✂ " + sel.replace('\n', ' ') : "");
        selView.setText(t + "   ✕");
        selView.setVisibility(View.VISIBLE);
        showPanel();
    }

    void copyAnswer() {
        String t = lastBlock.isEmpty() ? all.toString().trim() : lastBlock;
        if (t.isEmpty()) { setStatus("Nincs mit másolni"); return; }
        android.content.ClipboardManager cm = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        cm.setPrimaryClip(android.content.ClipData.newPlainText("Claude", plain(t)));
        setStatus("Vágólapra másolva ✓");
        vibrate();
    }

    void sendInput() {
        String t = input.getText().toString().trim();
        if (t.isEmpty() && (sel != null || img != null)) t = img != null ? "Mi ez? Röviden." : "Mit gondolsz erről?";
        ask(t, prefs.getBoolean("screen", true) && sel == null && img == null);
    }

    void ask(String q, final boolean screen) {
        if (q == null || q.isEmpty() || busy) return;
        String shown = q;
        if (sel != null) {
            q = q + "\n\nKijelölt szöveg:\n\"\"\"\n" + sel + "\n\"\"\"";
            shown += "  (✂ kijelölt szöveg)";
        }
        if (img != null) {
            q = q + "\n\nCsatolt kép (nézd meg Read-dal): " + img;
            shown += "  (🖼 kép)";
        }
        if (sel != null || img != null) { img = null; setSelection(null); }
        final String text = q;
        focusInput(false);
        input.setText("");
        busy = true; runId = null;
        bSend.setText("■"); bMic.setAlpha(0.4f);
        all.setLength(0); block.setLength(0); lastBlock = "";
        answer.setText("› " + shown + "\n\n");
        final boolean fresh = newNext; newNext = false;
        setStatus(screen ? "📷 Képernyőkép…" : "Gondolkodom…");
        setBubbleColor(0xff8a6bd1);
        if (screen) setOverlayVisible(false);
        // biztonsági háló: ha a szerver nem jelez vissza, 15 mp múlva újra látszik a gomb
        ui.postDelayed(new Runnable() { public void run() { setOverlayVisible(true); } }, 15000);
        new Thread(new Runnable() { public void run() { runRequest(text, screen, fresh, true); } }).start();
    }

    void runRequest(String text, boolean screen, boolean fresh, boolean mayStartServer) {
        HttpURLConnection c = null;
        try {
            JSONObject body = new JSONObject();
            body.put("text", text); body.put("screen", screen); body.put("newSession", fresh);
            c = (HttpURLConnection) new URL(BASE + "api/bubble").openConnection();
            c.setConnectTimeout(4000); c.setReadTimeout(0);
            c.setRequestMethod("POST"); c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/json");
            try (OutputStream o = c.getOutputStream()) { o.write(body.toString().getBytes("UTF-8")); }
            if (c.getResponseCode() != 200) {
                String err = "HTTP " + c.getResponseCode();
                try { err = new JSONObject(readAll(c)).optString("error", err); } catch (Exception e) {}
                final String m = err; ui.post(new Runnable() { public void run() { finish("Hiba: " + m, false); } });
                return;
            }
            BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), "UTF-8"));
            String line;
            while ((line = r.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                final JSONObject e;
                try { e = new JSONObject(line); } catch (Exception ex) { continue; }
                ui.post(new Runnable() { public void run() { onEvent(e); } });
            }
            ui.post(new Runnable() { public void run() { if (busy) finish(null, true); } });
        } catch (java.net.ConnectException e) {
            if (mayStartServer) {
                ui.post(new Runnable() { public void run() { setStatus("A motor nem fut – indítom…"); } });
                startServer();
                try { Thread.sleep(6000); } catch (InterruptedException ie) {}
                runRequest(text, screen, fresh, false);
            } else ui.post(new Runnable() { public void run() { finish("A motor nem fut. Nyisd meg a Claude Tablet appot.", false); } });
        } catch (Exception e) {
            final String m = e.getMessage();
            ui.post(new Runnable() { public void run() { finish("Hiba: " + m, false); } });
        } finally { if (c != null) c.disconnect(); }
    }

    static String readAll(HttpURLConnection c) throws Exception {
        StringBuilder sb = new StringBuilder();
        BufferedReader r = new BufferedReader(new InputStreamReader(c.getErrorStream() != null ? c.getErrorStream() : c.getInputStream(), "UTF-8"));
        String l; while ((l = r.readLine()) != null) sb.append(l);
        return sb.toString();
    }

    void startServer() {
        Intent i = new Intent("com.termux.RUN_COMMAND");
        i.setClassName("com.termux", "com.termux.app.RunCommandService");
        i.putExtra("com.termux.RUN_COMMAND_PATH", MainActivity.START_SCRIPT);
        i.putExtra("com.termux.RUN_COMMAND_BACKGROUND", true);
        try { if (Build.VERSION.SDK_INT >= 26) startForegroundService(i); else startService(i); } catch (Exception e) {}
    }

    void onEvent(JSONObject e) {
        String k = e.optString("kind");
        switch (k) {
            case "shot":
                setOverlayVisible(true);
                setStatus(e.optBoolean("ok") ? "Gondolkodom… (látom a képernyőt)" : "Gondolkodom… (képernyőkép nélkül)");
                break;
            case "run": runId = e.optString("runId"); break;
            case "overlay": setOverlayVisible(e.optBoolean("show")); break;
            case "launch": launch(e.optString("pkg")); break;
            case "thinking": setStatus("Gondolkodom…"); break;
            case "delta": block.append(e.optString("text")); render(); break;
            case "text":
                if (block.length() == 0) block.append(e.optString("text"));
                lastBlock = block.toString();
                all.append(block).append("\n\n"); block.setLength(0);
                render();
                break;
            case "tool": setStatus("🔧 " + toolLabel(e)); break;
            case "result": if (e.optBoolean("isError")) setStatus("⚠ eszközhiba, próbálkozom…"); break;
            case "error": all.append("⚠ ").append(e.optString("text")).append("\n"); render(); break;
            case "done": finish(e.optBoolean("isError") ? "Hiba történt" : "Kész", true); break;
        }
    }

    String toolLabel(JSONObject e) {
        String n = e.optString("name");
        JSONObject in = e.optJSONObject("input");
        if (in == null) return n;
        if (n.equals("Bash")) {
            String d = in.optString("description", "");
            return d.isEmpty() ? clip(in.optString("command"), 50) : d;
        }
        if (n.equals("Read")) return "Olvasom: " + new java.io.File(in.optString("file_path")).getName();
        if (n.startsWith("Web")) return "Keresés a weben…";
        return n;
    }
    static String clip(String s, int n) { return s.length() > n ? s.substring(0, n) + "…" : s; }

    void render() {
        String q = answer.getText().toString();
        int cut = q.indexOf("\n\n");
        String head = cut >= 0 ? q.substring(0, cut + 2) : "";
        answer.setText(head + plain(all.toString() + block));
        scroll.post(new Runnable() { public void run() { scroll.fullScroll(View.FOCUS_DOWN); } });
    }

    /** markdown-jelek eltávolítása a kis panelhez és a felolvasáshoz */
    static String plain(String s) {
        return s.replaceAll("(?m)^#{1,6}\\s*", "").replace("**", "").replace("`", "")
                .replaceAll("(?m)^\\s*[-*]\\s+", "• ").trim();
    }

    void finish(String msg, boolean ok) {
        busy = false;
        bSend.setText("➤"); bMic.setAlpha(1f);
        setOverlayVisible(true);
        setBubbleColor(C_ACCENT);
        if (msg != null) setStatus(msg);
        if (!ok && msg != null && all.length() == 0) { answer.append(msg); }
        if (ok) {
            if (block.length() > 0) { lastBlock = block.toString(); all.append(block); block.setLength(0); }
            if (!panelShown) showPanel();
            vibrate();
            if (prefs.getBoolean("tts", true) && !lastBlock.isEmpty()) speak(plain(lastBlock));
        }
    }

    void stopRun() {
        final String id = runId;
        if (id == null) return;
        setStatus("Leállítás…");
        new Thread(new Runnable() { public void run() {
            try {
                HttpURLConnection c = (HttpURLConnection) new URL(BASE + "api/stop").openConnection();
                c.setRequestMethod("POST"); c.setDoOutput(true); c.setRequestProperty("Content-Type", "application/json");
                try (OutputStream o = c.getOutputStream()) { o.write(new JSONObject().put("runId", id).toString().getBytes("UTF-8")); }
                c.getResponseCode(); c.disconnect();
            } catch (Exception e) {}
        } }).start();
    }

    void launch(String pkg) {
        Intent i = pkg == null ? null : getPackageManager().getLaunchIntentForPackage(pkg);
        if (i == null) { setStatus("Nincs ilyen app: " + pkg); return; }
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try { startActivity(i); } catch (Exception e) { setStatus("Nem indítható: " + pkg); }
    }

    // ------------------------------------------------------------------ felolvasás, rezgés

    void speak(String text) {
        if (tts == null) {
            pendingSpeak = text;
            tts = new TextToSpeech(this, new TextToSpeech.OnInitListener() { public void onInit(int st) {
                ttsReady = st == TextToSpeech.SUCCESS;
                if (ttsReady) { tts.setLanguage(new Locale("hu", "HU")); if (pendingSpeak != null) speak(pendingSpeak); pendingSpeak = null; }
            } });
            return;
        }
        if (!ttsReady) { pendingSpeak = text; return; }
        tts.stop();
        int max = Math.max(500, TextToSpeech.getMaxSpeechInputLength() - 10);
        for (int i = 0; i < text.length(); i += max)
            tts.speak(text.substring(i, Math.min(text.length(), i + max)), TextToSpeech.QUEUE_ADD, null, "b" + i);
    }

    void vibrate() {
        Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        if (v == null) return;
        if (Build.VERSION.SDK_INT >= 26) v.vibrate(VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE));
        else v.vibrate(30);
    }

    void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_LONG).show(); }

    // ------------------------------------------------------------------ indítás/leállítás máshonnan

    static void start(Context c) {
        Intent i = new Intent(c, BubbleService.class);
        if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i); else c.startService(i);
    }
    static void stop(Context c) {
        c.startService(new Intent(c, BubbleService.class).setAction(ACTION_STOP));
    }
}
