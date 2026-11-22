package com.alphabeat.daw.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import com.alphabeat.daw.R;

import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

public final class SwaggaFaderOverlay extends View {

    private static final float DESIGN_W = 1672f;
    private static final float DESIGN_H = 941f;

    private static final float[] X = {
            122.5f, 406f, 686f, 961.5f, 1246.5f
    };

    private static final float[] TOP = {
            629f, 628f, 628f, 628f, 624f
    };

    private static final float[] BOTTOM = {
            827f, 827f, 825f, 826f, 829f
    };

    private static final float[] KNOB_W = {
            46f, 46f, 47f, 46f, 54f
    };

    private static final float[] KNOB_H = {
            50f, 45f, 46f, 44f, 45f
    };

    private final float[] volume = {
            0.75f, 0.75f, 0.75f, 0.75f, 1.00f
    };

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private int activeFader = -1;

    private final Bitmap[] knobImages = new Bitmap[5];

    private void loadKnobs() {
        int[] ids = {
            R.drawable.swagga_knob_ch1,
            R.drawable.swagga_knob_ch2,
            R.drawable.swagga_knob_ch3,
            R.drawable.swagga_knob_ch4,
            R.drawable.swagga_knob_master
        };
        for (int i = 0; i < ids.length; i++) {
            knobImages[i] = BitmapFactory.decodeResource(
                    getResources(), ids[i]);
        }
    }


    public interface FaderListener {
        void onFaderChanged(int channel, float value);
    }

    private FaderListener listener;

    public void setFaderListener(FaderListener listener) {
        this.listener = listener;
    }

    @Override
    public boolean onTouchEvent(android.view.MotionEvent event) {
        if (getWidth() <= 0 || getHeight() <= 0) return false;

        float x = event.getX() * DESIGN_W / getWidth();
        float y = event.getY() * DESIGN_H / getHeight();

        switch (event.getActionMasked()) {
            case android.view.MotionEvent.ACTION_DOWN:
                activeFader = -1;

                for (int i = 0; i < X.length; i++) {
                    float currentY = BOTTOM[i]
                            - volume[i] * (BOTTOM[i] - TOP[i]);

                    if (Math.abs(x - X[i]) <= 35f
                            && Math.abs(y - currentY) <= 45f) {
                        activeFader = i;
                        break;
                    }
                }

                if (activeFader < 0) return false;

                getParent().requestDisallowInterceptTouchEvent(true);
                return true;

            case android.view.MotionEvent.ACTION_MOVE:
                if (activeFader < 0) return false;

                float value = (BOTTOM[activeFader] - y)
                        / (BOTTOM[activeFader] - TOP[activeFader]);

                setVolume(activeFader, value);

                if (listener != null) {
                    listener.onFaderChanged(
                            activeFader,
                            getVolume(activeFader)
                    );
                }
                return true;

            case android.view.MotionEvent.ACTION_UP:
            case android.view.MotionEvent.ACTION_CANCEL:
                if (activeFader < 0) return false;
                activeFader = -1;
                return true;
        }

        return false;
    }

    public SwaggaFaderOverlay(Context context) {
        super(context);
        setClickable(false);
        loadKnobs();
    }

    public void setVolume(int channel, float value) {
        if (channel < 0 || channel >= volume.length) return;
        volume[channel] = Math.max(0f, Math.min(1f, value));
        invalidate();
    }

    public float getVolume(int channel) {
        if (channel < 0 || channel >= volume.length) return 0f;
        return volume[channel];
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        canvas.save();
        canvas.scale(getWidth() / DESIGN_W, getHeight() / DESIGN_H);

        for (int i = 0; i < volume.length; i++) {
            float x = X[i];
            float y = BOTTOM[i] - volume[i] * (BOTTOM[i] - TOP[i]);

            Bitmap knob = knobImages[i];
            if (knob != null) {
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(Color.WHITE);
                canvas.drawBitmap(knob,
                        x - KNOB_W[i] / 2f,
                        y - KNOB_H[i] / 2f,
                        paint);
            }
        }

        // Percentuali VOL aggiornate insieme alle levette.
        final float[] percentX = {223f, 504f, 784f, 1060f, 1296f};
        final float[] percentY = {819f, 817f, 819f, 816f, 874f};

        Paint volPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        volPaint.setStyle(Paint.Style.FILL);
        volPaint.setTextAlign(Paint.Align.CENTER);
        volPaint.setTypeface(android.graphics.Typeface.create(
                android.graphics.Typeface.DEFAULT,
                android.graphics.Typeface.BOLD
        ));
        volPaint.setTextSize(27f);

        for (int i = 0; i < volume.length; i++) {
            // Copre il vecchio valore fisso del PNG.
            volPaint.setColor(Color.rgb(5, 17, 23));
            canvas.drawRect(
                    percentX[i] - 45f,
                    percentY[i] - 29f,
                    percentX[i] + 45f,
                    percentY[i] + 8f,
                    volPaint
            );

            volPaint.setColor(Color.WHITE);
            canvas.drawText(
                    Math.round(volume[i] * 100f) + "%",
                    percentX[i],
                    percentY[i],
                    volPaint
            );
        }

        canvas.restore();
    }

    private void drawFader(Canvas canvas, int i) {
        float x = X[i];
        float y = BOTTOM[i] - volume[i] * (BOTTOM[i] - TOP[i]);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(5f);
        paint.setColor(Color.rgb(57, 255, 20));
        canvas.drawLine(x, y, x, BOTTOM[i], paint);

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.rgb(20, 45, 50));

        RectF knob = new RectF(
                x - KNOB_W[i] / 2f,
                y - KNOB_H[i] / 2f,
                x + KNOB_W[i] / 2f,
                y + KNOB_H[i] / 2f
        );

        canvas.drawRoundRect(knob, 5f, 5f, paint);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(3f);
        paint.setColor(Color.rgb(57, 255, 20));
        canvas.drawRoundRect(knob, 5f, 5f, paint);

        // Percentuale dinamica nella posizione originale.
        final float[] percentX = {
                223f, 504f, 784f, 1060f, 1296f
        };
        final float[] percentY = {
                812f, 810f, 812f, 809f, 867f
        };

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.WHITE);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(21f);
        paint.setTypeface(android.graphics.Typeface.create(
                android.graphics.Typeface.DEFAULT,
                android.graphics.Typeface.BOLD
        ));

        canvas.drawText(
                Math.round(volume[i] * 100f) + "%",
                percentX[i],
                percentY[i] + 7f,
                paint
        );

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(3f);
        paint.setColor(Color.CYAN);
        canvas.drawLine(
                x - KNOB_W[i] / 2f + 5f,
                y,
                x + KNOB_W[i] / 2f - 5f,
                y,
                paint
        );
    }
}
