package com.digitalfitnessmp3.app.daw;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;

public final class WaveformView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float[] peaks = new float[0];
    private float playbackProgress = 0f;

    public WaveformView(Context context) {
        super(context);
        setMinimumHeight(120);
    }

    public void setPeaks(float[] values) {
        peaks = values == null ? new float[0] : values.clone();
        invalidate();
    }

    public void setPlaybackProgress(float progress) {
        playbackProgress = Math.max(0f, Math.min(1f, progress));
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float width = getWidth();
        float height = getHeight();
        float middle = height / 2f;

        canvas.drawColor(Color.rgb(20, 35, 40));

        paint.setColor(Color.rgb(57, 255, 20));
        paint.setStrokeWidth(2f);

        if (peaks.length == 0) return;

        float step = width / peaks.length;

        for (int i = 0; i < peaks.length; i++) {
            float amplitude = Math.min(1f,
                    Math.max(0f, Math.abs(peaks[i])));
            float x = (i + 0.5f) * step;
            float extent = amplitude * middle * 0.9f;

            paint.setColor(
                    x <= playbackProgress * width
                            ? Color.CYAN
                            : Color.rgb(57, 255, 20));

            canvas.drawLine(x, middle - extent,
                    x, middle + extent, paint);
        }

        // Cursore neon della riproduzione
        float cursorX = playbackProgress * width;
        paint.setColor(Color.WHITE);
        paint.setStrokeWidth(2f);
        canvas.drawLine(cursorX, 0, cursorX, height, paint);
    }
}
