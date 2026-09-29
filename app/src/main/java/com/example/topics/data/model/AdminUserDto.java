package com.example.topics.data.model;

public class AdminUserDto {
    public String _id;
    public String id;
    public String name;
    public String userCode;
    public String email;
    public String role;
    public String avatar;
    public String createdAt;

    public String getUserId() {
        return _id != null ? _id : id;
    }
}
