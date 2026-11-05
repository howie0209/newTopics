package com.example.topics.data.model;

import com.google.gson.annotations.SerializedName;

import java.util.List;

// 搜尋使用者、推薦好友的一筆資料（後端 publicPerson + relationship）
public class PersonDto {
    public String id;
    @SerializedName("_id")
    public String mongoId;
    public String name;
    public String userCode;
    public String avatar;
    public String createdAt;
    public String friendshipStatus;
    public String requestId;
    public int mutualFriendsCount;
    public List<String> reasons;

    public String getId() {
        if (id != null && !id.isEmpty()) return id;
        return mongoId == null ? "" : mongoId;
    }

    public String getDisplayName() {
        return name == null || name.isEmpty() ? "使用者" : name;
    }
}
