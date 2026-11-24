package com.alphabeat.daw.ui;

import android.content.Context;
import android.graphics.*;
import android.view.*;
import android.widget.FrameLayout;
import android.widget.ImageView;

import com.alphabeat.daw.R;

public final class SwaggaConsoleView extends FrameLayout {

    public interface Actions {
        void file();
        void play();
        void pause();
        void stop();
        void remix();
        void exportMp3();
        void selectBpm();
        void masterVolume(float value);
        void openSetup();
        void openEdit();
        void channel1();
        void channel2();
        void channel3();
        void channel4();
        void channelVolume(int channel, float value);
        void muteChannel(int channel);
        void soloChannel(int channel);
        void seek(float progress);
        void playbackBpm(int bpm);
        void openEq();
        void openFx();
        void openLoop();
    }

    private final Actions actions;
    private float[] waveformPeaks = new float[0];
    private float waveformProgress = 0f;
    private View waveformOverlay;
    private boolean faderDragging = false;
    private boolean waveformDragging = false;
    private String displayedTrackTitle = null;
    private int displayedDurationMs = 0;
    private int displayedPositionMs = 0;
    private View trackInfoOverlay;

    // La grafica PNG contiene un testo segnaposto: sovrascriviamo soltanto
    // il riquadro del titolo e del timer, senza alterare gli altri controlli.
    public void setTrackInfo(String title, int durationMs) {
        displayedTrackTitle = title == null ? "Brano audio" : title.trim();
        displayedDurationMs = Math.max(0, durationMs);
        displayedPositionMs = 0;
        if (trackInfoOverlay != null) trackInfoOverlay.invalidate();
    }

    public void setTrackPosition(int positionMs, int durationMs) {
        displayedDurationMs = Math.max(0, durationMs);
        displayedPositionMs = Math.max(0, Math.min(positionMs, displayedDurationMs));
        if (trackInfoOverlay != null) trackInfoOverlay.invalidate();
    }

    private static String formatTrackTime(int ms) {
        long seconds = Math.max(0, ms) / 1000L;
        return String.format(java.util.Locale.ITALY, "%02d:%02d",
                seconds / 60L, seconds % 60L);
    }


    private SwaggaBpmControl autoBpmControl;
    private SwaggaBpmDisplay autoBpmDisplay;
    private Runnable autoBpmRefresh;

    public void setDetectedBpm(int bpm) {
        if (autoBpmControl != null && autoBpmRefresh != null) {
            autoBpmControl.setBpm(bpm);
            // Aggiorna solo il display: non cambia la velocita audio.
            if (autoBpmDisplay != null) {
                autoBpmDisplay.setBpm(bpm);
            }
            autoBpmRefreshDisplayOnly();
        }
    }

    private void autoBpmRefreshDisplayOnly() {
        if (autoBpmRefreshDisplay != null) {
            autoBpmRefreshDisplay.run();
        }
    }

    private Runnable autoBpmRefreshDisplay;

    public void setWaveformPeaks(float[] peaks) {
        waveformPeaks = peaks == null ? new float[0] : peaks.clone();
        if (waveformOverlay != null) waveformOverlay.invalidate();
    }

    public void setWaveformProgress(float progress) {
        waveformProgress = Math.max(0f, Math.min(1f, progress));
        if (waveformOverlay != null) waveformOverlay.invalidate();
    }

    public SwaggaConsoleView(Context context, Actions actions) {
        super(context);
        this.actions = actions;

        setBackgroundColor(Color.rgb(4, 8, 11));

        ImageView design = new ImageView(context);
        design.setImageResource(R.drawable.swagga_fader_background);
        design.setScaleType(ImageView.ScaleType.FIT_XY);

        addView(design, new LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT
        ));

        // Overlay titolo/timer: il testo nella PNG e' statico.
        trackInfoOverlay = new View(context) {
            private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            private final Paint coverPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

            @Override protected void onDraw(Canvas canvas) {
                super.onDraw(canvas);
                if (displayedTrackTitle == null) return;

                float w = getWidth();
                float h = getHeight();
                // Non coprire l'icona a sinistra, la barra o i bordi neon.
                coverPaint.setColor(Color.rgb(3, 9, 12));
                canvas.drawRect(w * 0.070f, h * 0.278f,
                        w * 0.680f, h * 0.324f, coverPaint);
                canvas.drawRect(w * 0.888f, h * 0.278f,
                        w * 0.982f, h * 0.324f, coverPaint);

                textPaint.setColor(Color.WHITE);
                textPaint.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
                textPaint.setTextAlign(Paint.Align.LEFT);
                textPaint.setTextSize(h * 0.026f);

                String visibleTitle = displayedTrackTitle;
                float availableWidth = w * 0.600f;
                if (textPaint.measureText(visibleTitle) > availableWidth) {
                    final String ellipsis = "…";
                    while (!visibleTitle.isEmpty()
                            && textPaint.measureText(visibleTitle + ellipsis) > availableWidth) {
                        visibleTitle = visibleTitle.substring(0, visibleTitle.length() - 1);
                    }
                    visibleTitle += ellipsis;
                }
                canvas.drawText(visibleTitle, w * 0.074f, h * 0.312f, textPaint);

                textPaint.setTextAlign(Paint.Align.RIGHT);
                textPaint.setTextSize(h * 0.022f);
                canvas.drawText(formatTrackTime(displayedPositionMs)
                        + " / " + formatTrackTime(displayedDurationMs),
                        w * 0.978f, h * 0.312f, textPaint);
            }
        };
        trackInfoOverlay.setClickable(false);
        addView(trackInfoOverlay, new LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        // Cornice QA ampliata: contiene l'intera waveform senza cambiarne
        // altezza, ampiezza, posizione, colori o progressione.
        // Deve essere disegnata prima della waveform per lasciare i picchi
        // sempre in primo piano. Non intercetta il touch.
        final View waveformFrame = new View(context) {
            private final Paint border = new Paint(Paint.ANTI_ALIAS_FLAG);

            @Override protected void onDraw(Canvas canvas) {
                super.onDraw(canvas);
                final float w = getWidth();
                final float h = getHeight();

                // Il viewport attuale della waveform va da 30% a 54%
                // dell'altezza (center = 42%). La cornice aggiunge
                // una tolleranza di 0.8% su entrambi i lati.
                final RectF frame = new RectF(
                        w * 0.011f, h * 0.292f,
                        w * 0.960f, h * 0.548f);

                border.setStyle(Paint.Style.STROKE);
                border.setStrokeWidth(Math.max(1.5f, h * 0.0024f));
                border.setColor(Color.rgb(0, 220, 224));
                border.setShadowLayer(h * 0.007f, 0f, 0f,
                        Color.argb(180, 0, 255, 245));
                canvas.drawRoundRect(frame, h * 0.018f, h * 0.018f, border);
                border.clearShadowLayer();
            }
        };
        waveformFrame.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        waveformFrame.setClickable(false);
        addView(waveformFrame, new LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        waveformOverlay = new View(context) {
            private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

            @Override
            protected void onDraw(Canvas canvas) {
                if (waveformPeaks.length == 0) return;

                float width = getWidth();
                float height = getHeight();
                float middle = height / 2f;

                paint.setStrokeWidth(2f);

                for (int i = 0; i < waveformPeaks.length; i++) {
                    float x = (i + 0.5f) * width / waveformPeaks.length;
                    float amplitude = Math.min(1f,
                            Math.max(0f, waveformPeaks[i]));
                    float extent = amplitude * middle * 0.88f;

                    paint.setColor(
                            x <= waveformProgress * width
                                    ? Color.CYAN
                                    : Color.rgb(57, 255, 20));

                    canvas.drawLine(x, middle - extent,
                            x, middle + extent, paint);
                }

                paint.setColor(Color.WHITE);
                paint.setStrokeWidth(2f);
                float cursor = waveformProgress * width;
                canvas.drawLine(cursor, 0, cursor, height, paint);
            }
        };

        LayoutParams waveformParams = new LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT
        );

        waveformParams.leftMargin =
                (int)(getResources().getDisplayMetrics().widthPixels * 0.01f);
        waveformParams.rightMargin =
                (int)(getResources().getDisplayMetrics().widthPixels * 0.04f);
        waveformParams.topMargin =
                (int)(getResources().getDisplayMetrics().heightPixels * 0.30f);
        waveformParams.bottomMargin =
                (int)(getResources().getDisplayMetrics().heightPixels * 0.46f);

        addView(waveformOverlay, waveformParams);

        // Livello grafico dei cinque fader.
        // Non intercetta i tocchi: i controlli restano
        // gestiti dalla console.
        final SwaggaFaderOverlay faderOverlay =
                new SwaggaFaderOverlay(context);

        addView(faderOverlay, new LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT
        ));

        faderOverlay.setFaderListener((channel, value) -> {
            if (channel == 4) {
                actions.masterVolume(value);
            } else if (channel >= 0 && channel < 4) {
                actions.channelVolume(channel + 1, value);
            }
        });


        final SwaggaBpmDisplay bpmDisplay =
                new SwaggaBpmDisplay(context);

        // Solo il numero BPM, senza pulsanti aggiuntivi e senza intercettare tocchi.
        final int[] visibleBpm = {128};
        final View bpmNumberOverlay = new View(context) {
            private final Paint bpmPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            @Override protected void onDraw(Canvas canvas) {
                super.onDraw(canvas);
                bpmPaint.setColor(Color.WHITE);
                bpmPaint.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
                bpmPaint.setTextAlign(Paint.Align.CENTER);
                bpmPaint.setTextSize(getHeight() * 47f / 941f);
                canvas.drawText(String.valueOf(visibleBpm[0]),
                        getWidth() * 1244f / 1672f,
                        getHeight() * 63f / 941f, bpmPaint);
            }
        };
        bpmNumberOverlay.setClickable(false);
        addView(bpmNumberOverlay, new LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        // Controllo BPM PRO: 60-200, passo 1.
        final SwaggaBpmControl bpmControl =
                new SwaggaBpmControl(context);
        autoBpmControl = bpmControl;
        autoBpmDisplay = bpmDisplay;
        autoBpmRefreshDisplay = () -> {
            visibleBpm[0] = bpmControl.getBpm();
            bpmDisplay.setBpm(visibleBpm[0]);
            bpmNumberOverlay.invalidate();
        };

        autoBpmRefresh = () -> {
            autoBpmRefreshDisplay.run();
            actions.playbackBpm(visibleBpm[0]);
        };


        addView(new View(context) {
            @Override
            public boolean onTouchEvent(android.view.MotionEvent e) {
                float x = e.getX() / getWidth();
                float y = e.getY() / getHeight();

                // Trascinamento waveform: area centrale.
                if (waveformDragging) {
                    if (e.getActionMasked() == MotionEvent.ACTION_MOVE
                            || e.getActionMasked() == MotionEvent.ACTION_UP) {
                        float progress = Math.max(0f,
                                Math.min(1f, x));
                        setWaveformProgress(progress);
                        actions.seek(progress);
                    }
                    if (e.getActionMasked() == MotionEvent.ACTION_UP
                            || e.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                        waveformDragging = false;
                    }
                    return true;
                }

                if (e.getActionMasked() == MotionEvent.ACTION_DOWN
                        && y >= 0.30f && y <= 0.54f) {
                    waveformDragging = true;
                    float progress = Math.max(0f,
                            Math.min(1f, x));
                    setWaveformProgress(progress);
                    actions.seek(progress);
                    return true;
                }

                // Mantiene il controllo del gesto fino al rilascio.
                if (e.getActionMasked() == MotionEvent.ACTION_DOWN
                && y >= 0.905f && y <= 0.965f) {
            float[] muteX = {0.066f, 0.234f, 0.402f, 0.568f};
            float[] soloX = {0.118f, 0.286f, 0.454f, 0.620f};

            for (int i = 0; i < 4; i++) {
                if (Math.abs(x - muteX[i]) <= 0.022f) {
                    actions.muteChannel(i + 1);
                    return true;
                }
                if (Math.abs(x - soloX[i]) <= 0.022f) {
                    actions.soloChannel(i + 1);
                    return true;
                }
            }
        }

        if (faderDragging) {
                    faderOverlay.onTouchEvent(e);
                    if (e.getActionMasked() == MotionEvent.ACTION_UP
                            || e.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                        faderDragging = false;
                    }
                    return true;
                }

                if (e.getActionMasked() == MotionEvent.ACTION_DOWN
                        && faderOverlay.onTouchEvent(e)) {
                    faderDragging = true;
                    return true;
                }

                if (e.getAction() != MotionEvent.ACTION_UP) {
                    return true;
                }



                android.util.Log.i("SWAGGA_TOUCH",
                        String.format(java.util.Locale.US,
                                "x=%.3f y=%.3f", x, y));


                if (y >= 0.15f && y <= 0.24f) {
                    if (x >= 0.87f && x <= 0.97f) {
                        actions.openSetup();
                        return true;
                    }
                    if (x >= 0.20f && x <= 0.29f) {
                        actions.openEdit();
                        return true;
                    }
                }

                if (y >= 0.57f && y <= 0.71f) {
                    if (x >= 0.015f && x <= 0.12f) {
                        actions.channel1();
                        return true;
                    }
                    if (x >= 0.18f && x <= 0.29f) {
                        actions.channel2();
                        return true;
                    }
                    if (x >= 0.35f && x <= 0.46f) {
                        actions.channel3();
                        return true;
                    }
                    if (x >= 0.52f && x <= 0.63f) {
                        actions.channel4();
                        return true;
                    }
                }

                // BPM PRO: frecce verticali.
                // La grafica originale resta invariata.
                if (x >= 0.78f && x <= 0.84f
                        && y >= 0.010f && y <= 0.115f) {

                    if (y < 0.060f) {
                        bpmControl.setBpm(bpmControl.getBpm() + 1);
                        bpmDisplay.setBpm(bpmControl.getBpm());
                        actions.playbackBpm(bpmControl.getBpm());
                    } else {
                        bpmControl.setBpm(bpmControl.getBpm() - 1);
                        bpmDisplay.setBpm(bpmControl.getBpm());
                        actions.playbackBpm(bpmControl.getBpm());
                    }

                    visibleBpm[0] = bpmControl.getBpm();
                    bpmNumberOverlay.invalidate();

                    android.util.Log.i(
                            "SWAGGA_BPM",
                            "Valore BPM: " + bpmControl.getBpm()
                    );

                    android.widget.Toast.makeText(
                            getContext(),
                            "BPM: " + bpmControl.getBpm(),
                            android.widget.Toast.LENGTH_SHORT
                    ).show();

                    return true;
                }

                // Pulsanti FX, EQ e LOOP della console neon.
                if (x >= 0.84f && x <= 0.99f) {
                    if (y >= 0.595f && y <= 0.665f) {
                        actions.openFx();
                        return true;
                    }
                    if (y >= 0.685f && y <= 0.755f) {
                        actions.openEq();
                        return true;
                    }
                    if (y >= 0.775f && y <= 0.845f) {
                        actions.openLoop();
                        return true;
                    }
                }

                if (x >= 0.84f && x <= 0.94f
                        && y >= 0.86f && y <= 0.96f) {
                    actions.exportMp3();
                    return true;
                }

                if (y > 0.10f && y < 0.26f) {
                    if (x < 0.13f) {
                        actions.file();
                    } else if (x > 0.30f && x < 0.42f) {
                        actions.stop();
                    } else if (x >= 0.46f && x < 0.54f) {
                        actions.play();
                    } else if (x >= 0.57f && x < 0.65f) {
                        actions.pause();
                    } else if (x >= 0.69f && x < 0.84f) {
                        actions.remix();
                    }
                }

                return true;
            }
        }, new LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT
        ));


        LayoutParams bpmParams = new LayoutParams(1, 1);

        addView(bpmControl, bpmParams);

        // Il PNG originale rimane visibile.
        // Il controllo BPM riceve i tocchi senza disegnare
        // un secondo pulsante sopra la console.
        bpmControl.setVisibility(View.GONE);
        bpmControl.setBackgroundColor(
                android.graphics.Color.TRANSPARENT);
        // Invisibile graficamente, ma con area touch attiva.
        bpmControl.setAlpha(0.01f);
        bpmControl.bringToFront();

        bpmControl.post(() -> {
            int width = getWidth();
            int height = getHeight();

            LayoutParams params = new LayoutParams(
                    Math.round(width * 270f / 1672f),
                    Math.round(height * 100f / 941f)
            );

            params.leftMargin =
                    Math.round(width * 1080f / 1672f);
            params.topMargin =
                    Math.round(height * 12f / 941f);

            bpmControl.setLayoutParams(params);
        });

    }
}
