package hu.claudetablet;

import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Toast;

/** Szövegkijelölés menü („Claude”) és szövegmegosztás → a lebegő panelbe kerül a szöveg. Nem jelenít meg semmit. */
public class ProcessTextActivity extends Activity {
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Intent in = getIntent();
        CharSequence t = in.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT);
        if (t == null) t = in.getCharSequenceExtra(Intent.EXTRA_TEXT);
        if (t != null && t.length() > 0) {
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "Kapcsold be a lebegő Claude-gombot a Claude Tablet appban", Toast.LENGTH_LONG).show();
            } else {
                Intent i = new Intent(this, BubbleService.class).setAction(BubbleService.ACTION_TEXT).putExtra("text", t.toString());
                if (Build.VERSION.SDK_INT >= 26) startForegroundService(i); else startService(i);
            }
        }
        finish();
        overridePendingTransition(0, 0);
    }
}
