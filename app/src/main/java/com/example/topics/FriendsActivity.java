package com.example.topics;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.topics.data.local.SessionManager;
import com.example.topics.data.model.EmptyResponse;
import com.example.topics.data.model.FriendDto;
import com.example.topics.data.model.SearchUserResult;
import com.example.topics.data.model.UserDto;
import com.example.topics.data.repository.FriendRepository;
import com.example.topics.data.repository.RepositoryCallback;
import com.example.topics.ui.common.AppNavigator;
import com.example.topics.ui.common.AvatarBinder;
import com.example.topics.ui.design.AdriftSystemUi;
import com.example.topics.ui.social.SocialUserAdapter;
import com.example.topics.ui.social.SocialUserItem;

import java.util.ArrayList;
import java.util.List;

public class FriendsActivity extends AppCompatActivity {
    private FriendRepository friendRepository;
    private SessionManager sessionManager;
    private TextView status;
    private EditText searchInput;
    private Button searchButton;
    private FrameLayout searchResult;
    private View searchPanel;
    private RecyclerView friendsView;
    private RecyclerView receivedView;
    private RecyclerView sentView;
    private TextView tabSearch;
    private TextView tabFriends;
    private TextView tabReceived;
    private TextView tabSent;
    private SocialUserAdapter friendsAdapter;
    private SocialUserAdapter receivedAdapter;
    private SocialUserAdapter sentAdapter;
    private final List<FriendDto> friends = new ArrayList<>();
    private final List<FriendDto> received = new ArrayList<>();
    private final List<FriendDto> sent = new ArrayList<>();
    private boolean loading;
    private boolean actionInFlight;
    private boolean destroyed;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_friends);
        AdriftSystemUi.apply(this);
        friendRepository = new FriendRepository(this);
        sessionManager = SessionManager.getInstance(this);
        if (!sessionManager.hasToken()) {
            openLogin();
            return;
        }
        bindViews();
        setupAdapters();
        setupTabs();
        setupBottomNav();
        loadAll();
    }

    private void bindViews() {
        status = findViewById(R.id.tv_friends_status);
        searchInput = findViewById(R.id.et_friend_code);
        searchButton = findViewById(R.id.btn_friend_search);
        searchResult = findViewById(R.id.layout_search_result);
        searchPanel = findViewById(R.id.panel_friend_search);
        friendsView = findViewById(R.id.rv_friends);
        receivedView = findViewById(R.id.rv_received);
        sentView = findViewById(R.id.rv_sent);
        tabSearch = findViewById(R.id.tab_friends_search);
        tabFriends = findViewById(R.id.tab_friends_list);
        tabReceived = findViewById(R.id.tab_friends_received);
        tabSent = findViewById(R.id.tab_friends_sent);
        searchButton.setOnClickListener(v -> searchUser());
        searchInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE) {
                searchUser();
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
        friendsAdapter = new SocialUserAdapter(listener);
        receivedAdapter = new SocialUserAdapter(listener);
        sentAdapter = new SocialUserAdapter(listener);
        setupList(friendsView, friendsAdapter);
        setupList(receivedView, receivedAdapter);
        setupList(sentView, sentAdapter);
    }

    private void setupList(RecyclerView recyclerView, SocialUserAdapter adapter) {
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);
    }

    private void setupTabs() {
        tabSearch.setOnClickListener(v -> showTab("search"));
        tabFriends.setOnClickListener(v -> showTab("friends"));
        tabReceived.setOnClickListener(v -> showTab("received"));
        tabSent.setOnClickListener(v -> showTab("sent"));
        showTab("search");
    }

    private void setupBottomNav() {
        findViewById(R.id.friends_nav_map).setOnClickListener(v -> {
            AppNavigator.openTopLevel(this, MapActivity.class);
        });
        findViewById(R.id.friends_nav_explore).setOnClickListener(v -> AppNavigator.openTopLevel(this, ExploreActivity.class));
        findViewById(R.id.friends_nav_settings).setOnClickListener(v -> AppNavigator.openTopLevel(this, SettingsActivity.class));
    }

    private void showTab(String tab) {
        View root = getWindow() == null ? null : getWindow().getDecorView();
        if (root != null) root.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
        searchPanel.setVisibility("search".equals(tab) ? View.VISIBLE : View.GONE);
        friendsView.setVisibility("friends".equals(tab) ? View.VISIBLE : View.GONE);
        receivedView.setVisibility("received".equals(tab) ? View.VISIBLE : View.GONE);
        sentView.setVisibility("sent".equals(tab) ? View.VISIBLE : View.GONE);
        styleTab(tabSearch, "search".equals(tab));
        styleTab(tabFriends, "friends".equals(tab));
        styleTab(tabReceived, "received".equals(tab));
        styleTab(tabSent, "sent".equals(tab));
        updateStatusForTab(tab);
    }

    private void styleTab(TextView tab, boolean selected) {
        tab.setBackgroundResource(selected ? R.drawable.bg_adrift_nav_item : 0);
        tab.setTextColor(selected ? Color.WHITE : Color.rgb(185, 217, 232));
    }

    private void updateStatusForTab(String tab) {
        if (loading) return;
        if ("friends".equals(tab)) status.setText(friends.isEmpty() ? "還沒有好友" : "好友 " + friends.size() + " 位");
        else if ("received".equals(tab)) status.setText(received.isEmpty() ? "沒有新的好友邀請" : "收到 " + received.size() + " 則邀請");
        else if ("sent".equals(tab)) status.setText(sent.isEmpty() ? "沒有等待回覆的邀請" : "已送出 " + sent.size() + " 則邀請");
        else status.setText("使用 userCode 精準搜尋使用者");
    }

    private void loadAll() {
        loading = true;
        status.setText("同步好友資料中");
        status.setOnClickListener(null);
        friendRepository.getFriendDtos(new RepositoryCallback<List<FriendDto>>() {
            @Override
            public void onSuccess(List<FriendDto> value) {
                if (!isActive()) return;
                friends.clear();
                if (value != null) friends.addAll(value);
                friendsAdapter.submit(toFriendItems(friends));
                loadReceived();
            }

            @Override
            public void onError(String message) {
                if (!isActive()) return;
                loading = false;
                status.setText(message);
                status.setOnClickListener(v -> loadAll());
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
                receivedAdapter.submit(toRequestItems(received, true));
                loadSent();
            }

            @Override
            public void onError(String message) {
                if (!isActive()) return;
                loading = false;
                status.setText(message);
                status.setOnClickListener(v -> loadAll());
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
                sentAdapter.submit(toRequestItems(sent, false));
                loading = false;
                updateStatusForTab(currentTab());
            }

            @Override
            public void onError(String message) {
                if (!isActive()) return;
                loading = false;
                status.setText(message);
                status.setOnClickListener(v -> loadAll());
            }
        });
    }

    private String currentTab() {
        if (friendsView.getVisibility() == View.VISIBLE) return "friends";
        if (receivedView.getVisibility() == View.VISIBLE) return "received";
        if (sentView.getVisibility() == View.VISIBLE) return "sent";
        return "search";
    }

    private List<SocialUserItem> toFriendItems(List<FriendDto> values) {
        List<SocialUserItem> items = new ArrayList<>();
        for (FriendDto friend : values) {
            SocialUserItem item = new SocialUserItem();
            item.id = friend.getId();
            item.name = friend.name;
            item.userCode = friend.userCode;
            item.avatar = friend.avatar;
            item.status = "friend";
            item.meta = "好友";
            item.primaryText = "查看";
            item.secondaryText = "移除";
            item.showSecondary = true;
            items.add(item);
        }
        return items;
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
            item.meta = incoming ? "等待你回覆" : "等待對方回覆";
            item.primaryText = incoming ? "接受" : "等待中";
            item.primaryEnabled = incoming;
            item.secondaryText = incoming ? "拒絕" : "取消";
            item.showSecondary = true;
            items.add(item);
        }
        return items;
    }

    private void searchUser() {
        if (actionInFlight) return;
        String code = searchInput.getText().toString().trim();
        if (code.isEmpty()) {
            Toast.makeText(this, "請輸入 userCode", Toast.LENGTH_SHORT).show();
            return;
        }
        setSearchLoading(true);
        friendRepository.searchUser(code, new RepositoryCallback<SearchUserResult>() {
            @Override
            public void onSuccess(SearchUserResult value) {
                if (!isActive()) return;
                setSearchLoading(false);
                renderSearchResult(value);
            }

            @Override
            public void onError(String message) {
                if (!isActive()) return;
                setSearchLoading(false);
                searchResult.removeAllViews();
                status.setText(message);
            }
        });
    }

    private void renderSearchResult(SearchUserResult result) {
        searchResult.removeAllViews();
        if (result == null) {
            status.setText("找不到使用者");
            return;
        }
        SocialUserItem item = new SocialUserItem();
        item.id = result.getId();
        item.name = result.name;
        item.userCode = result.userCode;
        item.avatar = result.avatar;
        item.status = resolveStatus(result);
        applyActionTexts(item);

        View view = LayoutInflater.from(this).inflate(R.layout.item_social_user, searchResult, false);
        ImageView image = view.findViewById(R.id.iv_social_avatar);
        TextView fallback = view.findViewById(R.id.tv_social_avatar);
        TextView name = view.findViewById(R.id.tv_social_name);
        TextView meta = view.findViewById(R.id.tv_social_meta);
        Button primary = view.findViewById(R.id.btn_social_primary);
        Button secondary = view.findViewById(R.id.btn_social_secondary);
        name.setText(item.displayName());
        meta.setText(item.displayCode() + " · " + item.meta);
        AvatarBinder.bind(image, fallback, item.name, item.avatar);
        primary.setText(item.primaryText);
        primary.setEnabled(item.primaryEnabled);
        secondary.setText(item.secondaryText == null ? "" : item.secondaryText);
        secondary.setVisibility(item.showSecondary ? View.VISIBLE : View.GONE);
        view.setOnClickListener(v -> openProfile(item));
        primary.setOnClickListener(v -> handlePrimary(item));
        secondary.setOnClickListener(v -> handleSecondary(item));
        searchResult.addView(view);
        status.setText("搜尋完成");
    }

    private String resolveStatus(SearchUserResult result) {
        String userId = result.getId();
        if (same(userId, sessionManager.getUserId())) return "self";
        for (FriendDto friend : friends) if (same(friend.getId(), userId)) return "friend";
        for (FriendDto request : received) {
            if (request.from != null && same(request.from.getId(), userId)) return "received_request";
        }
        for (FriendDto request : sent) {
            if (request.to != null && same(request.to.getId(), userId)) return "sent_request";
        }
        return result.friendshipStatus == null ? "none" : result.friendshipStatus;
    }

    private void applyActionTexts(SocialUserItem item) {
        if ("self".equals(item.status)) {
            item.meta = "這是你";
            item.primaryText = "我的設定";
        } else if ("friend".equals(item.status)) {
            item.meta = "已是好友";
            item.primaryText = "查看";
            item.secondaryText = "移除";
            item.showSecondary = true;
        } else if ("sent_request".equals(item.status)) {
            item.meta = "邀請已送出";
            item.primaryText = "等待中";
            item.primaryEnabled = false;
            item.secondaryText = "取消";
            item.showSecondary = true;
        } else if ("received_request".equals(item.status)) {
            item.meta = "對方已邀請你";
            item.primaryText = "接受";
            item.secondaryText = "拒絕";
            item.showSecondary = true;
        } else {
            item.meta = "尚未成為好友";
            item.primaryText = "加好友";
        }
    }

    private void handlePrimary(SocialUserItem item) {
        if ("self".equals(item.status)) {
            startActivity(new Intent(this, SettingsActivity.class));
        } else if ("none".equals(item.status)) {
            sendRequest(item);
        } else if ("received_request".equals(item.status)) {
            accept(item);
        } else {
            openProfile(item);
        }
    }

    private void handleSecondary(SocialUserItem item) {
        if ("friend".equals(item.status)) {
            confirmDelete(item);
        } else if ("sent_request".equals(item.status)) {
            cancel(item);
        } else if ("received_request".equals(item.status)) {
            reject(item);
        }
    }

    private void sendRequest(SocialUserItem item) {
        runAction("送出邀請中", callbackMessage("好友邀請已送出", () ->
                friendRepository.sendFriendRequest(item.id, new EmptyCallback())));
    }

    private void accept(SocialUserItem item) {
        String requestId = requestIdFor(item, true);
        if (requestId.isEmpty()) {
            showTab("received");
            return;
        }
        runAction("接受邀請中", callbackMessage("已成為好友", () ->
                friendRepository.acceptRequest(requestId, new EmptyCallback())));
    }

    private void reject(SocialUserItem item) {
        String requestId = requestIdFor(item, true);
        if (requestId.isEmpty()) {
            showTab("received");
            return;
        }
        runAction("拒絕邀請中", callbackMessage("已拒絕邀請", () ->
                friendRepository.rejectRequest(requestId, new EmptyCallback())));
    }

    private void cancel(SocialUserItem item) {
        String requestId = requestIdFor(item, false);
        if (requestId.isEmpty()) {
            showTab("sent");
            return;
        }
        runAction("取消邀請中", callbackMessage("已取消邀請", () ->
                friendRepository.cancelRequest(requestId, new EmptyCallback())));
    }

    private void confirmDelete(SocialUserItem item) {
        new AlertDialog.Builder(this)
                .setTitle("移除好友")
                .setMessage("確定要移除 " + item.displayName() + " 嗎？")
                .setPositiveButton("移除", (dialog, which) ->
                        runAction("移除好友中", callbackMessage("已移除好友", () ->
                                friendRepository.deleteFriend(item.id, new EmptyCallback()))))
                .setNegativeButton("取消", null)
                .show();
    }

    private void runAction(String message, Runnable action) {
        if (actionInFlight) return;
        actionInFlight = true;
        searchButton.setEnabled(false);
        status.setText(message);
        action.run();
    }

    private Runnable callbackMessage(String successMessage, Runnable request) {
        return () -> {
            currentSuccessMessage = successMessage;
            request.run();
        };
    }

    private String currentSuccessMessage = "";

    private class EmptyCallback implements RepositoryCallback<EmptyResponse> {
        @Override
        public void onSuccess(EmptyResponse value) {
            if (!isActive()) return;
            actionInFlight = false;
            searchButton.setEnabled(true);
            Toast.makeText(FriendsActivity.this, currentSuccessMessage, Toast.LENGTH_SHORT).show();
            searchResult.removeAllViews();
            loadAll();
        }

        @Override
        public void onError(String message) {
            if (!isActive()) return;
            actionInFlight = false;
            searchButton.setEnabled(true);
            status.setText(message);
            Toast.makeText(FriendsActivity.this, message, Toast.LENGTH_SHORT).show();
        }
    }

    private String requestIdFor(SocialUserItem item, boolean incoming) {
        if (item.requestId != null && !item.requestId.isEmpty()) return item.requestId;
        List<FriendDto> requests = incoming ? received : sent;
        for (FriendDto request : requests) {
            UserDto user = incoming ? request.from : request.to;
            if (user != null && same(user.getId(), item.id)) return request.requestId == null ? "" : request.requestId;
        }
        return "";
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

    private void setSearchLoading(boolean value) {
        searchButton.setEnabled(!value);
        searchButton.setText(value ? "搜尋中" : "搜尋");
    }

    private void openLogin() {
        AppNavigator.openLoginAndClear(this);
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
