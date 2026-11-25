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
        void openCast();
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
    private View selectorOverlay;
    private int selectedChannel = 0;
    private final boolean[] mutedChannels = new boolean[4];
    private int transportState = 0; // 0 STOP, 1 PLAY, 2 PAUSE
    private String waveformStatus;
    private float touchX = -1f, touchY = -1f;

    public void setConsoleState(int channel, boolean[] muted, int transport) {
        selectedChannel = channel;
        System.arraycopy(muted, 0, mutedChannels, 0, 4);
        transportState = transport;
        if (selectorOverlay != null) selectorOverlay.invalidate();
    }

    public void setWaveformStatus(String status) {
        waveformStatus = status;
        if (waveformOverlay != null) waveformOverlay.invalidate();
    }

    @Override public boolean dispatchTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN && getWidth() > 0 && getHeight() > 0) {
            touchX = event.getX() / getWidth();
            touchY = event.getY() / getHeight();
        } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            touchX = touchY = -1f;
        }
        if (selectorOverlay != null) selectorOverlay.invalidate();
        return super.dispatchTouchEvent(event);
    }
    private boolean faderDragging = false;
    private boolean waveformDragging = false;
    private boolean draggingWindow;
    private double dragStart;
    private double windowSpan() { return timelineBpm > 0 ? 1920000.0 / timelineBpm : 0; }
    private double windowStart() { return timelineBpm > 0 ? (displayedPositionMs * (long) timelineBpm / 60000L / 32L) * windowSpan() : 0; }
    private float seekProgress(float x) {
        float f = Math.max(0f, Math.min(1f,(x-WAVE_LEFT)/(WAVE_RIGHT-WAVE_LEFT)));
        return draggingWindow && displayedDurationMs > 0 ? (float)Math.max(0,Math.min(1,(dragStart+f*windowSpan())/displayedDurationMs)) : f;
    }
    private String displayedTrackTitle = "Nessun brano caricato";
    private int displayedDurationMs = 0;
    private int displayedPositionMs = 0;
    private View trackInfoOverlay;
    private String remixPhase;
    public void setRemixPhase(String phase) {
        if (java.util.Objects.equals(remixPhase, phase)) return;
        remixPhase = phase;
        if (selectorOverlay != null) selectorOverlay.invalidate();
        if (timelineOverlay != null) timelineOverlay.invalidate();
    }
    private View timelineOverlay;
    private int cutoutSafeLeft;
    @Override public WindowInsets onApplyWindowInsets(WindowInsets insets) {
        int left = 0;
        if (android.os.Build.VERSION.SDK_INT >= 28 && insets.getDisplayCutout() != null) {
            left = insets.getDisplayCutout().getSafeInsetLeft();
        }
        cutoutSafeLeft = left;
        if (timelineOverlay != null) timelineOverlay.invalidate();
        return super.onApplyWindowInsets(insets);
    }
    private int timelineBpm = 0;
    private int timelineCount = 0;

    public void setTimelineBpm(int bpm) {
        timelineBpm = bpm;
        timelineCount = BeatGrid32.count(0, bpm);
        if (timelineOverlay != null) timelineOverlay.invalidate();
        if (waveformOverlay != null) waveformOverlay.invalidate();
    }

    private void updateTimelineCount() {
        int next = BeatGrid32.count(displayedPositionMs, timelineBpm);
        if (next != timelineCount) {
            timelineCount = next;
            if (timelineOverlay != null) timelineOverlay.invalidate();
        }
    }
    private static final float WAVE_LEFT = .025f, WAVE_RIGHT = .975f;
    private static final float WAVE_TOP = .326f, WAVE_BOTTOM = .482f;
    private static final int WAVE_CYAN = Color.rgb(0, 234, 255);
    private static final int WAVE_GREEN = Color.rgb(57, 255, 20);

    // La grafica PNG contiene un testo segnaposto: sovrascriviamo soltanto
    // il riquadro del titolo e del timer, senza alterare gli altri controlli.
    public void setTrackInfo(String title, int durationMs) {
        displayedTrackTitle = title == null ? "Brano audio" : title.trim();
        if (durationMs > 0) displayedDurationMs = durationMs;
        displayedPositionMs = Math.min(displayedPositionMs, displayedDurationMs);
        updateTimelineCount();
        if (trackInfoOverlay != null) trackInfoOverlay.invalidate();
        if (waveformOverlay != null) waveformOverlay.invalidate();
    }

    public void setTrackPosition(int positionMs, int durationMs) {
        displayedDurationMs = Math.max(0, durationMs);
        displayedPositionMs = Math.max(0, Math.min(positionMs, displayedDurationMs));
        updateTimelineCount();
        if (waveformOverlay != null) waveformOverlay.invalidate();
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
        if (trackInfoOverlay != null) trackInfoOverlay.invalidate();
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

        // QA: ricostruisce tutta la fascia centrale, senza lasciare
        // visibili i bordi del PNG. Un solo perimetro, tre zone non sovrapposte.
        final View waveformFrameAndTimeline = new View(context) {
            private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            @Override protected void onDraw(Canvas canvas) {
                float w = getWidth(), h = getHeight();
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(Color.rgb(4, 8, 11));
                canvas.drawRect(0, h * .263f, w, h * .578f, paint);
                RectF panel = new RectF(w * .010f, h * .268f, w * .990f, h * .570f);
                paint.setColor(Color.rgb(3, 14, 17));
                canvas.drawRoundRect(panel, h * .021f, h * .021f, paint);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(Math.max(1.5f, h * .0025f));
                paint.setColor(WAVE_CYAN);
                canvas.drawRoundRect(panel, h * .021f, h * .021f, paint);
                paint.setStyle(Paint.Style.FILL);

                paint.setTextAlign(Paint.Align.LEFT);
                paint.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
                paint.setTextSize(h * .023f);
                paint.setColor(Color.WHITE);
                String label = remixPhase == null ? "MULTITRACK TIMELINE" : remixPhase;
                // Above count 2, or farther right when the camera cutout requires it.
                float labelX = Math.max(w * .055f, cutoutSafeLeft + w * .010f);
                canvas.drawText(label, labelX, h * .507f, paint);
                float labelEnd = labelX + paint.measureText(label) + w * .016f;
                paint.setColor(Color.rgb(139, 255, 30));
                if (remixPhase == null) canvas.drawText(timelineBpm > 0 ? "•  SMART WAVE 32" : "•  32 COUNTS", labelEnd, h * .507f, paint);
                else canvas.drawText("•  IN CORSO", labelEnd, h * .507f, paint);

                final float left = w * WAVE_LEFT, right = w * WAVE_RIGHT;
                final float groupGap = timelineBpm > 0 ? 0f : w * .006f;
                final float cellWidth = (right - left - 3f * groupGap) / 32f;
                for (int i = 0; i < 32; i++) {
                    float x = left + i * cellWidth + (i / 8) * groupGap;
                    boolean current = timelineCount == i + 1;
                    paint.setColor(current ? Color.rgb(10, 65, 70) : Color.rgb(9, 31, 35));
                    canvas.drawRect(x + 1f, h * .518f, x + cellWidth - 1f, h * .558f, paint);
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setStrokeWidth(Math.max(1f, h * .001f));
                    paint.setColor(current ? WAVE_CYAN : Color.rgb(0, 110, 120));
                    canvas.drawRect(x + 1f, h * .518f, x + cellWidth - 1f, h * .558f, paint);
                    paint.setStyle(Paint.Style.FILL);
                    int marker = BeatGrid32.markerState(timelineCount, i + 1);
                    // Live mode lights each reached count, and brightens the entire
                    // group on its eighth count. No decorative bars before V6.
                    if (timelineBpm > 0) {
                        paint.setColor(marker == 2 ? WAVE_GREEN
                                : marker == 1 ? Color.rgb(0, 180, 38)
                                : Color.rgb(20, 49, 52));
                        canvas.drawRect(x + 2f, h * .543f, x + cellWidth - 2f, h * .556f, paint);
                    }
                    paint.setTextAlign(Paint.Align.CENTER);
                    paint.setTextSize(h * .019f);
                    paint.setColor(current ? WAVE_CYAN : Color.WHITE);
                    canvas.drawText(String.valueOf(i + 1), x + cellWidth / 2f, h * .539f, paint);
                }
            }
        };
        timelineOverlay = waveformFrameAndTimeline;
        waveformFrameAndTimeline.setClickable(false);
        addView(waveformFrameAndTimeline, new LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        trackInfoOverlay = new View(context) {
            private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            @Override protected void onDraw(Canvas canvas) {
                float w = getWidth(), h = getHeight();
                paint.setStyle(Paint.Style.FILL);
                paint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
                paint.setTextAlign(Paint.Align.LEFT);
                paint.setColor(WAVE_CYAN);
                paint.setTextSize(h * .045f);
                canvas.drawText("♫", w * .023f, h * .307f, paint);
                paint.setColor(Color.WHITE);
                paint.setTextSize(h * .023f);
                String shown = displayedTrackTitle;
                float available = w * .78f;
                if (paint.measureText(shown) > available) {
                    while (!shown.isEmpty() && paint.measureText(shown + "…") > available)
                        shown = shown.substring(0, shown.length() - 1);
                    shown += "…";
                }
                canvas.drawText(shown, w * .073f, h * .298f, paint);
                paint.setTextAlign(Paint.Align.RIGHT);
                paint.setTextSize(h * .020f);
                canvas.drawText(formatTrackTime(displayedPositionMs) + " / "
                        + formatTrackTime(displayedDurationMs), w * .975f, h * .298f, paint);

                float left = w * WAVE_LEFT, right = w * WAVE_RIGHT;
                float y = h * .315f;
                paint.setStrokeCap(Paint.Cap.ROUND);
                paint.setStrokeWidth(Math.max(2f, h * .006f));
                paint.setColor(Color.rgb(27, 62, 67));
                canvas.drawLine(left, y, right, y, paint);
                float progressX = left + (right - left) * waveformProgress;
                paint.setColor(WAVE_GREEN);
                if (waveformProgress > 0f) canvas.drawLine(left, y, progressX, y, paint);
                paint.setColor(WAVE_CYAN);
                canvas.drawCircle(progressX, y, h * .005f, paint);
                paint.setStrokeCap(Paint.Cap.BUTT);
            }
        };
        trackInfoOverlay.setClickable(false);
        addView(trackInfoOverlay, new LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        waveformOverlay = new View(context) {
            private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            @Override protected void onDraw(Canvas canvas) {
                float w = getWidth(), h = getHeight();
                float left = w * WAVE_LEFT, right = w * WAVE_RIGHT;
                float top = h * WAVE_TOP, bottom = h * WAVE_BOTTOM;
                float width = right - left, height = bottom - top;
                float middle = (top + bottom) / 2f;
                canvas.save();
                canvas.clipRect(left, top, right, bottom);
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(Color.rgb(3, 14, 17));
                canvas.drawRect(left, top, right, bottom, paint);
                boolean live = timelineBpm > 0;
                double startMs = live ? windowStart() : 0;
                double spanMs = live ? windowSpan() : displayedDurationMs;
                float local = live ? (float)Math.max(0,Math.min(1,(displayedPositionMs-startMs)/spanMs)) : waveformProgress;
                if (live) for(int beat=0;beat<=32;beat++) {
                    float x = left + width * beat / 32f;
                    paint.setColor(beat%8==0 ? Color.rgb(23,125,129) : Color.rgb(20,49,56));
                    paint.setStrokeWidth(beat%8==0 ? 2f : 1f);
                    canvas.drawLine(x,top,x,bottom,paint);
                }
                if (waveformPeaks.length == 0 && waveformStatus != null) {
                    paint.setColor(WAVE_CYAN);
                    paint.setTextSize(h * .024f);
                    paint.setTextAlign(Paint.Align.CENTER);
                    canvas.drawText(waveformStatus, (left + right) / 2f, middle, paint);
                }
                if (displayedDurationMs > 0 && spanMs > 0 && waveformPeaks.length > 0) {
                    int first = Math.max(0,(int)Math.floor(startMs*waveformPeaks.length/displayedDurationMs));
                    int end = Math.min(waveformPeaks.length,(int)Math.ceil((startMs+spanMs)*waveformPeaks.length/displayedDurationMs));
                    int stride = Math.max(1,(int)Math.ceil((end-first)/Math.max(1.0,width/2)));
                    paint.setStrokeWidth(2f);
                    for(int i=first;i<end;i+=stride) {
                        float amplitude=0;
                        int jEnd=Math.min(end,i+stride);
                        for(int j=i;j<jEnd;j++) amplitude=Math.max(amplitude,waveformPeaks[j]);
                        double time=(i+(jEnd-i)/2.0)*displayedDurationMs/waveformPeaks.length;
                        float fraction=(float)((time-startMs)/spanMs);
                        if(fraction<0 || fraction>1) continue;
                        float x=left+fraction*width;
                        float extent=Math.min(1f,Math.max(0f,amplitude))*height/2f*.88f;
                        paint.setColor(fraction<=local ? WAVE_GREEN : WAVE_CYAN);
                        canvas.drawLine(x,middle-extent,x,middle+extent,paint);
                    }
                }
                paint.setColor(Color.WHITE);
                paint.setStrokeWidth(2f);
                float cursor=left+local*width;
                canvas.drawLine(cursor,top,cursor,bottom,paint);
                canvas.restore();
            }
        };
        waveformOverlay.setClickable(false);
        addView(waveformOverlay, new LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

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


        // State-backed neon overlay. Geometry matches the existing touch zones.
        selectorOverlay = new View(context) {
            private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            private void glow(Canvas c, float x1, float y1, float x2, float y2, boolean active, int color) {
                boolean pressed = touchX >= x1 && touchX <= x2 && touchY >= y1 && touchY <= y2;
                if (!active && !pressed) return;
                RectF rect = new RectF(getWidth()*x1, getHeight()*y1, getWidth()*x2, getHeight()*y2);
                float radius = getHeight() * .012f;
                p.setStyle(Paint.Style.FILL);
                p.setColor(color);
                p.setAlpha(pressed ? 65 : 35);
                c.drawRoundRect(rect, radius, radius, p);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(Math.max(2f, getHeight()*.008f));
                p.setAlpha(70);
                c.drawRoundRect(rect, radius, radius, p);
                p.setStrokeWidth(Math.max(1.5f, getHeight()*.003f));
                p.setAlpha(255);
                c.drawRoundRect(rect, radius, radius, p);
                p.setStyle(Paint.Style.FILL);
            }
            @Override protected void onDraw(Canvas c) {
                glow(c,.015f,.15f,.13f,.24f,false,WAVE_CYAN);
                glow(c,.20f,.15f,.29f,.24f,false,WAVE_CYAN);
                glow(c,.30f,.15f,.42f,.24f,transportState == 0 && selectedChannel > 0,WAVE_CYAN);
                glow(c,.46f,.15f,.54f,.24f,transportState == 1,WAVE_GREEN);
                glow(c,.57f,.15f,.65f,.24f,transportState == 2,WAVE_CYAN);
                glow(c,.69f,.15f,.84f,.24f,remixPhase != null,WAVE_GREEN);
                glow(c,.87f,.15f,.97f,.24f,false,WAVE_CYAN);
                float[] chLeft = {.015f,.18f,.35f,.52f};
                float[] chRight = {.12f,.29f,.46f,.63f};
                float[] muteX = {.066f,.234f,.402f,.568f};
                float[] soloX = {.118f,.286f,.454f,.620f};
                for (int i=0;i<4;i++) {
                    glow(c,chLeft[i],.57f,chRight[i],.71f,selectedChannel == i+1,WAVE_CYAN);
                    glow(c,muteX[i]-.022f,.905f,muteX[i]+.022f,.965f,mutedChannels[i],Color.rgb(255,100,60));
                    // S currently selects a channel; do not fake a separate solo state.
                    glow(c,soloX[i]-.022f,.905f,soloX[i]+.022f,.965f,false,WAVE_GREEN);
                }
                glow(c,.84f,.595f,.99f,.665f,false,WAVE_CYAN);
                glow(c,.84f,.685f,.99f,.755f,false,WAVE_CYAN);
                glow(c,.84f,.775f,.99f,.845f,false,WAVE_CYAN);
                glow(c,.84f,.86f,.94f,.96f,false,WAVE_CYAN);
            }
        };
        selectorOverlay.setClickable(false);
        addView(selectorOverlay, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        addView(new View(context) {
            @Override
            public boolean onTouchEvent(android.view.MotionEvent e) {
                float x = e.getX() / getWidth();
                float y = e.getY() / getHeight();

                // Trascinamento waveform: area centrale.
                if (waveformDragging) {
                    if (e.getActionMasked() == MotionEvent.ACTION_MOVE
                            || e.getActionMasked() == MotionEvent.ACTION_UP) {
                        float progress = seekProgress(x);
                        actions.seek(progress);
                    }
                    if (e.getActionMasked() == MotionEvent.ACTION_UP
                            || e.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                        waveformDragging = false;
                    }
                    return true;
                }

                if (e.getActionMasked() == MotionEvent.ACTION_DOWN
                        && y >= .308f && y <= WAVE_BOTTOM
                        && x >= WAVE_LEFT && x <= WAVE_RIGHT) {
                    waveformDragging = true;
                    draggingWindow = timelineBpm > 0 && y >= WAVE_TOP;
                    dragStart = windowStart();
                    float progress = seekProgress(x);
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

        // Real clickable control, in the header gap between DAW PRO and BPM.
        final View cast = new View(context) {
            private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            @Override protected void onDraw(Canvas canvas) {
                float cw = getWidth(), ch = getHeight();
                float unit = Math.min(cw, ch) / 24f;
                canvas.save();
                canvas.translate(cw / 2f, ch / 2f);
                canvas.scale(unit, unit);
                p.setColor(Color.rgb(3, 14, 17));
                p.setStyle(Paint.Style.FILL);
                canvas.drawRoundRect(new RectF(-11,-11,11,11),3,3,p);
                p.setColor(WAVE_CYAN);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(1.5f);
                p.setStrokeCap(Paint.Cap.ROUND);
                p.setStrokeJoin(Paint.Join.ROUND);
                Path screen = new Path();
                screen.moveTo(-8,-2); screen.lineTo(-8,-7); screen.lineTo(8,-7);
                screen.lineTo(8,5); screen.lineTo(1,5);
                canvas.drawPath(screen,p);
                canvas.drawArc(new RectF(-15,-2,-1,12),270,90,false,p);
                canvas.drawArc(new RectF(-12,1,-4,9),270,90,false,p);
                p.setStyle(Paint.Style.FILL);
                canvas.drawCircle(-8,5,1.2f,p);
                canvas.restore();
            }
            @Override public CharSequence getAccessibilityClassName() {
                return android.widget.Button.class.getName();
            }
        };
        cast.setContentDescription("SMART CAST - Trasmetti schermo");
        cast.setTooltipText("SMART CAST");
        cast.setFocusable(true);
        cast.setOnClickListener(view -> actions.openCast());
        addView(cast, new LayoutParams(1,1));
        addOnLayoutChangeListener((view,l,t,r,b,ol,ot,orr,ob) -> {
            if (r-l == orr-ol && b-t == ob-ot) return;
            int width = r-l, height = b-t;
            LayoutParams params = new LayoutParams(Math.round(width * .049f),
                    Math.round(height * .092f));
            params.leftMargin = Math.round(width * .612f);
            params.topMargin = Math.round(height * .005f);
            cast.setLayoutParams(params);
        });
        requestApplyInsets();
    }
}
