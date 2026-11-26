package com.alphabeat.daw;

import android.app.Activity;
import android.view.Window;
import android.view.WindowManager;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.View;
import android.content.Intent;
import android.net.Uri;
import android.view.MotionEvent;
import android.widget.Toast;
import android.os.Bundle;
import android.graphics.Color;
import android.widget.ImageView;
import android.widget.FrameLayout;

public final class MainActivity extends Activity {

    private static final int PICK_AUDIO = 1001;
    private static final int PICK_AUDIO_CH3 = 1003;
    private static final int PICK_AUDIO_CH4 = 1004;

    private final Uri[] channelAudio = new Uri[4];
    // QA library imports are temporary, private and restricted to CH3/CH4.
    private final java.io.File[] libraryFiles = new java.io.File[4];
    private final String[] libraryTitles = new String[4];
    private final int[] libraryBpm = new int[4];
    private com.alphabeat.daw.ui.AlphaBeatLibraryDialog libraryDialog;

    // The future verified login layer must call this on the UI thread with
    // trusted origins and a session guard invalidated on logout/account switch.
    private void openPersonalLibrary(
            com.digitalfitnessmp3.app.network.AlphaBeatLibraryClient client,
            java.util.UUID owner, String accessToken, String publicKey,
            java.util.function.BooleanSupplier sessionCurrent) {
        if (sessionCurrent == null || !sessionCurrent.getAsBoolean()) {
            Toast.makeText(this, "Accedi al tuo account AlphaBeat", Toast.LENGTH_SHORT).show();
            return;
        }
        if (libraryDialog != null) libraryDialog.close();
        libraryDialog = new com.alphabeat.daw.ui.AlphaBeatLibraryDialog(
                this, client, owner, accessToken, publicKey, sessionCurrent,
                new com.alphabeat.daw.ui.AlphaBeatLibraryDialog.ImportTarget() {
                    public boolean channelAvailable(int channel) {
                        return (channel == 3 || channel == 4)
                                && channelAudio[channel - 1] == null
                                && !remixInProgress && sessionCurrent.getAsBoolean();
                    }

                    public boolean importAudio(int channel, java.io.File file, String title, int bpm) {
                        if (!channelAvailable(channel) || file == null || !file.isFile()
                                || file.length() <= 0) return false;
                        int index = channel - 1;
                        stopEditPreview();
                        stopAudio();
                        libraryFiles[index] = file;
                        libraryTitles[index] = title;
                        libraryBpm[index] = bpm >= 40 && bpm <= 220 ? bpm : 0;
                        channelAudio[index] = Uri.fromFile(file);
                        activeChannel = index;
                        selectedAudio = channelAudio[index];
                        swaggaPlaybackBpm = 128; // 1x: do not stretch an already-rendered remix.
                        updateTrackInfo(selectedAudio, title);
                        if (swaggaView != null) swaggaView.setDetectedBpm(libraryBpm[index]);
                        return true;
                    }
                });
        libraryDialog.open();
    }

    // Must also be called by the future authentication layer on logout/switch.
    private void clearPersonalLibrary() {
        if (libraryDialog != null) {
            libraryDialog.close();
            libraryDialog = null;
        }
        boolean selectedPrivate = false;
        for (int i = 2; i <= 3; i++) {
            if (libraryFiles[i] != null) {
                if (channelAudio[i] != null && channelAudio[i].equals(selectedAudio)) {
                    selectedPrivate = true;
                }
            }
        }
        stopEditPreview();
        if (selectedPrivate) {
            stopAudio();
            selectedAudio = originalAudio;
            activeChannel = 0;
            swaggaPlaybackBpm = 128;
            updateTrackInfo(selectedAudio, selectedAudio == null ? "Nessun brano" : originalTrackTitle);
        }
        for (int i = 2; i <= 3; i++) {
            if (libraryFiles[i] != null) {
                libraryFiles[i].delete();
                libraryFiles[i] = null;
                channelAudio[i] = null;
                libraryTitles[i] = null;
                libraryBpm[i] = 0;
            }
        }
    }
    private final float[] channelVolume = {
            0.75f, 0.75f, 0.75f, 0.75f
    };
    private final boolean[] channelMuted = new boolean[4];
    private int activeChannel = 0;
    private int consoleTransport = 0;
    private void refreshConsoleState() {
        if (swaggaView != null) swaggaView.setConsoleState(
                selectedAudio == null ? 0 : activeChannel + 1, channelMuted, consoleTransport);
    }

    private Uri selectedAudio;
    private android.media.MediaPlayer player;
    private float masterVolume = 1.00f; // Matches the master fader at launch.

    private void applyEffectiveVolume() {
        if (player == null) return;
        float volume = channelMuted[activeChannel]
                ? 0f
                : masterVolume * channelVolume[activeChannel];
        player.setVolume(volume, volume);
    }

    private void setChannelVolume(int channel, float value) {
        if (channel < 1 || channel > 4) return;
        channelVolume[channel - 1] =
                Math.max(0f, Math.min(1f, value));
        if (activeChannel == channel - 1) {
            applyEffectiveVolume();
        }
    }

    private void muteChannel(int channel) {
        if (channel < 1 || channel > 4) return;
        int index = channel - 1;
        channelMuted[index] = !channelMuted[index];
        refreshConsoleState();
        applyEffectiveVolume();
        Toast.makeText(this,
                "CH" + channel + (channelMuted[index]
                        ? " - MUTE ON" : " - MUTE OFF"),
                Toast.LENGTH_SHORT).show();
    }

    private void soloChannel(int channel) {
        selectAudioChannel(channel);
    }

    private void setMasterVolume(float value) {
        masterVolume = Math.max(0f, Math.min(1f, value));
        if (swaggaView != null) swaggaView.setMasterFaderVolume(masterVolume);
        if (player != null) {
            applyEffectiveVolume();
        }
    }
    private com.alphabeat.daw.ui.SwaggaConsoleView swaggaView;
    private int waveformGeneration = 0;
    private int trackInfoGeneration = 0;
    private String originalTrackTitle = "Brano originale";

    // Le etichette presenti sullo sfondo sono statiche: metadati aggiornati in QA.
    private void updateTrackInfo(Uri uri, String displayOverride) {
        final int generation = ++trackInfoGeneration;
        if (swaggaView == null) return;
        int gridBpm = 0;
        if (uri != null && uri.equals(readyRemix)) {
            gridBpm = readyRemixLibraryBpm;
        } else if (uri != null) {
            for (int i = 2; i < 4; i++) {
                if (libraryFiles[i] != null && uri.equals(channelAudio[i])) {
                    gridBpm = libraryBpm[i];
                    break;
                }
            }
        }
        swaggaView.setTimelineBpm(gridBpm);
        swaggaView.setTrackPosition(0, 0);
        swaggaView.setTrackInfo(
                displayOverride == null ? "Caricamento brano…" : displayOverride, 0);
        final int waveGeneration = ++waveformGeneration;
        swaggaView.setWaveformPeaks(null);
        swaggaView.setWaveformStatus(uri == null ? null : "ANALISI WAVEFORM…");
        swaggaView.setWaveformProgress(0f);
        refreshConsoleState();
        if (uri == null) return;
        new Thread(() -> {
            try {
                float[] peaks = com.alphabeat.daw.audio.WaveformAnalyzer.analyze(getApplicationContext(), uri, 8192);
                runOnUiThread(() -> {
                    if (waveGeneration == waveformGeneration && uri.equals(selectedAudio) && swaggaView != null) {
                        swaggaView.setWaveformPeaks(peaks);
                        swaggaView.setWaveformStatus(null);
                    }
                });
            } catch (Exception e) {
                android.util.Log.w("SWAGGA_AUDIO", "Waveform non disponibile", e);
                runOnUiThread(() -> {
                    if (waveGeneration == waveformGeneration && uri.equals(selectedAudio) && swaggaView != null)
                        swaggaView.setWaveformStatus("WAVEFORM NON DISPONIBILE");
                });
            }
        }, "SWAGGA-Waveform").start();
        new Thread(() -> {
            String title = displayOverride;
            int durationMs = 0;
            try {
                if (title == null) {
                    try (android.database.Cursor cursor =
                                 getContentResolver().query(uri,
                                         new String[]{android.provider.OpenableColumns.DISPLAY_NAME},
                                         null, null, null)) {
                        if (cursor != null && cursor.moveToFirst() && !cursor.isNull(0)) {
                            title = cursor.getString(0);
                        }
                    }
                }
                if (title == null || title.trim().isEmpty()) {
                    title = uri.getLastPathSegment();
                }
                if (title == null || title.trim().isEmpty()) {
                    title = "Brano audio";
                }
                android.media.MediaMetadataRetriever retriever =
                        new android.media.MediaMetadataRetriever();
                try {
                    retriever.setDataSource(getApplicationContext(), uri);
                    String duration = retriever.extractMetadata(
                            android.media.MediaMetadataRetriever.METADATA_KEY_DURATION);
                    if (duration != null) durationMs = Integer.parseInt(duration);
                } finally {
                    retriever.release();
                }
            } catch (Exception error) {
                android.util.Log.w("SWAGGA_TRACK_INFO",
                        "Metadati del brano non disponibili", error);
            }
            final String visibleTitle = title == null || title.trim().isEmpty()
                    ? "Brano audio" : title.trim();
            final int visibleDuration = Math.max(0, durationMs);
            runOnUiThread(() -> {
                if (generation != trackInfoGeneration || swaggaView == null) return;
                if (uri.equals(originalAudio) && displayOverride == null) {
                    originalTrackTitle = visibleTitle;
                }
                swaggaView.setTrackInfo(visibleTitle, visibleDuration);
            });
        }, "SWAGGA-TrackInfo").start();
    }

    private final android.os.Handler swaggaHandler =
            new android.os.Handler(android.os.Looper.getMainLooper());

    private final Runnable swaggaProgressUpdater = new Runnable() {
        @Override
        public void run() {
            if (player == null || swaggaView == null) return;

            try {
                int duration = player.getDuration();
                if (duration > 0) {
                    int position = player.getCurrentPosition();
                    swaggaView.setWaveformProgress((float) position / duration);
                    swaggaView.setTrackPosition(position, duration);
                }

                if (player.isPlaying()) {
                    swaggaHandler.postDelayed(this, 40);
                }
            } catch (IllegalStateException ignored) {
            }
        }
    };

    private void updateSwaggaProgress() {
        swaggaHandler.removeCallbacks(swaggaProgressUpdater);
        swaggaHandler.post(swaggaProgressUpdater);
    }


    private void showSwaggaSetup() {
        final int[] bpmValues = {128, 140, 150, 160, 180, 200, 220, 240, 260};
        final int[] pendingBpm = {selectedTargetBpm};

        android.widget.LinearLayout layout =
                new android.widget.LinearLayout(this);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        int pad = (int) (24 * getResources().getDisplayMetrics().density);
        layout.setPadding(pad, pad, pad, pad);

        android.widget.TextView volumeLabel =
                new android.widget.TextView(this);
        volumeLabel.setText("MASTER: " + Math.round(masterVolume * 100) + "%");
        layout.addView(volumeLabel);

        android.widget.SeekBar volume =
                new android.widget.SeekBar(this);
        volume.setMax(100);
        volume.setProgress(Math.round(masterVolume * 100));
        layout.addView(volume);

        volume.setOnSeekBarChangeListener(
                new android.widget.SeekBar.OnSeekBarChangeListener() {
                    public void onProgressChanged(
                            android.widget.SeekBar bar, int progress,
                            boolean fromUser) {
                        volumeLabel.setText("MASTER: " + progress + "%");
                    }
                    public void onStartTrackingTouch(android.widget.SeekBar bar) {}
                    public void onStopTrackingTouch(android.widget.SeekBar bar) {}
                });

        android.widget.TextView bpmLabel =
                new android.widget.TextView(this);
        bpmLabel.setText("\\nBPM PREDEFINITI");
        layout.addView(bpmLabel);

        android.widget.Spinner bpmSpinner =
                new android.widget.Spinner(this);

        String[] options = {"128 BPM", "140 BPM", "150 BPM",
                            "160 BPM", "180 BPM", "200 BPM",
                            "220 BPM", "240 BPM", "260 BPM"};

        android.widget.ArrayAdapter<String> adapter =
                new android.widget.ArrayAdapter<>(
                        this,
                        android.R.layout.simple_spinner_dropdown_item,
                        options);
        bpmSpinner.setAdapter(adapter);

        for (int i = 0; i < bpmValues.length; i++) {
            if (bpmValues[i] == selectedTargetBpm) {
                bpmSpinner.setSelection(i);
                break;
            }
        }

        layout.addView(bpmSpinner);

        new android.app.AlertDialog.Builder(this)
                .setTitle("SWAGGA LAB PRO - SETUP")
                .setView(layout)
                .setNegativeButton("ANNULLA", null)
                .setPositiveButton("SALVA", (dialog, which) -> {
                    setMasterVolume(volume.getProgress() / 100f);
                    pendingBpm[0] =
                            bpmValues[bpmSpinner.getSelectedItemPosition()];
                    selectedTargetBpm = pendingBpm[0];

                    Toast.makeText(this,
                            "MASTER " + volume.getProgress()
                            + "% - BPM " + selectedTargetBpm,
                            Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    private android.media.MediaPlayer editPreviewPlayer;
    private final android.os.Handler editPreviewHandler =
            new android.os.Handler(android.os.Looper.getMainLooper());
    private Runnable editPreviewStopper;

    private void stopEditPreview() {
        if (editPreviewStopper != null) {
            editPreviewHandler.removeCallbacks(editPreviewStopper);
            editPreviewStopper = null;
        }
        if (editPreviewPlayer != null) {
            editPreviewPlayer.release();
            editPreviewPlayer = null;
        }
    }

    private void showSwaggaEdit() {
        if (selectedAudio == null) {
            Toast.makeText(this, "Carica prima un brano",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        stopEditPreview();

        final Uri editSource = selectedAudio;
        final int duration;

        try {
            android.media.MediaMetadataRetriever retriever =
                    new android.media.MediaMetadataRetriever();
            try {
                retriever.setDataSource(this, editSource);
                String value = retriever.extractMetadata(
                        android.media.MediaMetadataRetriever.METADATA_KEY_DURATION);
                duration = Integer.parseInt(value);
            } finally {
                retriever.release();
            }
        } catch (Exception e) {
            Toast.makeText(this, "Durata audio non disponibile",
                    Toast.LENGTH_LONG).show();
            return;
        }

        if (duration <= 0) {
            Toast.makeText(this, "Durata audio non valida",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        android.widget.LinearLayout layout =
                new android.widget.LinearLayout(this);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);

        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        layout.setPadding(pad, pad, pad, pad);

        android.widget.TextView info = new android.widget.TextView(this);
        layout.addView(info);

        android.widget.SeekBar inBar = new android.widget.SeekBar(this);
        android.widget.SeekBar outBar = new android.widget.SeekBar(this);

        inBar.setMax(1000);
        outBar.setMax(1000);
        outBar.setProgress(1000);

        layout.addView(inBar);
        layout.addView(outBar);

        Runnable updateLabels = () -> {
            int inMs = (int) ((long) duration * inBar.getProgress() / 1000);
            int outMs = (int) ((long) duration * outBar.getProgress() / 1000);

            info.setText(
                    "IN: " + inMs / 1000 + " s"
                    + "\nOUT: " + outMs / 1000 + " s"
                    + "\nDurata brano: " + duration / 1000 + " s");
        };

        android.widget.SeekBar.OnSeekBarChangeListener listener =
                new android.widget.SeekBar.OnSeekBarChangeListener() {
                    public void onProgressChanged(
                            android.widget.SeekBar bar, int progress,
                            boolean fromUser) {
                        updateLabels.run();
                    }
                    public void onStartTrackingTouch(android.widget.SeekBar bar) {}
                    public void onStopTrackingTouch(android.widget.SeekBar bar) {}
                };

        inBar.setOnSeekBarChangeListener(listener);
        outBar.setOnSeekBarChangeListener(listener);
        updateLabels.run();

        android.widget.Button preview =
                new android.widget.Button(this);
        preview.setText("ASCOLTA SELEZIONE");
        layout.addView(preview);

        preview.setOnClickListener(v -> {
            int inMs = (int) ((long) duration * inBar.getProgress() / 1000);
            int outMs = (int) ((long) duration * outBar.getProgress() / 1000);

            if (outMs <= inMs) {
                Toast.makeText(this, "OUT deve essere maggiore di IN",
                        Toast.LENGTH_SHORT).show();
                return;
            }

            stopEditPreview();

            try {
                editPreviewPlayer = new android.media.MediaPlayer();
                editPreviewPlayer.setDataSource(this, editSource);
                editPreviewPlayer.prepare();
                editPreviewPlayer.setVolume(masterVolume, masterVolume);

                final android.media.MediaPlayer current = editPreviewPlayer;
                current.seekTo(inMs);
                current.start();

                editPreviewStopper = new Runnable() {
                    @Override
                    public void run() {
                        if (editPreviewPlayer != current) return;
                        try {
                            if (!current.isPlaying()
                                    || current.getCurrentPosition() >= outMs) {
                                stopEditPreview();
                            } else {
                                editPreviewHandler.postDelayed(this, 40);
                            }
                        } catch (IllegalStateException e) {
                            stopEditPreview();
                        }
                    }
                };
                editPreviewHandler.post(editPreviewStopper);

            } catch (Exception e) {
                stopEditPreview();
                Toast.makeText(this, "Errore anteprima EDIT",
                        Toast.LENGTH_LONG).show();
            }
        });

        new android.app.AlertDialog.Builder(this)
                .setTitle("SWAGGA LAB PRO - EDIT")
                .setView(layout)
                .setPositiveButton("CHIUDI", null)
                .setOnDismissListener(dialog -> stopEditPreview())
                .show();
    }

    private int swaggaPlaybackBpm = 128;

    private void applySwaggaPlaybackSpeed() {
        if (player == null) return;

        float speed = swaggaPlaybackBpm / 128f;

        try {
            android.media.PlaybackParams params =
                    new android.media.PlaybackParams()
                            .setAudioFallbackMode(
                                    android.media.PlaybackParams
                                            .AUDIO_FALLBACK_MODE_DEFAULT)
                            .setSpeed(speed)
                            .setPitch(1.0f);

            player.setPlaybackParams(params);

            android.util.Log.i(
                    "SWAGGA_BPM",
                    "Velocita audio: " + speed + "x"
            );
        } catch (IllegalStateException
                 | IllegalArgumentException e) {
            android.util.Log.w(
                    "SWAGGA_BPM",
                    "Regolazione velocita non disponibile", e
            );
        }
    }

    private void setSwaggaPlaybackBpm(int bpm) {
        swaggaPlaybackBpm = Math.max(60, Math.min(260, bpm));

        if (player != null) {
            applySwaggaPlaybackSpeed();
        }
    }

    private android.media.audiofx.Equalizer swaggaEqualizer;
    private final java.util.Map<Integer, Short> eqLevels = new java.util.HashMap<>();

    private void attachSwaggaEq() {
        if (player == null) return;

        if (swaggaEqualizer != null) {
            swaggaEqualizer.release();
            swaggaEqualizer = null;
        }

        try {
            swaggaEqualizer = new android.media.audiofx.Equalizer(
                    0, player.getAudioSessionId());
            short[] limits = swaggaEqualizer.getBandLevelRange();
            for (short band = 0; band < swaggaEqualizer.getNumberOfBands(); band++) {
                int frequency = swaggaEqualizer.getCenterFreq(band);
                short level = eqLevels.getOrDefault(frequency, (short) 0);
                swaggaEqualizer.setBandLevel(band, (short) Math.max(limits[0], Math.min(limits[1], level)));
            }
            swaggaEqualizer.setEnabled(true);
        } catch (RuntimeException e) {
            android.util.Log.w("SWAGGA_EQ",
                    "Equalizzatore non disponibile", e);
            if (swaggaEqualizer != null) swaggaEqualizer.release();
            swaggaEqualizer = null;
        }
    }

    private void showSwaggaEq() {
        if (player == null) {
            Toast.makeText(this, "Carica e avvia un MP3",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        if (swaggaEqualizer == null) {
            attachSwaggaEq();
        }

        if (swaggaEqualizer == null) {
            Toast.makeText(this, "EQ non disponibile",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        final String[] names = {"BASS", "MID", "TREBLE"};
        final int bands = swaggaEqualizer.getNumberOfBands();

        if (bands <= 0) return;

        android.widget.LinearLayout layout =
                new android.widget.LinearLayout(this);
        layout.setOrientation(1);
        layout.setPadding(35, 20, 35, 20);

        short[] range = swaggaEqualizer.getBandLevelRange();
        int min = range[0];
        int max = range[1];

        for (int i = 0; i < 3; i++) {
            final short band = (short) Math.min(
                    bands - 1,
                    i == 0 ? 0 : i == 1 ? bands / 2 : bands - 1);

            android.widget.TextView label =
                    new android.widget.TextView(this);
            label.setText(names[i]);
            layout.addView(label);

            android.widget.SeekBar slider =
                    new android.widget.SeekBar(this);
            slider.setMax(max - min);
            slider.setProgress(
                    swaggaEqualizer.getBandLevel(band) - min);

            slider.setOnSeekBarChangeListener(
                    new android.widget.SeekBar.OnSeekBarChangeListener() {
                        public void onProgressChanged(
                                android.widget.SeekBar bar,
                                int progress, boolean fromUser) {
                            if (fromUser && swaggaEqualizer != null) {
                                try {
                                    short level = (short)(min + progress);
                                    swaggaEqualizer.setBandLevel(band, level);
                                    eqLevels.put(swaggaEqualizer.getCenterFreq(band), level);
                                } catch (RuntimeException error) {
                                    android.util.Log.w("SWAGGA_EQ", "Regolazione EQ non disponibile", error);
                                }
                            }
                        }
                        public void onStartTrackingTouch(
                                android.widget.SeekBar bar) {}
                        public void onStopTrackingTouch(
                                android.widget.SeekBar bar) {}
                    });

            layout.addView(slider);
        }

        new android.app.AlertDialog.Builder(this)
                .setTitle("ALPHABEAT EQ")
                .setView(layout)
                .setPositiveButton("CHIUDI", null)
                .show();
    }


    private void releaseSwaggaFx() {
        // Nessun effetto Android da rilasciare.
        // Il motore FX software verrà collegato qui.
    }

    private void showSwaggaFx() {
        final String[] choices = {
                "ECHO / DELAY",
                "FILTER",
                "FX OFF"
        };

        new android.app.AlertDialog.Builder(this)
                .setTitle("ALPHABEAT FX")
                .setItems(choices, (dialog, which) -> {
                    if (which == 2) {
                        Toast.makeText(this,
                                "FX OFF",
                                Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this,
                                "FX software in preparazione",
                                Toast.LENGTH_SHORT).show();
                    }
                })
                .show();
    }

    private void showSwaggaLoop() {
        Toast.makeText(this, "LOOP pronto per integrazione",
                Toast.LENGTH_SHORT).show();
    }

    private void playAudio() {
        if (selectedAudio == null) {
            Toast.makeText(this, "Carica prima un MP3",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            if (player == null) {
                player = new android.media.MediaPlayer();
                player.setDataSource(this, selectedAudio);
                player.prepare();
                if (swaggaView != null) {
                    swaggaView.setTrackPosition(0, player.getDuration());
                }
                attachSwaggaEq();
                applyEffectiveVolume();
                player.setOnSeekCompleteListener(mp -> {
                    if (player == mp) updateSwaggaProgress();
                });
                player.setOnCompletionListener(mp -> {
                    if (player != mp) return;
                    consoleTransport = 0;
                    refreshConsoleState();
                    mp.seekTo(0);
                    if (swaggaView != null) {
                        swaggaView.setWaveformProgress(0f);
                        swaggaView.setTrackPosition(0, mp.getDuration());
                    }
                });
            }

            if (!player.isPlaying()) {
                player.start();
                consoleTransport = 1;
                refreshConsoleState();
                applySwaggaPlaybackSpeed();
                updateSwaggaProgress();
            }
        } catch (Exception e) {
            stopAudio();
            Toast.makeText(this, "Errore riproduzione audio",
                    Toast.LENGTH_LONG).show();
        }
    }

    private boolean remixInProgress = false;
    private Uri originalAudio;

    private int selectedTargetBpm = 128;

    private void showTargetBpmSelector() {
        final int[] values = {128, 140, 150, 160, 180, 200, 220, 240, 260};
        final String[] labels = {
                "128 BPM", "140 BPM", "150 BPM",
                "160 BPM", "180 BPM", "200 BPM",
                            "220 BPM", "240 BPM", "260 BPM"
        };

        int selected = 0;
        for (int i = 0; i < values.length; i++) {
            if (values[i] == selectedTargetBpm) {
                selected = i;
                break;
            }
        }

        new android.app.AlertDialog.Builder(this)
                .setTitle("BPM FINALI - FITNESS 32")
                .setSingleChoiceItems(labels, selected,
                        (dialog, which) -> {
                            selectedTargetBpm = values[which];
                            dialog.dismiss();
                            Toast.makeText(this,
                                    "BPM selezionati: " + selectedTargetBpm,
                                    Toast.LENGTH_SHORT).show();
                        })
                .setNegativeButton("ANNULLA", null)
                .show();
    }


    private void showRemixBpmDialog() {
        if (remixInProgress || selectedAudio == null) {
            showRemixBpmDialog(false, selectedTargetBpm);
            return;
        }
        new android.app.AlertDialog.Builder(this)
                .setTitle("ALPHABEAT REMIX ENGINE")
                .setItems(new String[]{
                    "FITNESS 32 CLASSIC  |  128–260 BPM",
                    "V6 CLUB — TAMARRO MODE  |  140 BPM (QA)"
                }, (dialog, which) -> {
                    if (which == 0) showRemixBpmDialog(false, selectedTargetBpm);
                    else showBatidaRemixDialog();
                })
                .setNegativeButton("ANNULLA", null)
                .show();
    }

    private void showBatidaRemixDialog() {
        android.widget.LinearLayout layout = new android.widget.LinearLayout(this);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        int pad = (int)(20 * getResources().getDisplayMetrics().density);
        layout.setPadding(pad, pad, pad, pad);
        android.widget.TextView info = new android.widget.TextView(this);
        info.setText("COMMERCIAL DANCE • WORKOUT\n140 BPM fissi • Base 32 count\nServer QA separato: usa il brano originale. Elaborazione di alcuni minuti; mantieni l'app aperta. Lascia vuoto per analisi automatica.");
        layout.addView(info);
        android.widget.EditText source = new android.widget.EditText(this);
        source.setHint("BPM musicali originali (70–180)");
        source.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        layout.addView(source);
        android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(this)
                .setTitle("V6 CLUB — TAMARRO MODE 140 QA")
                .setView(layout)
                .setNegativeButton("ANNULLA", null)
                .setPositiveButton("CREA REMIX", null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    String input = source.getText().toString().trim();
                    int bpm = 0;
                    try {
                        if (!input.isEmpty()) {
                            bpm = Integer.parseInt(input);
                            if (bpm < 70 || bpm > 180) throw new NumberFormatException();
                        }
                    } catch (NumberFormatException e) {
                        source.setError("Inserisci BPM musicali tra 70 e 180");
                        return;
                    }
                    dialog.dismiss();
                    createFitnessRemix(bpm, 140, true);
                }));
        dialog.show();
    }

    private void showRemixBpmDialog(
            boolean manualDefault, int targetDefault) {
        if (remixInProgress) {
            Toast.makeText(this, "Remix già in elaborazione",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        if (selectedAudio == null) {
            Toast.makeText(this, "Carica prima un MP3",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        final int[] bpmValues = {128, 140, 150, 160, 180, 200, 220, 240, 260};
        final String[] bpmLabels = {
                "128", "140", "150", "160", "180",
                "200", "220", "240", "260"
        };

        android.widget.LinearLayout layout =
                new android.widget.LinearLayout(this);
        layout.setOrientation(1);

        int pad = (int) (20 * getResources()
                .getDisplayMetrics().density);
        layout.setPadding(pad, pad, pad, pad);

        android.widget.TextView original =
                new android.widget.TextView(this);
        original.setText(manualDefault
                ? "BPM ORIGINALI - INSERIMENTO MANUALE"
                : "BPM ORIGINALI - AUTOMATICI");
        original.setTextSize(15);
        layout.addView(original);

        android.widget.EditText source =
                new android.widget.EditText(this);
        source.setHint("BPM originali (70-200)");
        source.setInputType(
                android.text.InputType.TYPE_CLASS_NUMBER);
        source.setVisibility(manualDefault
                ? android.view.View.VISIBLE
                : android.view.View.GONE);
        layout.addView(source);

        android.widget.TextView title =
                new android.widget.TextView(this);
        title.setText("VELOCITÀ DEL REMIX");
        title.setTextSize(17);
        title.setPadding(0, pad, 0, pad / 2);
        layout.addView(title);

        android.widget.GridLayout bpmGrid =
                new android.widget.GridLayout(this);
        bpmGrid.setColumnCount(3);
        bpmGrid.setRowCount(3);
        java.util.List<android.widget.RadioButton> bpmButtons =
                new java.util.ArrayList<>();

        for (int bpm : bpmValues) {
            android.widget.RadioButton button =
                    new android.widget.RadioButton(this);
            button.setText(String.valueOf(bpm));
            button.setTextSize(14);
            button.setTag(bpm);
            button.setChecked(bpm == (isSupportedFitnessTarget(targetDefault) ? targetDefault : 128));
            android.widget.GridLayout.LayoutParams params =
                    new android.widget.GridLayout.LayoutParams();
            params.width = 0;
            params.columnSpec = android.widget.GridLayout.spec(
                    android.widget.GridLayout.UNDEFINED, 1f);
            params.setMargins(0, 0, 0, pad / 3);
            bpmGrid.addView(button, params);
            bpmButtons.add(button);
            button.setOnClickListener(v -> {
                for (android.widget.RadioButton other : bpmButtons) {
                    other.setChecked(other == button);
                }
            });
        }

        layout.addView(bpmGrid);

        new android.app.AlertDialog.Builder(this)
                .setTitle("REMIX FITNESS 32")
                .setView(layout)
                .setNegativeButton("ANNULLA", null)
                .setPositiveButton("CREA IL MIO REMIX",
                        (dialog, which) -> {
                    try {
                        int sourceBpm = manualDefault
                                ? Integer.parseInt(
                                    source.getText().toString().trim())
                                : 0;

                        if (manualDefault &&
                                (sourceBpm < 70 || sourceBpm > 200)) {
                            throw new NumberFormatException();
                        }

                        int targetBpm = isSupportedFitnessTarget(targetDefault) ? targetDefault : 128;

                        for (android.widget.RadioButton button : bpmButtons) {
                            if (button.isChecked()) {
                                targetBpm = (Integer) button.getTag();
                                break;
                            }
                        }

                        if (sourceBpm != 0 && targetBpm < sourceBpm) {
                            Toast.makeText(this,
                                    "BPM finali inferiori agli originali",
                                    Toast.LENGTH_LONG).show();
                            return;
                        }

                        selectedTargetBpm = targetBpm;
                        createFitnessRemix(sourceBpm, targetBpm);

                    } catch (NumberFormatException e) {
                        Toast.makeText(this,
                                "Inserisci BPM originali validi",
                                Toast.LENGTH_LONG).show();
                    }
                })
                .show();
    }

    private static final int SAVE_REMIX_MP3 = 1002;
    private Uri readyRemix;
    private boolean readyRemixV6 = false;
    private int readyRemixLibraryBpm;
    private String readyRemixLibraryTitle;
    private boolean libraryUploadBusy;

    private void exportFitnessMp3() {
        if (remixInProgress) {
            Toast.makeText(this, "Attendi la fine del remix",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        if (readyRemix == null) {
            Toast.makeText(this, "Crea prima un remix",
                    Toast.LENGTH_LONG).show();
            return;
        }

        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("audio/mpeg");
        intent.putExtra(Intent.EXTRA_TITLE,
                "AlphaBeat-Fitness32-Remix.mp3");
        startActivityForResult(intent, SAVE_REMIX_MP3);
    }

    private void saveRemixTo(Uri destination) {
        final Uri source = readyRemix;

        new Thread(() -> {
            try (java.io.InputStream in =
                         getContentResolver().openInputStream(source);
                 java.io.OutputStream out =
                         getContentResolver().openOutputStream(destination, "w")) {

                if (in == null || out == null)
                    throw new java.io.IOException("File non accessibile");

                byte[] buffer = new byte[65536];
                int n;
                while ((n = in.read(buffer)) != -1) {
                    out.write(buffer, 0, n);
                }
                out.flush();

                runOnUiThread(() ->
                        Toast.makeText(this,
                                "MP3 esportato con successo!",
                                Toast.LENGTH_LONG).show());

            } catch (Exception e) {
                android.util.Log.e("SWAGGA_EXPORT",
                        "Errore esportazione", e);
                runOnUiThread(() ->
                        Toast.makeText(this,
                                "Esportazione MP3 non riuscita",
                                Toast.LENGTH_LONG).show());
            }
        }, "SWAGGA-Export").start();
    }

    private static boolean isSupportedFitnessTarget(int bpm) {
        return bpm == 128 || bpm == 140 || bpm == 150 || bpm == 160 || bpm == 180
                || bpm == 200 || bpm == 220 || bpm == 240 || bpm == 260;
    }

    private void createFitnessRemix(int sourceBpm, int targetBpm) {
        createFitnessRemix(sourceBpm, targetBpm, false);
    }

    private void createFitnessRemix(int sourceBpm, int targetBpm, boolean batida) {
        final String modeLabel = batida ? "V6 Club" : "Fitness 32";
        if ((batida && targetBpm != 140) || !isSupportedFitnessTarget(targetBpm)) {
            Toast.makeText(this, "Fitness 32: scegli un BPM da 128 a 260 tra i valori disponibili", Toast.LENGTH_LONG).show();
            return;
        }
        if (selectedAudio == null) {
            Toast.makeText(this, "Carica prima un MP3",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        if (remixInProgress) {
            Toast.makeText(this, "Remix già in elaborazione",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        final Uri source = originalAudio != null
                ? originalAudio : selectedAudio;

        for (int i = 2; i < 4; i++) {
            if (libraryFiles[i] != null &&
                    (source.equals(Uri.fromFile(libraryFiles[i])) ||
                     selectedAudio.equals(Uri.fromFile(libraryFiles[i])))) {
                Toast.makeText(this, "Brano privato: importa un file locale per creare un nuovo remix.", Toast.LENGTH_LONG).show();
                return;
            }
        }

        remixInProgress = true;
        if (swaggaView != null) swaggaView.setRemixPhase("PREPARAZIONE AUDIO");

        Toast.makeText(this, modeLabel + ": elaborazione avviata",
                Toast.LENGTH_LONG).show();

        new Thread(() -> {
            try {
                java.io.File remix =
                    com.digitalfitnessmp3.app.network.FitnessRemixClient
                        .createRemix(getApplicationContext(), source, sourceBpm, targetBpm, batida,
                            phase -> runOnUiThread(() -> {
                                if (remixInProgress && swaggaView != null) swaggaView.setRemixPhase(phase);
                            }));

                runOnUiThread(() -> {
                    stopAudio();
                    selectedAudio = Uri.fromFile(remix);
                    readyRemix = selectedAudio;
                    activeChannel = 1;
                    readyRemixV6 = batida;
                    readyRemixLibraryBpm = targetBpm;
                    readyRemixLibraryTitle = originalTrackTitle + " - " + modeLabel + " " + targetBpm + " BPM";
                    remixInProgress = false;
                    if (swaggaView != null) swaggaView.setRemixPhase(null);
                    updateTrackInfo(selectedAudio,
                            originalTrackTitle + " - " + modeLabel + " " + targetBpm + " BPM");

                    // Il remix e' gia' elaborato al BPM richiesto.
                    // Evita di applicare un secondo cambio di velocita'.
                    swaggaPlaybackBpm = 128;
                    if (swaggaView != null) {
                        swaggaView.setDetectedBpm(targetBpm);
                    }
                    Toast.makeText(this,
                            "REMIX PRONTO! Premi PLAY",
                            Toast.LENGTH_LONG).show();
                });

            } catch (Exception e) {
                android.util.Log.e("SWAGGA_REMIX",
                        "Errore elaborazione", e);

                runOnUiThread(() -> {
                    remixInProgress = false;
                    if (swaggaView != null) swaggaView.setRemixPhase(null);

                    String error = e.getMessage() == null
                            ? "" : e.getMessage();

                    boolean tempoUncertain = sourceBpm == 0
                            && error.contains("HTTP 422")
                            && (error.contains("Nessun candidato BPM")
                                || error.contains("Candidati BPM concorrenti")
                                || error.contains("mai inferiori agli originali"));

                    if (batida) {
                        new android.app.AlertDialog.Builder(this)
                                .setTitle("V6 Club QA")
                                .setMessage(error.contains("HTTP 503") || error.contains("HTTP 404")
                                    ? "Server QA non disponibile o lavoro scaduto. Il brano originale resta intatto."
                                    : "Remix non riuscito: " + error + "\nRiprova con lo stesso brano e gli stessi BPM per recuperare il lavoro.")
                                .setNegativeButton("CHIUDI", null)
                                .setPositiveButton("RIPROVA", (dialog, which) -> showBatidaRemixDialog())
                                .show();
                    } else if (error.contains("HTTP 422") && error.contains("Target BPM non supportato")) {
                        new android.app.AlertDialog.Builder(this)
                                .setTitle("BPM remix non accettato")
                                .setMessage("Il server ha rifiutato " + targetBpm + " BPM. Il servizio deve essere compatibile con questa velocità. Nessun remix è stato creato.")
                                .setNegativeButton("ANNULLA", null)
                                .setPositiveButton("SCEGLI BPM", (dialog, which) -> showRemixBpmDialog(sourceBpm != 0, 128))
                                .show();
                    } else if (tempoUncertain) {
                        new android.app.AlertDialog.Builder(this)
                                .setTitle("BPM AUTO non riconosciuti")
                                .setMessage(
                                    "Il motore non riesce a confermare "
                                    + "il tempo originale. Vuoi indicarlo "
                                    + "manualmente per riprovare?")
                                .setNegativeButton("ANNULLA", null)
                                .setPositiveButton("INSERISCI BPM",
                                    (dialog, which) ->
                                        showRemixBpmDialog(true, targetBpm))
                                .show();
                    } else {
                        Toast.makeText(this,
                                "Remix non riuscito: " + error,
                                Toast.LENGTH_LONG).show();
                    }
                });
            }
        }, batida ? "SWAGGA-BatidaExtreme" : "SWAGGA-Fitness32").start();
    }

    private void pauseAudio() {
        if (player != null && player.isPlaying()) {
            player.pause();
            consoleTransport = 2;
            refreshConsoleState();
            updateSwaggaProgress();
        }
    }

    private void selectAudioChannel(int channel) {
        if (channel < 1 || channel > 4) return;

        if (channel >= 3 && channelAudio[channel - 1] == null) {
            openAudioPickerForChannel(channel);
            return;
        }

        Uri audio = channel == 1 ? originalAudio
                : channel == 2 ? readyRemix
                : channelAudio[channel - 1];

        if (audio == null) {
            Toast.makeText(this,
                    channel == 1 ? "Carica prima un brano originale"
                    : "Crea prima un remix Fitness 32",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        stopAudio();
        activeChannel = channel - 1;
        selectedAudio = audio;
        updateTrackInfo(selectedAudio,
                channel == 2 ? readyRemixLibraryTitle : libraryTitles[channel - 1]);
        if (channel >= 3 && libraryFiles[channel - 1] != null) {
            swaggaPlaybackBpm = 128;
            if (swaggaView != null) swaggaView.setDetectedBpm(libraryBpm[channel - 1]);
        }
        applyEffectiveVolume();

        if (channel == 2) {
            swaggaPlaybackBpm = 128;
            if (swaggaView != null) {
                swaggaView.setDetectedBpm(readyRemixLibraryBpm);
            }
        }

        Toast.makeText(this,
                "CH" + channel + " - CANALE SELEZIONATO",
                Toast.LENGTH_SHORT).show();
    }

    private void seekSwaggaAudio(float progress) {
        if (player == null) return;
        try {
            int duration = player.getDuration();
            if (duration <= 0) return;

            float clamped = Math.max(0f, Math.min(1f, progress));
            int position = Math.round(duration * clamped);

            player.seekTo(position);
            if (swaggaView != null) {
                swaggaView.setWaveformProgress(clamped);
                swaggaView.setTrackPosition(position, duration);
            }
        } catch (IllegalStateException e) {
            android.util.Log.w("SWAGGA_SEEK",
                    "Seek non disponibile", e);
        }
    }

    private void stopAudio() {
        consoleTransport = 0;
        refreshConsoleState();
        swaggaHandler.removeCallbacks(swaggaProgressUpdater);
        if (swaggaView != null) {
            swaggaView.setWaveformProgress(0f);
            // Conserva titolo e durata anche dopo STOP.
            if (player != null) {
                try { swaggaView.setTrackPosition(0, player.getDuration()); }
                catch (IllegalStateException ignored) {}
            }
        }

        // Rilascia gli effetti audio prima del player.
        releaseSwaggaFx();

        // Rilascia EQ prima della sessione audio.
        if (swaggaEqualizer != null) {
            swaggaEqualizer.release();
            swaggaEqualizer = null;
        }

        if (player != null) {
            player.release();
            player = null;
        }
    }

    @Override
    protected void onDestroy() {
        libraryDestroyed = true;
        endLibrarySession();
        stopEditPreview();
        stopAudio();
        super.onDestroy();
    }


    private com.digitalfitnessmp3.app.network.AlphaBeatAccountClient libraryAccount;
    private com.digitalfitnessmp3.app.network.AlphaBeatAccountClient.Session librarySession;
    private boolean libraryLoginBusy;
    private android.os.CancellationSignal libraryGoogleCancel;
    private int libraryLoginGeneration;
    private volatile boolean libraryDestroyed;
    private final Runnable libraryExpiryCheck = new Runnable() {
        public void run() {
            if (librarySession == null || libraryDestroyed) return;
            if (!librarySession.isCurrent()) {
                endLibrarySession();
                Toast.makeText(MainActivity.this, "Sessione AlphaBeat scaduta", Toast.LENGTH_SHORT).show();
            } else {
                swaggaHandler.postDelayed(this, 1000);
            }
        }
    };

    private boolean libraryConfigured() {
        return !BuildConfig.LIBRARY_API.isEmpty()
                && !BuildConfig.LIBRARY_AUTH.isEmpty()
                && !BuildConfig.LIBRARY_PUBLIC_KEY.isEmpty();
    }

    private void openAudioPicker() {
        if (librarySession != null && !librarySession.isCurrent()) endLibrarySession();
        new android.app.AlertDialog.Builder(this)
                .setTitle("ALPHABEAT · FILE")
                .setItems(new String[]{"Importa dal dispositivo", "MY LIBRARY", "SALVA IN MY LIBRARY", "Disconnetti account AlphaBeat"},
                    (dialog, which) -> {
                        if (which == 0) openAudioPickerForChannel(1);
                        else if (which == 1) showLibraryAccess();
                        else if (which == 2) showSaveLibraryRemix();
                        else endLibrarySession();
                    })
                .setNegativeButton("ANNULLA", null).show();
    }

    private void showSaveLibraryRemix() {
        if (libraryUploadBusy) {
            Toast.makeText(this, "Salvataggio cloud in corso", Toast.LENGTH_SHORT).show();
            return;
        }
        if (remixInProgress || readyRemix == null) {
            Toast.makeText(this, "Crea prima un remix completo", Toast.LENGTH_LONG).show();
            return;
        }
        if (!libraryConfigured() || librarySession == null || !librarySession.isCurrent()) {
            Toast.makeText(this, "Accedi e poi seleziona nuovamente SALVA IN MY LIBRARY", Toast.LENGTH_LONG).show();
            showLibraryAccess();
            return;
        }
        final com.digitalfitnessmp3.app.network.AlphaBeatAccountClient.Session session = librarySession;
        final Uri source = readyRemix;
        final int bpm = readyRemixLibraryBpm;
        if (bpm < 40 || bpm > 220) {
            Toast.makeText(this, "Libreria: supportati remix da 40 a 220 BPM. Export locale disponibile.", Toast.LENGTH_LONG).show();
            return;
        }
        if (!"file".equals(source.getScheme()) || source.getPath() == null) return;
        final java.io.File file = new java.io.File(source.getPath());
        if (!file.isFile() || file.length() <= 0 || file.length() > 50_000_000L) {
            Toast.makeText(this, "File non disponibile o superiore a 50 MB", Toast.LENGTH_LONG).show();
            return;
        }
        android.widget.EditText title = new android.widget.EditText(this);
        title.setSingleLine(true);
        title.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(200)});
        title.setText(readyRemixLibraryTitle == null ? "AlphaBeat Remix" : readyRemixLibraryTitle);
        new android.app.AlertDialog.Builder(this)
            .setTitle("SALVA IN MY LIBRARY")
            .setMessage("Carica questo MP3 nel tuo archivio privato. Nessuna pubblicazione automatica.")
            .setView(title)
            .setNegativeButton("ANNULLA", null)
            .setPositiveButton("SALVA", (dialog, which) -> {
                final String name = title.getText().toString().trim();
                if (name.isEmpty() || libraryUploadBusy || librarySession != session
                        || !session.isCurrent() || !source.equals(readyRemix)) {
                    Toast.makeText(this, "Salvataggio annullato: verifica titolo e sessione", Toast.LENGTH_LONG).show();
                    return;
                }
                libraryUploadBusy = true;
                Toast.makeText(this, "Caricamento privato in corso…", Toast.LENGTH_LONG).show();
                new Thread(() -> {
                    boolean ok = false;
                    try {
                        com.digitalfitnessmp3.app.network.AlphaBeatLibraryClient client =
                            new com.digitalfitnessmp3.app.network.AlphaBeatLibraryClient(
                                BuildConfig.LIBRARY_API, BuildConfig.LIBRARY_AUTH, BuildConfig.LIBRARY_PUBLIC_KEY);
                        client.saveRemix(file, session.owner, session.accessToken(), name, bpm, session::isCurrent);
                        ok = true;
                    } catch (Exception ignored) {}
                    final boolean saved = ok;
                    runOnUiThread(() -> {
                        libraryUploadBusy = false;
                        if (libraryDestroyed || isFinishing() || librarySession != session || !session.isCurrent()) return;
                        Toast.makeText(this, saved ? "REMIX SALVATO IN MY LIBRARY"
                            : "Salvataggio non confermato. Controlla MY LIBRARY prima di riprovare.", Toast.LENGTH_LONG).show();
                        if (saved) openAuthenticatedLibrary();
                    });
                }, "AlphaBeat-QA-upload").start();
            }).show();
    }

    private void showLibraryAccess() {
        if (!libraryConfigured()) {
            new android.app.AlertDialog.Builder(this)
                    .setTitle("MY LIBRARY")
                    .setMessage("Archivio cloud non ancora configurato. L'importazione locale resta disponibile.")
                    .setPositiveButton("OK", null).show();
            return;
        }
        if (librarySession != null && librarySession.isCurrent()) {
            openAuthenticatedLibrary();
            return;
        }
        if (librarySession != null) endLibrarySession();
        if (libraryLoginBusy) return;
        android.widget.LinearLayout fields = new android.widget.LinearLayout(this);
        fields.setOrientation(android.widget.LinearLayout.VERTICAL);
        int pad = (int) (24 * getResources().getDisplayMetrics().density);
        fields.setPadding(pad, pad, pad, pad);
        android.widget.EditText email = new android.widget.EditText(this);
        email.setHint("Email account AlphaBeat");
        email.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        android.widget.EditText password = new android.widget.EditText(this);
        password.setHint("Password");
        password.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        email.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
        password.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
        fields.addView(email);
        fields.addView(password);
        android.app.AlertDialog login = new android.app.AlertDialog.Builder(this)
                .setTitle("ALPHABEAT · ACCOUNT")
                .setMessage("Usa esclusivamente un account di collaudo.")
                .setView(fields)
                .setNegativeButton("ANNULLA", null)
                .setPositiveButton("ACCEDI", null)
                .setNeutralButton("ACCEDI CON GOOGLE", null).create();
        login.setOnDismissListener(d -> password.setText(""));
        login.setOnShowListener(d -> {
            login.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
            login.getButton(android.app.AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
                if (libraryLoginBusy) return;
                login.dismiss();
                startLibraryGoogleLogin();
            });
            login.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                if (libraryLoginBusy) return;
                String address = email.getText().toString().trim();
                String secret = password.getText().toString();
                if (address.isEmpty() || secret.isEmpty()) {
                    password.setError("Inserisci email e password");
                    return;
                }
                password.setText("");
                libraryLoginBusy = true;
                final int attempt = ++libraryLoginGeneration;
                login.dismiss();
                new Thread(() -> {
                    try {
                        com.digitalfitnessmp3.app.network.AlphaBeatAccountClient account =
                                new com.digitalfitnessmp3.app.network.AlphaBeatAccountClient(
                                    BuildConfig.LIBRARY_AUTH, BuildConfig.LIBRARY_PUBLIC_KEY);
                        com.digitalfitnessmp3.app.network.AlphaBeatAccountClient.Session session =
                                account.signIn(address, secret);
                        runOnUiThread(() -> {
                            if (libraryDestroyed || isFinishing() || attempt != libraryLoginGeneration) {
                                session.invalidate();
                                new Thread(() -> {
                                    try { account.signOut(session); } catch (Exception ignored) {}
                                }).start();
                                return;
                            }
                            libraryLoginBusy = false;
                            libraryAccount = account;
                            librarySession = session;
                            swaggaHandler.removeCallbacks(libraryExpiryCheck);
                            swaggaHandler.post(libraryExpiryCheck);
                            openAuthenticatedLibrary();
                        });
                    } catch (Exception error) {
                        runOnUiThread(() -> {
                            if (attempt != libraryLoginGeneration) return;
                            libraryLoginBusy = false;
                            if (!libraryDestroyed && !isFinishing())
                                Toast.makeText(this, "Accesso non riuscito. Riprova.", Toast.LENGTH_LONG).show();
                        });
                    }
                }, "AlphaBeat-QA-login").start();
            });
        });
        login.show();
    }

    private void startLibraryGoogleLogin() {
        if (libraryLoginBusy || libraryDestroyed || isFinishing()) return;
        libraryLoginBusy = true;
        final int attempt = ++libraryLoginGeneration;
        try {
            byte[] random = new byte[32];
            new java.security.SecureRandom().nextBytes(random);
            StringBuilder raw = new StringBuilder();
            for (byte b : random) raw.append(String.format(java.util.Locale.US, "%02x", b & 255));
            final String nonce = raw.toString();
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256")
                .digest(nonce.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hashed = new StringBuilder();
            for (byte b : digest) hashed.append(String.format(java.util.Locale.US, "%02x", b & 255));
            com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption option =
                new com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption.Builder(
                    BuildConfig.LIBRARY_GOOGLE_WEB_CLIENT)
                    .setNonce(hashed.toString()).build();
            androidx.credentials.GetCredentialRequest request =
                new androidx.credentials.GetCredentialRequest.Builder().addCredentialOption(option).build();
            libraryGoogleCancel = new android.os.CancellationSignal();
            androidx.credentials.CredentialManager.create(this).getCredentialAsync(
                this, request, libraryGoogleCancel, getMainExecutor(),
                new androidx.credentials.CredentialManagerCallback<
                    androidx.credentials.GetCredentialResponse,
                    androidx.credentials.exceptions.GetCredentialException>() {
                    @Override public void onResult(androidx.credentials.GetCredentialResponse result) {
                        if (libraryDestroyed || isFinishing() || attempt != libraryLoginGeneration) return;
                        libraryGoogleCancel = null;
                        try {
                            androidx.credentials.Credential credential = result.getCredential();
                            if (!(credential instanceof androidx.credentials.CustomCredential)
                                    || !com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL.equals(credential.getType()))
                                throw new IllegalArgumentException("Unsupported credential");
                            String token = com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
                                .createFrom(credential.getData()).getIdToken();
                            new Thread(() -> {
                                try {
                                    com.digitalfitnessmp3.app.network.AlphaBeatAccountClient account =
                                        new com.digitalfitnessmp3.app.network.AlphaBeatAccountClient(
                                            BuildConfig.LIBRARY_AUTH, BuildConfig.LIBRARY_PUBLIC_KEY);
                                    com.digitalfitnessmp3.app.network.AlphaBeatAccountClient.Session session =
                                        account.signInGoogle(token, nonce);
                                    runOnUiThread(() -> {
                                        if (libraryDestroyed || isFinishing() || attempt != libraryLoginGeneration) {
                                            session.invalidate();
                                            new Thread(() -> { try { account.signOut(session); } catch (Exception ignored) {} }).start();
                                            return;
                                        }
                                        libraryLoginBusy = false;
                                        libraryAccount = account;
                                        librarySession = session;
                                        swaggaHandler.removeCallbacks(libraryExpiryCheck);
                                        swaggaHandler.post(libraryExpiryCheck);
                                        openAuthenticatedLibrary();
                                    });
                                } catch (Exception error) {
                                    runOnUiThread(() -> failLibraryGoogleLogin(attempt, "Accesso Google non riuscito. Riprova."));
                                }
                            }, "AlphaBeat-Google-QA").start();
                        } catch (Exception error) {
                            failLibraryGoogleLogin(attempt, "Credenziale Google non disponibile.");
                        }
                    }
                    @Override public void onError(androidx.credentials.exceptions.GetCredentialException error) {
                        failLibraryGoogleLogin(attempt, "Accesso Google annullato o non disponibile.");
                    }
                });
        } catch (Exception error) {
            failLibraryGoogleLogin(attempt, "Accesso Google non disponibile.");
        }
    }

    private void failLibraryGoogleLogin(int attempt, String message) {
        if (attempt != libraryLoginGeneration) return;
        libraryGoogleCancel = null;
        libraryLoginBusy = false;
        if (!libraryDestroyed && !isFinishing()) Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private void openAuthenticatedLibrary() {
        final com.digitalfitnessmp3.app.network.AlphaBeatAccountClient.Session session = librarySession;
        if (session == null || !session.isCurrent()) return;
        try {
            openPersonalLibrary(
                new com.digitalfitnessmp3.app.network.AlphaBeatLibraryClient(
                    BuildConfig.LIBRARY_API, BuildConfig.LIBRARY_AUTH, BuildConfig.LIBRARY_PUBLIC_KEY),
                session.owner, session.accessToken(), BuildConfig.LIBRARY_PUBLIC_KEY,
                session::isCurrent);
        } catch (Exception error) {
            Toast.makeText(this, "Libreria non disponibile", Toast.LENGTH_SHORT).show();
        }
    }

    private void endLibrarySession() {
        final com.digitalfitnessmp3.app.network.AlphaBeatAccountClient account = libraryAccount;
        final com.digitalfitnessmp3.app.network.AlphaBeatAccountClient.Session session = librarySession;
        libraryLoginGeneration++;
        if (libraryGoogleCancel != null) { libraryGoogleCancel.cancel(); libraryGoogleCancel = null; }
        libraryLoginBusy = false;
        librarySession = null;
        libraryAccount = null;
        swaggaHandler.removeCallbacks(libraryExpiryCheck);
        if (session != null) session.invalidate();
        clearPersonalLibrary();
        if (account != null && session != null) {
            new Thread(() -> {
                try { account.signOut(session); }
                catch (Exception ignored) {
                    runOnUiThread(() -> {
                        if (!libraryDestroyed && !isFinishing())
                            Toast.makeText(this, "Sessione locale terminata; logout remoto non confermato.", Toast.LENGTH_LONG).show();
                    });
                }
            }, "AlphaBeat-QA-logout").start();
        }
    }

    private void openAudioPickerForChannel(int channel) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("audio/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);

        int requestCode = channel == 3 ? PICK_AUDIO_CH3
                : channel == 4 ? PICK_AUDIO_CH4 : PICK_AUDIO;

        startActivityForResult(intent, requestCode);
    }

    @Override
    protected void onActivityResult(
            int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == SAVE_REMIX_MP3) {
            if (resultCode == RESULT_OK && data != null
                    && data.getData() != null) {
                saveRemixTo(data.getData());
            }
            return;
        }

        if ((requestCode == PICK_AUDIO_CH3
                || requestCode == PICK_AUDIO_CH4)
                && resultCode == RESULT_OK
                && data != null && data.getData() != null) {

            int channel = requestCode == PICK_AUDIO_CH3 ? 3 : 4;
            Uri audio = data.getData();

            stopEditPreview();
            stopAudio();
            if (libraryFiles[channel - 1] != null) {
                libraryFiles[channel - 1].delete();
                libraryFiles[channel - 1] = null;
                libraryTitles[channel - 1] = null;
                libraryBpm[channel - 1] = 0;
            }
            channelAudio[channel - 1] = audio;
            activeChannel = channel - 1;
            selectedAudio = audio;
            updateTrackInfo(audio, null);

            Toast.makeText(this,
                    "CH" + channel + " - BRANO CARICATO",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        if (requestCode == PICK_AUDIO
                && resultCode == RESULT_OK
                && data != null) {
            stopAudio();
            selectedAudio = data.getData();
            originalAudio = selectedAudio;
            activeChannel = 0;
            readyRemix = null;
            readyRemixV6 = false;
            readyRemixLibraryBpm = 0;
            readyRemixLibraryTitle = null;
            originalTrackTitle = "Brano originale";
            updateTrackInfo(selectedAudio, null);
            final Uri audio = selectedAudio;
            final int generation = waveformGeneration;

            new Thread(() -> {
                try {
                    com.digitalfitnessmp3.app.audio.PcmAudio pcm =
                        com.digitalfitnessmp3.app.audio.NativeAudioDecoder
                            .decode(getApplicationContext(), audio);

                    double detected =
                        com.digitalfitnessmp3.app.audio.BeatDetector
                            .detectBpm(pcm);

                    if (!Double.isFinite(detected)
                            || detected < 60 || detected > 200) {
                        throw new IllegalStateException(
                            "BPM non riconosciuti");
                    }

                    final int bpm = (int) Math.round(detected);

                    runOnUiThread(() -> {
                        if (generation == waveformGeneration
                                && swaggaView != null) {
                            swaggaView.setDetectedBpm(bpm);
                            Toast.makeText(this,
                                "BPM AUTO: " + bpm,
                                Toast.LENGTH_SHORT).show();
                        }
                    });
                } catch (Exception e) {
                    android.util.Log.w(
                        "SWAGGA_BPM_AUTO",
                        "Rilevamento non disponibile", e);
                }
            }, "SWAGGA-BPM-AUTO").start();



            Toast.makeText(this,
                    "MP3 caricato nella DAW",
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void openSmartCast() {
        // System mirroring, not a separate Cast receiver or audio stream.
        try {
            startActivity(new Intent(android.provider.Settings.ACTION_CAST_SETTINGS));
        } catch (android.content.ActivityNotFoundException | SecurityException e) {
            new android.app.AlertDialog.Builder(this)
                .setTitle("SMART CAST")
                .setMessage("Apri il pannello rapido Android e cerca Trasmetti schermo / Cast. Seleziona un dispositivo compatibile sulla stessa rete Wi-Fi, poi torna in SWAGGA LAB.")
                .setPositiveButton("OK", null)
                .show();
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN
        );

        getWindow().getDecorView().setFitsSystemWindows(false);

        if (android.os.Build.VERSION.SDK_INT >= 30) {
            WindowInsetsController controller =
                    getWindow().getInsetsController();

            if (controller != null) {
                controller.hide(
                        WindowInsets.Type.statusBars()
                        | WindowInsets.Type.navigationBars()
                );
                controller.setSystemBarsBehavior(
                        WindowInsetsController
                        .BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                );
            }
        }


        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        );

        getWindow().setStatusBarColor(Color.BLACK);
        getWindow().setNavigationBarColor(Color.BLACK);


        swaggaView = new com.alphabeat.daw.ui.SwaggaConsoleView(
                this,
                new com.alphabeat.daw.ui.SwaggaConsoleView.Actions() {
                    public void openCast() { openSmartCast(); }
                    public void openSetup() {
                        showSwaggaSetup();
                    }
                    public void openEdit() {
                        showSwaggaEdit();
                    }
                    public void channel1() {
                        selectAudioChannel(1);
                    }
                    public void channel2() {
                        selectAudioChannel(2);
                    }
                    public void channel3() {
                        selectAudioChannel(3);
                    }
                    public void channel4() {
                        selectAudioChannel(4);
                    }
                    public void channelVolume(int channel, float value) {
                        setChannelVolume(channel, value);
                    }
                    public void muteChannel(int channel) {
                        MainActivity.this.muteChannel(channel);
                    }
                    public void soloChannel(int channel) {
                        MainActivity.this.soloChannel(channel);
                    }

                    public void file() { openAudioPicker(); }
                    public void play() { playAudio(); }
                    public void pause() { pauseAudio(); }
                    public void stop() { stopAudio(); }
                    public void remix() {
                        showRemixBpmDialog();
                    }
                    public void exportMp3() {
                        exportFitnessMp3();
                    }
                    public void masterVolume(float value) {
                        setMasterVolume(value);
                    }
                    public void selectBpm() {
                        showTargetBpmSelector();
                    }
                    public void seek(float progress) {
                        seekSwaggaAudio(progress);
                    }
                    public void playbackBpm(int bpm) {
                        setSwaggaPlaybackBpm(bpm);
                    }
                    public void openEq() {
                        showSwaggaEq();
                    }
                    public void openFx() {
                        showSwaggaFx();
                    }
                    public void openLoop() {
                        showSwaggaLoop();
                    }
                }
            );
        setContentView(swaggaView);
    }


    private void showApprovedDesign() {
        FrameLayout root = new FrameLayout(this);
        android.graphics.drawable.GradientDrawable background =
                new android.graphics.drawable.GradientDrawable(
                        android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT,
                        new int[] {
                                Color.rgb(8, 12, 17),
                                Color.rgb(13, 28, 31),
                                Color.rgb(8, 12, 17)
                        });
        root.setBackground(background);

        ImageView reference = new ImageView(this);
        reference.setImageResource(R.drawable.swagga_reference);
        reference.setScaleType(ImageView.ScaleType.FIT_CENTER);
        reference.setAdjustViewBounds(false);

        root.addView(reference,
                new FrameLayout.LayoutParams(-1, -1));

        setContentView(root);
    }

    private void buildSwaggaInterface() {
        final int neon = Color.rgb(78, 255, 145);
        final int cyan = Color.rgb(45, 218, 235);
        final int panel = Color.rgb(21, 27, 34);

        android.widget.LinearLayout root =
                new android.widget.LinearLayout(this);
        root.setOrientation(android.widget.LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(8, 12, 17));
        root.setPadding(16, 10, 16, 10);

        android.widget.LinearLayout top =
                new android.widget.LinearLayout(this);
        top.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        top.setGravity(android.view.Gravity.CENTER_VERTICAL);

        android.widget.TextView title =
                new android.widget.TextView(this);
        title.setText("ALPHABEAT  /  SWAGGA LAB");
        title.setTextColor(neon);
        title.setTextSize(20);
        title.setTypeface(null, android.graphics.Typeface.BOLD);

        top.addView(title, new android.widget.LinearLayout.LayoutParams(
                0, -2, 1));

        addButton(top, "STOP", panel, () -> stopAudio());
        addButton(top, "PLAY", neon, () -> playAudio());
        addButton(top, "PAUSE", cyan, () -> pauseAudio());

        root.addView(top);

        android.widget.LinearLayout actions =
                new android.widget.LinearLayout(this);
        actions.setPadding(0, 12, 0, 12);

        addButton(actions, "FILE", cyan, () -> openAudioPicker());
        addButton(actions, "EDIT", panel, () ->
                Toast.makeText(this, "Editor in preparazione",
                        Toast.LENGTH_SHORT).show());
        addButton(actions, "REMIX", panel, () ->
                Toast.makeText(this, "Motore remix da collegare",
                        Toast.LENGTH_SHORT).show());

        root.addView(actions);

        android.widget.TextView timeline =
                new android.widget.TextView(this);
        timeline.setText("MULTITRACK TIMELINE   •   32 COUNTS");
        timeline.setTextColor(neon);
        timeline.setTextSize(16);
        timeline.setPadding(12, 12, 12, 12);
        timeline.setBackgroundColor(panel);
        root.addView(timeline);

        android.widget.LinearLayout workspace =
                new android.widget.LinearLayout(this);
        workspace.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        workspace.setPadding(0, 8, 0, 0);

        for (int i = 1; i <= 4; i++) {
            android.widget.LinearLayout channel =
                    new android.widget.LinearLayout(this);
            channel.setOrientation(android.widget.LinearLayout.VERTICAL);
            channel.setPadding(8, 8, 8, 8);
            channel.setBackgroundColor(panel);

            android.widget.TextView name =
                    new android.widget.TextView(this);
            name.setText("CH " + i);
            name.setTextColor(cyan);
            name.setTextSize(15);
            name.setGravity(android.view.Gravity.CENTER);
            channel.addView(name);

            android.widget.SeekBar volume =
                    new android.widget.SeekBar(this);
            volume.setMax(100);
            volume.setProgress(75);
            volume.setRotation(270);
            channel.addView(volume,
                    new android.widget.LinearLayout.LayoutParams(
                            -1, 100));

            android.widget.TextView level =
                    new android.widget.TextView(this);
            level.setText("VOL 75%");
            level.setTextColor(Color.WHITE);
            level.setGravity(android.view.Gravity.CENTER);
            channel.addView(level);

            volume.setOnSeekBarChangeListener(
                    new android.widget.SeekBar.OnSeekBarChangeListener() {
                public void onProgressChanged(
                        android.widget.SeekBar bar, int value,
                        boolean fromUser) {
                    level.setText("VOL " + value + "%");
                }
                public void onStartTrackingTouch(
                        android.widget.SeekBar bar) {}
                public void onStopTrackingTouch(
                        android.widget.SeekBar bar) {}
            });

            android.widget.LinearLayout.LayoutParams cp =
                    new android.widget.LinearLayout.LayoutParams(
                            0, -1, 1);
            cp.setMargins(3, 0, 3, 0);
            workspace.addView(channel, cp);
        }

        root.addView(workspace,
                new android.widget.LinearLayout.LayoutParams(
                        -1, 0, 1));

        setContentView(root);
    }

    private void addButton(
            android.widget.LinearLayout parent,
            String label, int color, Runnable action) {

        android.widget.Button button =
                new android.widget.Button(this);
        button.setText(label);
        button.setTextColor(
                color == Color.rgb(78, 255, 145)
                || color == Color.rgb(45, 218, 235)
                ? Color.BLACK : Color.WHITE);
        button.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(color));
        button.setOnClickListener(v -> action.run());

        android.widget.LinearLayout.LayoutParams params =
                new android.widget.LinearLayout.LayoutParams(
                        -2, 48);
        params.setMargins(4, 0, 4, 0);
        parent.addView(button, params);
    }
}
