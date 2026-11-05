package com.example.topics.data.model;

// POST /auth/check-email 的回傳：{ success, exists }
public class CheckEmailResponse {
    public boolean success;
    public String message;
    public boolean exists;
}
