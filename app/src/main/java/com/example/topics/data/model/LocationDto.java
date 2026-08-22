package com.example.topics.data.model;

import java.util.List;

public class LocationDto {
    public String type;
    public List<Double> coordinates;
    public Double lat;
    public Double lng;
    public String placeName;

    public Double getLatitude() {
        if (lat != null) return lat;
        if (coordinates != null && coordinates.size() == 2) return coordinates.get(1);
        return null;
    }

    public Double getLongitude() {
        if (lng != null) return lng;
        if (coordinates != null && coordinates.size() == 2) return coordinates.get(0);
        return null;
    }
}
