package com.example.topics.data.repository;

import android.content.Context;
import com.example.topics.ApiService;
import com.example.topics.data.local.SessionManager;
import com.example.topics.data.model.ApiResponse;
import com.example.topics.data.model.EmptyResponse;
import com.example.topics.data.model.UserDto;
import com.example.topics.data.remote.ApiClient;
import java.util.HashMap;
import java.util.Map;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class UserRepository extends BaseRepository {
    private final ApiService api;
    private final SessionManager sessionManager;

    public UserRepository(Context context) {
        api = ApiClient.getService(context);
        sessionManager = SessionManager.getInstance(context);
    }

    public void getMe(RepositoryCallback<UserDto> callback) {
        api.getMe().enqueue(new Callback<ApiResponse<UserDto>>() {
            @Override
            public void onResponse(Call<ApiResponse<UserDto>> call, Response<ApiResponse<UserDto>> response) {
                dispatchIfSuccessful(response, new RepositoryCallback<UserDto>() {
                    @Override
                    public void onSuccess(UserDto user) {
                        sessionManager.saveUser(user);
                        callback.onSuccess(user);
                    }

                    @Override
                    public void onError(String message) {
                        callback.onError(message);
                    }
                });
            }

            @Override
            public void onFailure(Call<ApiResponse<UserDto>> call, Throwable t) {
                callback.onError(networkMessage(t));
            }
        });
    }

    public void updateName(String name, RepositoryCallback<UserDto> callback) {
        Map<String, String> body = new HashMap<>();
        body.put("name", name);
        api.updateName(body).enqueue(refreshUserAfterMutation(callback));
    }

    public void updateEmail(String email, String password, RepositoryCallback<UserDto> callback) {
        Map<String, String> body = new HashMap<>();
        body.put("email", email);
        body.put("password", password);
        api.updateEmail(body).enqueue(refreshUserAfterMutation(callback));
    }

    public void updatePassword(String currentPassword, String newPassword, String confirmPassword, RepositoryCallback<EmptyResponse> callback) {
        Map<String, String> body = new HashMap<>();
        body.put("currentPassword", currentPassword);
        body.put("newPassword", newPassword);
        body.put("confirmPassword", confirmPassword);
        api.updatePassword(body).enqueue(emptyCallback(callback));
    }

    public void deleteAccount(String password, RepositoryCallback<EmptyResponse> callback) {
        Map<String, String> body = new HashMap<>();
        body.put("password", password);
        body.put("confirmText", "DELETE");
        api.deleteAccount(body).enqueue(emptyCallback(callback));
    }

    private Callback<ApiResponse<EmptyResponse>> refreshUserAfterMutation(RepositoryCallback<UserDto> callback) {
        return new Callback<ApiResponse<EmptyResponse>>() {
            @Override
            public void onResponse(Call<ApiResponse<EmptyResponse>> call, Response<ApiResponse<EmptyResponse>> response) {
                dispatchIfSuccessful(response, new RepositoryCallback<EmptyResponse>() {
                    @Override
                    public void onSuccess(EmptyResponse value) {
                        getMe(callback);
                    }

                    @Override
                    public void onError(String message) {
                        callback.onError(message);
                    }
                });
            }

            @Override
            public void onFailure(Call<ApiResponse<EmptyResponse>> call, Throwable t) {
                callback.onError(networkMessage(t));
            }
        };
    }

    private Callback<ApiResponse<EmptyResponse>> emptyCallback(RepositoryCallback<EmptyResponse> callback) {
        return new Callback<ApiResponse<EmptyResponse>>() {
            @Override
            public void onResponse(Call<ApiResponse<EmptyResponse>> call, Response<ApiResponse<EmptyResponse>> response) {
                dispatchIfSuccessful(response, callback);
            }

            @Override
            public void onFailure(Call<ApiResponse<EmptyResponse>> call, Throwable t) {
                callback.onError(networkMessage(t));
            }
        };
    }
}
