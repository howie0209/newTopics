package com.example.topics.data.model;

import java.util.List;

public class LifeMapData {
    public boolean notEnoughData;
    public int required;
    public int current;

    public String summary;
    public MoodTrend moodTrend;
    public List<LocationInsight> locationInsights;
    public List<String> behaviorPatterns;
    public List<String> suggestions;
}
