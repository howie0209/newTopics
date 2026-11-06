package com.example.topics;

import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.appcompat.app.AppCompatActivity;

import com.example.topics.data.local.SessionManager;
import com.example.topics.data.local.ThemeManager;
import com.example.topics.data.model.UserDto;
import com.example.topics.data.repository.AuthRepository;
import com.example.topics.data.repository.RepositoryCallback;
import com.example.topics.ui.design.AdriftSystemUi;

/**
 * 啟動畫面，照 iOS 版 SplashView + RootViewModel：
 * 至少顯示 0.9 秒，同時檢查登入狀態（最多 8 秒），再決定進地圖還是登入頁。
 * 網路不通但手機上有登入紀錄時，跟 iOS 一樣直接讓使用者進去。
 */
@SuppressLint("CustomSplashScreen")
public class SplashActivity extends AppCompatActivity {
    private static final long MIN_DISPLAY_MS = 900;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private long startedAt;
    private boolean navigated;
    private ObjectAnimator breathing;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);
        AdriftSystemUi.apply(this);
        startedAt = System.currentTimeMillis();

        FrameLayout root = findViewById(R.id.splash_root);
        root.addView(new StarsView(this, ThemeManager.isNight(this)), 0,
                new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        View logo = findViewById(R.id.splash_logo);
        breathing = ObjectAnimator.ofPropertyValuesHolder(logo,
                PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.028f),
                PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.028f),
                PropertyValuesHolder.ofFloat(View.ALPHA, 0.92f, 1f));
        breathing.setDuration(3200);
        breathing.setRepeatCount(ValueAnimator.INFINITE);
        breathing.setRepeatMode(ValueAnimator.REVERSE);
        breathing.start();

        SessionManager session = SessionManager.getInstance(this);
        if (!session.hasToken()) {
            finishWith(LoginActivity.class);
            return;
        }
        new AuthRepository(this).restoreSession(new RepositoryCallback<UserDto>() {
            @Override
            public void onSuccess(UserDto value) {
                finishWith(MapActivity.class);
            }

            @Override
            public void onError(String message) {
                // 401 時 token 已被清掉 → 回登入頁；只是連不上網路但有登入紀錄 → 照 iOS 先讓使用者進去
                boolean canStayIn = session.hasToken() && session.getUser() != null;
                finishWith(canStayIn ? MapActivity.class : LoginActivity.class);
            }
        });
    }

    private void finishWith(Class<?> target) {
        long wait = Math.max(0, MIN_DISPLAY_MS - (System.currentTimeMillis() - startedAt));
        handler.postDelayed(() -> {
            if (navigated || isFinishing() || isDestroyed()) return;
            navigated = true;
            Intent intent = new Intent(this, target);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            finish();
        }, wait);
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (breathing != null) breathing.cancel();
        super.onDestroy();
    }

    /** 背景星點，位置與大小照 iOS 版 SplashBackground 的 stars */
    private static class StarsView extends View {
        private static final float[][] STARS = {
                {0.16f, 0.19f, 2.0f}, {0.78f, 0.15f, 1.5f}, {0.88f, 0.33f, 2.4f}, {0.12f, 0.62f, 1.4f},
                {0.82f, 0.72f, 1.8f}, {0.29f, 0.82f, 2.2f}, {0.66f, 0.89f, 1.3f}, {0.44f, 0.28f, 1.4f}
        };
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final boolean night;
        private final float density;

        StarsView(Context context, boolean night) {
            super(context);
            this.night = night;
            this.density = context.getResources().getDisplayMetrics().density;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            for (int i = 0; i < STARS.length; i++) {
                boolean accent = i % 3 == 0;
                if (night) paint.setColor(accent ? 0xBF00FFFF : 0x94FFFFFF);
                else paint.setColor(accent ? 0xAD26BFD9 : 0x5C1A73B3);
                canvas.drawCircle(STARS[i][0] * getWidth(), STARS[i][1] * getHeight(), STARS[i][2] * density / 2f, paint);
            }
        }
    }
}
