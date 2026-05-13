package com.example.topics;

import java.util.List;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Query;

public interface ApiService {
    // 1. 註冊與登入
    @POST("api/register")
    Call<Void> register(@Body User user);

    @POST("api/login")
    Call<LoginResponse> login(@Body User user);

    // 2. 日記操作：存檔與讀取
    @POST("api/saveDiary")
    Call<Void> saveDiary(@Body DiaryEntry entry);

    @GET("api/getDiaries")
    Call<List<DiaryEntry>> getUserDiaries(@Query("userId") String userId);
}