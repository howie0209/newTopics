package com.example.topics.ui.social;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.topics.R;
import com.example.topics.data.mapper.DiaryMapper;
import com.example.topics.data.model.DiaryDto;
import com.example.topics.data.model.LocationDto;
import com.example.topics.data.model.ReactionDto;
import com.example.topics.data.model.ReactionUpdateData;
import com.example.topics.data.model.UserDto;
import com.example.topics.data.remote.ImageUrlResolver;
import com.example.topics.ui.common.AvatarBinder;

import java.util.ArrayList;
import java.util.List;

public class ExploreDiaryAdapter extends RecyclerView.Adapter<ExploreDiaryAdapter.ViewHolder> {
    public interface Listener {
        void onReact(DiaryDto diary, String type, int position);
        void onImage(DiaryDto diary);
        void onAuthor(DiaryDto diary);
    }

    private final Listener listener;
    private final List<DiaryDto> diaries = new ArrayList<>();

    public ExploreDiaryAdapter(Listener listener) {
        this.listener = listener;
    }

    public void submit(List<DiaryDto> nextDiaries) {
        diaries.clear();
        if (nextDiaries != null) diaries.addAll(nextDiaries);
        notifyDataSetChanged();
    }

    public void applyReaction(int position, ReactionUpdateData data) {
        if (position < 0 || position >= diaries.size() || data == null) return;
        DiaryDto diary = diaries.get(position);
        diary.reactions = data.getReactions();
        diary.userReaction = data.userReaction == null ? "" : data.userReaction;
        notifyItemChanged(position);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_explore_diary, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        DiaryDto diary = diaries.get(position);
        UserDto author = diary.getAuthor();
        String authorName = author == null ? "Adrift 使用者" : author.getDisplayName();
        holder.author.setText(authorName);
        holder.meta.setText(metaFor(diary, author));
        holder.title.setText(diary.getTitle());
        holder.body.setText(diary.getText());
        holder.place.setText(placeFor(diary.location));
        AvatarBinder.bind(holder.avatarImage, holder.avatarFallback, authorName, author == null ? "" : author.avatar);

        String imageUrl = diary.getImages().isEmpty() ? "" : ImageUrlResolver.resolve(diary.getImages().get(0));
        if (imageUrl.isEmpty()) {
            Glide.with(holder.image).clear(holder.image);
            holder.image.setVisibility(View.GONE);
        } else {
            holder.image.setVisibility(View.VISIBLE);
            Glide.with(holder.image).load(imageUrl).centerCrop().into(holder.image);
            holder.image.setOnClickListener(v -> listener.onImage(diary));
        }

        ReactionDto reactions = diary.getReactions();
        holder.understand.setText(label("懂", reactions.understand, "understand".equals(diary.userReaction)));
        holder.hug.setText(label("抱", reactions.hug, "hug".equals(diary.userReaction)));
        holder.relate.setText(label("共", reactions.relate, "relate".equals(diary.userReaction)));
        holder.understand.setOnClickListener(v -> listener.onReact(diary, "understand", holder.getBindingAdapterPosition()));
        holder.hug.setOnClickListener(v -> listener.onReact(diary, "hug", holder.getBindingAdapterPosition()));
        holder.relate.setOnClickListener(v -> listener.onReact(diary, "relate", holder.getBindingAdapterPosition()));
        holder.authorRoot.setOnClickListener(v -> listener.onAuthor(diary));
    }

    @Override
    public int getItemCount() {
        return diaries.size();
    }

    private String label(String label, int count, boolean selected) {
        return selected ? label + " " + count + " ✓" : label + " " + count;
    }

    private String metaFor(DiaryDto diary, UserDto author) {
        String visibility = "public".equals(diary.getVisibility()) ? "公開" : diary.getVisibility();
        String mood = DiaryMapper.moodLabel(diary.getMood().getType());
        return visibility + " · " + mood + " · " + shortDate(diary.createdAt);
    }

    private String placeFor(LocationDto location) {
        if (location == null) return "未標記位置";
        if (location.placeName != null && !location.placeName.isEmpty()) return location.placeName;
        Double lat = location.getLatitude();
        Double lng = location.getLongitude();
        if (lat == null || lng == null) return "未標記位置";
        return String.format("%.5f, %.5f", lat, lng);
    }

    private String shortDate(String value) {
        if (value == null || value.length() < 10) return "";
        return value.substring(0, 10);
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final LinearLayout authorRoot;
        final ImageView avatarImage;
        final TextView avatarFallback;
        final TextView author;
        final TextView meta;
        final TextView title;
        final TextView body;
        final ImageView image;
        final TextView place;
        final Button understand;
        final Button hug;
        final Button relate;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            authorRoot = itemView.findViewById(R.id.layout_explore_author);
            avatarImage = itemView.findViewById(R.id.iv_explore_author_avatar);
            avatarFallback = itemView.findViewById(R.id.tv_explore_author_avatar);
            author = itemView.findViewById(R.id.tv_explore_author);
            meta = itemView.findViewById(R.id.tv_explore_meta);
            title = itemView.findViewById(R.id.tv_explore_title);
            body = itemView.findViewById(R.id.tv_explore_body);
            image = itemView.findViewById(R.id.iv_explore_image);
            place = itemView.findViewById(R.id.tv_explore_place);
            understand = itemView.findViewById(R.id.btn_react_understand);
            hug = itemView.findViewById(R.id.btn_react_hug);
            relate = itemView.findViewById(R.id.btn_react_relate);
        }
    }
}
