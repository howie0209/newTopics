package com.example.topics;

import com.google.gson.annotations.SerializedName;

public class FriendRecord {
    // 💡 對接後端回傳的關係 ID
    @SerializedName("id")
    private String id;

    // 💡 對接後端處理好的對方名字 (可能是 requester 或 recipient 的 username)
    @SerializedName("targetName")
    private String targetName;

    // 💡 對接對方的 User ID
    @SerializedName("targetId")
    private String targetId;

    @SerializedName("status")
    private String status;

    private String createdAt;

    public FriendRecord(String id, String targetName, String targetId, String status, String createdAt) {
        this.id = id;
        this.targetName = targetName;
        this.targetId = targetId;
        this.status = status;
        this.createdAt = createdAt;
    }

    // Getter 方法
    public String getId() { return id; }

    public String getTargetName() { return targetName; }

    public String getTargetId() { return targetId; }

    public String getStatus() { return status; }

    public String getCreatedAt() { return createdAt; }
}
