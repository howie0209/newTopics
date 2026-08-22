package com.example.topics.data.remote;

public class ImageUrlResolver {
    private ImageUrlResolver() {}

    public static String resolve(String path) {
        if (path == null) return "";
        String trimmed = path.trim();
        if (trimmed.isEmpty()) return "";
        if (trimmed.startsWith("http://")
                || trimmed.startsWith("https://")
                || trimmed.startsWith("data:")
                || trimmed.startsWith("blob:")) {
            return trimmed;
        }
        String base = ApiClient.BASE_URL.endsWith("/")
                ? ApiClient.BASE_URL.substring(0, ApiClient.BASE_URL.length() - 1)
                : ApiClient.BASE_URL;
        String normalizedPath = trimmed.startsWith("/") ? trimmed : "/" + trimmed;
        return base + normalizedPath;
    }
}
