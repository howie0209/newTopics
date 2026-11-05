package com.example.topics.data.model;

import java.util.ArrayList;
import java.util.List;

// GET /notifications 的回傳：{ items, nextCursor, unreadCount }
public class NotificationListData {
    public List<NotificationDto> items;
    public String nextCursor;
    public int unreadCount;

    public List<NotificationDto> getItems() {
        return items == null ? new ArrayList<>() : items;
    }
}
