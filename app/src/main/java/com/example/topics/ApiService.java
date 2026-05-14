package com.example.topics;

import java.util.List;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Query;

public interface ApiService {

    // 1. 註冊：傳送包含 username, password, email 的 User 物件
    @POST("api/register")
    Call<ResponseBody> register(@Body User user);

    // 2. 登入：傳送包含 email 與 password 的 User 物件
    // 💡 確保這裡與 LoginActivity 中的 Callback<LoginResponse> 一致，以便解析 userId
    @POST("api/login")
    Call<LoginResponse> login(@Body User user);

    // 3. 儲存日記：傳送包含 userId 的 DiaryEntry 物件，實現帳號綁定
    @POST("api/saveDiary")
    Call<ResponseBody> saveDiary(@Body DiaryEntry entry);

    // 4. 讀取日記：透過 Query 帶入 userId，只抓取該使用者的資料
    @GET("api/getDiaries")
    Call<List<DiaryEntry>> getUserDiaries(@Query("userId") String userId);
}