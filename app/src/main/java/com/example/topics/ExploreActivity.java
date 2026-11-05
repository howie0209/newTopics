package com.example.topics;

import android.content.Intent;
import android.os.Bundle;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.topics.data.local.SessionManager;
import com.example.topics.data.model.DiaryDto;
import com.example.topics.data.model.FeedData;
import com.example.topics.data.model.ReactionUpdateData;
import com.example.topics.data.model.UserDto;
import com.example.topics.data.repository.DiaryRepository;
import com.example.topics.data.repository.RepositoryCallback;
import com.example.topics.ui.common.AppNavigator;
import com.example.topics.ui.design.AdriftSystemUi;
import com.example.topics.ui.social.ExploreDiaryAdapter;

import java.util.HashSet;
import java.util.Set;

/**
 * 動態牆（底部第三個分頁），對齊網頁版 FeedPage。
 * 原本是「探索」（附近公開日記），附近日記改在地圖頁的「附近動態」看。
 */
public class ExploreActivity extends AppCompatActivity {
    private DiaryRepository diaryRepository;
    private SessionManager sessionManager;
    private ExploreDiaryAdapter adapter;
    private TextView tabAll, tabFriends, tabPublic, loadingView, emptyTitle, errorText;
    private View emptyView, errorView;
    private String filter = "all";
    private String nextCursor;
    private boolean loading;
    private boolean destroyed;
    private int requestToken = 0;
    private final Set<String> reactingDiaryIds = new HashSet<>();
    private final RealtimeEvents.Listener realtimeListener = (event, data) -> {
        if (event.startsWith("diary:") || "social:changed".equals(event) || "content:changed".equals(event)) {
            load(false);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_explore);
        AdriftSystemUi.apply(this);
        diaryRepository = new DiaryRepository(this);
        sessionManager = SessionManager.getInstance(this);
        if (!sessionManager.hasToken()) {
            AppNavigator.openLoginAndClear(this);
            return;
        }
        tabAll = findViewById(R.id.tab_feed_all);
        tabFriends = findViewById(R.id.tab_feed_friends);
        tabPublic = findViewById(R.id.tab_feed_public);
        loadingView = findViewById(R.id.tv_feed_loading);
        emptyView = findViewById(R.id.layout_feed_empty);
        emptyTitle = findViewById(R.id.tv_feed_empty_title);
        errorView = findViewById(R.id.layout_feed_error);
        errorText = findViewById(R.id.tv_feed_error);

        tabAll.setOnClickListener(v -> selectFilter("all"));
        tabFriends.setOnClickListener(v -> selectFilter("friends"));
        tabPublic.setOnClickListener(v -> selectFilter("public"));
        findViewById(R.id.btn_feed_refresh).setOnClickListener(v -> load(false));
        findViewById(R.id.btn_feed_retry).setOnClickListener(v -> load(false));
        findViewById(R.id.btn_feed_new_diary).setOnClickListener(v -> openNewDiary());
        findViewById(R.id.btn_feed_empty_new).setOnClickListener(v -> openNewDiary());

        RecyclerView recyclerView = findViewById(R.id.rv_explore_diaries);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ExploreDiaryAdapter(new ExploreDiaryAdapter.Listener() {
            @Override
            public void onReact(DiaryDto diary, String type, int position) {
                react(diary, type, position);
            }

            @Override
            public void onImage(DiaryDto diary) {
                if (diary.getImages().isEmpty()) return;
                Intent intent = new Intent(ExploreActivity.this, ImagePreviewActivity.class);
                intent.putExtra(ImagePreviewActivity.EXTRA_IMAGE_URL, diary.getImages().get(0));
                startActivity(intent);
            }

            @Override
            public void onAuthor(DiaryDto diary) {
                openAuthorProfile(diary);
            }

            @Override
            public void onOpen(DiaryDto diary) {
                openDiaryOnMap(diary);
            }

            @Override
            public void onLoadMore() {
                load(true);
            }
        });
        adapter.setCurrentUserId(sessionManager.getUserId());
        recyclerView.setAdapter(adapter);

        findViewById(R.id.explore_nav_map).setOnClickListener(v -> AppNavigator.openTopLevel(this, MapActivity.class));
        findViewById(R.id.explore_nav_friends).setOnClickListener(v -> AppNavigator.openTopLevel(this, FriendsActivity.class));
        findViewById(R.id.explore_nav_settings).setOnClickListener(v -> startActivity(new Intent(this, InsightActivity.class)));

        renderTabs();
        load(false);
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

    private void selectFilter(String next) {
        if (filter.equals(next)) return;
        filter = next;
        View root = getWindow() == null ? null : getWindow().getDecorView();
        if (root != null) root.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
        renderTabs();
        load(false);
    }

    private void renderTabs() {
        styleTab(tabAll, "all".equals(filter));
        styleTab(tabFriends, "friends".equals(filter));
        styleTab(tabPublic, "public".equals(filter));
    }

    private void styleTab(TextView view, boolean selected) {
        view.setBackgroundResource(selected ? R.drawable.bg_adrift_nav_item : 0);
        view.setTextColor(getColor(selected ? R.color.ad_text : R.color.ad_text_secondary));
    }

    private void load(boolean more) {
        if (more && (loading || nextCursor == null)) return;
        final int token = ++requestToken;
        loading = true;
        errorView.setVisibility(View.GONE);
        if (more) {
            adapter.setLoadingMore(true);
        } else {
            emptyView.setVisibility(View.GONE);
            loadingView.setVisibility(adapter.diaryCount() == 0 ? View.VISIBLE : View.GONE);
        }
        diaryRepository.getFeed(filter, more ? nextCursor : null, new RepositoryCallback<FeedData>() {
            @Override
            public void onSuccess(FeedData value) {
                if (!isActive() || token != requestToken) return;
                loading = false;
                loadingView.setVisibility(View.GONE);
                nextCursor = value == null ? null : value.nextCursor;
                if (nextCursor != null && nextCursor.isEmpty()) nextCursor = null;
                adapter.submit(value == null ? null : value.getDiaries(), more, nextCursor != null);
                boolean empty = adapter.diaryCount() == 0;
                emptyTitle.setText("friends".equals(filter) ? "朋友的記憶，會在這裡相遇" : "附近還很安靜");
                emptyView.setVisibility(empty ? View.VISIBLE : View.GONE);
            }

            @Override
            public void onError(String message) {
                if (!isActive() || token != requestToken) return;
                loading = false;
                loadingView.setVisibility(View.GONE);
                adapter.setLoadingMore(false);
                errorText.setText(message);
                errorView.setVisibility(View.VISIBLE);
            }
        });
    }

    private void react(DiaryDto diary, String type, int position) {
        if (position == RecyclerView.NO_POSITION || diary == null || diary.getId().isEmpty()) return;
        if (!reactingDiaryIds.add(diary.getId())) return;
        adapter.applyOptimisticReaction(position, type);
        diaryRepository.reactToDiary(diary.getId(), type, new RepositoryCallback<ReactionUpdateData>() {
            @Override
            public void onSuccess(ReactionUpdateData value) {
                if (!isActive()) return;
                reactingDiaryIds.remove(diary.getId());
                adapter.applyReaction(position, value);
                View root = getWindow() == null ? null : getWindow().getDecorView();
                if (root != null) root.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            }

            @Override
            public void onError(String message) {
                if (!isActive()) return;
                reactingDiaryIds.remove(diary.getId());
                adapter.applyOptimisticReaction(position, type);
                Toast.makeText(ExploreActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void openNewDiary() {
        Intent intent = new Intent(this, MapActivity.class);
        intent.putExtra(MapActivity.EXTRA_NEW_DIARY, true);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }

    private void openDiaryOnMap(DiaryDto diary) {
        if (diary == null || diary.getId().isEmpty()) return;
        Intent intent = new Intent(this, MapActivity.class);
        intent.putExtra("open_diary_id", diary.getId());
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }

    private void openAuthorProfile(DiaryDto diary) {
        UserDto author = diary == null ? null : diary.getAuthor();
        if (author == null) return;
        Intent intent = new Intent(this, ProfileActivity.class);
        intent.putExtra(ProfileActivity.EXTRA_USER_ID, author.getId());
        intent.putExtra(ProfileActivity.EXTRA_NAME, author.getDisplayName());
        intent.putExtra(ProfileActivity.EXTRA_USER_CODE, author.getUserCode());
        intent.putExtra(ProfileActivity.EXTRA_AVATAR, author.avatar);
        intent.putExtra(ProfileActivity.EXTRA_STATUS, same(author.getId(), sessionManager.getUserId()) ? "self" : "none");
        startActivity(intent);
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
        reactingDiaryIds.clear();
        super.onDestroy();
    }
}
