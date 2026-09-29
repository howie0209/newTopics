package com.example.topics;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.Patterns;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
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
import com.example.topics.ui.settings.AvatarCropView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class SettingsActivity extends AppCompatActivity {

    private static final long MAX_AVATAR_BYTES = 10 * 1024 * 1024;

    private UserRepository userRepository;
    private SessionManager sessionManager;

    // 頂部摘要卡
    private TextView tvUsername, tvUsercode, tvRoleBadge;
    private ImageView ivAvatar;

    // 個人檔案分頁內容
    private ImageView ivAvatarLarge;
    private Button btnUploadAvatar, btnRemoveAvatar;
    private TextView tvProfileRowName, tvSettingsEmail, tvProfileRowUsercode, tvProfileRowRole, tvSettingsJoinDate;
    private Button btnEditName, btnEditEmail, btnCopyUsercode;

    private ActivityResultLauncher<Intent> avatarPickerLauncher;
    private LinearLayout layoutEditName, layoutEditEmail, layoutEditPassword;
    private EditText etNewName, etCurrentPwdForEmail, etNewEmail, etCurrentPwd, etNewPwd, etConfirmPwd;
    private Button btnSaveName, btnSaveEmail, btnSavePassword;
    private Button btnCancelName, btnCancelEmail, btnCancelPassword;
    private boolean settingsBusy;
    private boolean destroyed;

    private String originalName = "";
    private String originalEmail = "";

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
                        handleAvatarSelected(result.getData().getData());
                    }
                }
        );

        View.OnClickListener pickAvatar = v -> {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("image/*");
            avatarPickerLauncher.launch(Intent.createChooser(intent, "選擇頭貼"));
        };
        if (ivAvatar != null) ivAvatar.setOnClickListener(pickAvatar);
        if (ivAvatarLarge != null) ivAvatarLarge.setOnClickListener(pickAvatar);
        if (btnUploadAvatar != null) btnUploadAvatar.setOnClickListener(pickAvatar);
        if (btnRemoveAvatar != null) btnRemoveAvatar.setOnClickListener(v -> removeAvatar());

        setupDirtyCheck();
        loadUserProfile();
        setupClickListeners();
        selectSettingsTab("profile");
    }

    private void initViews() {
        tvUsername = findViewById(R.id.tv_settings_username);
        tvUsercode = findViewById(R.id.tv_settings_usercode);
        tvRoleBadge = findViewById(R.id.tv_settings_role_badge);
        ivAvatar = findViewById(R.id.iv_settings_avatar);

        ivAvatarLarge = findViewById(R.id.iv_settings_avatar_large);
        btnUploadAvatar = findViewById(R.id.btn_upload_avatar);
        btnRemoveAvatar = findViewById(R.id.btn_remove_avatar);
        tvProfileRowName = findViewById(R.id.tv_profile_row_name);
        tvSettingsEmail = findViewById(R.id.tv_settings_email);
        tvProfileRowUsercode = findViewById(R.id.tv_profile_row_usercode);
        tvProfileRowRole = findViewById(R.id.tv_profile_row_role);
        tvSettingsJoinDate = findViewById(R.id.tv_settings_join_date);
        btnEditName = findViewById(R.id.btn_edit_name);
        btnEditEmail = findViewById(R.id.btn_edit_email);
        btnCopyUsercode = findViewById(R.id.btn_copy_usercode);

        tabProfile = findViewById(R.id.tab_settings_profile);
        tabSecurity = findViewById(R.id.tab_settings_security);
        tabDanger = findViewById(R.id.tab_settings_danger);
        contentProfile = findViewById(R.id.tab_content_profile);
        contentSecurity = findViewById(R.id.tab_content_security);
        contentDanger = findViewById(R.id.tab_content_danger);

        layoutEditName = findViewById(R.id.layout_edit_name);
        layoutEditEmail = findViewById(R.id.layout_edit_email);
        layoutEditPassword = findViewById(R.id.layout_edit_password);

        etNewName = findViewById(R.id.et_new_name);
        etCurrentPwdForEmail = findViewById(R.id.et_current_pwd_for_email);
        etNewEmail = findViewById(R.id.et_new_email);
        etCurrentPwd = findViewById(R.id.et_current_pwd);
        etNewPwd = findViewById(R.id.et_new_pwd);
        etConfirmPwd = findViewById(R.id.et_confirm_pwd);
        btnSaveName = findViewById(R.id.btn_save_name);
        btnSaveEmail = findViewById(R.id.btn_save_email);
        btnSavePassword = findViewById(R.id.btn_save_password);
        btnCancelName = findViewById(R.id.btn_cancel_name);
        btnCancelEmail = findViewById(R.id.btn_cancel_email);
        btnCancelPassword = findViewById(R.id.btn_cancel_password);
    }

    // ---- dirty check：名稱/Email 沒改過就 disable 儲存按鈕 ----
    private void setupDirtyCheck() {
        if (etNewName != null) {
            etNewName.addTextChangedListener(new SimpleWatcher(() -> {
                if (btnSaveName != null) {
                    btnSaveName.setEnabled(!etNewName.getText().toString().trim().equals(originalName)
                            && !etNewName.getText().toString().trim().isEmpty());
                }
            }));
        }
        if (etNewEmail != null) {
            SimpleWatcher.Callback emailCheck = () -> {
                if (btnSaveEmail != null) {
                    boolean dirty = !etNewEmail.getText().toString().trim().equalsIgnoreCase(originalEmail);
                    boolean hasPwd = etCurrentPwdForEmail != null && !etCurrentPwdForEmail.getText().toString().isEmpty();
                    btnSaveEmail.setEnabled(dirty && hasPwd);
                }
            };
            etNewEmail.addTextChangedListener(new SimpleWatcher(emailCheck));
            if (etCurrentPwdForEmail != null) etCurrentPwdForEmail.addTextChangedListener(new SimpleWatcher(emailCheck));
        }
    }

    private static class SimpleWatcher implements TextWatcher {
        interface Callback { void run(); }
        private final Callback callback;
        SimpleWatcher(Callback callback) { this.callback = callback; }
        @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
        @Override public void onTextChanged(CharSequence s, int start, int before, int count) { callback.run(); }
        @Override public void afterTextChanged(Editable s) {}
    }

    private void setupClickListeners() {
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        if (tabProfile != null) tabProfile.setOnClickListener(v -> selectSettingsTab("profile"));
        if (tabSecurity != null) tabSecurity.setOnClickListener(v -> selectSettingsTab("security"));
        if (tabDanger != null) tabDanger.setOnClickListener(v -> selectSettingsTab("danger"));

        if (btnEditName != null) btnEditName.setOnClickListener(v -> togglePanel(layoutEditName));
        if (btnEditEmail != null) btnEditEmail.setOnClickListener(v -> togglePanel(layoutEditEmail));
        findViewById(R.id.header_edit_password).setOnClickListener(v -> togglePanel(layoutEditPassword));

        if (btnCancelName != null) btnCancelName.setOnClickListener(v -> {
            etNewName.setText(originalName);
            layoutEditName.setVisibility(View.GONE);
        });
        if (btnCancelEmail != null) btnCancelEmail.setOnClickListener(v -> {
            etNewEmail.setText(originalEmail);
            etCurrentPwdForEmail.setText("");
            layoutEditEmail.setVisibility(View.GONE);
        });
        if (btnCancelPassword != null) btnCancelPassword.setOnClickListener(v -> {
            etCurrentPwd.setText("");
            etNewPwd.setText("");
            etConfirmPwd.setText("");
            layoutEditPassword.setVisibility(View.GONE);
        });

        if (btnCopyUsercode != null) {
            btnCopyUsercode.setOnClickListener(v -> {
                ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                clipboard.setPrimaryClip(ClipData.newPlainText("userCode", tvProfileRowUsercode.getText()));
                Toast.makeText(this, "已複製", Toast.LENGTH_SHORT).show();
            });
        }

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

        findViewById(R.id.header_danger_zone).setOnClickListener(v -> showDeleteAccountDialog());
        findViewById(R.id.header_logout).setOnClickListener(v -> showLogoutDialog());
        findViewById(R.id.settings_nav_map).setOnClickListener(v -> AppNavigator.openTopLevel(this, MapActivity.class));
        findViewById(R.id.settings_nav_friends).setOnClickListener(v -> AppNavigator.openTopLevel(this, FriendsActivity.class));
        findViewById(R.id.settings_nav_explore).setOnClickListener(v -> AppNavigator.openTopLevel(this, ExploreActivity.class));
    }

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

    private void togglePanel(LinearLayout panelToToggle) {
        layoutEditName.setVisibility(View.GONE);
        layoutEditEmail.setVisibility(View.GONE);
        layoutEditPassword.setVisibility(View.GONE);
        panelToToggle.setVisibility(View.VISIBLE);
    }

    private void loadUserProfile() {
        userRepository.getMe(new RepositoryCallback<UserDto>() {
            @Override
            public void onSuccess(UserDto user) {
                if (!isActive()) return;

                String displayName = user.getDisplayName();
                String userCode = user.getUserCode();

                originalName = displayName;
                originalEmail = user.email == null ? "" : user.email;

                tvUsername.setText(displayName);
                if (tvUsercode != null) tvUsercode.setText(userCode.isEmpty() ? "@adrift" : "@" + userCode);

                if (tvProfileRowName != null) tvProfileRowName.setText(displayName);
                if (tvSettingsEmail != null) tvSettingsEmail.setText(originalEmail);
                if (tvProfileRowUsercode != null) tvProfileRowUsercode.setText(userCode.isEmpty() ? "@adrift" : "@" + userCode);

                if (etNewName != null) etNewName.setText(displayName);
                if (etNewEmail != null) etNewEmail.setText(originalEmail);

                String role = user.role == null ? "user" : user.role;
                if (tvProfileRowRole != null) tvProfileRowRole.setText(capitalize(role));
                boolean isPrivileged = "admin".equals(role) || "owner".equals(role);
                if (tvRoleBadge != null) {
                    tvRoleBadge.setVisibility(isPrivileged ? View.VISIBLE : View.GONE);
                    tvRoleBadge.setText(role.toUpperCase());
                }

                if(user.createdAt != null && user.createdAt.length() >= 10 && tvSettingsJoinDate != null) {
                    tvSettingsJoinDate.setText(user.createdAt.substring(0, 10));
                }

                bindAvatar(user);

                if (btnSaveName != null) btnSaveName.setEnabled(false);
                if (btnSaveEmail != null) btnSaveEmail.setEnabled(false);
            }

            @Override
            public void onError(String message) {
                if (!isActive()) return;
                Toast.makeText(SettingsActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private String capitalize(String value) {
        if (value == null || value.isEmpty()) return value;
        return value.substring(0, 1).toUpperCase() + value.substring(1);
    }

    private void bindAvatar(UserDto user) {
        boolean hasAvatar = user.avatar != null && !user.avatar.trim().isEmpty();
        ImageView[] targets = {ivAvatar, ivAvatarLarge};
        for (ImageView target : targets) {
            if (target == null) continue;
            if (hasAvatar) {
                Glide.with(SettingsActivity.this)
                        .load(ImageUrlResolver.resolve(user.avatar))
                        .placeholder(R.drawable.bg_adrift_avatar)
                        .error(R.drawable.bg_adrift_avatar)
                        .centerCrop()
                        .into(target);
            } else {
                Glide.with(SettingsActivity.this).clear(target);
                target.setImageDrawable(null);
            }
        }
        if (btnRemoveAvatar != null) btnRemoveAvatar.setVisibility(hasAvatar ? View.VISIBLE : View.GONE);
    }

    // ---- 選圖後先驗證，再進裁切畫面 ----
    private void handleAvatarSelected(Uri uri) {
        String mimeType = getContentResolver().getType(uri);
        if (mimeType == null || !(mimeType.equals("image/jpeg") || mimeType.equals("image/png") || mimeType.equals("image/webp"))) {
            Toast.makeText(this, "頭貼僅支援 JPG、PNG、WebP", Toast.LENGTH_SHORT).show();
            return;
        }

        long size = queryFileSize(uri);
        if (size > MAX_AVATAR_BYTES) {
            Toast.makeText(this, "頭貼檔案不可超過 10MB", Toast.LENGTH_SHORT).show();
            return;
        }

        Bitmap bitmap = decodeBitmap(uri);
        if (bitmap == null) {
            Toast.makeText(this, "無法讀取圖片", Toast.LENGTH_SHORT).show();
            return;
        }
        if (bitmap.getWidth() < 128 || bitmap.getHeight() < 128) {
            Toast.makeText(this, "圖片尺寸太小，請上傳至少 128x128 的圖片", Toast.LENGTH_SHORT).show();
            return;
        }

        showAvatarCropDialog(bitmap);
    }

    private long queryFileSize(Uri uri) {
        try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.SIZE);
                if (index >= 0 && !cursor.isNull(index)) return cursor.getLong(index);
            }
        }
        return -1L;
    }

    private Bitmap decodeBitmap(Uri uri) {
        try (InputStream input = getContentResolver().openInputStream(uri)) {
            return BitmapFactory.decodeStream(input);
        } catch (IOException e) {
            return null;
        }
    }

    // ---- 頭貼裁切對話框（拖曳 + 縮放）----
    private void showAvatarCropDialog(Bitmap bitmap) {
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(dp(24), dp(16), dp(24), dp(8));

        TextView hint = new TextView(this);
        hint.setText("拖曳圖片調整位置，使用滑桿調整縮放");
        hint.setTextColor(getResources().getColor(R.color.hint_text));
        hint.setTextSize(12);
        container.addView(hint);

        FrameLayout cropFrame = new FrameLayout(this);
        LinearLayout.LayoutParams cropParams = new LinearLayout.LayoutParams(dp(260), dp(260));
        cropParams.topMargin = dp(16);
        cropParams.gravity = Gravity.CENTER_HORIZONTAL;
        cropFrame.setLayoutParams(cropParams);

        AvatarCropView cropView = new AvatarCropView(this);
        cropView.setLayoutParams(new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        cropView.setBitmap(bitmap);
        cropFrame.addView(cropView);
        container.addView(cropFrame);

        TextView zoomLabel = new TextView(this);
        zoomLabel.setText("縮放");
        zoomLabel.setTextColor(getResources().getColor(R.color.white));
        zoomLabel.setTextSize(12);
        LinearLayout.LayoutParams zoomLabelParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        zoomLabelParams.topMargin = dp(16);
        zoomLabel.setLayoutParams(zoomLabelParams);
        container.addView(zoomLabel);

        SeekBar zoomSeekBar = new SeekBar(this);
        zoomSeekBar.setMax(200);
        zoomSeekBar.setProgress(0);
        zoomSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                cropView.setZoom(1f + progress / 100f);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        container.addView(zoomSeekBar);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("調整頭貼")
                .setView(container)
                .setPositiveButton("儲存頭貼", null)
                .setNegativeButton("取消", null)
                .create();

        dialog.setOnShowListener(shown -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            Bitmap cropped = cropView.getCroppedBitmap(512);
            dialog.dismiss();
            uploadCroppedAvatar(cropped);
        }));
        dialog.show();
    }

    private void uploadCroppedAvatar(Bitmap bitmap) {
        try {
            File file = new File(getCacheDir(), "avatar-" + System.currentTimeMillis() + ".jpg");
            try (FileOutputStream out = new FileOutputStream(file)) {
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out);
            }
            uploadAvatar(Uri.fromFile(file));
        } catch (IOException e) {
            Toast.makeText(this, "頭貼裁切失敗", Toast.LENGTH_SHORT).show();
        }
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
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

    private void removeAvatar() {
        userRepository.deleteAvatar(new RepositoryCallback<UserDto>() {
            @Override
            public void onSuccess(UserDto user) {
                if (!isActive()) return;
                Toast.makeText(SettingsActivity.this, "頭貼已移除", Toast.LENGTH_SHORT).show();
                loadUserProfile();
            }

            @Override
            public void onError(String message) {
                if (!isActive()) return;
                Toast.makeText(SettingsActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

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
            etCurrentPwdForEmail.setText("");
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