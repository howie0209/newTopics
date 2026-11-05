package com.example.topics;

import com.example.topics.data.model.AvatarUpdateData;
import com.example.topics.data.model.ApiResponse;
import com.example.topics.data.model.AuthResponse;
import com.example.topics.data.model.DiaryDetailData;
import com.example.topics.data.model.DiaryDto;
import com.example.topics.data.model.DiaryListData;
import com.example.topics.data.model.FeedData;
import com.example.topics.data.model.CheckEmailResponse;
import com.example.topics.data.model.MemoryPageData;
import com.example.topics.data.model.PersonDto;
import com.example.topics.data.model.SharedPlacesData;
import com.example.topics.data.model.NotificationListData;
import com.example.topics.data.model.NotificationTargetData;
import com.example.topics.data.model.EmptyResponse;
import com.example.topics.data.model.FriendDto;
import com.example.topics.data.model.FriendProfileDto;
import com.example.topics.data.model.FriendRequestBody;
import com.example.topics.data.model.LoginRequest;
import com.example.topics.data.model.RegisterRequest;
import com.example.topics.data.model.ReactionUpdateData;
import com.example.topics.data.model.SearchUserResult;
import com.example.topics.data.model.UserDto;
import com.example.topics.data.model.AdminDiaryListData;
import com.example.topics.data.model.AdminStatsData;
import com.example.topics.data.model.AdminUserDto;
import com.example.topics.data.model.AdminUserListData;
import retrofit2.http.QueryMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.HTTP;
import retrofit2.http.Multipart;
import retrofit2.http.PATCH;
import retrofit2.http.Part;
import retrofit2.http.PartMap;
import retrofit2.http.POST;
import retrofit2.http.Path;
import retrofit2.http.Query;
import com.example.topics.data.model.ChatData;
import com.example.topics.data.model.ChatRequest;

import com.example.topics.data.model.LifeMapData;
import com.example.topics.data.model.OverviewData;

public interface ApiService {

    // 先確認 Email 是否已註冊，決定走登入還是註冊（對齊網頁版 api.checkEmail）
    @POST("auth/check-email")
    Call<CheckEmailResponse> checkEmail(@Body Map<String, String> body);

    @POST("auth/login")
    Call<ApiResponse<AuthResponse>> login(@Body LoginRequest request);

    @POST("auth/register")
    Call<ApiResponse<AuthResponse>> register(@Body RegisterRequest request);

    @GET("users/me")
    Call<ApiResponse<UserDto>> getMe();

    @PATCH("users/me/name")
    Call<ApiResponse<EmptyResponse>> updateName(@Body Map<String, String> body);

    @PATCH("users/me/email")
    Call<ApiResponse<EmptyResponse>> updateEmail(@Body Map<String, String> body);

    @PATCH("users/me/password")
    Call<ApiResponse<EmptyResponse>> updatePassword(@Body Map<String, String> body);

    @HTTP(method = "DELETE", path = "users/me", hasBody = true)
    Call<ApiResponse<EmptyResponse>> deleteAccount(@Body Map<String, String> body);

    @GET("users/search")
    Call<ApiResponse<SearchUserResult>> searchUser(@Query("userCode") String userCode);

    @GET("diaries")
    Call<ApiResponse<DiaryListData>> getDiaries();

    @GET("diaries")
    Call<ApiResponse<DiaryListData>> getDiariesNearby(
            @Query("lat") double lat,
            @Query("lng") double lng,
            @Query("radius") int radius
    );

    @Multipart
    @POST("diaries")
    Call<ApiResponse<DiaryDetailData>> createDiary(
            @PartMap HashMap<String, RequestBody> fields,
            @Part MultipartBody.Part image
    );

    @PATCH("diaries/{id}")
    Call<ApiResponse<DiaryDetailData>> updateDiary(
            @Path("id") String diaryId,
            @Body HashMap<String, Object> body
    );

    @DELETE("diaries/{id}")
    Call<ApiResponse<EmptyResponse>> deleteDiary(@Path("id") String diaryId);

    // 通知，對齊網頁版 api.getNotifications / readNotification / readAllNotifications / getNotificationTarget
    @GET("notifications")
    Call<ApiResponse<NotificationListData>> getNotifications(@Query("cursor") String cursor);

    @PATCH("notifications/read-all")
    Call<ApiResponse<EmptyResponse>> readAllNotifications();

    @PATCH("notifications/{id}/read")
    Call<ApiResponse<EmptyResponse>> readNotification(@Path("id") String id);

    @GET("notifications/{id}/target")
    Call<ApiResponse<NotificationTargetData>> getNotificationTarget(@Path("id") String id);

    // 動態牆，對齊網頁版 api.getFeed
    @GET("diaries/feed")
    Call<ApiResponse<FeedData>> getFeed(@Query("filter") String filter, @Query("cursor") String cursor);

    @GET("diaries/{id}")
    Call<ApiResponse<DiaryDetailData>> getDiary(@Path("id") String diaryId);

    @GET("diaries/explore")
    Call<ApiResponse<List<DiaryDto>>> getExploreDiaries(
            @Query("lat") double lat,
            @Query("lng") double lng,
            @Query("radius") int radius
    );

    @POST("diaries/{id}/react")
    Call<ApiResponse<ReactionUpdateData>> reactToDiary(
            @Path("id") String diaryId,
            @Body Map<String, String> body
    );

    @GET("friends")
    Call<ApiResponse<List<FriendDto>>> getFriends();

    @POST("friends/request")
    Call<ApiResponse<EmptyResponse>> sendFriendRequest(@Body FriendRequestBody request);

    @GET("friends/requests")
    Call<ApiResponse<List<FriendDto>>> getFriendRequests();

    @GET("friends/requests/sent")
    Call<ApiResponse<List<FriendDto>>> getSentFriendRequests();

    @DELETE("friends/requests/{requestId}/cancel")
    Call<ApiResponse<EmptyResponse>> cancelFriendRequest(@Path("requestId") String requestId);

    @POST("friends/requests/{requestId}/accept")
    Call<ApiResponse<EmptyResponse>> acceptFriendRequest(@Path("requestId") String requestId);

    @POST("friends/requests/{requestId}/reject")
    Call<ApiResponse<EmptyResponse>> rejectFriendRequest(@Path("requestId") String requestId);

    @DELETE("friends/{friendId}")
    Call<ApiResponse<EmptyResponse>> deleteFriend(@Path("friendId") String friendId);

    @GET("friends/{friendId}/profile")
    Call<ApiResponse<FriendProfileDto>> getFriendProfile(@Path("friendId") String friendId);

    // 好友：名稱／ID 搜尋、推薦、可見記憶、共同地點（對齊網頁版 FriendsPage / FriendProfile）
    @GET("friends/search")
    Call<ApiResponse<List<PersonDto>>> searchPeople(@Query("q") String query);

    @GET("friends/recommendations")
    Call<ApiResponse<List<PersonDto>>> getFriendRecommendations(@Query("limit") int limit);

    @GET("friends/{friendId}/memories")
    Call<ApiResponse<MemoryPageData>> getFriendMemories(@Path("friendId") String friendId, @Query("cursor") String cursor);

    @GET("friends/{friendId}/places")
    Call<ApiResponse<SharedPlacesData>> getSharedPlaces(@Path("friendId") String friendId);

    @GET("ai/life-map")
    Call<ApiResponse<LifeMapData>> getLifeMapInsight();
    @Multipart
    @PATCH("users/me/avatar")
    Call<ApiResponse<AvatarUpdateData>> updateAvatar(@Part MultipartBody.Part avatar);

    @DELETE("users/me/avatar")
    Call<ApiResponse<AvatarUpdateData>> deleteAvatar();

    @GET("admin/stats")
    Call<ApiResponse<AdminStatsData>> getAdminStats();

    @GET("admin/users")
    Call<ApiResponse<AdminUserListData>> getAdminUsers(@QueryMap Map<String, String> query);

    @PATCH("admin/users/{id}/role")
    Call<ApiResponse<AdminUserDto>> updateAdminUserRole(@Path("id") String id, @Body Map<String, String> body);

    @DELETE("admin/users/{id}")
    Call<ApiResponse<EmptyResponse>> deleteAdminUser(@Path("id") String id);

    @GET("admin/diaries")
    Call<ApiResponse<AdminDiaryListData>> getAdminDiaries(@QueryMap Map<String, String> query);

    @DELETE("admin/diaries/{id}")
    Call<ApiResponse<EmptyResponse>> deleteAdminDiary(@Path("id") String id);

    @POST("ai/chat")
    Call<ApiResponse<ChatData>> chatMemory(@Body ChatRequest request);
    @GET("ai/overview")
    Call<ApiResponse<OverviewData>> getIntelligenceOverview(@Query("days") int days);
}
