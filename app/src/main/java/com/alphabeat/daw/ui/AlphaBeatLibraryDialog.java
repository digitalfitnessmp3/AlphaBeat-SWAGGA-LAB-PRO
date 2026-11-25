package com.alphabeat.daw.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.widget.Toast;
import com.digitalfitnessmp3.app.network.AlphaBeatLibraryClient;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.util.ArrayList;
import java.util.UUID;
import java.util.function.BooleanSupplier;

/** QA dialog. The login layer must supply a current verified session and
 * invalidate it on logout/account switch. Never persist credentials here. */
public final class AlphaBeatLibraryDialog {
    public interface ImportTarget {
        boolean channelAvailable(int channel);
        // Return true only after taking responsibility for the private file.
        boolean importAudio(int channel, File audio, String title, int bpm);
    }

    private final Activity activity;
    private final AlphaBeatLibraryClient client;
    private final UUID owner;
    private final String token, publicKey;
    private final BooleanSupplier current;
    private final ImportTarget target;
    private volatile boolean closed;
    private boolean busy;
    private AlertDialog visible;

    public AlphaBeatLibraryDialog(Activity activity, AlphaBeatLibraryClient client,
            UUID owner, String token, String publicKey, BooleanSupplier current,
            ImportTarget target) {
        this.activity = activity;
        this.client = client;
        this.owner = owner;
        this.token = token;
        this.publicKey = publicKey;
        this.current = current;
        this.target = target;
    }

    private boolean valid() { return !closed && current.getAsBoolean(); }
    private boolean usable() { return valid() && !activity.isFinishing() && !activity.isDestroyed(); }

    public void close() {
        closed = true;
        if (visible != null) visible.dismiss();
    }

    private void message(String text) {
        if (usable()) Toast.makeText(activity, text, Toast.LENGTH_LONG).show();
    }

    public void open() {
        if (busy || !usable()) return;
        busy = true;
        message("Caricamento libreria personale…");
        new Thread(() -> {
            try {
                JSONArray items = client.list(token);
                ArrayList<JSONObject> rows = new ArrayList<>();
                ArrayList<String> labels = new ArrayList<>();
                for (int i = 0; i < items.length(); i++) {
                    JSONObject row = items.optJSONObject(i);
                    if (row == null) continue;
                    try { UUID.fromString(row.getString("id")); }
                    catch (Exception invalid) { continue; }
                    rows.add(row);
                    int bpm = row.optInt("target_bpm", 0);
                    labels.add(row.optString("title", "Remix") + (bpm > 0 ? " · " + bpm + " BPM" : ""));
                }
                activity.runOnUiThread(() -> {
                    busy = false;
                    if (!usable()) return;
                    if (rows.isEmpty()) { message("Nessun remix nella tua libreria."); return; }
                    visible = new AlertDialog.Builder(activity)
                        .setTitle("ALPHABEAT · LA MIA LIBRERIA")
                        .setItems(labels.toArray(new String[0]), (d, i) -> chooseChannel(rows.get(i)))
                        .setNegativeButton("Chiudi", null).show();
                });
            } catch (Exception e) {
                activity.runOnUiThread(() -> { busy = false; message("Libreria non disponibile. Riprova."); });
            }
        }, "alphabeat-library-list").start();
    }

    private void chooseChannel(JSONObject row) {
        if (!usable() || busy) return;
        ArrayList<Integer> channels = new ArrayList<>();
        ArrayList<String> labels = new ArrayList<>();
        for (int c = 3; c <= 4; c++) {
            if (target.channelAvailable(c)) { channels.add(c); labels.add("CH" + c); }
        }
        if (channels.isEmpty()) { message("CH3 e CH4 occupati. Libera un canale prima di importare."); return; }
        visible = new AlertDialog.Builder(activity)
            .setTitle("Importa in un canale libero")
            .setItems(labels.toArray(new String[0]), (d, i) -> download(row, channels.get(i)))
            .setNegativeButton("Annulla", null).show();
    }

    private void download(JSONObject row, int channel) {
        if (busy || !usable() || !target.channelAvailable(channel)) return;
        busy = true;
        message("Recupero audio privato in corso…");
        new Thread(() -> {
            try {
                File file = client.downloadAudio(activity.getApplicationContext(),
                    UUID.fromString(row.getString("id")), owner, token, publicKey, this::valid);
                activity.runOnUiThread(() -> {
                    busy = false;
                    boolean accepted = false;
                    try {
                        if (usable() && target.channelAvailable(channel)) {
                            accepted = target.importAudio(channel, file,
                                row.optString("title", "Remix"), row.optInt("target_bpm", 0));
                            if (accepted) message("Remix importato in CH" + channel);
                        }
                    } finally {
                        if (!accepted) file.delete();
                    }
                });
            } catch (Exception e) {
                activity.runOnUiThread(() -> { busy = false; message("Download non completato. Riprova."); });
            }
        }, "alphabeat-library-download").start();
    }
}
