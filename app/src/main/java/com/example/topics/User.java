package com.example.topics;

public class User {
    public String username;
    public String password;
    public String email;

    // 💡 補上無參數建構子：修復 LoginActivity 裡 new User() 找不到建構子的錯誤
    public User() {
    }

    // 💡 供註冊使用的建構子：包含帳號、密碼、Email
    public User(String username, String password, String email) {
        this.username = username;
        this.password = password;
        this.email = email;
    }

    // 💡 供登入使用的建構子：僅需帳號與密碼
    public User(String username, String password) {
        this.username = username;
        this.password = password;
    }

    // 💡 補上 Setter 方法：修復 LoginActivity 裡找不到 setEmail 和 setPassword 的錯誤
    public void setUsername(String username) {
        this.username = username;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    // 💡 建議也補上 Getter 方法，方便後續資料讀取
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