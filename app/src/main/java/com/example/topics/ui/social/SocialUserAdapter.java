package com.example.topics.ui.social;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.topics.R;
import com.example.topics.ui.common.AvatarBinder;

import java.util.ArrayList;
import java.util.List;

public class SocialUserAdapter extends RecyclerView.Adapter<SocialUserAdapter.ViewHolder> {
    public interface Listener {
        void onPrimary(SocialUserItem item);
        void onSecondary(SocialUserItem item);
        void onOpenProfile(SocialUserItem item);
    }

    private final Listener listener;
    private final List<SocialUserItem> items = new ArrayList<>();

    public SocialUserAdapter(Listener listener) {
        this.listener = listener;
    }

    public void submit(List<SocialUserItem> nextItems) {
        items.clear();
        if (nextItems != null) items.addAll(nextItems);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_social_user, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        SocialUserItem item = items.get(position);
        holder.name.setText(item.displayName());
        String meta = item.displayCode();
        if (item.meta != null && !item.meta.isEmpty()) {
            meta = meta.isEmpty() ? item.meta : meta + " · " + item.meta;
        }
        holder.meta.setText(meta);
        AvatarBinder.bind(holder.avatarImage, holder.avatarFallback, item.name, item.avatar);
        holder.primary.setText(item.primaryText == null ? "查看" : item.primaryText);
        holder.primary.setEnabled(item.primaryEnabled);
        holder.secondary.setText(item.secondaryText == null ? "" : item.secondaryText);
        holder.secondary.setVisibility(item.showSecondary ? View.VISIBLE : View.GONE);
        holder.itemView.setOnClickListener(v -> listener.onOpenProfile(item));
        holder.primary.setOnClickListener(v -> listener.onPrimary(item));
        holder.secondary.setOnClickListener(v -> listener.onSecondary(item));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final ImageView avatarImage;
        final TextView avatarFallback;
        final TextView name;
        final TextView meta;
        final Button primary;
        final Button secondary;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            avatarImage = itemView.findViewById(R.id.iv_social_avatar);
            avatarFallback = itemView.findViewById(R.id.tv_social_avatar);
            name = itemView.findViewById(R.id.tv_social_name);
            meta = itemView.findViewById(R.id.tv_social_meta);
            primary = itemView.findViewById(R.id.btn_social_primary);
            secondary = itemView.findViewById(R.id.btn_social_secondary);
        }
    }
}
