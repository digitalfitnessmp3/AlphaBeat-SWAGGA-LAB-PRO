package com.alphabeat.daw.ui;

import android.content.Context;
import android.graphics.*;
import android.view.MotionEvent;
import android.view.View;

public final class SwaggaBpmControl extends View {

    private int bpm = 128;

    private final Paint paint =
            new Paint(Paint.ANTI_ALIAS_FLAG);

    private final int neon = Color.rgb(0, 235, 230);
    private final int green = Color.rgb(57, 255, 20);

    public SwaggaBpmControl(Context context) {
        super(context);
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
    }

    public int getBpm() {
        return bpm;
    }

    public void setBpm(int value) {
        bpm = Math.max(60, Math.min(260, value));
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float sx = getWidth() / 270f;
        float sy = getHeight() / 100f;

        canvas.save();
        canvas.scale(sx, sy);

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.rgb(5, 17, 23));
        canvas.drawRoundRect(
                3, 7, 267, 93,
                18, 18, paint);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(3);
        paint.setColor(neon);
        paint.setShadowLayer(8, 0, 0, neon);
        canvas.drawRoundRect(
                3, 7, 267, 93,
                18, 18, paint);
        paint.clearShadowLayer();

        paint.setStyle(Paint.Style.FILL);
        paint.setTypeface(Typeface.create(
                Typeface.DEFAULT, Typeface.BOLD));
        paint.setTextAlign(Paint.Align.CENTER);

        paint.setColor(Color.WHITE);
        paint.setTextSize(46);
        canvas.drawText(
                String.valueOf(bpm),
                135, 54, paint);

        paint.setColor(green);
        paint.setTextSize(20);
        canvas.drawText("BPM", 135, 78, paint);

        drawTriangle(canvas, 39, 49, false);
        drawTriangle(canvas, 231, 49, true);

        canvas.restore();
    }

    private void drawTriangle(
            Canvas canvas, float x, float y, boolean right) {

        Path triangle = new Path();

        if (right) {
            triangle.moveTo(x - 15, y - 19);
            triangle.lineTo(x + 16, y);
            triangle.lineTo(x - 15, y + 19);
        } else {
            triangle.moveTo(x + 15, y - 19);
            triangle.lineTo(x - 16, y);
            triangle.lineTo(x + 15, y + 19);
        }

        triangle.close();

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(neon);
        paint.setShadowLayer(7, 0, 0, neon);
        canvas.drawPath(triangle, paint);
        paint.clearShadowLayer();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            android.util.Log.i(
                    "SWAGGA_TOUCH",
                    "DOWN x=" + event.getX()
                    + " y=" + event.getY()
                    + " width=" + getWidth()
                    + " height=" + getHeight()
            );
            return true;
        }

        if (event.getActionMasked() == MotionEvent.ACTION_UP) {
            float y = event.getY() / getHeight();

            if (y < 0.5f) {
                // Freccia SU
                setBpm(bpm + 1);
            } else {
                // Freccia GIU
                setBpm(bpm - 1);
            }

            android.widget.Toast.makeText(
                    getContext(),
                    "BPM: " + bpm,
                    android.widget.Toast.LENGTH_SHORT
            ).show();

            android.util.Log.i(
                    "SWAGGA_BPM",
                    "Valore BPM: " + bpm
            );

            performClick();
            return true;
        }

        return true;
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }
}
