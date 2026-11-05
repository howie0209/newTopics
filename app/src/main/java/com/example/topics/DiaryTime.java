package com.example.topics;

import java.util.Calendar;

/**
 * 日記時間顯示，對齊網頁版 utils/diaryTime.js 的 formatDiaryTime：
 * 一小時內「N 分鐘前」、一天內「N 小時前」、一年內「M月D日 HH:mm」、更久「YYYY年M月D日」。
 * 後端時間是 UTC，這裡轉成手機的時區顯示。
 */
public final class DiaryTime {
    private static final long MINUTE = 60_000L;
    private static final long HOUR = 60 * MINUTE;
    private static final long DAY = 24 * HOUR;
    private static final long YEAR = 365 * DAY;

    private DiaryTime() {}

    public static String format(String iso) {
        long ms = InsightFormat.parseIso(iso);
        if (ms < 0) return "";
        return format(ms, System.currentTimeMillis());
    }

    public static String format(long ms, long now) {
        long diff = now - ms;
        Calendar date = Calendar.getInstance();
        date.setTimeInMillis(ms);
        String hh = pad(date.get(Calendar.HOUR_OF_DAY));
        String mm = pad(date.get(Calendar.MINUTE));
        int month = date.get(Calendar.MONTH) + 1;
        int day = date.get(Calendar.DAY_OF_MONTH);

        if (diff >= 0 && diff < HOUR) {
            return Math.max(1, diff / MINUTE) + " 分鐘前";
        }
        if (diff >= 0 && diff < DAY) {
            return (diff / HOUR) + " 小時前";
        }
        if (diff >= 0 && diff < YEAR) {
            return month + "月" + day + "日 " + hh + ":" + mm;
        }
        return date.get(Calendar.YEAR) + "年" + month + "月" + day + "日";
    }

    /** 只顯示日期（例如加入日期），用手機時區，格式 YYYY年M月D日 */
    public static String dateOnly(String iso) {
        long ms = InsightFormat.parseIso(iso);
        if (ms < 0) return "";
        Calendar date = Calendar.getInstance();
        date.setTimeInMillis(ms);
        return date.get(Calendar.YEAR) + "年" + (date.get(Calendar.MONTH) + 1) + "月" + date.get(Calendar.DAY_OF_MONTH) + "日";
    }

    private static String pad(int value) {
        return value < 10 ? "0" + value : String.valueOf(value);
    }
}
