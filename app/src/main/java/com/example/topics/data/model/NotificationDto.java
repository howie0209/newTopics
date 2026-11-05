package com.example.topics.data.model;

import com.google.gson.annotations.SerializedName;

// 通知，對齊後端 models/Notification.js：type 是 friend_request / friend_accepted / diary_reaction
public class NotificationDto {
    @SerializedName("_id")
    public String id;
    public String type;
    public UserDto actor;
    public String targetId;
    public String readAt;
    public String createdAt;

    public boolean isUnread() {
        return readAt == null || readAt.isEmpty();
    }
}
