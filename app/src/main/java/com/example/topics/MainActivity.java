package com.example.topics; // ⚠️ 請檢查這行，確保跟你的專案 Package 名稱一致

import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

public class MainActivity extends AppCompatActivity {

    private CardView cardFriends;
    private CardView cardMyDiary;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 1. 初始化元件
        initView();

        // 2. 設定點擊監聽器
        setListeners();
    }

    private void initView() {
        cardFriends = findViewById(R.id.card_friends);
        cardMyDiary = findViewById(R.id.card_my_diary);
    }

    private void setListeners() {
        // 點擊「好友列表」
        cardFriends.setOnClickListener(v -> {
            // 目前先跳轉到註冊頁面測試，之後有好友頁面再修改此處
            Intent intent = new Intent(MainActivity.this, RegisterActivity.class);
            startActivity(intent);
        });

        // 點擊「我的日記」-> 跳轉到剛改名好的地圖頁面 (MapActivity)
        cardMyDiary.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, MapActivity.class);
            startActivity(intent);
        });
    }
}