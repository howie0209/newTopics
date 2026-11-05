package com.example.topics;

import android.content.Intent;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.example.topics.data.local.SessionManager;
import com.example.topics.data.mapper.DiaryMapper;
import com.example.topics.data.model.DiaryDto;
import com.example.topics.data.model.EmptyResponse;
import com.example.topics.data.model.FriendProfileDto;
import com.example.topics.data.model.MemoryPageData;
import com.example.topics.data.model.SharedPlacesData;
import com.example.topics.data.model.UserDto;
import com.example.topics.data.remote.ImageUrlResolver;
import com.example.topics.data.repository.FriendRepository;
import com.example.topics.data.repository.RepositoryCallback;
import com.example.topics.ui.common.AppNavigator;
import com.example.topics.ui.common.AvatarBinder;
import com.example.topics.ui.design.AdriftSystemUi;

/**
 * 個人資料，對齊網頁版 FriendProfile：
 * 身分與好友動作、公開記憶／好友限定統計、可見記憶（可載入更多、點了回到地圖）、共同地點（只有互為好友才看得到）。
 */
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
    private TextView avatarFallback, nameView, codeView, statusView, statPublic, statFriends, statJoined, listState;
    private TextView tabMemories, tabPlaces;
    private Button primaryButton, secondaryButton, moreButton;
    private LinearLayout memoriesContainer, placesContainer;
    private String userId, name, userCode, avatar, friendshipStatus, requestId;
    private String memoryCursor;
    private boolean canViewPlaces;
    private boolean placesLoaded;
    private boolean busy;
    private boolean destroyed;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);
        AdriftSystemUi.apply(this);
        friendRepository = new FriendRepository(this);
        sessionManager = SessionManager.getInstance(this);
        if (!sessionManager.hasToken()) {
            AppNavigator.openLoginAndClear(this);
            return;
        }
        Intent intent = getIntent();
        userId = value(intent.getStringExtra(EXTRA_USER_ID));
        name = value(intent.getStringExtra(EXTRA_NAME));
        userCode = value(intent.getStringExtra(EXTRA_USER_CODE));
        avatar = value(intent.getStringExtra(EXTRA_AVATAR));
        friendshipStatus = value(intent.getStringExtra(EXTRA_STATUS));
        requestId = value(intent.getStringExtra(EXTRA_REQUEST_ID));
        if (same(userId, sessionManager.getUserId())) friendshipStatus = "self";
        if ("self".equals(friendshipStatus)) {
            UserDto me = sessionManager.getUser();
            if (me != null) {
                userId = me.getId();
                name = me.getDisplayName();
                userCode = me.getUserCode();
                avatar = me.avatar;
            }
        }

        findViewById(R.id.btn_profile_back).setOnClickListener(v -> finish());
        avatarImage = findViewById(R.id.iv_profile_avatar);
        avatarFallback = findViewById(R.id.tv_profile_avatar);
        nameView = findViewById(R.id.tv_profile_name);
        codeView = findViewById(R.id.tv_profile_code);
        statusView = findViewById(R.id.tv_profile_status);
        statPublic = findViewById(R.id.tv_profile_stat_public);
        statFriends = findViewById(R.id.tv_profile_stat_friends);
        statJoined = findViewById(R.id.tv_profile_stat_joined);
        listState = findViewById(R.id.tv_profile_list_state);
        tabMemories = findViewById(R.id.tab_profile_memories);
        tabPlaces = findViewById(R.id.tab_profile_places);
        primaryButton = findViewById(R.id.btn_profile_primary);
        secondaryButton = findViewById(R.id.btn_profile_secondary);
        moreButton = findViewById(R.id.btn_profile_more);
        memoriesContainer = findViewById(R.id.layout_profile_memories);
        placesContainer = findViewById(R.id.layout_profile_places);

        tabMemories.setOnClickListener(v -> showTab(false));
        tabPlaces.setOnClickListener(v -> showTab(true));
        moreButton.setOnClickListener(v -> loadMemories(true));

        renderIdentity();
        configureActions();
        loadProfile();
        loadMemories(false);
    }

    private void renderIdentity() {
        nameView.setText(name.isEmpty() ? "使用者" : name);
        codeView.setText(userCode.isEmpty() ? "" : "@" + userCode);
        AvatarBinder.bind(avatarImage, avatarFallback, name, avatar);
    }

    // 好友動作，對齊網頁版 controls()
    private void configureActions() {
        primaryButton.setEnabled(!busy);
        secondaryButton.setVisibility(View.GONE);
        if ("self".equals(friendshipStatus)) {
            statusView.setText("你自己");
            primaryButton.setText("我的帳號");
            primaryButton.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        } else if ("friend".equals(friendshipStatus)) {
            statusView.setText("✓ 好友");
            primaryButton.setText("回到記憶地圖");
            primaryButton.setOnClickListener(v -> AppNavigator.openTopLevel(this, MapActivity.class));
            secondaryButton.setText("移除好友");
            secondaryButton.setVisibility(View.VISIBLE);
            secondaryButton.setOnClickListener(v -> confirmRemove());
        } else if ("sent_request".equals(friendshipStatus)) {
            statusView.setText("等待回覆");
            primaryButton.setText("撤回邀請");
            primaryButton.setOnClickListener(v -> runAction("好友邀請已撤回", "none", cb -> friendRepository.cancelRequest(requestId, cb)));
        } else if ("received_request".equals(friendshipStatus)) {
            statusView.setText("邀請你成為好友");
            primaryButton.setText("接受");
            primaryButton.setOnClickListener(v -> runAction("已成為好友", "friend", cb -> friendRepository.acceptRequest(requestId, cb)));
            secondaryButton.setText("拒絕");
            secondaryButton.setVisibility(View.VISIBLE);
            secondaryButton.setOnClickListener(v -> runAction("已拒絕好友邀請", "none", cb -> friendRepository.rejectRequest(requestId, cb)));
        } else {
            statusView.setText("尚未成為好友");
            primaryButton.setText("加入好友");
            primaryButton.setOnClickListener(v -> runAction("好友邀請已送出", "sent_request", cb -> friendRepository.sendFriendRequest(userId, cb)));
        }
    }

    private void confirmRemove() {
        new AlertDialog.Builder(this)
                .setTitle("移除 " + (name.isEmpty() ? "好友" : name) + "？")
                .setMessage("你們將無法再查看彼此的好友限定記憶。公開日記不受影響。")
                .setPositiveButton("移除好友", (d, w) -> runAction("已移除好友", "none", cb -> friendRepository.deleteFriend(userId, cb)))
                .setNegativeButton("取消", null)
                .show();
    }

    private interface Action {
        void run(RepositoryCallback<EmptyResponse> callback);
    }

    private void runAction(String message, String nextStatus, Action action) {
        if (busy || userId.isEmpty()) return;
        busy = true;
        configureActions();
        action.run(new RepositoryCallback<EmptyResponse>() {
            @Override
            public void onSuccess(EmptyResponse value) {
                if (!isActive()) return;
                busy = false;
                friendshipStatus = nextStatus;
                Toast.makeText(ProfileActivity.this, message, Toast.LENGTH_SHORT).show();
                configureActions();
                loadProfile();
                loadMemories(false);
            }

            @Override
            public void onError(String error) {
                if (!isActive()) return;
                busy = false;
                configureActions();
                Toast.makeText(ProfileActivity.this, error, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void loadProfile() {
        if (userId.isEmpty()) return;
        friendRepository.getFriendProfile(userId, new RepositoryCallback<FriendProfileDto>() {
            @Override
            public void onSuccess(FriendProfileDto value) {
                if (!isActive() || value == null) return;
                name = value.getDisplayName();
                userCode = value.userCode == null ? "" : value.userCode;
                avatar = value.avatar == null ? "" : value.avatar;
                if (value.friendshipStatus != null && !value.friendshipStatus.isEmpty()) friendshipStatus = value.friendshipStatus;
                if (value.requestId != null && !value.requestId.isEmpty()) requestId = value.requestId;
                canViewPlaces = value.canViewSharedPlaces;
                renderIdentity();
                configureActions();
                int publicCount = value.diaryStats == null ? 0 : value.diaryStats.publicCount;
                int friendsCount = value.diaryStats == null ? 0 : value.diaryStats.friendsCount;
                statPublic.setText(publicCount + "\n公開記憶");
                statFriends.setText(friendsCount + "\n好友限定");
                String joined = DiaryTime.dateOnly(value.createdAt);
                statJoined.setText((joined.isEmpty() ? "-" : joined) + "\n加入 Adrift");
                tabPlaces.setVisibility(canViewPlaces ? View.VISIBLE : View.GONE);
            }

            @Override
            public void onError(String message) {
                if (!isActive()) return;
                statusView.setText(message);
            }
        });
    }

    private void showTab(boolean places) {
        tabMemories.setBackgroundResource(places ? 0 : R.drawable.bg_adrift_nav_item);
        tabPlaces.setBackgroundResource(places ? R.drawable.bg_adrift_nav_item : 0);
        tabMemories.setTextColor(getColor(places ? R.color.ad_text_secondary : R.color.ad_text));
        tabPlaces.setTextColor(getColor(places ? R.color.ad_text : R.color.ad_text_secondary));
        memoriesContainer.setVisibility(places ? View.GONE : View.VISIBLE);
        moreButton.setVisibility(!places && memoryCursor != null ? View.VISIBLE : View.GONE);
        placesContainer.setVisibility(places ? View.VISIBLE : View.GONE);
        listState.setVisibility(View.GONE);
        if (places && !placesLoaded) loadPlaces();
    }

    private void loadMemories(boolean more) {
        if (userId.isEmpty() || (more && memoryCursor == null)) return;
        if (!more) {
            memoriesContainer.removeAllViews();
            listState.setText("正在讀取可見記憶…");
            listState.setVisibility(View.VISIBLE);
        }
        moreButton.setEnabled(false);
        friendRepository.getFriendMemories(userId, more ? memoryCursor : null, new RepositoryCallback<MemoryPageData>() {
            @Override
            public void onSuccess(MemoryPageData value) {
                if (!isActive()) return;
                moreButton.setEnabled(true);
                memoryCursor = value == null || value.nextCursor == null || value.nextCursor.isEmpty() ? null : value.nextCursor;
                if (value != null) for (DiaryDto diary : value.getDiaries()) addMemoryRow(diary);
                boolean empty = memoriesContainer.getChildCount() == 0;
                listState.setText("還沒有可見的記憶。這裡只顯示對你公開或向好友分享的日記。");
                listState.setVisibility(empty ? View.VISIBLE : View.GONE);
                moreButton.setVisibility(memoryCursor != null && memoriesContainer.getVisibility() == View.VISIBLE ? View.VISIBLE : View.GONE);
            }

            @Override
            public void onError(String message) {
                if (!isActive()) return;
                moreButton.setEnabled(true);
                listState.setText(message);
                listState.setVisibility(View.VISIBLE);
            }
        });
    }

    private void addMemoryRow(DiaryDto diary) {
        View row = LayoutInflater.from(this).inflate(R.layout.item_profile_memory, memoriesContainer, false);
        ImageView image = row.findViewById(R.id.iv_memory_image);
        String imageUrl = ImageUrlResolver.resolve(diary.imageUrl);
        if (imageUrl.isEmpty()) {
            image.setVisibility(View.GONE);
        } else {
            image.setVisibility(View.VISIBLE);
            Glide.with(this).load(imageUrl).centerCrop().into(image);
        }
        String moodType = diary.mood == null ? null : diary.mood.type;
        String mood = moodType == null || moodType.isEmpty() ? "生活記錄" : DiaryMapper.moodDisplayLabel(moodType);
        ((TextView) row.findViewById(R.id.tv_memory_meta)).setText(DiaryTime.dateOnly(diary.createdAt) + " · " + mood);
        ((TextView) row.findViewById(R.id.tv_memory_title)).setText(diary.getTitle());
        ((TextView) row.findViewById(R.id.tv_memory_text)).setText(diary.getText());
        String place = diary.location == null || diary.location.placeName == null || diary.location.placeName.isEmpty()
                ? "未命名地點" : PlaceNameResolver.normalizeTaiwan(diary.location.placeName);
        ((TextView) row.findViewById(R.id.tv_memory_place)).setText("📍 " + place);
        row.setOnClickListener(v -> openDiary(diary.getId()));
        memoriesContainer.addView(row);
    }

    private void loadPlaces() {
        placesLoaded = true;
        placesContainer.removeAllViews();
        addPlaceText("你們都留下過記憶的地方", 15, R.color.ad_text, true);
        TextView loadingView = addPlaceText("正在比對已分享地點…", 12, R.color.ad_text_secondary, false);
        friendRepository.getSharedPlaces(userId, new RepositoryCallback<SharedPlacesData>() {
            @Override
            public void onSuccess(SharedPlacesData value) {
                if (!isActive()) return;
                placesContainer.removeView(loadingView);
                addPlaceText(value != null && value.description != null ? value.description : "只比對彼此可見的已分享日記，不推測共同活動。",
                        12, R.color.ad_text_secondary, false);
                if (value == null || value.getPlaces().isEmpty()) {
                    addPlaceText("還沒有找到重疊的地點。各自的城市故事，也值得慢慢探索。", 13, R.color.ad_text_muted, false);
                    return;
                }
                for (SharedPlacesData.Place place : value.getPlaces()) {
                    addPlaceText("📍 " + PlaceNameResolver.normalizeTaiwan(place.place), 14, R.color.ad_text, true);
                    if (place.mine != null) addPlaceLink("你的記憶", place.mine);
                    if (place.theirs != null) {
                        for (SharedPlacesData.PlaceDiary diary : place.theirs) addPlaceLink((name.isEmpty() ? "好友" : name) + " 的記憶", diary);
                    }
                }
            }

            @Override
            public void onError(String message) {
                if (!isActive()) return;
                placesLoaded = false;
                loadingView.setText(message);
            }
        });
    }

    private TextView addPlaceText(String text, int sizeSp, int colorRes, boolean bold) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp);
        view.setTextColor(getColor(colorRes));
        if (bold) view.setTypeface(view.getTypeface(), android.graphics.Typeface.BOLD);
        int pad = (int) (6 * getResources().getDisplayMetrics().density);
        view.setPadding(0, pad, 0, pad);
        placesContainer.addView(view);
        return view;
    }

    private void addPlaceLink(String label, SharedPlacesData.PlaceDiary diary) {
        TextView view = new TextView(this);
        view.setText(label + "：" + (diary.title == null ? "（未命名日記）" : diary.title) + "  ›");
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        view.setTextColor(getColor(R.color.ad_accent));
        view.setBackgroundResource(R.drawable.bg_ad_note);
        int pad = (int) (10 * getResources().getDisplayMetrics().density);
        view.setPadding(pad, pad, pad, pad);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = pad / 2;
        view.setLayoutParams(params);
        view.setOnClickListener(v -> openDiary(diary.id));
        placesContainer.addView(view);
    }

    private void openDiary(String diaryId) {
        if (diaryId == null || diaryId.isEmpty()) return;
        Intent intent = new Intent(this, MapActivity.class);
        intent.putExtra("open_diary_id", diaryId);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }

    private String value(String value) {
        return value == null ? "" : value;
    }

    private boolean same(String a, String b) {
        return a != null && b != null && !a.isEmpty() && a.equals(b);
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
