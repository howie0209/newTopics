package com.example.topics;

import android.content.Intent;
import android.os.Bundle;
import android.util.Patterns;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

import com.example.topics.data.local.SessionManager;
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

public class LoginActivity extends AppCompatActivity implements OnMapReadyCallback {

    private GoogleMap mBackgroundMap;
    private EditText etEmail, etPassword;
    private AuthRepository authRepository;
    private SessionManager sessionManager;
    private Button btnLogin;
    private boolean destroyed;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        AdriftSystemUi.apply(this);

        authRepository = new AuthRepository(this);
        sessionManager = SessionManager.getInstance(this);

        // 初始化背景地圖 fragment
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.login_map_background);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }

        etEmail = findViewById(R.id.et_login_email);
        etPassword = findViewById(R.id.et_login_password);
        btnLogin = findViewById(R.id.btn_login_submit);
        TextView tvGoRegister = findViewById(R.id.tv_go_register);

        if (sessionManager.hasToken()) {
            restoreExistingSession();
        }

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
        if (btnLogin != null && !btnLogin.isEnabled()) return;
        String email = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString().trim();

        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "請輸入電子郵件與密碼", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            Toast.makeText(this, "請輸入有效的電子郵件", Toast.LENGTH_SHORT).show();
            return;
        }

        setLoading(true);
        authRepository.login(email, password, new RepositoryCallback<UserDto>() {
            @Override
            public void onSuccess(UserDto user) {
                if (!isActive()) return;
                setLoading(false);
                Toast.makeText(LoginActivity.this, "登入成功！", Toast.LENGTH_SHORT).show();
                openMainApp();
            }

            @Override
            public void onError(String message) {
                if (!isActive()) return;
                setLoading(false);
                Toast.makeText(LoginActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void restoreExistingSession() {
        setLoading(true);
        authRepository.restoreSession(new RepositoryCallback<UserDto>() {
            @Override
            public void onSuccess(UserDto user) {
                if (!isActive()) return;
                setLoading(false);
                openMainApp();
            }

            @Override
            public void onError(String message) {
                if (!isActive()) return;
                setLoading(false);
                authRepository.logout();
            }
        });
    }

    private void openMainApp() {
        Intent intent = new Intent(LoginActivity.this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void setLoading(boolean loading) {
        if (btnLogin != null) {
            btnLogin.setEnabled(!loading);
            btnLogin.setText(loading ? "登入中..." : "登入");
        }
    }

    private boolean isActive() {
        return !destroyed && !isFinishing() && !isDestroyed();
    }

    @Override
    protected void onDestroy() {
        destroyed = true;
        super.onDestroy();
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mBackgroundMap = googleMap;

        try {
            googleMap.setMapStyle(
                    MapStyleOptions.loadRawResourceStyle(
                            this, R.raw.map_style_dark));
        } catch (Exception ignored) {
        }

        mBackgroundMap.getUiSettings().setAllGesturesEnabled(false);

        LatLng taiwanCenter = new LatLng(23.6, 121.0);
        mBackgroundMap.moveCamera(CameraUpdateFactory.newLatLngZoom(taiwanCenter, 7.5f));
    }
}
