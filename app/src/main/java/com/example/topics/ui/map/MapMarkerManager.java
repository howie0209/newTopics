package com.example.topics.ui.map;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;

import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;

public class MapMarkerManager {
    private final float density;

    public MapMarkerManager(Context context) {
        density = context.getResources().getDisplayMetrics().density;
    }

    public BitmapDescriptor iconFor(String moodLabel, boolean isMine, boolean selected) {
        int size = dp(selected ? 52 : 40);
        int center = size / 2;
        int haloRadius = dp(selected ? 24 : 18);
        int coreRadius = dp(selected ? 12 : 8);
        int color = moodColor(moodLabel, isMine);

        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        Paint halo = new Paint(Paint.ANTI_ALIAS_FLAG);
        halo.setShader(new RadialGradient(
                center,
                center,
                haloRadius,
                adjustAlpha(color, selected ? 0.42f : 0.28f),
                Color.TRANSPARENT,
                Shader.TileMode.CLAMP
        ));
        canvas.drawCircle(center, center, haloRadius, halo);

        Paint shell = new Paint(Paint.ANTI_ALIAS_FLAG);
        shell.setColor(adjustAlpha(Color.rgb(10, 24, 38), selected ? 0.88f : 0.78f));
        canvas.drawCircle(center, center, coreRadius + dp(5), shell);

        Paint core = new Paint(Paint.ANTI_ALIAS_FLAG);
        core.setColor(color);
        canvas.drawCircle(center, center, coreRadius, core);

        Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(dp(selected ? 2.4f : 1.8f));
        stroke.setColor(selected ? Color.WHITE : adjustAlpha(Color.WHITE, 0.82f));
        canvas.drawCircle(center, center, coreRadius + dp(1), stroke);

        if (selected) {
            Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
            dot.setColor(Color.WHITE);
            canvas.drawCircle(center, center, dp(3), dot);
        }

        return BitmapDescriptorFactory.fromBitmap(bitmap);
    }

    private int moodColor(String moodLabel, boolean isMine) {
        if (moodLabel == null) return isMine ? Color.rgb(38, 191, 218) : Color.rgb(89, 107, 230);
        if (moodLabel.contains("開心")) return Color.rgb(245, 178, 56);
        if (moodLabel.contains("難過")) return Color.rgb(107, 158, 209);
        if (moodLabel.contains("平靜")) return Color.rgb(82, 189, 148);
        if (moodLabel.contains("興奮")) return Color.rgb(235, 115, 179);
        if (moodLabel.contains("懷舊") || moodLabel.contains("累")) return Color.rgb(158, 138, 219);
        return isMine ? Color.rgb(38, 191, 218) : Color.rgb(89, 217, 179);
    }

    private int dp(float value) {
        return Math.round(value * density);
    }

    private int adjustAlpha(int color, float factor) {
        return Color.argb(
                Math.round(Color.alpha(color) * factor),
                Color.red(color),
                Color.green(color),
                Color.blue(color)
        );
    }
}
