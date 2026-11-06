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
import androidx.core.content.ContextCompat;
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
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.navigation.NavigationView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MapActivity extends AppCompatActivity implements OnMapReadyCallback {
    public static final String EXTRA_NEW_DIARY = "action_new_diary";

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

    // 日記卡片的三種模式，對齊網頁版：查看（DiarySidePanel）、新增、編輯（DiaryModal）
    private static final int SHEET_DETAIL = 0;
    private static final int SHEET_CREATE = 1;
    private static final int SHEET_EDIT = 2;
    private int sheetMode = SHEET_CREATE;
    private DiaryEntry editingEntry;
    private Location sheetLocation;
    private View layoutDiaryDetail, layoutDiaryForm, layoutOwnerActions, layoutLocationLine, layoutEditRules;
    private TextView tvDiaryEyebrow, tvDetailAvatarLetter, tvDetailAuthorName, tvDetailAuthorCode, tvDetailTime, tvDetailEdited,
            tvDetailPlace, tvDetailMood, tvDetailVisibility, tvDetailTitle, tvDetailText, tvEditAvailability,
            tvLocationLine, btnRefreshLocation, tvEditRulesPlace, tvEditRulesTime, tvEditRulesDistance, tvEditRulesWarning,
            btnEditRefreshLocation, btnVoiceInput, tvSpeechStatus, tvImageLabel;
    private ImageView ivDetailAvatar;
    private Button btnEditDiary;
    private final android.os.Handler sheetTicker = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable sheetTickRunnable = new Runnable() {
        @Override
        public void run() {
            refreshEditStatus();
            sheetTicker.postDelayed(this, 30_000);
        }
    };
    private ActivityResultLauncher<Intent> speechLauncher;
    private TextView tvNotificationBadge;
    private com.example.topics.data.repository.NotificationRepository notificationRepository;
    // 即時更新：別人新增／修改／刪除日記時地圖跟著變，通知數也會更新（對齊網頁版 EventSource）
    private final RealtimeEvents.Listener realtimeListener = this::handleRealtimeEvent;
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
    private Button btnAllFilter, btnPriv, btnFrdOnly, btnPub;
    private TextView tvTitleCounter;
    private static final int DIARY_TEXT_MAX = 2000;
    private Uri pendingCameraUri;
    private boolean processingPhoto;
    private LinearLayout layoutMyDiaryRoot, layoutFriendManagement;

    private RecyclerView rvDiaryList;
    private DiaryListAdapter diaryListAdapter;
    private List<DiaryEntry> displayedDiaries = new ArrayList<>();
    private boolean isShowingMine = true;
    private int currentFilter = -1;

    // 🎯 附近動態：距離篩選按鈕與清單
    private Button btnRadius1km, btnRadius5km, btnRadius10km, btnRadius50km;
    private TextView tvNearbyMineCount, tvNearbyVisibleCount, tvEmptyNearby;
    private RecyclerView rvNearbyDiaries;
    private NearbyDiaryAdapter nearbyDiaryAdapter;
    private List<DiaryEntry> nearbyDiaries = new ArrayList<>();
    private int currentRadiusMeters = 50000;

    private RecyclerView rvImagePreview;
    private ImageAdapter imageAdapter;
    private List<Uri> selectedImageUris = new ArrayList<>();

    private Map<String, DiaryEntry> markerDataMap = new HashMap<>();
    private Map<String, Marker> diaryMarkerMap = new HashMap<>();
    private Map<String, String> markerDiaryIdMap = new HashMap<>();
    private Marker lastSelectedMarker = null;
    private String selectedDiaryId = null;
    private DiaryMarkerLayer markerLayer;
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
        String moodType = "";
        String authorCode = "";
        int editCount = 0;
        String lastEditedAt = "";
        String editExpiresAt = "";
        int editDistanceLimitMeters = 1000;

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
        notificationRepository = new com.example.topics.data.repository.NotificationRepository(this);
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
        tvTitleCounter = findViewById(R.id.tv_title_counter);
        btnCloseDiary = findViewById(R.id.btn_close_diary);
        rvImagePreview = findViewById(R.id.rv_image_preview);
        ivDiaryRemoteImage = findViewById(R.id.iv_diary_remote_image);
        layoutDiaryDetail = findViewById(R.id.layout_diary_detail);
        layoutDiaryForm = findViewById(R.id.layout_diary_form);
        layoutOwnerActions = findViewById(R.id.layout_owner_actions);
        layoutLocationLine = findViewById(R.id.layout_location_line);
        layoutEditRules = findViewById(R.id.layout_edit_rules);
        tvDiaryEyebrow = findViewById(R.id.tv_diary_eyebrow);
        tvDetailAvatarLetter = findViewById(R.id.tv_detail_avatar_letter);
        ivDetailAvatar = findViewById(R.id.iv_detail_avatar);
        tvDetailAuthorName = findViewById(R.id.tv_detail_author_name);
        tvDetailAuthorCode = findViewById(R.id.tv_detail_author_code);
        tvDetailTime = findViewById(R.id.tv_detail_time);
        tvDetailEdited = findViewById(R.id.tv_detail_edited);
        tvDetailPlace = findViewById(R.id.tv_detail_place);
        tvDetailMood = findViewById(R.id.tv_detail_mood);
        tvDetailVisibility = findViewById(R.id.tv_detail_visibility);
        tvDetailTitle = findViewById(R.id.tv_detail_title);
        tvDetailText = findViewById(R.id.tv_detail_text);
        tvEditAvailability = findViewById(R.id.tv_edit_availability);
        btnEditDiary = findViewById(R.id.btn_edit_diary);
        tvLocationLine = findViewById(R.id.tv_location_line);
        btnRefreshLocation = findViewById(R.id.btn_refresh_location);
        tvEditRulesPlace = findViewById(R.id.tv_edit_rules_place);
        tvEditRulesTime = findViewById(R.id.tv_edit_rules_time);
        tvEditRulesDistance = findViewById(R.id.tv_edit_rules_distance);
        tvEditRulesWarning = findViewById(R.id.tv_edit_rules_warning);
        btnEditRefreshLocation = findViewById(R.id.btn_edit_refresh_location);
        btnVoiceInput = findViewById(R.id.btn_voice_input);
        tvSpeechStatus = findViewById(R.id.tv_speech_status);
        tvImageLabel = findViewById(R.id.tv_image_label);

        speechLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() != RESULT_OK || result.getData() == null) {
                        setSpeechStatus("");
                        return;
                    }
                    java.util.ArrayList<String> matches = result.getData().getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS);
                    String transcript = matches == null || matches.isEmpty() ? "" : matches.get(0).trim();
                    if (transcript.isEmpty()) {
                        setSpeechStatus("辨識失敗，請再試一次。");
                        return;
                    }
                    if (diaryInput != null) {
                        String current = diaryInput.getText().toString();
                        diaryInput.setText(current + (current.trim().isEmpty() ? "" : "\n") + transcript);
                        diaryInput.setSelection(diaryInput.getText().length());
                    }
                    setSpeechStatus("已加入日記內容");
                }
        );
        if (btnVoiceInput != null) btnVoiceInput.setOnClickListener(v -> startSpeechInput());
        if (btnEditDiary != null) {
            btnEditDiary.setOnClickListener(v -> {
                DiaryEntry entry = getCurrentDiaryEntry();
                if (entry != null) showEditForm(entry);
            });
        }
        if (btnRefreshLocation != null) btnRefreshLocation.setOnClickListener(v -> refreshSheetLocation());
        if (btnEditRefreshLocation != null) btnEditRefreshLocation.setOnClickListener(v -> refreshSheetLocation());
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
                    // SeekBar 0~4 對應網頁版心情強度 1~5
                    if (tvIntensityLabel != null) tvIntensityLabel.setText("心情強度：" + (progress + 1) + " / 5");
                }
                @Override public void onStartTrackingTouch(SeekBar seekBar) {}
                @Override public void onStopTrackingTouch(SeekBar seekBar) {}
            });
        }

        if (etDiaryTitle != null) {
            etDiaryTitle.addTextChangedListener(new android.text.TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
                @Override
                public void afterTextChanged(android.text.Editable s) {
                    if (tvTitleCounter != null) tvTitleCounter.setText(s.toString().trim().length() + " / 50");
                }
            });
        }

        View fabAdd = findViewById(R.id.btn_top_add_diary);
        if (fabAdd != null) {
            fabAdd.setOnClickListener(v -> {
                clearSelectedDiaryMarker();
                showCreateForm();
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

        // 心情、可見性選單：選項、順序與圖示對齊網頁版 DiaryModal
        if (moodSpinner != null) {
            moodSpinner.setAdapter(new AdriftSelectAdapter(moodSpinner, MOOD_OPTIONS));
        }

        if (visibilitySpinner != null) {
            visibilitySpinner.setAdapter(new AdriftSelectAdapter(visibilitySpinner, VISIBILITY_OPTIONS));
        }

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map_fragment);
        if (mapFragment != null) mapFragment.getMapAsync(this);

        ActivityResultLauncher<Intent> photoPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        Intent data = result.getData();
                        Uri uri = data.getData();
                        if (uri == null && data.getClipData() != null && data.getClipData().getItemCount() > 0) {
                            uri = data.getClipData().getItemAt(0).getUri();
                        }
                        if (uri == null) return;
                        processDiaryPhoto(uri);
                    }
                }
        );

        // 拍照：用系統相機拍好存到暫存檔，再跟選照片一樣壓縮處理
        ActivityResultLauncher<Uri> cameraLauncher = registerForActivityResult(
                new ActivityResultContracts.TakePicture(),
                success -> {
                    Uri uri = pendingCameraUri;
                    pendingCameraUri = null;
                    if (Boolean.TRUE.equals(success) && uri != null) processDiaryPhoto(uri);
                }
        );

        View imagePicker = findViewById(R.id.card_image_picker);
        if (imagePicker != null) {
            imagePicker.setOnClickListener(v -> {
                if (sheetMode == SHEET_EDIT) {
                    Toast.makeText(this, "圖片目前僅支援新增日記時上傳", Toast.LENGTH_SHORT).show();
                } else if (processingPhoto) {
                    Toast.makeText(this, "照片處理中，請稍候", Toast.LENGTH_SHORT).show();
                } else if (diaryInput != null && diaryInput.isEnabled()) {
                    KeyboardDismiss.hide(this);
                    showPhotoSourceSheet(cameraLauncher, photoPickerLauncher);
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
                if (entry != null) showDiaryDetail(entry);
            });
            previewController.setOnCloseListener(v -> clearSelectedDiaryMarker());
        }
        handleInitialTab();
        if (getIntent().getBooleanExtra(EXTRA_NEW_DIARY, false)) {
            getIntent().removeExtra(EXTRA_NEW_DIARY);
            showCreateForm();
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (intent.getBooleanExtra(EXTRA_NEW_DIARY, false)) {
            intent.removeExtra(EXTRA_NEW_DIARY);
            clearSelectedDiaryMarker();
            showCreateForm();
        }
        String openDiaryId = intent.getStringExtra("open_diary_id");
        if (openDiaryId != null && mapReady) {
            intent.removeExtra("open_diary_id");
            openDiaryById(openDiaryId);
        }
    }

    // 從動態牆、通知或 Intelligence 點進來時打開指定日記；地圖上沒有就向後端拿那一篇
    private void openDiaryById(String diaryId) {
        if (diaryId == null || diaryId.isEmpty()) return;
        if (markerDataMap.containsKey(diaryId)) {
            selectDiaryMarker(diaryId, true);
            return;
        }
        diaryRepository.getDiary(diaryId, new RepositoryCallback<DiaryDto>() {
            @Override
            public void onSuccess(DiaryDto diary) {
                if (!isActive()) return;
                DiaryEntry entry = buildEntryFromDto(diary);
                if (entry == null) return;
                addOrUpdateDiaryMarker(entry);
                updateStatistics();
                updateDiaryList();
                updateMapEmptyState();
                selectDiaryMarker(entry.id, true);
            }

            @Override
            public void onError(String message) {
                if (!isActive()) return;
                showMapStatus(message == null || message.isEmpty() ? "這篇記憶已移除或不再向你分享" : message, false);
            }
        });
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
        tvNotificationBadge = findViewById(R.id.tv_map_notification_badge);
        View bell = findViewById(R.id.btn_map_notifications);
        if (bell != null) bell.setOnClickListener(v -> startActivity(new Intent(this, NotificationsActivity.class)));

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
            mapIdentityChip.setOnClickListener(this::showIdentityPopupMenu);
        }
        bindMapIdentity(sessionManager == null ? null : sessionManager.getUser());
    }
    private void showIdentityPopupMenu(View anchor) {
        View popupView = LayoutInflater.from(this).inflate(R.layout.popup_identity_menu, null);

        TextView tvAvatar = popupView.findViewById(R.id.tv_popup_avatar);
        TextView tvName = popupView.findViewById(R.id.tv_popup_name);
        TextView tvRoleBadge = popupView.findViewById(R.id.tv_popup_role_badge);
        TextView tvUserCode = popupView.findViewById(R.id.tv_popup_usercode);
        TextView btnCopy = popupView.findViewById(R.id.btn_popup_copy);
        View btnMyAccount = popupView.findViewById(R.id.btn_popup_my_account);
        View btnAdmin = popupView.findViewById(R.id.btn_popup_admin);
        View btnLogout = popupView.findViewById(R.id.btn_popup_logout);

        UserDto user = sessionManager == null ? null : sessionManager.getUser();
        String displayName = user != null ? user.getDisplayName() : "Adrift";
        String userCode = user != null ? user.getUserCode() : "";
        String role = user != null && user.role != null ? user.role : "user";

        tvName.setText(displayName);
        tvAvatar.setText(displayName.isEmpty() ? "A" : displayName.substring(0, 1));
        tvUserCode.setText(userCode.isEmpty() ? "@adrift" : "@" + userCode);

        boolean isAdmin = "admin".equals(role) || "owner".equals(role);
        tvRoleBadge.setVisibility(isAdmin ? View.VISIBLE : View.GONE);
        tvRoleBadge.setText(role.toUpperCase());
        btnAdmin.setVisibility(isAdmin ? View.VISIBLE : View.GONE);

        android.widget.PopupWindow popupWindow = new android.widget.PopupWindow(
                popupView,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                true
        );
        popupWindow.setElevation(12f);
        popupWindow.setOutsideTouchable(true);

        btnCopy.setOnClickListener(v -> {
            android.content.ClipboardManager clipboard =
                    (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("userCode", tvUserCode.getText()));
            Toast.makeText(this, "已複製", Toast.LENGTH_SHORT).show();
        });

        btnMyAccount.setOnClickListener(v -> {
            popupWindow.dismiss();
            startActivity(new Intent(this, SettingsActivity.class));
        });

        btnAdmin.setOnClickListener(v -> {
            popupWindow.dismiss();
            startActivity(new Intent(this, AdminActivity.class));
        });

        btnLogout.setOnClickListener(v -> {
            popupWindow.dismiss();
            sessionManager.clear();
            Intent intent = new Intent(this, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });

        popupWindow.showAsDropDown(anchor, 0, 8);
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
            item.setTextColor(getColor(selected ? R.color.ad_text : R.color.ad_text_secondary));
        }
    }

    // ===== 心情 / 可見性選單（對齊網頁版） =====
    private static class SelectOption {
        final String value;
        final String icon;
        final String label;
        final int iconRes; // 手機字型畫不好的符號改用向量圖，0 代表直接顯示文字圖示

        SelectOption(String value, String icon, String label) {
            this(value, icon, label, 0);
        }

        SelectOption(String value, String icon, String label, int iconRes) {
            this.value = value;
            this.icon = icon;
            this.label = label;
            this.iconRes = iconRes;
        }
    }

    // 對應網頁版 constants/app.js 的 MOOD_OPTIONS（不含「其他」）與 DiaryModal 的 moodIcons
    private static final List<SelectOption> MOOD_OPTIONS = java.util.Arrays.asList(
            new SelectOption("calm", "🌿", "平靜"),
            new SelectOption("joy", "😊", "喜悅"),
            new SelectOption("sad", "🌧", "低落"),
            new SelectOption("wonder", "✨", "驚奇"),
            new SelectOption("anxious", "◌", "焦慮", R.drawable.ic_mood_anxious),
            new SelectOption("confused", "◔", "疑惑", R.drawable.ic_mood_confused),
            new SelectOption("nostalgic", "◐", "懷舊", R.drawable.ic_mood_nostalgic)
    );

    // 對應網頁版 VISIBILITY_OPTIONS 的順序：公開、好友、私人
    private static final List<SelectOption> VISIBILITY_OPTIONS = java.util.Arrays.asList(
            new SelectOption("public", "🌐", "公開"),
            new SelectOption("friends", "👥", "好友"),
            new SelectOption("private", "🔒", "私人")
    );

    private class AdriftSelectAdapter extends ArrayAdapter<SelectOption> {
        private final Spinner owner;

        AdriftSelectAdapter(Spinner owner, List<SelectOption> options) {
            super(MapActivity.this, R.layout.item_adrift_select_trigger, options);
            this.owner = owner;
        }

        @NonNull
        @Override
        public View getView(int position, View convertView, @NonNull ViewGroup parent) {
            View view = convertView != null ? convertView
                    : LayoutInflater.from(getContext()).inflate(R.layout.item_adrift_select_trigger, parent, false);
            bindOption(view, getItem(position));
            return view;
        }

        @Override
        public View getDropDownView(int position, View convertView, @NonNull ViewGroup parent) {
            View view = convertView != null ? convertView
                    : LayoutInflater.from(getContext()).inflate(R.layout.item_adrift_select_option, parent, false);
            bindOption(view, getItem(position));
            boolean active = position == owner.getSelectedItemPosition();
            view.setActivated(active);
            TextView label = view.findViewById(R.id.tv_select_label);
            if (label != null) label.setTextColor(getColor(active ? R.color.ad_text : R.color.ad_select_text));
            return view;
        }

        private void bindOption(View view, SelectOption option) {
            if (option == null) return;
            TextView icon = view.findViewById(R.id.tv_select_icon);
            TextView label = view.findViewById(R.id.tv_select_label);
            if (icon != null) {
                if (option.iconRes != 0) {
                    icon.setText("");
                    icon.setCompoundDrawablesRelativeWithIntrinsicBounds(option.iconRes, 0, 0, 0);
                } else {
                    icon.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, 0, 0);
                    icon.setText(option.icon);
                }
            }
            if (label != null) label.setText(option.label);
        }
    }

    private String selectedMoodValue() {
        if (moodSpinner == null || !(moodSpinner.getSelectedItem() instanceof SelectOption)) return "calm";
        return ((SelectOption) moodSpinner.getSelectedItem()).value;
    }

    private int selectedVisibilityIndex() {
        if (visibilitySpinner == null || !(visibilitySpinner.getSelectedItem() instanceof SelectOption)) return 2;
        return DiaryMapper.visibilityToIndex(((SelectOption) visibilitySpinner.getSelectedItem()).value);
    }

    private int moodPositionFor(String moodType) {
        String type = moodType == null ? "" : moodType;
        if ("happy".equals(type)) type = "joy";
        if ("excited".equals(type)) type = "wonder";
        for (int i = 0; i < MOOD_OPTIONS.size(); i++) {
            if (MOOD_OPTIONS.get(i).value.equals(type)) return i;
        }
        return 0;
    }

    private int visibilityPositionFor(int visibilityIndex) {
        for (int i = 0; i < VISIBILITY_OPTIONS.size(); i++) {
            if (DiaryMapper.visibilityToIndex(VISIBILITY_OPTIONS.get(i).value) == visibilityIndex) return i;
        }
        return 0;
    }

    private void showDiaryCard(String eyebrow, String headerText) {
        if (diaryCardView != null) {
            TextView tvHeader = diaryCardView.findViewById(R.id.tv_diary_header);
            if (tvHeader != null) tvHeader.setText(headerText);
            if (tvDiaryEyebrow != null) tvDiaryEyebrow.setText(eyebrow);
            View scroll = diaryCardView.findViewById(R.id.diary_sheet_scroll);
            if (scroll != null) scroll.scrollTo(0, 0);
            diaryCardView.setVisibility(View.VISIBLE);
        }
        if (mapBlurOverlay != null) mapBlurOverlay.setVisibility(View.VISIBLE);
        sheetTicker.removeCallbacks(sheetTickRunnable);
        sheetTicker.postDelayed(sheetTickRunnable, 30_000);
    }

    private void setSheetMode(int mode) {
        sheetMode = mode;
        boolean detail = mode == SHEET_DETAIL;
        if (layoutDiaryDetail != null) layoutDiaryDetail.setVisibility(detail ? View.VISIBLE : View.GONE);
        if (layoutDiaryForm != null) layoutDiaryForm.setVisibility(detail ? View.GONE : View.VISIBLE);
        if (layoutLocationLine != null) layoutLocationLine.setVisibility(mode == SHEET_CREATE ? View.VISIBLE : View.GONE);
        if (layoutEditRules != null) layoutEditRules.setVisibility(mode == SHEET_EDIT ? View.VISIBLE : View.GONE);
        int imageVisibility = mode == SHEET_CREATE ? View.VISIBLE : View.GONE;
        if (tvImageLabel != null) tvImageLabel.setVisibility(imageVisibility);
        View picker = findViewById(R.id.card_image_picker);
        if (picker != null) picker.setVisibility(imageVisibility);
        setSpeechStatus("");
    }

    // ===== 新增日記：對齊網頁版 DiaryModal（create） =====
    private void showCreateForm() {
        editingEntry = null;
        setSheetMode(SHEET_CREATE);
        clearDiaryForm();
        setDiaryFieldsEnabled(true);
        selectedImageUris.clear();
        if (imageAdapter != null) imageAdapter.notifyDataSetChanged();
        if (rvImagePreview != null) rvImagePreview.setVisibility(View.GONE);
        if (btnSaveDiary != null) btnSaveDiary.setVisibility(View.VISIBLE);
        setDiaryLoading(false);
        if (tvLocationLine != null) tvLocationLine.setText("📍 正在取得位置...");
        showDiaryCard("NEW MEMORY", "新增日記");
        refreshSheetLocation();
    }

    // ===== 編輯日記：對齊網頁版 DiaryModal（edit） =====
    private void showEditForm(DiaryEntry entry) {
        if (entry == null) return;
        editingEntry = entry;
        setSheetMode(SHEET_EDIT);
        if (etDiaryTitle != null) etDiaryTitle.setText(entry.title);
        if (diaryInput != null) diaryInput.setText(entry.text);
        if (sbMoodIntensity != null) sbMoodIntensity.setProgress(Math.max(1, Math.min(5, entry.intensity)) - 1);
        if (moodSpinner != null) moodSpinner.setSelection(moodPositionFor(entry.moodType));
        if (visibilitySpinner != null) visibilitySpinner.setSelection(visibilityPositionFor(entry.visibility));
        selectedImageUris.clear();
        if (imageAdapter != null) imageAdapter.notifyDataSetChanged();
        if (rvImagePreview != null) rvImagePreview.setVisibility(View.GONE);
        setDiaryFieldsEnabled(true);
        setDiaryLoading(false);
        if (tvEditRulesPlace != null) tvEditRulesPlace.setText("📍 日記位置不可修改：" + placeTextFor(entry));
        resolveMissingPlace(entry, tvEditRulesPlace, "📍 日記位置不可修改：");
        showDiaryCard("EDIT MEMORY", "編輯日記");
        refreshEditStatus();
        refreshSheetLocation();
    }

    // ===== 查看日記：對齊網頁版 DiarySidePanel =====
    private void showDiaryDetail(DiaryEntry entry) {
        if (entry == null) return;
        editingEntry = null;
        setSheetMode(SHEET_DETAIL);
        String name = entry.authorName == null || entry.authorName.isEmpty() ? "Unknown" : entry.authorName;
        if (tvDetailAuthorName != null) tvDetailAuthorName.setText(name);
        if (tvDetailAuthorCode != null) tvDetailAuthorCode.setText("@" + (entry.authorCode == null || entry.authorCode.isEmpty() ? "unknown" : entry.authorCode));
        if (tvDetailAvatarLetter != null) tvDetailAvatarLetter.setText(name.substring(0, 1).toUpperCase());
        if (ivDetailAvatar != null) {
            if (entry.authorAvatar != null && !entry.authorAvatar.trim().isEmpty()) {
                ivDetailAvatar.setVisibility(View.VISIBLE);
                Glide.with(this).load(ImageUrlResolver.resolve(entry.authorAvatar)).circleCrop().into(ivDetailAvatar);
            } else {
                Glide.with(this).clear(ivDetailAvatar);
                ivDetailAvatar.setVisibility(View.GONE);
            }
        }
        bindRemoteImage(entry);
        if (tvDetailTime != null) tvDetailTime.setText("🕒 " + formatDateTime(entry.time));
        if (tvDetailEdited != null) {
            boolean edited = entry.editCount > 0 || (entry.lastEditedAt != null && !entry.lastEditedAt.isEmpty());
            String editedTime = formatClock(entry.lastEditedAt);
            tvDetailEdited.setText(editedTime.isEmpty() ? "已編輯" : "已編輯 · " + editedTime);
            tvDetailEdited.setVisibility(edited ? View.VISIBLE : View.GONE);
        }
        if (tvDetailPlace != null) tvDetailPlace.setText("📍 " + placeTextFor(entry));
        resolveMissingPlace(entry, tvDetailPlace, "📍 ");
        if (tvDetailMood != null) tvDetailMood.setText(DiaryMapper.moodDisplayLabel(entry.moodType) + " / " + entry.intensity);
        if (tvDetailVisibility != null) {
            String icon = entry.visibility == 0 ? "🔒 " : entry.visibility == 1 ? "👥 " : "🌐 ";
            tvDetailVisibility.setText(icon + (entry.visibility == 0 ? "私人" : entry.visibility == 1 ? "好友" : "公開"));
        }
        if (tvDetailTitle != null) tvDetailTitle.setText(entry.title == null || entry.title.trim().isEmpty() ? "（未命名日記）" : entry.title);
        if (tvDetailText != null) tvDetailText.setText(entry.text);
        bindReactionButtons(entry);
        if (layoutReactions != null) layoutReactions.setVisibility(View.VISIBLE);
        if (layoutOwnerActions != null) layoutOwnerActions.setVisibility(entry.isMine ? View.VISIBLE : View.GONE);
        setDiaryLoading(false);
        showDiaryCard("SELECTED DIARY", "日記詳細");
        refreshEditStatus();
        if (entry.isMine) refreshSheetLocation();
    }

    private String placeTextFor(DiaryEntry entry) {
        if (entry.placeName != null && !entry.placeName.trim().isEmpty()) return PlaceNameResolver.normalizeTaiwan(entry.placeName);
        return entry.location == null ? PlaceNameResolver.UNKNOWN : "正在取得地點...";
    }

    // 日記沒有存地名時，跟 iOS 版一樣用座標查地名再補上，不直接顯示座標
    private void resolveMissingPlace(DiaryEntry entry, TextView target, String prefix) {
        if (target == null || entry == null || entry.location == null) return;
        if (entry.placeName != null && !entry.placeName.trim().isEmpty()) return;
        String diaryId = entry.id;
        PlaceNameResolver.resolve(this, entry.location.latitude, entry.location.longitude, placeName -> {
            if (!isActive() || diaryCardView == null || diaryCardView.getVisibility() != View.VISIBLE) return;
            DiaryEntry current = sheetMode == SHEET_EDIT ? editingEntry : getCurrentDiaryEntry();
            if (current != null && diaryId != null && diaryId.equals(current.id)) target.setText(prefix + placeName);
        });
    }

    // 使用者只允許「大概位置」時送 approximate，照 iOS 版 LocationManager.accuracyType
    private String locationAccuracyType() {
        return ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                ? "precise" : "approximate";
    }

    // 選好或拍好的照片：先縮圖、轉正、壓成 JPEG 再放進日記（照 iOS 版 resizedForDiaryUpload）
    // 拍照或選擇照片：iOS action sheet 風格的底部選單（明亮／深色自動套用 colors_theme）
    private void showPhotoSourceSheet(ActivityResultLauncher<Uri> cameraLauncher,
                                      ActivityResultLauncher<Intent> photoPickerLauncher) {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        View content = LayoutInflater.from(this).inflate(R.layout.bottom_sheet_photo_source, null, false);

        content.findViewById(R.id.row_photo_camera).setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            dialog.dismiss();
            try {
                pendingCameraUri = DiaryImageProcessor.createCameraUri(this);
                cameraLauncher.launch(pendingCameraUri);
            } catch (Exception e) {
                pendingCameraUri = null;
                Toast.makeText(this, "無法開啟相機", Toast.LENGTH_SHORT).show();
            }
        });

        content.findViewById(R.id.row_photo_library).setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            dialog.dismiss();
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("image/*");
            photoPickerLauncher.launch(Intent.createChooser(intent, "選擇照片"));
        });

        content.findViewById(R.id.btn_photo_cancel).setOnClickListener(v -> dialog.dismiss());

        dialog.setContentView(content);
        dialog.setOnShowListener(d -> {
            View sheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (sheet != null) {
                sheet.setBackgroundColor(Color.TRANSPARENT);
                BottomSheetBehavior<View> behavior = BottomSheetBehavior.from(sheet);
                behavior.setSkipCollapsed(true);
                behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
            }
            if (dialog.getWindow() != null) {
                dialog.getWindow().setDimAmount(0f);
                dialog.getWindow().getDecorView().setBackgroundColor(Color.TRANSPARENT);
                View container = dialog.findViewById(com.google.android.material.R.id.container);
                if (container != null) {
                    container.setBackgroundColor(ContextCompat.getColor(this, R.color.ad_sheet_scrim));
                }
            }
        });
        dialog.show();
    }

    private void processDiaryPhoto(Uri source) {
        processingPhoto = true;
        TextView picker = findViewById(R.id.card_image_picker);
        if (picker != null) picker.setText("⏳ 照片處理中...");
        DiaryImageProcessor.prepare(this, source, new DiaryImageProcessor.Callback() {
            @Override
            public void onReady(Uri uri) {
                if (!isActive()) return;
                processingPhoto = false;
                if (picker != null) picker.setText("📷 拍照或選擇照片");
                // 跟網頁版一樣，一篇日記只放一張照片
                selectedImageUris.clear();
                selectedImageUris.add(uri);
                if (imageAdapter != null) imageAdapter.notifyDataSetChanged();
                if (rvImagePreview != null) rvImagePreview.setVisibility(View.VISIBLE);
            }

            @Override
            public void onError(String message) {
                if (!isActive()) return;
                processingPhoto = false;
                if (picker != null) picker.setText("📷 拍照或選擇照片");
                Toast.makeText(MapActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public boolean dispatchTouchEvent(android.view.MotionEvent event) {
        KeyboardDismiss.handle(this, event);
        return super.dispatchTouchEvent(event);
    }

    // 取得目前位置：新增時顯示地點，查看／編輯時計算與原地點的距離
    private void refreshSheetLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            if (tvLocationLine != null) tvLocationLine.setText("📍 請允許定位權限");
            refreshEditStatus();
            return;
        }
        fusedLocationClient.getLastLocation().addOnSuccessListener(this, location -> {
            if (!isActive()) return;
            sheetLocation = location;
            if (location == null) {
                if (tvLocationLine != null) tvLocationLine.setText("📍 暫時無法取得目前位置");
                refreshEditStatus();
                return;
            }
            if (sheetMode == SHEET_CREATE && tvLocationLine != null) {
                // 照 iOS 版：顯示地名，後面標註精確位置或概略位置
                String accuracy = " · " + ("approximate".equals(locationAccuracyType()) ? "概略位置" : "精確位置");
                tvLocationLine.setText("📍 正在取得地點..." + accuracy);
                PlaceNameResolver.resolve(this, location.getLatitude(), location.getLongitude(), placeName -> {
                    if (isActive() && sheetMode == SHEET_CREATE && tvLocationLine != null) {
                        tvLocationLine.setText("📍 " + placeName + accuracy);
                    }
                });
            }
            refreshEditStatus();
        });
    }

    // 編輯限制：發布後 1 小時內、離原地點 1 公里內（對齊網頁版 getDiaryEditStatus）
    private void refreshEditStatus() {
        if (diaryCardView == null || diaryCardView.getVisibility() != View.VISIBLE) return;
        DiaryEntry entry = sheetMode == SHEET_EDIT ? editingEntry : getCurrentDiaryEntry();
        if (entry == null || !entry.isMine || sheetMode == SHEET_CREATE) return;

        long expiresAt = InsightFormat.parseIso(entry.editExpiresAt);
        if (expiresAt < 0) {
            long created = InsightFormat.parseIso(entry.time);
            expiresAt = created < 0 ? -1 : created + 60L * 60 * 1000;
        }
        long remainingMs = expiresAt < 0 ? 0 : expiresAt - System.currentTimeMillis();
        boolean timeAllowed = remainingMs > 0;
        Float distance = null;
        if (sheetLocation != null && entry.location != null) {
            float[] result = new float[1];
            Location.distanceBetween(sheetLocation.getLatitude(), sheetLocation.getLongitude(),
                    entry.location.latitude, entry.location.longitude, result);
            distance = result[0];
        }
        boolean distanceAllowed = distance != null && distance <= entry.editDistanceLimitMeters;

        if (sheetMode == SHEET_DETAIL) {
            boolean canEdit = entry.canEdit && timeAllowed && distanceAllowed;
            if (tvEditAvailability != null) {
                tvEditAvailability.setText("可編輯時間剩餘 " + formatRemainingTime(remainingMs));
                tvEditAvailability.setVisibility(canEdit ? View.VISIBLE : View.GONE);
            }
            if (btnEditDiary != null) btnEditDiary.setVisibility(canEdit ? View.VISIBLE : View.GONE);
            return;
        }

        if (tvEditRulesTime != null) tvEditRulesTime.setText(timeAllowed ? "剩餘可編輯時間：" + formatRemainingTime(remainingMs) : "已超過可編輯時間");
        if (tvEditRulesDistance != null) tvEditRulesDistance.setText("目前距離原位置：" + (distance == null ? "無法判斷" : "約 " + formatDistance(distance)));
        String warning = null;
        if (!distanceAllowed) warning = "你目前距離原日記位置超過 1 公里，無法編輯";
        if (!timeAllowed) warning = "日記發布超過 1 小時後無法再編輯";
        if (warning == null && remainingMs <= 5 * 60 * 1000) warning = "可編輯時間即將結束";
        if (tvEditRulesWarning != null) {
            tvEditRulesWarning.setText(warning == null ? "" : warning);
            tvEditRulesWarning.setVisibility(warning == null ? View.GONE : View.VISIBLE);
        }
        if (btnSaveDiary != null && !isDiaryRequestInFlight) {
            btnSaveDiary.setEnabled(timeAllowed && distanceAllowed);
            btnSaveDiary.setAlpha(timeAllowed && distanceAllowed ? 1f : 0.5f);
        }
    }

    private static String formatRemainingTime(long ms) {
        long totalMinutes = Math.max(1, (long) Math.ceil(ms / 60000.0));
        long hours = totalMinutes / 60;
        long minutes = totalMinutes % 60;
        if (hours > 0 && minutes > 0) return hours + " 小時 " + minutes + " 分鐘";
        if (hours > 0) return hours + " 小時";
        return minutes + " 分鐘";
    }

    private static String formatDistance(float meters) {
        if (meters >= 1000) return String.format(java.util.Locale.US, "%.1f 公里", meters / 1000f);
        return Math.round(meters) + " 公尺";
    }

    // 網頁版 formatDiaryDateTime：YYYY/MM/DD HH:mm（手機時區）
    private static String formatDateTime(String iso) {
        long ms = InsightFormat.parseIso(iso);
        if (ms < 0) return "";
        return new java.text.SimpleDateFormat("yyyy/MM/dd HH:mm", java.util.Locale.TAIWAN).format(new java.util.Date(ms));
    }

    private static String formatClock(String iso) {
        long ms = InsightFormat.parseIso(iso);
        if (ms < 0) return "";
        return new java.text.SimpleDateFormat("HH:mm", java.util.Locale.TAIWAN).format(new java.util.Date(ms));
    }

    // 語音輸入：對齊網頁版的 SpeechRecognition（zh-TW），辨識結果接在日記內容後面
    private void startSpeechInput() {
        Intent intent = new Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE, "zh-TW");
        intent.putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT, "正在聆聽...");
        try {
            setSpeechStatus("正在聆聽...");
            speechLauncher.launch(intent);
        } catch (android.content.ActivityNotFoundException e) {
            setSpeechStatus("目前裝置不支援語音輸入，請改用文字輸入。");
        }
    }

    private void setSpeechStatus(String message) {
        if (tvSpeechStatus == null) return;
        tvSpeechStatus.setText(message);
        tvSpeechStatus.setVisibility(message == null || message.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void hideDiaryCard() {
        sheetTicker.removeCallbacks(sheetTickRunnable);
        editingEntry = null;
        if (diaryCardView != null) diaryCardView.setVisibility(View.GONE);
        if (mapBlurOverlay != null) mapBlurOverlay.setVisibility(View.GONE);
    }

    private void clearDiaryForm() {
        if (etDiaryTitle != null) etDiaryTitle.setText("");
        if (diaryInput != null) diaryInput.setText("");
        if (sbMoodIntensity != null) sbMoodIntensity.setProgress(2);
        if (tvTitleCounter != null) tvTitleCounter.setText("0 / 50");
        if (moodSpinner != null) moodSpinner.setSelection(0);
        if (visibilitySpinner != null) visibilitySpinner.setSelection(0);
    }

    private void setDiaryFieldsEnabled(boolean enabled) {
        if (etDiaryTitle != null) etDiaryTitle.setEnabled(enabled);
        if (diaryInput != null) diaryInput.setEnabled(enabled);
        if (sbMoodIntensity != null) sbMoodIntensity.setEnabled(enabled);
        if (moodSpinner != null) {
            moodSpinner.setEnabled(enabled);
            moodSpinner.setAlpha(enabled ? 1f : 0.5f);
        }
        if (visibilitySpinner != null) {
            visibilitySpinner.setEnabled(enabled);
            visibilitySpinner.setAlpha(enabled ? 1f : 0.5f);
        }
    }

    private void setDiaryLoading(boolean loading) {
        isDiaryRequestInFlight = loading;
        if (btnSaveDiary != null) {
            btnSaveDiary.setEnabled(!loading);
            btnSaveDiary.setAlpha(1f);
            btnSaveDiary.setText(loading ? "保存中..." : (sheetMode == SHEET_EDIT ? "儲存變更" : "保存日記"));
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
                btnSwitchMine.setTextColor(getColor(R.color.ad_text));
                btnSwitchExplore.setBackground(null);
                TextView tvExp = getExploreSwitchText(btnSwitchExplore);
                if (tvExp != null) tvExp.setTextColor(getColor(R.color.ad_text_dim));
            });

            btnSwitchExplore.setOnClickListener(v -> {
                btnSwitchExplore.setBackgroundResource(R.drawable.bg_adrift_nav_item);
                TextView tvExp = getExploreSwitchText(btnSwitchExplore);
                if (tvExp != null) tvExp.setTextColor(getColor(R.color.ad_text));
                btnSwitchMine.setBackground(null);
                btnSwitchMine.setTextColor(getColor(R.color.ad_text_dim));
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
            btnAllFilter = navView.findViewById(R.id.filter_all);
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

            // 🎯 附近動態：距離篩選按鈕與清單
            btnRadius1km = navView.findViewById(R.id.btn_radius_1km);
            btnRadius5km = navView.findViewById(R.id.btn_radius_5km);
            btnRadius10km = navView.findViewById(R.id.btn_radius_10km);
            btnRadius50km = navView.findViewById(R.id.btn_radius_50km);
            tvNearbyMineCount = navView.findViewById(R.id.tv_nearby_mine_count);
            tvNearbyVisibleCount = navView.findViewById(R.id.tv_nearby_visible_count);
            tvEmptyNearby = navView.findViewById(R.id.tv_empty_nearby);
            rvNearbyDiaries = navView.findViewById(R.id.rv_nearby_diaries);

            if (rvNearbyDiaries != null) {
                rvNearbyDiaries.setLayoutManager(new LinearLayoutManager(this));
                nearbyDiaryAdapter = new NearbyDiaryAdapter();
                rvNearbyDiaries.setAdapter(nearbyDiaryAdapter);
            }

            View.OnClickListener radiusClick = v -> {
                int radius = v == btnRadius1km ? 1000
                        : v == btnRadius5km ? 5000
                        : v == btnRadius10km ? 10000
                        : 50000;
                currentRadiusMeters = radius;
                updateRadiusButtonUI((Button) v);
                loadNearbyDiaries();
            };
            if (btnRadius1km != null) btnRadius1km.setOnClickListener(radiusClick);
            if (btnRadius5km != null) btnRadius5km.setOnClickListener(radiusClick);
            if (btnRadius10km != null) btnRadius10km.setOnClickListener(radiusClick);
            if (btnRadius50km != null) btnRadius50km.setOnClickListener(radiusClick);

            if (rvDiaryList != null) {
                rvDiaryList.setLayoutManager(new LinearLayoutManager(this));
                diaryListAdapter = new DiaryListAdapter();
                rvDiaryList.setAdapter(diaryListAdapter);
            }

            if (tabMine != null) {
                tabMine.setOnClickListener(v -> {
                    isShowingMine = true;
                    tabMine.setBackgroundResource(R.drawable.bg_login_card);
                    tabMine.setTextColor(getColor(R.color.ad_text));
                    if (tabFriends != null) {
                        tabFriends.setBackground(null);
                        tabFriends.setTextColor(getColor(R.color.ad_text_dim));
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
                    tabFriends.setTextColor(getColor(R.color.ad_text));
                    if (tabMine != null) {
                        tabMine.setBackground(null);
                        tabMine.setTextColor(getColor(R.color.ad_text_dim));
                    }
                    if (layoutMyDiaryRoot != null) layoutMyDiaryRoot.setVisibility(View.GONE);
                    if (layoutFriendManagement != null) layoutFriendManagement.setVisibility(View.VISIBLE);
                    loadNearbyDiaries();
                });
            }

            if (btnAllFilter != null) btnAllFilter.setOnClickListener(v -> handleFilterClick(-1, btnAllFilter));
            if (btnPriv != null) btnPriv.setOnClickListener(v -> handleFilterClick(0, btnPriv));
            if (btnFrdOnly != null) btnFrdOnly.setOnClickListener(v -> handleFilterClick(1, btnFrdOnly));
            if (btnPub != null) btnPub.setOnClickListener(v -> handleFilterClick(2, btnPub));

            updateFilterUI(btnAllFilter);
            updateStatistics();
            updateDiaryList();

        }
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
    // 跟網頁版一樣：全部／公開／朋友／私人 四個按鈕，點哪個就篩選哪個
    private void handleFilterClick(int filterType, Button btn) {
        currentFilter = filterType;
        updateFilterUI(btn);
        updateDiaryList();
    }

    private void updateDiaryList() {
        displayedDiaries.clear();
        for (DiaryEntry entry : markerDataMap.values()) {
            if (currentFilter == -1 || entry.visibility == currentFilter) {
                displayedDiaries.add(entry);
            }
        }
        Collections.sort(displayedDiaries, (d1, d2) -> d2.time.compareTo(d1.time));
        if (diaryListAdapter != null) diaryListAdapter.notifyDataSetChanged();
    }

    private void updateFilterUI(Button selected) {
        Button[] btns = {btnAllFilter, btnPub, btnFrdOnly, btnPriv};
        for (Button b : btns) {
            if (b == null) continue;
            if (b == selected) {
                b.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#00E5FF")));
                b.setTextColor(Color.BLACK);
            } else {
                b.setBackgroundTintList(ColorStateList.valueOf(getColor(R.color.ad_chip_tint)));
                b.setTextColor(getColor(R.color.ad_text));
            }
        }
    }

    // 🎯 附近動態：更新距離篩選按鈕的高亮樣式
    private void updateRadiusButtonUI(Button selected) {
        Button[] radiusButtons = {btnRadius1km, btnRadius5km, btnRadius10km, btnRadius50km};
        for (Button b : radiusButtons) {
            if (b == null) continue;
            if (b == selected) {
                b.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#00E5FF")));
                b.setTextColor(Color.BLACK);
            } else {
                b.setBackgroundTintList(ColorStateList.valueOf(getColor(R.color.ad_chip_tint)));
                b.setTextColor(getColor(R.color.ad_text));
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

    private void scheduleMarkerRefresh() {
        if (markerLayer == null) return;
        markerLayer.setItems(buildMarkerItems());
    }

    private List<DiaryMarkerLayer.Item> buildMarkerItems() {
        List<DiaryMarkerLayer.Item> list = new ArrayList<>();
        for (DiaryEntry entry : markerDataMap.values()) {
            if (entry.id == null || entry.location == null) continue;
            DiaryMarkerLayer.Item item = new DiaryMarkerLayer.Item();
            item.id = entry.id;
            item.lat = entry.location.latitude;
            item.lng = entry.location.longitude;
            item.title = entry.title;
            item.moodLabel = entry.mood;
            item.time = entry.time;
            item.moodColor = DiaryMapper.moodMarkerColor(entry.moodType);
            list.add(item);
        }
        return list;
    }

    private void removeDiaryMarker(String diaryId) {
        if (diaryId == null || diaryId.isEmpty()) return;
        markerDataMap.remove(diaryId);
        scheduleMarkerRefresh();
        if (diaryId.equals(selectedDiaryId)) {
            selectedDiaryId = null;
            if (markerLayer != null) markerLayer.setSelected(null);
            if (previewController != null) previewController.hide(getResources().getInteger(R.integer.motion_fast));
        }
    }

    private void addOrUpdateDiaryMarker(DiaryEntry entry) {
        if (entry == null || entry.id == null || entry.id.isEmpty()) return;
        markerDataMap.put(entry.id, entry);
        scheduleMarkerRefresh();
    }

    private void selectDiaryMarker(String diaryId, boolean animateCamera) {
        if (diaryId == null || diaryId.isEmpty()) return;
        selectedDiaryId = diaryId;
        if (markerLayer != null) {
            markerLayer.closeExpansion();
            markerLayer.setSelected(diaryId);
        }

        DiaryEntry entry = markerDataMap.get(diaryId);
        if (entry == null) return;

        if (previewController != null) previewController.show(buildPreviewData(entry), getResources().getInteger(R.integer.motion_normal));
        if (animateCamera) focusCameraForPreview(entry);
        View root = findViewById(R.id.drawer_layout);
        if (root != null) root.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
    }

    private void clearSelectedDiaryMarker() {
        selectedDiaryId = null;
        lastSelectedMarker = null;
        if (markerLayer != null) markerLayer.setSelected(null);
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
        data.mood = DiaryMapper.moodDisplayLabel(entry.moodType) + " / " + entry.intensity;
        data.reactions = "❤️ " + entry.heartCount + " · 🤗 " + entry.smileCount + " · 🌧 " + entry.surpriseCount;
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
        entry.moodType = uiDiary.moodType;
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
        UserDto author = diary.getAuthor();
        entry.authorCode = author == null ? "" : author.getUserCode();
        entry.editCount = diary.editCount;
        entry.lastEditedAt = diary.lastEditedAt == null ? "" : diary.lastEditedAt;
        entry.editExpiresAt = diary.editExpiresAt == null ? "" : diary.editExpiresAt;
        if (diary.editDistanceLimitMeters != null) entry.editDistanceLimitMeters = diary.editDistanceLimitMeters;
        return entry;
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

    private void bindReactionButtons(DiaryEntry entry) {
        // 對齊網頁版 REACTION_OPTIONS：❤️ 我懂、🤗 抱抱、🌧 有同感，自己按過的會亮起來
        styleReactionButton(btnReactUnderstand, "❤️ 我懂 " + entry.heartCount, "understand".equals(entry.userReaction));
        styleReactionButton(btnReactHug, "🤗 抱抱 " + entry.smileCount, "hug".equals(entry.userReaction));
        styleReactionButton(btnReactRelate, "🌧 有同感 " + entry.surpriseCount, "relate".equals(entry.userReaction));
    }

    private void styleReactionButton(Button button, String text, boolean active) {
        if (button == null) return;
        button.setText(text);
        button.setBackgroundTintList(ColorStateList.valueOf(getColor(active ? R.color.ad_reaction_active : R.color.ad_reaction_bg)));
        button.setTextColor(getColor(active ? R.color.ad_text : R.color.ad_select_text));
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
        refreshNotificationBadge();
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

    private void refreshNotificationBadge() {
        if (notificationRepository == null || tvNotificationBadge == null) return;
        notificationRepository.getNotifications(null, new RepositoryCallback<com.example.topics.data.model.NotificationListData>() {
            @Override
            public void onSuccess(com.example.topics.data.model.NotificationListData value) {
                if (!isActive() || value == null) return;
                int unread = value.unreadCount;
                tvNotificationBadge.setText(unread > 99 ? "99+" : String.valueOf(unread));
                tvNotificationBadge.setVisibility(unread > 0 ? View.VISIBLE : View.GONE);
            }

            @Override
            public void onError(String message) {}
        });
    }

    private void handleRealtimeEvent(String event, String data) {
        if (!isActive()) return;
        if ("notifications:changed".equals(event) || "connected".equals(event)) {
            refreshNotificationBadge();
            return;
        }
        if ("social:changed".equals(event) || "content:changed".equals(event)) {
            if (mapReady && (diaryCardView == null || diaryCardView.getVisibility() != View.VISIBLE)) loadDiariesFromServer();
            refreshNotificationBadge();
            return;
        }
        try {
            com.google.gson.JsonObject payload = com.google.gson.JsonParser.parseString(data).getAsJsonObject();
            if ("diary:deleted".equals(event)) {
                String diaryId = payload.has("diaryId") ? payload.get("diaryId").getAsString() : "";
                if (sheetMode == SHEET_EDIT && editingEntry != null && diaryId.equals(editingEntry.id)) return;
                removeDiaryMarker(diaryId);
            } else if ("diary:created".equals(event) || "diary:updated".equals(event)) {
                if (!payload.has("diary")) return;
                DiaryDto diary = new com.google.gson.Gson().fromJson(payload.get("diary"), DiaryDto.class);
                DiaryEntry entry = buildEntryFromDto(diary);
                if (entry == null) return;
                addOrUpdateDiaryMarker(entry);
                if (entry.id.equals(selectedDiaryId) && previewController != null) {
                    previewController.show(buildPreviewData(entry), 0);
                }
            } else {
                return;
            }
            updateStatistics();
            updateDiaryList();
            updateMapEmptyState();
        } catch (Exception ignored) {
            // 格式不對就略過，下一次正常讀取會補回來
        }
    }

    @Override
    protected void onDestroy() {
        destroyed = true;
        sheetTicker.removeCallbacks(sheetTickRunnable);
        if (markerLayer != null) markerLayer.destroy();
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
        // 深色主題用深色地圖，明亮主題用 Google 預設的淺色地圖（對齊網頁版切換主題時的地圖）
        try {
            if (com.example.topics.data.local.ThemeManager.isNight(this)) {
                googleMap.setMapStyle(MapStyleOptions.loadRawResourceStyle(this, R.raw.map_style_dark));
            } else {
                googleMap.setMapStyle(null);
            }
        } catch (Exception ignored) {}

        mMap.getUiSettings().setMyLocationButtonEnabled(false);
        mMap.getUiSettings().setCompassEnabled(true);
        mMap.getUiSettings().setMapToolbarEnabled(false);

        markerLayer = new DiaryMarkerLayer(this, mMap, findViewById(R.id.map_fragment),
                diaryId -> selectDiaryMarker(diaryId, true));
        markerLayer.setObstacleViews(
                findViewById(R.id.map_identity_chip),
                findViewById(R.id.btn_open_drawer),
                findViewById(R.id.btn_map_notifications),
                findViewById(R.id.map_mode_chip),
                findViewById(R.id.map_control_rail),
                findViewById(R.id.map_bottom_nav),
                findViewById(R.id.diary_preview_card));

        enableMyLocationLayer();

        // 🎯 呼叫下載接收雲端資料
        loadDiariesFromServer();

        mMap.setOnMarkerClickListener(marker -> markerLayer.onMarkerClick(marker));

        mMap.setOnMapClickListener(latLng -> {
            markerLayer.closeExpansion();
            clearSelectedDiaryMarker();
        });
        mMap.setOnCameraMoveStartedListener(reason -> {
            markerLayer.onCameraMoveStarted(reason);
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
                if (markerLayer != null) markerLayer.reset();
                markerDataMap.clear();
                diaryMarkerMap.clear();
                markerDiaryIdMap.clear();
                selectedDiaryId = null;
                lastSelectedMarker = null;
                if (markerLayer != null) markerLayer.setSelected(null);
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

                // 從 Adrift Intelligence 的「常去地點」點進來時，自動打開那篇日記
                String openDiaryId = getIntent().getStringExtra("open_diary_id");
                if (openDiaryId != null) {
                    getIntent().removeExtra("open_diary_id");
                    openDiaryById(openDiaryId);
                }
            }

            @Override
            public void onError(String message) {
                if (!isActive()) return;
                showMapStatus(message == null || message.isEmpty() ? "讀取地圖日記失敗" : message, true);
                updateMapEmptyState();
            }
        });
    }

    // 🎯 附近動態：依目前位置與所選半徑抓取附近公開日記
    private void loadNearbyDiaries() {
        if (mMap == null) return;
        fusedLocationClient.getLastLocation().addOnSuccessListener(this, location -> {
            if (!isActive() || location == null) return;
            diaryRepository.getExploreDiaries(
                    location.getLatitude(),
                    location.getLongitude(),
                    currentRadiusMeters,
                    new RepositoryCallback<List<DiaryDto>>() {
                        @Override
                        public void onSuccess(List<DiaryDto> diaries) {
                            if (!isActive()) return;
                            nearbyDiaries.clear();
                            int mineCount = 0;
                            for (DiaryDto dto : diaries) {
                                DiaryEntry entry = buildEntryFromDto(dto);
                                if (entry != null) {
                                    nearbyDiaries.add(entry);
                                    if (entry.isMine) mineCount++;
                                }
                            }
                            Collections.sort(nearbyDiaries, (d1, d2) -> d2.time.compareTo(d1.time));

                            if (tvNearbyMineCount != null) tvNearbyMineCount.setText(String.valueOf(mineCount));
                            if (tvNearbyVisibleCount != null) tvNearbyVisibleCount.setText(String.valueOf(nearbyDiaries.size()));
                            if (tvEmptyNearby != null) tvEmptyNearby.setVisibility(nearbyDiaries.isEmpty() ? View.VISIBLE : View.GONE);
                            if (nearbyDiaryAdapter != null) nearbyDiaryAdapter.notifyDataSetChanged();
                        }

                        @Override
                        public void onError(String message) {
                            if (!isActive()) return;
                            Toast.makeText(MapActivity.this, message, Toast.LENGTH_SHORT).show();
                        }
                    }
            );
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
                String selectedMood = selectedMoodValue();
                int intensity = (sbMoodIntensity != null) ? sbMoodIntensity.getProgress() + 1 : 3;
                String diaryText = (diaryInput != null) ? diaryInput.getText().toString().trim() : "";
                int visibility = selectedVisibilityIndex();
                // 驗證規則對齊網頁版 DiaryModal
                if (title.isEmpty()) {
                    Toast.makeText(this, "請輸入日記標題", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (title.length() > 50) {
                    Toast.makeText(this, "日記標題最多 50 字", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (diaryText.isEmpty()) {
                    Toast.makeText(this, "請輸入日記內容", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (diaryText.length() > DIARY_TEXT_MAX) {
                    Toast.makeText(this, "日記內容最多 2000 字", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (processingPhoto) {
                    Toast.makeText(this, "照片處理中，請稍候", Toast.LENGTH_SHORT).show();
                    return;
                }

                DiaryEntry existing = sheetMode == SHEET_EDIT ? editingEntry : null;
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
        // 跟網頁版一樣先把座標轉成地點名稱再送出，網頁上才看得到地名
        PlaceNameResolver.resolve(this, location.latitude, location.longitude, placeName -> {
            if (!isActive()) return;
            submitNewDiary(title, mood, intensity, content, visibility, location, placeName);
        });
    }

    private void submitNewDiary(String title, String mood, int intensity, String content, int visibility, LatLng location, String placeName) {
        Uri imageUri = selectedImageUris.isEmpty() ? null : selectedImageUris.get(0);
        diaryRepository.createDiary(
                title,
                content,
                mood,
                intensity,
                DiaryMapper.visibilityApiValue(visibility),
                location.latitude,
                location.longitude,
                PlaceNameResolver.isResolved(placeName) ? placeName : "",
                locationAccuracyType(),
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
                mood,
                intensity,
                DiaryMapper.visibilityApiValue(visibility),
                location.getLatitude(),
                location.getLongitude(),
                locationAccuracyType(),
                new RepositoryCallback<DiaryDto>() {
                    @Override
                    public void onSuccess(DiaryDto diary) {
                        if (!isActive()) return;
                        setDiaryLoading(false);
                        DiaryEntry entry = buildEntryFromDto(diary);
                        if (entry != null) {
                            addOrUpdateDiaryMarker(entry);
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

            bindDiaryListRow(holder.itemView, entry);

            holder.itemView.setOnClickListener(v -> {
                if (drawerLayout != null) drawerLayout.closeDrawer(GravityCompat.END);
                selectDiaryMarker(entry.id, true);
            });
        }

        @Override
        public int getItemCount() { return displayedDiaries.size(); }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvTitle, tvTime, tvIcon, tvHeart, tvSmile, tvSurprise;
            TextView tvItemAuthor;

            ViewHolder(View v) {
                super(v);
                tvTitle = v.findViewById(R.id.tv_item_title);
                tvTime = v.findViewById(R.id.tv_item_time);
                tvIcon = v.findViewById(R.id.tv_item_icon);
                tvHeart = v.findViewById(R.id.tv_heart_count);
                tvSmile = v.findViewById(R.id.tv_smile_count);
                tvSurprise = v.findViewById(R.id.tv_surprise_count);
                tvItemAuthor = v.findViewById(R.id.tv_item_author);
            }
        }
    }

    // 🎯 附近動態列表配接器
    private class NearbyDiaryAdapter extends RecyclerView.Adapter<NearbyDiaryAdapter.ViewHolder> {
        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_diary_list, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            DiaryEntry entry = nearbyDiaries.get(position);
            bindDiaryListRow(holder.itemView, entry);
            holder.itemView.setOnClickListener(v -> {
                if (drawerLayout != null) drawerLayout.closeDrawer(GravityCompat.END);
                addOrUpdateDiaryMarker(entry);
                selectDiaryMarker(entry.id, true);
            });
        }

        @Override
        public int getItemCount() { return nearbyDiaries.size(); }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvTitle, tvTime, tvIcon, tvHeart, tvSmile, tvSurprise, tvItemAuthor;
            ViewHolder(View v) {
                super(v);
                tvTitle = v.findViewById(R.id.tv_item_title);
                tvTime = v.findViewById(R.id.tv_item_time);
                tvIcon = v.findViewById(R.id.tv_item_icon);
                tvHeart = v.findViewById(R.id.tv_heart_count);
                tvSmile = v.findViewById(R.id.tv_smile_count);
                tvSurprise = v.findViewById(R.id.tv_surprise_count);
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

    // 日記清單的一列，對齊網頁版 MemoryPanel 的 memory-item：標題、共鳴數、@作者ID、已編輯、相對時間
    private void bindDiaryListRow(View row, DiaryEntry entry) {
        TextView tvTitle = row.findViewById(R.id.tv_item_title);
        TextView tvTime = row.findViewById(R.id.tv_item_time);
        TextView tvIcon = row.findViewById(R.id.tv_item_icon);
        TextView tvHeart = row.findViewById(R.id.tv_heart_count);
        TextView tvSmile = row.findViewById(R.id.tv_smile_count);
        TextView tvSurprise = row.findViewById(R.id.tv_surprise_count);
        TextView tvAuthor = row.findViewById(R.id.tv_item_author);
        TextView tvEdited = row.findViewById(R.id.tv_item_edited);

        if (tvTitle != null) tvTitle.setText(entry.title == null || entry.title.isEmpty() ? "（未命名日記）" : entry.title);
        if (tvTime != null) tvTime.setText(DiaryTime.format(entry.time));
        if (tvHeart != null) tvHeart.setText("❤️ " + entry.heartCount);
        if (tvSmile != null) tvSmile.setText("🤗 " + entry.smileCount);
        if (tvSurprise != null) tvSurprise.setText("🌧 " + entry.surpriseCount);
        if (tvIcon != null) tvIcon.setVisibility(View.GONE);
        if (tvAuthor != null) {
            String code = entry.authorCode == null || entry.authorCode.isEmpty() ? "unknown" : entry.authorCode;
            tvAuthor.setText("@" + code);
        }
        if (tvEdited != null) {
            boolean edited = entry.editCount > 0 || (entry.lastEditedAt != null && !entry.lastEditedAt.isEmpty());
            tvEdited.setVisibility(edited ? View.VISIBLE : View.GONE);
        }
    }

    private String formatDate(String value) {
        String formatted = DiaryTime.format(value);
        return formatted.isEmpty() ? "尚無資料" : formatted;
    }
}
