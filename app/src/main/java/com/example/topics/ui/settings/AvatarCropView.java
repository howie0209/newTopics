package com.example.topics.ui.settings;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

public class AvatarCropView extends View {
    private Bitmap bitmap;
    private float zoom = 1f;
    private final PointF offset = new PointF(0f, 0f);
    private float cropSize;
    private float lastX, lastY;
    private final Paint borderPaint = new Paint();

    public AvatarCropView(Context context) {
        super(context);
        borderPaint.setColor(Color.parseColor("#66FFFFFF"));
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(3f);
    }

    public void setBitmap(Bitmap bitmap) {
        this.bitmap = bitmap;
        offset.set(0f, 0f);
        invalidate();
    }

    public void setZoom(float zoom) {
        this.zoom = Math.max(1f, Math.min(3f, zoom));
        clampOffset();
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        cropSize = Math.min(w, h);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (bitmap == null || cropSize <= 0) return;

        float baseScale = Math.min(cropSize / bitmap.getWidth(), cropSize / bitmap.getHeight());
        float displayWidth = bitmap.getWidth() * baseScale * zoom;
        float displayHeight = bitmap.getHeight() * baseScale * zoom;
        float left = cropSize / 2f + offset.x - displayWidth / 2f;
        float top = cropSize / 2f + offset.y - displayHeight / 2f;

        RectF dest = new RectF(left, top, left + displayWidth, top + displayHeight);
        canvas.drawBitmap(bitmap, null, dest, null);
        canvas.drawRect(0, 0, cropSize, cropSize, borderPaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                lastX = event.getX();
                lastY = event.getY();
                return true;
            case MotionEvent.ACTION_MOVE:
                float dx = event.getX() - lastX;
                float dy = event.getY() - lastY;
                offset.x += dx;
                offset.y += dy;
                clampOffset();
                lastX = event.getX();
                lastY = event.getY();
                invalidate();
                return true;
        }
        return true;
    }

    private void clampOffset() {
        if (cropSize <= 0) return;
        offset.x = Math.max(-cropSize, Math.min(cropSize, offset.x));
        offset.y = Math.max(-cropSize, Math.min(cropSize, offset.y));
    }

    public Bitmap getCroppedBitmap(int outputSize) {
        Bitmap output = Bitmap.createBitmap(outputSize, outputSize, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(output);
        canvas.drawColor(Color.WHITE);

        if (bitmap == null || cropSize <= 0) return output;

        float baseScale = Math.min(cropSize / bitmap.getWidth(), cropSize / bitmap.getHeight());
        float displayWidth = bitmap.getWidth() * baseScale * zoom;
        float displayHeight = bitmap.getHeight() * baseScale * zoom;
        float ratio = outputSize / cropSize;

        float drawX = (cropSize / 2f + offset.x - displayWidth / 2f) * ratio;
        float drawY = (cropSize / 2f + offset.y - displayHeight / 2f) * ratio;
        float drawWidth = displayWidth * ratio;
        float drawHeight = displayHeight * ratio;

        RectF dest = new RectF(drawX, drawY, drawX + drawWidth, drawY + drawHeight);
        canvas.drawBitmap(bitmap, null, dest, null);
        return output;
    }
}