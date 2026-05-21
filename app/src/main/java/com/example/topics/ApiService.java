package com.example.topics;

import java.util.List;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.DELETE;
import retrofit2.http.Path;
import retrofit2.http.Query;

public interface ApiService {

    // 1. 註冊 (不動)
    @POST("api/register")
    Call<ResponseBody> register(@Body User user);

    // 2. 登入 (不動)
    @POST("api/login")
    Call<LoginResponse> login(@Body User user);

    // 3. 儲存日記 (不動)
    @POST("api/saveDiary")
    Call<ResponseBody> saveDiary(@Body DiaryEntry entry);

    // 4. 讀取日記 (不動)
    @GET("api/getDiaries")
    Call<List<DiaryEntry>> getUserDiaries(@Query("userId") String userId);

    // --- 以下是好友功能相關方法 ---

    // 5. 搜尋使用者
    @GET("users/search/{targetId}")
    Call<User> searchUser(@Path("targetId") String targetId);

    // 6. 發送好友邀請
    @POST("friends/request")
    Call<ResponseBody> sendFriendRequest(@Body FriendRequest request);

    // 7. 取得好友列表 (sent, received, accepted)
    @GET("friends/list")
    Call<List<FriendRecord>> getFriendList(
            @Query("userId") String userId,
            @Query("status") String status
    );

    // 💡 8. 更新好友狀態 (接受)
    @PUT("friends/update/{id}")
    Call<ResponseBody> updateFriendStatus(
            @Path("id") String id,
            @Query("status") String status
    );

    // 💡 9. 刪除好友 / 拒絕邀請 / 收回邀請
    @DELETE("friends/remove/{id}")
    Call<ResponseBody> removeFriend(@Path("id") String id);

    // 💡 宣告 getUserProfile 方法，讓 Java 能透過 ID 跟後端索取資料
    // 🛠️ 修正關鍵：移除原本多餘的 profile/，完美對齊後端的 app.get('/api/user/:id')
    @retrofit2.http.GET("api/user/{id}")
    retrofit2.Call<java.util.Map<String, String>> getUserProfile(@retrofit2.http.Path("id") String userId);

    // 💡 1. 更新使用者名稱
    @retrofit2.http.PUT("api/user/update/username/{id}")
    retrofit2.Call<okhttp3.ResponseBody> updateUsername(
            @retrofit2.http.Path("id") String userId,
            @retrofit2.http.Body java.util.Map<String, String> body
    );

    // 💡 2. 更新 Email
    @retrofit2.http.PUT("api/user/update/email/{id}")
    retrofit2.Call<okhttp3.ResponseBody> updateEmail(
            @retrofit2.http.Path("id") String userId,
            @retrofit2.http.Body java.util.Map<String, String> body
    );

    // 💡 3. 更新密碼
    @retrofit2.http.PUT("api/user/update/password/{id}")
    retrofit2.Call<okhttp3.ResponseBody> updatePassword(
            @retrofit2.http.Path("id") String userId,
            @retrofit2.http.Body java.util.Map<String, String> body
    );

    // 💡 4. 刪除帳號
    @retrofit2.http.DELETE("api/user/delete/{id}")
    retrofit2.Call<okhttp3.ResponseBody> deleteAccount(
            @retrofit2.http.Path("id") String userId
    );
    @GET("api/getDiaryStatistics")
    Call<java.util.Map<String, Integer>> getDiaryStatistics(@Query("userId") String userId);
}