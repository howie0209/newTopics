package com.example.topics;

import android.app.Application;

import com.example.topics.data.local.ThemeManager;

public class AdriftApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        // 套用使用者選的外觀（預設跟隨手機系統）
        ThemeManager.getInstance(this).apply();
    }
}
