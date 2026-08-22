package com.example.topics;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.topics.data.local.SessionManager;
import com.example.topics.data.model.DiaryStatsDto;
import com.example.topics.data.model.EmptyResponse;
import com.example.topics.data.model.FriendProfileDto;
import com.example.topics.data.model.UserDto;
import com.example.topics.data.repository.FriendRepository;
import com.example.topics.data.repository.RepositoryCallback;
import com.example.topics.ui.common.AvatarBinder;
import com.example.topics.ui.design.AdriftSystemUi;

public class ProfileActivity extends AppCompatActivity {
    public static final String EXTRA_USER_ID = "user_id";
    public static final String EXTRA_NAME = "name";
    public static final String EXTRA_USER_CODE = "user_code";
    public static final String EXTRA_AVATAR = "avatar";
    public static final String EXTRA_STATUS = "friendship_status";
    public static final String EXTRA_REQUEST_ID = "request_id";

    private FriendRepository friendRepository;
    private SessionManager sessionManager;
    private ImageView avatarImage;
    private TextView avatarFallback;
    private TextView nameView;
    private TextView codeView;
    private TextView statusView;
    private TextView statsView;
    private Button primaryButton;
    private String userId;
    private String name;
    private String userCode;
    private String avatar;
    private String friendshipStatus;
    private String requestId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);
        AdriftSystemUi.apply(this);
        friendRepository = new FriendRepository(this);
        sessionManager = SessionManager.getInstance(this);
        if (!sessionManager.hasToken()) {
            openLogin();
            return;
        }
        readExtras();
        bindViews();
        renderPreview();
        loadFormalProfileIfAvailable();
    }

    private void readExtras() {
        Intent intent = getIntent();
        userId = value(intent.getStringExtra(EXTRA_USER_ID));
        name = value(intent.getStringExtra(EXTRA_NAME));
        userCode = value(intent.getStringExtra(EXTRA_USER_CODE));
        avatar = value(intent.getStringExtra(EXTRA_AVATAR));
        friendshipStatus = value(intent.getStringExtra(EXTRA_STATUS));
        requestId = value(intent.getStringExtra(EXTRA_REQUEST_ID));
        if (same(userId, sessionManager.getUserId())) friendshipStatus = "self";
    }

    private void bindViews() {
        findViewById(R.id.btn_profile_back).setOnClickListener(v -> finish());
        avatarImage = findViewById(R.id.iv_profile_avatar);
        avatarFallback = findViewById(R.id.tv_profile_avatar);
        nameView = findViewById(R.id.tv_profile_name);
        codeView = findViewById(R.id.tv_profile_code);
        statusView = findViewById(R.id.tv_profile_status);
        statsView = findViewById(R.id.tv_profile_diary_stats);
        primaryButton = findViewById(R.id.btn_profile_primary);
    }

    private void renderPreview() {
        if ("self".equals(friendshipStatus)) {
            UserDto user = sessionManager.getUser();
            if (user != null) {
                userId = user.getId();
                name = user.getDisplayName();
                userCode = user.getUserCode();
                avatar = user.avatar;
            }
        }
        nameView.setText(name.isEmpty() ? "使用者" : name);
        codeView.setText(userCode.isEmpty() ? "" : "@" + userCode);
        AvatarBinder.bind(avatarImage, avatarFallback, name, avatar);
        configureAction();
    }

    private void configureAction() {
        if ("self".equals(friendshipStatus)) {
            statusView.setText("這是你的 Adrift 帳號");
            statsView.setText("你的私人、好友與公開日記會顯示在地圖中。");
            primaryButton.setText("開啟設定");
            primaryButton.setVisibility(View.VISIBLE);
            primaryButton.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
            return;
        }
        if ("friend".equals(friendshipStatus)) {
            statusView.setText("已是好友");
            statsView.setText("讀取好友可見日記統計中");
            primaryButton.setText("回到好友");
            primaryButton.setVisibility(View.VISIBLE);
            primaryButton.setOnClickListener(v -> finish());
            return;
        }
        if ("sent_request".equals(friendshipStatus)) {
            statusView.setText("好友邀請已送出");
            statsView.setText("對方接受邀請後，Android 會透過正式好友 profile API 顯示更多資訊。");
            primaryButton.setText("等待回覆");
            primaryButton.setEnabled(false);
            return;
        }
        if ("received_request".equals(friendshipStatus)) {
            statusView.setText("對方已邀請你");
            statsView.setText("接受邀請後即可查看好友 profile 統計。");
            primaryButton.setText("接受邀請");
            primaryButton.setVisibility(View.VISIBLE);
            primaryButton.setOnClickListener(v -> acceptRequest());
            return;
        }
        statusView.setText("尚未成為好友");
        statsView.setText("Backend 目前未提供非好友 profile detail，這裡只顯示搜尋結果中的公開欄位。");
        primaryButton.setText("加好友");
        primaryButton.setVisibility(View.VISIBLE);
        primaryButton.setOnClickListener(v -> sendFriendRequest());
    }

    private void loadFormalProfileIfAvailable() {
        if (!"friend".equals(friendshipStatus) || userId.isEmpty()) return;
        friendRepository.getFriendProfile(userId, new RepositoryCallback<FriendProfileDto>() {
            @Override
            public void onSuccess(FriendProfileDto value) {
                if (value == null) return;
                name = value.getDisplayName();
                userCode = value.userCode == null ? "" : value.userCode;
                avatar = value.avatar == null ? "" : value.avatar;
                nameView.setText(name);
                codeView.setText(userCode.isEmpty() ? "" : "@" + userCode);
                AvatarBinder.bind(avatarImage, avatarFallback, name, avatar);
                renderStats(value.diaryStats);
            }

            @Override
            public void onError(String message) {
                statsView.setText(message);
            }
        });
    }

    private void renderStats(DiaryStatsDto stats) {
        if (stats == null) {
            statsView.setText("Backend 未回傳日記統計");
            return;
        }
        statsView.setText("公開日記 " + stats.publicCount + " 篇 · 好友可見 " + stats.friendsCount + " 篇");
    }

    private void sendFriendRequest() {
        setBusy("送出中");
        friendRepository.sendFriendRequest(userId, new RepositoryCallback<EmptyResponse>() {
            @Override
            public void onSuccess(EmptyResponse value) {
                friendshipStatus = "sent_request";
                primaryButton.setEnabled(true);
                Toast.makeText(ProfileActivity.this, "好友邀請已送出", Toast.LENGTH_SHORT).show();
                configureAction();
            }

            @Override
            public void onError(String message) {
                primaryButton.setEnabled(true);
                primaryButton.setText("加好友");
                Toast.makeText(ProfileActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void acceptRequest() {
        if (requestId.isEmpty()) {
            startActivity(new Intent(this, FriendsActivity.class));
            finish();
            return;
        }
        setBusy("接受中");
        friendRepository.acceptRequest(requestId, new RepositoryCallback<EmptyResponse>() {
            @Override
            public void onSuccess(EmptyResponse value) {
                friendshipStatus = "friend";
                primaryButton.setEnabled(true);
                Toast.makeText(ProfileActivity.this, "已成為好友", Toast.LENGTH_SHORT).show();
                configureAction();
                loadFormalProfileIfAvailable();
            }

            @Override
            public void onError(String message) {
                primaryButton.setEnabled(true);
                primaryButton.setText("接受邀請");
                Toast.makeText(ProfileActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void setBusy(String text) {
        primaryButton.setEnabled(false);
        primaryButton.setText(text);
    }

    private void openLogin() {
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private String value(String value) {
        return value == null ? "" : value;
    }

    private boolean same(String a, String b) {
        return a != null && b != null && !a.isEmpty() && a.equals(b);
    }
}
