package com.example.topics;

import com.google.android.gms.maps.model.LatLng;

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
        this.date = time; // 修正處：將傳入的 time 賦值給類別變數 date
    }
}