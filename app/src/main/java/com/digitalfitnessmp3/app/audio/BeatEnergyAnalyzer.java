package com.digitalfitnessmp3.app.audio;

public final class BeatEnergyAnalyzer {

    private BeatEnergyAnalyzer() {}

    public static float[] analyze(PcmAudio audio, int windowMs) {
        if (audio == null || windowMs <= 0) {
            throw new IllegalArgumentException("Parametri non validi");
        }

        int windowSize = Math.max(
                1, (int) ((long) audio.sampleRate * windowMs / 1000));

        int count = (audio.samples.length + windowSize - 1)
                / windowSize;

        float[] energy = new float[count];

        for (int i = 0; i < count; i++) {
            int start = i * windowSize;
            int end = Math.min(start + windowSize,
                    audio.samples.length);

            double sum = 0;

            for (int j = start; j < end; j++) {
                double sample = audio.samples[j];
                sum += sample * sample;
            }

            energy[i] = (float) Math.sqrt(
                    sum / Math.max(1, end - start));
        }

        return energy;
    }
}
