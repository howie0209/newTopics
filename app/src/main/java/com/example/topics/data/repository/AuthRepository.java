package com.example.topics.data.repository;

import android.content.Context;
import com.example.topics.ApiService;
import com.example.topics.data.local.SessionManager;
import com.example.topics.data.model.ApiResponse;
import com.example.topics.data.model.AuthResponse;
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
