package com.example.topics.data.model;

import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.List;

// GET /friends/:id/places 的回傳：你們都留下過記憶的地方
public class SharedPlacesData {
    public List<Place> places;
    public String description;

    public List<Place> getPlaces() {
        return places == null ? new ArrayList<>() : places;
    }

    public static class Place {
        public String place;
        public PlaceDiary mine;
        public List<PlaceDiary> theirs;
    }

    public static class PlaceDiary {
        @SerializedName("_id")
        public String id;
        public String title;
        public String createdAt;
    }
}
