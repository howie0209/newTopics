package com.example.topics;

import android.content.Intent;
import android.content.SharedPreferences;
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

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class LoginActivity extends AppCompatActivity implements OnMapReadyCallback {

    private GoogleMap mBackgroundMap;
    private EditText etEmail, etPassword;
    private ApiService apiService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        // 初始化 Retrofit
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("http://10.0.2.2:3000/") // 💡 記得換成你的 IP
                .addConverterFactory(GsonConverterFactory.create())
                .build();
        apiService = retrofit.create(ApiService.class);

        // 初始化背景地圖 fragment
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.login_map_background);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }

        // 綁定 XML 裡的元件 ID
        etEmail = findViewById(R.id.et_login_email); // 💡 確保 XML 裡有這個 ID
        etPassword = findViewById(R.id.et_login_password);
        Button btnLogin = findViewById(R.id.btn_login_submit);
        TextView tvGoRegister = findViewById(R.id.tv_go_register);

        // 1. 點擊「登入」按鈕邏輯
        btnLogin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                performLogin();

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

    private void performLogin() {
        String email = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString().trim();

        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "請輸入電子郵件與密碼", Toast.LENGTH_SHORT).show();
            return;
        }

        // 封裝登入資料
        User loginUser = new User();
        loginUser.setEmail(email);
        loginUser.setPassword(password);

        // 執行登入請求
        apiService.login(loginUser).enqueue(new Callback<LoginResponse>() {
            @Override
            public void onResponse(Call<LoginResponse> call, Response<LoginResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    // 💡 登入成功，儲存狀態與關鍵的 userId
                    String userId = response.body().getUserId();

                    SharedPreferences.Editor editor = getSharedPreferences("UserData", MODE_PRIVATE).edit();
                    editor.putBoolean("isLoggedIn", true);
                    editor.putString("current_user_id", userId); // 💡 儲存 ID 用於日記綁定
                    editor.apply();

                    Toast.makeText(LoginActivity.this, "登入成功！", Toast.LENGTH_SHORT).show();

                    Intent intent = new Intent(LoginActivity.this, MapActivity.class);
                    startActivity(intent);
                    finish();
                } else {
                    Toast.makeText(LoginActivity.this, "登入失敗：帳號或密碼錯誤", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<LoginResponse> call, Throwable t) {
                Log.e("LoginError", "連線失敗: " + t.getMessage());
                Toast.makeText(LoginActivity.this, "伺服器連線失敗，請檢查網路", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mBackgroundMap = googleMap;

        try {
            googleMap.setMapStyle(
                    MapStyleOptions.loadRawResourceStyle(
                            this, R.raw.map_style_dark));
        } catch (Exception e) {
            e.printStackTrace();
        }

        mBackgroundMap.getUiSettings().setAllGesturesEnabled(false);

        LatLng taiwanCenter = new LatLng(23.6, 121.0);
        mBackgroundMap.moveCamera(CameraUpdateFactory.newLatLngZoom(taiwanCenter, 7.5f));
    }
}