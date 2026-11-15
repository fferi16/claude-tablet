package hu.claudetablet;

import android.content.Intent;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

/** Gyorsbeállítás-csempe: egy koppintás = lebegő panel + azonnali diktálás (bármelyik appból). */
public class ClaudeTileService extends TileService {
    @Override
    public void onStartListening() {
        Tile t = getQsTile();
        if (t == null) return;
        t.setState(BubbleService.running ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        t.updateTile();
    }

    @Override
    public void onClick() {
        Intent i = new Intent(this, ProcessTextActivity.class).setAction(BubbleService.ACTION_LISTEN);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        // a lehúzható menü bezárása + indítás
        startActivityAndCollapse(i);
    }
}
