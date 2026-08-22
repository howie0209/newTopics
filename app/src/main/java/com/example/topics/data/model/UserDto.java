package com.example.topics.data.model;

import com.google.gson.annotations.SerializedName;

public class UserDto {
    public String id;
    @SerializedName("_id")
    public String mongoId;
    public String name;
    public String email;
    public String userCode;
    public String role;
    public String avatar;
    public String createdAt;

    public String getId() {
        return firstNonEmpty(id, mongoId);
    }

    public String getDisplayName() {
        return firstNonEmpty(name, "使用者");
    }

    public String getUserCode() {
        return firstNonEmpty(userCode, "");
    }

    private static String firstNonEmpty(String value, String fallback) {
        return value == null || value.isEmpty() ? fallback : value;
    }
}
