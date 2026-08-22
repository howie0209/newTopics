package com.example.topics.ui.social;

public class SocialUserItem {
    public String id;
    public String requestId;
    public String name;
    public String userCode;
    public String avatar;
    public String meta;
    public String status;
    public String primaryText;
    public String secondaryText;
    public boolean primaryEnabled = true;
    public boolean showSecondary;

    public String displayName() {
        return name == null || name.isEmpty() ? "使用者" : name;
    }

    public String displayCode() {
        return userCode == null || userCode.isEmpty() ? "" : "@" + userCode;
    }
}
