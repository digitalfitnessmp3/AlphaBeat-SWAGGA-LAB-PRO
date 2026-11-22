package com.digitalfitnessmp3.app.audio;

import java.util.ArrayList;

public final class BeatDetector {

    private BeatDetector() {}

    public static double detectBpm(PcmAudio audio) {
        if (audio == null || audio.samples.length == 0) {
            return 0;
        }

        final int windowMs = 10;
        float[] energy = BeatEnergyAnalyzer.analyze(audio, windowMs);

        if (energy.length < 100) {
            return 0;
        }

        float maximum = 0;

        for (float value : energy) {
            maximum = Math.max(maximum, value);
        }

        if (maximum < 0.001f) {
            return 0;
        }

        ArrayList<Long> beats = new ArrayList<>();
        int minimumDistance = 25;
        int lastPeak = -minimumDistance;

        for (int i = 2; i < energy.length - 2; i++) {
            float localAverage = 0;
            int count = 0;

            for (int j = Math.max(0, i - 20);
                 j <= Math.min(energy.length - 1, i + 20);
                 j++) {
                localAverage += energy[j];
                count++;
            }

            localAverage /= count;

            boolean peak = energy[i] > energy[i - 1]
                    && energy[i] >= energy[i + 1]
                    && energy[i] > localAverage * 1.5f
                    && energy[i] > maximum * 0.08f;

            if (peak && i - lastPeak >= minimumDistance) {
                beats.add(i * (long) windowMs);
                lastPeak = i;
            }
        }

        if (beats.size() < 3) {
            return 0;
        }

        long[] times = new long[beats.size()];

        for (int i = 0; i < beats.size(); i++) {
            times[i] = beats.get(i);
        }

        return BpmEngine.calculateBpm(times);
    }
}
