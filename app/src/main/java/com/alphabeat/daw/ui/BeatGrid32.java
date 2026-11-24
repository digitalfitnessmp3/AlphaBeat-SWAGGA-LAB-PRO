package com.alphabeat.daw.ui;

/** Nominal zero-origin pulse grid for the rendered V6, not a phrase detector. */
public final class BeatGrid32 {
    private BeatGrid32() {}
    public static int count(long positionMs, int bpm) {
        if (bpm <= 0 || positionMs < 0) return 0;
        return (int)((positionMs * bpm / 60000L) % 32L) + 1;
    }
}
