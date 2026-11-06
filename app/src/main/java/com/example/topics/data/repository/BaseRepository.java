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
import org.json.JSONObject;

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
        try {
            if (response.errorBody() != null) {
                String raw = response.errorBody().string();
                String message = new JSONObject(raw).optString("message", "");
                if (!message.isEmpty()) return message;
            }
        } catch (Exception ignored) {
        }
        // 後端沒給訊息時，換成看得懂的中文（照 iOS 版 localizedError）
        int code = response.code();
        if (code == 401) return "登入狀態已失效，請重新登入";
        if (code == 403) return "你沒有權限進行這個操作";
        if (code == 404) return "找不到這筆資料，可能已被刪除";
        if (code == 413) return "檔案太大，請換一張較小的照片";
        if (code == 429) return "操作太頻繁，請稍後再試";
        if (code >= 500) return "伺服器暫時無法回應，請稍後再試";
        return "暫時無法完成，請稍後再試";
    }

    // 網路錯誤一律顯示中文，不把系統的英文錯誤直接丟給使用者（照 iOS 版 localizedError）
    protected String networkMessage(Throwable throwable) {
        if (throwable instanceof java.net.SocketTimeoutException) return "連線逾時，請檢查網路後再試";
        if (throwable instanceof java.net.UnknownHostException
                || throwable instanceof java.net.ConnectException
                || throwable instanceof javax.net.ssl.SSLException
                || throwable instanceof java.io.IOException) {
            String message = throwable.getMessage();
            if (message != null && message.matches(".*[\\u4e00-\\u9fff].*")) return message;
            return "網路連線失敗，請稍後再試";
        }
        String message = throwable.getMessage();
        if (message != null && message.matches(".*[\\u4e00-\\u9fff].*")) return message;
        return "網路連線失敗，請稍後再試";
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
