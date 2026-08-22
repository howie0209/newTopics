package com.example.topics.data.repository;

import android.content.Context;
import com.example.topics.ApiService;
import com.example.topics.FriendRecord;
import com.example.topics.data.model.ApiResponse;
import com.example.topics.data.model.EmptyResponse;
import com.example.topics.data.model.FriendDto;
import com.example.topics.data.model.FriendRequestBody;
import com.example.topics.data.model.SearchUserResult;
import com.example.topics.data.model.UserDto;
import com.example.topics.data.remote.ApiClient;
import java.util.ArrayList;
import java.util.List;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class FriendRepository extends BaseRepository {
    private final ApiService api;

    public FriendRepository(Context context) {
        api = ApiClient.getService(context);
    }

    public void searchUser(String userCode, RepositoryCallback<SearchUserResult> callback) {
        api.searchUser(userCode).enqueue(new Callback<ApiResponse<SearchUserResult>>() {
            @Override
            public void onResponse(Call<ApiResponse<SearchUserResult>> call, Response<ApiResponse<SearchUserResult>> response) {
                dispatchIfSuccessful(response, callback);
            }

            @Override
            public void onFailure(Call<ApiResponse<SearchUserResult>> call, Throwable t) {
                callback.onError(networkMessage(t));
            }
        });
    }

    public void sendFriendRequest(String targetUserId, RepositoryCallback<EmptyResponse> callback) {
        api.sendFriendRequest(new FriendRequestBody(targetUserId)).enqueue(emptyCallback(callback));
    }

    public void getFriends(RepositoryCallback<List<FriendRecord>> callback) {
        api.getFriends().enqueue(listCallback(callback, "accepted"));
    }

    public void getReceivedRequests(RepositoryCallback<List<FriendRecord>> callback) {
        api.getFriendRequests().enqueue(listCallback(callback, "received"));
    }

    public void getSentRequests(RepositoryCallback<List<FriendRecord>> callback) {
        api.getSentFriendRequests().enqueue(listCallback(callback, "sent"));
    }

    public void acceptRequest(String requestId, RepositoryCallback<EmptyResponse> callback) {
        api.acceptFriendRequest(requestId).enqueue(emptyCallback(callback));
    }

    public void rejectRequest(String requestId, RepositoryCallback<EmptyResponse> callback) {
        api.rejectFriendRequest(requestId).enqueue(emptyCallback(callback));
    }

    public void cancelRequest(String requestId, RepositoryCallback<EmptyResponse> callback) {
        api.cancelFriendRequest(requestId).enqueue(emptyCallback(callback));
    }

    public void deleteFriend(String friendId, RepositoryCallback<EmptyResponse> callback) {
        api.deleteFriend(friendId).enqueue(emptyCallback(callback));
    }

    private Callback<ApiResponse<List<FriendDto>>> listCallback(RepositoryCallback<List<FriendRecord>> callback, String type) {
        return new Callback<ApiResponse<List<FriendDto>>>() {
            @Override
            public void onResponse(Call<ApiResponse<List<FriendDto>>> call, Response<ApiResponse<List<FriendDto>>> response) {
                ApiResponse<List<FriendDto>> body = response.body();
                if (!response.isSuccessful() || body == null || !body.success) {
                    callback.onError(extractMessage(response, body));
                    return;
                }
                List<FriendRecord> records = new ArrayList<>();
                if (body.data != null) {
                    for (FriendDto item : body.data) {
                        records.add(toRecord(item, type));
                    }
                }
                callback.onSuccess(records);
            }

            @Override
            public void onFailure(Call<ApiResponse<List<FriendDto>>> call, Throwable t) {
                callback.onError(networkMessage(t));
            }
        };
    }

    private Callback<ApiResponse<EmptyResponse>> emptyCallback(RepositoryCallback<EmptyResponse> callback) {
        return new Callback<ApiResponse<EmptyResponse>>() {
            @Override
            public void onResponse(Call<ApiResponse<EmptyResponse>> call, Response<ApiResponse<EmptyResponse>> response) {
                dispatchIfSuccessful(response, callback);
            }

            @Override
            public void onFailure(Call<ApiResponse<EmptyResponse>> call, Throwable t) {
                callback.onError(networkMessage(t));
            }
        };
    }

    private FriendRecord toRecord(FriendDto item, String type) {
        if ("sent".equals(type)) {
            UserDto to = item.to;
            return new FriendRecord(item.requestId, to == null ? "" : to.getDisplayName(), to == null ? "" : to.getId(), "pending", item.createdAt);
        }
        if ("received".equals(type)) {
            UserDto from = item.from;
            return new FriendRecord(item.requestId, from == null ? "" : from.getDisplayName(), from == null ? "" : from.getId(), "pending", item.createdAt);
        }
        return new FriendRecord(item.getId(), item.name, item.getId(), "accepted", item.createdAt);
    }
}
