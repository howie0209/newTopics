package com.example.topics.data.model;

public class ChatRelatedDiary {
    public String id;
    public String title;
    public String createdAt;
    public String place;
    public Mood mood;

    public static class Mood {
        public String type;
        public Integer intensity;
    }
}