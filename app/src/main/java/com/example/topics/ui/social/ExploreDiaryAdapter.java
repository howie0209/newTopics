package com.example.topics.ui.social;

import android.content.res.ColorStateList;
import android.graphics.Color;
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
import com.example.topics.DiaryTime;
import com.example.topics.PlaceNameResolver;
import com.example.topics.R;
import com.example.topics.data.mapper.DiaryMapper;
import com.example.topics.data.model.DiaryDto;
import com.example.topics.data.model.ReactionDto;
import com.example.topics.data.model.ReactionUpdateData;
import com.example.topics.data.model.UserDto;
import com.example.topics.data.remote.ImageUrlResolver;
import com.example.topics.ui.common.AvatarBinder;

import java.util.ArrayList;
import java.util.List;

/**
 * 動態牆清單，對齊網頁版 FeedPage：作者、時間與可見性、標題內文、照片、地點與心情、三個共鳴按鈕、查看記憶。
 * 最後一列是「載入更多記憶」。
 */
public class ExploreDiaryAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    public interface Listener {
        void onReact(DiaryDto diary, String type, int position);
        void onImage(DiaryDto diary);
        void onAuthor(DiaryDto diary);
        void onOpen(DiaryDto diary);
        void onLoadMore();
    }

    private static final int TYPE_DIARY = 0;
    private static final int TYPE_MORE = 1;

    private final Listener listener;
    private final List<DiaryDto> diaries = new ArrayList<>();
    private String currentUserId = "";
    private boolean hasMore;
    private boolean loadingMore;

    public ExploreDiaryAdapter(Listener listener) {
        this.listener = listener;
    }

    public void setCurrentUserId(String userId) {
        currentUserId = userId == null ? "" : userId;
    }

    public void submit(List<DiaryDto> nextDiaries, boolean append, boolean more) {
        if (!append) diaries.clear();
        if (nextDiaries != null) {
            for (DiaryDto diary : nextDiaries) {
                if (!contains(diary.getId())) diaries.add(diary);
            }
        }
        hasMore = more;
        loadingMore = false;
        notifyDataSetChanged();
    }

    public void setLoadingMore(boolean loading) {
        loadingMore = loading;
        if (hasMore) notifyItemChanged(diaries.size());
    }

    public int diaryCount() {
        return diaries.size();
    }

    public void removeDiary(String diaryId) {
        for (int i = 0; i < diaries.size(); i++) {
            if (diaries.get(i).getId().equals(diaryId)) {
                diaries.remove(i);
                notifyItemRemoved(i);
                return;
            }
        }
    }

    public void applyReaction(int position, ReactionUpdateData data) {
        if (position < 0 || position >= diaries.size() || data == null) return;
        DiaryDto diary = diaries.get(position);
        diary.reactions = data.getReactions();
        diary.userReaction = data.userReaction == null ? "" : data.userReaction;
        notifyItemChanged(position);
    }

    // 先在畫面上更新數字，等後端回來再校正（跟網頁版一樣的樂觀更新）
    public void applyOptimisticReaction(int position, String type) {
        if (position < 0 || position >= diaries.size()) return;
        DiaryDto diary = diaries.get(position);
        ReactionDto counts = diary.getReactions();
        String current = diary.userReaction == null ? "" : diary.userReaction;
        if (!current.isEmpty()) adjust(counts, current, -1);
        String next = current.equals(type) ? "" : type;
        if (!next.isEmpty()) adjust(counts, next, 1);
        diary.userReaction = next;
        notifyItemChanged(position);
    }

    private static void adjust(ReactionDto counts, String type, int delta) {
        if ("understand".equals(type)) counts.understand = Math.max(0, counts.understand + delta);
        else if ("hug".equals(type)) counts.hug = Math.max(0, counts.hug + delta);
        else if ("relate".equals(type)) counts.relate = Math.max(0, counts.relate + delta);
    }

    private boolean contains(String id) {
        for (DiaryDto diary : diaries) {
            if (diary.getId().equals(id)) return true;
        }
        return false;
    }

    @Override
    public int getItemViewType(int position) {
        return position < diaries.size() ? TYPE_DIARY : TYPE_MORE;
    }

    @Override
    public int getItemCount() {
        return diaries.size() + (hasMore ? 1 : 0);
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_MORE) {
            return new MoreHolder(inflater.inflate(R.layout.item_feed_more, parent, false));
        }
        return new ViewHolder(inflater.inflate(R.layout.item_explore_diary, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder raw, int position) {
        if (raw instanceof MoreHolder) {
            Button button = ((MoreHolder) raw).button;
            button.setEnabled(!loadingMore);
            button.setText(loadingMore ? "正在讀取…" : "載入更多記憶");
            button.setOnClickListener(v -> listener.onLoadMore());
            return;
        }
        ViewHolder holder = (ViewHolder) raw;
        DiaryDto diary = diaries.get(position);
        UserDto author = diary.getAuthor();
        String authorName = author == null || author.getDisplayName().isEmpty() ? "使用者" : author.getDisplayName();
        String code = author == null || author.getUserCode().isEmpty() ? "unknown" : author.getUserCode();
        holder.author.setText(authorName);
        holder.authorCode.setText("@" + code);
        AvatarBinder.bind(holder.avatarImage, holder.avatarFallback, authorName, author == null ? "" : author.avatar);
        holder.time.setText(DiaryTime.format(diary.createdAt));
        holder.meta.setText("friends".equals(diary.getVisibility()) ? "👥 好友" : "🌊 公開");
        String title = diary.title == null || diary.title.trim().isEmpty() ? "未命名記憶" : diary.title;
        holder.title.setText(title);
        holder.body.setText(diary.getText());

        String imageUrl = diary.getImages().isEmpty() ? "" : ImageUrlResolver.resolve(diary.getImages().get(0));
        if (imageUrl.isEmpty()) {
            Glide.with(holder.image).clear(holder.image);
            holder.image.setVisibility(View.GONE);
        } else {
            holder.image.setVisibility(View.VISIBLE);
            Glide.with(holder.image).load(imageUrl).centerCrop().into(holder.image);
            holder.image.setOnClickListener(v -> listener.onImage(diary));
        }

        String place = diary.location == null || diary.location.placeName == null || diary.location.placeName.trim().isEmpty()
                ? "記憶座標" : PlaceNameResolver.normalizeTaiwan(diary.location.placeName);
        holder.place.setText("📍 " + place);
        String moodType = diary.mood == null ? null : diary.mood.type;
        String mood = moodType == null || moodType.isEmpty() ? "生活片段" : DiaryMapper.moodDisplayLabel(moodType);
        holder.mood.setText(mood + " · " + (diary.mood == null || diary.mood.intensity < 1 ? "–" : String.valueOf(diary.mood.intensity)));

        ReactionDto reactions = diary.getReactions();
        styleReaction(holder.understand, "❤️ 我懂 " + reactions.understand, "understand".equals(diary.userReaction));
        styleReaction(holder.hug, "🤗 抱抱 " + reactions.hug, "hug".equals(diary.userReaction));
        styleReaction(holder.relate, "🌧 有同感 " + reactions.relate, "relate".equals(diary.userReaction));
        holder.understand.setOnClickListener(v -> listener.onReact(diary, "understand", holder.getBindingAdapterPosition()));
        holder.hug.setOnClickListener(v -> listener.onReact(diary, "hug", holder.getBindingAdapterPosition()));
        holder.relate.setOnClickListener(v -> listener.onReact(diary, "relate", holder.getBindingAdapterPosition()));
        holder.authorRoot.setOnClickListener(v -> listener.onAuthor(diary));
        holder.story.setOnClickListener(v -> listener.onOpen(diary));
        holder.place.setOnClickListener(v -> listener.onOpen(diary));
        holder.open.setOnClickListener(v -> listener.onOpen(diary));
    }

    private static void styleReaction(Button button, String text, boolean active) {
        button.setText(text);
        android.content.Context context = button.getContext();
        button.setBackgroundTintList(ColorStateList.valueOf(context.getColor(active ? R.color.ad_reaction_active : R.color.ad_reaction_bg)));
        button.setTextColor(context.getColor(active ? R.color.ad_text : R.color.ad_select_text));
    }

    static class MoreHolder extends RecyclerView.ViewHolder {
        final Button button;

        MoreHolder(@NonNull View itemView) {
            super(itemView);
            button = (Button) itemView;
        }
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final LinearLayout authorRoot;
        final LinearLayout story;
        final ImageView avatarImage;
        final TextView avatarFallback;
        final TextView author;
        final TextView authorCode;
        final TextView time;
        final TextView meta;
        final TextView title;
        final TextView body;
        final ImageView image;
        final TextView place;
        final TextView mood;
        final Button understand;
        final Button hug;
        final Button relate;
        final TextView open;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            authorRoot = itemView.findViewById(R.id.layout_explore_author);
            story = itemView.findViewById(R.id.layout_explore_story);
            avatarImage = itemView.findViewById(R.id.iv_explore_author_avatar);
            avatarFallback = itemView.findViewById(R.id.tv_explore_author_avatar);
            author = itemView.findViewById(R.id.tv_explore_author);
            authorCode = itemView.findViewById(R.id.tv_explore_author_code);
            time = itemView.findViewById(R.id.tv_explore_time);
            meta = itemView.findViewById(R.id.tv_explore_meta);
            title = itemView.findViewById(R.id.tv_explore_title);
            body = itemView.findViewById(R.id.tv_explore_body);
            image = itemView.findViewById(R.id.iv_explore_image);
            place = itemView.findViewById(R.id.tv_explore_place);
            mood = itemView.findViewById(R.id.tv_explore_mood);
            understand = itemView.findViewById(R.id.btn_react_understand);
            hug = itemView.findViewById(R.id.btn_react_hug);
            relate = itemView.findViewById(R.id.btn_react_relate);
            open = itemView.findViewById(R.id.btn_explore_open);
        }
    }
}
