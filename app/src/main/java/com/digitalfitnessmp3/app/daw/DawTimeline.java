package com.digitalfitnessmp3.app.daw;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.widget.LinearLayout;
import android.widget.TextView;

public final class DawTimeline extends LinearLayout {

    private final WaveformView waveform;

    public DawTimeline(Context context) {
        super(context);
        setOrientation(VERTICAL);
        setPadding(16, 16, 16, 16);
        setBackgroundColor(Color.rgb(20, 24, 30));

        addTrack("01  AUDIO", 0xFF22D3EE);
        waveform = new WaveformView(context);
        LayoutParams waveParams = new LayoutParams(
                LayoutParams.MATCH_PARENT, 120);
        waveParams.bottomMargin = 12;
        addView(waveform, waveParams);
        addTrack("02  KICK", 0xFF84CC16);
        addTrack("03  BASS", 0xFFF59E0B);
        addTrack("04  PERCUSSION", 0xFFD946EF);
    }

    public void setPlaybackProgress(float progress) {
        waveform.setPlaybackProgress(progress);
    }

    public void showWaveform(float[] peaks) {
        waveform.setPeaks(peaks);
    }

    public void showAudioClip(AudioClip clip) {
        if (clip == null || getChildCount() == 0) return;

        TextView track = (TextView) getChildAt(0);
        long seconds = clip.durationMs / 1000;
        long minutes = seconds / 60;

        track.setText("01  AUDIO  |  MP3  |  "
                + minutes + ":" + String.format(
                java.util.Locale.US, "%02d", seconds % 60));
        track.setBackgroundColor(Color.rgb(30, 65, 75));
    }

    private void addTrack(String name, int color) {
        TextView track = new TextView(getContext());
        track.setText(name + "     ▰ ▰ ▰ ▰ ▰ ▰ ▰ ▰");
        track.setTextColor(color);
        track.setTextSize(15);
        track.setTypeface(null, Typeface.BOLD);
        track.setPadding(12, 22, 12, 22);
        track.setClickable(true);
        track.setOnClickListener(v -> {
            for (int i = 0; i < getChildCount(); i++) {
                getChildAt(i).setBackgroundColor(Color.TRANSPARENT);
            }
            track.setBackgroundColor(Color.rgb(45, 55, 65));
        });

        LayoutParams params = new LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT);
        params.bottomMargin = 8;
        addView(track, params);
    }
}
