package com.example.topics.data.model;

import java.util.ArrayList;
import java.util.List;

// GET /friends/:id/memories 的回傳
public class MemoryPageData {
    public List<DiaryDto> diaries;
    public String nextCursor;

    public List<DiaryDto> getDiaries() {
        return diaries == null ? new ArrayList<>() : diaries;
    }
}
