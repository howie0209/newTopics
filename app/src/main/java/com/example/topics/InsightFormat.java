package com.example.topics;

import com.example.topics.data.mapper.DiaryMapper;
import com.example.topics.data.model.OverviewData;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

public final class InsightFormat {
    private static final long DAY_MS = 86400000L;
    private static final long TAIPEI_OFFSET_MS = 8L * 3600000L;

    private InsightFormat() {}

    public static class TrendBin {
        public String label;
        public Double average;
        public int count;
    }

    public static int moodColor(String type) {
        if ("calm".equals(type)) return 0xFF158E91;
        if ("joy".equals(type)) return 0xFFC18822;
        if ("sad".equals(type)) return 0xFF568BC9;
        if ("wonder".equals(type)) return 0xFF9276B7;
        if ("anxious".equals(type)) return 0xFFB36C86;
        if ("confused".equals(type)) return 0xFF7C83BF;
        if ("nostalgic".equals(type)) return 0xFF889466;
        return 0xFF7C909C;
    }

    public static String moodLabel(String type) {
        if (type == null || type.isEmpty()) return "未分類";
        return DiaryMapper.moodLabel(type);
    }

    public static long parseIso(String value) {
        if (value == null) return -1;
        String[] patterns = {"yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", "yyyy-MM-dd'T'HH:mm:ss'Z'"};
        for (String pattern : patterns) {
            try {
                SimpleDateFormat format = new SimpleDateFormat(pattern, Locale.US);
                format.setTimeZone(TimeZone.getTimeZone("UTC"));
                return format.parse(value).getTime();
            } catch (ParseException ignored) {
            }
        }
        return -1;
    }

    // 以台北時間的「日期」表示，例如 11/1
    public static String shortDate(String iso) {
        long ms = parseIso(iso);
        return ms < 0 ? "" : shortDateFromMillis(ms);
    }

    public static String shortDateFromMillis(long ms) {
        return shortDateFromDay((ms + TAIPEI_OFFSET_MS) / DAY_MS);
    }

    private static String shortDateFromDay(long day) {
        Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        calendar.setTimeInMillis(day * DAY_MS);
        return (calendar.get(Calendar.MONTH) + 1) + "/" + calendar.get(Calendar.DAY_OF_MONTH);
    }

    // "2026-11-01" -> 自 1970 起算的天數（日期本身就是台北日期，所以直接用 UTC 午夜計算）
    private static long dayIndex(String date) {
        try {
            String[] parts = date.split("-");
            Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
            calendar.clear();
            calendar.set(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]) - 1, Integer.parseInt(parts[2]));
            return calendar.getTimeInMillis() / DAY_MS;
        } catch (Exception e) {
            return Long.MIN_VALUE;
        }
    }

    public static List<TrendBin> trendBins(OverviewData data) {
        List<TrendBin> result = new ArrayList<>();
        if (data == null) return result;
        long startMs = parseIso(data.start), endMs = parseIso(data.end);
        if (startMs < 0 || endMs < 0 || endMs <= startMs) return result;

        long startDay = (startMs + TAIPEI_OFFSET_MS) / DAY_MS;
        long endDay = (endMs + TAIPEI_OFFSET_MS) / DAY_MS;
        long span = endDay - startDay + 1;
        long size = (span + 4) / 5;
        int binCount = (int) ((span + size - 1) / size);

        double[] total = new double[binCount];
        int[] weight = new int[binCount];
        int[] count = new int[binCount];

        if (data.daily != null) {
            for (OverviewData.OverviewDay day : data.daily) {
                long index = dayIndex(day.date);
                if (index == Long.MIN_VALUE || index < startDay) continue;
                int bin = (int) ((index - startDay) / size);
                if (bin < 0 || bin >= binCount) continue;
                count[bin] += day.count;
                if (day.averageIntensity != null && day.intensityCount > 0) {
                    total[bin] += day.averageIntensity * day.intensityCount;
                    weight[bin] += day.intensityCount;
                }
            }
        }

        for (int i = 0; i < binCount; i++) {
            TrendBin bin = new TrendBin();
            bin.label = shortDateFromDay(startDay + i * size);
            bin.count = count[i];
            bin.average = weight[i] > 0 ? total[i] / weight[i] : null;
            result.add(bin);
        }
        return result;
    }
}
