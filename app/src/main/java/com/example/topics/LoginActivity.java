package com.example.topics;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MapStyleOptions; // 👈 記得加這行

public class LoginActivity extends AppCompatActivity implements OnMapReadyCallback {

    private GoogleMap mBackgroundMap;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);



        setContentView(R.layout.activity_login);

        // 初始化背景地圖 fragment
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.login_map_background);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }

        // 綁定 XML 裡的元件 ID
        Button btnLogin = findViewById(R.id.btn_login_submit);
        TextView tvGoRegister = findViewById(R.id.tv_go_register);

        // 1. 點擊「登入」按鈕邏輯
        btnLogin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // 💡 儲存登入狀態
                SharedPreferences.Editor editor = getSharedPreferences("UserData", MODE_PRIVATE).edit();
                editor.putBoolean("isLoggedIn", true);
                editor.apply();

                Intent intent = new Intent(LoginActivity.this, MapActivity.class);
                startActivity(intent);
                finish();
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

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mBackgroundMap = googleMap;

        // 💡 套用深色地圖樣式
        try {
            googleMap.setMapStyle(
                    MapStyleOptions.loadRawResourceStyle(
                            this, R.raw.map_style_dark));
        } catch (Exception e) {
            e.printStackTrace();
        }

        // 💡 禁用所有地圖手勢，讓它純粹當裝飾背景
        mBackgroundMap.getUiSettings().setAllGesturesEnabled(false);

        // 將背景地圖定位在一個好看的位置（例如台灣中心）
        LatLng taiwanCenter = new LatLng(23.6, 121.0);
        mBackgroundMap.moveCamera(CameraUpdateFactory.newLatLngZoom(taiwanCenter, 7.5f));
    }
}