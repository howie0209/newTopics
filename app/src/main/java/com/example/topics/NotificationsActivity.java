package com.example.topics;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.topics.data.model.EmptyResponse;
import com.example.topics.data.model.NotificationDto;
import com.example.topics.data.model.NotificationListData;
import com.example.topics.data.model.NotificationTargetData;
import com.example.topics.data.repository.NotificationRepository;
import com.example.topics.data.repository.RepositoryCallback;
import com.example.topics.ui.common.AvatarBinder;
import com.example.topics.ui.design.AdriftSystemUi;

import java.util.ArrayList;
import java.util.List;

/** 通知清單，對齊網頁版 NotificationList。 */
public class NotificationsActivity extends AppCompatActivity {
    private NotificationRepository repository;
    private final List<NotificationDto> items = new ArrayList<>();
    private Adapter adapter;
    private TextView summary, errorText, emptyTitle, readAllButton;
    private View errorView, emptyView;
    private String nextCursor;
    private int unreadCount;
    private boolean loading;
    private boolean busy;
    private boolean destroyed;
    private final RealtimeEvents.Listener realtimeListener = (event, data) -> {
        if ("notifications:changed".equals(event) || "content:changed".equals(event)) load(false);
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notifications);
        AdriftSystemUi.apply(this);
        repository = new NotificationRepository(this);
        summary = findViewById(R.id.tv_notifications_summary);
        errorText = findViewById(R.id.tv_notifications_error);
        errorView = findViewById(R.id.layout_notifications_error);
        emptyView = findViewById(R.id.layout_notifications_empty);
        emptyTitle = findViewById(R.id.tv_notifications_empty_title);
        readAllButton = findViewById(R.id.btn_notifications_read_all);

        findViewById(R.id.btn_notifications_back).setOnClickListener(v -> finish());
        findViewById(R.id.btn_notifications_reload).setOnClickListener(v -> load(false));
        readAllButton.setOnClickListener(v -> readAll());

        RecyclerView list = findViewById(R.id.rv_notifications);
        list.setLayoutManager(new LinearLayoutManager(this));
        adapter = new Adapter();
        list.setAdapter(adapter);
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

    private void load(boolean more) {
        if (loading || (more && nextCursor == null)) return;
        loading = true;
        errorView.setVisibility(View.GONE);
        if (!more && items.isEmpty()) {
            emptyTitle.setText("正在讀取通知");
            emptyView.setVisibility(View.VISIBLE);
        }
        adapter.notifyDataSetChanged();
        repository.getNotifications(more ? nextCursor : null, new RepositoryCallback<NotificationListData>() {
            @Override
            public void onSuccess(NotificationListData value) {
                if (!isActive()) return;
                loading = false;
                if (!more) items.clear();
                if (value != null) {
                    for (NotificationDto item : value.getItems()) {
                        if (!containsId(item.id)) items.add(item);
                    }
                    unreadCount = value.unreadCount;
                    nextCursor = value.nextCursor == null || value.nextCursor.isEmpty() ? null : value.nextCursor;
                }
                render();
            }

            @Override
            public void onError(String message) {
                if (!isActive()) return;
                loading = false;
                errorText.setText(message);
                errorView.setVisibility(View.VISIBLE);
                render();
            }
        });
    }

    private boolean containsId(String id) {
        for (NotificationDto item : items) if (item.id != null && item.id.equals(id)) return true;
        return false;
    }

    private void render() {
        summary.setText(unreadCount > 0 ? unreadCount + " 則未讀" : "你的互動都在這裡");
        readAllButton.setEnabled(unreadCount > 0 && !busy);
        readAllButton.setAlpha(unreadCount > 0 && !busy ? 1f : 0.5f);
        emptyTitle.setText("目前沒有通知");
        emptyView.setVisibility(items.isEmpty() && errorView.getVisibility() != View.VISIBLE ? View.VISIBLE : View.GONE);
        adapter.notifyDataSetChanged();
    }

    private void readAll() {
        if (busy || unreadCount == 0) return;
        busy = true;
        render();
        repository.readAll(new RepositoryCallback<EmptyResponse>() {
            @Override
            public void onSuccess(EmptyResponse value) {
                if (!isActive()) return;
                busy = false;
                String now = "read";
                for (NotificationDto item : items) if (item.isUnread()) item.readAt = now;
                unreadCount = 0;
                render();
            }

            @Override
            public void onError(String message) {
                if (!isActive()) return;
                busy = false;
                errorText.setText(message);
                errorView.setVisibility(View.VISIBLE);
                render();
            }
        });
    }

    // 點一則通知：先確認目標還在，標成已讀，再打開對應畫面（對齊網頁版 act(id)）
    private void open(NotificationDto item) {
        if (busy || item == null || item.id == null) return;
        busy = true;
        repository.getTarget(item.id, new RepositoryCallback<NotificationTargetData>() {
            @Override
            public void onSuccess(NotificationTargetData target) {
                if (!isActive()) return;
                busy = false;
                if (item.isUnread()) {
                    item.readAt = "read";
                    unreadCount = Math.max(0, unreadCount - 1);
                    repository.read(item.id, new RepositoryCallback<EmptyResponse>() {
                        @Override public void onSuccess(EmptyResponse value) {}
                        @Override public void onError(String message) {}
                    });
                }
                render();
                navigate(item, target);
            }

            @Override
            public void onError(String message) {
                if (!isActive()) return;
                busy = false;
                Toast.makeText(NotificationsActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void navigate(NotificationDto item, NotificationTargetData target) {
        String type = target == null || target.type == null ? item.type : target.type;
        if ("diary_reaction".equals(type)) {
            Intent intent = new Intent(this, MapActivity.class);
            intent.putExtra("open_diary_id", target != null && target.targetId != null ? target.targetId : item.targetId);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
        } else if ("friend_request".equals(type)) {
            Intent intent = new Intent(this, FriendsActivity.class);
            intent.putExtra(FriendsActivity.EXTRA_SECTION, "requests");
            startActivity(intent);
        } else {
            String actorId = target != null && target.actorId != null ? target.actorId : (item.actor == null ? "" : item.actor.getId());
            Intent intent = new Intent(this, ProfileActivity.class);
            intent.putExtra(ProfileActivity.EXTRA_USER_ID, actorId);
            intent.putExtra(ProfileActivity.EXTRA_NAME, item.actor == null ? "好友" : item.actor.getDisplayName());
            intent.putExtra(ProfileActivity.EXTRA_USER_CODE, item.actor == null ? "" : item.actor.getUserCode());
            intent.putExtra(ProfileActivity.EXTRA_AVATAR, item.actor == null ? "" : item.actor.avatar);
            intent.putExtra(ProfileActivity.EXTRA_STATUS, "friend");
            startActivity(intent);
        }
    }

    private static String messageFor(String type) {
        if ("friend_request".equals(type)) return "想加你為好友";
        if ("friend_accepted".equals(type)) return "接受了你的好友邀請";
        if ("diary_reaction".equals(type)) return "對你的記憶留下了共鳴";
        return "";
    }

    private boolean isActive() {
        return !destroyed && !isFinishing() && !isDestroyed();
    }

    @Override
    protected void onDestroy() {
        destroyed = true;
        super.onDestroy();
    }

    private class Adapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        @Override
        public int getItemCount() {
            return items.size() + (nextCursor != null ? 1 : 0);
        }

        @Override
        public int getItemViewType(int position) {
            return position < items.size() ? 0 : 1;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LayoutInflater inflater = LayoutInflater.from(parent.getContext());
            View view = inflater.inflate(viewType == 0 ? R.layout.item_notification : R.layout.item_feed_more, parent, false);
            return new RecyclerView.ViewHolder(view) {};
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            View view = holder.itemView;
            if (getItemViewType(position) == 1) {
                Button more = (Button) view;
                more.setEnabled(!loading);
                more.setText(loading ? "正在讀取…" : "載入更多通知");
                more.setOnClickListener(v -> load(true));
                return;
            }
            NotificationDto item = items.get(position);
            String name = item.actor == null ? "已離開的使用者" : item.actor.getDisplayName();
            ImageView avatar = view.findViewById(R.id.iv_notification_avatar);
            TextView fallback = view.findViewById(R.id.tv_notification_avatar);
            AvatarBinder.bind(avatar, fallback, name, item.actor == null ? "" : item.actor.avatar);
            ((TextView) view.findViewById(R.id.tv_notification_name)).setText(name);
            ((TextView) view.findViewById(R.id.tv_notification_message)).setText(messageFor(item.type));
            ((TextView) view.findViewById(R.id.tv_notification_time)).setText(DiaryTime.format(item.createdAt));
            view.findViewById(R.id.view_notification_unread).setVisibility(item.isUnread() ? View.VISIBLE : View.INVISIBLE);
            view.setAlpha(busy ? 0.6f : 1f);
            view.setOnClickListener(v -> open(item));
        }
    }
}
