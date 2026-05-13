package com.example.topics;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log; // 💡 新增：用來印出連線錯誤訊息
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
import com.google.android.material.navigation.NavigationView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

// 💡 新增：Retrofit 連線所需的套件
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class MapActivity extends AppCompatActivity implements OnMapReadyCallback {

    private GoogleMap mMap;
    private FusedLocationProviderClient fusedLocationClient;
    private DrawerLayout drawerLayout;

    private View diaryCardView;
    private View mapBlurOverlay; // 新增遮罩層變數
    private EditText etDiaryTitle, diaryInput;
    private Spinner moodSpinner, visibilitySpinner;
    private SeekBar sbMoodIntensity;
    private TextView tvIntensityLabel;
    private ImageButton btnCloseDiary;

    private TextView tabMine, tabFriends;
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
    private Marker lastSelectedMarker = null;

    // 這是你原本用來管理地圖標記的內部類別 (維持不變)
    class DiaryEntry {
        LatLng location;
        String title;
        String mood;
        int intensity;
        String text;
        String time;
        List<Uri> images;
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

        public void updateReaction(int type) {
            if (myReaction == 1) heartCount--;
            else if (myReaction == 2) smileCount--;
            else if (myReaction == 3) surpriseCount--;

            if (myReaction == type) {
                myReaction = 0;
            } else {
                myReaction = type;
                if (type == 1) heartCount++;
                else if (type == 2) smileCount++;
                else if (type == 3) surpriseCount++;
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_map);

        drawerLayout = findViewById(R.id.drawer_layout);
        ImageButton btnOpenDrawer = findViewById(R.id.btn_open_drawer);
        if (btnOpenDrawer != null) {
            btnOpenDrawer.setOnClickListener(v -> {
                if (drawerLayout != null) drawerLayout.openDrawer(GravityCompat.END);
            });
        }

        initNavigation();
        initToggleSwitch();

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
                lastSelectedMarker = null;
                if (etDiaryTitle != null) { etDiaryTitle.setText(""); etDiaryTitle.setEnabled(true); }
                if (diaryInput != null) { diaryInput.setText(""); diaryInput.setEnabled(true); }
                if (sbMoodIntensity != null) { sbMoodIntensity.setProgress(3); sbMoodIntensity.setEnabled(true); }
                if (moodSpinner != null) moodSpinner.setEnabled(true);
                if (visibilitySpinner != null) { visibilitySpinner.setEnabled(true); visibilitySpinner.setSelection(0); }

                selectedImageUris.clear();
                if (imageAdapter != null) imageAdapter.notifyDataSetChanged();
                if (rvImagePreview != null) rvImagePreview.setVisibility(View.GONE);

                showDiaryCard("新增日記");
            });
        }

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        if (rvImagePreview != null) {
            rvImagePreview.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
            imageAdapter = new ImageAdapter();
            rvImagePreview.setAdapter(imageAdapter);

            ItemTouchHelper itemTouchHelper = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(
                    ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT, 0) {
                @Override
                public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
                    if (diaryInput != null && !diaryInput.isEnabled()) return false;
                    int from = viewHolder.getAdapterPosition();
                    int to = target.getAdapterPosition();
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
                if (diaryInput != null && diaryInput.isEnabled()) {
                    Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                    intent.setType("image/*");
                    intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                    photoPickerLauncher.launch(Intent.createChooser(intent, "選擇照片"));
                } else {
                    Toast.makeText(this, "查看模式下無法修改照片", Toast.LENGTH_SHORT).show();
                }
            });
        }

        View btnSave = findViewById(R.id.btn_save);
        if (btnSave != null) {
            btnSave.setOnClickListener(v -> {
                if (diaryInput != null && diaryInput.isEnabled()) {
                    saveTrace();
                    hideDiaryCard();
                } else {
                    Toast.makeText(this, "目前為閱讀模式，無法儲存修改", Toast.LENGTH_SHORT).show();
                }
            });
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

    private void initToggleSwitch() {
        TextView btnSwitchMine = findViewById(R.id.btn_switch_mine);
        LinearLayout btnSwitchExplore = findViewById(R.id.btn_switch_explore);

        if (btnSwitchMine != null && btnSwitchExplore != null) {
            btnSwitchMine.setOnClickListener(v -> {
                btnSwitchMine.setBackgroundResource(R.drawable.bg_login_card);
                btnSwitchMine.setTextColor(Color.WHITE);
                btnSwitchExplore.setBackground(null);
                TextView tvExp = (TextView) btnSwitchExplore.getChildAt(1);
                ImageView ivExp = (ImageView) btnSwitchExplore.getChildAt(0);
                tvExp.setTextColor(Color.parseColor("#99FFFFFF"));
                ivExp.setImageTintList(ColorStateList.valueOf(Color.parseColor("#99FFFFFF")));
            });

            btnSwitchExplore.setOnClickListener(v -> {
                btnSwitchExplore.setBackgroundResource(R.drawable.bg_login_card);
                TextView tvExp = (TextView) btnSwitchExplore.getChildAt(1);
                ImageView ivExp = (ImageView) btnSwitchExplore.getChildAt(0);
                tvExp.setTextColor(Color.WHITE);
                ivExp.setImageTintList(ColorStateList.valueOf(Color.WHITE));
                btnSwitchMine.setBackground(null);
                btnSwitchMine.setTextColor(Color.parseColor("#99FFFFFF"));
            });
        }
    }

    private void deleteCurrentDiary() {
        if (lastSelectedMarker != null) {
            markerDataMap.remove(lastSelectedMarker.getId());
            lastSelectedMarker.remove();
            lastSelectedMarker = null;
            if (etDiaryTitle != null) etDiaryTitle.setText("");
            if (diaryInput != null) diaryInput.setText("");
            selectedImageUris.clear();
            if (imageAdapter != null) imageAdapter.notifyDataSetChanged();
            if (rvImagePreview != null) rvImagePreview.setVisibility(View.GONE);
            hideDiaryCard();
            Toast.makeText(this, "日記已刪除", Toast.LENGTH_SHORT).show();
            updateDiaryList();
            updateStatistics();
        }
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

            layoutMyDiaryRoot = navView.findViewById(R.id.layout_my_diary_root);
            layoutFriendManagement = navView.findViewById(R.id.layout_friend_management);
            etSearchFriend = navView.findViewById(R.id.et_search_friend);
            btnSearchUser = navView.findViewById(R.id.btn_search_user);

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
                    updateFriendEmptyStates();
                });
            }

            if (btnPriv != null) btnPriv.setOnClickListener(v -> handleFilterClick(0, btnPriv));
            if (btnFrdOnly != null) btnFrdOnly.setOnClickListener(v -> handleFilterClick(1, btnFrdOnly));
            if (btnPub != null) btnPub.setOnClickListener(v -> handleFilterClick(2, btnPub));

            updateStatistics();
            updateDiaryList();

            View btnLogout = navView.findViewById(R.id.btn_logout);
            if (btnLogout != null) {
                btnLogout.setOnClickListener(v -> {
                    Intent intent = new Intent(this, LoginActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    finish();
                });
            }
        }
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
            if (isShowingMine == entry.isMine) {
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
            int friendsCount = 0;
            for (DiaryEntry entry : markerDataMap.values()) {
                if (entry.isMine) mineCount++;
                else friendsCount++;
            }
            tvCountMine.setText(String.valueOf(mineCount));
            tvCountVisible.setText(String.valueOf(friendsCount));
        }
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        try {
            googleMap.setMapStyle(MapStyleOptions.loadRawResourceStyle(this, R.raw.map_style_dark));
        } catch (Exception e) { e.printStackTrace(); }

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            mMap.setMyLocationEnabled(true);
        }

        mMap.setOnMarkerClickListener(marker -> {
            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(marker.getPosition(), 15f));
            showDiaryCard("編輯日記");
            lastSelectedMarker = marker;
            DiaryEntry entry = markerDataMap.get(marker.getId());
            if (entry != null) {
                if (etDiaryTitle != null) etDiaryTitle.setText(entry.title);
                if (diaryInput != null) diaryInput.setText(entry.text);
                if (sbMoodIntensity != null) sbMoodIntensity.setProgress(entry.intensity);
                if (moodSpinner != null) {
                    for (int i = 0; i < moodSpinner.getCount(); i++) {
                        if (moodSpinner.getItemAtPosition(i).toString().equals(entry.mood)) {
                            moodSpinner.setSelection(i);
                            break;
                        }
                    }
                }
                if (visibilitySpinner != null) visibilitySpinner.setSelection(entry.visibility);

                selectedImageUris.clear();
                if (entry.images != null) selectedImageUris.addAll(entry.images);
                if (imageAdapter != null) imageAdapter.notifyDataSetChanged();
                if (rvImagePreview != null) rvImagePreview.setVisibility(selectedImageUris.isEmpty() ? View.GONE : View.VISIBLE);

                checkDistanceAndEdit(marker);
            }
            return true;
        });
    }

    // 💡 修改部分：在儲存至本地地圖的最後，呼叫上傳至雲端的方法
    private void saveTrace() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return;
        fusedLocationClient.getLastLocation().addOnSuccessListener(this, location -> {
            if (location != null) {
                String title = (etDiaryTitle != null) ? etDiaryTitle.getText().toString() : "";
                String selectedMood = (moodSpinner != null) ? moodSpinner.getSelectedItem().toString() : "";
                int intensity = (sbMoodIntensity != null) ? sbMoodIntensity.getProgress() : 3;
                String diaryText = (diaryInput != null) ? diaryInput.getText().toString() : "";
                String currentTime = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(new Date());
                int visibility = (visibilitySpinner != null) ? visibilitySpinner.getSelectedItemPosition() : 0;

                LatLng savePos;
                if (lastSelectedMarker != null) {
                    savePos = lastSelectedMarker.getPosition();
                    markerDataMap.remove(lastSelectedMarker.getId());
                    lastSelectedMarker.remove();
                } else {
                    savePos = new LatLng(location.getLatitude(), location.getLongitude());
                }

                DiaryEntry entry = new DiaryEntry(savePos, title, selectedMood, intensity, diaryText, currentTime, new ArrayList<>(selectedImageUris), true, visibility);
                Marker newMarker = mMap.addMarker(new MarkerOptions().position(savePos).title(title.isEmpty() ? selectedMood : title));

                if (newMarker != null) {
                    markerDataMap.put(newMarker.getId(), entry);
                    lastSelectedMarker = newMarker;
                }

                Toast.makeText(this, (lastSelectedMarker != null) ? "日記已更新！" : "紀錄成功！", Toast.LENGTH_SHORT).show();
                updateStatistics();
                updateDiaryList();

                // 💡 在這裡觸發上傳到資料庫！
                uploadDiaryToDatabase(title, selectedMood, diaryText, savePos, currentTime);
            }
        });
    }

    private void checkDistanceAndEdit(Marker marker) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return;
        fusedLocationClient.getLastLocation().addOnSuccessListener(this, location -> {
            if (location != null) {
                float[] results = new float[1];
                Location.distanceBetween(location.getLatitude(), location.getLongitude(),
                        marker.getPosition().latitude, marker.getPosition().longitude, results);

                boolean canEdit = results[0] <= 50;
                if (etDiaryTitle != null) etDiaryTitle.setEnabled(canEdit);
                if (diaryInput != null) diaryInput.setEnabled(canEdit);
                if (sbMoodIntensity != null) sbMoodIntensity.setEnabled(canEdit);
                if (moodSpinner != null) moodSpinner.setEnabled(canEdit);
                if (visibilitySpinner != null) visibilitySpinner.setEnabled(canEdit);

                if (!canEdit) {
                    Toast.makeText(this, "太遠了！僅供閱讀。距離約 " + (int)results[0] + "m", Toast.LENGTH_SHORT).show();
                }
                if (imageAdapter != null) imageAdapter.notifyDataSetChanged();
            }
        });
    }

    // 💡 新增：上傳到資料庫的專屬方法
    private void uploadDiaryToDatabase(String title, String mood, String content, LatLng location, String time) {
        // 使用 com.example.topics.DiaryEntry，避免跟你上面定義的內部類別 DiaryEntry 搞混
        com.example.topics.DiaryEntry apiEntry = new com.example.topics.DiaryEntry(
                "USER_ID", // 之後從登入資訊拿
                title,
                mood,
                content,
                location.latitude,
                location.longitude,
                time
        );

        // 將原本的 getApiService() 改為 getRetrofitInstance().create(ApiService.class)
        RetrofitClient.getRetrofitInstance().create(ApiService.class).saveDiary(apiEntry).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                if (response.isSuccessful()) {
                    Toast.makeText(MapActivity.this, "日誌保存成功！", Toast.LENGTH_SHORT).show();
                } else {
                    Log.e("API_SAVE", "資料儲存失敗，Error Code: " + response.code());
                }
            }

            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                Log.e("API_SAVE", "連線伺服器失敗: " + t.getMessage());
            }
        });
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
            holder.tvTitle.setText(entry.title.isEmpty() ? entry.mood : entry.title);
            holder.tvTime.setText(entry.time);
            holder.tvHeart.setText("❤️ " + entry.heartCount);
            holder.tvSmile.setText("😊 " + entry.smileCount);
            holder.tvSurprise.setText("☔ " + entry.surpriseCount);

            String icon = (entry.visibility == 0) ? "🔒" : (entry.visibility == 1) ? "👥" : "👁️";
            if (holder.tvIcon != null) holder.tvIcon.setText(icon);

            holder.itemView.setOnClickListener(v -> {
                mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(entry.location, 15f));
                if (drawerLayout != null) drawerLayout.closeDrawer(GravityCompat.END);

                showDiaryCard("編輯日記");
                if (etDiaryTitle != null) etDiaryTitle.setText(entry.title);
                if (diaryInput != null) diaryInput.setText(entry.text);
                setDiaryFieldsEnabled(false);
            });

            if (entry.isMine) {
                holder.itemView.setOnLongClickListener(v -> {
                    PopupMenu popup = new PopupMenu(MapActivity.this, v);
                    popup.getMenu().add(0, 0, 0, "🔒 設為私人");
                    popup.getMenu().add(0, 1, 1, "👥 設為朋友");
                    popup.getMenu().add(0, 2, 2, "👁️ 設為公開");
                    popup.getMenu().add(0, 3, 3, "🗑️ 刪除日記");
                    popup.setOnMenuItemClickListener(item -> {
                        if (item.getItemId() == 3) {
                            for (Map.Entry<String, DiaryEntry> e : markerDataMap.entrySet()) {
                                if (e.getValue() == entry) {
                                    markerDataMap.remove(e.getKey());
                                    updateDiaryList();
                                    updateStatistics();
                                    Toast.makeText(MapActivity.this, "已刪除", Toast.LENGTH_SHORT).show();
                                    break;
                                }
                            }
                        } else {
                            entry.visibility = item.getItemId();
                            updateDiaryList();
                            Toast.makeText(MapActivity.this, "權限已更新", Toast.LENGTH_SHORT).show();
                        }
                        return true;
                    });
                    popup.show();
                    return true;
                });
            }
        }

        private void setDiaryFieldsEnabled(boolean enabled) {
            if (etDiaryTitle != null) etDiaryTitle.setEnabled(enabled);
            if (diaryInput != null) diaryInput.setEnabled(enabled);
            if (sbMoodIntensity != null) sbMoodIntensity.setEnabled(enabled);
            if (moodSpinner != null) moodSpinner.setEnabled(enabled);
            if (visibilitySpinner != null) visibilitySpinner.setEnabled(enabled);
        }

        @Override
        public int getItemCount() { return displayedDiaries.size(); }
        class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvTitle, tvTime, tvIcon, tvHeart, tvSmile, tvSurprise;
            ViewHolder(View v) {
                super(v);
                tvTitle = v.findViewById(R.id.tv_item_title);
                tvTime = v.findViewById(R.id.tv_item_time);
                tvIcon = v.findViewById(R.id.tv_item_icon);
                tvHeart = v.findViewById(R.id.tv_heart_count);
                tvSmile = v.findViewById(R.id.tv_smile_count);
                tvSurprise = v.findViewById(R.id.tv_surprise_count);
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
            holder.img.setImageURI(selectedImageUris.get(position));
            holder.btnDelete.setVisibility((diaryInput != null && diaryInput.isEnabled()) ? View.VISIBLE : View.GONE);
            holder.btnDelete.setOnClickListener(v -> {
                int cp = holder.getAdapterPosition();
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
}
