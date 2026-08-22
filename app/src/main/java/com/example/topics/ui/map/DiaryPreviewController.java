package com.example.topics.ui.map;

import android.app.Activity;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.example.topics.R;
import com.example.topics.data.remote.ImageUrlResolver;

public class DiaryPreviewController {
    public static class PreviewData {
        public String title;
        public String text;
        public String meta;
        public String author;
        public String mood;
        public String reactions;
        public String imageUrl;
    }

    private final Activity activity;
    private final View card;
    private final ImageView image;
    private final TextView title;
    private final TextView text;
    private final TextView meta;
    private final TextView author;
    private final TextView mood;
    private final TextView reactions;
    private final ImageButton close;

    public DiaryPreviewController(Activity activity) {
        this.activity = activity;
        card = activity.findViewById(R.id.diary_preview_card);
        image = activity.findViewById(R.id.iv_preview_diary_image);
        title = activity.findViewById(R.id.tv_preview_title);
        text = activity.findViewById(R.id.tv_preview_text);
        meta = activity.findViewById(R.id.tv_preview_meta);
        author = activity.findViewById(R.id.tv_preview_author);
        mood = activity.findViewById(R.id.tv_preview_mood);
        reactions = activity.findViewById(R.id.tv_preview_reactions);
        close = activity.findViewById(R.id.btn_close_preview_card);
    }

    public void setOnOpenListener(View.OnClickListener listener) {
        if (card != null) card.setOnClickListener(listener);
    }

    public void setOnCloseListener(View.OnClickListener listener) {
        if (close != null) close.setOnClickListener(listener);
    }

    public boolean isShowing() {
        return card != null && card.getVisibility() == View.VISIBLE;
    }

    public void show(PreviewData data, int duration) {
        if (card == null || data == null) return;
        bind(data);
        if (card.getVisibility() != View.VISIBLE) {
            card.setVisibility(View.VISIBLE);
            card.setAlpha(0f);
            card.setTranslationY(card.getResources().getDisplayMetrics().density * 26f);
        }
        card.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(duration)
                .start();
    }

    public void hide(int duration) {
        if (card == null || card.getVisibility() != View.VISIBLE) return;
        card.animate()
                .alpha(0f)
                .translationY(card.getResources().getDisplayMetrics().density * 26f)
                .setDuration(duration)
                .withEndAction(() -> card.setVisibility(View.GONE))
                .start();
    }

    private void bind(PreviewData data) {
        if (title != null) title.setText(safe(data.title, "未命名日記"));
        if (text != null) {
            text.setText(safe(data.text, ""));
            text.setVisibility(data.text == null || data.text.isEmpty() ? View.GONE : View.VISIBLE);
        }
        if (meta != null) meta.setText(safe(data.meta, ""));
        if (author != null) author.setText(safe(data.author, "Adrift 使用者"));
        if (mood != null) mood.setText(safe(data.mood, "其他"));
        if (reactions != null) reactions.setText(safe(data.reactions, ""));
        if (image != null) {
            if (data.imageUrl != null && !data.imageUrl.trim().isEmpty()) {
                image.setVisibility(View.VISIBLE);
                Glide.with(activity)
                        .load(ImageUrlResolver.resolve(data.imageUrl))
                        .placeholder(R.drawable.bg_image_placeholder)
                        .error(R.drawable.bg_image_placeholder)
                        .centerCrop()
                        .into(image);
            } else {
                Glide.with(activity).clear(image);
                image.setVisibility(View.GONE);
            }
        }
    }

    private String safe(String value, String fallback) {
        return value == null || value.isEmpty() ? fallback : value;
    }
}
