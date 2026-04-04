package com.example.topics;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

public class LoginActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        // 綁定 XML 裡的元件 ID
        Button btnLogin = findViewById(R.id.btn_login_submit);
        TextView tvGoRegister = findViewById(R.id.tv_go_register);

        // 1. 點擊「登入」按鈕邏輯
        btnLogin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // 修改處：登入後先跳轉到「主選單 (MainActivity)」
                Intent intent = new Intent(LoginActivity.this, MainActivity.class);
                startActivity(intent);
                finish(); // 登入後關閉此頁，防止按返回鍵回到登入畫面
            }
        });

        // 2. 點擊「註冊」文字邏輯
        tvGoRegister.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(LoginActivity.this, RegisterActivity.class);
                startActivity(intent);
            }
        });
    }
}