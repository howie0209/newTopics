package com.example.topics.data.repository;

import android.content.Context;
import com.example.topics.ApiService;
import com.example.topics.data.local.SessionManager;
import com.example.topics.data.model.ApiResponse;
import com.example.topics.data.model.AuthResponse;
import com.example.topics.data.model.CheckEmailResponse;
import com.example.topics.data.model.LoginRequest;
import com.example.topics.data.model.RegisterRequest;
import com.example.topics.data.model.UserDto;
import com.example.topics.data.remote.ApiClient;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class AuthRepository extends BaseRepository {
    private final ApiService api;
    private final SessionManager sessionManager;
    private final UserRepository userRepository;

    public AuthRepository(Context context) {
        api = ApiClient.getService(context);
        sessionManager = SessionManager.getInstance(context);
        userRepository = new UserRepository(context);
    }

    // 回傳 true 代表這個 Email 已有帳號
    public void checkEmail(String email, RepositoryCallback<Boolean> callback) {
        java.util.Map<String, String> body = new java.util.HashMap<>();
        body.put("email", email);
        api.checkEmail(body).enqueue(new Callback<CheckEmailResponse>() {
            @Override
            public void onResponse(Call<CheckEmailResponse> call, Response<CheckEmailResponse> response) {
                CheckEmailResponse result = response.body();
                if (response.isSuccessful() && result != null && result.success) {
                    callback.onSuccess(result.exists);
                    return;
                }
                String message = result != null && result.message != null ? result.message : "";
                if (message.isEmpty()) {
                    try {
                        if (response.errorBody() != null) {
                            message = new org.json.JSONObject(response.errorBody().string()).optString("message", "");
                        }
                    } catch (Exception ignored) {
                    }
                }
                callback.onError(message.isEmpty() ? "無法確認 Email，請稍後再試" : message);
            }

            @Override
            public void onFailure(Call<CheckEmailResponse> call, Throwable t) {
                callback.onError(networkMessage(t));
            }
        });
    }

    public void login(String email, String password, RepositoryCallback<UserDto> callback) {
        api.login(new LoginRequest(email, password)).enqueue(new Callback<ApiResponse<AuthResponse>>() {
            @Override
            public void onResponse(Call<ApiResponse<AuthResponse>> call, Response<ApiResponse<AuthResponse>> response) {
                ApiResponse<AuthResponse> body = response.body();
                AuthResponse auth = body != null && body.data != null ? body.data : null;
                String token = auth != null && auth.token != null ? auth.token : body != null ? body.token : null;

                if (!response.isSuccessful() || body == null || !body.success || token == null || token.isEmpty()) {
                    callback.onError(extractMessage(response, body));
                    return;
                }

                sessionManager.saveToken(token);
                ApiClient.resetAuthRedirectGuard();
                if (auth != null && auth.user != null) {
                    sessionManager.saveUser(auth.user);
                } else if (body.user != null) {
                    sessionManager.saveUser(body.user);
                }

                userRepository.getMe(callback);
            }

            @Override
            public void onFailure(Call<ApiResponse<AuthResponse>> call, Throwable t) {
                callback.onError(networkMessage(t));
            }
        });
    }

    public void register(String name, String email, String password, String userCode, RepositoryCallback<UserDto> callback) {
        api.register(new RegisterRequest(name, email, password, userCode)).enqueue(new Callback<ApiResponse<AuthResponse>>() {
            @Override
            public void onResponse(Call<ApiResponse<AuthResponse>> call, Response<ApiResponse<AuthResponse>> response) {
                ApiResponse<AuthResponse> body = response.body();
                if (!response.isSuccessful() || body == null || !body.success) {
                    callback.onError(extractMessage(response, body));
                    return;
                }

                login(email, password, callback);
            }

            @Override
            public void onFailure(Call<ApiResponse<AuthResponse>> call, Throwable t) {
                callback.onError(networkMessage(t));
            }
        });
    }

    public void restoreSession(RepositoryCallback<UserDto> callback) {
        if (!sessionManager.hasToken()) {
            callback.onError("尚未登入");
            return;
        }
        userRepository.getMe(callback);
    }

    public void logout() {
        sessionManager.clear();
    }
}
