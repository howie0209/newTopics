package com.example.topics.data.local;

import android.content.Context;
import android.content.SharedPreferences;
import com.example.topics.data.model.UserDto;

public class SessionManager {
    private static final String PREFS_NAME = "AdriftSession";
    private static final String KEY_TOKEN = "jwt_token";
    private static final String KEY_USER_ID = "user_id";
    private static final String KEY_NAME = "name";
    private static final String KEY_EMAIL = "email";
    private static final String KEY_USER_CODE = "user_code";
    private static final String KEY_ROLE = "role";
    private static final String KEY_AVATAR = "avatar";
    private static final String KEY_CREATED_AT = "created_at";

    private static SessionManager instance;
    private final SharedPreferences preferences;

    private SessionManager(Context context) {
        preferences = context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static synchronized SessionManager getInstance(Context context) {
        if (instance == null) {
            instance = new SessionManager(context);
        }
        return instance;
    }

    public void saveToken(String token) {
        preferences.edit().putString(KEY_TOKEN, token == null ? "" : token).apply();
    }

    public String getToken() {
        return preferences.getString(KEY_TOKEN, "");
    }

    public boolean hasToken() {
        String token = getToken();
        return token != null && !token.isEmpty();
    }

    public void saveUser(UserDto user) {
        if (user == null) return;

        preferences.edit()
                .putString(KEY_USER_ID, safe(user.getId()))
                .putString(KEY_NAME, safe(user.name))
                .putString(KEY_EMAIL, safe(user.email))
                .putString(KEY_USER_CODE, safe(user.userCode))
                .putString(KEY_ROLE, safe(user.role))
                .putString(KEY_AVATAR, safe(user.avatar))
                .putString(KEY_CREATED_AT, safe(user.createdAt))
                .apply();
    }

    public UserDto getUser() {
        if (getUserId().isEmpty()) return null;
        UserDto user = new UserDto();
        user.id = getUserId();
        user.mongoId = getUserId();
        user.name = getName();
        user.email = getEmail();
        user.userCode = getUserCode();
        user.role = preferences.getString(KEY_ROLE, "");
        user.avatar = preferences.getString(KEY_AVATAR, "");
        user.createdAt = preferences.getString(KEY_CREATED_AT, "");
        return user;
    }

    public String getUserId() {
        return preferences.getString(KEY_USER_ID, "");
    }

    public String getName() {
        return preferences.getString(KEY_NAME, "");
    }

    public String getEmail() {
        return preferences.getString(KEY_EMAIL, "");
    }

    public String getUserCode() {
        return preferences.getString(KEY_USER_CODE, "");
    }

    public void clear() {
        preferences.edit().clear().apply();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
