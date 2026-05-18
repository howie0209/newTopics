package com.example.topics;

public class FriendRequest {
    public String fromUserId;
    public String toUserId;

    public FriendRequest(String from, String to) {
        this.fromUserId = from;
        this.toUserId = to;
    }
}