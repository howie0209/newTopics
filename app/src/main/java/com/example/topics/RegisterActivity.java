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

import com.example.topics.data.model.UserDto;
import com.example.topics.data.repository.AuthRepository;
import com.example.topics.data.repository.RepositoryCallback;
import com.example.topics.ui.design.AdriftSystemUi;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MapStyleOptions;

public class RegisterActivity extends AppCompatActivity implements OnMapReadyCallback {

    private GoogleMap mBackgroundMap;
    private AuthRepository authRepository;
    private Button btnRegister;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);
        AdriftSystemUi.apply(this);
        authRepository = new AuthRepository(this);

        // 初始化背景地圖 fragment
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.register_map_background);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }

        // 綁定 UI 元件
        EditText etUsername = findViewById(R.id.et_register_username);
        EditText etUserCode = findViewById(R.id.et_register_user_code);
        EditText etEmail = findViewById(R.id.et_register_email);
        EditText etPassword = findViewById(R.id.et_register_password);
        EditText etConfirmPassword = findViewById(R.id.reg_confirm_password);
        btnRegister = findViewById(R.id.btn_register_submit);
        TextView tvGoLogin = findViewById(R.id.tv_go_login);

        // 註冊按鈕點擊事件
        btnRegister.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // 取得使用者輸入的字串
                String name = etUsername.getText().toString().trim();
                String userCode = etUserCode.getText().toString().trim().toLowerCase();
                String mail = etEmail.getText().toString().trim();
                String pwd = etPassword.getText().toString().trim();
                String confirmPwd = etConfirmPassword.getText().toString().trim();

                // 基本防呆檢查
                if (name.isEmpty() || userCode.isEmpty() || mail.isEmpty() || pwd.isEmpty()) {
                    Toast.makeText(RegisterActivity.this, "請填寫完整資訊", Toast.LENGTH_SHORT).show();
                    return;
                }

                if (!userCode.matches("^[a-zA-Z0-9_-]{4,20}$")) {
                    Toast.makeText(RegisterActivity.this, "使用者 ID 需為 4-20 位英數、底線或連字號", Toast.LENGTH_SHORT).show();
                    return;
                }

                if (!pwd.equals(confirmPwd)) {
                    Toast.makeText(RegisterActivity.this, "兩次輸入的密碼不一致", Toast.LENGTH_SHORT).show();
                    return;
                }

                setLoading(true);
                authRepository.register(name, mail, pwd, userCode, new RepositoryCallback<UserDto>() {
                    @Override
                    public void onSuccess(UserDto user) {
                        setLoading(false);
                        Toast.makeText(RegisterActivity.this, "註冊成功！", Toast.LENGTH_SHORT).show();
                        Intent intent = new Intent(RegisterActivity.this, MainActivity.class);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                    }

                    @Override
                    public void onError(String message) {
                        setLoading(false);
                        Log.e("API_REG", "註冊失敗: " + message);
                        Toast.makeText(RegisterActivity.this, message, Toast.LENGTH_SHORT).show();
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

    private void setLoading(boolean loading) {
        if (btnRegister != null) {
            btnRegister.setEnabled(!loading);
            btnRegister.setText(loading ? "註冊中..." : "註冊");
        }
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
