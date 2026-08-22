package com.example.topics.data.mapper;

public class DiaryUiModel {
    public final String id;
    public final double lat;
    public final double lng;
    public final String title;
    public final String moodType;
    public final int intensity;
    public final String text;
    public final String time;
    public final String imageUrl;
    public final String placeName;
    public final String authorAvatar;
    public final String userReaction;
    public final boolean canEdit;
    public final boolean isMine;
    public final int visibility;
    public final String authorName;
    public final int understandCount;
    public final int hugCount;
    public final int relateCount;

    public DiaryUiModel(
            String id,
            double lat,
            double lng,
            String title,
            String moodType,
            int intensity,
            String text,
            String time,
            String imageUrl,
            String placeName,
            String authorAvatar,
            String userReaction,
            boolean canEdit,
            boolean isMine,
            int visibility,
            String authorName,
            int understandCount,
            int hugCount,
            int relateCount
    ) {
        this.id = id;
        this.lat = lat;
        this.lng = lng;
        this.title = title;
        this.moodType = moodType;
        this.intensity = intensity;
        this.text = text;
        this.time = time;
        this.imageUrl = imageUrl;
        this.placeName = placeName;
        this.authorAvatar = authorAvatar;
        this.userReaction = userReaction;
        this.canEdit = canEdit;
        this.isMine = isMine;
        this.visibility = visibility;
        this.authorName = authorName;
        this.understandCount = understandCount;
        this.hugCount = hugCount;
        this.relateCount = relateCount;
    }
}
