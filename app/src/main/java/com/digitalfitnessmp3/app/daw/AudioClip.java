package com.digitalfitnessmp3.app.daw;

import android.net.Uri;

public final class AudioClip {
    public final Uri source;
    public final int trackIndex;
    public final long startMs;
    public final long durationMs;

    public AudioClip(Uri source, int trackIndex,
                     long startMs, long durationMs) {
        if (source == null || trackIndex < 0 ||
                startMs < 0 || durationMs <= 0) {
            throw new IllegalArgumentException(
                    "Parametri clip non validi");
        }

        this.source = source;
        this.trackIndex = trackIndex;
        this.startMs = startMs;
        this.durationMs = durationMs;
    }

    public long endMs() {
        return startMs + durationMs;
    }
}
