package com.example.topics;

public class User {
    // --- 新加入的欄位 ---
    public String userId;    // 使用者唯一 ID (對接資料庫亂碼 ID)
    public String status;    // 好友狀態 (pending, accepted 等)

    // --- 原有的欄位 (維持不動) ---
    public String username;
    public String password;
    public String email;

    // 💡 補上無參數建構子：修復 LoginActivity 裡 new User() 找不到建構子的錯誤
    public User() {
    }

    // 💡 供註冊使用的建構子：包含帳號、密碼、Email (維持不動)
    public User(String username, String password, String email) {
        this.username = username;
        this.password = password;
        this.email = email;
    }

    // 💡 供登入使用的建構子：僅需帳號與密碼 (維持不動)
    public User(String username, String password) {
        this.username = username;
        this.password = password;
    }

    // 💡 新功能專用：用靜態方法建立搜尋物件，避免與 (String, String) 建構子衝突
    public static User createForSearch(String userId, String username) {
        User user = new User();
        user.userId = userId;
        user.username = username;
        return user;
    }

    // --- 新加入的 Setter/Getter ---
    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getUserId() {
        return userId;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getStatus() {
        return status;
    }

    // --- 原有的 Setter/Getter (維持不動) ---
    public void setUsername(String username) {
        this.username = username;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getUsername() {
        return username;
    }

    public String getEmail() {
        return email;
    }

    public String getPassword() {
        return password;
    }
}