package com.example.topics.data.model;

public class RegisterRequest {
    public final String name;
    public final String email;
    public final String password;
    public final String userCode;

    public RegisterRequest(String name, String email, String password, String userCode) {
        this.name = name;
        this.email = email;
        this.password = password;
        this.userCode = userCode;
    }
}
