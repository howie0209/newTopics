package com.example.topics;

public class DiaryEntry {
    public String userId;    // 關聯使用者
    public String title;     // 標題
    public String mood;      // 心情
    public String content;   // 日記內容
    public double latitude;  // 緯度
    public double longitude; // 經度
    public String date;      // 日期 (保持名稱一致)

    public DiaryEntry(String userId, String title, String mood, String content, double latitude, double longitude, String time) {
        this.userId = userId;
        this.title = title;
        this.mood = mood;
        this.content = content;
        this.latitude = latitude;
        this.longitude = longitude;
        this.date = time;
    }

    // 💡 補上這些 Getter 方法，MapActivity 才能讀取到資料
    public double getLat() {
        return latitude;
    }

    public double getLng() {
        return longitude;
    }

    public String getTitle() {
        return title;
    }

    public String getMood() {
        return mood;
    }
    public String getUserId() {
        return userId; // 💡 確保 DiaryEntry 類別裡有宣告 userId 變數
    }
}