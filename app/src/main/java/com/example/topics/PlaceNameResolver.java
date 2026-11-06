package com.example.topics;

import android.content.Context;
import android.location.Address;
import android.location.Geocoder;
import android.os.Handler;
import android.os.Looper;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 把經緯度轉成地點名稱，照 iOS 版 PlaceNameResolver 的做法：
 * 顯示「縣市＋區」（例如 臺中市沙鹿區），台→臺；
 * 查不到中文地名時顯示「地點暫時無法取得」，不把座標露出來。
 */
public final class PlaceNameResolver {
    public interface Callback {
        void onResolved(String placeName);
    }

    public static final String UNKNOWN = "地點暫時無法取得";
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final Map<String, String> CACHE = new HashMap<>();

    private PlaceNameResolver() {}

    /** 是否真的查到地名（查不到時不要把「地點暫時無法取得」存進日記） */
    public static boolean isResolved(String name) {
        return name != null && !name.isEmpty() && !UNKNOWN.equals(name);
    }

    public static String formatCoordinates(double lat, double lng) {
        return String.format(Locale.US, "%.3f, %.3f", lat, lng);
    }

    public static void resolve(Context context, double lat, double lng, Callback callback) {
        String key = String.format(Locale.US, "%.3f,%.3f", lat, lng);
        synchronized (CACHE) {
            String cached = CACHE.get(key);
            if (cached != null) {
                callback.onResolved(cached);
                return;
            }
        }
        Context app = context.getApplicationContext();
        EXECUTOR.execute(() -> {
            String name = lookup(app, lat, lng);
            if (isResolved(name)) {
                synchronized (CACHE) {
                    CACHE.put(key, name);
                }
            }
            MAIN.post(() -> callback.onResolved(name));
        });
    }

    @SuppressWarnings("deprecation")
    private static String lookup(Context context, double lat, double lng) {
        if (!Geocoder.isPresent()) return UNKNOWN;
        try {
            Geocoder geocoder = new Geocoder(context, Locale.TRADITIONAL_CHINESE);
            List<Address> results = geocoder.getFromLocation(lat, lng, 1);
            if (results == null || results.isEmpty()) return UNKNOWN;
            Address address = results.get(0);
            // 縣市：AdminArea（臺中市、彰化縣）；區：Locality 或 SubAdminArea（沙鹿區、彰化市）
            String city = hanOnly(firstNonEmpty(address.getAdminArea(), address.getSubAdminArea()));
            String district = hanOnly(firstNonEmpty(address.getLocality(), address.getSubAdminArea(), address.getSubLocality()));
            if (city == null && district == null) return UNKNOWN;
            if (city == null) return normalizeTaiwan(district);
            if (district == null || district.equals(city) || city.contains(district)) return normalizeTaiwan(city);
            return normalizeTaiwan(city + district);
        } catch (Exception ignored) {
            return UNKNOWN;
        }
    }

    public static String normalizeTaiwan(String name) {
        if (name == null) return "";
        return name.trim()
                .replace("台北", "臺北")
                .replace("台中", "臺中")
                .replace("台南", "臺南")
                .replace("台東", "臺東")
                .replace("台灣", "臺灣");
    }

    private static String hanOnly(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.matches(".*[\\u4e00-\\u9fff].*") ? trimmed : null;
    }

    private static String firstNonEmpty(String... values) {
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) return value;
        }
        return null;
    }
}
