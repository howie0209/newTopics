package com.example.topics.ui.common;

import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.example.topics.data.remote.ImageUrlResolver;

public class AvatarBinder {
    private AvatarBinder() {}

    public static void bind(ImageView imageView, TextView fallbackView, String name, String avatarPath) {
        String resolved = ImageUrlResolver.resolve(avatarPath);
        String initial = initial(name);
        fallbackView.setText(initial);
        if (resolved.isEmpty()) {
            Glide.with(imageView).clear(imageView);
            imageView.setVisibility(View.GONE);
            fallbackView.setVisibility(View.VISIBLE);
            return;
        }
        fallbackView.setVisibility(View.GONE);
        imageView.setVisibility(View.VISIBLE);
        Glide.with(imageView)
                .load(resolved)
                .centerCrop()
                .into(imageView);
    }

    private static String initial(String name) {
        if (name == null) return "A";
        String trimmed = name.trim();
        if (trimmed.isEmpty()) return "A";
        return trimmed.substring(0, 1).toUpperCase();
    }
}
