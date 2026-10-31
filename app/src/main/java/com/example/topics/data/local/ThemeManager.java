package com.example.topics.data.local;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.appcompat.app.AppCompatDelegate;

public class ThemeManager {
    private static final String PREFS_NAME = "AdriftTheme";
    private static final String KEY_LIGHT_MODE = "is_light_mode";

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

    public boolean isLightMode() {
        return preferences.getBoolean(KEY_LIGHT_MODE, false);
    }

    public void setLightMode(boolean light) {
        preferences.edit().putBoolean(KEY_LIGHT_MODE, light).apply();
        AppCompatDelegate.setDefaultNightMode(
                light ? AppCompatDelegate.MODE_NIGHT_NO : AppCompatDelegate.MODE_NIGHT_YES);
    }
}