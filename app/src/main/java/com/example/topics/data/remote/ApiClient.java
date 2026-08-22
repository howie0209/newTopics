package com.example.topics.data.remote;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import com.example.topics.ApiService;
import com.example.topics.LoginActivity;
import com.example.topics.data.local.SessionManager;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class ApiClient {
    public static final String BASE_URL = "https://adrifttw.com/api/";

    private static Retrofit retrofit;
    private static AuthExpiredHandler authExpiredHandler;

    public static synchronized ApiService getService(Context context) {
        if (retrofit == null) {
            Context appContext = context.getApplicationContext();
            SessionManager sessionManager = SessionManager.getInstance(appContext);

            OkHttpClient client = new OkHttpClient.Builder()
                    .addInterceptor(createAuthInterceptor(appContext, sessionManager))
                    .build();

            retrofit = new Retrofit.Builder()
                    .baseUrl(BASE_URL)
                    .client(client)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();
        }

        return retrofit.create(ApiService.class);
    }

    public static void setAuthExpiredHandler(AuthExpiredHandler handler) {
        authExpiredHandler = handler;
    }

    private static Interceptor createAuthInterceptor(Context context, SessionManager sessionManager) {
        return chain -> {
            Request original = chain.request();
            Request.Builder builder = original.newBuilder();
            String token = sessionManager.getToken();
            boolean hadToken = token != null && !token.isEmpty();

            if (hadToken) {
                builder.header("Authorization", "Bearer " + token);
            }

            Response response = chain.proceed(builder.build());

            if (response.code() == 401 && hadToken) {
                sessionManager.clear();
                if (authExpiredHandler != null) {
                    new Handler(Looper.getMainLooper()).post(authExpiredHandler::onAuthExpired);
                } else {
                    Intent intent = new Intent(context, LoginActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    new Handler(Looper.getMainLooper()).post(() -> context.startActivity(intent));
                }
            }

            return response;
        };
    }
}
