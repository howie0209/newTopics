package com.example.topics.data.model;

import com.google.gson.annotations.SerializedName;

public class FriendDto {
    public String id;
    @SerializedName("_id")
    public String mongoId;
    public String name;
    public String userCode;
    public String avatar;
    public String requestId;
    public UserDto from;
    public UserDto to;
    public String createdAt;

    public String getId() {
        if (id != null && !id.isEmpty()) return id;
        if (mongoId != null && !mongoId.isEmpty()) return mongoId;
        return "";
    }
}
