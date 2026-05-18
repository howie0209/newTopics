package com.example.topics;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import java.util.HashMap;
import java.util.Map;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class SettingsActivity extends AppCompatActivity {

    private String currentUserId;
    private ApiService api;

    // UI 元件
    private TextView tvUsername, tvEmail, tvJoinDate;
    private LinearLayout layoutEditName, layoutEditEmail, layoutEditPassword;
    private EditText etNewName, etCurrentPwdForEmail, etNewEmail, etCurrentPwd, etNewPwd, etConfirmPwd;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // TODO: 請確保你有建立 res/layout/activity_settings.xml
        setContentView(R.layout.activity_settings);

        // 取得當前使用者 ID
        SharedPreferences prefs = getSharedPreferences("UserData", MODE_PRIVATE);
        currentUserId = prefs.getString("current_user_id", "");
        api = RetrofitClient.getRetrofitInstance().create(ApiService.class);

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
            Map<String, String> body = new HashMap<>();
            body.put("newUsername", newName);
            api.updateUsername(currentUserId, body).enqueue(new SimpleCallback("名稱更新成功", true));
        });

        // 2. 更新 Email
        findViewById(R.id.btn_save_email).setOnClickListener(v -> {
            String pwd = etCurrentPwdForEmail.getText().toString();
            String newEmail = etNewEmail.getText().toString().trim();
            if(pwd.isEmpty() || newEmail.isEmpty()) {
                Toast.makeText(this, "請填寫完整資訊", Toast.LENGTH_SHORT).show(); return;
            }
            Map<String, String> body = new HashMap<>();
            body.put("currentPassword", pwd);
            body.put("newEmail", newEmail);
            api.updateEmail(currentUserId, body).enqueue(new SimpleCallback("Email 更新成功", true));
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

            Map<String, String> body = new HashMap<>();
            body.put("currentPassword", currentPwd);
            body.put("newPassword", newPwd);
            api.updatePassword(currentUserId, body).enqueue(new SimpleCallback("密碼更新成功，請重新登入", false));
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
        api.getUserProfile(currentUserId).enqueue(new Callback<Map<String, String>>() {
            @Override
            public void onResponse(Call<Map<String, String>> call, Response<Map<String, String>> response) {
                if(response.isSuccessful() && response.body() != null) {
                    tvUsername.setText(response.body().get("username"));
                    tvEmail.setText(response.body().get("email"));

                    // 處理 MongoDB 傳回的 ISO 時間格式 (擷取前面的日期)
                    String dateStr = response.body().get("joinDate");
                    if(dateStr != null && dateStr.length() >= 10) {
                        tvJoinDate.setText(dateStr.substring(0, 10)); // 顯示 YYYY-MM-DD
                    }
                }
            }
            @Override public void onFailure(Call<Map<String, String>> call, Throwable t) {}
        });
    }

    // 刪除帳號的警告彈窗
    private void showDeleteAccountDialog() {
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("⚠️ 警告：刪除帳號")
                .setMessage("您確定要永久刪除帳號嗎？\n\n這將會清除您所有的地圖日記與好友紀錄，且無法復原！")
                .setPositiveButton("確認刪除", (dialog, which) -> {
                    api.deleteAccount(currentUserId).enqueue(new Callback<ResponseBody>() {
                        @Override
                        public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                            if(response.isSuccessful()) {
                                Toast.makeText(SettingsActivity.this, "帳號已永久刪除", Toast.LENGTH_LONG).show();
                                logoutUser();
                            }
                        }
                        @Override public void onFailure(Call<ResponseBody> call, Throwable t) {}
                    });
                })
                .setNegativeButton("取消", null)
                .show();
    }

    // 登出並返回登入頁
    private void logoutUser() {
        getSharedPreferences("UserData", MODE_PRIVATE).edit().clear().apply();
        Intent intent = new Intent(this, LoginActivity.class); // 換成你的登入頁面類別
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
    }

    // 共用的 Retrofit 回呼處理類別
    private class SimpleCallback implements Callback<ResponseBody> {
        private String successMsg;
        private boolean reloadData;

        SimpleCallback(String successMsg, boolean reloadData) {
            this.successMsg = successMsg;
            this.reloadData = reloadData;
        }

        @Override
        public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
            if(response.isSuccessful()) {
                Toast.makeText(SettingsActivity.this, successMsg, Toast.LENGTH_SHORT).show();
                if(reloadData) {
                    loadUserProfile(); // 刷新畫面的基本資料
                    // 操作成功後將所有面板收合
                    layoutEditName.setVisibility(View.GONE);
                    layoutEditEmail.setVisibility(View.GONE);
                    layoutEditPassword.setVisibility(View.GONE);
                    // 清空輸入框
                    etNewName.setText(""); etNewEmail.setText(""); etCurrentPwdForEmail.setText("");
                } else {
                    // 通常是改密碼成功，強制登出重登
                    logoutUser();
                }
            } else {
                Toast.makeText(SettingsActivity.this, "操作失敗，請確認密碼是否正確", Toast.LENGTH_SHORT).show();
            }
        }
        @Override public void onFailure(Call<ResponseBody> call, Throwable t) {
            Toast.makeText(SettingsActivity.this, "網路錯誤", Toast.LENGTH_SHORT).show();
        }
    }
}