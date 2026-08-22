package com.example.topics;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
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
import com.example.topics.ui.design.AdriftSystemUi;
import com.example.topics.ui.social.ExploreDiaryAdapter;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;

import java.util.List;

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
    private int radius = DEFAULT_RADIUS;
    private boolean loading;
    private ActivityResultLauncher<String> permissionLauncher;

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
        findViewById(R.id.btn_explore_refresh).setOnClickListener(v -> refresh());
    }

    private void setupPermissionLauncher() {
        permissionLauncher = registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
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
            startActivity(new Intent(this, MapActivity.class));
            finish();
        });
        findViewById(R.id.explore_nav_friends).setOnClickListener(v -> startActivity(new Intent(this, FriendsActivity.class)));
        findViewById(R.id.explore_nav_settings).setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
    }

    private void selectRadius(int nextRadius) {
        radius = nextRadius;
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
        statusView.setText("取得附近公開日記中");
        locationClient.getLastLocation()
                .addOnSuccessListener(this, this::loadExploreForLocation)
                .addOnFailureListener(this, e -> {
                    loading = false;
                    statusView.setText("無法取得位置：" + e.getMessage());
                });
    }

    private void loadExploreForLocation(Location location) {
        if (location == null) {
            loading = false;
            statusView.setText("目前沒有可用位置，請在模擬器設定位置後刷新");
            return;
        }
        diaryRepository.getExploreDiaries(location.getLatitude(), location.getLongitude(), radius, new RepositoryCallback<List<DiaryDto>>() {
            @Override
            public void onSuccess(List<DiaryDto> value) {
                loading = false;
                adapter.submit(value);
                int count = value == null ? 0 : value.size();
                statusView.setText(count == 0 ? "附近沒有公開日記" : "附近公開日記 " + count + " 篇");
            }

            @Override
            public void onError(String message) {
                loading = false;
                adapter.submit(null);
                statusView.setText(message);
            }
        });
    }

    private void react(DiaryDto diary, String type, int position) {
        if (position == RecyclerView.NO_POSITION || diary == null || diary.getId().isEmpty()) return;
        diaryRepository.reactToDiary(diary.getId(), type, new RepositoryCallback<ReactionUpdateData>() {
            @Override
            public void onSuccess(ReactionUpdateData value) {
                adapter.applyReaction(position, value);
            }

            @Override
            public void onError(String message) {
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
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private boolean same(String a, String b) {
        return a != null && b != null && !a.isEmpty() && a.equals(b);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }
}
