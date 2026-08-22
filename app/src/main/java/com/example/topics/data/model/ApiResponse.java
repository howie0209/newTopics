package com.example.topics.data.model;

public class ApiResponse<T> {
    public boolean success;
    public String message;
    public String token;
    public UserDto user;
    public T data;

    public boolean isSuccessful() {
        return success;
    }
}
