package com.example.topics.data.model;

import com.google.gson.annotations.SerializedName;

public class FriendProfileDto {
    public String id;
    @SerializedName("_id")
    public String mongoId;
    public String name;
    public String userCode;
    public String avatar;
    public String createdAt;
    public DiaryStatsDto diaryStats;

    public String getId() {
        if (id != null && !id.isEmpty()) return id;
        if (mongoId != null && !mongoId.isEmpty()) return mongoId;
        return "";
    }

    public String getDisplayName() {
        return name == null || name.isEmpty() ? "使用者" : name;
    }
}
