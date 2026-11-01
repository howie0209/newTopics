package com.example.topics.data.model;

import com.google.gson.JsonElement;
import java.util.List;

public class ChatRequest {
    public String message;
    public List<ChatTurn> history;
    public JsonElement queryContext;

    public ChatRequest(String message, List<ChatTurn> history, JsonElement queryContext) {
        this.message = message;
        this.history = history;
        this.queryContext = queryContext;
    }
}