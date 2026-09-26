package com.example.topics;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.example.topics.data.model.LifeMapData;
import com.example.topics.data.model.LocationInsight;
import com.example.topics.data.repository.InsightRepository;
import com.example.topics.data.repository.RepositoryCallback;

public class InsightActivity extends AppCompatActivity {

    private InsightRepository insightRepository;
    private TextView tvStatus, tvSummary, tvMoodDescription, tvDominantMoodChip, tvIntensityChip;
    private ProgressBar progressIntensity;
    private LinearLayout layoutPreview, layoutLoading, layoutContent;
    private LinearLayout containerSuggestions, containerLocations, containerBehaviors;
    private Button btnRefresh, btnBackToMap;
    private boolean destroyed;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_insight);

        insightRepository = new InsightRepository(this);

        layoutPreview = findViewById(R.id.layout_insight_preview);
        layoutLoading = findViewById(R.id.layout_insight_loading);
        tvStatus = findViewById(R.id.tv_insight_status);
        layoutContent = findViewById(R.id.layout_insight_content);
        tvSummary = findViewById(R.id.tv_insight_summary);
        tvMoodDescription = findViewById(R.id.tv_mood_description);
        tvDominantMoodChip = findViewById(R.id.tv_dominant_mood_chip);
        tvIntensityChip = findViewById(R.id.tv_intensity_chip);
        progressIntensity = findViewById(R.id.progress_intensity);
        containerSuggestions = findViewById(R.id.container_suggestions);
        containerLocations = findViewById(R.id.container_locations);
        containerBehaviors = findViewById(R.id.container_behaviors);
        btnRefresh = findViewById(R.id.btn_refresh_insight);
        btnBackToMap = findViewById(R.id.btn_back_to_map);

        btnRefresh.setOnClickListener(v -> loadInsight());
        btnBackToMap.setOnClickListener(v -> {
            startActivity(new Intent(this, MapActivity.class));
            finish();
        });

        showInitialPreview();
    }

    private void showInitialPreview() {
        layoutPreview.setVisibility(View.VISIBLE);
        layoutLoading.setVisibility(View.GONE);
        tvStatus.setVisibility(View.GONE);
        layoutContent.setVisibility(View.GONE);
    }

    private void loadInsight() {
        layoutPreview.setVisibility(View.GONE);
        layoutLoading.setVisibility(View.VISIBLE);
        tvStatus.setVisibility(View.GONE);
        layoutContent.setVisibility(View.GONE);
        btnRefresh.setEnabled(false);

        insightRepository.getLifeMapInsight(new RepositoryCallback<LifeMapData>() {
            @Override
            public void onSuccess(LifeMapData data) {
                if (!isActive()) return;
                btnRefresh.setEnabled(true);
                btnRefresh.setText("✨ 重新產生洞察");
                layoutLoading.setVisibility(View.GONE);

                if (data == null) {
                    tvStatus.setVisibility(View.VISIBLE);
                    tvStatus.setText("目前沒有可顯示的分析結果");
                    layoutContent.setVisibility(View.GONE);
                    return;
                }

                if (data.notEnoughData) {
                    tvStatus.setVisibility(View.VISIBLE);
                    tvStatus.setText("日記數量不足（" + data.current + "/" + data.required + "），\n再多寫幾篇日記就能產生完整分析");
                    layoutContent.setVisibility(View.GONE);
                    return;
                }

                tvStatus.setVisibility(View.GONE);
                layoutContent.setVisibility(View.VISIBLE);
                bindInsight(data);
            }

            @Override
            public void onError(String message) {
                if (!isActive()) return;
                btnRefresh.setEnabled(true);
                btnRefresh.setText("✨ 重新產生洞察");
                layoutLoading.setVisibility(View.GONE);
                tvStatus.setVisibility(View.VISIBLE);
                tvStatus.setText("Adrift Intelligence 暫時無法使用：" + message);
                layoutContent.setVisibility(View.GONE);
            }
        });
    }

    private void bindInsight(LifeMapData data) {
        tvSummary.setText(data.summary);

        if (data.moodTrend != null) {
            tvMoodDescription.setText(data.moodTrend.description);
            tvDominantMoodChip.setText("主要心情：" + data.moodTrend.dominantMood);
            tvIntensityChip.setText("平均強度：" + data.moodTrend.averageIntensity + " / 5");
            int progress = (int) Math.round((data.moodTrend.averageIntensity / 5.0) * 100);
            progressIntensity.setProgress(Math.max(0, Math.min(100, progress)));
        }

        containerSuggestions.removeAllViews();
        if (data.suggestions != null) {
            for (String suggestion : data.suggestions) {
                View item = LayoutInflater.from(this).inflate(R.layout.item_insight_suggestion, containerSuggestions, false);
                TextView tv = item.findViewById(R.id.tv_suggestion_text);
                tv.setText(suggestion);
                containerSuggestions.addView(item);
            }
        }

        containerLocations.removeAllViews();
        if (data.locationInsights != null) {
            for (LocationInsight location : data.locationInsights) {
                View item = LayoutInflater.from(this).inflate(R.layout.item_insight_location, containerLocations, false);
                TextView tvPlace = item.findViewById(R.id.tv_location_place);
                TextView tvMood = item.findViewById(R.id.tv_location_mood);
                TextView tvInsight = item.findViewById(R.id.tv_location_insight);
                tvPlace.setText(location.place);
                tvMood.setText(location.dominantMood);
                tvInsight.setText(location.insight);
                containerLocations.addView(item);
            }
        }

        containerBehaviors.removeAllViews();
        if (data.behaviorPatterns != null) {
            for (String pattern : data.behaviorPatterns) {
                View item = LayoutInflater.from(this).inflate(R.layout.item_insight_behavior, containerBehaviors, false);
                TextView tv = item.findViewById(R.id.tv_behavior_text);
                tv.setText(pattern);
                containerBehaviors.addView(item);
            }
        }
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