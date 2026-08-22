package com.example.topics.data.model;

public class MoodDto {
    public String type;
    public int intensity;

    public String getType() {
        return type == null || type.isEmpty() ? "other" : type;
    }

    public int getIntensity() {
        if (intensity < 1) return 1;
        if (intensity > 5) return 5;
        return intensity;
    }
}
