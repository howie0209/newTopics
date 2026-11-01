package com.example.topics;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.text.Editable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.style.BackgroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.text.style.TypefaceSpan;
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
import com.example.topics.data.model.ChatEvidence;
import com.example.topics.data.model.ChatRelatedDiary;
import com.example.topics.data.model.ChatTurn;
import com.example.topics.data.repository.InsightRepository;
import com.google.gson.JsonElement;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MemoryChatController {
    private static final int MAX_HISTORY = 12;
    private static final String[] SUGGESTIONS = {
            "回顧我最近的心情",
            "哪個地方留下最多回憶？",
            "我最近有哪些快樂的時刻？",
            "幫我回顧上個月的生活"
    };
    private static final Pattern LIST = Pattern.compile("^\\s*(?:([-*])|\\d+[.)])\\s+(.+)");
    private static final Pattern HEADING = Pattern.compile("^#{1,3}\\s+(.+)");
    private static final Pattern INLINE = Pattern.compile("(\\*\\*[^*]+\\*\\*|`[^`]+`)");

    private enum Status { PENDING, COMPLETE, ERROR }

    private static class Message {
        final boolean fromUser;
        String text;
        String question;
        Status status = Status.COMPLETE;
        String error;
        List<ChatRelatedDiary> refs;
        ChatEvidence evidence;
        String resultStatus;
        JsonElement queryContext;

        Message(boolean fromUser, String text) {
            this.fromUser = fromUser;
            this.text = text;
        }
    }

    private final Activity activity;
    private final InsightRepository repository;
    private final RecyclerView recyclerView;
    private final View introView;
    private final EditText input;
    private final Button sendButton;
    private final TextView newChatButton;
    private final TextView latestButton;
    private final List<Message> messages = new ArrayList<>();
    private final MessageAdapter adapter = new MessageAdapter();

    private int conversationId;
    private boolean sending;
    private boolean destroyed;
    private boolean nearBottom = true;

    public MemoryChatController(Activity activity) {
        this.activity = activity;
        repository = new InsightRepository(activity);
        recyclerView = activity.findViewById(R.id.rv_chat_messages);
        introView = activity.findViewById(R.id.layout_chat_intro);
        input = activity.findViewById(R.id.et_chat_input);
        sendButton = activity.findViewById(R.id.btn_chat_send);
        newChatButton = activity.findViewById(R.id.btn_chat_new);
        latestButton = activity.findViewById(R.id.btn_chat_latest);
        LinearLayout suggestionContainer = activity.findViewById(R.id.container_chat_suggestions);

        recyclerView.setLayoutManager(new LinearLayoutManager(activity));
        recyclerView.setAdapter(adapter);
        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                nearBottom = !rv.canScrollVertically(1);
                if (nearBottom) latestButton.setVisibility(View.GONE);
            }
        });

        for (String suggestion : SUGGESTIONS) {
            View row = LayoutInflater.from(activity).inflate(R.layout.item_chat_suggestion, suggestionContainer, false);
            ((TextView) row.findViewById(R.id.tv_suggestion_label)).setText(suggestion);
            row.setOnClickListener(v -> send(suggestion, null));
            suggestionContainer.addView(row);
        }

        sendButton.setOnClickListener(v -> send(input.getText().toString(), null));
        newChatButton.setOnClickListener(v -> startNewConversation());
        latestButton.setOnClickListener(v -> {
            nearBottom = true;
            scrollToEnd();
            latestButton.setVisibility(View.GONE);
        });
        input.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                updateControls();
            }
        });
        updateControls();
    }

    public void destroy() {
        destroyed = true;
    }

    public void setDraft(String text) {
        input.setText(text);
        input.setSelection(input.getText().length());
        input.requestFocus();
    }

    // ---------- 送出 / 重試 ----------
    private void send(String question, Message retry) {
        String text = question == null ? "" : question.trim();
        if (text.isEmpty() || text.length() > 1000 || sending) return;

        List<Message> previous;
        if (retry != null) {
            int index = messages.indexOf(retry);
            if (index < 0) return;
            previous = new ArrayList<>(messages.subList(0, index));
        } else {
            previous = new ArrayList<>(messages);
        }

        // 後端要求 history 每一筆都是 {role, content}，且只用使用者的問題
        List<ChatTurn> context = new ArrayList<>();
        for (Message item : previous) {
            if (item.fromUser) context.add(new ChatTurn("user", item.text));
        }
        if (retry != null && !context.isEmpty() && context.get(context.size() - 1).content.equals(text)) {
            context.remove(context.size() - 1);
        }
        while (context.size() > MAX_HISTORY) context.remove(0);

        JsonElement queryContext = null;
        for (int i = previous.size() - 1; i >= 0; i--) {
            Message item = previous.get(i);
            if (!item.fromUser && item.queryContext != null) {
                queryContext = item.queryContext;
                break;
            }
        }

        nearBottom = true;
        final Message answer;
        if (retry != null) {
            answer = retry;
            answer.status = Status.PENDING;
            answer.error = null;
            refreshMessage(answer);
        } else {
            addMessage(new Message(true, text));
            answer = new Message(false, "");
            answer.question = text;
            answer.status = Status.PENDING;
            addMessage(answer);
        }
        input.setText("");
        sending = true;
        updateControls();

        final int startedIn = conversationId;
        repository.chat(text, context, queryContext, new InsightRepository.ChatCallback() {
            @Override
            public void onSuccess(ChatData data) {
                if (destroyed || startedIn != conversationId) return;
                sending = false;
                updateControls();

                if (data.answer == null) {
                    answer.status = Status.ERROR;
                    answer.error = "回覆不完整，請重試。";
                } else {
                    answer.status = Status.COMPLETE;
                    answer.text = data.answer;
                    answer.resultStatus = data.status;
                    answer.evidence = data.evidence;
                    if (data.queryContext != null) answer.queryContext = data.queryContext;
                    answer.refs = new ArrayList<>();
                    if (data.relatedDiaries != null) {
                        for (ChatRelatedDiary diary : data.relatedDiaries) {
                            if (diary != null && diary.id != null && !diary.id.isEmpty()
                                    && InsightFormat.parseIso(diary.createdAt) >= 0) {
                                answer.refs.add(diary);
                            }
                        }
                    }
                }
                refreshMessage(answer);
            }

            @Override
            public void onError(String message, ChatData errorData) {
                if (destroyed || startedIn != conversationId) return;
                sending = false;
                updateControls();

                answer.status = Status.ERROR;
                answer.error = message == null || message.isEmpty() ? "暫時無法回答，請重試。" : message;
                if (errorData != null) {
                    if (errorData.evidence != null) answer.evidence = errorData.evidence;
                    if (errorData.queryContext != null) answer.queryContext = errorData.queryContext;
                }
                refreshMessage(answer);
            }
        });
    }

    private void startNewConversation() {
        conversationId++;
        sending = false;
        messages.clear();
        nearBottom = true;
        latestButton.setVisibility(View.GONE);
        adapter.notifyDataSetChanged();
        input.setText("");
        updateControls();
        input.requestFocus();
    }

    // ---------- 清單 ----------
    private void addMessage(Message message) {
        messages.add(message);
        adapter.notifyItemInserted(messages.size() - 1);
        afterChange();
        updateControls();
    }

    private void refreshMessage(Message message) {
        int index = messages.indexOf(message);
        if (index < 0) return;
        adapter.notifyItemChanged(index);
        afterChange();
    }

    private void afterChange() {
        if (nearBottom) {
            scrollToEnd();
            latestButton.setVisibility(View.GONE);
        } else {
            latestButton.setVisibility(View.VISIBLE);
        }
    }

    private void scrollToEnd() {
        if (!messages.isEmpty()) recyclerView.scrollToPosition(messages.size() - 1);
    }

    private void updateControls() {
        boolean hasText = !input.getText().toString().trim().isEmpty();
        sendButton.setEnabled(!sending && hasText);
        boolean canReset = !messages.isEmpty() || !input.getText().toString().isEmpty();
        newChatButton.setEnabled(canReset);
        newChatButton.setAlpha(canReset ? 1f : 0.4f);
        introView.setVisibility(messages.isEmpty() ? View.VISIBLE : View.GONE);
    }

    // ---------- 跳轉 ----------
    private void openDiary(String diaryId) {
        Intent intent = new Intent(activity, MapActivity.class);
        intent.putExtra("open_diary_id", diaryId);
        activity.startActivity(intent);
        activity.finish();
    }

    private void goMap() {
        activity.startActivity(new Intent(activity, MapActivity.class));
        activity.finish();
    }

    // ---------- 格式 ----------
    private static String memoryDate(String iso) {
        long ms = InsightFormat.parseIso(iso);
        if (ms < 0) return "";
        SimpleDateFormat format = new SimpleDateFormat("yyyy年M月d日", Locale.TAIWAN);
        format.setTimeZone(TimeZone.getTimeZone("Asia/Taipei"));
        return format.format(new Date(ms));
    }

    private static CharSequence formatAnswer(String text) {
        SpannableStringBuilder out = new SpannableStringBuilder();
        boolean gap = false;
        int ordered = 0;
        for (String line : String.valueOf(text).split("\n")) {
            if (line.trim().isEmpty()) {
                gap = true;
                ordered = 0;
                continue;
            }
            if (out.length() > 0) {
                out.append("\n");
                if (gap) out.append("\n");
            }
            gap = false;

            Matcher list = LIST.matcher(line);
            Matcher heading = HEADING.matcher(line);
            if (list.find()) {
                if (list.group(1) != null) {
                    ordered = 0;
                    out.append("•  ");
                } else {
                    ordered++;
                    out.append(ordered + ".  ");
                }
                appendInline(out, list.group(2));
            } else if (heading.find()) {
                ordered = 0;
                int start = out.length();
                appendInline(out, heading.group(1));
                out.setSpan(new StyleSpan(Typeface.BOLD), start, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                out.setSpan(new RelativeSizeSpan(1.1f), start, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else {
                ordered = 0;
                appendInline(out, line);
            }
        }
        return out;
    }

    private static void appendInline(SpannableStringBuilder out, String text) {
        Matcher matcher = INLINE.matcher(text);
        int last = 0;
        while (matcher.find()) {
            out.append(text, last, matcher.start());
            String token = matcher.group();
            int start = out.length();
            if (token.startsWith("**")) {
                out.append(token.substring(2, token.length() - 2));
                out.setSpan(new StyleSpan(Typeface.BOLD), start, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else {
                out.append(token.substring(1, token.length() - 1));
                out.setSpan(new TypefaceSpan("monospace"), start, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                out.setSpan(new BackgroundColorSpan(0x33FFFFFF), start, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            last = matcher.end();
        }
        out.append(text, last, text.length());
    }

    // ---------- Adapter ----------
    private class MessageAdapter extends RecyclerView.Adapter<MessageAdapter.Holder> {
        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_chat_message, parent, false);
            return new Holder(view);
        }

        @Override
        public int getItemCount() {
            return messages.size();
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            Message m = messages.get(position);
            float density = activity.getResources().getDisplayMetrics().density;
            int pad = Math.round(12 * density);

            holder.bubble.setVisibility(View.GONE);
            holder.pending.setVisibility(View.GONE);
            holder.error.setVisibility(View.GONE);
            holder.evidence.setVisibility(View.GONE);
            holder.citationsWrap.setVisibility(View.GONE);
            holder.actions.setVisibility(View.GONE);

            LinearLayout.LayoutParams bubbleParams = (LinearLayout.LayoutParams) holder.bubble.getLayoutParams();

            if (m.fromUser) {
                bubbleParams.gravity = Gravity.END;
                holder.bubble.setLayoutParams(bubbleParams);
                holder.bubble.setBackgroundResource(R.drawable.bg_edittext_dark);
                holder.bubble.setPadding(pad, pad, pad, pad);
                holder.bubble.setText(m.text);
                holder.bubble.setVisibility(View.VISIBLE);
                return;
            }

            bubbleParams.gravity = Gravity.START;
            holder.bubble.setLayoutParams(bubbleParams);
            holder.bubble.setBackground(null);
            holder.bubble.setPadding(0, 0, 0, 0);

            if (m.status == Status.PENDING) {
                holder.pending.setVisibility(View.VISIBLE);
            } else if (m.status == Status.ERROR) {
                holder.error.setVisibility(View.VISIBLE);
                holder.errorText.setText(m.error);
                holder.retry.setEnabled(!sending);
                holder.retry.setOnClickListener(v -> send(m.question, m));
            } else {
                holder.bubble.setText(formatAnswer(m.text));
                holder.bubble.setVisibility(View.VISIBLE);
            }

            bindEvidence(holder, m);
            bindCitations(holder, m);
            bindActions(holder, m);
        }

        private void bindEvidence(Holder holder, Message m) {
            ChatEvidence evidence = m.evidence;
            if (evidence == null || evidence.matchedCount <= 0) return;
            StringBuilder builder = new StringBuilder();
            builder.append("本次找到 ").append(evidence.matchedCount).append(" 篇記錄 · 閱讀 ")
                    .append(evidence.sampledCount).append(" 篇內文片段");
            if ("INSUFFICIENT_EVIDENCE".equals(evidence.status)) {
                builder.append("\n現有片段不足以判斷長期變化，仍可回顧有記錄的部分。");
            }
            if (m.status == Status.ERROR) {
                builder.append("\n檢索結果已保留，重新嘗試即可繼續分析。");
            }
            holder.evidence.setText(builder.toString());
            holder.evidence.setVisibility(View.VISIBLE);
        }

        private void bindCitations(Holder holder, Message m) {
            while (holder.citations.getChildCount() > 1) holder.citations.removeViewAt(1);
            if (m.refs == null || m.refs.isEmpty()) return;

            for (ChatRelatedDiary diary : m.refs) {
                View row = LayoutInflater.from(activity).inflate(R.layout.item_chat_citation, holder.citations, false);
                String title = diary.title == null || diary.title.isEmpty() ? "未命名日記" : diary.title;
                String moodType = diary.mood != null ? diary.mood.type : null;
                String place = diary.place == null || diary.place.isEmpty() ? "未命名地點" : diary.place;
                ((TextView) row.findViewById(R.id.tv_citation_title)).setText(title);
                ((TextView) row.findViewById(R.id.tv_citation_meta)).setText(memoryDate(diary.createdAt) + " · " + InsightFormat.moodLabel(moodType));
                ((TextView) row.findViewById(R.id.tv_citation_place)).setText("📍 " + place);
                row.setOnClickListener(v -> openDiary(diary.id));
                holder.citations.addView(row);
            }
            holder.citationsWrap.setVisibility(View.VISIBLE);
        }

        private void bindActions(Holder holder, Message m) {
            if (m.status != Status.COMPLETE || !"empty".equals(m.resultStatus) || m.evidence == null) return;
            String status = m.evidence.status;
            if ("NO_DIARIES".equals(status)) {
                holder.action1.setText("＋ 新增第一篇日記");
                holder.action1.setOnClickListener(v -> goMap());
                holder.action2.setVisibility(View.GONE);
                holder.actions.setVisibility(View.VISIBLE);
            } else if ("NO_MATCHES".equals(status)) {
                holder.action1.setText("調整時間範圍");
                holder.action1.setOnClickListener(v -> setDraft("不限時間，延續剛才的問題"));
                holder.action2.setText("查看我的日記");
                holder.action2.setOnClickListener(v -> goMap());
                holder.action2.setVisibility(View.VISIBLE);
                holder.actions.setVisibility(View.VISIBLE);
            }
        }

        class Holder extends RecyclerView.ViewHolder {
            final TextView bubble, errorText, retry, evidence, action1, action2;
            final View pending, error, citationsWrap, actions;
            final LinearLayout citations;

            Holder(View view) {
                super(view);
                bubble = view.findViewById(R.id.tv_chat_bubble);
                pending = view.findViewById(R.id.row_chat_pending);
                error = view.findViewById(R.id.layout_chat_error);
                errorText = view.findViewById(R.id.tv_chat_error);
                retry = view.findViewById(R.id.btn_chat_retry);
                evidence = view.findViewById(R.id.tv_chat_evidence);
                citationsWrap = view.findViewById(R.id.layout_chat_citations);
                citations = view.findViewById(R.id.container_chat_citations);
                actions = view.findViewById(R.id.layout_chat_actions);
                action1 = view.findViewById(R.id.btn_chat_action_1);
                action2 = view.findViewById(R.id.btn_chat_action_2);
            }
        }
    }
}