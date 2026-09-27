package com.example.topics.data.repository;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.webkit.MimeTypeMap;
import com.example.topics.data.model.ApiResponse;
import java.io.IOException;
import java.io.InputStream;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import okio.BufferedSink;
import retrofit2.Response;

abstract class BaseRepository {
    protected <T> boolean dispatchIfSuccessful(Response<ApiResponse<T>> response, RepositoryCallback<T> callback) {
        ApiResponse<T> body = response.body();
        if (response.isSuccessful() && body != null && body.success) {
            callback.onSuccess(body.data);
            return true;
        }

        callback.onError(extractMessage(response, body));
        return false;
    }

    protected <T> String extractMessage(Response<ApiResponse<T>> response, ApiResponse<T> body) {
        if (body != null && body.message != null && !body.message.isEmpty()) {
            return body.message;
        }
        return "Request failed (" + response.code() + ")";
    }

    protected String networkMessage(Throwable throwable) {
        String message = throwable.getMessage();
        return message == null || message.isEmpty() ? "網路連線失敗，請稍後再試" : message;
    }

    // 🎯 共用：圖片 Uri 轉 MultipartBody.Part，fieldName 例如 "image" 或 "avatar"
    protected MultipartBody.Part imagePart(Context context, Uri uri, String fieldName) {
        if (uri == null) return null;
        Context appContext = context.getApplicationContext();
        String mimeType = appContext.getContentResolver().getType(uri);
        if (mimeType == null || mimeType.isEmpty()) mimeType = "image/jpeg";
        RequestBody requestBody = new UriRequestBody(appContext.getContentResolver(), uri, mimeType, querySize(appContext, uri));
        return MultipartBody.Part.createFormData(fieldName, queryName(appContext, uri, mimeType), requestBody);
    }

    protected RequestBody textPart(String value) {
        return RequestBody.create(MediaType.parse("text/plain"), value == null ? "" : value);
    }

    private String queryName(Context context, Uri uri, String mimeType) {
        String name = null;
        try (Cursor cursor = context.getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) name = cursor.getString(index);
            }
        }
        if (name != null && !name.trim().isEmpty()) return name;
        String extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType);
        if (extension == null || extension.isEmpty()) extension = "jpg";
        return "adrift-upload-" + System.currentTimeMillis() + "." + extension;
    }

    private long querySize(Context context, Uri uri) {
        try (Cursor cursor = context.getContentResolver().query(uri, null, null, null, null)) {
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
