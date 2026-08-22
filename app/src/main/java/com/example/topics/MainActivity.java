package com.example.topics;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.topics.data.local.SessionManager;
import com.example.topics.ui.design.AdriftSystemUi;

public class MainActivity extends AppCompatActivity {
    private TextView navMap, navFriends, navExplore, navSettings;
    private TextView kicker, title, body;
    private Button primaryButton;
    private String currentTab = "map";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        AdriftSystemUi.apply(this);

        if (!SessionManager.getInstance(this).hasToken()) {
            Intent intent = new Intent(this, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
            return;
        }

        bindViews();
        setupNavigation();
        selectTab("map");
    }

    private void bindViews() {
        navMap = findViewById(R.id.nav_map);
        navFriends = findViewById(R.id.nav_friends);
        navExplore = findViewById(R.id.nav_explore);
        navSettings = findViewById(R.id.nav_settings);
        kicker = findViewById(R.id.tv_shell_kicker);
        title = findViewById(R.id.tv_shell_title);
        body = findViewById(R.id.tv_shell_body);
        primaryButton = findViewById(R.id.btn_shell_primary);
    }

    private void setupNavigation() {
        navMap.setOnClickListener(v -> selectTab("map"));
        navFriends.setOnClickListener(v -> selectTab("friends"));
        navExplore.setOnClickListener(v -> selectTab("explore"));
        navSettings.setOnClickListener(v -> selectTab("settings"));
        primaryButton.setOnClickListener(v -> openSelectedTab());
    }

    private void selectTab(String tab) {
        currentTab = tab;
        navMap.setSelected("map".equals(tab));
        navFriends.setSelected("friends".equals(tab));
        navExplore.setSelected("explore".equals(tab));
        navSettings.setSelected("settings".equals(tab));

        int active = Color.WHITE;
        int inactive = Color.rgb(185, 217, 232);
        navMap.setTextColor("map".equals(tab) ? active : inactive);
        navFriends.setTextColor("friends".equals(tab) ? active : inactive);
        navExplore.setTextColor("explore".equals(tab) ? active : inactive);
        navSettings.setTextColor("settings".equals(tab) ? active : inactive);

        if ("friends".equals(tab)) {
            kicker.setText("FRIENDS");
            title.setText("好友網絡");
            body.setText("查看邀請、搜尋使用者，整理與你同行的人。");
            primaryButton.setText("前往好友");
        } else if ("explore".equals(tab)) {
            kicker.setText("EXPLORE");
            title.setText("探索附近記憶");
            body.setText("切換到探索視角，看見附近公開漂流的片段。");
            primaryButton.setText("開啟探索");
        } else if ("settings".equals(tab)) {
            kicker.setText("SETTINGS");
            title.setText("帳號設定");
            body.setText("管理個人資料、Email、密碼與危險操作。");
            primaryButton.setText("開啟設定");
        } else {
            kicker.setText("MAP");
            title.setText("地圖日記");
            body.setText("回到你的地圖日記，沿著位置找回每一次停留。");
            primaryButton.setText("開啟地圖");
        }
    }

    private void openSelectedTab() {
        if ("settings".equals(currentTab)) {
            startActivity(new Intent(this, SettingsActivity.class));
        } else {
            Intent intent = new Intent(this, MapActivity.class);
            intent.putExtra("initial_tab", currentTab);
            startActivity(intent);
        }
    }
}
