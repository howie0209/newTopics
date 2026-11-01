package com.example.topics;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class TrendChartView extends View {
    private final List<InsightFormat.TrendBin> bins = new ArrayList<>();
    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pointPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint valuePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final float density;

    public TrendChartView(Context context) {
        this(context, null);
    }

    public TrendChartView(Context context, AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;

        gridPaint.setColor(0x33FFFFFF);
        gridPaint.setStyle(Paint.Style.STROKE);
        gridPaint.setStrokeWidth(density);
        gridPaint.setPathEffect(new DashPathEffect(new float[]{4 * density, 6 * density}, 0));

        linePaint.setColor(0xFF50E3C2);
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(2 * density);
        linePaint.setStrokeCap(Paint.Cap.ROUND);
        linePaint.setStrokeJoin(Paint.Join.ROUND);

        pointPaint.setColor(0xFF50E3C2);
        pointPaint.setStyle(Paint.Style.FILL);

        ringPaint.setColor(0xFF888888);
        ringPaint.setStyle(Paint.Style.STROKE);
        ringPaint.setStrokeWidth(1.5f * density);

        valuePaint.setColor(0xFFFFFFFF);
        valuePaint.setTextAlign(Paint.Align.CENTER);
        valuePaint.setTextSize(sp(11));

        labelPaint.setColor(0xFF999999);
        labelPaint.setTextAlign(Paint.Align.CENTER);
        labelPaint.setTextSize(sp(10));
    }

    private float sp(float value) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value, getResources().getDisplayMetrics());
    }

    public void setBins(List<InsightFormat.TrendBin> items) {
        bins.clear();
        if (items != null) bins.addAll(items);
        invalidate();
    }

    private float yFor(double value, float top, float plotHeight) {
        double clamped = Math.max(1, Math.min(5, value));
        return top + (float) ((5 - clamped) / 4.0) * plotHeight;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (bins.isEmpty()) return;

        float width = getWidth(), height = getHeight();
        float top = 30 * density, bottom = 24 * density;
        float plotHeight = height - top - bottom;

        for (int level : new int[]{5, 3, 1}) {
            float y = yFor(level, top, plotHeight);
            path.reset();
            path.moveTo(0, y);
            path.lineTo(width, y);
            canvas.drawPath(path, gridPaint);
        }

        int n = bins.size();
        float[] xs = new float[n];
        float[] ys = new float[n];
        boolean[] has = new boolean[n];
        for (int i = 0; i < n; i++) {
            xs[i] = (i + 0.5f) * width / n;
            Double average = bins.get(i).average;
            has[i] = average != null;
            ys[i] = has[i] ? yFor(average, top, plotHeight) : top + plotHeight + 8 * density;
        }

        path.reset();
        for (int i = 1; i < n; i++) {
            if (has[i] && has[i - 1]) {
                path.moveTo(xs[i - 1], ys[i - 1]);
                path.lineTo(xs[i], ys[i]);
            }
        }
        canvas.drawPath(path, linePaint);

        for (int i = 0; i < n; i++) {
            if (has[i]) {
                canvas.drawCircle(xs[i], ys[i], 4.5f * density, pointPaint);
                canvas.drawText(String.format(Locale.US, "%.1f", bins.get(i).average), xs[i], ys[i] - 10 * density, valuePaint);
            } else {
                canvas.drawCircle(xs[i], ys[i], 4 * density, ringPaint);
                canvas.drawText("—", xs[i], ys[i] - 10 * density, valuePaint);
            }
            canvas.drawText(bins.get(i).label, xs[i], height - 6 * density, labelPaint);
        }
    }
}
