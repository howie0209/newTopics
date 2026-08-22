package com.example.topics.data.repository;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.webkit.MimeTypeMap;

import com.example.topics.ApiService;
import com.example.topics.data.model.ApiResponse;
import com.example.topics.data.model.DiaryDetailData;
import com.example.topics.data.model.DiaryDto;
import com.example.topics.data.model.DiaryListData;
import com.example.topics.data.model.EmptyResponse;
import com.example.topics.data.model.ReactionUpdateData;
import com.example.topics.data.remote.ApiClient;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import okio.BufferedSink;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class DiaryRepository extends BaseRepository {
    private final Context appContext;
    private final ApiService api;

    public DiaryRepository(Context context) {
        appContext = context.getApplicationContext();
        api = ApiClient.getService(context);
    }

    public void getDiaries(RepositoryCallback<List<DiaryDto>> callback) {
        api.getDiaries().enqueue(new Callback<ApiResponse<DiaryListData>>() {
            @Override
            public void onResponse(Call<ApiResponse<DiaryListData>> call, Response<ApiResponse<DiaryListData>> response) {
                ApiResponse<DiaryListData> body = response.body();
                if (response.isSuccessful() && body != null && body.success && body.data != null) {
                    callback.onSuccess(body.data.getDiaries());
                    return;
                }
                callback.onError(extractMessage(response, body));
            }

            @Override
            public void onFailure(Call<ApiResponse<DiaryListData>> call, Throwable t) {
                callback.onError(networkMessage(t));
            }
        });
    }

    public void createDiary(
            String title,
            String text,
            String moodType,
            int intensity,
            String visibility,
            double lat,
            double lng,
            String placeName,
            Uri imageUri,
            RepositoryCallback<DiaryDto> callback
    ) {
        HashMap<String, RequestBody> fields = new HashMap<>();
        fields.put("title", textPart(title));
        fields.put("text", textPart(text));
        fields.put("moodType", textPart(moodType));
        fields.put("moodIntensity", textPart(String.valueOf(intensity)));
        fields.put("visibility", textPart(visibility));
        fields.put("lat", textPart(String.valueOf(lat)));
        fields.put("lng", textPart(String.valueOf(lng)));
        fields.put("placeName", textPart(placeName == null ? "" : placeName));
        fields.put("locationAccuracy", textPart("precise"));

        api.createDiary(fields, imagePart(imageUri)).enqueue(new Callback<ApiResponse<DiaryDetailData>>() {
            @Override
            public void onResponse(Call<ApiResponse<DiaryDetailData>> call, Response<ApiResponse<DiaryDetailData>> response) {
                ApiResponse<DiaryDetailData> body = response.body();
                if (response.isSuccessful() && body != null && body.success && body.data != null && body.data.diary != null) {
                    callback.onSuccess(body.data.diary);
                    return;
                }
                callback.onError(extractMessage(response, body));
            }

            @Override
            public void onFailure(Call<ApiResponse<DiaryDetailData>> call, Throwable t) {
                callback.onError(networkMessage(t));
            }
        });
    }

    public void updateDiary(
            String diaryId,
            String title,
            String text,
            String moodType,
            int intensity,
            String visibility,
            double currentLat,
            double currentLng,
            RepositoryCallback<DiaryDto> callback
    ) {
        HashMap<String, Object> body = new HashMap<>();
        body.put("title", title);
        body.put("content", text);
        body.put("moodType", moodType);
        body.put("moodIntensity", intensity);
        body.put("visibility", visibility);

        HashMap<String, Object> currentLocation = new HashMap<>();
        currentLocation.put("lat", currentLat);
        currentLocation.put("lng", currentLng);
        currentLocation.put("accuracyType", "precise");
        body.put("currentLocation", currentLocation);

        api.updateDiary(diaryId, body).enqueue(new Callback<ApiResponse<DiaryDetailData>>() {
            @Override
            public void onResponse(Call<ApiResponse<DiaryDetailData>> call, Response<ApiResponse<DiaryDetailData>> response) {
                ApiResponse<DiaryDetailData> body = response.body();
                if (response.isSuccessful() && body != null && body.success && body.data != null && body.data.diary != null) {
                    callback.onSuccess(body.data.diary);
                    return;
                }
                callback.onError(extractMessage(response, body));
            }

            @Override
            public void onFailure(Call<ApiResponse<DiaryDetailData>> call, Throwable t) {
                callback.onError(networkMessage(t));
            }
        });
    }

    public void deleteDiary(String diaryId, RepositoryCallback<EmptyResponse> callback) {
        api.deleteDiary(diaryId).enqueue(new Callback<ApiResponse<EmptyResponse>>() {
            @Override
            public void onResponse(Call<ApiResponse<EmptyResponse>> call, Response<ApiResponse<EmptyResponse>> response) {
                ApiResponse<EmptyResponse> body = response.body();
                if (response.isSuccessful() && body != null && body.success) {
                    callback.onSuccess(body.data == null ? new EmptyResponse() : body.data);
                    return;
                }
                callback.onError(extractMessage(response, body));
            }

            @Override
            public void onFailure(Call<ApiResponse<EmptyResponse>> call, Throwable t) {
                callback.onError(networkMessage(t));
            }
        });
    }

    public void reactToDiary(String diaryId, String type, RepositoryCallback<ReactionUpdateData> callback) {
        Map<String, String> body = new HashMap<>();
        body.put("type", type);
        api.reactToDiary(diaryId, body).enqueue(new Callback<ApiResponse<ReactionUpdateData>>() {
            @Override
            public void onResponse(Call<ApiResponse<ReactionUpdateData>> call, Response<ApiResponse<ReactionUpdateData>> response) {
                ApiResponse<ReactionUpdateData> body = response.body();
                if (response.isSuccessful() && body != null && body.success && body.data != null) {
                    callback.onSuccess(body.data);
                    return;
                }
                callback.onError(extractMessage(response, body));
            }

            @Override
            public void onFailure(Call<ApiResponse<ReactionUpdateData>> call, Throwable t) {
                callback.onError(networkMessage(t));
            }
        });
    }

    private MultipartBody.Part imagePart(Uri uri) {
        if (uri == null) return null;
        String mimeType = appContext.getContentResolver().getType(uri);
        if (mimeType == null || mimeType.isEmpty()) mimeType = "image/jpeg";
        RequestBody requestBody = new UriRequestBody(appContext.getContentResolver(), uri, mimeType, querySize(uri));
        return MultipartBody.Part.createFormData("image", queryName(uri, mimeType), requestBody);
    }

    private RequestBody textPart(String value) {
        return RequestBody.create(MediaType.parse("text/plain"), value == null ? "" : value);
    }

    private String queryName(Uri uri, String mimeType) {
        String name = null;
        try (Cursor cursor = appContext.getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) name = cursor.getString(index);
            }
        }
        if (name != null && !name.trim().isEmpty()) return name;
        String extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType);
        if (extension == null || extension.isEmpty()) extension = "jpg";
        return "adrift-diary-" + System.currentTimeMillis() + "." + extension;
    }

    private long querySize(Uri uri) {
        try (Cursor cursor = appContext.getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.SIZE);
                if (index >= 0 && !cursor.isNull(index)) return cursor.getLong(index);
            }
        }
        return -1L;
    }

    private static class UriRequestBody extends RequestBody {
        private final ContentResolver resolver;
        private final Uri uri;
        private final String mimeType;
        private final long size;

        UriRequestBody(ContentResolver resolver, Uri uri, String mimeType, long size) {
            this.resolver = resolver;
            this.uri = uri;
            this.mimeType = mimeType;
            this.size = size;
        }

        @Override
        public MediaType contentType() {
            return MediaType.parse(mimeType);
        }

        @Override
        public long contentLength() {
            return size;
        }

        @Override
        public void writeTo(BufferedSink sink) throws IOException {
            try (InputStream input = resolver.openInputStream(uri)) {
                if (input == null) throw new IOException("無法讀取圖片");
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    sink.write(buffer, 0, read);
                }
            }
        }
    }
}
