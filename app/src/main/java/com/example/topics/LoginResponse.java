package com.example.topics;

import com.google.gson.annotations.SerializedName;

public class LoginResponse {

    // 後端回傳的 JSON 裡沒有 success 欄位，是透過 HTTP 狀態碼判斷，但保留 message
    @SerializedName("message")
    private String message;

    // 💡 關鍵修正：後端回傳的是 userId (user._id)，必須準確對應
    @SerializedName("userId")
    private String userId;

    // --- Getter 方法，讓 Activity 可以取得資料 ---

    public String getMessage() {
        return message;
    }

    public String getUserId() {
        return userId;
    }

    // --- Setter 方法 (選用) ---
    public void setMessage(String message) {
        this.message = message;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }
}