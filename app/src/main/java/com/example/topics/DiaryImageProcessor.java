package com.example.topics;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 日記照片處理，照 iOS 版 AddDiaryView 的做法：
 * 讀取照片 → 轉正方向 → 最長邊縮到 1600px → 轉成 JPEG（品質 82）→ 確認不超過 5MB 再上傳。
 * 這樣手機拍的大照片也能上傳，不會因為原檔太大被擋。
 */
public final class DiaryImageProcessor {
    public interface Callback {
        void onReady(Uri uri);
        void onError(String message);
    }

    public static final int MAX_SIDE = 1600;
    public static final int JPEG_QUALITY = 82;
    public static final long MAX_BYTES = 5L * 1024 * 1024;
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private DiaryImageProcessor() {}

    /** 相機拍照用的暫存檔位置（FileProvider 的 content:// 網址） */
    public static Uri createCameraUri(Context context) throws IOException {
        File file = new File(imageDir(context), "camera-" + System.currentTimeMillis() + ".jpg");
        if (!file.exists() && !file.createNewFile()) throw new IOException("無法建立照片檔案");
        return FileProvider.getUriForFile(context, context.getPackageName() + ".fileprovider", file);
    }

    public static void prepare(Context context, Uri source, Callback callback) {
        Context app = context.getApplicationContext();
        EXECUTOR.execute(() -> {
            try {
                Uri result = prepareBlocking(app, source);
                MAIN.post(() -> callback.onReady(result));
            } catch (IllegalStateException e) {
                MAIN.post(() -> callback.onError(e.getMessage()));
            } catch (Exception | OutOfMemoryError e) {
                MAIN.post(() -> callback.onError("照片載入失敗，請重新選擇"));
            }
        });
    }

    private static Uri prepareBlocking(Context context, Uri source) throws IOException {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream input = context.getContentResolver().openInputStream(source)) {
            if (input == null) throw new IllegalStateException("無法讀取這張照片");
            BitmapFactory.decodeStream(input, null, bounds);
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw new IllegalStateException("無法讀取這張照片");

        // 先用 inSampleSize 粗略縮小，避免大照片佔太多記憶體
        BitmapFactory.Options options = new BitmapFactory.Options();
        int longest = Math.max(bounds.outWidth, bounds.outHeight);
        int sample = 1;
        while (longest / (sample * 2) >= MAX_SIDE) sample *= 2;
        options.inSampleSize = sample;
        Bitmap bitmap;
        try (InputStream input = context.getContentResolver().openInputStream(source)) {
            bitmap = BitmapFactory.decodeStream(input, null, options);
        }
        if (bitmap == null) throw new IllegalStateException("無法讀取這張照片");

        // 依照片的 EXIF 方向轉正（手機直拍的照片常常是橫的）
        int rotation = 0;
        try (InputStream input = context.getContentResolver().openInputStream(source)) {
            if (input != null) {
                int orientation = new ExifInterface(input).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
                if (orientation == ExifInterface.ORIENTATION_ROTATE_90) rotation = 90;
                else if (orientation == ExifInterface.ORIENTATION_ROTATE_180) rotation = 180;
                else if (orientation == ExifInterface.ORIENTATION_ROTATE_270) rotation = 270;
            }
        } catch (Exception ignored) {
        }

        float scale = Math.min(1f, (float) MAX_SIDE / Math.max(bitmap.getWidth(), bitmap.getHeight()));
        if (scale < 1f || rotation != 0) {
            Matrix matrix = new Matrix();
            matrix.postScale(scale, scale);
            if (rotation != 0) matrix.postRotate(rotation);
            Bitmap transformed = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
            if (transformed != bitmap) bitmap.recycle();
            bitmap = transformed;
        }

        File output = new File(imageDir(context), "diary-" + System.currentTimeMillis() + ".jpg");
        try (FileOutputStream stream = new FileOutputStream(output)) {
            bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, stream);
        } finally {
            bitmap.recycle();
        }
        if (output.length() > MAX_BYTES) {
            //noinspection ResultOfMethodCallIgnored
            output.delete();
            throw new IllegalStateException("圖片不可超過 5 MB");
        }
        return FileProvider.getUriForFile(context, context.getPackageName() + ".fileprovider", output);
    }

    private static File imageDir(Context context) {
        File dir = new File(context.getCacheDir(), "images");
        if (!dir.exists()) //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
        return dir;
    }
}
