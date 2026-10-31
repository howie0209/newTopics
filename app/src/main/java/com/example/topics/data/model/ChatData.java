package com.example.topics.data.model;

import com.google.gson.JsonElement;
import java.util.List;

public class ChatData {
    public String answer;
    public String scope;
    public String status;
    public List<ChatRelatedDiary> relatedDiaries;
    public int matchedCount;
    public int sampledCount;
    public JsonElement queryContext;
}
