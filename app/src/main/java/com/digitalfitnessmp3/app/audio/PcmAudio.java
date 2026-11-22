package com.digitalfitnessmp3.app.audio;

public final class PcmAudio {
    public final float[] samples;
    public final int sampleRate;

    public PcmAudio(float[] samples, int sampleRate) {
        if (samples == null || sampleRate <= 0) {
            throw new IllegalArgumentException("Audio PCM non valido");
        }

        this.samples = samples;
        this.sampleRate = sampleRate;
    }

    public double durationSeconds() {
        return samples.length / (double) sampleRate;
    }

    public int sampleCount() {
        return samples.length;
    }
}
