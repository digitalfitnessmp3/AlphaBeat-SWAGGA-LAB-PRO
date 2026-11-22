package com.alphabeat.daw.audio.fx;

public final class SwaggaEcho {

    private final float[] delay;
    private int position = 0;
    private float wet = 0.30f;
    private float feedback = 0.35f;

    public SwaggaEcho(int sampleRate, int delayMs) {
        if (sampleRate <= 0 || delayMs <= 0)
            throw new IllegalArgumentException("Parametri non validi");

        int frames = Math.max(
                1, (int)((long) sampleRate * delayMs / 1000L));
        delay = new float[frames * 2];
    }

    public void process(float[] stereo, int frames) {
        if (stereo == null || frames < 0 ||
                frames > stereo.length / 2)
            throw new IllegalArgumentException("Buffer non valido");

        for (int i = 0; i < frames; i++) {
            int input = i * 2;
            int echo = position * 2;

            for (int ch = 0; ch < 2; ch++) {
                float dry = stereo[input + ch];
                float delayed = delay[echo + ch];

                delay[echo + ch] =
                        dry + delayed * feedback;

                stereo[input + ch] =
                        Math.max(-1f, Math.min(1f,
                                dry + delayed * wet));
            }

            position = (position + 1) % (delay.length / 2);
        }
    }

    public void reset() {
        java.util.Arrays.fill(delay, 0f);
        position = 0;
    }
}
