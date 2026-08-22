package com.example.topics;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import com.example.topics.data.local.SessionManager;
import com.example.topics.data.model.EmptyResponse;
import com.example.topics.data.model.UserDto;
import com.example.topics.data.repository.RepositoryCallback;
import com.example.topics.data.repository.UserRepository;

public class SettingsActivity extends AppCompatActivity {

    private UserRepository userRepository;
    private SessionManager sessionManager;

    // UI 元件
    private TextView tvUsername, tvEmail, tvJoinDate;
    private LinearLayout layoutEditName, layoutEditEmail, layoutEditPassword;
    private EditText etNewName, etCurrentPwdForEmail, etNewEmail, etCurrentPwd, etNewPwd, etConfirmPwd;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        userRepository = new UserRepository(this);
        sessionManager = SessionManager.getInstance(this);

        initViews();
        loadUserProfile();
        setupClickListeners();
    }

    private void initViews() {
        tvUsername = findViewById(R.id.tv_settings_username);
        tvEmail = findViewById(R.id.tv_settings_email);
        tvJoinDate = findViewById(R.id.tv_settings_join_date);

        // 展開的面板容器
        layoutEditName = findViewById(R.id.layout_edit_name);
        layoutEditEmail = findViewById(R.id.layout_edit_email);
        layoutEditPassword = findViewById(R.id.layout_edit_password);

        // 輸入框
        etNewName = findViewById(R.id.et_new_name);
        etCurrentPwdForEmail = findViewById(R.id.et_current_pwd_for_email);
        etNewEmail = findViewById(R.id.et_new_email);
        etCurrentPwd = findViewById(R.id.et_current_pwd);
        etNewPwd = findViewById(R.id.et_new_pwd);
        etConfirmPwd = findViewById(R.id.et_confirm_pwd);
    }

    private void setupClickListeners() {
        // 返回按鈕
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        // 面板展開/收合控制 (點擊標題展開，其他收合)
        findViewById(R.id.header_edit_name).setOnClickListener(v -> togglePanel(layoutEditName));
        findViewById(R.id.header_edit_email).setOnClickListener(v -> togglePanel(layoutEditEmail));
        findViewById(R.id.header_edit_password).setOnClickListener(v -> togglePanel(layoutEditPassword));

        // 1. 儲存新名稱
        findViewById(R.id.btn_save_name).setOnClickListener(v -> {
            String newName = etNewName.getText().toString().trim();
            if(newName.isEmpty()) return;
            userRepository.updateName(newName, new UserCallback("名稱更新成功"));
        });

        // 2. 更新 Email
        findViewById(R.id.btn_save_email).setOnClickListener(v -> {
            String pwd = etCurrentPwdForEmail.getText().toString();
            String newEmail = etNewEmail.getText().toString().trim();
            if(pwd.isEmpty() || newEmail.isEmpty()) {
                Toast.makeText(this, "請填寫完整資訊", Toast.LENGTH_SHORT).show(); return;
            }
            userRepository.updateEmail(newEmail, pwd, new UserCallback("Email 更新成功"));
        });

        // 3. 更新密碼
        findViewById(R.id.btn_save_password).setOnClickListener(v -> {
            String currentPwd = etCurrentPwd.getText().toString();
            String newPwd = etNewPwd.getText().toString();
            String confirmPwd = etConfirmPwd.getText().toString();

            if(!newPwd.equals(confirmPwd)) {
                Toast.makeText(this, "兩次輸入的新密碼不一致！", Toast.LENGTH_SHORT).show(); return;
            }
            if(newPwd.length() < 6) {
                Toast.makeText(this, "新密碼至少需 6 個字元", Toast.LENGTH_SHORT).show(); return;
            }

            userRepository.updatePassword(currentPwd, newPwd, confirmPwd, new RepositoryCallback<EmptyResponse>() {
                @Override
                public void onSuccess(EmptyResponse value) {
                    Toast.makeText(SettingsActivity.this, "密碼更新成功，請重新登入", Toast.LENGTH_SHORT).show();
                    logoutUser();
                }

                @Override
                public void onError(String message) {
                    Toast.makeText(SettingsActivity.this, message, Toast.LENGTH_SHORT).show();
                }
            });
        });

        // 4. 危險區域：刪除帳號
        findViewById(R.id.header_danger_zone).setOnClickListener(v -> showDeleteAccountDialog());
    }

    // 控制面板展開的輔助方法 (一次只展開一個)
    private void togglePanel(LinearLayout panelToToggle) {
        layoutEditName.setVisibility(View.GONE);
        layoutEditEmail.setVisibility(View.GONE);
        layoutEditPassword.setVisibility(View.GONE);
        panelToToggle.setVisibility(View.VISIBLE);
    }

    // 載入基本資料
    private void loadUserProfile() {
        userRepository.getMe(new RepositoryCallback<UserDto>() {
            @Override
            public void onSuccess(UserDto user) {
                tvUsername.setText(user.getDisplayName());
                tvEmail.setText(user.email == null ? "" : user.email);
                if(user.createdAt != null && user.createdAt.length() >= 10) {
                    tvJoinDate.setText(user.createdAt.substring(0, 10));
                }
            }

            @Override
            public void onError(String message) {
                Toast.makeText(SettingsActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    // 刪除帳號的警告彈窗
    private void showDeleteAccountDialog() {
        LinearLayout fields = new LinearLayout(this);
        fields.setOrientation(LinearLayout.VERTICAL);
        fields.setPadding(48, 8, 48, 0);

        EditText passwordInput = new EditText(this);
        passwordInput.setHint("目前密碼");
        passwordInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        fields.addView(passwordInput);

        EditText confirmInput = new EditText(this);
        confirmInput.setHint("輸入 DELETE 確認");
        fields.addView(confirmInput);

        new AlertDialog.Builder(this)
                .setTitle("⚠️ 警告：刪除帳號")
                .setMessage("您確定要永久刪除帳號嗎？\n\n這將會清除您所有的地圖日記與好友紀錄，且無法復原！")
                .setView(fields)
                .setPositiveButton("確認刪除", (dialog, which) -> {
                    String password = passwordInput.getText().toString();
                    String confirm = confirmInput.getText().toString().trim();
                    if (!"DELETE".equals(confirm)) {
                        Toast.makeText(SettingsActivity.this, "請輸入 DELETE 確認刪除", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    userRepository.deleteAccount(password, new RepositoryCallback<EmptyResponse>() {
                        @Override
                        public void onSuccess(EmptyResponse value) {
                            Toast.makeText(SettingsActivity.this, "帳號已永久刪除", Toast.LENGTH_LONG).show();
                            logoutUser();
                        }

                        @Override
                        public void onError(String message) {
                            Toast.makeText(SettingsActivity.this, message, Toast.LENGTH_SHORT).show();
                        }
                    });
                })
                .setNegativeButton("取消", null)
                .show();
    }

    // 登出並返回登入頁
    private void logoutUser() {
        sessionManager.clear();
        Intent intent = new Intent(this, LoginActivity.class); // 換成你的登入頁面類別
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
    }

    private class UserCallback implements RepositoryCallback<UserDto> {
        private final String successMsg;

        UserCallback(String successMsg) {
            this.successMsg = successMsg;
        }
        @Override
        public void onSuccess(UserDto value) {
            Toast.makeText(SettingsActivity.this, successMsg, Toast.LENGTH_SHORT).show();
            loadUserProfile();
            layoutEditName.setVisibility(View.GONE);
            layoutEditEmail.setVisibility(View.GONE);
            layoutEditPassword.setVisibility(View.GONE);
            etNewName.setText(""); etNewEmail.setText(""); etCurrentPwdForEmail.setText("");
        }

        @Override
        public void onError(String message) {
            Toast.makeText(SettingsActivity.this, message, Toast.LENGTH_SHORT).show();
        }
    }
}
