package com.example.topics;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import com.bumptech.glide.Glide;
import com.example.topics.data.local.SessionManager;
import com.example.topics.data.model.AdminDiaryDto;
import com.example.topics.data.model.AdminDiaryListData;
import com.example.topics.data.model.AdminStatsData;
import com.example.topics.data.model.AdminUserDto;
import com.example.topics.data.model.AdminUserListData;
import com.example.topics.data.model.EmptyResponse;
import com.example.topics.data.remote.ImageUrlResolver;
import com.example.topics.data.repository.AdminRepository;
import com.example.topics.data.repository.RepositoryCallback;

import java.util.ArrayList;
import java.util.List;


public class AdminActivity extends AppCompatActivity {

    private static final String[] ROLE_FILTER_VALUES = {"all", "user", "admin", "owner"};
    private static final String[] ROLE_FILTER_LABELS = {"All roles", "User", "Admin", "Owner"};
    private static final String[] ROLE_EDIT_VALUES = {"user", "admin", "owner"};
    private static final String[] VISIBILITY_FILTER_VALUES = {"all", "public", "friends", "private"};
    private static final String[] VISIBILITY_FILTER_LABELS = {"All visibility", "public", "friends", "private"};
    private static final String[] MOOD_FILTER_VALUES = {"all", "calm", "joy", "sad", "wonder", "anxious", "confused", "nostalgic", "other"};
    private static final String[] MOOD_FILTER_LABELS = {"All moods", "calm", "joy", "sad", "wonder", "anxious", "confused", "nostalgic", "other"};

    private AdminRepository adminRepository;
    private SessionManager sessionManager;
    private boolean destroyed;
    private boolean isOwner;

    private TextView tabOverview, tabUsers, tabDiaries;
    private View contentOverview, contentUsers, contentDiaries;
    private LinearLayout containerStats, containerUsers, containerDiaries;

    private EditText etUserSearch, etDiarySearch;
    private Spinner spinnerRole, spinnerVisibility, spinnerMood;
    private TextView tvUsersEmpty, tvUsersPageInfo, tvDiariesEmpty, tvDiariesPageInfo;
    private Button btnUsersPrev, btnUsersNext, btnDiariesPrev, btnDiariesNext;

    private String userSearch = "";
    private String userRole = "all";
    private int userPage = 1;

    private String diarySearch = "";
    private String diaryVisibility = "all";
    private String diaryMood = "all";
    private int diaryPage = 1;

    private final Handler searchHandler = new Handler(Looper.getMainLooper());
    private Runnable userSearchRunnable;
    private Runnable diarySearchRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin);

        adminRepository = new AdminRepository(this);
        sessionManager = SessionManager.getInstance(this);
        String role = sessionManager.getUser() != null && sessionManager.getUser().role != null
                ? sessionManager.getUser().role : "user";
        isOwner = "owner".equals(role);

        initViews();
        setupTabs();
        setupUsersTab();
        setupDiariesTab();

        findViewById(R.id.btn_admin_back).setOnClickListener(v -> finish());
        findViewById(R.id.btn_admin_refresh).setOnClickListener(v -> refreshActiveTab());

        selectTab("overview");
        loadStats();
    }

    private void initViews() {
        tabOverview = findViewById(R.id.tab_admin_overview);
        tabUsers = findViewById(R.id.tab_admin_users);
        tabDiaries = findViewById(R.id.tab_admin_diaries);
        contentOverview = findViewById(R.id.content_admin_overview);
        contentUsers = findViewById(R.id.content_admin_users);
        contentDiaries = findViewById(R.id.content_admin_diaries);
        containerStats = findViewById(R.id.container_admin_stats);
        containerUsers = findViewById(R.id.container_admin_users);
        containerDiaries = findViewById(R.id.container_admin_diaries);

        etUserSearch = findViewById(R.id.et_admin_user_search);
        spinnerRole = findViewById(R.id.spinner_admin_role);
        tvUsersEmpty = findViewById(R.id.tv_admin_users_empty);
        tvUsersPageInfo = findViewById(R.id.tv_admin_users_page_info);
        btnUsersPrev = findViewById(R.id.btn_admin_users_prev);
        btnUsersNext = findViewById(R.id.btn_admin_users_next);

        etDiarySearch = findViewById(R.id.et_admin_diary_search);
        spinnerVisibility = findViewById(R.id.spinner_admin_visibility);
        spinnerMood = findViewById(R.id.spinner_admin_mood);
        tvDiariesEmpty = findViewById(R.id.tv_admin_diaries_empty);
        tvDiariesPageInfo = findViewById(R.id.tv_admin_diaries_page_info);
        btnDiariesPrev = findViewById(R.id.btn_admin_diaries_prev);
        btnDiariesNext = findViewById(R.id.btn_admin_diaries_next);
    }

    private void setupTabs() {
        tabOverview.setOnClickListener(v -> { selectTab("overview"); loadStats(); });
        tabUsers.setOnClickListener(v -> { selectTab("users"); loadUsers(); });
        tabDiaries.setOnClickListener(v -> { selectTab("diaries"); loadDiaries(); });
    }

    private void selectTab(String tab) {
        boolean isOverview = "overview".equals(tab);
        boolean isUsers = "users".equals(tab);
        boolean isDiaries = "diaries".equals(tab);

        contentOverview.setVisibility(isOverview ? View.VISIBLE : View.GONE);
        contentUsers.setVisibility(isUsers ? View.VISIBLE : View.GONE);
        contentDiaries.setVisibility(isDiaries ? View.VISIBLE : View.GONE);

        updateTabStyle(tabOverview, isOverview);
        updateTabStyle(tabUsers, isUsers);
        updateTabStyle(tabDiaries, isDiaries);
    }

    private void updateTabStyle(TextView tab, boolean selected) {
        if (selected) {
            tab.setBackgroundResource(R.drawable.bg_login_card);
            tab.setTextColor(getResources().getColor(R.color.white));
        } else {
            tab.setBackground(null);
            tab.setTextColor(getResources().getColor(R.color.hint_text));
        }
    }

    private void refreshActiveTab() {
        if (contentOverview.getVisibility() == View.VISIBLE) loadStats();
        else if (contentUsers.getVisibility() == View.VISIBLE) loadUsers();
        else loadDiaries();
    }

    // ---- Overview ----
    private void loadStats() {
        adminRepository.getStats(new RepositoryCallback<AdminStatsData>() {
            @Override
            public void onSuccess(AdminStatsData data) {
                if (!isActive()) return;
                containerStats.removeAllViews();
                addStatRow("總使用者數", data.totalUsers);
                addStatRow("總日記數", data.totalDiaries);
                addStatRow("今日新增使用者", data.todayUsers);
                addStatRow("今日新增日記", data.todayDiaries);
                addStatRow("Public 日記", data.publicDiaries);
                addStatRow("Friends 日記", data.friendsDiaries);
                addStatRow("Private 日記", data.privateDiaries);
            }

            @Override
            public void onError(String message) {
                if (!isActive()) return;
                Toast.makeText(AdminActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void addStatRow(String label, int value) {
        View item = LayoutInflater.from(this).inflate(R.layout.item_admin_stat, containerStats, false);
        ((TextView) item.findViewById(R.id.tv_admin_stat_label)).setText(label);
        ((TextView) item.findViewById(R.id.tv_admin_stat_value)).setText(String.valueOf(value));
        containerStats.addView(item);
    }

    // ---- Users ----
    private void setupUsersTab() {
        spinnerRole.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, ROLE_FILTER_LABELS));
        spinnerRole.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                userRole = ROLE_FILTER_VALUES[position];
                userPage = 1;
                loadUsers();
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });

        etUserSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (userSearchRunnable != null) searchHandler.removeCallbacks(userSearchRunnable);
                userSearchRunnable = () -> {
                    userSearch = s.toString().trim();
                    userPage = 1;
                    loadUsers();
                };
                searchHandler.postDelayed(userSearchRunnable, 400);
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        btnUsersPrev.setOnClickListener(v -> { if (userPage > 1) { userPage--; loadUsers(); } });
        btnUsersNext.setOnClickListener(v -> { userPage++; loadUsers(); });
    }

    private void loadUsers() {
        adminRepository.getUsers(userSearch, userRole, userPage, 10, new RepositoryCallback<AdminUserListData>() {
            @Override
            public void onSuccess(AdminUserListData data) {
                if (!isActive()) return;
                List<AdminUserDto> items = data.items != null ? data.items : new ArrayList<>();
                containerUsers.removeAllViews();
                for (AdminUserDto user : items) {
                    containerUsers.addView(buildUserRow(user));
                }
                tvUsersEmpty.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);

                int page = data.pagination != null ? data.pagination.page : 1;
                int totalPages = data.pagination != null ? data.pagination.totalPages : 1;
                int total = data.pagination != null ? data.pagination.total : 0;
                tvUsersPageInfo.setText("第 " + page + " / " + totalPages + " 頁 · 共 " + total + " 筆");
                btnUsersPrev.setEnabled(page > 1);
                btnUsersNext.setEnabled(page < totalPages);
            }

            @Override
            public void onError(String message) {
                if (!isActive()) return;
                Toast.makeText(AdminActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private View buildUserRow(AdminUserDto user) {
        View item = LayoutInflater.from(this).inflate(R.layout.item_admin_user, containerUsers, false);
        TextView tvName = item.findViewById(R.id.tv_admin_user_name);
        TextView tvRole = item.findViewById(R.id.tv_admin_user_role);
        TextView tvCode = item.findViewById(R.id.tv_admin_user_code);
        TextView tvEmail = item.findViewById(R.id.tv_admin_user_email);
        TextView tvCreated = item.findViewById(R.id.tv_admin_user_created);
        Button btnDelete = item.findViewById(R.id.btn_admin_user_delete);

        tvName.setText(user.name == null ? "" : user.name);
        tvRole.setText(capitalize(user.role));
        tvCode.setText("@" + (user.userCode == null ? "" : user.userCode));
        tvEmail.setText(user.email == null ? "" : user.email);
        tvCreated.setText(formatDate(user.createdAt));

        String myId = sessionManager.getUserId();
        boolean isSelf = user.getUserId() != null && user.getUserId().equals(myId);

        if (isOwner && !isSelf) {
            tvRole.setOnClickListener(v -> showRoleMenu(tvRole, user));
            btnDelete.setVisibility(View.VISIBLE);
            btnDelete.setOnClickListener(v -> confirmDeleteUser(user));
        }

        return item;
    }

    private void showRoleMenu(View anchor, AdminUserDto user) {
        PopupMenu menu = new PopupMenu(this, anchor);
        for (String value : ROLE_EDIT_VALUES) menu.getMenu().add(capitalize(value));
        menu.setOnMenuItemClickListener(item -> {
            String newRole = item.getTitle().toString().toLowerCase();
            if (newRole.equals(user.role)) return true;
            adminRepository.updateUserRole(user.getUserId(), newRole, new RepositoryCallback<AdminUserDto>() {
                @Override
                public void onSuccess(AdminUserDto value) {
                    if (!isActive()) return;
                    Toast.makeText(AdminActivity.this, "使用者權限已更新", Toast.LENGTH_SHORT).show();
                    loadUsers();
                }

                @Override
                public void onError(String message) {
                    if (!isActive()) return;
                    Toast.makeText(AdminActivity.this, message, Toast.LENGTH_SHORT).show();
                }
            });
            return true;
        });
        menu.show();
    }

    private void confirmDeleteUser(AdminUserDto user) {
        LinearLayout fields = new LinearLayout(this);
        fields.setOrientation(LinearLayout.VERTICAL);
        fields.setPadding(48, 8, 48, 0);

        EditText confirmInput = new EditText(this);
        confirmInput.setHint("輸入 DELETE 確認");
        fields.addView(confirmInput);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("確定要刪除此使用者嗎？")
                .setMessage("此操作會刪除 " + user.name + "（@" + user.userCode + "）的所有日記、好友關係與相關資料，且無法復原。")
                .setView(fields)
                .setPositiveButton("確認刪除", null)
                .setNegativeButton("取消", null)
                .create();

        dialog.setOnShowListener(shown -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            if (!"DELETE".equals(confirmInput.getText().toString().trim())) {
                Toast.makeText(this, "請輸入 DELETE 確認刪除", Toast.LENGTH_SHORT).show();
                return;
            }
            adminRepository.deleteUser(user.getUserId(), new RepositoryCallback<EmptyResponse>() {
                @Override
                public void onSuccess(EmptyResponse value) {
                    if (!isActive()) return;
                    Toast.makeText(AdminActivity.this, "使用者已刪除", Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                    loadUsers();
                    loadStats();
                }

                @Override
                public void onError(String message) {
                    if (!isActive()) return;
                    Toast.makeText(AdminActivity.this, message, Toast.LENGTH_SHORT).show();
                }
            });
        }));
        dialog.show();
    }

    // ---- Diaries ----
    private void setupDiariesTab() {
        spinnerVisibility.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, VISIBILITY_FILTER_LABELS));
        spinnerVisibility.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                diaryVisibility = VISIBILITY_FILTER_VALUES[position];
                diaryPage = 1;
                loadDiaries();
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });

        spinnerMood.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, MOOD_FILTER_LABELS));
        spinnerMood.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                diaryMood = MOOD_FILTER_VALUES[position];
                diaryPage = 1;
                loadDiaries();
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });

        etDiarySearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (diarySearchRunnable != null) searchHandler.removeCallbacks(diarySearchRunnable);
                diarySearchRunnable = () -> {
                    diarySearch = s.toString().trim();
                    diaryPage = 1;
                    loadDiaries();
                };
                searchHandler.postDelayed(diarySearchRunnable, 400);
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        btnDiariesPrev.setOnClickListener(v -> { if (diaryPage > 1) { diaryPage--; loadDiaries(); } });
        btnDiariesNext.setOnClickListener(v -> { diaryPage++; loadDiaries(); });
    }

    private void loadDiaries() {
        adminRepository.getDiaries(diarySearch, diaryVisibility, diaryMood, diaryPage, 10, new RepositoryCallback<AdminDiaryListData>() {
            @Override
            public void onSuccess(AdminDiaryListData data) {
                if (!isActive()) return;
                List<AdminDiaryDto> items = data.items != null ? data.items : new ArrayList<>();
                containerDiaries.removeAllViews();
                for (AdminDiaryDto diary : items) {
                    containerDiaries.addView(buildDiaryRow(diary));
                }
                tvDiariesEmpty.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);

                int page = data.pagination != null ? data.pagination.page : 1;
                int totalPages = data.pagination != null ? data.pagination.totalPages : 1;
                int total = data.pagination != null ? data.pagination.total : 0;
                tvDiariesPageInfo.setText("第 " + page + " / " + totalPages + " 頁 · 共 " + total + " 筆");
                btnDiariesPrev.setEnabled(page > 1);
                btnDiariesNext.setEnabled(page < totalPages);
            }

            @Override
            public void onError(String message) {
                if (!isActive()) return;
                Toast.makeText(AdminActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private View buildDiaryRow(AdminDiaryDto diary) {
        View item = LayoutInflater.from(this).inflate(R.layout.item_admin_diary, containerDiaries, false);
        TextView tvTitle = item.findViewById(R.id.tv_admin_diary_title);
        TextView tvMeta = item.findViewById(R.id.tv_admin_diary_meta);
        Button btnView = item.findViewById(R.id.btn_admin_diary_view);
        Button btnDelete = item.findViewById(R.id.btn_admin_diary_delete);

        String authorCode = diary.author != null && diary.author.userCode != null ? diary.author.userCode : "unknown";
        String moodType = diary.mood != null && diary.mood.type != null ? diary.mood.type : "-";

        tvTitle.setText(diary.title == null ? "（未命名日記）" : diary.title);
        tvMeta.setText("@" + authorCode + " · " + moodType + " · " + diary.visibility + " · " + formatDate(diary.createdAt));

        btnView.setOnClickListener(v -> showDiaryDetail(diary));
        btnDelete.setOnClickListener(v -> confirmDeleteDiary(diary));

        return item;
    }

    private void showDiaryDetail(AdminDiaryDto diary) {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_admin_diary_detail, null);

        TextView tvAuthor = view.findViewById(R.id.tv_dialog_diary_author);
        ImageButton btnClose = view.findViewById(R.id.btn_dialog_diary_close);
        TextView tvTitle = view.findViewById(R.id.tv_dialog_diary_title);
        TextView tvText = view.findViewById(R.id.tv_dialog_diary_text);
        TextView tvMeta = view.findViewById(R.id.tv_dialog_diary_meta);
        ImageView ivImage = view.findViewById(R.id.iv_dialog_diary_image);
        TextView tvMood = view.findViewById(R.id.tv_dialog_diary_mood);
        TextView tvVisibility = view.findViewById(R.id.tv_dialog_diary_visibility);
        TextView tvDelete = view.findViewById(R.id.tv_dialog_diary_delete);

        String authorCode = diary.author != null && diary.author.userCode != null ? diary.author.userCode : "unknown";
        String authorName = diary.author != null && diary.author.name != null ? diary.author.name : authorCode;
        String moodType = diary.mood != null && diary.mood.type != null ? diary.mood.type : "其他";
        int intensity = diary.mood != null ? diary.mood.intensity : 0;
        String placeName = diary.location != null && diary.location.placeName != null && !diary.location.placeName.trim().isEmpty()
                ? diary.location.placeName : "未記錄地點";

        tvAuthor.setText("@" + authorName);
        tvTitle.setText(diary.title == null || diary.title.isEmpty() ? "（未命名日記）" : diary.title);

        String content = diary.content != null ? diary.content : (diary.text != null ? diary.text : "");
        tvText.setText(content);
        tvText.setVisibility(content.isEmpty() ? View.GONE : View.VISIBLE);

        tvMeta.setText(placeName + " · " + formatDate(diary.createdAt) + " · 編輯 " + diary.editCount + " 次");
        tvMood.setText(moodType + " · 強度 " + intensity);
        tvVisibility.setText(diary.visibility == null ? "" : diary.visibility);

        if (diary.imageUrl != null && !diary.imageUrl.trim().isEmpty()) {
            ivImage.setVisibility(View.VISIBLE);
            Glide.with(this)
                    .load(ImageUrlResolver.resolve(diary.imageUrl))
                    .placeholder(R.drawable.bg_image_placeholder)
                    .error(R.drawable.bg_image_placeholder)
                    .centerCrop()
                    .into(ivImage);
        } else {
            ivImage.setVisibility(View.GONE);
        }

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(view)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        btnClose.setOnClickListener(v -> dialog.dismiss());
        tvDelete.setOnClickListener(v -> {
            dialog.dismiss();
            confirmDeleteDiary(diary);
        });

        dialog.show();
    }

    private void confirmDeleteDiary(AdminDiaryDto diary) {
        new AlertDialog.Builder(this)
                .setTitle("刪除這篇日記？")
                .setMessage("刪除後無法復原。")
                .setPositiveButton("確認刪除", (dialog, which) -> {
                    adminRepository.deleteDiary(diary._id, new RepositoryCallback<EmptyResponse>() {
                        @Override
                        public void onSuccess(EmptyResponse value) {
                            if (!isActive()) return;
                            Toast.makeText(AdminActivity.this, "日記已由管理員刪除", Toast.LENGTH_SHORT).show();
                            loadDiaries();
                            loadStats();
                        }

                        @Override
                        public void onError(String message) {
                            if (!isActive()) return;
                            Toast.makeText(AdminActivity.this, message, Toast.LENGTH_SHORT).show();
                        }
                    });
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private String capitalize(String value) {
        if (value == null || value.isEmpty()) return "User";
        return value.substring(0, 1).toUpperCase() + value.substring(1);
    }

    private String formatDate(String value) {
        if (value == null || value.length() < 10) return "-";
        return value.substring(0, 10);
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
