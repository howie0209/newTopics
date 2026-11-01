package com.example.topics.data.model;

public class ChatTurn {
    public String role;
    public String content;

    public ChatTurn(String role, String content) {
        this.role = role;
        this.content = content;
    }
}