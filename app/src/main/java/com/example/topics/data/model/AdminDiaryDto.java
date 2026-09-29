package com.example.topics.data.model;

public class AdminDiaryDto {
    public String _id;
    public String title;
    public String content;
    public String text;
    public AdminMood mood;
    public String visibility;
    public String imageUrl;
    public String createdAt;
    public String lastEditedAt;
    public int editCount;
    public AdminDiaryAuthor author;
    public AdminLocation location;
    public String locationAccuracy;

    public static class AdminMood {
        public String type;
        public int intensity;
    }

    public static class AdminLocation {
        public String placeName;
    }
}