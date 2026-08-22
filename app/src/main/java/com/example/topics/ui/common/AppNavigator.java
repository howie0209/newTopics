package com.example.topics.ui.common;

import android.app.Activity;
import android.content.Intent;

import com.example.topics.LoginActivity;

public class AppNavigator {
    private AppNavigator() {}

    public static void openTopLevel(Activity activity, Class<?> target) {
        if (activity == null || activity.isFinishing()) return;
        if (activity.getClass().equals(target)) return;
        Intent intent = new Intent(activity, target);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        activity.startActivity(intent);
        activity.finish();
    }

    public static void openLoginAndClear(Activity activity) {
        if (activity == null || activity.isFinishing()) return;
        Intent intent = new Intent(activity, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        activity.startActivity(intent);
        activity.finish();
    }
}
