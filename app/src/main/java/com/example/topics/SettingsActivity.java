package com.example.topics;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.util.Patterns;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import com.bumptech.glide.Glide;
import com.example.topics.data.local.SessionManager;
import com.example.topics.data.model.EmptyResponse;
import com.example.topics.data.model.UserDto;
import com.example.topics.data.remote.ImageUrlResolver;
import com.example.topics.data.repository.RepositoryCallback;
import com.example.topics.data.repository.UserRepository;
import com.example.topics.ui.common.AppNavigator;
import com.example.topics.ui.design.AdriftSystemUi;

public class SettingsActivity extends AppCompatActivity {

    private UserRepository userRepository;
    private SessionManager sessionManager;

    // UI 元件
    private TextView tvUsername, tvEmail, tvJoinDate, tvRoleBadge;
    private ImageView ivAvatar;
    private ActivityResultLauncher<Intent> avatarPickerLauncher;
    private LinearLayout layoutEditName, layoutEditEmail, layoutEditPassword;
    private EditText etNewName, etCurrentPwdForEmail, etNewEmail, etCurrentPwd, etNewPwd, etConfirmPwd;
    private Button btnSaveName, btnSaveEmail, btnSavePassword;
    private boolean settingsBusy;
    private boolean destroyed;

    // 分頁
    private TextView tabProfile, tabSecurity, tabDanger;
    private View contentProfile, contentSecurity, contentDanger;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        AdriftSystemUi.apply(this);

        userRepository = new UserRepository(this);
        sessionManager = SessionManager.getInstance(this);

        initViews();
        avatarPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null && result.getData().getData() != null) {
                        Uri uri = result.getData().getData();
                        uploadAvatar(uri);
                    }
                }
        );
        if (ivAvatar != null) {
            ivAvatar.setOnClickListener(v -> {
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.setType("image/*");
                avatarPickerLauncher.launch(Intent.createChooser(intent, "選擇頭貼"));
            });
        }
        loadUserProfile();
        setupClickListeners();
        selectSettingsTab("profile");
    }

    private void initViews() {
        tvUsername = findViewById(R.id.tv_settings_username);
        tvEmail = findViewById(R.id.tv_settings_email);
        tvJoinDate = findViewById(R.id.tv_settings_join_date);
        tvRoleBadge = findViewById(R.id.tv_settings_role_badge);
        ivAvatar = findViewById(R.id.iv_settings_avatar);

        // 分頁按鈕與內容容器
        tabProfile = findViewById(R.id.tab_settings_profile);
        tabSecurity = findViewById(R.id.tab_settings_security);
        tabDanger = findViewById(R.id.tab_settings_danger);
        contentProfile = findViewById(R.id.tab_content_profile);
        contentSecurity = findViewById(R.id.tab_content_security);
        contentDanger = findViewById(R.id.tab_content_danger);

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
        btnSaveName = findViewById(R.id.btn_save_name);
        btnSaveEmail = findViewById(R.id.btn_save_email);
        btnSavePassword = findViewById(R.id.btn_save_password);
    }

    private void setupClickListeners() {
        // 返回按鈕
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        // 分頁切換
        if (tabProfile != null) tabProfile.setOnClickListener(v -> selectSettingsTab("profile"));
        if (tabSecurity != null) tabSecurity.setOnClickListener(v -> selectSettingsTab("security"));
        if (tabDanger != null) tabDanger.setOnClickListener(v -> selectSettingsTab("danger"));

        // 面板展開/收合控制 (點擊標題展開，其他收合)
        findViewById(R.id.header_edit_name).setOnClickListener(v -> togglePanel(layoutEditName));
        findViewById(R.id.header_edit_email).setOnClickListener(v -> togglePanel(layoutEditEmail));
        findViewById(R.id.header_edit_password).setOnClickListener(v -> togglePanel(layoutEditPassword));

        // 1. 儲存新名稱
        btnSaveName.setOnClickListener(v -> {
            if (settingsBusy) return;
            String newName = etNewName.getText().toString().trim();
            if (newName.isEmpty()) {
                Toast.makeText(this, "請輸入新的名稱", Toast.LENGTH_SHORT).show();
                return;
            }
            setSettingsBusy(btnSaveName, true, "儲存中", "儲存新名稱");
            userRepository.updateName(newName, new UserCallback("名稱更新成功", btnSaveName, "儲存新名稱"));
        });

        // 2. 更新 Email
        btnSaveEmail.setOnClickListener(v -> {
            if (settingsBusy) return;
            String pwd = etCurrentPwdForEmail.getText().toString();
            String newEmail = etNewEmail.getText().toString().trim();
            if(pwd.isEmpty() || newEmail.isEmpty()) {
                Toast.makeText(this, "請填寫完整資訊", Toast.LENGTH_SHORT).show(); return;
            }
            if (!Patterns.EMAIL_ADDRESS.matcher(newEmail).matches()) {
                Toast.makeText(this, "請輸入有效的電子郵件", Toast.LENGTH_SHORT).show();
                return;
            }
            setSettingsBusy(btnSaveEmail, true, "更新中", "驗證並更新 Email");
            userRepository.updateEmail(newEmail, pwd, new UserCallback("Email 更新成功", btnSaveEmail, "驗證並更新 Email"));
        });

        // 3. 更新密碼
        btnSavePassword.setOnClickListener(v -> {
            if (settingsBusy) return;
            String currentPwd = etCurrentPwd.getText().toString();
            String newPwd = etNewPwd.getText().toString();
            String confirmPwd = etConfirmPwd.getText().toString();

            if(!newPwd.equals(confirmPwd)) {
                Toast.makeText(this, "兩次輸入的新密碼不一致！", Toast.LENGTH_SHORT).show(); return;
            }
            if(newPwd.length() < 6) {
                Toast.makeText(this, "新密碼至少需 6 個字元", Toast.LENGTH_SHORT).show(); return;
            }

            setSettingsBusy(btnSavePassword, true, "更新中", "更新密碼");
            userRepository.updatePassword(currentPwd, newPwd, confirmPwd, new RepositoryCallback<EmptyResponse>() {
                @Override
                public void onSuccess(EmptyResponse value) {
                    if (!isActive()) return;
                    Toast.makeText(SettingsActivity.this, "密碼更新成功，請重新登入", Toast.LENGTH_SHORT).show();
                    logoutUser();
                }

                @Override
                public void onError(String message) {
                    if (!isActive()) return;
                    setSettingsBusy(btnSavePassword, false, "更新中", "更新密碼");
                    Toast.makeText(SettingsActivity.this, message, Toast.LENGTH_SHORT).show();
                }
            });
        });

        // 4. 危險區域：刪除帳號
        findViewById(R.id.header_danger_zone).setOnClickListener(v -> showDeleteAccountDialog());
        findViewById(R.id.header_logout).setOnClickListener(v -> showLogoutDialog());
        findViewById(R.id.settings_nav_map).setOnClickListener(v -> {
            AppNavigator.openTopLevel(this, MapActivity.class);
        });
        findViewById(R.id.settings_nav_friends).setOnClickListener(v -> AppNavigator.openTopLevel(this, FriendsActivity.class));
        findViewById(R.id.settings_nav_explore).setOnClickListener(v -> AppNavigator.openTopLevel(this, ExploreActivity.class));
    }

    // 分頁切換
    private void selectSettingsTab(String tab) {
        boolean isProfile = "profile".equals(tab);
        boolean isSecurity = "security".equals(tab);
        boolean isDanger = "danger".equals(tab);

        if (contentProfile != null) contentProfile.setVisibility(isProfile ? View.VISIBLE : View.GONE);
        if (contentSecurity != null) contentSecurity.setVisibility(isSecurity ? View.VISIBLE : View.GONE);
        if (contentDanger != null) contentDanger.setVisibility(isDanger ? View.VISIBLE : View.GONE);

        updateTabStyle(tabProfile, isProfile);
        updateTabStyle(tabSecurity, isSecurity);
        updateTabStyle(tabDanger, isDanger);
    }

    private void updateTabStyle(TextView tab, boolean selected) {
        if (tab == null) return;
        if (selected) {
            tab.setBackgroundResource(R.drawable.bg_login_card);
            tab.setTextColor(getResources().getColor(R.color.white));
        } else {
            tab.setBackground(null);
            tab.setTextColor(getResources().getColor(R.color.hint_text));
        }
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
                if (!isActive()) return;
                tvUsername.setText(user.getDisplayName());
                tvEmail.setText(user.email == null ? "" : user.email);
                if(user.createdAt != null && user.createdAt.length() >= 10) {
                    tvJoinDate.setText(user.createdAt.substring(0, 10));
                }
                bindAvatar(user);
                bindRoleBadge(user);
            }

            @Override
            public void onError(String message) {
                if (!isActive()) return;
                Toast.makeText(SettingsActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void bindAvatar(UserDto user) {
        if (ivAvatar == null) return;
        if (user.avatar != null && !user.avatar.trim().isEmpty()) {
            Glide.with(SettingsActivity.this)
                    .load(ImageUrlResolver.resolve(user.avatar))
                    .placeholder(R.drawable.bg_adrift_avatar)
                    .error(R.drawable.bg_adrift_avatar)
                    .centerCrop()
                    .into(ivAvatar);
        } else {
            Glide.with(SettingsActivity.this).clear(ivAvatar);
            ivAvatar.setImageDrawable(null);
        }
    }

    private void bindRoleBadge(UserDto user) {
        if (tvRoleBadge == null) return;
        String role = user.role == null ? "user" : user.role;
        boolean isPrivileged = "admin".equals(role) || "owner".equals(role);
        tvRoleBadge.setVisibility(isPrivileged ? View.VISIBLE : View.GONE);
        tvRoleBadge.setText(role.toUpperCase());
    }

    private void uploadAvatar(Uri uri) {
        userRepository.updateAvatar(this, uri, new RepositoryCallback<UserDto>() {
            @Override
            public void onSuccess(UserDto user) {
                if (!isActive()) return;
                Toast.makeText(SettingsActivity.this, "頭貼已更新", Toast.LENGTH_SHORT).show();
                loadUserProfile();
            }

            @Override
            public void onError(String message) {
                if (!isActive()) return;
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

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("⚠️ 警告：刪除帳號")
                .setMessage("您確定要永久刪除帳號嗎？\n\n這將會清除您所有的地圖日記與好友紀錄，且無法復原！")
                .setView(fields)
                .setPositiveButton("確認刪除", null)
                .setNegativeButton("取消", null)
                .create();
        dialog.setOnShowListener(shown -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            if (settingsBusy) return;
            String password = passwordInput.getText().toString();
            String confirm = confirmInput.getText().toString().trim();
            if (!"DELETE".equals(confirm)) {
                Toast.makeText(SettingsActivity.this, "請輸入 DELETE 確認刪除", Toast.LENGTH_SHORT).show();
                return;
            }
            setSettingsBusy(dialog.getButton(AlertDialog.BUTTON_POSITIVE), true, "刪除中", "確認刪除");
            userRepository.deleteAccount(password, new RepositoryCallback<EmptyResponse>() {
                @Override
                public void onSuccess(EmptyResponse value) {
                    if (!isActive()) return;
                    Toast.makeText(SettingsActivity.this, "帳號已永久刪除", Toast.LENGTH_LONG).show();
                    dialog.dismiss();
                    logoutUser();
                }

                @Override
                public void onError(String message) {
                    if (!isActive()) return;
                    setSettingsBusy(dialog.getButton(AlertDialog.BUTTON_POSITIVE), false, "刪除中", "確認刪除");
                    Toast.makeText(SettingsActivity.this, message, Toast.LENGTH_SHORT).show();
                }
            });
        }));
        dialog.show();
    }

    // 登出並返回登入頁
    private void logoutUser() {
        sessionManager.clear();
        AppNavigator.openLoginAndClear(this);
    }

    private void showLogoutDialog() {
        new AlertDialog.Builder(this)
                .setTitle("登出")
                .setMessage("確定要登出目前帳號嗎？")
                .setPositiveButton("登出", (dialog, which) -> logoutUser())
                .setNegativeButton("取消", null)
                .show();
    }

    private class UserCallback implements RepositoryCallback<UserDto> {
        private final String successMsg;
        private final Button sourceButton;
        private final String idleText;

        UserCallback(String successMsg, Button sourceButton, String idleText) {
            this.successMsg = successMsg;
            this.sourceButton = sourceButton;
            this.idleText = idleText;
        }
        @Override
        public void onSuccess(UserDto value) {
            if (!isActive()) return;
            setSettingsBusy(sourceButton, false, "", idleText);
            Toast.makeText(SettingsActivity.this, successMsg, Toast.LENGTH_SHORT).show();
            loadUserProfile();
            layoutEditName.setVisibility(View.GONE);
            layoutEditEmail.setVisibility(View.GONE);
            layoutEditPassword.setVisibility(View.GONE);
            etNewName.setText(""); etNewEmail.setText(""); etCurrentPwdForEmail.setText("");
        }

        @Override
        public void onError(String message) {
            if (!isActive()) return;
            setSettingsBusy(sourceButton, false, "", idleText);
            Toast.makeText(SettingsActivity.this, message, Toast.LENGTH_SHORT).show();
        }
    }

    private void setSettingsBusy(Button button, boolean busy, String busyText, String idleText) {
        settingsBusy = busy;
        if (button != null) {
            button.setEnabled(!busy);
            button.setText(busy ? busyText : idleText);
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
}