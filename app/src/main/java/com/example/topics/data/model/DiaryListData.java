package com.example.topics.data.model;

import java.util.ArrayList;
import java.util.List;

public class DiaryListData {
    public List<DiaryDto> diaries;

    public List<DiaryDto> getDiaries() {
        return diaries == null ? new ArrayList<>() : diaries;
    }
}
