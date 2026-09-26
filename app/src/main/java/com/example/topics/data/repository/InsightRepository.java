package com.example.topics.data.repository;

import android.content.Context;
import com.example.topics.ApiService;
import com.example.topics.data.model.ApiResponse;
import com.example.topics.data.model.LifeMapData;
import com.example.topics.data.remote.ApiClient;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class InsightRepository extends BaseRepository {
    private final ApiService api;

    public InsightRepository(Context context) {
        api = ApiClient.getService(context);
    }

    public void getLifeMapInsight(RepositoryCallback<LifeMapData> callback) {
        api.getLifeMapInsight().enqueue(new Callback<ApiResponse<LifeMapData>>() {
            @Override
            public void onResponse(Call<ApiResponse<LifeMapData>> call, Response<ApiResponse<LifeMapData>> response) {
                ApiResponse<LifeMapData> body = response.body();

                if (!response.isSuccessful() || body == null || !body.success) {
                    callback.onError(extractMessage(response, body));
                    return;
                }

                callback.onSuccess(body.data);
            }

            @Override
            public void onFailure(Call<ApiResponse<LifeMapData>> call, Throwable t) {
                callback.onError(networkMessage(t));
            }
        });
    }
}