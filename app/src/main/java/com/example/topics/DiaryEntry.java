package com.example.topics;

// 🎯 只新增這行導入，確保 Gson 能識別序列化名稱
import com.google.gson.annotations.SerializedName;

public class DiaryEntry {
    public String userId;    // 關聯使用者
    public String title;     // 標題
    public String mood;      // 心情
    public String content;   // 日記內容
    public double latitude;  // 緯度
    public double longitude; // 經度
    public String date;      // 日期 (保持名稱一致)

    // 🎯 核心更動：加上註解，強制讓 Retrofit/Gson 在解析後端資料時，將資料庫的 "privacy" 穩穩寫入這個變數
    @SerializedName("privacy")
    public int privacy;

    // 💡 保留你原本運作的 7 個參數建構子，確保原本的發文完全不崩潰
    public DiaryEntry(String userId, String title, String mood, String content, double latitude, double longitude, String time) {
        this.userId = userId;
        this.title = title;
        this.mood = mood;
        this.content = content;
        this.latitude = latitude;
        this.longitude = longitude;
        this.date = time;
        this.privacy = 0; // 預設私人
    }

    // 🎯 核心新增：重載一個包含 privacy 的 8 參數建構子，讓更新與同步能正確夾帶權限上傳！
    public DiaryEntry(String userId, String title, String mood, String content, double latitude, double longitude, String time, int privacy) {
        this.userId = userId;
        this.title = title;
        this.mood = mood;
        this.content = content;
        this.latitude = latitude;
        this.longitude = longitude;
        this.date = time;
        this.privacy = privacy;
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

    // 🎯 核心新增：Getter 讓連動框架可以正常讀取權限
    public int getPrivacy() {
        return privacy;
    }
}