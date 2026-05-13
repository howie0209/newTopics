package com.example.topics;

public class User {
    public String username;
    public String password;
    public String email; // 💡 已經幫你加上 Email 欄位了

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
}