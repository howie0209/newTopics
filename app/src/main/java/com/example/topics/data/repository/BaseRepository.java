package com.example.topics.data.repository;

import com.example.topics.data.model.ApiResponse;
import retrofit2.Response;

abstract class BaseRepository {
    protected <T> boolean dispatchIfSuccessful(Response<ApiResponse<T>> response, RepositoryCallback<T> callback) {
        ApiResponse<T> body = response.body();
        if (response.isSuccessful() && body != null && body.success) {
            callback.onSuccess(body.data);
            return true;
        }

        callback.onError(extractMessage(response, body));
        return false;
    }

    protected <T> String extractMessage(Response<ApiResponse<T>> response, ApiResponse<T> body) {
        if (body != null && body.message != null && !body.message.isEmpty()) {
            return body.message;
        }
        return "Request failed (" + response.code() + ")";
    }

    protected String networkMessage(Throwable throwable) {
        String message = throwable.getMessage();
        return message == null || message.isEmpty() ? "網路連線失敗，請稍後再試" : message;
    }
}
