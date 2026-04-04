package com.example.topics;

import android.Manifest;
import android.content.Intent; // 💡 新增：跳轉頁面用
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView; // 💡 新增：選單文字按鈕用
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.app.ActivityCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

//地圖記錄日記功能
public class MapActivity extends AppCompatActivity implements OnMapReadyCallback {

    private GoogleMap mMap;
    private FusedLocationProviderClient fusedLocationClient;

    private Spinner moodSpinner;
    private EditText diaryInput;
    private ImageView selectedImagePreview;
    private CardView cardImagePicker;

    private Map<String, DiaryEntry> markerDataMap = new HashMap<>();
    private Marker lastSelectedMarker = null;

    class DiaryEntry {
        LatLng location;
        String mood;
        String text;
        String time;
        public DiaryEntry(LatLng loc, String m, String t, String time) {
            this.location = loc; this.mood = m; this.text = t; this.time = time;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_map);

        // --- 💡 這裡是新增的選單跳轉功能 ---

        // 1. 點擊「🏠 首頁」回主畫面
        findViewById(R.id.nav_home).setOnClickListener(v -> {
            Intent intent = new Intent(MapActivity.this, MainActivity.class);
            startActivity(intent);
        });

        // 2. 點擊「📍 日記地圖」 (因為已經在這一頁了，顯示提示)
        findViewById(R.id.nav_map).setOnClickListener(v -> {
            Toast.makeText(this, "目前已在日記地圖", Toast.LENGTH_SHORT).show();
        });

        // 3. 點擊「👥 好友」 (提示尚未開發)
        findViewById(R.id.nav_friends).setOnClickListener(v -> {
            Toast.makeText(this, "好友功能開發中，敬請期待！", Toast.LENGTH_SHORT).show();
        });

        // --- 💡 選單跳轉結束，下面是你原本的代碼 ---

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        moodSpinner = findViewById(R.id.mood_spinner);
        diaryInput = findViewById(R.id.diary_input);
        selectedImagePreview = findViewById(R.id.selected_image_preview);
        cardImagePicker = findViewById(R.id.card_image_picker);
        Button btnSave = findViewById(R.id.btn_save);

        String[] moods = {"😊 開心", "😢 難過", "😎 平靜", "🤩 興奮", "😴 累了"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, moods);
        moodSpinner.setAdapter(adapter);

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map_fragment);
        if (mapFragment != null) mapFragment.getMapAsync(this);

        ActivityResultLauncher<String> mGetContent = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri != null) {
                        selectedImagePreview.setImageURI(uri);
                        selectedImagePreview.setPadding(0, 0, 0, 0);
                        selectedImagePreview.clearColorFilter();
                    }
                }
        );

        cardImagePicker.setOnClickListener(v -> mGetContent.launch("image/*"));
        btnSave.setOnClickListener(v -> saveTrace());
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            mMap.setMyLocationEnabled(true);
        }

        mMap.setOnMarkerClickListener(marker -> {
            checkDistanceAndEdit(marker);
            return false;
        });
    }

    // 檢查距離 (50公尺限制)
    private void checkDistanceAndEdit(Marker marker) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return;

        fusedLocationClient.getLastLocation().addOnSuccessListener(this, location -> {
            if (location != null) {
                float[] results = new float[1];
                // 計算目前位置與標記點的距離
                Location.distanceBetween(location.getLatitude(), location.getLongitude(),
                        marker.getPosition().latitude, marker.getPosition().longitude, results);

                float distanceInMeters = results[0];

                if (distanceInMeters <= 50) {
                    // 距離在 50 公尺內，允許編輯
                    DiaryEntry entry = markerDataMap.get(marker.getId());
                    if (entry != null) {
                        diaryInput.setText(entry.text);
                        for (int i = 0; i < moodSpinner.getCount(); i++) {
                            if (moodSpinner.getItemAtPosition(i).toString().equals(entry.mood)) {
                                moodSpinner.setSelection(i);
                                break;
                            }
                        }
                        lastSelectedMarker = marker;
                        Toast.makeText(this, "距離 " + (int)distanceInMeters + "m，進入編輯模式", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    // 超過 50 公尺，拒絕編輯
                    Toast.makeText(this, "太遠了！距離此標記點約 " + (int)distanceInMeters + "m (限制50m)", Toast.LENGTH_LONG).show();
                    lastSelectedMarker = null;
                }
            }
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

                DiaryEntry entry = new DiaryEntry(currentPos, selectedMood, diaryText, currentTime);
                Marker marker = mMap.addMarker(new MarkerOptions()
                        .position(currentPos)
                        .title(selectedMood + " (" + currentTime + ")")
                        .snippet(diaryText));

                markerDataMap.put(marker.getId(), entry);
                mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(currentPos, 15));
                Toast.makeText(this, "紀錄成功！", Toast.LENGTH_SHORT).show();
                diaryInput.setText("");
            }
        });
    }
}