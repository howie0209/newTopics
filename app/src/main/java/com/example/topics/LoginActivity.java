package com.example.topics;

import android.content.Intent;
import android.os.Bundle;
import android.util.Patterns;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.topics.data.local.SessionManager;
import com.example.topics.data.local.ThemeManager;
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

/**
 * 登入／註冊，對齊網頁版 AuthPanel 的分步驟流程：
 * 1. 輸入 Email → 後端確認是否已有帳號
 * 2a. 已有帳號：輸入密碼登入
 * 2b. 新帳號：使用者名稱、使用者 ID → 設定密碼 → 建立帳號
 */
public class LoginActivity extends AppCompatActivity implements OnMapReadyCallback {
    private static final int STEP_EMAIL = 0;
    private static final int STEP_LOGIN_PASSWORD = 1;
    private static final int STEP_REGISTER_PROFILE = 2;
    private static final int STEP_REGISTER_PASSWORD = 3;
    private static final String USER_CODE_PATTERN = "^[a-zA-Z0-9_-]{4,20}$";

    private AuthRepository authRepository;
    private SessionManager sessionManager;
    private EditText etEmail, etPassword, etName, etUserCode, etConfirm;
    private TextView tvTitle, tvSubtitle, tvEmailChip, tvError, btnBack;
    private View layoutEmail, layoutProfile, layoutPassword, layoutConfirm;
    private Button btnSubmit;
    private int step = STEP_EMAIL;
    private boolean loading;
    private boolean destroyed;
    private int requestVersion = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        AdriftSystemUi.apply(this);

        authRepository = new AuthRepository(this);
        sessionManager = SessionManager.getInstance(this);

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.login_map_background);
        if (mapFragment != null) mapFragment.getMapAsync(this);

        etEmail = findViewById(R.id.et_login_email);
        etPassword = findViewById(R.id.et_login_password);
        etName = findViewById(R.id.et_register_name);
        etUserCode = findViewById(R.id.et_register_code);
        etConfirm = findViewById(R.id.et_register_confirm);
        tvTitle = findViewById(R.id.tv_auth_title);
        tvSubtitle = findViewById(R.id.tv_auth_subtitle);
        tvEmailChip = findViewById(R.id.tv_auth_email_chip);
        tvError = findViewById(R.id.tv_auth_error);
        btnBack = findViewById(R.id.btn_auth_back);
        layoutEmail = findViewById(R.id.layout_auth_email);
        layoutProfile = findViewById(R.id.layout_auth_profile);
        layoutPassword = findViewById(R.id.layout_auth_password);
        layoutConfirm = findViewById(R.id.layout_auth_confirm);
        btnSubmit = findViewById(R.id.btn_login_submit);

        btnSubmit.setOnClickListener(v -> submit());
        btnBack.setOnClickListener(v -> goBack());
        tvEmailChip.setOnClickListener(v -> moveTo(STEP_EMAIL));
        TextView.OnEditorActionListener done = (v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                submit();
                return true;
            }
            return false;
        };
        etPassword.setOnEditorActionListener(done);
        etConfirm.setOnEditorActionListener(done);
        etEmail.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_NEXT || actionId == EditorInfo.IME_ACTION_DONE) {
                if (step == STEP_EMAIL) {
                    submit();
                    return true;
                }
            }
            return false;
        });

        render();
        if (sessionManager.hasToken()) restoreExistingSession();
    }

    @Override
    public void onBackPressed() {
        if (step != STEP_EMAIL && !loading) {
            goBack();
            return;
        }
        super.onBackPressed();
    }

    private void submit() {
        if (loading) return;
        showError("");
        if (step == STEP_EMAIL) {
            String email = etEmail.getText().toString().trim().toLowerCase();
            if (email.isEmpty()) {
                showError("請輸入 Email");
                return;
            }
            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                showError("Email 格式不正確");
                return;
            }
            etEmail.setText(email);
            final int version = ++requestVersion;
            setLoading(true);
            authRepository.checkEmail(email, new RepositoryCallback<Boolean>() {
                @Override
                public void onSuccess(Boolean exists) {
                    if (!isActive() || version != requestVersion) return;
                    setLoading(false);
                    moveTo(Boolean.TRUE.equals(exists) ? STEP_LOGIN_PASSWORD : STEP_REGISTER_PROFILE);
                }

                @Override
                public void onError(String message) {
                    if (!isActive() || version != requestVersion) return;
                    setLoading(false);
                    showError(message);
                }
            });
            return;
        }

        if (step == STEP_REGISTER_PROFILE) {
            String name = etName.getText().toString().trim();
            String userCode = etUserCode.getText().toString().trim();
            if (name.isEmpty()) {
                showError("請輸入使用者名稱");
                return;
            }
            if (name.length() < 2 || name.length() > 30) {
                showError("使用者名稱長度需為 2 到 30 字元");
                return;
            }
            if (userCode.isEmpty()) {
                showError("請輸入使用者 ID");
                return;
            }
            if (!userCode.matches(USER_CODE_PATTERN)) {
                showError("使用者 ID 需為 4 到 20 個英文、數字、底線或減號");
                return;
            }
            moveTo(STEP_REGISTER_PASSWORD);
            return;
        }

        String email = etEmail.getText().toString().trim().toLowerCase();
        String password = etPassword.getText().toString();
        if (password.isEmpty()) {
            showError("請輸入密碼");
            return;
        }

        if (step == STEP_LOGIN_PASSWORD) {
            setLoading(true);
            authRepository.login(email, password, new RepositoryCallback<UserDto>() {
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
                    showError(message == null || message.isEmpty() ? "暫時無法完成，請稍後再試" : message);
                }
            });
            return;
        }

        // STEP_REGISTER_PASSWORD
        if (password.length() < 8) {
            showError("密碼至少需要 8 個字元");
            return;
        }
        String confirm = etConfirm.getText().toString();
        if (confirm.isEmpty()) {
            showError("請再次輸入密碼");
            return;
        }
        if (!confirm.equals(password)) {
            showError("兩次輸入的密碼不一致");
            return;
        }
        String name = etName.getText().toString().trim();
        String userCode = etUserCode.getText().toString().trim().toLowerCase();
        setLoading(true);
        authRepository.register(name, email, password, userCode, new RepositoryCallback<UserDto>() {
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
                // 使用者 ID 被用走時，回到上一步讓使用者改 ID（對齊網頁版）
                if (message != null && message.contains("使用者 ID")) {
                    moveTo(STEP_REGISTER_PROFILE);
                }
                showError(message == null || message.isEmpty() ? "暫時無法完成，請稍後再試" : message);
            }
        });
    }

    private void goBack() {
        if (loading) return;
        moveTo(step == STEP_REGISTER_PASSWORD ? STEP_REGISTER_PROFILE : STEP_EMAIL);
    }

    private void moveTo(int next) {
        if (next == STEP_EMAIL) {
            requestVersion++;
            etPassword.setText("");
            etConfirm.setText("");
        }
        step = next;
        showError("");
        render();
    }

    private void render() {
        layoutEmail.setVisibility(step == STEP_EMAIL ? View.VISIBLE : View.GONE);
        layoutProfile.setVisibility(step == STEP_REGISTER_PROFILE ? View.VISIBLE : View.GONE);
        boolean passwordStep = step == STEP_LOGIN_PASSWORD || step == STEP_REGISTER_PASSWORD;
        layoutPassword.setVisibility(passwordStep ? View.VISIBLE : View.GONE);
        layoutConfirm.setVisibility(step == STEP_REGISTER_PASSWORD ? View.VISIBLE : View.GONE);
        btnBack.setVisibility(step == STEP_EMAIL ? View.GONE : View.VISIBLE);
        tvEmailChip.setVisibility(step == STEP_EMAIL ? View.GONE : View.VISIBLE);
        tvEmailChip.setText("✉ " + etEmail.getText().toString().trim() + "  · 更換");
        etPassword.setHint(step == STEP_REGISTER_PASSWORD ? "至少 8 個字元" : "輸入密碼");

        // 文案對齊網頁版 getStepCopy
        if (step == STEP_LOGIN_PASSWORD) {
            tvTitle.setText("歡迎回來");
            tvSubtitle.setText("輸入密碼，回到你的城市記憶。");
        } else if (step == STEP_REGISTER_PROFILE) {
            tvTitle.setText("建立帳號");
            tvSubtitle.setText("先讓 Adrift 認識你。");
        } else if (step == STEP_REGISTER_PASSWORD) {
            tvTitle.setText("設定密碼");
            tvSubtitle.setText("完成最後一步，開始留下第一個足跡。");
        } else {
            tvTitle.setText("歡迎來到 Adrift");
            tvSubtitle.setText("輸入 Email，繼續你的漂流足跡。");
        }
        btnSubmit.setText(submitLabel());
        View focus = step == STEP_EMAIL ? etEmail : step == STEP_REGISTER_PROFILE ? etName : etPassword;
        focus.requestFocus();
    }

    private String submitLabel() {
        if (loading) {
            if (step == STEP_LOGIN_PASSWORD) return "登入中...";
            if (step == STEP_REGISTER_PASSWORD) return "建立中...";
            if (step == STEP_EMAIL) return "確認中...";
            return "處理中...";
        }
        if (step == STEP_LOGIN_PASSWORD) return "登入";
        if (step == STEP_REGISTER_PASSWORD) return "建立帳號";
        return "下一步";
    }

    private void showError(String message) {
        boolean empty = message == null || message.isEmpty();
        tvError.setText(empty ? "" : message);
        tvError.setVisibility(empty ? View.GONE : View.VISIBLE);
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

    private void setLoading(boolean value) {
        loading = value;
        btnSubmit.setEnabled(!value);
        btnSubmit.setText(submitLabel());
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
        try {
            if (ThemeManager.isNight(this)) {
                googleMap.setMapStyle(MapStyleOptions.loadRawResourceStyle(this, R.raw.map_style_dark));
            }
        } catch (Exception ignored) {
        }
        googleMap.getUiSettings().setAllGesturesEnabled(false);
        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(23.6, 121.0), 7.5f));
    }

    // 點輸入框以外的地方收起鍵盤（照 iOS 版）
    @Override
    public boolean dispatchTouchEvent(android.view.MotionEvent event) {
        KeyboardDismiss.handle(this, event);
        return super.dispatchTouchEvent(event);
    }
}
