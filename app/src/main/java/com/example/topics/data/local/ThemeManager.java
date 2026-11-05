package com.example.topics.data.local;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;

import androidx.appcompat.app.AppCompatDelegate;

/**
 * 外觀主題，對齊網頁版 AccountSettings 的「外觀」：明亮／深色，另外多一個「跟隨系統」（預設）。
 */
public class ThemeManager {
    public static final String MODE_SYSTEM = "system";
    public static final String MODE_LIGHT = "bright";
    public static final String MODE_DARK = "dark";

    private static final String PREFS_NAME = "AdriftTheme";
    private static final String KEY_MODE = "theme_mode";

    private static ThemeManager instance;
    private final SharedPreferences preferences;

    private ThemeManager(Context context) {
        preferences = context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static synchronized ThemeManager getInstance(Context context) {
        if (instance == null) {
            instance = new ThemeManager(context);
        }
        return instance;
    }

    public String getMode() {
        String mode = preferences.getString(KEY_MODE, MODE_SYSTEM);
        if (MODE_LIGHT.equals(mode) || MODE_DARK.equals(mode)) return mode;
        return MODE_SYSTEM;
    }

    public void setMode(String mode) {
        preferences.edit().putString(KEY_MODE, mode).apply();
        apply();
    }

    public void apply() {
        String mode = getMode();
        int nightMode = MODE_LIGHT.equals(mode) ? AppCompatDelegate.MODE_NIGHT_NO
                : MODE_DARK.equals(mode) ? AppCompatDelegate.MODE_NIGHT_YES
                : AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
        if (AppCompatDelegate.getDefaultNightMode() != nightMode) {
            AppCompatDelegate.setDefaultNightMode(nightMode);
        }
    }

    public static boolean isNight(Context context) {
        int uiMode = context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return uiMode == Configuration.UI_MODE_NIGHT_YES;
    }
}
