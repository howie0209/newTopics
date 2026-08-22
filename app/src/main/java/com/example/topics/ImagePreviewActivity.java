package com.example.topics;

import android.os.Bundle;
import android.widget.ImageButton;
import android.widget.ImageView;

import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.example.topics.data.remote.ImageUrlResolver;

public class ImagePreviewActivity extends AppCompatActivity {
    public static final String EXTRA_IMAGE_URL = "image_url";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_image_preview);

        ImageView imageView = findViewById(R.id.iv_fullscreen_image);
        ImageButton closeButton = findViewById(R.id.btn_close_preview);
        String imageUrl = ImageUrlResolver.resolve(getIntent().getStringExtra(EXTRA_IMAGE_URL));

        Glide.with(this)
                .load(imageUrl)
                .error(R.drawable.bg_image_placeholder)
                .into(imageView);

        closeButton.setOnClickListener(v -> finish());
    }
}
