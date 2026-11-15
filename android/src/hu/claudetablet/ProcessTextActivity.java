package hu.claudetablet;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.database.Cursor;
import android.provider.Settings;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import org.json.JSONObject;

/** Láthatatlan „Claude” belépési pont a lebegő panelhez:
 *  szövegkijelölés menü (PROCESS_TEXT), kép megosztása (SEND image/*), gyorsbeállítás-csempe (LISTEN). */
public class ProcessTextActivity extends Activity {
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        L.init(this);
        Intent in = getIntent();
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, L.t("Kapcsold be a lebegő Claude-gombot a Claude Tablet appban"), Toast.LENGTH_LONG).show();
            done(); return;
        }
        if (BubbleService.ACTION_LISTEN.equals(in.getAction())) {
            toBubble(new Intent(this, BubbleService.class).setAction(BubbleService.ACTION_LISTEN));
            done(); return;
        }
        if (ACTION_PICK.equals(in.getAction())) {
            // a lebegő panel 📎 gombja: képválasztó, az eredmény az onActivityResult-ba jön
            Intent p = new Intent(Intent.ACTION_GET_CONTENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE);
            try { startActivityForResult(Intent.createChooser(p, L.t("Kép választása")), 1); } catch (Exception e) { done(); }
            return;
        }
        String type = in.getType();
        if (Intent.ACTION_SEND.equals(in.getAction()) && type != null && type.startsWith("image/")) {
            final Uri u = in.getParcelableExtra(Intent.EXTRA_STREAM);
            if (u == null) { done(); return; }
            sendImage(u);
            return;
        }
        CharSequence t = in.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT);
        if (t == null) t = in.getCharSequenceExtra(Intent.EXTRA_TEXT);
        if (t != null && t.length() > 0)
            toBubble(new Intent(this, BubbleService.class).setAction(BubbleService.ACTION_TEXT).putExtra("text", t.toString()));
        done();
    }

    static final String ACTION_PICK = "hu.claudetablet.PICK";

    @Override
    protected void onActivityResult(int req, int code, Intent data) {
        final Uri u = code == RESULT_OK && data != null ? data.getData() : null;
        if (u == null) { done(); return; }
        sendImage(u);
    }

    void sendImage(final Uri u) {
        Toast.makeText(this, L.t("Kép küldése Claude-nak…"), Toast.LENGTH_SHORT).show();
        final String name = nameOf(u);
        new Thread(new Runnable() { public void run() {
            final String path = upload(u, name);
            runOnUiThread(new Runnable() { public void run() {
                if (path == null) Toast.makeText(ProcessTextActivity.this, L.t("A kép nem küldhető – fut a motor?"), Toast.LENGTH_LONG).show();
                else toBubble(new Intent(ProcessTextActivity.this, BubbleService.class).setAction(BubbleService.ACTION_IMAGE).putExtra("path", path));
                done();
            } });
        } }).start();
    }

    void toBubble(Intent i) {
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(i); else startService(i);
    }

    void done() { finish(); overridePendingTransition(0, 0); }

    String nameOf(Uri u) {
        try (Cursor c = getContentResolver().query(u, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst()) { String n = c.getString(0); if (n != null) return n; }
        } catch (Exception e) {}
        return "kep.jpg";
    }

    /** feltöltés a motorra (a felhasználó home mappájába) – visszaadja a fájl útvonalát */
    String upload(Uri u, String name) {
        try {
            HttpURLConnection c = (HttpURLConnection) new URL(MainActivity.BASE + "api/projects").openConnection();
            c.setConnectTimeout(3000);
            String home = new JSONObject(read(c.getInputStream())).getString("home");
            c.disconnect();
            c = (HttpURLConnection) new URL(MainActivity.BASE + "api/upload?cwd=" + URLEncoder.encode(home, "UTF-8")
                    + "&name=" + URLEncoder.encode(name, "UTF-8")).openConnection();
            c.setRequestMethod("POST"); c.setDoOutput(true); c.setChunkedStreamingMode(64 * 1024);
            c.setConnectTimeout(3000);
            try (InputStream is = getContentResolver().openInputStream(u); OutputStream os = c.getOutputStream()) {
                byte[] buf = new byte[64 * 1024]; int n;
                while ((n = is.read(buf)) > 0) os.write(buf, 0, n);
            }
            if (c.getResponseCode() != 200) { android.util.Log.w("ClaudeTablet", "upload HTTP " + c.getResponseCode()); return null; }
            return new JSONObject(read(c.getInputStream())).optString("path", null);
        } catch (Exception e) { android.util.Log.w("ClaudeTablet", "upload hiba", e); return null; }
    }

    static String read(InputStream is) throws Exception {
        StringBuilder sb = new StringBuilder();
        BufferedReader r = new BufferedReader(new InputStreamReader(is, "UTF-8"));
        String l; while ((l = r.readLine()) != null) sb.append(l);
        return sb.toString();
    }
}
