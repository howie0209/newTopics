package com.example.topics;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.topics.data.local.SessionManager;
import com.example.topics.data.model.EmptyResponse;
import com.example.topics.data.model.FriendDto;
import com.example.topics.data.model.PersonDto;
import com.example.topics.data.model.UserDto;
import com.example.topics.data.repository.FriendRepository;
import com.example.topics.data.repository.RepositoryCallback;
import com.example.topics.ui.common.AppNavigator;
import com.example.topics.ui.design.AdriftSystemUi;
import com.example.topics.ui.social.SocialUserAdapter;
import com.example.topics.ui.social.SocialUserItem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * 好友頁，對齊網頁版 FriendsPage：
 * 我的好友（篩選、排序）、好友邀請（收到／已送出）、探索使用者（名稱或 ID 搜尋 + 可能認識的人）。
 */
public class FriendsActivity extends AppCompatActivity {
    public static final String EXTRA_SECTION = "friends_section";

    private FriendRepository friendRepository;
    private SessionManager sessionManager;
    private TextView status, statFriends, statReceived, statSent, copyCode, sortButton, searchState, recommendState;
    private TextView tabFriends, tabInvites, tabSearch, tabReceived, tabSent;
    private View panelFriends, panelInvites, panelSearch;
    private RecyclerView receivedView, sentView;
    private EditText filterInput, searchInput;
    private SocialUserAdapter friendsAdapter, receivedAdapter, sentAdapter, searchAdapter, recommendAdapter;
    private final List<FriendDto> friends = new ArrayList<>();
    private final List<FriendDto> received = new ArrayList<>();
    private final List<FriendDto> sent = new ArrayList<>();
    private final List<PersonDto> searchResults = new ArrayList<>();
    private final List<PersonDto> recommendations = new ArrayList<>();
    private String tab = "friends";
    private String inviteTab = "received";
    private boolean sortByJoined;
    private boolean loading;
    private boolean actionInFlight;
    private boolean destroyed;
    private int searchToken;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable pendingSearch;
    private final RealtimeEvents.Listener realtimeListener = (event, data) -> {
        if ("social:changed".equals(event) || "content:changed".equals(event)) loadAll();
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_friends);
        AdriftSystemUi.apply(this);
        friendRepository = new FriendRepository(this);
        sessionManager = SessionManager.getInstance(this);
        if (!sessionManager.hasToken()) {
            AppNavigator.openLoginAndClear(this);
            return;
        }
        bindViews();
        setupAdapters();
        setupBottomNav();
        String section = getIntent().getStringExtra(EXTRA_SECTION);
        showTab("requests".equals(section) ? "invites" : "friends");
        loadAll();
        loadRecommendations();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if ("requests".equals(intent.getStringExtra(EXTRA_SECTION))) {
            inviteTab = "received";
            showTab("invites");
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        RealtimeEvents.getInstance(this).addListener(realtimeListener);
    }

    @Override
    protected void onStop() {
        RealtimeEvents.getInstance(this).removeListener(realtimeListener);
        super.onStop();
    }

    private void bindViews() {
        status = findViewById(R.id.tv_friends_status);
        statFriends = findViewById(R.id.tv_stat_friends);
        statReceived = findViewById(R.id.tv_stat_received);
        statSent = findViewById(R.id.tv_stat_sent);
        copyCode = findViewById(R.id.btn_copy_my_code);
        sortButton = findViewById(R.id.btn_friend_sort);
        searchState = findViewById(R.id.tv_search_state);
        recommendState = findViewById(R.id.tv_recommend_state);
        tabFriends = findViewById(R.id.tab_friends_list);
        tabInvites = findViewById(R.id.tab_friends_invites);
        tabSearch = findViewById(R.id.tab_friends_search);
        tabReceived = findViewById(R.id.tab_friends_received);
        tabSent = findViewById(R.id.tab_friends_sent);
        panelFriends = findViewById(R.id.panel_friends);
        panelInvites = findViewById(R.id.panel_invites);
        panelSearch = findViewById(R.id.panel_friend_search);
        receivedView = findViewById(R.id.rv_received);
        sentView = findViewById(R.id.rv_sent);
        filterInput = findViewById(R.id.et_friend_filter);
        searchInput = findViewById(R.id.et_friend_code);

        UserDto me = sessionManager.getUser();
        String myCode = me == null ? "" : me.getUserCode();
        copyCode.setText("📋 複製我的 ID  @" + (myCode.isEmpty() ? "user" : myCode));
        copyCode.setOnClickListener(v -> copy(myCode));

        tabFriends.setOnClickListener(v -> showTab("friends"));
        tabInvites.setOnClickListener(v -> showTab("invites"));
        tabSearch.setOnClickListener(v -> showTab("discover"));
        tabReceived.setOnClickListener(v -> selectInviteTab("received"));
        tabSent.setOnClickListener(v -> selectInviteTab("sent"));
        findViewById(R.id.btn_friends_refresh).setOnClickListener(v -> {
            loadAll();
            loadRecommendations();
        });
        sortButton.setOnClickListener(v -> {
            sortByJoined = !sortByJoined;
            sortButton.setText(sortByJoined ? "排序：最近加入" : "排序：名稱");
            renderFriends();
        });
        filterInput.addTextChangedListener(new SimpleWatcher(this::renderFriends));
        searchInput.addTextChangedListener(new SimpleWatcher(this::scheduleSearch));
        searchInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE) {
                runSearch();
                return true;
            }
            return false;
        });
    }

    private void setupAdapters() {
        SocialUserAdapter.Listener listener = new SocialUserAdapter.Listener() {
            @Override
            public void onPrimary(SocialUserItem item) {
                handlePrimary(item);
            }

            @Override
            public void onSecondary(SocialUserItem item) {
                handleSecondary(item);
            }

            @Override
            public void onOpenProfile(SocialUserItem item) {
                openProfile(item);
            }
        };
        friendsAdapter = setupList(R.id.rv_friends, listener);
        receivedAdapter = setupList(R.id.rv_received, listener);
        sentAdapter = setupList(R.id.rv_sent, listener);
        searchAdapter = setupList(R.id.rv_search_results, listener);
        recommendAdapter = setupList(R.id.rv_recommendations, listener);
    }

    private SocialUserAdapter setupList(int id, SocialUserAdapter.Listener listener) {
        RecyclerView view = findViewById(id);
        SocialUserAdapter adapter = new SocialUserAdapter(listener);
        view.setLayoutManager(new LinearLayoutManager(this));
        view.setAdapter(adapter);
        return adapter;
    }

    private void setupBottomNav() {
        findViewById(R.id.friends_nav_map).setOnClickListener(v -> AppNavigator.openTopLevel(this, MapActivity.class));
        findViewById(R.id.friends_nav_explore).setOnClickListener(v -> AppNavigator.openTopLevel(this, ExploreActivity.class));
        findViewById(R.id.friends_nav_settings).setOnClickListener(v -> startActivity(new Intent(this, InsightActivity.class)));
    }

    private void showTab(String next) {
        tab = next;
        View root = getWindow() == null ? null : getWindow().getDecorView();
        if (root != null) root.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
        panelFriends.setVisibility("friends".equals(next) ? View.VISIBLE : View.GONE);
        panelInvites.setVisibility("invites".equals(next) ? View.VISIBLE : View.GONE);
        panelSearch.setVisibility("discover".equals(next) ? View.VISIBLE : View.GONE);
        styleTab(tabFriends, "friends".equals(next));
        styleTab(tabInvites, "invites".equals(next));
        styleTab(tabSearch, "discover".equals(next));
        selectInviteTab(inviteTab);
        updateStatus();
    }

    private void selectInviteTab(String next) {
        inviteTab = next;
        receivedView.setVisibility("received".equals(next) ? View.VISIBLE : View.GONE);
        sentView.setVisibility("sent".equals(next) ? View.VISIBLE : View.GONE);
        tabReceived.setBackgroundResource("received".equals(next) ? R.drawable.bg_ad_pill : 0);
        tabSent.setBackgroundResource("sent".equals(next) ? R.drawable.bg_ad_pill : 0);
        tabReceived.setTextColor(getColor("received".equals(next) ? R.color.ad_text : R.color.ad_text_secondary));
        tabSent.setTextColor(getColor("sent".equals(next) ? R.color.ad_text : R.color.ad_text_secondary));
        updateStatus();
    }

    private void styleTab(TextView view, boolean selected) {
        view.setBackgroundResource(selected ? R.drawable.bg_adrift_nav_item : 0);
        view.setTextColor(getColor(selected ? R.color.ad_text : R.color.ad_text_secondary));
    }

    private void updateStatus() {
        if (loading) return;
        if ("friends".equals(tab)) {
            status.setText(friends.isEmpty() ? "你的城市故事，還有更多人可以一起寫下。" : "你的好友 · " + filteredFriends().size() + " 位");
        } else if ("invites".equals(tab)) {
            if ("received".equals(inviteTab)) status.setText(received.isEmpty() ? "目前沒有新的好友邀請。新的連結，會在這裡與你相遇。" : "由你決定建立哪些連結");
            else status.setText(sent.isEmpty() ? "目前沒有等待回覆的邀請。找到認識的人，從一個邀請開始。" : "由你決定建立哪些連結");
        } else {
            status.setText("");
        }
    }

    private void renderStats() {
        statFriends.setText(friends.size() + "\n位好友");
        statReceived.setText(received.size() + "\n待處理邀請");
        statSent.setText(sent.size() + "\n已送出邀請");
        tabReceived.setText("收到的邀請 " + received.size());
        tabSent.setText("已送出邀請 " + sent.size());
        tabInvites.setText(received.isEmpty() ? "好友邀請" : "好友邀請 •");
    }

    private void loadAll() {
        loading = true;
        status.setText("正在讀取好友…");
        friendRepository.getFriendDtos(new RepositoryCallback<List<FriendDto>>() {
            @Override
            public void onSuccess(List<FriendDto> value) {
                if (!isActive()) return;
                friends.clear();
                if (value != null) friends.addAll(value);
                loadReceived();
            }

            @Override
            public void onError(String message) {
                if (!isActive()) return;
                loading = false;
                status.setText(message);
            }
        });
    }

    private void loadReceived() {
        friendRepository.getReceivedRequestDtos(new RepositoryCallback<List<FriendDto>>() {
            @Override
            public void onSuccess(List<FriendDto> value) {
                if (!isActive()) return;
                received.clear();
                if (value != null) received.addAll(value);
                loadSent();
            }

            @Override
            public void onError(String message) {
                if (!isActive()) return;
                loading = false;
                status.setText(message);
            }
        });
    }

    private void loadSent() {
        friendRepository.getSentRequestDtos(new RepositoryCallback<List<FriendDto>>() {
            @Override
            public void onSuccess(List<FriendDto> value) {
                if (!isActive()) return;
                sent.clear();
                if (value != null) sent.addAll(value);
                loading = false;
                renderFriends();
                receivedAdapter.submit(toRequestItems(received, true));
                sentAdapter.submit(toRequestItems(sent, false));
                renderStats();
                renderSearchResults();
                updateStatus();
            }

            @Override
            public void onError(String message) {
                if (!isActive()) return;
                loading = false;
                status.setText(message);
            }
        });
    }

    private List<FriendDto> filteredFriends() {
        String query = filterInput.getText().toString().trim().replaceFirst("^@", "").toLowerCase(Locale.ROOT);
        List<FriendDto> result = new ArrayList<>();
        for (FriendDto friend : friends) {
            String name = friend.name == null ? "" : friend.name.toLowerCase(Locale.ROOT);
            String code = friend.userCode == null ? "" : friend.userCode.toLowerCase(Locale.ROOT);
            if (query.isEmpty() || name.contains(query) || code.contains(query)) result.add(friend);
        }
        if (sortByJoined) {
            Collections.sort(result, (a, b) -> safe(b.createdAt).compareTo(safe(a.createdAt)));
        } else {
            Collections.sort(result, (a, b) -> safe(a.name).compareToIgnoreCase(safe(b.name)));
        }
        return result;
    }

    private void renderFriends() {
        List<SocialUserItem> items = new ArrayList<>();
        for (FriendDto friend : filteredFriends()) {
            SocialUserItem item = new SocialUserItem();
            item.id = friend.getId();
            item.name = friend.name;
            item.userCode = friend.userCode;
            item.avatar = friend.avatar;
            item.status = "friend";
            item.meta = "好友";
            item.primaryText = "個人資料";
            item.secondaryText = "移除好友";
            item.showSecondary = true;
            items.add(item);
        }
        friendsAdapter.submit(items);
        updateStatus();
    }

    private List<SocialUserItem> toRequestItems(List<FriendDto> values, boolean incoming) {
        List<SocialUserItem> items = new ArrayList<>();
        for (FriendDto request : values) {
            UserDto user = incoming ? request.from : request.to;
            SocialUserItem item = new SocialUserItem();
            item.id = user == null ? "" : user.getId();
            item.requestId = request.requestId;
            item.name = user == null ? "" : user.getDisplayName();
            item.userCode = user == null ? "" : user.getUserCode();
            item.avatar = user == null ? "" : user.avatar;
            item.status = incoming ? "received_request" : "sent_request";
            applyActionTexts(item);
            items.add(item);
        }
        return items;
    }

    // 搜尋：輸入停 350ms 後送出，至少 2 個字（對齊網頁版 FriendsPage）
    private void scheduleSearch() {
        if (pendingSearch != null) handler.removeCallbacks(pendingSearch);
        pendingSearch = this::runSearch;
        handler.postDelayed(pendingSearch, 350);
    }

    private void runSearch() {
        String query = searchInput.getText().toString().trim();
        final int token = ++searchToken;
        if (query.replaceFirst("^@", "").length() < 2) {
            searchResults.clear();
            renderSearchResults();
            searchState.setVisibility(View.GONE);
            return;
        }
        searchState.setText("正在尋找使用者…");
        searchState.setVisibility(View.VISIBLE);
        friendRepository.searchPeople(query, new RepositoryCallback<List<PersonDto>>() {
            @Override
            public void onSuccess(List<PersonDto> value) {
                if (!isActive() || token != searchToken) return;
                searchResults.clear();
                if (value != null) searchResults.addAll(value);
                renderSearchResults();
                if (searchResults.isEmpty()) {
                    searchState.setText("找不到符合條件的使用者。檢查名稱或 ID，或試試其他關鍵字。");
                    searchState.setVisibility(View.VISIBLE);
                } else {
                    searchState.setVisibility(View.GONE);
                }
            }

            @Override
            public void onError(String message) {
                if (!isActive() || token != searchToken) return;
                searchState.setText(message);
                searchState.setVisibility(View.VISIBLE);
            }
        });
    }

    private void renderSearchResults() {
        searchAdapter.submit(toPersonItems(searchResults));
        recommendAdapter.submit(toPersonItems(recommendations));
    }

    private void loadRecommendations() {
        recommendState.setText("正在整理好友連結…");
        recommendState.setVisibility(View.VISIBLE);
        friendRepository.getRecommendations(8, new RepositoryCallback<List<PersonDto>>() {
            @Override
            public void onSuccess(List<PersonDto> value) {
                if (!isActive()) return;
                recommendations.clear();
                if (value != null) recommendations.addAll(value);
                renderSearchResults();
                if (recommendations.isEmpty()) {
                    recommendState.setText("目前還沒有足夠的共同好友連結。你可以先搜尋認識的人。");
                } else {
                    recommendState.setVisibility(View.GONE);
                }
            }

            @Override
            public void onError(String message) {
                if (!isActive()) return;
                recommendState.setText("推薦暫時無法載入，仍可使用上方搜尋。");
            }
        });
    }

    private List<SocialUserItem> toPersonItems(List<PersonDto> people) {
        List<SocialUserItem> items = new ArrayList<>();
        for (PersonDto person : people) {
            SocialUserItem item = new SocialUserItem();
            item.id = person.getId();
            item.name = person.getDisplayName();
            item.userCode = person.userCode;
            item.avatar = person.avatar;
            item.status = resolveStatus(person);
            item.requestId = person.requestId;
            applyActionTexts(item);
            if (person.reasons != null && !person.reasons.isEmpty()) item.meta = person.reasons.get(0);
            items.add(item);
        }
        return items;
    }

    // 以最新的好友／邀請清單為準，避免搜尋結果狀態過期
    private String resolveStatus(PersonDto person) {
        String userId = person.getId();
        if (same(userId, sessionManager.getUserId())) return "self";
        for (FriendDto friend : friends) if (same(friend.getId(), userId)) return "friend";
        for (FriendDto request : received) if (request.from != null && same(request.from.getId(), userId)) return "received_request";
        for (FriendDto request : sent) if (request.to != null && same(request.to.getId(), userId)) return "sent_request";
        return person.friendshipStatus == null ? "none" : person.friendshipStatus;
    }

    // 按鈕文字對齊網頁版 controls()
    private void applyActionTexts(SocialUserItem item) {
        item.primaryEnabled = true;
        item.showSecondary = false;
        if ("self".equals(item.status)) {
            item.meta = "你自己";
            item.primaryText = "✓ 你自己";
            item.primaryEnabled = false;
        } else if ("friend".equals(item.status)) {
            item.meta = "好友";
            item.primaryText = "✓ 好友";
            item.primaryEnabled = false;
        } else if ("sent_request".equals(item.status)) {
            item.meta = "等待回覆";
            item.primaryText = "撤回邀請";
        } else if ("received_request".equals(item.status)) {
            item.meta = "邀請你成為好友";
            item.primaryText = "接受";
            item.secondaryText = "拒絕";
            item.showSecondary = true;
        } else {
            item.meta = "尚未成為好友";
            item.primaryText = "加入好友";
        }
    }

    private void handlePrimary(SocialUserItem item) {
        if ("friend".equals(item.status) && "個人資料".equals(item.primaryText)) {
            openProfile(item);
        } else if ("none".equals(item.status)) {
            runAction(item, "好友邀請已送出", cb -> friendRepository.sendFriendRequest(item.id, cb));
        } else if ("received_request".equals(item.status)) {
            String requestId = requestIdFor(item, true);
            if (requestId.isEmpty()) return;
            runAction(item, "已成為好友", cb -> friendRepository.acceptRequest(requestId, cb));
        } else if ("sent_request".equals(item.status)) {
            String requestId = requestIdFor(item, false);
            if (requestId.isEmpty()) return;
            runAction(item, "好友邀請已撤回", cb -> friendRepository.cancelRequest(requestId, cb));
        } else {
            openProfile(item);
        }
    }

    private void handleSecondary(SocialUserItem item) {
        if ("friend".equals(item.status)) {
            confirmDelete(item);
        } else if ("received_request".equals(item.status)) {
            String requestId = requestIdFor(item, true);
            if (requestId.isEmpty()) return;
            runAction(item, "已拒絕好友邀請", cb -> friendRepository.rejectRequest(requestId, cb));
        }
    }

    private void confirmDelete(SocialUserItem item) {
        new AlertDialog.Builder(this)
                .setTitle("移除 " + item.displayName() + "？")
                .setMessage("你們將無法再查看彼此的好友限定記憶。公開日記不受影響。")
                .setPositiveButton("移除好友", (dialog, which) ->
                        runAction(item, "已移除好友", cb -> friendRepository.deleteFriend(item.id, cb)))
                .setNegativeButton("取消", null)
                .show();
    }

    private interface FriendAction {
        void run(RepositoryCallback<EmptyResponse> callback);
    }

    private void runAction(SocialUserItem item, String successMessage, FriendAction action) {
        if (actionInFlight) return;
        actionInFlight = true;
        action.run(new RepositoryCallback<EmptyResponse>() {
            @Override
            public void onSuccess(EmptyResponse value) {
                if (!isActive()) return;
                actionInFlight = false;
                Toast.makeText(FriendsActivity.this, successMessage, Toast.LENGTH_SHORT).show();
                loadAll();
                loadRecommendations();
            }

            @Override
            public void onError(String message) {
                if (!isActive()) return;
                actionInFlight = false;
                Toast.makeText(FriendsActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private String requestIdFor(SocialUserItem item, boolean incoming) {
        List<FriendDto> requests = incoming ? received : sent;
        for (FriendDto request : requests) {
            UserDto user = incoming ? request.from : request.to;
            if (user != null && same(user.getId(), item.id)) return request.requestId == null ? "" : request.requestId;
        }
        return item.requestId == null ? "" : item.requestId;
    }

    private void openProfile(SocialUserItem item) {
        Intent intent = new Intent(this, ProfileActivity.class);
        intent.putExtra(ProfileActivity.EXTRA_USER_ID, item.id);
        intent.putExtra(ProfileActivity.EXTRA_NAME, item.name);
        intent.putExtra(ProfileActivity.EXTRA_USER_CODE, item.userCode);
        intent.putExtra(ProfileActivity.EXTRA_AVATAR, item.avatar);
        intent.putExtra(ProfileActivity.EXTRA_STATUS, item.status);
        intent.putExtra(ProfileActivity.EXTRA_REQUEST_ID, requestIdFor(item, "received_request".equals(item.status)));
        startActivity(intent);
    }

    private void copy(String code) {
        if (code == null || code.isEmpty()) return;
        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(ClipData.newPlainText("userCode", code));
        Toast.makeText(this, "已複製使用者 ID", Toast.LENGTH_SHORT).show();
    }

    private static String safe(String value) {
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
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    private static class SimpleWatcher implements TextWatcher {
        private final Runnable onChange;

        SimpleWatcher(Runnable onChange) {
            this.onChange = onChange;
        }

        @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
        @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
        @Override public void afterTextChanged(Editable s) { onChange.run(); }
    }

    // 點輸入框以外的地方收起鍵盤（照 iOS 版）
    @Override
    public boolean dispatchTouchEvent(android.view.MotionEvent event) {
        KeyboardDismiss.handle(this, event);
        return super.dispatchTouchEvent(event);
    }
}
