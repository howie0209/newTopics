package com.example.topics;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.location.Location;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.core.graphics.PathParser;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.Projection;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class DiaryMarkerLayer {
    private static final float GEO_GROUP_METERS = 20f;
    private static final float SCREEN_GROUP_DP = 56f;

    private static final int BRAND = 0xFF91D9D6;
    private static final int SURFACE = 0xFF172E36;
    private static final int SURFACE_TOP = 0xFF25414A;
    private static final int TEXT = 0xFFF1FAF9;
    private static final int MUTED = 0xFFB7CACA;
    private static final int BORDER = 0xFF537B82;
    private static final int POPUP = 0xFF193038;

    private static final String SPARKLE_PATH =
            "M9.937 15.5A2 2 0 0 0 8.5 14.063l-6.135-1.582a.5.5 0 0 1 0-.962L8.5 9.936A2 2 0 0 0 9.937 8.5l1.582-6.135a.5.5 0 0 1 .963 0L14.063 8.5A2 2 0 0 0 15.5 9.937l6.135 1.581a.5.5 0 0 1 0 .964L15.5 14.063a2 2 0 0 0-1.437 1.437l-1.582 6.135a.5.5 0 0 1-.963 0z"
                    + " M20 3v4 M22 5h-4 M4 17v2 M5 18H3";

    public static class Item {
        public String id;
        public String title;
        public String moodLabel;
        public String time;
        public double lat;
        public double lng;
        public int moodColor = 0xFF7C909C;
    }

    public interface Listener {
        void onDiarySelected(String diaryId);
    }

    private static class GeoGroup {
        String key;
        LatLng center;
        final List<Item> items = new ArrayList<>();
    }

    private static class Cluster {
        String key;
        LatLng center;
        final List<Item> items = new ArrayList<>();

        boolean isStack() {
            return items.size() > 1;
        }

        boolean contains(String id) {
            for (Item item : items) if (item.id.equals(id)) return true;
            return false;
        }
    }

    private final Activity activity;
    private final GoogleMap map;
    private final View mapView;
    private final Listener listener;
    private final float density;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable refreshRunnable = this::refresh;
    private final IconFactory icons;
    private final List<View> obstacleViews = new ArrayList<>();

    private List<Item> items = new ArrayList<>();
    private List<Cluster> clusters = new ArrayList<>();
    private final Map<String, Cluster> clusterByKey = new HashMap<>();
    private final Map<String, Marker> clusterMarkers = new HashMap<>();
    private final Map<String, String> iconKeys = new HashMap<>();
    private final List<Marker> fanMarkers = new ArrayList<>();
    private final List<Polyline> connectors = new ArrayList<>();
    private Cluster expanded;
    private String selectedId;
    private boolean destroyed;

    public DiaryMarkerLayer(Activity activity, GoogleMap map, View mapView, Listener listener) {
        this.activity = activity;
        this.map = map;
        this.mapView = mapView != null ? mapView : activity.getWindow().getDecorView();
        this.listener = listener;
        this.density = activity.getResources().getDisplayMetrics().density;
        this.icons = new IconFactory();
        map.setOnCameraIdleListener(this::requestRefresh);
    }

    // ---------- 對外 ----------
    public void setObstacleViews(View... views) {
        obstacleViews.clear();
        for (View view : views) if (view != null) obstacleViews.add(view);
    }

    public void setItems(List<Item> list) {
        items = new ArrayList<>(list);
        requestRefresh();
    }

    public void setSelected(String diaryId) {
        selectedId = diaryId;
        if (!clusters.isEmpty()) render();
    }

    public boolean onMarkerClick(Marker marker) {
        Object tag = marker.getTag();
        if (!(tag instanceof String)) return false;
        String value = (String) tag;

        if (value.startsWith("fan:")) {
            String id = value.substring(4);
            closeExpansion();
            if (listener != null) listener.onDiarySelected(id);
            return true;
        }

        Cluster cluster = clusterByKey.get(value);
        if (cluster == null) return true;
        if (cluster.isStack()) {
            if (expanded != null && expanded.key.equals(cluster.key)) closeExpansion();
            else expand(cluster);
        } else {
            closeExpansion();
            if (listener != null) listener.onDiarySelected(cluster.items.get(0).id);
        }
        return true;
    }

    public void onCameraMoveStarted(int reason) {
        if (reason == GoogleMap.OnCameraMoveStartedListener.REASON_GESTURE) closeExpansion();
    }

    public void closeExpansion() {
        for (Marker marker : fanMarkers) marker.remove();
        for (Polyline line : connectors) line.remove();
        fanMarkers.clear();
        connectors.clear();
        expanded = null;
    }

    // 呼叫 map.clear() 之後使用：地圖已經移除所有標記，這裡只需要忘記它們
    public void reset() {
        clusterMarkers.clear();
        iconKeys.clear();
        clusterByKey.clear();
        fanMarkers.clear();
        connectors.clear();
        clusters = new ArrayList<>();
        expanded = null;
    }

    public void destroy() {
        destroyed = true;
        handler.removeCallbacksAndMessages(null);
    }

    // ---------- 分組 ----------
    private void requestRefresh() {
        handler.removeCallbacks(refreshRunnable);
        handler.post(refreshRunnable);
    }

    private void refresh() {
        if (destroyed) return;
        clusters = groupOnScreen(groupGeographically());
        render();
        if (expanded != null && !clusterByKey.containsKey(expanded.key)) closeExpansion();
    }

    private static int find(int[] parent, int index) {
        while (parent[index] != index) {
            parent[index] = parent[parent[index]];
            index = parent[index];
        }
        return index;
    }

    // 距離 20 公尺內、可以一路連起來的日記算同一疊
    private List<GeoGroup> groupGeographically() {
        int n = items.size();
        int[] parent = new int[n];
        for (int i = 0; i < n; i++) parent[i] = i;
        float[] result = new float[1];
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                Item a = items.get(i), b = items.get(j);
                if (Math.abs(a.lat - b.lat) > 0.001) continue;
                Location.distanceBetween(a.lat, a.lng, b.lat, b.lng, result);
                if (result[0] <= GEO_GROUP_METERS) parent[find(parent, i)] = find(parent, j);
            }
        }

        Map<Integer, GeoGroup> byRoot = new HashMap<>();
        for (int i = 0; i < n; i++) {
            int root = find(parent, i);
            GeoGroup group = byRoot.get(root);
            if (group == null) {
                group = new GeoGroup();
                byRoot.put(root, group);
            }
            group.items.add(items.get(i));
        }

        List<GeoGroup> groups = new ArrayList<>(byRoot.values());
        for (GeoGroup group : groups) {
            Item anchor = group.items.get(0);
            for (Item item : group.items) if (item.id.compareTo(anchor.id) < 0) anchor = item;
            group.key = anchor.id;
            group.center = new LatLng(anchor.lat, anchor.lng);
        }
        Collections.sort(groups, (a, b) -> a.key.compareTo(b.key));
        return groups;
    }

    private static String text(String value) {
        return value == null ? "" : value;
    }

    // 螢幕上相距不到 56dp 的疊會再合併成一個群組
    private List<Cluster> groupOnScreen(List<GeoGroup> groups) {
        int n = groups.size();
        List<Cluster> result = new ArrayList<>();
        if (n == 0) return result;

        Projection projection = map.getProjection();
        Point[] points = new Point[n];
        for (int i = 0; i < n; i++) points[i] = projection.toScreenLocation(groups.get(i).center);

        float threshold = SCREEN_GROUP_DP * density;
        int[] parent = new int[n];
        for (int i = 0; i < n; i++) parent[i] = i;
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                double distance = Math.hypot(points[i].x - points[j].x, points[i].y - points[j].y);
                if (distance < threshold) parent[find(parent, i)] = find(parent, j);
            }
        }

        Map<Integer, List<GeoGroup>> byRoot = new HashMap<>();
        for (int i = 0; i < n; i++) {
            int root = find(parent, i);
            List<GeoGroup> members = byRoot.get(root);
            if (members == null) {
                members = new ArrayList<>();
                byRoot.put(root, members);
            }
            members.add(groups.get(i));
        }

        for (List<GeoGroup> members : byRoot.values()) {
            GeoGroup anchor = members.get(0);
            Cluster cluster = new Cluster();
            cluster.key = anchor.key;
            cluster.center = anchor.center;
            for (GeoGroup group : members) cluster.items.addAll(group.items);
            Collections.sort(cluster.items, (a, b) -> {
                int c = text(b.time).compareTo(text(a.time));
                return c != 0 ? c : a.id.compareTo(b.id);
            });
            result.add(cluster);
        }
        Collections.sort(result, (a, b) -> a.key.compareTo(b.key));
        return result;
    }

    // ---------- 畫標記 ----------
    private void render() {
        clusterByKey.clear();
        for (Cluster cluster : clusters) clusterByKey.put(cluster.key, cluster);

        for (Cluster cluster : clusters) {
            boolean selected = selectedId != null && cluster.contains(selectedId);
            boolean dimmed = selectedId != null && !selected;
            Item top = cluster.items.get(0);
            String iconKey = Math.min(cluster.items.size(), 100) + "|" + top.moodColor + "|" + selected;

            Marker marker = clusterMarkers.get(cluster.key);
            if (marker == null) {
                marker = map.addMarker(new MarkerOptions()
                        .position(cluster.center)
                        .anchor(0.5f, 0.5f)
                        .icon(icons.get(cluster.items.size(), top.moodColor, selected)));
                if (marker == null) continue;
                marker.setTag(cluster.key);
                clusterMarkers.put(cluster.key, marker);
                iconKeys.put(cluster.key, iconKey);
            } else {
                if (!marker.getPosition().equals(cluster.center)) marker.setPosition(cluster.center);
                if (!iconKey.equals(iconKeys.get(cluster.key))) {
                    marker.setIcon(icons.get(cluster.items.size(), top.moodColor, selected));
                    iconKeys.put(cluster.key, iconKey);
                }
            }
            marker.setZIndex(selected ? 3f : 1f);
            marker.setAlpha(dimmed ? 0.72f : 1f);
        }

        Iterator<Map.Entry<String, Marker>> iterator = clusterMarkers.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Marker> entry = iterator.next();
            if (!clusterByKey.containsKey(entry.getKey())) {
                entry.getValue().remove();
                iconKeys.remove(entry.getKey());
                iterator.remove();
            }
        }
    }

    // ---------- 展開 ----------
    private Rect mapRect() {
        Rect rect = new Rect();
        mapView.getGlobalVisibleRect(rect);
        return rect;
    }

    private List<RectF> obstacleRects(Rect mapRect) {
        List<RectF> list = new ArrayList<>();
        Rect rect = new Rect();
        float pad = 12f * density;
        for (View view : obstacleViews) {
            if (view.getVisibility() != View.VISIBLE) continue;
            if (!view.getGlobalVisibleRect(rect)) continue;
            list.add(new RectF(rect.left - mapRect.left - pad, rect.top - mapRect.top - pad,
                    rect.right - mapRect.left + pad, rect.bottom - mapRect.top + pad));
        }
        return list;
    }

    private void expand(Cluster cluster) {
        closeExpansion();
        Rect mapRect = mapRect();
        float pad = 12f * density;
        RectF bounds = new RectF(pad, pad, mapRect.width() - pad, mapRect.height() - pad);
        Projection projection = map.getProjection();
        Point center = projection.toScreenLocation(cluster.center);

        float[][] layout = arcLayout(cluster.items.size(), center, bounds, obstacleRects(mapRect));
        if (layout == null) {
            showStackList(cluster);
            return;
        }

        expanded = cluster;
        for (int i = 0; i < cluster.items.size(); i++) {
            Item item = cluster.items.get(i);
            LatLng position = projection.fromScreenLocation(
                    new Point(Math.round(center.x + layout[i][0]), Math.round(center.y + layout[i][1])));

            connectors.add(map.addPolyline(new PolylineOptions()
                    .add(cluster.center, position)
                    .color(0x6691D9D6)
                    .width(density)
                    .zIndex(2f)));

            Marker marker = map.addMarker(new MarkerOptions()
                    .position(position)
                    .anchor(0.5f, 0.5f)
                    .zIndex(5f)
                    .icon(icons.get(1, item.moodColor, item.id.equals(selectedId))));
            if (marker != null) {
                marker.setTag("fan:" + item.id);
                fanMarkers.add(marker);
            }
        }
    }

    // 2～6 篇、放得下才用扇形；方向依序嘗試，都放不下就改用清單
    private float[][] arcLayout(int count, Point center, RectF bounds, List<RectF> obstacles) {
        if (count < 2 || count > 6) return null;
        double angle = Math.atan2(bounds.centerY() - center.y, bounds.centerX() - center.x);
        double span = count <= 3 ? Math.PI * 2 / 3 : Math.PI;
        double radius = Math.max(76 * density, Math.ceil(60 * density / (2 * Math.sin(span / (count - 1) / 2))));
        double[] directions = {angle, 0, Math.PI, -Math.PI / 2, Math.PI / 2,
                -Math.PI / 4, Math.PI / 4, Math.PI * 3 / 4, -Math.PI * 3 / 4};
        float half = 26f * density;

        for (double direction : directions) {
            float[][] result = new float[count][2];
            boolean fits = true;
            for (int i = 0; i < count && fits; i++) {
                double theta = direction - span / 2 + i * span / (count - 1);
                float x = (float) (Math.cos(theta) * radius);
                float y = (float) (Math.sin(theta) * radius);
                result[i][0] = x;
                result[i][1] = y;
                RectF box = new RectF(center.x + x - half, center.y + y - half, center.x + x + half, center.y + y + half);
                if (!bounds.contains(box)) {
                    fits = false;
                    break;
                }
                for (RectF obstacle : obstacles) {
                    if (RectF.intersects(box, obstacle)) {
                        fits = false;
                        break;
                    }
                }
            }
            if (fits) return result;
        }
        return null;
    }

    private void showStackList(Cluster cluster) {
        BottomSheetDialog dialog = new BottomSheetDialog(activity);

        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        float radius = 16f * density;
        GradientDrawable background = new GradientDrawable();
        background.setColor(POPUP);
        background.setStroke(Math.max(1, Math.round(density)), BORDER);
        background.setCornerRadii(new float[]{radius, radius, radius, radius, 0, 0, 0, 0});
        root.setBackground(background);

        int pad = Math.round(16 * density);
        TextView header = new TextView(activity);
        header.setText(cluster.items.size() + " 篇記憶");
        header.setTextColor(TEXT);
        header.setTextSize(15);
        header.setTypeface(Typeface.DEFAULT_BOLD);
        header.setPadding(pad, pad, pad, Math.round(8 * density));
        root.addView(header);

        LinearLayout rows = new LinearLayout(activity);
        rows.setOrientation(LinearLayout.VERTICAL);
        for (Item item : cluster.items) {
            LinearLayout row = new LinearLayout(activity);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(pad, Math.round(10 * density), pad, Math.round(10 * density));

            TextView title = new TextView(activity);
            boolean selected = item.id.equals(selectedId);
            String titleText = item.title == null || item.title.isEmpty() ? "未命名日記" : item.title;
            title.setText(selected ? titleText + "  ✓" : titleText);
            title.setTextColor(TEXT);
            title.setTextSize(14);
            title.setTypeface(Typeface.DEFAULT_BOLD);
            row.addView(title);

            String date = DiaryTime.format(item.time);
            TextView meta = new TextView(activity);
            meta.setText(text(item.moodLabel) + (date.isEmpty() ? "" : " · " + date));
            meta.setTextColor(MUTED);
            meta.setTextSize(12);
            row.addView(meta);

            row.setOnClickListener(v -> {
                dialog.dismiss();
                if (listener != null) listener.onDiarySelected(item.id);
            });
            rows.addView(row);
        }

        ScrollView scroll = new ScrollView(activity);
        scroll.setNestedScrollingEnabled(true);
        scroll.addView(rows);
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        dialog.setContentView(root);
        dialog.show();
        View sheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
        if (sheet != null) sheet.setBackgroundColor(Color.TRANSPARENT);
    }

    // ---------- 圖示 ----------
    private class IconFactory {
        private final Map<String, BitmapDescriptor> cache = new HashMap<>();
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path sparkle;

        IconFactory() {
            Path path = null;
            try {
                path = PathParser.createPathFromPathData(SPARKLE_PATH);
            } catch (RuntimeException ignored) {
            }
            if (path == null) {
                path = new Path();
                path.moveTo(12, 2);
                path.quadTo(12, 12, 22, 12);
                path.quadTo(12, 12, 12, 22);
                path.quadTo(12, 12, 2, 12);
                path.quadTo(12, 12, 12, 2);
                path.close();
            }
            sparkle = path;
        }

        BitmapDescriptor get(int count, int moodColor, boolean selected) {
            String key = Math.min(count, 100) + "|" + moodColor + "|" + selected;
            BitmapDescriptor descriptor = cache.get(key);
            if (descriptor == null) {
                descriptor = BitmapDescriptorFactory.fromBitmap(render(count, moodColor, selected));
                cache.put(key, descriptor);
            }
            return descriptor;
        }

        private Path shape(float left, float top, float size) {
            Path path = new Path();
            path.addRoundRect(new RectF(left, top, left + size, top + size),
                    new float[]{8, 8, 8, 8, 8, 8, 3, 3}, Path.Direction.CW);
            return path;
        }

        private void drawLayer(Canvas canvas, float left, float top, float size, int alpha) {
            Path path = shape(left, top, size);
            paint.reset();
            paint.setAntiAlias(true);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(SURFACE);
            paint.setAlpha(alpha);
            canvas.drawPath(path, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(1f);
            paint.setColor(BORDER);
            paint.setAlpha(alpha);
            canvas.drawPath(path, paint);
        }

        private Bitmap render(int count, int moodColor, boolean selected) {
            int px = Math.round(48 * density);
            Bitmap bitmap = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            canvas.scale(density, density);
            boolean stack = count > 1;
            float cx = 24f, cy = 24f;

            // 光暈
            paint.reset();
            paint.setAntiAlias(true);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(BRAND);
            paint.setAlpha(23);
            canvas.drawCircle(cx, cy, 21f, paint);

            // 選中的外框
            if (selected) {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(1f);
                paint.setColor(BRAND);
                paint.setAlpha(217);
                canvas.drawCircle(cx, cy, 22f, paint);
            }

            // 疊在後面的兩張卡片
            if (stack) {
                drawLayer(canvas, cx - 14f + 5f, cy - 14f + 5f, 28f, 166);
                drawLayer(canvas, cx - 14f + 2f, cy - 14f + 2f, 28f, 255);
            }

            float core = stack ? 32f : 28f;
            float left = cx - core / 2f, top = cy - core / 2f;
            Path shape = shape(left, top, core);

            canvas.save();
            if (selected) canvas.scale(1.08f, 1.08f, cx, cy);

            // 底色與陰影
            paint.reset();
            paint.setAntiAlias(true);
            paint.setStyle(Paint.Style.FILL);
            paint.setShader(new LinearGradient(left, top, left + core, top + core, SURFACE_TOP, SURFACE, Shader.TileMode.CLAMP));
            paint.setShadowLayer(3.5f * density, 0f, 3f * density, 0x4D000000);
            canvas.drawPath(shape, paint);

            // 上半部的玻璃反光與底下的心情色條
            canvas.save();
            canvas.clipPath(shape);
            paint.reset();
            paint.setAntiAlias(true);
            paint.setShader(new LinearGradient(0f, top + 2f, 0f, top + core / 2f, 0x35FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
            canvas.drawRect(left + 3f, top + 2f, left + core - 3f, top + core / 2f, paint);
            paint.reset();
            paint.setAntiAlias(true);
            paint.setColor(moodColor);
            canvas.drawRoundRect(cx - 4.5f, top + core - 5f, cx + 4.5f, top + core - 2f, 2f, 2f, paint);
            canvas.restore();

            // 邊框
            paint.reset();
            paint.setAntiAlias(true);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(1.5f);
            paint.setColor(BRAND);
            canvas.drawPath(shape, paint);

            // 中央：疊用數字，單篇用閃光符號
            if (stack) {
                paint.reset();
                paint.setAntiAlias(true);
                paint.setColor(TEXT);
                paint.setTypeface(Typeface.DEFAULT_BOLD);
                paint.setTextSize(13f);
                paint.setTextAlign(Paint.Align.CENTER);
                Paint.FontMetrics metrics = paint.getFontMetrics();
                float baseline = cy - 1.5f - (metrics.ascent + metrics.descent) / 2f;
                canvas.drawText(count > 99 ? "99+" : String.valueOf(count), cx, baseline, paint);
            } else {
                canvas.save();
                canvas.translate(cx - 7f, cy - 8f);
                canvas.scale(14f / 24f, 14f / 24f);
                paint.reset();
                paint.setAntiAlias(true);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(1.7f);
                paint.setStrokeCap(Paint.Cap.ROUND);
                paint.setStrokeJoin(Paint.Join.ROUND);
                paint.setColor(BRAND);
                canvas.drawPath(sparkle, paint);
                canvas.restore();
            }
            canvas.restore();
            return bitmap;
        }
    }
}
