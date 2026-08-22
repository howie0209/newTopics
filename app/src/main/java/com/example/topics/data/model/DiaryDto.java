package com.example.topics.data.model;

import com.google.gson.annotations.SerializedName;
import java.util.ArrayList;
import java.util.List;

public class DiaryDto {
    public String id;
    @SerializedName("_id")
    public String mongoId;
    public String title;
    public String text;
    public String content;
    public MoodDto mood;
    public String imageUrl;
    public List<String> images;
    public LocationDto location;
    public String locationAccuracy;
    public String visibility;
    public ReactionDto reactions;
    public String userReaction;
    public UserDto author;
    public UserDto user;
    public String createdAt;
    public String updatedAt;
    public String lastEditedAt;
    public int editCount;
    public boolean canEdit;
    public String editExpiresAt;
    public Integer editDistanceLimitMeters;

    public String getId() {
        return firstNonEmpty(id, mongoId);
    }

    public String getText() {
        return firstNonEmpty(text, content);
    }

    public String getTitle() {
        return firstNonEmpty(title, "（未命名日記）");
    }

    public String getVisibility() {
        return firstNonEmpty(visibility, "private");
    }

    public MoodDto getMood() {
        if (mood == null) mood = new MoodDto();
        return mood;
    }

    public ReactionDto getReactions() {
        if (reactions == null) reactions = new ReactionDto();
        return reactions;
    }

    public UserDto getAuthor() {
        return author != null ? author : user;
    }

    public List<String> getImages() {
        if (images == null) images = new ArrayList<>();
        if (imageUrl != null && !imageUrl.isEmpty() && !images.contains(imageUrl)) {
            images.add(0, imageUrl);
        }
        return images;
    }

    private static String firstNonEmpty(String value, String fallback) {
        return value == null || value.isEmpty() ? fallback : value;
    }
}
