package com.example.topics;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MapStyleOptions;

import okhttp3.ResponseBody; // 💡 修正編譯錯誤所需的 Import
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

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

        // 綁定 UI 元件
        EditText etUsername = findViewById(R.id.et_register_username);
        EditText etEmail = findViewById(R.id.et_register_email);
        EditText etPassword = findViewById(R.id.et_register_password);
        Button btnRegister = findViewById(R.id.btn_register_submit);
        TextView tvGoLogin = findViewById(R.id.tv_go_login);

        // 註冊按鈕點擊事件
        btnRegister.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // 取得使用者輸入的字串
                String name = etUsername.getText().toString().trim();
                String mail = etEmail.getText().toString().trim();
                String pwd = etPassword.getText().toString().trim();

                // 基本防呆檢查
                if (name.isEmpty() || mail.isEmpty() || pwd.isEmpty()) {
                    Toast.makeText(RegisterActivity.this, "請填寫完整資訊", Toast.LENGTH_SHORT).show();
                    return;
                }

                // 建立 User 物件並發送 API 請求
                User user = new User(name, pwd, mail);
                ApiService apiService = RetrofitClient.getRetrofitInstance().create(ApiService.class);

                // 💡 修正處：將 Callback<Void> 改為 Callback<ResponseBody> 以解決紅字錯誤
                apiService.register(user).enqueue(new Callback<ResponseBody>() {
                    @Override
                    public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                        if (response.isSuccessful()) {
                            Toast.makeText(RegisterActivity.this, "註冊成功！", Toast.LENGTH_SHORT).show();

                            // 註冊成功後跳轉
                            Intent intent = new Intent(RegisterActivity.this, VerifyActivity.class);
                            startActivity(intent);
                            finish();
                        } else {
                            // 💡 根據截圖邏輯，失敗時提示帳號可能已存在
                            Toast.makeText(RegisterActivity.this, "註冊失敗：帳號可能已存在", Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onFailure(Call<ResponseBody> call, Throwable t) {
                        Log.e("API_REG", "連線失敗: " + t.getMessage());
                        Toast.makeText(RegisterActivity.this, "伺服器連線失敗", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });

        // 返回登入頁點擊事件
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