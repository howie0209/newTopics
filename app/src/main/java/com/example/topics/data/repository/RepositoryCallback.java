package com.example.topics.data.repository;

public interface RepositoryCallback<T> {
    void onSuccess(T value);
    void onError(String message);
}
