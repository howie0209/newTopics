package com.example.topics.data.repository;

import android.content.Context;
import com.example.topics.ApiService;
import com.example.topics.data.model.ApiResponse;
import com.example.topics.data.model.ChatData;
import com.example.topics.data.model.ChatRequest;
import com.example.topics.data.model.ChatTurn;
import com.example.topics.data.model.LifeMapData;
import com.example.topics.data.model.OverviewData;
import com.example.topics.data.remote.ApiClient;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.List;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class InsightRepository extends BaseRepository {
    private final ApiService api;

    public interface ChatCallback {
        void onSuccess(ChatData data);

        void onError(String message, ChatData errorData);
    }

    private static class ChatErrorBody {
        String message;
        ChatData data;
    }

    public InsightRepository(Context context) {
        api = ApiClient.getService(context);
    }

    public void getOverview(int days, RepositoryCallback<OverviewData> callback) {
        api.getIntelligenceOverview(days).enqueue(new Callback<ApiResponse<OverviewData>>() {
            @Override
            public void onResponse(Call<ApiResponse<OverviewData>> call, Response<ApiResponse<OverviewData>> response) {
                ApiResponse<OverviewData> body = response.body();
                if (!response.isSuccessful() || body == null || !body.success || body.data == null) {
                    callback.onError(extractMessage(response, body));
                    return;
                }
                callback.onSuccess(body.data);
            }

            @Override
            public void onFailure(Call<ApiResponse<OverviewData>> call, Throwable t) {
                callback.onError(networkMessage(t));
            }
        });
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

    public void chat(String message, List<ChatTurn> history, JsonElement queryContext, ChatCallback callback) {
        api.chatMemory(new ChatRequest(message, history, queryContext)).enqueue(new Callback<ApiResponse<ChatData>>() {
            @Override
            public void onResponse(Call<ApiResponse<ChatData>> call, Response<ApiResponse<ChatData>> response) {
                ApiResponse<ChatData> body = response.body();
                if (response.isSuccessful() && body != null && body.success && body.data != null) {
                    callback.onSuccess(body.data);
                    return;
                }

                String errorMessage = "";
                ChatData errorData = null;
                try {
                    if (response.errorBody() != null) {
                        ChatErrorBody error = new Gson().fromJson(response.errorBody().string(), ChatErrorBody.class);
                        if (error != null) {
                            if (error.message != null) errorMessage = error.message;
                            errorData = error.data;
                        }
                    }
                } catch (Exception ignored) {
                }
                if (errorMessage.isEmpty()) {
                    errorMessage = body != null && body.message != null && !body.message.isEmpty()
                            ? body.message : "Request failed (" + response.code() + ")";
                }
                callback.onError(errorMessage, errorData);
            }

            @Override
            public void onFailure(Call<ApiResponse<ChatData>> call, Throwable t) {
                String text = t instanceof SocketTimeoutException ? "連線逾時，請稍後重試。"
                        : t instanceof IOException ? "連線暫時中斷，請檢查網路後重試。"
                        : networkMessage(t);
                callback.onError(text, null);
            }
        });
    }
}