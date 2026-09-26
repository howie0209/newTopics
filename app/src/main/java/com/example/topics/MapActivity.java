package com.example.topics;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.app.ActivityCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.topics.data.local.SessionManager;
import com.example.topics.data.mapper.DiaryMapper;
import com.example.topics.data.mapper.DiaryUiModel;
import com.example.topics.data.model.DiaryDto;
import com.example.topics.data.model.EmptyResponse;
import com.example.topics.data.model.ReactionUpdateData;
import com.example.topics.data.model.SearchUserResult;
import com.example.topics.data.model.UserDto;
import com.example.topics.data.remote.ImageUrlResolver;
import com.example.topics.data.repository.DiaryRepository;
import com.example.topics.data.repository.FriendRepository;
import com.example.topics.data.repository.RepositoryCallback;
import com.example.topics.data.repository.UserRepository;
import com.example.topics.ui.common.AppNavigator;
import com.example.topics.ui.design.AdriftSystemUi;
import com.example.topics.ui.map.DiaryPreviewController;
import com.example.topics.ui.map.MapMarkerManager;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MapStyleOptions;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.material.navigation.NavigationView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MapActivity extends AppCompatActivity implements OnMapReadyCallback {

    private SessionManager sessionManager;
    private DiaryRepository diaryRepository;
    private UserRepository userRepository;
    private FriendRepository friendRepository;
    private MapMarkerManager markerManager;
    private DiaryPreviewController previewController;

    private GoogleMap mMap;
    private FusedLocationProviderClient fusedLocationClient;
    private DrawerLayout drawerLayout;
    private ActivityResultLauncher<String> locationPermissionLauncher;
    private boolean mapReady = false;
    private boolean locationPermissionRequested = false;
    private boolean isLocating = false;
    private boolean destroyed = false;

    private View diaryCardView;
    private View mapBlurOverlay; // 新增遮罩層變數
    private EditText etDiaryTitle, diaryInput;
    private Spinner moodSpinner, visibilitySpinner;
    private SeekBar sbMoodIntensity;
    private TextView tvIntensityLabel;
    private ImageButton btnCloseDiary;
    private ImageView ivDiaryRemoteImage;
    private TextView tvDiaryMeta;
    private Button btnSaveDiary, btnDeleteDiary, btnReactUnderstand, btnReactHug, btnReactRelate;
    private View layoutReactions;
    private boolean isDiaryRequestInFlight = false;

    private TextView tabMine, tabFriends;
    private TextView mapNavMap, mapNavFriends, mapNavExplore, mapNavSettings;
    private ImageButton btnCurrentLocation;
    private View mapStatusChip, mapEmptyChip, mapIdentityChip;
    private TextView tvMapStatus, btnRetryDiaries, tvMapIdentityName, tvMapIdentityCode, tvMapIdentityAvatar;
    private ImageView ivMapIdentityAvatar;
    private Runnable mapStatusDismissRunnable;
    private TextView tvCountMine, tvCountVisible;
    private Button btnPriv, btnFrdOnly, btnPub;
    private LinearLayout layoutMyDiaryRoot, layoutFriendManagement;
    private EditText etSearchFriend;
    private ImageButton btnSearchUser;

    private RecyclerView rvDiaryList;
    private DiaryListAdapter diaryListAdapter;
    private List<DiaryEntry> displayedDiaries = new ArrayList<>();
    private boolean isShowingMine = true;
    private int currentFilter = -1;

    private RecyclerView rvSentRequests, rvReceivedRequests, rvFriendsList;
    private TextView tvEmptySent, tvEmptyReceived, tvEmptyFriends;

    private RecyclerView rvImagePreview;
    private ImageAdapter imageAdapter;
    private List<Uri> selectedImageUris = new ArrayList<>();

    private Map<String, DiaryEntry> markerDataMap = new HashMap<>();
    private Map<String, Marker> diaryMarkerMap = new HashMap<>();
    private Map<String, String> markerDiaryIdMap = new HashMap<>();
    private Marker lastSelectedMarker = null;
    private String selectedDiaryId = null;
    public String username; // 🎯 專門用來放名字


    // 這是你原本用來管理地圖標記的內部類別 (維持不變)
    class DiaryEntry {
        String id;
        LatLng location;
        String title;
        String mood;
        String authorName;
        int intensity;
        String text;
        String time;
        List<Uri> images;
        String imageUrl = "";
        String placeName = "";
        String authorAvatar = "";
        String userReaction = "";
        boolean canEdit = false;
        boolean isMine;
        int visibility;

        int heartCount = 0;
        int smileCount = 0;
        int surpriseCount = 0;
        int myReaction = 0;

        public DiaryEntry(LatLng loc, String title, String mood, int intensity, String text, String time, List<Uri> images, boolean isMine, int visibility) {
            this.location = loc; this.title = title; this.mood = mood; this.intensity = intensity;
            this.text = text;
            this.time = time; this.images = images; this.isMine = isMine; this.visibility = visibility;
        }

        public boolean hasRemoteImage() {
            return imageUrl != null && !imageUrl.trim().isEmpty();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_map);
        AdriftSystemUi.apply(this);

        sessionManager = SessionManager.getInstance(this);
        if (!sessionManager.hasToken()) {
            redirectToLogin();
            return;
        }
        diaryRepository = new DiaryRepository(this);
        userRepository = new UserRepository(this);
        friendRepository = new FriendRepository(this);
        markerManager = new MapMarkerManager(this);
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        locationPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                granted -> {
                    locationPermissionRequested = true;
                    if (granted) {
                        enableMyLocationLayer();
                        locateUser(true);
                    } else {
                        showMapStatus("無法使用定位，仍可瀏覽地圖日記", false);
                        setLocationButtonState("error");
                    }
                }
        );

        drawerLayout = findViewById(R.id.drawer_layout);
        initMapHud();
        ImageButton btnOpenDrawer = findViewById(R.id.btn_open_drawer);
        if (btnOpenDrawer != null) {
            btnOpenDrawer.setOnClickListener(v -> {
                if (drawerLayout != null) drawerLayout.openDrawer(GravityCompat.END);
            });
        }

        initNavigation();
        initToggleSwitch();
        initAppNavigation();

        diaryCardView = findViewById(R.id.diary_bottom_sheet);
        mapBlurOverlay = findViewById(R.id.map_blur_overlay); // 初始化遮罩層
        etDiaryTitle = findViewById(R.id.et_diary_title);
        diaryInput = findViewById(R.id.diary_input);
        moodSpinner = findViewById(R.id.mood_spinner);
        visibilitySpinner = findViewById(R.id.spinner_visibility);
        sbMoodIntensity = findViewById(R.id.sb_mood_intensity);
        tvIntensityLabel = findViewById(R.id.tv_intensity_label);
        btnCloseDiary = findViewById(R.id.btn_close_diary);
        rvImagePreview = findViewById(R.id.rv_image_preview);
        ivDiaryRemoteImage = findViewById(R.id.iv_diary_remote_image);
        tvDiaryMeta = findViewById(R.id.tv_diary_meta);
        btnSaveDiary = findViewById(R.id.btn_save);
        btnDeleteDiary = findViewById(R.id.btn_delete_diary);
        btnReactUnderstand = findViewById(R.id.btn_react_understand);
        btnReactHug = findViewById(R.id.btn_react_hug);
        btnReactRelate = findViewById(R.id.btn_react_relate);
        layoutReactions = findViewById(R.id.layout_reactions);

        if (btnCloseDiary != null) {
            btnCloseDiary.setOnClickListener(v -> hideDiaryCard());
        }

        if (sbMoodIntensity != null) {
            sbMoodIntensity.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (tvIntensityLabel != null) tvIntensityLabel.setText("強度 " + progress);
                }
                @Override public void onStartTrackingTouch(SeekBar seekBar) {}
                @Override public void onStopTrackingTouch(SeekBar seekBar) {}
            });
        }

        View fabAdd = findViewById(R.id.btn_top_add_diary);
        if (fabAdd != null) {
            fabAdd.setOnClickListener(v -> {
                clearSelectedDiaryMarker();
                clearDiaryForm();
                setDiaryFieldsEnabled(true);

                selectedImageUris.clear();
                if (imageAdapter != null) imageAdapter.notifyDataSetChanged();
                if (rvImagePreview != null) rvImagePreview.setVisibility(View.GONE);
                if (ivDiaryRemoteImage != null) ivDiaryRemoteImage.setVisibility(View.GONE);
                if (tvDiaryMeta != null) tvDiaryMeta.setVisibility(View.GONE);
                if (btnDeleteDiary != null) btnDeleteDiary.setVisibility(View.GONE);
                if (layoutReactions != null) layoutReactions.setVisibility(View.GONE);
                if (btnSaveDiary != null) btnSaveDiary.setVisibility(View.VISIBLE);
                setDiaryLoading(false);

                showDiaryCard("新增日記");
            });
        }

        if (rvImagePreview != null) {
            rvImagePreview.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
            imageAdapter = new ImageAdapter();
            rvImagePreview.setAdapter(imageAdapter);

            ItemTouchHelper itemTouchHelper = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(
                    ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT, 0) {
                @Override
                public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
                    if (diaryInput != null && !diaryInput.isEnabled()) return false;
                    int from = viewHolder.getBindingAdapterPosition();
                    int to = target.getBindingAdapterPosition();
                    if (from == RecyclerView.NO_POSITION || to == RecyclerView.NO_POSITION) return false;
                    Collections.swap(selectedImageUris, from, to);
                    imageAdapter.notifyItemMoved(from, to);
                    return true;
                }
                @Override
                public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {}
            });
            itemTouchHelper.attachToRecyclerView(rvImagePreview);
        }

        String[] moods = {"😊 開心", "😢 難過", "😎 平靜", "🤩 興奮", "😴 累了"};
        if (moodSpinner != null) {
            moodSpinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, moods));
        }

        String[] visibilities = {"🔒 私人", "👥 朋友", "👁️ 公開"};
        if (visibilitySpinner != null) {
            visibilitySpinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, visibilities));
        }

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map_fragment);
        if (mapFragment != null) mapFragment.getMapAsync(this);

        ActivityResultLauncher<Intent> photoPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        Intent data = result.getData();
                        if (data.getClipData() != null) {
                            int count = data.getClipData().getItemCount();
                            for (int i = 0; i < count; i++) {
                                Uri uri = data.getClipData().getItemAt(i).getUri();
                                if (!selectedImageUris.contains(uri)) selectedImageUris.add(uri);
                            }
                        } else if (data.getData() != null) {
                            Uri uri = data.getData();
                            if (!selectedImageUris.contains(uri)) selectedImageUris.add(uri);
                        }
                        if (imageAdapter != null) imageAdapter.notifyDataSetChanged();
                        if (rvImagePreview != null) rvImagePreview.setVisibility(View.VISIBLE);
                    }
                }
        );

        View imagePicker = findViewById(R.id.card_image_picker);
        if (imagePicker != null) {
            imagePicker.setOnClickListener(v -> {
                if (getCurrentDiaryEntry() != null) {
                    Toast.makeText(this, "圖片目前僅支援新增日記時上傳", Toast.LENGTH_SHORT).show();
                } else if (diaryInput != null && diaryInput.isEnabled()) {
                    Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                    intent.setType("image/*");
                    intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                    photoPickerLauncher.launch(Intent.createChooser(intent, "選擇照片"));
                } else {
                    Toast.makeText(this, "查看模式下無法修改照片", Toast.LENGTH_SHORT).show();
                }
            });
        }

        if (btnSaveDiary != null) {
            btnSaveDiary.setOnClickListener(v -> {
                if (diaryInput != null && diaryInput.isEnabled()) saveTrace();
                else Toast.makeText(this, "目前為閱讀模式，無法儲存修改", Toast.LENGTH_SHORT).show();
            });
        }
        if (btnDeleteDiary != null) {
            btnDeleteDiary.setOnClickListener(v -> confirmDeleteCurrentDiary());
        }
        if (btnReactUnderstand != null) {
            btnReactUnderstand.setOnClickListener(v -> reactToCurrentDiary("understand"));
        }
        if (btnReactHug != null) {
            btnReactHug.setOnClickListener(v -> reactToCurrentDiary("hug"));
        }
        if (btnReactRelate != null) {
            btnReactRelate.setOnClickListener(v -> reactToCurrentDiary("relate"));
        }
        if (ivDiaryRemoteImage != null) {
            ivDiaryRemoteImage.setOnClickListener(v -> openCurrentImagePreview());
        }
        if (previewController != null) {
            previewController.setOnOpenListener(v -> {
                DiaryEntry entry = getCurrentDiaryEntry();
                if (entry != null) {
                    showDiaryCard(entry.isMine && entry.canEdit ? "編輯日記" : "閱讀日記");
                    bindDiaryToSheet(entry);
                }
            });
            previewController.setOnCloseListener(v -> clearSelectedDiaryMarker());
        }
        handleInitialTab();
    }

    private void initMapHud() {
        btnCurrentLocation = findViewById(R.id.btn_current_location);
        mapStatusChip = findViewById(R.id.map_status_chip);
        mapEmptyChip = findViewById(R.id.map_empty_chip);
        tvMapStatus = findViewById(R.id.tv_map_status);
        btnRetryDiaries = findViewById(R.id.btn_retry_diaries);
        mapIdentityChip = findViewById(R.id.map_identity_chip);
        tvMapIdentityName = findViewById(R.id.tv_map_identity_name);
        tvMapIdentityCode = findViewById(R.id.tv_map_identity_code);
        tvMapIdentityAvatar = findViewById(R.id.tv_map_identity_avatar);
        ivMapIdentityAvatar = findViewById(R.id.iv_map_identity_avatar);
        previewController = new DiaryPreviewController(this);

        if (btnCurrentLocation != null) {
            btnCurrentLocation.setOnClickListener(v -> {
                v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                clearSelectedDiaryMarker();
                locateUser(true);
            });
        }
        if (btnRetryDiaries != null) {
            btnRetryDiaries.setOnClickListener(v -> loadDiariesFromServer());
        }
        if (mapEmptyChip != null) {
            mapEmptyChip.setOnClickListener(v -> {
                View add = findViewById(R.id.btn_top_add_diary);
                if (add != null) add.performClick();
            });
        }
        if (mapIdentityChip != null) {
            mapIdentityChip.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        }
        bindMapIdentity(sessionManager == null ? null : sessionManager.getUser());
    }

    private void initAppNavigation() {
        mapNavMap = findViewById(R.id.map_nav_map);
        mapNavFriends = findViewById(R.id.map_nav_friends);
        mapNavExplore = findViewById(R.id.map_nav_explore);
        mapNavSettings = findViewById(R.id.map_nav_settings);

        if (mapNavMap != null) mapNavMap.setOnClickListener(v -> selectMapNav("map"));
        if (mapNavFriends != null) {
            mapNavFriends.setOnClickListener(v -> {
                selectMapNav("friends");
                AppNavigator.openTopLevel(this, FriendsActivity.class);
            });
        }
        if (mapNavExplore != null) {
            mapNavExplore.setOnClickListener(v -> {
                selectMapNav("explore");
                AppNavigator.openTopLevel(this, ExploreActivity.class);
            });
        }
        if (mapNavSettings != null) {
            mapNavSettings.setOnClickListener(v -> {
                selectMapNav("settings");
                startActivity(new Intent(this, InsightActivity.class));
            });
        }
        selectMapNav("map");
    }

    private void handleInitialTab() {
        String initialTab = getIntent().getStringExtra("initial_tab");
        if ("friends".equals(initialTab) && mapNavFriends != null) {
            mapNavFriends.performClick();
        } else if ("explore".equals(initialTab) && mapNavExplore != null) {
            mapNavExplore.performClick();
        }
    }

    private void selectMapNav(String tab) {
        TextView[] items = {mapNavMap, mapNavFriends, mapNavExplore, mapNavSettings};
        String[] names = {"map", "friends", "explore", "settings"};
        for (int i = 0; i < items.length; i++) {
            TextView item = items[i];
            if (item == null) continue;
            boolean selected = names[i].equals(tab);
            item.setSelected(selected);
            item.setTextColor(selected ? Color.WHITE : Color.rgb(185, 217, 232));
        }
    }

    private void showDiaryCard(String headerText) {
        if (diaryCardView != null) {
            TextView tvHeader = diaryCardView.findViewById(R.id.tv_diary_header);
            if (tvHeader != null) tvHeader.setText(headerText);
            diaryCardView.setVisibility(View.VISIBLE);
        }
        if (mapBlurOverlay != null) mapBlurOverlay.setVisibility(View.VISIBLE);
    }

    private void hideDiaryCard() {
        if (diaryCardView != null) diaryCardView.setVisibility(View.GONE);
        if (mapBlurOverlay != null) mapBlurOverlay.setVisibility(View.GONE);
    }

    private void clearDiaryForm() {
        if (etDiaryTitle != null) etDiaryTitle.setText("");
        if (diaryInput != null) diaryInput.setText("");
        if (sbMoodIntensity != null) sbMoodIntensity.setProgress(3);
        if (moodSpinner != null) moodSpinner.setSelection(0);
        if (visibilitySpinner != null) visibilitySpinner.setSelection(0);
    }

    private void setDiaryFieldsEnabled(boolean enabled) {
        if (etDiaryTitle != null) etDiaryTitle.setEnabled(enabled);
        if (diaryInput != null) diaryInput.setEnabled(enabled);
        if (sbMoodIntensity != null) sbMoodIntensity.setEnabled(enabled);
        if (moodSpinner != null) moodSpinner.setEnabled(enabled);
        if (visibilitySpinner != null) visibilitySpinner.setEnabled(enabled);
    }

    private void setDiaryLoading(boolean loading) {
        isDiaryRequestInFlight = loading;
        if (btnSaveDiary != null) {
            btnSaveDiary.setEnabled(!loading);
            btnSaveDiary.setText(loading ? "同步中..." : "保存日記");
        }
        if (btnDeleteDiary != null) btnDeleteDiary.setEnabled(!loading);
        if (btnReactUnderstand != null) btnReactUnderstand.setEnabled(!loading);
        if (btnReactHug != null) btnReactHug.setEnabled(!loading);
        if (btnReactRelate != null) btnReactRelate.setEnabled(!loading);
    }

    private void initToggleSwitch() {
        TextView btnSwitchMine = findViewById(R.id.btn_switch_mine);
        LinearLayout btnSwitchExplore = findViewById(R.id.btn_switch_explore);

        if (btnSwitchMine != null && btnSwitchExplore != null) {
            btnSwitchMine.setOnClickListener(v -> {
                btnSwitchMine.setBackgroundResource(R.drawable.bg_adrift_nav_item);
                btnSwitchMine.setTextColor(Color.WHITE);
                btnSwitchExplore.setBackground(null);
                TextView tvExp = getExploreSwitchText(btnSwitchExplore);
                if (tvExp != null) tvExp.setTextColor(Color.parseColor("#99FFFFFF"));
            });

            btnSwitchExplore.setOnClickListener(v -> {
                btnSwitchExplore.setBackgroundResource(R.drawable.bg_adrift_nav_item);
                TextView tvExp = getExploreSwitchText(btnSwitchExplore);
                if (tvExp != null) tvExp.setTextColor(Color.WHITE);
                btnSwitchMine.setBackground(null);
                btnSwitchMine.setTextColor(Color.parseColor("#99FFFFFF"));
            });
        }
    }

    private TextView getExploreSwitchText(LinearLayout btnSwitchExplore) {
        if (btnSwitchExplore == null || btnSwitchExplore.getChildCount() == 0) return null;
        View child = btnSwitchExplore.getChildAt(btnSwitchExplore.getChildCount() - 1);
        return child instanceof TextView ? (TextView) child : null;
    }

    private void confirmDeleteCurrentDiary() {
        DiaryEntry entry = getCurrentDiaryEntry();
        if (entry == null || entry.id == null || entry.id.isEmpty()) return;
        if (!entry.isMine) {
            Toast.makeText(this, "只能刪除自己的日記", Toast.LENGTH_SHORT).show();
            return;
        }
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("刪除日記")
                .setMessage("確定要刪除這篇日記嗎？")
                .setPositiveButton("刪除", (dialog, which) -> deleteDiaryFromBackend(entry))
                .setNegativeButton("取消", null)
                .show();
    }

    private void deleteDiaryFromBackend(DiaryEntry entry) {
        if (isDiaryRequestInFlight) return;
        setDiaryLoading(true);
        diaryRepository.deleteDiary(entry.id, new RepositoryCallback<EmptyResponse>() {
            @Override
            public void onSuccess(EmptyResponse value) {
                if (!isActive()) return;
                setDiaryLoading(false);
                removeDiaryMarker(entry.id);
                clearSelectedDiaryMarker();
                hideDiaryCard();
                updateStatistics();
                updateDiaryList();
                updateMapEmptyState();
                Toast.makeText(MapActivity.this, "日記已刪除", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onError(String message) {
                if (!isActive()) return;
                setDiaryLoading(false);
                Toast.makeText(MapActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void reactToCurrentDiary(String type) {
        DiaryEntry entry = getCurrentDiaryEntry();
        if (entry == null || entry.id == null || entry.id.isEmpty()) return;
        if (isDiaryRequestInFlight) return;
        setDiaryLoading(true);
        diaryRepository.reactToDiary(entry.id, type, new RepositoryCallback<ReactionUpdateData>() {
            @Override
            public void onSuccess(ReactionUpdateData data) {
                if (!isActive()) return;
                setDiaryLoading(false);
                entry.heartCount = data.getReactions().understand;
                entry.smileCount = data.getReactions().hug;
                entry.surpriseCount = data.getReactions().relate;
                entry.userReaction = data.userReaction == null ? "" : data.userReaction;
                bindReactionButtons(entry);
                updateDiaryList();
            }

            @Override
            public void onError(String message) {
                if (!isActive()) return;
                setDiaryLoading(false);
                Toast.makeText(MapActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void initNavigation() {
        NavigationView navView = findViewById(R.id.nav_view);
        if (navView != null) {
            tabMine = navView.findViewById(R.id.tab_view_mine);
            tabFriends = navView.findViewById(R.id.tab_view_friends);
            tvCountMine = navView.findViewById(R.id.tv_my_diary_count);
            tvCountVisible = navView.findViewById(R.id.tv_visible_memory_count);
            btnPriv = navView.findViewById(R.id.filter_private);
            btnFrdOnly = navView.findViewById(R.id.filter_friends);
            btnPub = navView.findViewById(R.id.filter_public);
            rvDiaryList = navView.findViewById(R.id.rv_diary_history);
            View openFriendsPage = navView.findViewById(R.id.btn_open_friends_page);
            if (openFriendsPage != null) {
                openFriendsPage.setOnClickListener(v -> startActivity(new Intent(this, FriendsActivity.class)));
            }

            layoutMyDiaryRoot = navView.findViewById(R.id.layout_my_diary_root);
            layoutFriendManagement = navView.findViewById(R.id.layout_friend_management);
            etSearchFriend = navView.findViewById(R.id.et_search_friend);
            btnSearchUser = navView.findViewById(R.id.btn_search_user);

            // 搜尋按鈕邏輯 (已存在)
            if (btnSearchUser != null) {
                btnSearchUser.setOnClickListener(v -> {
                    String name = etSearchFriend.getText().toString().trim();
                    if (!name.isEmpty()) performUserSearch(name);
                });
            }

            rvSentRequests = navView.findViewById(R.id.rv_sent_requests);
            rvReceivedRequests = navView.findViewById(R.id.rv_received_requests);
            rvFriendsList = navView.findViewById(R.id.rv_friends_list);
            tvEmptySent = navView.findViewById(R.id.tv_empty_sent);
            tvEmptyReceived = navView.findViewById(R.id.tv_empty_received);
            tvEmptyFriends = navView.findViewById(R.id.tv_empty_friends);

            if (rvDiaryList != null) {
                rvDiaryList.setLayoutManager(new LinearLayoutManager(this));
                diaryListAdapter = new DiaryListAdapter();
                rvDiaryList.setAdapter(diaryListAdapter);
            }

            if (rvFriendsList != null) rvFriendsList.setLayoutManager(new LinearLayoutManager(this));
            if (rvSentRequests != null) rvSentRequests.setLayoutManager(new LinearLayoutManager(this));
            if (rvReceivedRequests != null) rvReceivedRequests.setLayoutManager(new LinearLayoutManager(this));

            if (tabMine != null) {
                tabMine.setOnClickListener(v -> {
                    isShowingMine = true;
                    tabMine.setBackgroundResource(R.drawable.bg_login_card);
                    tabMine.setTextColor(Color.WHITE);
                    if (tabFriends != null) {
                        tabFriends.setBackground(null);
                        tabFriends.setTextColor(Color.parseColor("#99FFFFFF"));
                    }
                    if (layoutMyDiaryRoot != null) layoutMyDiaryRoot.setVisibility(View.VISIBLE);
                    if (layoutFriendManagement != null) layoutFriendManagement.setVisibility(View.GONE);
                    updateDiaryList();
                });
            }

            if (tabFriends != null) {
                tabFriends.setOnClickListener(v -> {
                    isShowingMine = false;
                    tabFriends.setBackgroundResource(R.drawable.bg_login_card);
                    tabFriends.setTextColor(Color.WHITE);
                    if (tabMine != null) {
                        tabMine.setBackground(null);
                        tabMine.setTextColor(Color.parseColor("#99FFFFFF"));
                    }
                    if (layoutMyDiaryRoot != null) layoutMyDiaryRoot.setVisibility(View.GONE);
                    if (layoutFriendManagement != null) layoutFriendManagement.setVisibility(View.VISIBLE);

                    // 💡 關鍵修正：點開好友分頁時，除了更新狀態，還要「抓取最新邀請清單」
                    updateFriendEmptyStates();
                    loadFriendData();
                });
            }

            if (btnPriv != null) btnPriv.setOnClickListener(v -> handleFilterClick(0, btnPriv));
            if (btnFrdOnly != null) btnFrdOnly.setOnClickListener(v -> handleFilterClick(1, btnFrdOnly));
            if (btnPub != null) btnPub.setOnClickListener(v -> handleFilterClick(2, btnPub));

            updateStatistics();
            updateDiaryList();

            // 💡 採用相同的 findViewById 方式獲取新設計的底欄元件
            View btnLogout = navView.findViewById(R.id.btn_logout);
            if (btnLogout != null) {
                btnLogout.setOnClickListener(v -> {
                    sessionManager.clear();
                    Intent intent = new Intent(this, LoginActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    finish();
                });
            }

            // 2. 個人檔案區域按鈕：點擊整塊區域直接跳轉至設定頁面
            View btnProfileSettings = navView.findViewById(R.id.btn_profile_settings);
            if (btnProfileSettings != null) {
                btnProfileSettings.setOnClickListener(v -> {
                    Intent intent = new Intent(this, SettingsActivity.class);
                    startActivity(intent);
                });
            }

            View targetContainer = navView.getHeaderCount() > 0 ? navView.getHeaderView(0) : navView;
            android.widget.TextView tvBotAvatar = targetContainer.findViewById(R.id.tv_bottom_avatar);
            android.widget.TextView tvBotName = targetContainer.findViewById(R.id.tv_bottom_name);
            android.widget.TextView tvBotUser = targetContainer.findViewById(R.id.tv_bottom_username);
            bindBottomProfile(sessionManager.getUser(), tvBotAvatar, tvBotName, tvBotUser);
            userRepository.getMe(new RepositoryCallback<UserDto>() {
                @Override
                public void onSuccess(UserDto user) {
                    bindBottomProfile(user, tvBotAvatar, tvBotName, tvBotUser);
                }

                @Override
                public void onError(String message) {
                }
            });
        }
    }

    private void bindBottomProfile(UserDto user, TextView avatar, TextView name, TextView code) {
        if (user == null) return;
        String displayName = user.getDisplayName();
        if (name != null) name.setText(displayName);
        if (code != null) code.setText("@" + user.getUserCode());
        if (avatar != null && !displayName.isEmpty()) avatar.setText(displayName.substring(0, 1));
        bindMapIdentity(user);
    }

    private void bindMapIdentity(UserDto user) {
        if (user == null) return;
        String displayName = user.getDisplayName();
        String userCode = user.getUserCode();
        if (tvMapIdentityName != null) tvMapIdentityName.setText(displayName);
        if (tvMapIdentityCode != null) tvMapIdentityCode.setText(userCode.isEmpty() ? "@adrift" : "@" + userCode);

        String firstLetter = displayName.isEmpty() ? "A" : displayName.substring(0, 1);
        if (tvMapIdentityAvatar != null) tvMapIdentityAvatar.setText(firstLetter);
        if (ivMapIdentityAvatar == null || tvMapIdentityAvatar == null) return;

        if (user.avatar != null && !user.avatar.trim().isEmpty()) {
            ivMapIdentityAvatar.setVisibility(View.VISIBLE);
            tvMapIdentityAvatar.setVisibility(View.GONE);
            Glide.with(this)
                    .load(ImageUrlResolver.resolve(user.avatar))
                    .placeholder(R.drawable.bg_adrift_avatar)
                    .error(R.drawable.bg_adrift_avatar)
                    .centerCrop()
                    .into(ivMapIdentityAvatar);
        } else {
            Glide.with(this).clear(ivMapIdentityAvatar);
            ivMapIdentityAvatar.setVisibility(View.GONE);
            tvMapIdentityAvatar.setVisibility(View.VISIBLE);
        }
    }

    private void redirectToLogin() {
        if (sessionManager != null) sessionManager.clear();
        AppNavigator.openLoginAndClear(this);
    }
    private void handleFilterClick(int filterType, Button btn) {
        if (currentFilter == filterType) {
            currentFilter = -1;
            updateFilterUI(null);
        } else {
            currentFilter = filterType;
            updateFilterUI(btn);
        }
        updateDiaryList();
    }

    private void updateFriendEmptyStates() {
        if (tvEmptySent != null) tvEmptySent.setVisibility(View.VISIBLE);
        if (tvEmptyReceived != null) tvEmptyReceived.setVisibility(View.VISIBLE);
        if (tvEmptyFriends != null) tvEmptyFriends.setVisibility(View.VISIBLE);
    }

    private void updateDiaryList() {
        displayedDiaries.clear();
        for (DiaryEntry entry : markerDataMap.values()) {
            // 🎯 修正核心：
            // 1. 如果 currentFilter == -1 (代表現在沒有按[私人/朋友/公開]過濾，想看全部) -> 直接不卡 isShowingMine，全部放行！
            // 2. 如果有按特定權限過濾，才去嚴格比對分頁。
            if (currentFilter == -1 || (isShowingMine ? entry.isMine : true)) {
                if (currentFilter == -1 || entry.visibility == currentFilter) {
                    displayedDiaries.add(entry);
                }
            }
        }
        Collections.sort(displayedDiaries, (d1, d2) -> d2.time.compareTo(d1.time));
        if (diaryListAdapter != null) diaryListAdapter.notifyDataSetChanged();
    }

    private void updateFilterUI(Button selected) {
        Button[] btns = {btnPriv, btnFrdOnly, btnPub};
        for (Button b : btns) {
            if (b == null) continue;
            if (b == selected) {
                b.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#00E5FF")));
                b.setTextColor(Color.BLACK);
            } else {
                b.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#33FFFFFF")));
                b.setTextColor(Color.WHITE);
            }
        }
    }

    private void updateStatistics() {
        if (tvCountMine != null && tvCountVisible != null) {
            int mineCount = 0;
            for (DiaryEntry entry : markerDataMap.values()) {
                if (entry.isMine) mineCount++;
            }
            tvCountMine.setText(String.valueOf(mineCount));
            tvCountVisible.setText(String.valueOf(markerDataMap.size()));
        }
    }

    private DiaryEntry getCurrentDiaryEntry() {
        if (selectedDiaryId != null && markerDataMap.containsKey(selectedDiaryId)) {
            return markerDataMap.get(selectedDiaryId);
        }
        if (lastSelectedMarker == null) return null;
        String diaryId = markerDiaryIdMap.get(lastSelectedMarker.getId());
        if (diaryId == null) return markerDataMap.get(lastSelectedMarker.getId());
        return markerDataMap.get(diaryId);
    }

    private void removeDiaryMarker(String diaryId) {
        if (diaryId == null || diaryId.isEmpty()) return;
        Marker marker = diaryMarkerMap.remove(diaryId);
        if (marker != null) {
            markerDiaryIdMap.remove(marker.getId());
            marker.remove();
        }
        markerDataMap.remove(diaryId);
        if (diaryId.equals(selectedDiaryId)) {
            selectedDiaryId = null;
            if (previewController != null) previewController.hide(getResources().getInteger(R.integer.motion_fast));
        }
    }

    private void addOrUpdateDiaryMarker(DiaryEntry entry) {
        if (entry == null || entry.id == null || entry.id.isEmpty() || mMap == null) return;
        removeDiaryMarker(entry.id);
        Marker marker = mMap.addMarker(new MarkerOptions()
                .position(entry.location)
                .title(entry.title == null || entry.title.isEmpty() ? entry.mood : entry.title)
                .snippet(entry.mood)
                .anchor(0.5f, 0.5f)
                .icon(markerManager.iconFor(entry.mood, entry.isMine, entry.id.equals(selectedDiaryId))));
        if (marker != null) {
            markerDataMap.put(entry.id, entry);
            diaryMarkerMap.put(entry.id, marker);
            markerDiaryIdMap.put(marker.getId(), entry.id);
        }
    }

    private void selectDiaryMarker(String diaryId, boolean animateCamera) {
        if (diaryId == null || diaryId.isEmpty()) return;
        String previousId = selectedDiaryId;
        selectedDiaryId = diaryId;
        refreshMarkerIcon(previousId);
        refreshMarkerIcon(selectedDiaryId);

        Marker marker = diaryMarkerMap.get(diaryId);
        DiaryEntry entry = markerDataMap.get(diaryId);
        if (marker != null) lastSelectedMarker = marker;
        if (entry == null) return;

        if (previewController != null) previewController.show(buildPreviewData(entry), getResources().getInteger(R.integer.motion_normal));
        if (animateCamera) focusCameraForPreview(entry);
        View root = findViewById(R.id.drawer_layout);
        if (root != null) root.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
    }

    private void clearSelectedDiaryMarker() {
        String previousId = selectedDiaryId;
        selectedDiaryId = null;
        lastSelectedMarker = null;
        refreshMarkerIcon(previousId);
        if (previewController != null) previewController.hide(getResources().getInteger(R.integer.motion_normal));
        if (mMap != null) mMap.setPadding(0, 0, 0, 0);
    }

    private void refreshMarkerIcon(String diaryId) {
        if (diaryId == null || diaryId.isEmpty()) return;
        Marker marker = diaryMarkerMap.get(diaryId);
        DiaryEntry entry = markerDataMap.get(diaryId);
        if (marker != null && entry != null && markerManager != null) {
            marker.setIcon(markerManager.iconFor(entry.mood, entry.isMine, diaryId.equals(selectedDiaryId)));
            marker.setAnchor(0.5f, 0.5f);
        }
    }

    private DiaryPreviewController.PreviewData buildPreviewData(DiaryEntry entry) {
        DiaryPreviewController.PreviewData data = new DiaryPreviewController.PreviewData();
        data.title = entry.title == null || entry.title.isEmpty() ? entry.mood : entry.title;
        data.text = entry.text == null ? "" : entry.text;
        String author = entry.authorName == null || entry.authorName.isEmpty() ? "Adrift 使用者" : entry.authorName;
        data.author = "@" + author;
        String place = entry.placeName == null || entry.placeName.isEmpty() ? "地圖日記" : entry.placeName;
        data.meta = place + " · " + formatDate(entry.time);
        data.mood = entry.mood + " · 強度 " + entry.intensity;
        data.reactions = "懂 " + entry.heartCount + " · 抱 " + entry.smileCount + " · 共鳴 " + entry.surpriseCount;
        data.imageUrl = entry.imageUrl;
        return data;
    }

    private void focusCameraForPreview(DiaryEntry entry) {
        if (mMap == null || entry == null) return;
        int bottomPadding = (int) (getResources().getDisplayMetrics().density * 230f);
        mMap.setPadding(0, 0, 0, bottomPadding);
        float zoom = Math.max(mMap.getCameraPosition().zoom, 15f);
        CameraPosition position = new CameraPosition.Builder()
                .target(entry.location)
                .zoom(zoom)
                .tilt(34f)
                .bearing(mMap.getCameraPosition().bearing)
                .build();
        mMap.animateCamera(
                CameraUpdateFactory.newCameraPosition(position),
                getResources().getInteger(R.integer.motion_slow),
                null
        );
    }

    private DiaryEntry buildEntryFromDto(DiaryDto diary) {
        DiaryUiModel uiDiary = DiaryMapper.toUiModel(diary, sessionManager.getUserId());
        if (uiDiary == null) return null;
        DiaryEntry entry = new DiaryEntry(
                new LatLng(uiDiary.lat, uiDiary.lng),
                uiDiary.title,
                DiaryMapper.moodLabel(uiDiary.moodType),
                uiDiary.intensity,
                uiDiary.text,
                uiDiary.time,
                new ArrayList<>(),
                uiDiary.isMine,
                uiDiary.visibility
        );
        entry.id = uiDiary.id;
        entry.authorName = uiDiary.authorName;
        entry.authorAvatar = uiDiary.authorAvatar;
        entry.imageUrl = uiDiary.imageUrl;
        entry.placeName = uiDiary.placeName;
        entry.userReaction = uiDiary.userReaction;
        entry.canEdit = uiDiary.canEdit;
        entry.heartCount = uiDiary.understandCount;
        entry.smileCount = uiDiary.hugCount;
        entry.surpriseCount = uiDiary.relateCount;
        return entry;
    }

    private void bindDiaryToSheet(DiaryEntry entry) {
        if (entry == null) return;
        if (etDiaryTitle != null) etDiaryTitle.setText(entry.title);
        if (diaryInput != null) diaryInput.setText(entry.text);
        if (sbMoodIntensity != null) sbMoodIntensity.setProgress(entry.intensity);
        if (moodSpinner != null) {
            for (int i = 0; i < moodSpinner.getCount(); i++) {
                if (moodSpinner.getItemAtPosition(i).toString().contains(entry.mood)) {
                    moodSpinner.setSelection(i);
                    break;
                }
            }
        }
        if (visibilitySpinner != null) visibilitySpinner.setSelection(entry.visibility);

        selectedImageUris.clear();
        if (imageAdapter != null) imageAdapter.notifyDataSetChanged();
        if (rvImagePreview != null) rvImagePreview.setVisibility(View.GONE);

        bindRemoteImage(entry);
        bindDiaryMeta(entry);
        bindReactionButtons(entry);
        setDiaryFieldsEnabled(entry.isMine && entry.canEdit);
        if (btnDeleteDiary != null) btnDeleteDiary.setVisibility(entry.isMine ? View.VISIBLE : View.GONE);
        if (layoutReactions != null) layoutReactions.setVisibility(View.VISIBLE);
        if (btnSaveDiary != null) btnSaveDiary.setVisibility(entry.isMine ? View.VISIBLE : View.GONE);
    }

    private void bindRemoteImage(DiaryEntry entry) {
        if (ivDiaryRemoteImage == null) return;
        if (entry.hasRemoteImage()) {
            ivDiaryRemoteImage.setVisibility(View.VISIBLE);
            Glide.with(this)
                    .load(ImageUrlResolver.resolve(entry.imageUrl))
                    .placeholder(R.drawable.bg_image_placeholder)
                    .error(R.drawable.bg_image_placeholder)
                    .centerCrop()
                    .into(ivDiaryRemoteImage);
        } else {
            ivDiaryRemoteImage.setVisibility(View.GONE);
            Glide.with(this).clear(ivDiaryRemoteImage);
        }
    }

    private void bindDiaryMeta(DiaryEntry entry) {
        if (tvDiaryMeta == null) return;
        String author = entry.authorName == null || entry.authorName.isEmpty() ? "Adrift" : entry.authorName;
        String place = entry.placeName == null || entry.placeName.isEmpty() ? "" : " · " + entry.placeName;
        String visibility = entry.visibility == 0 ? "私人" : entry.visibility == 1 ? "朋友" : "公開";
        tvDiaryMeta.setText("@" + author + place + " · " + visibility + " · " + formatDate(entry.time));
        tvDiaryMeta.setVisibility(View.VISIBLE);
    }

    private void bindReactionButtons(DiaryEntry entry) {
        if (btnReactUnderstand != null) btnReactUnderstand.setText(("understand".equals(entry.userReaction) ? "✓ " : "") + "懂 " + entry.heartCount);
        if (btnReactHug != null) btnReactHug.setText(("hug".equals(entry.userReaction) ? "✓ " : "") + "抱 " + entry.smileCount);
        if (btnReactRelate != null) btnReactRelate.setText(("relate".equals(entry.userReaction) ? "✓ " : "") + "共鳴 " + entry.surpriseCount);
    }

    private void openCurrentImagePreview() {
        DiaryEntry entry = getCurrentDiaryEntry();
        if (entry == null || !entry.hasRemoteImage()) return;
        Intent intent = new Intent(this, ImagePreviewActivity.class);
        intent.putExtra(ImagePreviewActivity.EXTRA_IMAGE_URL, entry.imageUrl);
        startActivity(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mapReady) enableMyLocationLayer();
    }

    @Override
    protected void onDestroy() {
        destroyed = true;
        if (mapStatusChip != null && mapStatusDismissRunnable != null) {
            mapStatusChip.removeCallbacks(mapStatusDismissRunnable);
            mapStatusDismissRunnable = null;
        }
        super.onDestroy();
    }

    private boolean isActive() {
        return !destroyed && !isFinishing() && !isDestroyed();
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        mapReady = true;
        try {
            googleMap.setMapStyle(MapStyleOptions.loadRawResourceStyle(this, R.raw.map_style_dark));
        } catch (Exception ignored) {}

        mMap.getUiSettings().setMyLocationButtonEnabled(false);
        mMap.getUiSettings().setCompassEnabled(true);
        mMap.getUiSettings().setMapToolbarEnabled(false);
        enableMyLocationLayer();

        // 🎯 呼叫下載接收雲端資料
        loadDiariesFromServer();

        mMap.setOnMarkerClickListener(marker -> {
            lastSelectedMarker = marker;
            DiaryEntry entry = getCurrentDiaryEntry();
            if (entry != null) {
                selectDiaryMarker(entry.id, true);
            }
            return true;
        });

        mMap.setOnMapClickListener(latLng -> clearSelectedDiaryMarker());
        mMap.setOnCameraMoveStartedListener(reason -> {
            if (reason == GoogleMap.OnCameraMoveStartedListener.REASON_GESTURE) {
                setLocationButtonState("normal");
            }
        });
    }

    private void enableMyLocationLayer() {
        if (mMap == null) return;
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            try {
                mMap.setMyLocationEnabled(true);
            } catch (SecurityException ignored) {
                showMapStatus("定位權限無法啟用", false);
            }
            if (!locationPermissionRequested) locateUser(false);
        } else if (!locationPermissionRequested) {
            locationPermissionRequested = true;
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION);
        }
    }

    private void locateUser(boolean explicit) {
        if (mMap == null || isLocating) return;
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            if (!locationPermissionRequested) {
                locationPermissionRequested = true;
                locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION);
            } else if (explicit) {
                showMapStatus("請允許定位權限後再回到目前位置", false);
                setLocationButtonState("error");
            }
            return;
        }

        isLocating = true;
        setLocationButtonState("locating");
        fusedLocationClient.getLastLocation()
                .addOnSuccessListener(this, location -> {
                    if (!isActive()) return;
                    isLocating = false;
                    if (location == null) {
                        showMapStatus("暫時無法取得目前位置", false);
                        setLocationButtonState("error");
                        return;
                    }
                    LatLng target = new LatLng(location.getLatitude(), location.getLongitude());
                    float currentZoom = mMap.getCameraPosition().zoom;
                    float zoom = currentZoom < 11f ? 14.5f : Math.max(currentZoom, 14.5f);
                    CameraPosition position = new CameraPosition.Builder()
                            .target(target)
                            .zoom(zoom)
                            .tilt(32f)
                            .bearing(mMap.getCameraPosition().bearing)
                            .build();
                    mMap.animateCamera(
                            CameraUpdateFactory.newCameraPosition(position),
                            getResources().getInteger(R.integer.motion_slow),
                            null
                    );
                    setLocationButtonState("success");
                    if (btnCurrentLocation != null) btnCurrentLocation.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                    if (explicit) showMapStatus("已回到目前位置", false);
                })
                .addOnFailureListener(this, error -> {
                    if (!isActive()) return;
                    isLocating = false;
                    showMapStatus("定位失敗，請稍後再試", false);
                    setLocationButtonState("error");
                });
    }

    private void setLocationButtonState(String state) {
        if (btnCurrentLocation == null) return;
        if ("locating".equals(state)) {
            btnCurrentLocation.setEnabled(false);
            btnCurrentLocation.setAlpha(0.58f);
            btnCurrentLocation.animate().rotationBy(180f).setDuration(getResources().getInteger(R.integer.motion_normal)).start();
        } else if ("success".equals(state)) {
            btnCurrentLocation.setEnabled(true);
            btnCurrentLocation.setAlpha(1f);
            btnCurrentLocation.animate().scaleX(1.08f).scaleY(1.08f).setDuration(getResources().getInteger(R.integer.motion_fast))
                    .withEndAction(() -> btnCurrentLocation.animate().scaleX(1f).scaleY(1f).setDuration(getResources().getInteger(R.integer.motion_fast)).start())
                    .start();
        } else if ("error".equals(state)) {
            btnCurrentLocation.setEnabled(true);
            btnCurrentLocation.setAlpha(0.82f);
        } else {
            btnCurrentLocation.setEnabled(true);
            btnCurrentLocation.setAlpha(1f);
        }
    }

    private void showMapStatus(String message, boolean retry) {
        if (mapStatusChip == null || tvMapStatus == null) return;
        if (mapStatusDismissRunnable != null) {
            mapStatusChip.removeCallbacks(mapStatusDismissRunnable);
            mapStatusDismissRunnable = null;
        }
        tvMapStatus.setText(message);
        if (btnRetryDiaries != null) btnRetryDiaries.setVisibility(retry ? View.VISIBLE : View.GONE);
        mapStatusChip.setVisibility(View.VISIBLE);
        mapStatusChip.setAlpha(0f);
        mapStatusChip.animate().alpha(1f).setDuration(getResources().getInteger(R.integer.motion_normal)).start();
        if (!retry) {
            mapStatusDismissRunnable = () -> {
                if (mapStatusChip != null && mapStatusChip.getVisibility() == View.VISIBLE && btnRetryDiaries != null && btnRetryDiaries.getVisibility() != View.VISIBLE) {
                    mapStatusChip.animate().alpha(0f).setDuration(getResources().getInteger(R.integer.motion_normal))
                            .withEndAction(() -> mapStatusChip.setVisibility(View.GONE))
                            .start();
                }
            };
            mapStatusChip.postDelayed(mapStatusDismissRunnable, 2200);
        }
    }

    private void hideMapStatus() {
        if (mapStatusChip == null) return;
        if (mapStatusDismissRunnable != null) {
            mapStatusChip.removeCallbacks(mapStatusDismissRunnable);
            mapStatusDismissRunnable = null;
        }
        mapStatusChip.animate().alpha(0f).setDuration(getResources().getInteger(R.integer.motion_fast))
                .withEndAction(() -> mapStatusChip.setVisibility(View.GONE))
                .start();
    }

    private void updateMapEmptyState() {
        if (mapEmptyChip == null) return;
        boolean empty = markerDataMap.isEmpty();
        mapEmptyChip.setVisibility(empty ? View.VISIBLE : View.GONE);
        mapEmptyChip.setAlpha(empty ? 1f : 0f);
    }

    // 🎯 從雲端同步加載所有你看得到（包含好友、公開）的日記
    private void loadDiariesFromServer() {
        showMapStatus("讀取地圖日記...", false);
        diaryRepository.getDiaries(new RepositoryCallback<List<DiaryDto>>() {
            @Override
            public void onSuccess(List<DiaryDto> diaries) {
                if (!isActive()) return;
                if (mMap != null) {
                    mMap.clear();
                }
                markerDataMap.clear();
                diaryMarkerMap.clear();
                markerDiaryIdMap.clear();
                selectedDiaryId = null;
                lastSelectedMarker = null;
                if (previewController != null) previewController.hide(getResources().getInteger(R.integer.motion_fast));
                if (mMap != null) mMap.setPadding(0, 0, 0, 0);

                for (DiaryDto diary : diaries) {
                    DiaryEntry internalEntry = buildEntryFromDto(diary);
                    if (internalEntry != null) addOrUpdateDiaryMarker(internalEntry);
                }

                updateStatistics();
                updateDiaryList();
                updateMapEmptyState();
                hideMapStatus();
            }

            @Override
            public void onError(String message) {
                if (!isActive()) return;
                showMapStatus(message == null || message.isEmpty() ? "讀取地圖日記失敗" : message, true);
                updateMapEmptyState();
            }
        });
    }


    private void saveTrace() {
        if (isDiaryRequestInFlight) return;
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "請允許定位後再儲存日記", Toast.LENGTH_SHORT).show();
            return;
        }
        fusedLocationClient.getLastLocation().addOnSuccessListener(this, location -> {
            if (!isActive()) return;
            if (location != null) {
                String title = (etDiaryTitle != null) ? etDiaryTitle.getText().toString().trim() : "";
                String selectedMood = (moodSpinner != null) ? moodSpinner.getSelectedItem().toString() : "";
                int intensity = (sbMoodIntensity != null) ? sbMoodIntensity.getProgress() : 3;
                String diaryText = (diaryInput != null) ? diaryInput.getText().toString().trim() : "";
                int visibility = (visibilitySpinner != null) ? visibilitySpinner.getSelectedItemPosition() : 0;
                if (title.isEmpty() || diaryText.isEmpty()) {
                    Toast.makeText(this, "請填寫標題與文字", Toast.LENGTH_SHORT).show();
                    return;
                }

                DiaryEntry existing = getCurrentDiaryEntry();
                if (existing != null && existing.id != null && !existing.id.isEmpty()) {
                    updateDiaryOnBackend(existing, title, selectedMood, intensity, diaryText, visibility, location);
                } else {
                    LatLng savePos = new LatLng(location.getLatitude(), location.getLongitude());
                    createDiaryOnBackend(title, selectedMood, intensity, diaryText, visibility, savePos);
                }
            } else {
                Toast.makeText(this, "目前無法取得定位，請稍後再試", Toast.LENGTH_SHORT).show();
            }
        }).addOnFailureListener(this, error -> {
            if (!isActive()) return;
            Toast.makeText(this, "目前無法取得定位，請稍後再試", Toast.LENGTH_SHORT).show();
        });
    }

    private void createDiaryOnBackend(String title, String mood, int intensity, String content, int visibility, LatLng location) {
        setDiaryLoading(true);
        Uri imageUri = selectedImageUris.isEmpty() ? null : selectedImageUris.get(0);
        diaryRepository.createDiary(
                title,
                content,
                DiaryMapper.moodApiValueFromSpinner(mood),
                intensity,
                DiaryMapper.visibilityApiValue(visibility),
                location.latitude,
                location.longitude,
                "",
                imageUri,
                new RepositoryCallback<DiaryDto>() {
                    @Override
                    public void onSuccess(DiaryDto diary) {
                        if (!isActive()) return;
                        setDiaryLoading(false);
                        DiaryEntry entry = buildEntryFromDto(diary);
                        if (entry != null) addOrUpdateDiaryMarker(entry);
                        selectedImageUris.clear();
                        if (imageAdapter != null) imageAdapter.notifyDataSetChanged();
                        hideDiaryCard();
                        updateStatistics();
                        updateDiaryList();
                        updateMapEmptyState();
                        if (entry != null) selectDiaryMarker(entry.id, true);
                        Toast.makeText(MapActivity.this, "日記已同步", Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onError(String message) {
                        if (!isActive()) return;
                        setDiaryLoading(false);
                        Toast.makeText(MapActivity.this, message, Toast.LENGTH_SHORT).show();
                    }
                }
        );
    }

    private void updateDiaryOnBackend(DiaryEntry existing, String title, String mood, int intensity, String content, int visibility, Location location) {
        if (!existing.isMine) {
            Toast.makeText(this, "只能編輯自己的日記", Toast.LENGTH_SHORT).show();
            return;
        }
        setDiaryLoading(true);
        diaryRepository.updateDiary(
                existing.id,
                title,
                content,
                DiaryMapper.moodApiValueFromSpinner(mood),
                intensity,
                DiaryMapper.visibilityApiValue(visibility),
                location.getLatitude(),
                location.getLongitude(),
                new RepositoryCallback<DiaryDto>() {
                    @Override
                    public void onSuccess(DiaryDto diary) {
                        if (!isActive()) return;
                        setDiaryLoading(false);
                        DiaryEntry entry = buildEntryFromDto(diary);
                        if (entry != null) {
                            addOrUpdateDiaryMarker(entry);
                            bindDiaryToSheet(entry);
                            selectDiaryMarker(entry.id, true);
                        }
                        hideDiaryCard();
                        updateStatistics();
                        updateDiaryList();
                        updateMapEmptyState();
                        Toast.makeText(MapActivity.this, "日記已更新", Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onError(String message) {
                        if (!isActive()) return;
                        setDiaryLoading(false);
                        Toast.makeText(MapActivity.this, message, Toast.LENGTH_SHORT).show();
                    }
                }
        );
    }

    private class DiaryListAdapter extends RecyclerView.Adapter<DiaryListAdapter.ViewHolder> {
        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_diary_list, parent, false);
            return new ViewHolder(v);
        }
        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            DiaryEntry entry = displayedDiaries.get(position);

            // 保持你原本就運作良好的所有顯示邏輯
            holder.tvTitle.setText(entry.title.isEmpty() ? entry.mood : entry.title);
            holder.tvTime.setText(entry.time);
            holder.tvHeart.setText("❤️ " + entry.heartCount);
            holder.tvSmile.setText("😊 " + entry.smileCount);
            holder.tvSurprise.setText("☔ " + entry.surpriseCount);

            String icon = (entry.visibility == 0) ? "🔒" : (entry.visibility == 1) ? "👥" : "👁️";
            if (holder.tvIcon != null) holder.tvIcon.setText(icon);

            // 🎯 【最終解決方案：直接顯示名字】
            // 為了確保運作，我們在 MapActivity 裡建立一個簡單的查找機制
            if (holder.tvItemAuthor != null) {
                String authorName = entry.authorName == null || entry.authorName.isEmpty() ? "Adrift" : entry.authorName;
                holder.tvItemAuthor.setText("@" + authorName);
            }

            holder.itemView.setOnClickListener(v -> {
                if (drawerLayout != null) drawerLayout.closeDrawer(GravityCompat.END);
                selectDiaryMarker(entry.id, true);
            });
        }

        @Override
        public int getItemCount() { return displayedDiaries.size(); }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvTitle, tvTime, tvIcon, tvHeart, tvSmile, tvSurprise;
            // 🎯 1. 【精準新增】宣告發布者名字的 TextView 變數 (維持與 XML 對齊的命名規範)
            TextView tvItemAuthor;

            ViewHolder(View v) {
                super(v);
                tvTitle = v.findViewById(R.id.tv_item_title);
                tvTime = v.findViewById(R.id.tv_item_time);
                tvIcon = v.findViewById(R.id.tv_item_icon);
                tvHeart = v.findViewById(R.id.tv_heart_count);
                tvSmile = v.findViewById(R.id.tv_smile_count);
                tvSurprise = v.findViewById(R.id.tv_surprise_count);

                // 🎯 2. 【精準綁定】將變數與 XML 剛建好的 id (tv_item_author) 正確連線，保證不跑版！
                tvItemAuthor = v.findViewById(R.id.tv_item_author);
            }
        }
    }

    private class ImageAdapter extends RecyclerView.Adapter<ImageAdapter.ViewHolder> {
        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_image_preview, parent, false);
            return new ViewHolder(v);
        }
        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            Glide.with(holder.img.getContext())
                    .load(selectedImageUris.get(position))
                    .placeholder(R.drawable.bg_image_placeholder)
                    .error(R.drawable.bg_image_placeholder)
                    .centerCrop()
                    .into(holder.img);
            holder.btnDelete.setVisibility((diaryInput != null && diaryInput.isEnabled()) ? View.VISIBLE : View.GONE);
            holder.btnDelete.setOnClickListener(v -> {
                int cp = holder.getBindingAdapterPosition();
                if (cp != RecyclerView.NO_POSITION) {
                    selectedImageUris.remove(cp);
                    notifyDataSetChanged();
                    if (selectedImageUris.isEmpty() && rvImagePreview != null) rvImagePreview.setVisibility(View.GONE);
                }
            }); }
        @Override
        public int getItemCount() { return selectedImageUris.size(); }
        class ViewHolder extends RecyclerView.ViewHolder {
            ImageView img, btnDelete;
            ViewHolder(View v) {
                super(v);
                img = v.findViewById(R.id.iv_preview);
                btnDelete = v.findViewById(R.id.btn_delete_img);
            }
        }

    }

    // 💡 方法一：處理搜尋邏輯
    private void performUserSearch(String targetName) {
        friendRepository.searchUser(targetName, new RepositoryCallback<SearchUserResult>() {
            @Override
            public void onSuccess(SearchUserResult user) {
                showAddFriendDialog(user);
            }

            @Override
            public void onError(String message) {
                android.widget.Toast.makeText(MapActivity.this, message, android.widget.Toast.LENGTH_SHORT).show();
            }
        });
    }

    // 💡 方法二：處理彈出對話框 (完美解決 ID 為空、未知用戶的致命防線)
    private void showAddFriendDialog(SearchUserResult targetUser) {
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("新增好友")
                .setMessage("確定要發送邀請給 " + targetUser.name + " 嗎？")
                .setPositiveButton("確定", (dialog, which) -> {
                    sendFriendRequestToServer(targetUser.getId());
                })
                .setNegativeButton("取消", null)
                .show();
    }

    // 💡 方法三：處理發送邀請 (原封不動，維持完美的運作機制)
    private void sendFriendRequestToServer(String targetId) {
        friendRepository.sendFriendRequest(targetId, new RepositoryCallback<EmptyResponse>() {
            @Override
            public void onSuccess(EmptyResponse value) {
                android.widget.Toast.makeText(MapActivity.this, "邀請已送出！", android.widget.Toast.LENGTH_SHORT).show();
                loadFriendData();
            }

            @Override
            public void onError(String message) {
                android.widget.Toast.makeText(MapActivity.this, message, android.widget.Toast.LENGTH_SHORT).show();
            }
        });
    }



    private void loadFriendData() {
        friendRepository.getSentRequests(new FriendListCallback(rvSentRequests, tvEmptySent, "sent", "尚未送出好友邀請"));
        friendRepository.getReceivedRequests(new FriendListCallback(rvReceivedRequests, tvEmptyReceived, "received", "尚未收到好友邀請"));
        friendRepository.getFriends(new FriendListCallback(rvFriendsList, tvEmptyFriends, "accepted", "還沒有好友，去搜尋吧"));
    }

// --- 💡 以下是為了搭配新版列表介面所需要的新增方法 ---

    // 執行更新狀態 (例如接受邀請)
    private void executeUpdateAction(String recordId, String status) {
        friendRepository.acceptRequest(recordId, reloadFriendsCallback());
    }

    // 執行刪除操作 (收回、拒絕、刪除好友共用)
    private void executeDeleteAction(String recordId) {
        friendRepository.deleteFriend(recordId, reloadFriendsCallback());
    }

    private void executeRejectRequest(String recordId) {
        friendRepository.rejectRequest(recordId, reloadFriendsCallback());
    }

    private void executeCancelRequest(String recordId) {
        friendRepository.cancelRequest(recordId, reloadFriendsCallback());
    }

    private RepositoryCallback<EmptyResponse> reloadFriendsCallback() {
        return new RepositoryCallback<EmptyResponse>() {
            @Override
            public void onSuccess(EmptyResponse value) {
                loadFriendData();
            }

            @Override
            public void onError(String message) {
                Toast.makeText(MapActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        };
    }

    private class FriendListCallback implements RepositoryCallback<List<FriendRecord>> {
        private final RecyclerView recyclerView;
        private final TextView emptyView;
        private final String type;
        private final String emptyText;

        FriendListCallback(RecyclerView recyclerView, TextView emptyView, String type, String emptyText) {
            this.recyclerView = recyclerView;
            this.emptyView = emptyView;
            this.type = type;
            this.emptyText = emptyText;
        }

        @Override
        public void onSuccess(List<FriendRecord> list) {
            if (list != null && !list.isEmpty()) {
                if (emptyView != null) emptyView.setVisibility(View.GONE);
                if (recyclerView != null) {
                    recyclerView.setVisibility(View.VISIBLE);
                    recyclerView.setLayoutManager(new LinearLayoutManager(MapActivity.this));
                    recyclerView.setAdapter(new FriendListAdapter(list, type));
                }
            } else {
                if (emptyView != null) {
                    emptyView.setVisibility(View.VISIBLE);
                    emptyView.setText(emptyText);
                }
                if (recyclerView != null) recyclerView.setVisibility(View.GONE);
            }
        }

        @Override
        public void onError(String message) {
            if (emptyView != null) {
                emptyView.setVisibility(View.VISIBLE);
                emptyView.setText(message);
            }
        }
    }

    // 彈出好友個人資料視窗 (對應你的第三張截圖)
    private void showFriendProfileDialog(FriendRecord record) {
        new androidx.appcompat.app.AlertDialog.Builder(MapActivity.this)
                .setTitle(record.getTargetName() + " 的個人資料")
                .setMessage("@" + record.getTargetName() + "\n\n加入日期：" + formatDate(record.getCreatedAt()))
                .setPositiveButton("查看地圖日記", null)
                .setNegativeButton("刪除好友", (dialog, which) -> executeDeleteAction(record.getId()))
                .show();
    }

    private String formatDate(String value) {
        if (value != null && value.length() >= 10) return value.substring(0, 10);
        return "尚無資料";
    }

    // 卡片列表配接器 (控制收回、接受、刪除等按鈕)
    private class FriendListAdapter extends androidx.recyclerview.widget.RecyclerView.Adapter<FriendListAdapter.ViewHolder> {
        private java.util.List<FriendRecord> data;
        private String type;

        public FriendListAdapter(java.util.List<FriendRecord> data, String type) {
            this.data = data;
            this.type = type;
        }

        @androidx.annotation.NonNull
        @Override
        public ViewHolder onCreateViewHolder(@androidx.annotation.NonNull android.view.ViewGroup parent, int viewType) {
            // ⚠️ 這裡需要確保你有 res/layout/item_friend_card.xml
            // 若目前還沒畫 XML，可以先用一個簡單的 TextView 取代避免崩潰，但若要像截圖那樣，需自己建立佈局。
            android.view.View view = android.view.LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_friend_card, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@androidx.annotation.NonNull ViewHolder holder, int position) {
            FriendRecord record = data.get(position);

            // 💡 終極根治法：名字由後端在傳輸時保證一定是「對方」的 username
            String displayName = record.getTargetName();

            // 💡 將參數完全不變地設定到 UI 上
            holder.tvName.setText(displayName);

            if ("sent".equals(type)) {
                holder.tvStatus.setText("@" + displayName + " · 等待對方回覆");
                holder.btnAction1.setText("收回");
                holder.btnAction1.setOnClickListener(v -> executeCancelRequest(record.getId()));
                holder.btnAction2.setVisibility(android.view.View.GONE);
            } else if ("received".equals(type)) {
                holder.tvStatus.setText("@" + displayName + " · 向您發送了邀請");
                holder.btnAction1.setText("接受");
                holder.btnAction2.setText("拒絕");
                holder.btnAction2.setVisibility(android.view.View.VISIBLE);
                holder.btnAction1.setOnClickListener(v -> executeUpdateAction(record.getId(), "accepted"));
                holder.btnAction2.setOnClickListener(v -> executeRejectRequest(record.getId()));
            } else if ("accepted".equals(type)) {
                holder.tvStatus.setText("@" + displayName + " · 已是好友");
                holder.btnAction1.setText("個人資料");
                holder.btnAction2.setText("刪除");
                holder.btnAction2.setVisibility(android.view.View.VISIBLE);
                holder.btnAction1.setOnClickListener(v -> showFriendProfileDialog(record));
                holder.btnAction2.setOnClickListener(v -> executeDeleteAction(record.getId()));
            }
        }

        @Override
        public int getItemCount() { return data.size(); }

        class ViewHolder extends androidx.recyclerview.widget.RecyclerView.ViewHolder {
            android.widget.TextView tvName, tvStatus;
            android.widget.Button btnAction1, btnAction2;
            public ViewHolder(@androidx.annotation.NonNull android.view.View itemView) {
                super(itemView);
                tvName = itemView.findViewById(R.id.tv_item_name);
                tvStatus = itemView.findViewById(R.id.tv_item_status);
                btnAction1 = itemView.findViewById(R.id.btn_item_action_1);
                btnAction2 = itemView.findViewById(R.id.btn_item_action_2);
            }
        }

    }
}
