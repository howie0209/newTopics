package com.example.topics.data.repository;

import android.content.Context;

import com.example.topics.ApiService;
import com.example.topics.data.model.ApiResponse;
import com.example.topics.data.model.EmptyResponse;
import com.example.topics.data.model.NotificationListData;
import com.example.topics.data.model.NotificationTargetData;
import com.example.topics.data.remote.ApiClient;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class NotificationRepository extends BaseRepository {
    private final ApiService api;

    public NotificationRepository(Context context) {
        api = ApiClient.getService(context);
    }

    public void getNotifications(String cursor, RepositoryCallback<NotificationListData> callback) {
        api.getNotifications(cursor).enqueue(new Callback<ApiResponse<NotificationListData>>() {
            @Override
            public void onResponse(Call<ApiResponse<NotificationListData>> call, Response<ApiResponse<NotificationListData>> response) {
                dispatchIfSuccessful(response, callback);
            }

            @Override
            public void onFailure(Call<ApiResponse<NotificationListData>> call, Throwable t) {
                callback.onError(networkMessage(t));
            }
        });
    }

    public void readAll(RepositoryCallback<EmptyResponse> callback) {
        api.readAllNotifications().enqueue(emptyCallback(callback));
    }

    public void read(String id, RepositoryCallback<EmptyResponse> callback) {
        api.readNotification(id).enqueue(emptyCallback(callback));
    }

    public void getTarget(String id, RepositoryCallback<NotificationTargetData> callback) {
        api.getNotificationTarget(id).enqueue(new Callback<ApiResponse<NotificationTargetData>>() {
            @Override
            public void onResponse(Call<ApiResponse<NotificationTargetData>> call, Response<ApiResponse<NotificationTargetData>> response) {
                dispatchIfSuccessful(response, callback);
            }

            @Override
            public void onFailure(Call<ApiResponse<NotificationTargetData>> call, Throwable t) {
                callback.onError(networkMessage(t));
            }
        });
    }

    private Callback<ApiResponse<EmptyResponse>> emptyCallback(RepositoryCallback<EmptyResponse> callback) {
        return new Callback<ApiResponse<EmptyResponse>>() {
            @Override
            public void onResponse(Call<ApiResponse<EmptyResponse>> call, Response<ApiResponse<EmptyResponse>> response) {
                ApiResponse<EmptyResponse> body = response.body();
                if (response.isSuccessful() && body != null && body.success) {
                    callback.onSuccess(body.data);
                    return;
                }
                callback.onError(extractMessage(response, body));
            }

            @Override
            public void onFailure(Call<ApiResponse<EmptyResponse>> call, Throwable t) {
                callback.onError(networkMessage(t));
            }
        };
    }
}
