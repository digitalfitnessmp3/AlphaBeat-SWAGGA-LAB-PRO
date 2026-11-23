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
    private final float[] channelVolume = {
            0.75f, 0.75f, 0.75f, 0.75f
    };
    private final boolean[] channelMuted = new boolean[4];
    private int activeChannel = 0;

    private Uri selectedAudio;
    private android.media.MediaPlayer player;
    private float masterVolume = 0.75f;

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
        if (player != null) {
            applyEffectiveVolume();
        }
    }
    private com.alphabeat.daw.ui.SwaggaConsoleView swaggaView;
    private int waveformGeneration = 0;

    private final android.os.Handler swaggaHandler =
            new android.os.Handler(android.os.Looper.getMainLooper());

    private final Runnable swaggaProgressUpdater = new Runnable() {
        @Override
        public void run() {
            if (player == null || swaggaView == null) return;

            try {
                int duration = player.getDuration();
                if (duration > 0) {
                    swaggaView.setWaveformProgress(
                            (float) player.getCurrentPosition() / duration);
                }

                if (player.isPlaying()) {
                    swaggaHandler.postDelayed(this, 80);
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

    private void attachSwaggaEq() {
        if (player == null) return;

        if (swaggaEqualizer != null) {
            swaggaEqualizer.release();
            swaggaEqualizer = null;
        }

        try {
            swaggaEqualizer = new android.media.audiofx.Equalizer(
                    0, player.getAudioSessionId());
            swaggaEqualizer.setEnabled(true);
        } catch (RuntimeException e) {
            android.util.Log.w("SWAGGA_EQ",
                    "Equalizzatore non disponibile", e);
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
                                swaggaEqualizer.setBandLevel(
                                        band, (short)(min + progress));
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
                attachSwaggaEq();
                applyEffectiveVolume();
                player.setOnCompletionListener(mp -> {
                    mp.seekTo(0);
                });
            }

            if (!player.isPlaying()) {
                player.start();
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

    private int selectedTargetBpm = 160;

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
        showRemixBpmDialog(false, selectedTargetBpm);
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
        java.util.List<android.widget.RadioButton> bpmButtons =
                new java.util.ArrayList<>();

        for (int bpm : bpmValues) {
            android.widget.RadioButton button =
                    new android.widget.RadioButton(this);
            button.setText(String.valueOf(bpm));
            button.setTextSize(13);
            button.setTag(bpm);
            button.setPadding(0, 0, pad / 3, 0);
            bpmGrid.addView(button);
            bpmButtons.add(button);
            button.setChecked(bpm == targetDefault);
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

                        int targetBpm = targetDefault;

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

    private void createFitnessRemix(int sourceBpm, int targetBpm) {
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

        remixInProgress = true;

        Toast.makeText(this, "Fitness 32: elaborazione avviata",
                Toast.LENGTH_LONG).show();

        new Thread(() -> {
            try {
                java.io.File remix =
                    com.digitalfitnessmp3.app.network.FitnessRemixClient
                        .createRemix(getApplicationContext(), source, sourceBpm, targetBpm);

                runOnUiThread(() -> {
                    stopAudio();
                    selectedAudio = Uri.fromFile(remix);
                    readyRemix = selectedAudio;
                    remixInProgress = false;

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

                    String error = e.getMessage() == null
                            ? "" : e.getMessage();

                    boolean tempoUncertain = sourceBpm == 0
                            && error.contains("HTTP 422")
                            && (error.contains("Nessun candidato BPM")
                                || error.contains("Candidati BPM concorrenti")
                                || error.contains("mai inferiori agli originali"));

                    if (tempoUncertain) {
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
        }, "SWAGGA-Fitness32").start();
    }

    private void pauseAudio() {
        if (player != null && player.isPlaying()) {
            player.pause();
            swaggaHandler.removeCallbacks(swaggaProgressUpdater);
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
        applyEffectiveVolume();

        if (channel == 2) {
            swaggaPlaybackBpm = 128;
            if (swaggaView != null) {
                swaggaView.setDetectedBpm(selectedTargetBpm);
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
            }
        } catch (IllegalStateException e) {
            android.util.Log.w("SWAGGA_SEEK",
                    "Seek non disponibile", e);
        }
    }

    private void stopAudio() {
        swaggaHandler.removeCallbacks(swaggaProgressUpdater);
        if (swaggaView != null) swaggaView.setWaveformProgress(0f);

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
        stopEditPreview();
        stopAudio();
        super.onDestroy();
    }


    private void openAudioPicker() {
        openAudioPickerForChannel(1);
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

            channelAudio[channel - 1] = audio;
            stopAudio();
            activeChannel = channel - 1;
            selectedAudio = audio;

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
            readyRemix = null;
            final Uri audio = selectedAudio;
            final int generation = ++waveformGeneration;

            if (swaggaView != null) {
                swaggaView.setWaveformPeaks(null);
                swaggaView.setWaveformProgress(0f);
            }

            new Thread(() -> {
                try {
                    float[] peaks =
                            com.alphabeat.daw.audio.WaveformAnalyzer
                                    .analyze(this, audio, 600);

                    runOnUiThread(() -> {
                        if (generation == waveformGeneration
                                && swaggaView != null) {
                            swaggaView.setWaveformPeaks(peaks);
                        }
                    });
                } catch (Exception e) {
                    android.util.Log.e(
                            "SWAGGA_AUDIO",
                            "Errore analisi waveform", e);
                    runOnUiThread(() -> {
                        if (generation == waveformGeneration) {
                            Toast.makeText(this,
                                    "Waveform non disponibile",
                                    Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }, "SWAGGA-Waveform").start();

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
