package com.example.topics.data.model;

import java.util.List;

public class OverviewData {
    public int days;
    public String start;
    public String end;
    public int count;
    public List<OverviewMood> moods;
    public List<OverviewDay> daily;
    public int placeCount;
    public List<OverviewPlace> places;

    public static class OverviewMood {
        public String type;
        public int count;
    }

    public static class OverviewDay {
        public String date;
        public int count;
        public Double averageIntensity;
        public int intensityCount;
    }

    public static class OverviewPlace {
        public String name;
        public int count;
        public String diaryId;
        public String mood;
        public String latestAt;
    }
}