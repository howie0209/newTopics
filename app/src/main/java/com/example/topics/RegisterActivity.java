package com.example.topics;

import android.content.Intent;
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
import com.google.android.gms.maps.model.MapStyleOptions;

public class RegisterActivity extends AppCompatActivity implements OnMapReadyCallback {

    private GoogleMap mBackgroundMap;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        // 初始化背景地圖 fragment
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.register_map_background);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }

        Button btnRegister = findViewById(R.id.btn_register_submit);
        TextView tvGoLogin = findViewById(R.id.tv_go_login);

        btnRegister.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(RegisterActivity.this, VerifyActivity.class);
                startActivity(intent);
            }
        });

        tvGoLogin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mBackgroundMap = googleMap;

        // 套用深色地圖樣式
        try {
            googleMap.setMapStyle(
                    MapStyleOptions.loadRawResourceStyle(
                            this, R.raw.map_style_dark));
        } catch (Exception e) {
            e.printStackTrace();
        }

        // 禁用地圖手勢
        mBackgroundMap.getUiSettings().setAllGesturesEnabled(false);

        // 定位在台灣中心
        LatLng taiwanCenter = new LatLng(23.6, 121.0);
        mBackgroundMap.moveCamera(CameraUpdateFactory.newLatLngZoom(taiwanCenter, 7.5f));
    }
}