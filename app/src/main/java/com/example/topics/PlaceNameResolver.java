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
 * 把經緯度轉成地點名稱，對齊網頁版 utils/placeName.js：
 * 優先取縣市層級的名稱，台→臺，查不到時退回「緯度, 經度」。
 */
public final class PlaceNameResolver {
    public interface Callback {
        void onResolved(String placeName);
    }

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final Map<String, String> CACHE = new HashMap<>();

    private PlaceNameResolver() {}

    public static String formatCoordinates(double lat, double lng) {
        return String.format(Locale.US, "%.3f, %.3f", lat, lng);
    }

    public static void resolve(Context context, double lat, double lng, Callback callback) {
        String key = String.format(Locale.US, "%.4f,%.4f", lat, lng);
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
            synchronized (CACHE) {
                CACHE.put(key, name);
            }
            MAIN.post(() -> callback.onResolved(name));
        });
    }

    @SuppressWarnings("deprecation")
    private static String lookup(Context context, double lat, double lng) {
        String fallback = formatCoordinates(lat, lng);
        if (!Geocoder.isPresent()) return fallback;
        try {
            Geocoder geocoder = new Geocoder(context, Locale.TRADITIONAL_CHINESE);
            List<Address> results = geocoder.getFromLocation(lat, lng, 1);
            if (results == null || results.isEmpty()) return fallback;
            Address address = results.get(0);
            String name = firstNonEmpty(address.getAdminArea(), address.getSubAdminArea(), address.getLocality(), address.getCountryName());
            return name == null ? fallback : normalizeTaiwan(name);
        } catch (Exception ignored) {
            return fallback;
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

    private static String firstNonEmpty(String... values) {
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) return value;
        }
        return null;
    }
}
