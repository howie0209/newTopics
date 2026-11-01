package com.example.topics;

import android.app.Activity;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.topics.data.model.ChatData;
import com.example.topics.data.model.ChatRelatedDiary;
import com.example.topics.data.repository.InsightRepository;
import com.example.topics.data.repository.RepositoryCallback;
import com.google.gson.JsonElement;
import java.util.ArrayList;
import java.util.List;

public class MemoryChatController {
    private static final int MAX_HISTORY = 12;

    private final InsightRepository repository;
    private final RecyclerView recyclerView;
    private final TextView emptyView;
    private final EditText input;
    private final Button sendButton;
    private final List<Message> messages = new ArrayList<>();
    private final List<String> askedQuestions = new ArrayList<>();
    private final MessageAdapter adapter = new MessageAdapter();

    private JsonElement queryContext;
    private int conversationId;
    private boolean sending;
    private boolean destroyed;

    public MemoryChatController(Activity activity) {
        repository = new InsightRepository(activity);
        recyclerView = activity.findViewById(R.id.rv_chat_messages);
        emptyView = activity.findViewById(R.id.tv_chat_empty);
        input = activity.findViewById(R.id.et_chat_input);
        sendButton = activity.findViewById(R.id.btn_chat_send);
        View newChatButton = activity.findViewById(R.id.btn_chat_new);

        recyclerView.setLayoutManager(new LinearLayoutManager(activity));
        recyclerView.setAdapter(adapter);

        sendButton.setOnClickListener(v -> send());
        newChatButton.setOnClickListener(v -> startNewConversation());
        updateEmptyState();
    }

    public void destroy() {
        destroyed = true;
    }
    public void setDraft(String text) {
        input.setText(text);
        input.setSelection(input.getText().length());
        input.requestFocus();
    }

    private void send() {
        final String question = input.getText().toString().trim();
        if (question.isEmpty() || sending) return;

        sending = true;
        sendButton.setEnabled(false);
        input.setText("");

        addMessage(new Message(true, question));
        final Message pending = new Message(false, "");
        pending.pending = true;
        addMessage(pending);

        final int startedIn = conversationId;
        int from = Math.max(0, askedQuestions.size() - MAX_HISTORY);
        List<String> history = new ArrayList<>(askedQuestions.subList(from, askedQuestions.size()));

        repository.chat(question, history, queryContext, new RepositoryCallback<ChatData>() {
            @Override
            public void onSuccess(ChatData data) {
                if (destroyed) return;
                sending = false;
                sendButton.setEnabled(true);
                if (startedIn != conversationId) return;

                askedQuestions.add(question);
                if (data.queryContext != null) queryContext = data.queryContext;

                pending.pending = false;
                pending.text = data.answer == null || data.answer.isEmpty() ? "目前沒有可顯示的回覆" : data.answer;
                pending.refs = data.relatedDiaries;
                refreshMessage(pending);
            }

            @Override
            public void onError(String message) {
                if (destroyed) return;
                sending = false;
                sendButton.setEnabled(true);
                if (startedIn != conversationId) return;

                pending.pending = false;
                pending.error = true;
                pending.text = message;
                refreshMessage(pending);
            }
        });
    }

    private void startNewConversation() {
        conversationId++;
        messages.clear();
        askedQuestions.clear();
        queryContext = null;
        adapter.notifyDataSetChanged();
        updateEmptyState();
    }

    private void addMessage(Message message) {
        messages.add(message);
        adapter.notifyItemInserted(messages.size() - 1);
        recyclerView.scrollToPosition(messages.size() - 1);
        updateEmptyState();
    }

    private void refreshMessage(Message message) {
        int index = messages.indexOf(message);
        if (index < 0) return;
        adapter.notifyItemChanged(index);
        recyclerView.scrollToPosition(messages.size() - 1);
    }

    private void updateEmptyState() {
        emptyView.setVisibility(messages.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private static class Message {
        final boolean fromUser;
        String text;
        List<ChatRelatedDiary> refs;
        boolean pending;
        boolean error;

        Message(boolean fromUser, String text) {
            this.fromUser = fromUser;
            this.text = text;
        }
    }

    private class MessageAdapter extends RecyclerView.Adapter<MessageAdapter.Holder> {
        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_chat_message, parent, false);
            return new Holder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            Message message = messages.get(position);
            int gravity = message.fromUser ? Gravity.END : Gravity.START;
            setGravity(holder.bubble, gravity);
            setGravity(holder.refs, gravity);

            holder.bubble.setText(message.pending ? "整理記憶中…" : message.text);
            holder.bubble.setBackgroundResource(message.fromUser ? R.drawable.bg_login_card : R.drawable.bg_edittext_dark);
            holder.bubble.setTextColor(message.error ? 0xFFFFAB6B : 0xFFFFFFFF);

            String refsText = buildRefsText(message);
            holder.refs.setText(refsText);
            holder.refs.setVisibility(refsText.isEmpty() ? View.GONE : View.VISIBLE);
        }

        @Override
        public int getItemCount() {
            return messages.size();
        }

        private void setGravity(View view, int gravity) {
            LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) view.getLayoutParams();
            params.gravity = gravity;
            view.setLayoutParams(params);
        }

        private String buildRefsText(Message message) {
            if (message.fromUser || message.refs == null || message.refs.isEmpty()) return "";
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < message.refs.size(); i++) {
                ChatRelatedDiary diary = message.refs.get(i);
                String title = diary.title == null || diary.title.isEmpty() ? "（未命名日記）" : diary.title;
                String date = diary.createdAt != null && diary.createdAt.length() >= 10 ? diary.createdAt.substring(0, 10) : "";
                if (i > 0) builder.append("\n");
                builder.append("[").append(i + 1).append("] ").append(title);
                if (!date.isEmpty()) builder.append(" · ").append(date);
            }
            return builder.toString();
        }

        class Holder extends RecyclerView.ViewHolder {
            final TextView bubble;
            final TextView refs;

            Holder(View view) {
                super(view);
                bubble = view.findViewById(R.id.tv_chat_bubble);
                refs = view.findViewById(R.id.tv_chat_refs);
            }
        }
    }
}
