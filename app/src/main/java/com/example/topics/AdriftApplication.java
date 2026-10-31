package com.example.topics;

import android.app.Application;
import androidx.appcompat.app.AppCompatDelegate;
import com.example.topics.data.local.ThemeManager;

public class AdriftApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        boolean isLight = ThemeManager.getInstance(this).isLightMode();
        AppCompatDelegate.setDefaultNightMode(
                isLight ? AppCompatDelegate.MODE_NIGHT_NO : AppCompatDelegate.MODE_NIGHT_YES);
    }
}
