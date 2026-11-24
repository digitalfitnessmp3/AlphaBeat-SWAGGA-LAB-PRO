package com.alphabeat.daw.ui;

/** Nominal zero-origin pulse grid for the rendered V6, not a phrase detector. */
public final class BeatGrid32 {
    private BeatGrid32() {}
    /** 0=unreached, 1=reached, 2=eighth count reached for this group. */
    public static int markerState(int count, int cell) {
        if (count < 1 || count > 32 || cell < 1 || cell > 32 || cell > count) return 0;
        return count >= ((cell - 1) / 8 + 1) * 8 ? 2 : 1;
    }
    public static int count(long positionMs, int bpm) {
        if (bpm <= 0 || positionMs < 0) return 0;
        return (int)((positionMs * bpm / 60000L) % 32L) + 1;
    }
}
