package com.example.topics.data.model;

import com.google.gson.JsonElement;
import java.util.List;

public class ChatRequest {
    public String message;
    public List<String> history;
    public JsonElement queryContext;

    public ChatRequest(String message, List<String> history, JsonElement queryContext) {
        this.message = message;
        this.history = history;
        this.queryContext = queryContext;
    }
}