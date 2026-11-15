package hu.claudetablet;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Path;
import android.graphics.Rect;
import android.hardware.HardwareBuffer;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.view.Display;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import org.json.JSONObject;

/** Akadálymentességi szolgáltatás: a szerver parancsára képernyőképet készít, koppint, görget, gépel és kiolvassa
 *  a képernyő szövegét – ADB nélkül. Kapcsolat: GET /api/agent/listen (parancsok), POST /api/agent/result (válasz). */
public class AgentService extends AccessibilityService {

    static volatile boolean connected = false;
    static volatile AgentService inst;
    final Handler ui = new Handler(Looper.getMainLooper());
    volatile boolean alive = false;
    Thread loop;

    @Override
    protected void onServiceConnected() {
        inst = this;
        alive = true;
        loop = new Thread(new Runnable() { public void run() { listenLoop(); } });
        loop.start();
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent e) {}
    @Override public void onInterrupt() {}

    @Override
    public void onDestroy() {
        alive = false; connected = false; inst = null;
        if (loop != null) loop.interrupt();
        super.onDestroy();
    }

    static boolean enabled() { return inst != null; }

    // ------------------------------------------------------------------ kapcsolat

    void listenLoop() {
        while (alive) {
            HttpURLConnection c = null;
            try {
                c = (HttpURLConnection) new URL(MainActivity.BASE + "api/agent/listen").openConnection();
                c.setConnectTimeout(3000);
                c.setReadTimeout(60000); // a szerver 20 mp-enként pingel
                BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), "UTF-8"));
                connected = true;
                String line;
                while (alive && (line = r.readLine()) != null) {
                    if (line.trim().isEmpty()) continue;
                    try { handle(new JSONObject(line)); } catch (Exception e) {}
                }
            } catch (Exception e) {
            } finally {
                connected = false;
                if (c != null) c.disconnect();
            }
            try { Thread.sleep(3000); } catch (InterruptedException e) { return; }
        }
    }

    void reply(final JSONObject o) {
        new Thread(new Runnable() { public void run() {
            try {
                HttpURLConnection c = (HttpURLConnection) new URL(MainActivity.BASE + "api/agent/result").openConnection();
                c.setRequestMethod("POST"); c.setDoOutput(true);
                c.setConnectTimeout(3000); c.setReadTimeout(30000);
                c.setRequestProperty("Content-Type", "application/json");
                try (OutputStream out = c.getOutputStream()) { out.write(o.toString().getBytes("UTF-8")); }
                c.getResponseCode(); c.disconnect();
            } catch (Exception e) {}
        } }).start();
    }

    JSONObject res(String id, boolean ok, String key, Object val) {
        JSONObject o = new JSONObject();
        try { o.put("id", id); o.put("ok", ok); if (key != null) o.put(key, val); } catch (Exception e) {}
        return o;
    }
    void ok(String id, String text) { reply(res(id, true, "text", text)); }
    void err(String id, String msg) { reply(res(id, false, "error", msg)); }
    /** ezt az ügynök nem tudja – a `tablet` parancs ADB-re vált */
    void unsupported(String id, String msg) {
        JSONObject o = res(id, false, "error", msg);
        try { o.put("unsupported", true); } catch (Exception e) {}
        reply(o);
    }

    // ------------------------------------------------------------------ parancsok

    void handle(final JSONObject m) {
        final String id = m.optString("id"), cmd = m.optString("cmd");
        if (id.isEmpty()) return; // ping / hello
        ui.post(new Runnable() { public void run() {
            try {
                switch (cmd) {
                    case "shot": shot(id); break;
                    case "tap": gesture(id, m.optInt("x"), m.optInt("y"), m.optInt("x"), m.optInt("y"), m.optInt("ms", 60)); break;
                    case "longpress": gesture(id, m.optInt("x"), m.optInt("y"), m.optInt("x"), m.optInt("y"), 800); break;
                    case "swipe": gesture(id, m.optInt("x1"), m.optInt("y1"), m.optInt("x2"), m.optInt("y2"), m.optInt("ms", 300)); break;
                    case "key": key(id, m.optString("key")); break;
                    case "type": type(id, m.optString("text")); break;
                    case "text": ok(id, dumpText()); break;
                    case "clicktext": clickText(id, m.optString("text")); break;
                    case "open": open(id, m.optString("pkg")); break;
                    default: unsupported(id, "Ismeretlen parancs: " + cmd);
                }
            } catch (Exception e) { err(id, "Hiba: " + e); }
        } });
    }

    void shot(final String id) {
        if (Build.VERSION.SDK_INT < 30) { unsupported(id, "Képernyőkép az appal csak Android 11+-on"); return; }
        final BubbleService b = BubbleService.inst;
        if (b != null) b.setOverlayVisible(false);
        ui.postDelayed(new Runnable() { public void run() {
            takeScreenshot(Display.DEFAULT_DISPLAY, getMainExecutor(), new TakeScreenshotCallback() {
                public void onSuccess(final ScreenshotResult r) {
                    if (b != null) b.setOverlayVisible(true);
                    new Thread(new Runnable() { public void run() {
                        try {
                            HardwareBuffer hb = r.getHardwareBuffer();
                            Bitmap hw = Bitmap.wrapHardwareBuffer(hb, r.getColorSpace());
                            Bitmap bm = hw.copy(Bitmap.Config.ARGB_8888, false);
                            hb.close(); hw.recycle();
                            ByteArrayOutputStream bo = new ByteArrayOutputStream();
                            bm.compress(Bitmap.CompressFormat.PNG, 100, bo);
                            bm.recycle();
                            reply(res(id, true, "png", Base64.encodeToString(bo.toByteArray(), Base64.NO_WRAP)));
                        } catch (Exception e) { err(id, "Képernyőkép hiba: " + e); }
                    } }).start();
                }
                public void onFailure(int code) {
                    if (b != null) b.setOverlayVisible(true);
                    err(id, "Képernyőkép sikertelen (kód " + code + ")");
                }
            });
        } }, b != null ? 250 : 0);
    }

    void gesture(final String id, int x1, int y1, int x2, int y2, int ms) {
        Path p = new Path();
        p.moveTo(x1, y1);
        if (x1 != x2 || y1 != y2) p.lineTo(x2, y2);
        GestureDescription g = new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(p, 0, Math.max(1, ms))).build();
        boolean sent = dispatchGesture(g, new GestureResultCallback() {
            public void onCompleted(GestureDescription d) { ok(id, "ok"); }
            public void onCancelled(GestureDescription d) { err(id, "A mozdulat megszakadt"); }
        }, null);
        if (!sent) err(id, "A mozdulat nem küldhető");
    }

    void key(String id, String k) {
        k = k.toUpperCase();
        int a;
        switch (k) {
            case "BACK": a = GLOBAL_ACTION_BACK; break;
            case "HOME": a = GLOBAL_ACTION_HOME; break;
            case "APP_SWITCH": case "RECENTS": a = GLOBAL_ACTION_RECENTS; break;
            case "NOTIFICATIONS": a = GLOBAL_ACTION_NOTIFICATIONS; break;
            case "ENTER":
                AccessibilityNodeInfo f = findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
                if (f != null && Build.VERSION.SDK_INT >= 30
                        && f.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.getId())) { ok(id, "ok"); return; }
                unsupported(id, "ENTER nem küldhető"); return;
            default: unsupported(id, "Ez a gomb csak ADB-vel: " + k); return;
        }
        if (performGlobalAction(a)) ok(id, "ok"); else err(id, "Nem sikerült: " + k);
    }

    void type(String id, String text) {
        AccessibilityNodeInfo f = findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
        if (f == null || !f.isEditable()) { err(id, "Nincs kijelölt szövegmező – előbb koppints bele"); return; }
        CharSequence cur = f.getText();
        if (cur == null || (Build.VERSION.SDK_INT >= 26 && f.isShowingHintText())) cur = "";
        Bundle b = new Bundle();
        b.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, cur.toString() + text);
        if (f.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, b)) ok(id, "ok"); else err(id, "A gépelés nem sikerült");
    }

    void open(String id, String pkg) {
        Intent i = getPackageManager().getLaunchIntentForPackage(pkg);
        if (i == null) { err(id, "Nincs ilyen app: " + pkg); return; }
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(i);
        ok(id, "ok");
    }

    // ------------------------------------------------------------------ képernyő szövege

    String dumpText() {
        StringBuilder sb = new StringBuilder();
        List<AccessibilityWindowInfo> ws = getWindows();
        for (AccessibilityWindowInfo w : ws) {
            AccessibilityNodeInfo root = w.getRoot();
            if (root == null) continue;
            if (root.getPackageName() != null && root.getPackageName().toString().equals(getPackageName())) continue;
            String kind = w.getType() == AccessibilityWindowInfo.TYPE_INPUT_METHOD ? "billentyűzet"
                    : w.getType() == AccessibilityWindowInfo.TYPE_SYSTEM ? "rendszer" : "app";
            sb.append("## ").append(kind).append(": ").append(root.getPackageName()).append('\n');
            walk(root, sb, 0);
            if (sb.length() > 30000) { sb.append("…(levágva)\n"); break; }
        }
        return sb.length() == 0 ? "(nem olvasható a képernyő)" : sb.toString();
    }

    void walk(AccessibilityNodeInfo n, StringBuilder sb, int depth) {
        if (n == null || depth > 60 || sb.length() > 30000) return;
        if (!n.isVisibleToUser()) return;
        CharSequence t = n.getText(), d = n.getContentDescription();
        String label = t != null && t.length() > 0 ? t.toString() : d != null && d.length() > 0 ? d.toString() : null;
        boolean click = n.isClickable() || n.isLongClickable();
        if (label != null || n.isEditable()) {
            Rect r = new Rect(); n.getBoundsInScreen(r);
            sb.append('[').append(r.centerX()).append(',').append(r.centerY()).append("] ");
            if (n.isEditable()) sb.append("✎ ");
            sb.append(label == null ? "(üres mező)" : label.replace('\n', ' '));
            if (click) sb.append("  ‹koppintható›");
            if (n.isChecked()) sb.append("  ‹bejelölve›");
            sb.append('\n');
        }
        for (int i = 0; i < n.getChildCount(); i++) walk(n.getChild(i), sb, depth + 1);
    }

    void clickText(String id, String text) {
        for (AccessibilityWindowInfo w : getWindows()) {
            AccessibilityNodeInfo root = w.getRoot();
            if (root == null) continue;
            if (root.getPackageName() != null && root.getPackageName().toString().equals(getPackageName())) continue;
            List<AccessibilityNodeInfo> hits = root.findAccessibilityNodeInfosByText(text);
            for (AccessibilityNodeInfo h : hits) {
                if (!h.isVisibleToUser()) continue;
                AccessibilityNodeInfo c = h;
                while (c != null && !c.isClickable()) c = c.getParent();
                if (c != null && c.performAction(AccessibilityNodeInfo.ACTION_CLICK)) { ok(id, "rákoppintva: " + text); return; }
                Rect r = new Rect(); h.getBoundsInScreen(r);
                gesture(id, r.centerX(), r.centerY(), r.centerX(), r.centerY(), 60);
                return;
            }
        }
        err(id, "Nem találom a képernyőn: " + text);
    }
}
