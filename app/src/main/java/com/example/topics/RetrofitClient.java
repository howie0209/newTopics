package com.example.topics;

import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class RetrofitClient {
    private static Retrofit retrofit = null;

    // 💡 提示：
    // 1. 如果你使用 Android 內建模擬器，請用 10.0.2.2
    // 2. 如果你是用實體手機連接電腦，請改為電腦的區域網路 IP (例如 192.168.x.x)
    private static final String BASE_URL = "http://10.0.2.2:3000/";

    // 修正方法名稱以符合 RegisterActivity 的呼叫，並回傳 Retrofit 實例
    public static Retrofit getRetrofitInstance() {
        if (retrofit == null) {
            retrofit = new Retrofit.Builder()
                    .baseUrl(BASE_URL)
                    .addConverterFactory(GsonConverterFactory.create()) // 負責將 JSON 轉成 Java 物件
                    .build();
        }
        return retrofit;
    }
}