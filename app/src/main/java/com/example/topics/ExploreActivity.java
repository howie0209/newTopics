package com.example.topics;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.topics.data.local.SessionManager;
import com.example.topics.data.model.DiaryDto;
import com.example.topics.data.model.ReactionUpdateData;
import com.example.topics.data.model.UserDto;
import com.example.topics.data.repository.DiaryRepository;
import com.example.topics.data.repository.RepositoryCallback;
import com.example.topics.ui.common.AppNavigator;
import com.example.topics.ui.design.AdriftSystemUi;
import com.example.topics.ui.social.ExploreDiaryAdapter;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.ArrayList;
import java.util.Collections;

public class ExploreActivity extends AppCompatActivity {
    private static final int DEFAULT_RADIUS = 5000;

    private DiaryRepository diaryRepository;
    private SessionManager sessionManager;
    private FusedLocationProviderClient locationClient;
    private ExploreDiaryAdapter adapter;
    private TextView statusView;
    private TextView chip1;
    private TextView chip5;
    private TextView chip10;
    private Button refreshButton;
    private int radius = DEFAULT_RADIUS;
    private boolean loading;
    private boolean destroyed;
    private ActivityResultLauncher<String> permissionLauncher;
    private final Set<String> reactingDiaryIds = new HashSet<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_explore);
        AdriftSystemUi.apply(this);
        diaryRepository = new DiaryRepository(this);
        sessionManager = SessionManager.getInstance(this);
        locationClient = LocationServices.getFusedLocationProviderClient(this);
        if (!sessionManager.hasToken()) {
            openLogin();
            return;
        }
        bindViews();
        setupPermissionLauncher();
        setupList();
        setupRadiusControls();
        setupBottomNav();
        refresh();
    }

    private void bindViews() {
        statusView = findViewById(R.id.tv_explore_status);
        chip1 = findViewById(R.id.chip_radius_1);
        chip5 = findViewById(R.id.chip_radius_5);
        chip10 = findViewById(R.id.chip_radius_10);
        refreshButton = findViewById(R.id.btn_explore_refresh);
        refreshButton.setOnClickListener(v -> refresh());
    }

    private void setupPermissionLauncher() {
        permissionLauncher = registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
            if (!isActive()) return;
            if (granted) refresh();
            else statusView.setText("需要位置權限才能取得附近公開日記");
        });
    }

    private void setupList() {
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
        });
        recyclerView.setAdapter(adapter);
    }

    private void setupRadiusControls() {
        chip1.setOnClickListener(v -> selectRadius(1000));
        chip5.setOnClickListener(v -> selectRadius(5000));
        chip10.setOnClickListener(v -> selectRadius(10000));
        renderRadius();
    }

    private void setupBottomNav() {
        findViewById(R.id.explore_nav_map).setOnClickListener(v -> {
            AppNavigator.openTopLevel(this, MapActivity.class);
        });
        findViewById(R.id.explore_nav_friends).setOnClickListener(v -> AppNavigator.openTopLevel(this, FriendsActivity.class));
        findViewById(R.id.explore_nav_settings).setOnClickListener(v -> startActivity(new Intent(this, InsightActivity.class)));
    }

    private void selectRadius(int nextRadius) {
        if (radius == nextRadius && loading) return;
        radius = nextRadius;
        View root = getWindow() == null ? null : getWindow().getDecorView();
        if (root != null) root.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
        renderRadius();
        refresh();
    }

    private void renderRadius() {
        styleRadius(chip1, radius == 1000);
        styleRadius(chip5, radius == 5000);
        styleRadius(chip10, radius == 10000);
    }

    private void styleRadius(TextView view, boolean selected) {
        view.setBackgroundResource(selected ? R.drawable.bg_adrift_nav_item : 0);
        view.setTextColor(selected ? 0xFFFFFFFF : 0xFFB9D9E8);
    }

    private void refresh() {
        if (loading) return;
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            statusView.setText("允許位置權限後即可探索附近日記");
            permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION);
            return;
        }
        loading = true;
        refreshButton.setEnabled(false);
        refreshButton.setText("刷新中");
        statusView.setText("取得附近公開日記中");
        locationClient.getLastLocation()
                .addOnSuccessListener(this, this::loadExploreForLocation)
                .addOnFailureListener(this, e -> {
                    if (!isActive()) return;
                    loading = false;
                    refreshButton.setEnabled(true);
                    refreshButton.setText("刷新");
                    statusView.setText("無法取得位置：" + e.getMessage());
                });
    }

    private void loadExploreForLocation(Location location) {
        if (!isActive()) return;
        if (location == null) {
            loading = false;
            refreshButton.setEnabled(true);
            refreshButton.setText("刷新");
            statusView.setText("目前沒有可用位置，請在模擬器設定位置後刷新");
            return;
        }
        diaryRepository.getExploreDiaries(location.getLatitude(), location.getLongitude(), radius, new RepositoryCallback<List<DiaryDto>>() {
            @Override
            public void onSuccess(List<DiaryDto> value) {
                if (!isActive()) return;
                loading = false;
                refreshButton.setEnabled(true);
                refreshButton.setText("刷新");
                List<DiaryDto> sorted = value == null ? new ArrayList<>() : new ArrayList<>(value);
                Collections.sort(sorted, (d1, d2) -> {
                    String t1 = d1.createdAt == null ? "" : d1.createdAt;
                    String t2 = d2.createdAt == null ? "" : d2.createdAt;
                    return t2.compareTo(t1);
                });
                adapter.submit(sorted);
                int count = sorted.size();
                statusView.setText(count == 0 ? "附近沒有公開日記" : "附近公開日記 " + count + " 篇");
            }

            @Override
            public void onError(String message) {
                if (!isActive()) return;
                loading = false;
                refreshButton.setEnabled(true);
                refreshButton.setText("刷新");
                adapter.submit(null);
                statusView.setText(message);
            }
        });
    }

    private void react(DiaryDto diary, String type, int position) {
        if (position == RecyclerView.NO_POSITION || diary == null || diary.getId().isEmpty()) return;
        if (!reactingDiaryIds.add(diary.getId())) return;
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
                Toast.makeText(ExploreActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });
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

    private void openLogin() {
        AppNavigator.openLoginAndClear(this);
    }

    private boolean same(String a, String b) {
        return a != null && b != null && !a.isEmpty() && a.equals(b);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
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
