package com.example.topics;

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
            // 目前跳轉到註冊頁面測試
            Intent intent = new Intent(MainActivity.this, RegisterActivity.class);
            startActivity(intent);
        });

        // 點擊「我的日記」-> 跳轉到地圖頁面 (MapActivity)
        cardMyDiary.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, MapActivity.class);
            startActivity(intent);
        });
    }
}