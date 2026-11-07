package com.example.topics.ui.design;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

public class AdriftSystemUi {
    private AdriftSystemUi() {}

    public static void apply(Activity activity) {
        apply(activity, false, false);
    }

    /**
     * 給洞察、管理、驗證、圖片預覽等頁面使用：
     * 1. 依每台手機實際的狀態列／導覽列高度把內容讓開（不寫死 dp）
     * 2. 讓開的上下兩條區域沿用頁面本身的背景色，不會出現色差
     * 3. fitKeyboard 為 true 時，鍵盤彈出會把內容往上推（例如記憶對話的輸入框）
     * 需在 setContentView 之後呼叫。
     */
    public static void applyEdgeToEdge(Activity activity, boolean fitKeyboard) {
        apply(activity, fitKeyboard, true);
    }

    @SuppressWarnings("deprecation")
    private static void apply(Activity activity, boolean fitKeyboard, boolean matchBackground) {
        Window window = activity.getWindow();
        WindowCompat.setDecorFitsSystemWindows(window, false);
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.TRANSPARENT);

        WindowInsetsControllerCompat controller =
                new WindowInsetsControllerCompat(window, window.getDecorView());
        // 明亮主題用深色狀態列圖示，深色主題用淺色圖示
        boolean night = com.example.topics.data.local.ThemeManager.isNight(activity);
        controller.setAppearanceLightStatusBars(!night);
        controller.setAppearanceLightNavigationBars(!night);

        View content = window.findViewById(android.R.id.content);
        if (matchBackground) copyPageBackground(content);

        ViewCompat.setOnApplyWindowInsetsListener(content, (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            int bottom = bars.bottom;
            if (fitKeyboard) {
                Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
                bottom = Math.max(bottom, ime.bottom);
            }
            view.setPadding(0, bars.top, 0, bottom);
            return insets;
        });
        ViewCompat.requestApplyInsets(content);
    }

    // 把頁面根版面的背景複製到外層，狀態列與導覽列底下就會是同一個顏色
    private static void copyPageBackground(View content) {
        if (!(content instanceof ViewGroup)) return;
        ViewGroup group = (ViewGroup) content;
        if (group.getChildCount() == 0) return;
        Drawable bg = group.getChildAt(0).getBackground();
        if (bg == null || bg.getConstantState() == null) return;
        content.setBackground(bg.getConstantState().newDrawable().mutate());
    }
}
