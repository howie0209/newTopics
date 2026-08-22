package com.example.topics;

import com.example.topics.data.model.ApiResponse;
import com.example.topics.data.model.AuthResponse;
import com.example.topics.data.model.DiaryDetailData;
import com.example.topics.data.model.DiaryDto;
import com.example.topics.data.model.DiaryListData;
import com.example.topics.data.model.EmptyResponse;
import com.example.topics.data.model.FriendDto;
import com.example.topics.data.model.FriendProfileDto;
import com.example.topics.data.model.FriendRequestBody;
import com.example.topics.data.model.LoginRequest;
import com.example.topics.data.model.RegisterRequest;
import com.example.topics.data.model.ReactionUpdateData;
import com.example.topics.data.model.SearchUserResult;
import com.example.topics.data.model.UserDto;
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

public interface ApiService {

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
}
