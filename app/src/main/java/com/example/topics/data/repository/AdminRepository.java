package com.example.topics.data.repository;

import android.content.Context;
import com.example.topics.ApiService;
import com.example.topics.data.model.AdminDiaryListData;
import com.example.topics.data.model.AdminStatsData;
import com.example.topics.data.model.AdminUserDto;
import com.example.topics.data.model.AdminUserListData;
import com.example.topics.data.model.ApiResponse;
import com.example.topics.data.model.EmptyResponse;
import com.example.topics.data.remote.ApiClient;
import java.util.HashMap;
import java.util.Map;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class AdminRepository extends BaseRepository {
    private final ApiService api;

    public AdminRepository(Context context) {
        api = ApiClient.getService(context);
    }

    public void getStats(RepositoryCallback<AdminStatsData> callback) {
        api.getAdminStats().enqueue(new Callback<ApiResponse<AdminStatsData>>() {
            @Override
            public void onResponse(Call<ApiResponse<AdminStatsData>> call, Response<ApiResponse<AdminStatsData>> response) {
                dispatchIfSuccessful(response, callback);
            }

            @Override
            public void onFailure(Call<ApiResponse<AdminStatsData>> call, Throwable t) {
                callback.onError(networkMessage(t));
            }
        });
    }

    public void getUsers(String search, String role, int page, int limit, RepositoryCallback<AdminUserListData> callback) {
        Map<String, String> query = new HashMap<>();
        query.put("search", search == null ? "" : search);
        query.put("role", role == null ? "all" : role);
        query.put("page", String.valueOf(page));
        query.put("limit", String.valueOf(limit));

        api.getAdminUsers(query).enqueue(new Callback<ApiResponse<AdminUserListData>>() {
            @Override
            public void onResponse(Call<ApiResponse<AdminUserListData>> call, Response<ApiResponse<AdminUserListData>> response) {
                dispatchIfSuccessful(response, callback);
            }

            @Override
            public void onFailure(Call<ApiResponse<AdminUserListData>> call, Throwable t) {
                callback.onError(networkMessage(t));
            }
        });
    }

    public void updateUserRole(String userId, String role, RepositoryCallback<AdminUserDto> callback) {
        Map<String, String> body = new HashMap<>();
        body.put("role", role);
        api.updateAdminUserRole(userId, body).enqueue(new Callback<ApiResponse<AdminUserDto>>() {
            @Override
            public void onResponse(Call<ApiResponse<AdminUserDto>> call, Response<ApiResponse<AdminUserDto>> response) {
                dispatchIfSuccessful(response, callback);
            }

            @Override
            public void onFailure(Call<ApiResponse<AdminUserDto>> call, Throwable t) {
                callback.onError(networkMessage(t));
            }
        });
    }

    public void deleteUser(String userId, RepositoryCallback<EmptyResponse> callback) {
        api.deleteAdminUser(userId).enqueue(new Callback<ApiResponse<EmptyResponse>>() {
            @Override
            public void onResponse(Call<ApiResponse<EmptyResponse>> call, Response<ApiResponse<EmptyResponse>> response) {
                ApiResponse<EmptyResponse> body = response.body();
                if (response.isSuccessful() && body != null && body.success) {
                    callback.onSuccess(body.data == null ? new EmptyResponse() : body.data);
                    return;
                }
                callback.onError(extractMessage(response, body));
            }

            @Override
            public void onFailure(Call<ApiResponse<EmptyResponse>> call, Throwable t) {
                callback.onError(networkMessage(t));
            }
        });
    }

    public void getDiaries(String search, String visibility, String mood, int page, int limit, RepositoryCallback<AdminDiaryListData> callback) {
        Map<String, String> query = new HashMap<>();
        query.put("search", search == null ? "" : search);
        query.put("visibility", visibility == null ? "all" : visibility);
        query.put("mood", mood == null ? "all" : mood);
        query.put("page", String.valueOf(page));
        query.put("limit", String.valueOf(limit));

        api.getAdminDiaries(query).enqueue(new Callback<ApiResponse<AdminDiaryListData>>() {
            @Override
            public void onResponse(Call<ApiResponse<AdminDiaryListData>> call, Response<ApiResponse<AdminDiaryListData>> response) {
                dispatchIfSuccessful(response, callback);
            }

            @Override
            public void onFailure(Call<ApiResponse<AdminDiaryListData>> call, Throwable t) {
                callback.onError(networkMessage(t));
            }
        });
    }

    public void deleteDiary(String diaryId, RepositoryCallback<EmptyResponse> callback) {
        api.deleteAdminDiary(diaryId).enqueue(new Callback<ApiResponse<EmptyResponse>>() {
            @Override
            public void onResponse(Call<ApiResponse<EmptyResponse>> call, Response<ApiResponse<EmptyResponse>> response) {
                ApiResponse<EmptyResponse> body = response.body();
                if (response.isSuccessful() && body != null && body.success) {
                    callback.onSuccess(body.data == null ? new EmptyResponse() : body.data);
                    return;
                }
                callback.onError(extractMessage(response, body));
            }

            @Override
            public void onFailure(Call<ApiResponse<EmptyResponse>> call, Throwable t) {
                callback.onError(networkMessage(t));
            }
        });
    }
}
