package com.example.topics;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.example.topics.data.local.SessionManager;
import com.example.topics.data.remote.ApiClient;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * 即時更新，對齊網頁版 App.jsx 的 EventSource(/diaries/events)：
 * diary:created / diary:updated / diary:deleted / social:changed / notifications:changed / content:changed。
 * 有畫面在監聽時才連線，全部離開就斷線；斷線後 3 秒自動重連。
 */
public final class RealtimeEvents {
    public interface Listener {
        void onEvent(String event, String data);
    }

    private static RealtimeEvents instance;

    private final Context appContext;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final List<Listener> listeners = new ArrayList<>();
    private final OkHttpClient client = new OkHttpClient.Builder()
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .retryOnConnectionFailure(true)
            .build();
    private Call currentCall;
    private Thread worker;
    private volatile boolean running;

    private RealtimeEvents(Context context) {
        appContext = context.getApplicationContext();
    }

    public static synchronized RealtimeEvents getInstance(Context context) {
        if (instance == null) instance = new RealtimeEvents(context);
        return instance;
    }

    public void addListener(Listener listener) {
        if (listener == null || listeners.contains(listener)) return;
        listeners.add(listener);
        start();
    }

    public void removeListener(Listener listener) {
        listeners.remove(listener);
        if (listeners.isEmpty()) {
            // 換頁時新畫面會先加入監聽，稍等一下再決定是否真的斷線
            main.postDelayed(() -> {
                if (listeners.isEmpty()) stop();
            }, 1500);
        }
    }

    private synchronized void start() {
        if (running) return;
        running = true;
        worker = new Thread(this::loop, "adrift-realtime");
        worker.setDaemon(true);
        worker.start();
    }

    private synchronized void stop() {
        running = false;
        if (currentCall != null) currentCall.cancel();
        if (worker != null) worker.interrupt();
        worker = null;
    }

    private void loop() {
        while (running) {
            String token = SessionManager.getInstance(appContext).getToken();
            if (token == null || token.isEmpty()) {
                sleepQuietly(5000);
                continue;
            }
            try {
                String url = ApiClient.BASE_URL + "diaries/events?token=" + URLEncoder.encode(token, "UTF-8");
                Request request = new Request.Builder().url(url).header("Accept", "text/event-stream").build();
                Call call = client.newCall(request);
                synchronized (this) {
                    currentCall = call;
                }
                try (Response response = call.execute()) {
                    if (!response.isSuccessful() || response.body() == null) {
                        sleepQuietly(response.code() == 401 ? 30000 : 5000);
                        continue;
                    }
                    readStream(new BufferedReader(new InputStreamReader(response.body().byteStream(), StandardCharsets.UTF_8)));
                }
            } catch (Exception ignored) {
                // 網路中斷或被取消，稍後重連
            }
            if (running) sleepQuietly(3000);
        }
    }

    private void readStream(BufferedReader reader) throws Exception {
        String event = "message";
        StringBuilder data = new StringBuilder();
        String line;
        while (running && (line = reader.readLine()) != null) {
            if (line.isEmpty()) {
                if (data.length() > 0 || !"message".equals(event)) dispatch(event, data.toString());
                event = "message";
                data.setLength(0);
            } else if (line.startsWith("event:")) {
                event = line.substring(6).trim();
            } else if (line.startsWith("data:")) {
                if (data.length() > 0) data.append('\n');
                data.append(line.substring(5).trim());
            }
        }
    }

    private void dispatch(String event, String data) {
        main.post(() -> {
            for (Listener listener : new ArrayList<>(listeners)) {
                try {
                    listener.onEvent(event, data);
                } catch (Exception ignored) {
                }
            }
        });
    }

    private void sleepQuietly(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {
        }
    }
}
