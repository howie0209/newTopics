package com.example.topics.data.remote;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import com.example.topics.ApiService;
import com.example.topics.LoginActivity;
import com.example.topics.data.local.SessionManager;
import java.util.concurrent.atomic.AtomicBoolean;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import java.util.concurrent.TimeUnit;

public class ApiClient {
    public static final String BASE_URL = "https://adrifttw.com/api/";

    private static Retrofit retrofit;
    private static AuthExpiredHandler authExpiredHandler;
    private static final AtomicBoolean authRedirectInProgress = new AtomicBoolean(false);

    public static synchronized ApiService getService(Context context) {
        if (retrofit == null) {
            Context appContext = context.getApplicationContext();
            SessionManager sessionManager = SessionManager.getInstance(appContext);

            // 等待時間：一般請求 15 秒；檢查登入狀態 8 秒（照 iOS 版）；上傳 30 秒；AI 才給 65 秒
            OkHttpClient client = new OkHttpClient.Builder()
                    .addInterceptor(createTimeoutInterceptor())
                    .addInterceptor(createAuthInterceptor(appContext, sessionManager))
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(15, TimeUnit.SECONDS)
                    .writeTimeout(30, TimeUnit.SECONDS)
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

    public static void resetAuthRedirectGuard() {
        authRedirectInProgress.set(false);
    }

    private static Interceptor createTimeoutInterceptor() {
        return chain -> {
            Request request = chain.request();
            String path = request.url().encodedPath();
            String method = request.method();
            if (path.contains("/ai/")) {
                return chain.withReadTimeout(65, TimeUnit.SECONDS).proceed(request);
            }
            if ("GET".equals(method) && path.endsWith("/users/me")) {
                return chain.withConnectTimeout(8, TimeUnit.SECONDS).withReadTimeout(8, TimeUnit.SECONDS).proceed(request);
            }
            boolean upload = request.body() != null && request.body().contentType() != null
                    && "multipart".equals(request.body().contentType().type());
            if (upload) {
                return chain.withReadTimeout(30, TimeUnit.SECONDS).proceed(request);
            }
            return chain.proceed(request);
        };
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

            if (response.code() == 401 && hadToken && authRedirectInProgress.compareAndSet(false, true)) {
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
