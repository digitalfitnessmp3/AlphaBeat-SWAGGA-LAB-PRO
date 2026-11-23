package com.alphabeat.daw.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.view.View;

public final class SwaggaBpmDisplay extends View {

    private int bpm = 128;

    private final Paint paint =
            new Paint(Paint.ANTI_ALIAS_FLAG);

    public SwaggaBpmDisplay(Context context) {
        super(context);
        setClickable(false);
        setFocusable(false);
    }

    public void setBpm(int value) {
        bpm = Math.max(60, Math.min(260, value));
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        paint.setStyle(Paint.Style.FILL);
        paint.setTypeface(Typeface.create(
                Typeface.DEFAULT,
                Typeface.BOLD
        ));

        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(getHeight() * 0.715f);
        paint.setColor(Color.WHITE);

        Paint.FontMetrics fm = paint.getFontMetrics();

        float baseline = getHeight() / 2f
                - (fm.ascent + fm.descent) / 2f;

        canvas.drawText(
                String.valueOf(bpm),
                getWidth() / 2f,
                baseline,
                paint
        );
    }
}
