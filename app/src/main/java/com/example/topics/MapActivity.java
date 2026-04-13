package com.example.topics;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.PopupMenu;
import androidx.cardview.widget.CardView;
import androidx.core.app.ActivityCompat;
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
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MapActivity extends AppCompatActivity implements OnMapReadyCallback {

    private GoogleMap mMap;
    private FusedLocationProviderClient fusedLocationClient;

    private Spinner moodSpinner;
    private EditText diaryInput;
    private BottomSheetBehavior<View> bottomSheetBehavior;

    // --- 多圖預覽相關 ---
    private RecyclerView rvImagePreview;
    private ImageAdapter imageAdapter;
    private List<Uri> selectedImageUris = new ArrayList<>();

    private Map<String, DiaryEntry> markerDataMap = new HashMap<>();
    private Marker lastSelectedMarker = null;

    class DiaryEntry {
        LatLng location;
        String mood;
        String text;
        String time;
        List<Uri> images;
        public DiaryEntry(LatLng loc, String m, String t, String time, List<Uri> images) {
            this.location = loc; this.mood = m; this.text = t; this.time = time; this.images = images;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_map);

        initNavigation();
        View bottomSheet = findViewById(R.id.diary_bottom_sheet);
        bottomSheetBehavior = BottomSheetBehavior.from(bottomSheet);
        bottomSheetBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);

        findViewById(R.id.fab_show_diary).setOnClickListener(v -> {
            lastSelectedMarker = null;
            diaryInput.setText("");
            diaryInput.setEnabled(true);
            selectedImageUris.clear();
            imageAdapter.notifyDataSetChanged();
            rvImagePreview.setVisibility(View.GONE);
            bottomSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
        });

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        moodSpinner = findViewById(R.id.mood_spinner);
        diaryInput = findViewById(R.id.diary_input);

        rvImagePreview = findViewById(R.id.rv_image_preview);
        rvImagePreview.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        imageAdapter = new ImageAdapter();
        rvImagePreview.setAdapter(imageAdapter);

        // 💡 加入拖拽排序功能
        ItemTouchHelper itemTouchHelper = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(
                ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT, 0) {
            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
                if (!diaryInput.isEnabled()) return false;
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

        String[] moods = {"😊 開心", "😢 難過", "😎 平靜", "🤩 興奮", "😴 累了"};
        moodSpinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, moods));

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
                                if (!selectedImageUris.contains(uri)) {
                                    selectedImageUris.add(uri);
                                }
                            }
                        } else if (data.getData() != null) {
                            Uri uri = data.getData();
                            if (!selectedImageUris.contains(uri)) {
                                selectedImageUris.add(uri);
                            }
                        }
                        imageAdapter.notifyDataSetChanged();
                        rvImagePreview.setVisibility(View.VISIBLE);
                    }
                }
        );

        findViewById(R.id.card_image_picker).setOnClickListener(v -> {
            if (diaryInput.isEnabled()) {
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.setType("image/*");
                intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                photoPickerLauncher.launch(Intent.createChooser(intent, "選擇照片"));
            } else {
                Toast.makeText(this, "查看模式下無法修改照片", Toast.LENGTH_SHORT).show();
            }
        });

        findViewById(R.id.btn_save).setOnClickListener(v -> {
            if (diaryInput.isEnabled()) {
                saveTrace();
                bottomSheetBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);
            } else {
                Toast.makeText(this, "目前為閱讀模式，無法儲存修改", Toast.LENGTH_SHORT).show();
            }
        });

        findViewById(R.id.btn_more_options).setOnClickListener(v -> {
            PopupMenu popup = new PopupMenu(this, v);
            popup.getMenu().add("🗑️ 刪除這則日記");
            popup.setOnMenuItemClickListener(item -> {
                if (item.getTitle().equals("🗑️ 刪除這則日記")) {
                    if (lastSelectedMarker != null) {
                        deleteCurrentDiary();
                    } else {
                        Toast.makeText(this, "請先點選地圖上的標記再進行刪除", Toast.LENGTH_SHORT).show();
                    }
                    return true;
                }
                return false;
            });
            popup.show();
        });
    }

    private void deleteCurrentDiary() {
        if (lastSelectedMarker != null) {
            markerDataMap.remove(lastSelectedMarker.getId());
            lastSelectedMarker.remove();
            lastSelectedMarker = null;

            diaryInput.setText("");
            selectedImageUris.clear();
            imageAdapter.notifyDataSetChanged();
            rvImagePreview.setVisibility(View.GONE);

            bottomSheetBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);
            Toast.makeText(this, "日記已成功刪除", Toast.LENGTH_SHORT).show();
        }
    }

    private void initNavigation() {
        findViewById(R.id.nav_home).setOnClickListener(v -> startActivity(new Intent(this, MainActivity.class)));
        findViewById(R.id.nav_map).setOnClickListener(v -> Toast.makeText(this, "目前已在日記地圖", Toast.LENGTH_SHORT).show());
        findViewById(R.id.nav_friends).setOnClickListener(v -> Toast.makeText(this, "好友功能開發中！", Toast.LENGTH_SHORT).show());
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            mMap.setMyLocationEnabled(true);
        }

        mMap.setInfoWindowAdapter(new GoogleMap.InfoWindowAdapter() {
            @Override
            public View getInfoWindow(@NonNull Marker marker) { return null; }
            @Override
            public View getInfoContents(@NonNull Marker marker) {
                View view = getLayoutInflater().inflate(R.layout.custom_info_window, null);
                TextView tvTitle = view.findViewById(R.id.tv_info_title);
                TextView tvSnippet = view.findViewById(R.id.tv_info_snippet);
                ImageView ivImage = view.findViewById(R.id.iv_info_image);

                tvTitle.setText(marker.getTitle());
                tvSnippet.setText(marker.getSnippet());

                DiaryEntry entry = markerDataMap.get(marker.getId());
                if (entry != null && entry.images != null && !entry.images.isEmpty()) {
                    ivImage.setVisibility(View.VISIBLE);
                    ivImage.setImageURI(entry.images.get(0));
                } else {
                    ivImage.setVisibility(View.GONE);
                }
                return view;
            }
        });

        mMap.setOnMarkerClickListener(marker -> {
            bottomSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
            lastSelectedMarker = marker;

            DiaryEntry entry = markerDataMap.get(marker.getId());
            if (entry != null) {
                diaryInput.setText(entry.text);
                for (int i = 0; i < moodSpinner.getCount(); i++) {
                    if (moodSpinner.getItemAtPosition(i).toString().equals(entry.mood)) {
                        moodSpinner.setSelection(i);
                        break;
                    }
                }
                selectedImageUris.clear();
                if (entry.images != null && !entry.images.isEmpty()) {
                    selectedImageUris.addAll(entry.images);
                    rvImagePreview.setVisibility(View.VISIBLE);
                } else {
                    rvImagePreview.setVisibility(View.GONE);
                }
                imageAdapter.notifyDataSetChanged();
            }
            checkDistanceAndEdit(marker);
            return false;
        });
    }

    private void saveTrace() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return;

        fusedLocationClient.getLastLocation().addOnSuccessListener(this, location -> {
            if (location != null) {
                LatLng currentPos = (lastSelectedMarker != null) ? lastSelectedMarker.getPosition() : new LatLng(location.getLatitude(), location.getLongitude());
                String selectedMood = moodSpinner.getSelectedItem().toString();
                String diaryText = diaryInput.getText().toString();
                String currentTime = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(new Date());

                if (lastSelectedMarker != null) {
                    markerDataMap.remove(lastSelectedMarker.getId());
                    lastSelectedMarker.remove();
                    lastSelectedMarker = null;
                }

                DiaryEntry entry = new DiaryEntry(currentPos, selectedMood, diaryText, currentTime, new ArrayList<>(selectedImageUris));
                Marker marker = mMap.addMarker(new MarkerOptions()
                        .position(currentPos)
                        .title(selectedMood + " (" + currentTime + ")")
                        .snippet(diaryText));

                markerDataMap.put(marker.getId(), entry);
                selectedImageUris.clear();
                imageAdapter.notifyDataSetChanged();
                rvImagePreview.setVisibility(View.GONE);
                diaryInput.setText("");
                Toast.makeText(this, "紀錄成功！", Toast.LENGTH_SHORT).show();
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

                float distanceInMeters = results[0];

                if (distanceInMeters <= 50) {
                    lastSelectedMarker = marker;
                    diaryInput.setEnabled(true);
                    imageAdapter.notifyDataSetChanged();
                } else {
                    diaryInput.setEnabled(false);
                    imageAdapter.notifyDataSetChanged();
                    Toast.makeText(this, "太遠了！僅供閱讀。距離約 " + (int)distanceInMeters + "m", Toast.LENGTH_LONG).show();
                }
            }
        });
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
            holder.img.setScaleType(ImageView.ScaleType.CENTER_CROP);

            if (diaryInput.isEnabled()) {
                holder.btnDelete.setVisibility(View.VISIBLE);
                holder.btnDelete.setImageResource(android.R.drawable.ic_menu_close_clear_cancel);
                holder.btnDelete.setColorFilter(android.graphics.Color.parseColor("#424242"));
                holder.btnDelete.setBackgroundResource(android.R.drawable.presence_offline);
                holder.btnDelete.getBackground().setTint(android.graphics.Color.parseColor("#EEEEEE"));
            } else {
                holder.btnDelete.setVisibility(View.GONE);
            }

            holder.btnDelete.setOnClickListener(v -> {
                if (diaryInput.isEnabled()) {
                    int currentPos = holder.getAdapterPosition();
                    if (currentPos != RecyclerView.NO_POSITION) {
                        selectedImageUris.remove(currentPos);
                        notifyDataSetChanged();
                        if (selectedImageUris.isEmpty()) rvImagePreview.setVisibility(View.GONE);
                    }
                }
            });
            // 💡 移除原本的 setOnLongClickListener 刪除邏輯
        }

        @Override
        public int getItemCount() { return selectedImageUris.size(); }

        class ViewHolder extends RecyclerView.ViewHolder {
            ImageView img;
            ImageView btnDelete;
            ViewHolder(View v) {
                super(v);
                img = v.findViewById(R.id.iv_preview);
                btnDelete = v.findViewById(R.id.btn_delete_img);
            }
        }
    }
}0