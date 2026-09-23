package com.translator.screen;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

public class SelectionOverlayView extends View {

    public interface OnSelectionListener {
        void onSelected(
                int left,
                int top,
                int right,
                int bottom
        );
    }

    private final Paint paint =
            new Paint(Paint.ANTI_ALIAS_FLAG);

    private final Paint borderPaint =
            new Paint(Paint.ANTI_ALIAS_FLAG);

    private final OnSelectionListener listener;

    private float startX;
    private float startY;
    private float endX;
    private float endY;

    private boolean selecting = false;

    public SelectionOverlayView(
            Context context,
            OnSelectionListener listener) {

        super(context);

        this.listener = listener;

        paint.setColor(
                Color.argb(
                        90,
                        0,
                        0,
                        0
                )
        );

        borderPaint.setColor(
                Color.rgb(
                        37,
                        99,
                        235
                )
        );

        borderPaint.setStyle(
                Paint.Style.STROKE
        );

        borderPaint.setStrokeWidth(5);
    }

    @Override
    protected void onDraw(Canvas canvas) {

        super.onDraw(canvas);

        canvas.drawColor(
                Color.argb(
                        90,
                        0,
                        0,
                        0
                )
        );

        if (!selecting) {
            return;
        }

        float left =
                Math.min(
                        startX,
                        endX
                );

        float top =
                Math.min(
                        startY,
                        endY
                );

        float right =
                Math.max(
                        startX,
                        endX
                );

        float bottom =
                Math.max(
                        startY,
                        endY
                );

        RectF rect =
                new RectF(
                        left,
                        top,
                        right,
                        bottom
                );

        Paint clearPaint =
                new Paint();

        clearPaint.setColor(
                Color.TRANSPARENT
        );

        clearPaint.setXfermode(
                new android.graphics.PorterDuffXfermode(
                        android.graphics.PorterDuff.Mode.CLEAR
                )
        );

        canvas.drawRect(
                rect,
                clearPaint
        );

        canvas.drawRect(
                rect,
                borderPaint
        );
    }

    @Override
    public boolean onTouchEvent(
            MotionEvent event) {

        switch (event.getAction()) {

            case MotionEvent.ACTION_DOWN:

                startX = event.getX();
                startY = event.getY();

                endX = startX;
                endY = startY;

                selecting = true;

                invalidate();

                return true;

            case MotionEvent.ACTION_MOVE:

                endX = event.getX();
                endY = event.getY();

                invalidate();

                return true;

            case MotionEvent.ACTION_UP:

                endX = event.getX();
                endY = event.getY();

                selecting = false;

                int left =
                        (int) Math.min(
                                startX,
                                endX
                        );

                int top =
                        (int) Math.min(
                                startY,
                                endY
                        );

                int right =
                        (int) Math.max(
                                startX,
                                endX
                        );

                int bottom =
                        (int) Math.max(
                                startY,
                                endY
                        );

                if (right - left > 20 &&
                        bottom - top > 20) {

                    listener.onSelected(
                            left,
                            top,
                            right,
                            bottom
                    );
                }

                invalidate();

                return true;
        }

        return true;
    }
        }
