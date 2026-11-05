package com.example.topics.data.model;

import java.util.ArrayList;
import java.util.List;

// GET /diaries/feed 的回傳：{ diaries, nextCursor }
public class FeedData {
    public List<DiaryDto> diaries;
    public String nextCursor;

    public List<DiaryDto> getDiaries() {
        return diaries == null ? new ArrayList<>() : diaries;
    }
}
