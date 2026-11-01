package com.example.topics;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.example.topics.data.model.LifeMapData;
import com.example.topics.data.model.LocationInsight;
import com.example.topics.data.model.OverviewData;
import com.example.topics.data.repository.InsightRepository;
import com.example.topics.data.repository.RepositoryCallback;
import java.util.ArrayList;
import java.util.List;

public class InsightActivity extends AppCompatActivity {

    private static final int COLOR_ERROR = 0xFFFFAB6B;
    private static final int PREVIEW_COUNT = 3;

    private InsightRepository repository;
    private MemoryChatController chatController;
    private boolean destroyed;

    // 分頁
    private TextView tabAnalysis, tabChat;
    private View scrollInsight, panelChat;

    // 統計
    private TextView range7, range30, range90;
    private TextView tvOverviewStatus, tvOverviewSummary, tvOverviewError, btnOverviewRetry;
    private ImageButton btnOverviewRefresh;
    private View layoutOverviewError, layoutOverviewData, layoutOverviewBody, layoutOverviewEmpty;
    private TextView tvMetricCount, tvMetricMoodDot, tvMetricMood, tvMetricPlaces, tvMoodTotal, tvPlacesEmpty;
    private LinearLayout containerMoodBars, containerPlaces;
    private TrendChartView trendChart;

    // AI 回顧
    private Button btnRefresh;
    private TextView tvReviewNotice, tvReviewEmptyText;
    private View layoutReviewEmpty, layoutReviewResult;
    private TextView tvInsightSummary, tvGeneratedAt, tvMoodDescription;
    private LinearLayout containerBehaviors, containerLocations, containerSuggestions;
    private TextView tvBehaviorsMore, tvLocationsMore, tvLocationsEmpty, tvSuggestionsMore;

    // 狀態
    private int days = 30;
    private int overviewRequest;
    private boolean overviewLoading;
    private OverviewData overview;

    private boolean reviewLoading;
    private String reviewStatus = "idle";
    private String reviewError = "";
    private int warmupCurrent;
    private int warmupRequired = 3;
    private LifeMapData insight;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_insight);

        repository = new InsightRepository(this);
        bindViews();

        ((ImageButton) findViewById(R.id.btn_insight_back)).setOnClickListener(v -> goMap());
        findViewById(R.id.btn_overview_new_diary).setOnClickListener(v -> goMap());
        findViewById(R.id.btn_review_new_diary).setOnClickListener(v -> goMap());

        range7.setOnClickListener(v -> selectRange(7));
        range30.setOnClickListener(v -> selectRange(30));
        range90.setOnClickListener(v -> selectRange(90));
        btnOverviewRefresh.setOnClickListener(v -> loadOverview());
        btnOverviewRetry.setOnClickListener(v -> loadOverview());
        btnRefresh.setOnClickListener(v -> generateInsight());

        findViewById(R.id.btn_trend_ask).setOnClickListener(v -> {
            int range = overview != null ? overview.days : days;
            askChat("幫我回顧最近 " + range + " 天的心情變化");
        });
        findViewById(R.id.btn_review_ask).setOnClickListener(v -> askChat("幫我深入回顧最近一個月的心情變化"));

        chatController = new MemoryChatController(this);
        tabAnalysis.setOnClickListener(v -> selectInsightTab(false));
        tabChat.setOnClickListener(v -> selectInsightTab(true));
        selectInsightTab(false);

        updateRangeStyle();
        renderOverviewStatus();
        renderReview();
        loadOverview();
    }

    private void bindViews() {
        tabAnalysis = findViewById(R.id.tab_insight_analysis);
        tabChat = findViewById(R.id.tab_insight_chat);
        scrollInsight = findViewById(R.id.scroll_insight);
        panelChat = findViewById(R.id.panel_chat);

        range7 = findViewById(R.id.tv_range_7);
        range30 = findViewById(R.id.tv_range_30);
        range90 = findViewById(R.id.tv_range_90);
        btnOverviewRefresh = findViewById(R.id.btn_overview_refresh);
        tvOverviewStatus = findViewById(R.id.tv_overview_status);
        tvOverviewSummary = findViewById(R.id.tv_overview_summary);
        layoutOverviewError = findViewById(R.id.layout_overview_error);
        tvOverviewError = findViewById(R.id.tv_overview_error);
        btnOverviewRetry = findViewById(R.id.btn_overview_retry);
        layoutOverviewData = findViewById(R.id.layout_overview_data);
        layoutOverviewBody = findViewById(R.id.layout_overview_body);
        layoutOverviewEmpty = findViewById(R.id.layout_overview_empty);
        tvMetricCount = findViewById(R.id.tv_metric_count);
        tvMetricMoodDot = findViewById(R.id.tv_metric_mood_dot);
        tvMetricMood = findViewById(R.id.tv_metric_mood);
        tvMetricPlaces = findViewById(R.id.tv_metric_places);
        tvMoodTotal = findViewById(R.id.tv_mood_total);
        containerMoodBars = findViewById(R.id.container_mood_bars);
        trendChart = findViewById(R.id.trend_chart);
        containerPlaces = findViewById(R.id.container_places);
        tvPlacesEmpty = findViewById(R.id.tv_places_empty);

        btnRefresh = findViewById(R.id.btn_refresh_insight);
        tvReviewNotice = findViewById(R.id.tv_review_notice);
        layoutReviewEmpty = findViewById(R.id.layout_review_empty);
        tvReviewEmptyText = findViewById(R.id.tv_review_empty_text);
        layoutReviewResult = findViewById(R.id.layout_review_result);
        tvInsightSummary = findViewById(R.id.tv_insight_summary);
        tvGeneratedAt = findViewById(R.id.tv_insight_generated_at);
        tvMoodDescription = findViewById(R.id.tv_mood_description);
        containerBehaviors = findViewById(R.id.container_behaviors);
        tvBehaviorsMore = findViewById(R.id.tv_behaviors_more);
        containerLocations = findViewById(R.id.container_locations);
        tvLocationsMore = findViewById(R.id.tv_locations_more);
        tvLocationsEmpty = findViewById(R.id.tv_locations_empty);
        containerSuggestions = findViewById(R.id.container_suggestions);
        tvSuggestionsMore = findViewById(R.id.tv_suggestions_more);
    }

    // ---------- 分頁 ----------
    private void selectInsightTab(boolean chat) {
        scrollInsight.setVisibility(chat ? View.GONE : View.VISIBLE);
        panelChat.setVisibility(chat ? View.VISIBLE : View.GONE);
        updateTabStyle(tabAnalysis, !chat);
        updateTabStyle(tabChat, chat);
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

    private void askChat(String question) {
        chatController.setDraft(question);
        selectInsightTab(true);
    }

    private void goMap() {
        startActivity(new Intent(this, MapActivity.class));
        finish();
    }

    private void openDiaryOnMap(String diaryId) {
        if (diaryId == null || diaryId.isEmpty()) return;
        Intent intent = new Intent(this, MapActivity.class);
        intent.putExtra("open_diary_id", diaryId);
        startActivity(intent);
        finish();
    }

    // ---------- 統計 ----------
    private void selectRange(int value) {
        if (days == value) return;
        days = value;
        updateRangeStyle();
        loadOverview();
    }

    private void updateRangeStyle() {
        styleRange(range7, days == 7);
        styleRange(range30, days == 30);
        styleRange(range90, days == 90);
    }

    private void styleRange(TextView view, boolean selected) {
        if (selected) {
            view.setBackgroundResource(R.drawable.bg_login_card);
            view.setTextColor(getResources().getColor(R.color.white));
        } else {
            view.setBackground(null);
            view.setTextColor(getResources().getColor(R.color.hint_text));
        }
    }

    private void loadOverview() {
        final int request = ++overviewRequest;
        final int requested = days;
        overviewLoading = true;
        layoutOverviewError.setVisibility(View.GONE);
        btnOverviewRefresh.setEnabled(false);
        renderOverviewStatus();

        repository.getOverview(requested, new RepositoryCallback<OverviewData>() {
            @Override
            public void onSuccess(OverviewData data) {
                if (!isActive() || request != overviewRequest) return;
                if (data == null || data.days != requested || data.daily == null) {
                    showOverviewError("統計資料不完整，請重新載入。");
                    return;
                }
                overview = data;
                overviewLoading = false;
                btnOverviewRefresh.setEnabled(true);
                bindOverview();
            }

            @Override
            public void onError(String message) {
                if (!isActive() || request != overviewRequest) return;
                showOverviewError(message == null || message.isEmpty() ? "記錄統計暫時無法載入。" : message);
            }
        });
    }

    private void showOverviewError(String message) {
        overviewLoading = false;
        btnOverviewRefresh.setEnabled(true);
        layoutOverviewError.setVisibility(View.VISIBLE);
        tvOverviewError.setText(message);
        renderOverviewStatus();
    }

    private void renderOverviewStatus() {
        String text;
        if (overviewLoading) {
            text = overview != null
                    ? "正在讀取最近 " + days + " 天，暫時保留最近 " + overview.days + " 天的統計。"
                    : "正在整理你的日記統計…";
        } else if (overview != null) {
            text = InsightFormat.shortDate(overview.start) + " — " + InsightFormat.shortDate(overview.end) + " · 只計算你實際留下的日記";
        } else {
            text = "以真實記錄為依據，不推測沒有留下的生活。";
        }
        tvOverviewStatus.setText(text);
    }

    private void bindOverview() {
        renderOverviewStatus();
        layoutOverviewError.setVisibility(View.GONE);
        layoutOverviewData.setVisibility(View.VISIBLE);

        List<OverviewData.OverviewMood> moods = overview.moods != null ? overview.moods : new ArrayList<>();
        List<OverviewData.OverviewPlace> places = overview.places != null ? overview.places : new ArrayList<>();
        OverviewData.OverviewMood top = moods.isEmpty() ? null : moods.get(0);

        if (overview.count > 0) {
            String summary = "這段日子，你留下了 " + overview.count + " 篇記憶";
            if (top != null) summary += "，最常記錄的是" + InsightFormat.moodLabel(top.type);
            tvOverviewSummary.setText(summary + "。");
            tvOverviewSummary.setVisibility(View.VISIBLE);
        } else {
            tvOverviewSummary.setVisibility(View.GONE);
        }

        tvMetricCount.setText(overview.count + " 篇");
        tvMetricPlaces.setText(overview.placeCount + " 處");
        if (top != null) {
            tvMetricMoodDot.setVisibility(View.VISIBLE);
            tvMetricMoodDot.setTextColor(InsightFormat.moodColor(top.type));
            tvMetricMood.setText(InsightFormat.moodLabel(top.type));
        } else {
            tvMetricMoodDot.setVisibility(View.GONE);
            tvMetricMood.setText("—");
        }

        boolean hasRecords = overview.count > 0;
        layoutOverviewBody.setVisibility(hasRecords ? View.VISIBLE : View.GONE);
        layoutOverviewEmpty.setVisibility(hasRecords ? View.GONE : View.VISIBLE);
        if (!hasRecords) return;

        // 情緒分布
        tvMoodTotal.setText(overview.count + " 篇日記");
        containerMoodBars.removeAllViews();
        for (OverviewData.OverviewMood mood : moods) {
            View row = LayoutInflater.from(this).inflate(R.layout.item_intel_mood_bar, containerMoodBars, false);
            float percent = mood.count * 100f / overview.count;
            TextView dot = row.findViewById(R.id.tv_bar_dot);
            dot.setTextColor(InsightFormat.moodColor(mood.type));
            ((TextView) row.findViewById(R.id.tv_bar_label)).setText(InsightFormat.moodLabel(mood.type));
            View fill = row.findViewById(R.id.bar_fill);
            View rest = row.findViewById(R.id.bar_rest);
            fill.setBackgroundColor(InsightFormat.moodColor(mood.type));
            ((LinearLayout.LayoutParams) fill.getLayoutParams()).weight = percent;
            ((LinearLayout.LayoutParams) rest.getLayoutParams()).weight = Math.max(0f, 100f - percent);
            ((TextView) row.findViewById(R.id.tv_bar_value)).setText(mood.count + " · " + Math.round(percent) + "%");
            containerMoodBars.addView(row);
        }

        // 趨勢
        trendChart.setBins(InsightFormat.trendBins(overview));

        // 常去地點
        containerPlaces.removeAllViews();
        for (int i = 0; i < places.size(); i++) {
            OverviewData.OverviewPlace place = places.get(i);
            View row = LayoutInflater.from(this).inflate(R.layout.item_intel_place, containerPlaces, false);
            ((TextView) row.findViewById(R.id.tv_place_rank)).setText(String.format("%02d", i + 1));
            ((TextView) row.findViewById(R.id.tv_place_name)).setText(place.name == null ? "" : place.name);
            ((TextView) row.findViewById(R.id.tv_place_meta)).setText("最近記錄 · " + InsightFormat.moodLabel(place.mood));
            ((TextView) row.findViewById(R.id.tv_place_count)).setText(place.count + " 篇");
            row.setOnClickListener(v -> openDiaryOnMap(place.diaryId));
            containerPlaces.addView(row);
        }
        tvPlacesEmpty.setVisibility(places.isEmpty() ? View.VISIBLE : View.GONE);
    }

    // ---------- AI 回顧 ----------
    private void generateInsight() {
        if (reviewLoading) return;
        reviewLoading = true;
        reviewStatus = "idle";
        renderReview();

        repository.getLifeMapInsight(new RepositoryCallback<LifeMapData>() {
            @Override
            public void onSuccess(LifeMapData data) {
                if (!isActive()) return;
                reviewLoading = false;

                if (data == null) {
                    reviewStatus = "error";
                    reviewError = "洞察內容不完整，請再試一次。";
                } else if (data.notEnoughData) {
                    reviewStatus = "notEnough";
                    warmupCurrent = data.current;
                    warmupRequired = data.required > 0 ? data.required : 3;
                } else if (data.summary == null || data.summary.trim().isEmpty()) {
                    reviewStatus = "error";
                    reviewError = "洞察內容不完整，請再試一次。";
                } else {
                    insight = data;
                    reviewStatus = "success";
                    tvGeneratedAt.setText("更新於 " + InsightFormat.shortDateFromMillis(System.currentTimeMillis()));
                    bindReview();
                }
                renderReview();
            }

            @Override
            public void onError(String message) {
                if (!isActive()) return;
                reviewLoading = false;
                reviewStatus = "error";
                reviewError = message == null || message.isEmpty() ? "暫時無法整理洞察，請稍後重試。" : message;
                renderReview();
            }
        });
    }

    private void renderReview() {
        String buttonText = reviewLoading ? "正在整理洞察"
                : "error".equals(reviewStatus) ? "重新嘗試"
                : insight != null ? "✨ 更新 AI 回顧" : "✨ 產生智慧洞察";
        btnRefresh.setText(buttonText);
        btnRefresh.setEnabled(!reviewLoading);

        String notice = "";
        int color = getResources().getColor(R.color.hint_text);
        if (reviewLoading) {
            notice = insight != null ? "正在更新洞察，仍可閱讀上次的結果。" : "正在根據你的日記整理洞察…";
        } else if ("error".equals(reviewStatus)) {
            notice = reviewError + "\n" + (insight != null ? "已保留上次的結果與記錄統計。" : "記錄統計不受影響，也可以切換至記憶對話。");
            color = COLOR_ERROR;
        }
        tvReviewNotice.setText(notice);
        tvReviewNotice.setTextColor(color);
        tvReviewNotice.setVisibility(notice.isEmpty() ? View.GONE : View.VISIBLE);

        boolean notEnough = "notEnough".equals(reviewStatus);
        layoutReviewEmpty.setVisibility(notEnough ? View.VISIBLE : View.GONE);
        if (notEnough) {
            tvReviewEmptyText.setText("已有 " + warmupCurrent + " 篇；至少 " + warmupRequired + " 篇就能串起生活片段。");
        }
        layoutReviewResult.setVisibility(insight != null ? View.VISIBLE : View.GONE);
    }

    private void bindReview() {
        tvInsightSummary.setText(insight.summary);

        String description = insight.moodTrend != null && insight.moodTrend.description != null && !insight.moodTrend.description.trim().isEmpty()
                ? insight.moodTrend.description : "持續記錄後，才能看見更清楚的生活節奏。";
        tvMoodDescription.setText(description);

        fillList(containerBehaviors, tvBehaviorsMore, cleanList(insight.behaviorPatterns), "其他 %d 項");
        fillList(containerSuggestions, tvSuggestionsMore, cleanList(insight.suggestions), "其他 %d 項");

        containerLocations.removeAllViews();
        tvLocationsMore.setVisibility(View.GONE);
        final List<LocationInsight> locations = new ArrayList<>();
        if (insight.locationInsights != null) locations.addAll(insight.locationInsights);
        tvLocationsEmpty.setVisibility(locations.isEmpty() ? View.VISIBLE : View.GONE);
        for (int i = 0; i < locations.size() && i < PREVIEW_COUNT; i++) addPlaceInsight(locations.get(i));
        if (locations.size() > PREVIEW_COUNT) {
            tvLocationsMore.setText("其他 " + (locations.size() - PREVIEW_COUNT) + " 個地點觀察");
            tvLocationsMore.setVisibility(View.VISIBLE);
            tvLocationsMore.setOnClickListener(v -> {
                for (int i = PREVIEW_COUNT; i < locations.size(); i++) addPlaceInsight(locations.get(i));
                tvLocationsMore.setVisibility(View.GONE);
            });
        }
    }

    private List<String> cleanList(List<String> items) {
        List<String> result = new ArrayList<>();
        if (items == null) return result;
        for (String item : items) {
            if (item != null && !item.trim().isEmpty()) result.add(item);
        }
        return result;
    }

    private void fillList(LinearLayout container, TextView more, final List<String> items, String moreFormat) {
        container.removeAllViews();
        more.setVisibility(View.GONE);
        for (int i = 0; i < items.size() && i < PREVIEW_COUNT; i++) addBullet(container, items.get(i));
        if (items.size() > PREVIEW_COUNT) {
            more.setText(String.format(moreFormat, items.size() - PREVIEW_COUNT));
            more.setVisibility(View.VISIBLE);
            more.setOnClickListener(v -> {
                for (int i = PREVIEW_COUNT; i < items.size(); i++) addBullet(container, items.get(i));
                more.setVisibility(View.GONE);
            });
        }
    }

    private void addBullet(LinearLayout container, String text) {
        View item = LayoutInflater.from(this).inflate(R.layout.item_insight_behavior, container, false);
        ((TextView) item.findViewById(R.id.tv_behavior_text)).setText(text);
        container.addView(item);
    }

    private void addPlaceInsight(LocationInsight location) {
        View item = LayoutInflater.from(this).inflate(R.layout.item_intel_place_insight, containerLocations, false);
        String place = location.place == null || location.place.isEmpty() ? "未命名地點" : location.place;
        ((TextView) item.findViewById(R.id.tv_pi_name)).setText(place);
        ((TextView) item.findViewById(R.id.tv_pi_text)).setText(location.insight == null ? "" : location.insight);
        item.findViewById(R.id.tv_pi_ask).setOnClickListener(v -> askChat("我在" + place + "留下過哪些記憶？"));
        containerLocations.addView(item);
    }

    private boolean isActive() {
        return !destroyed && !isFinishing() && !isDestroyed();
    }

    @Override
    protected void onDestroy() {
        destroyed = true;
        if (chatController != null) chatController.destroy();
        super.onDestroy();
    }
}