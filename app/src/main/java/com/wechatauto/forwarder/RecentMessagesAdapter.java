package com.wechatauto.forwarder;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class RecentMessagesAdapter extends RecyclerView.Adapter<RecentMessagesAdapter.VH> {

    private final List<ForwardedMessage> items = new ArrayList<>();
    private final DateFormat df = new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault());

    public void setItems(List<ForwardedMessage> list) {
        items.clear();
        items.addAll(list);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_message, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        ForwardedMessage m = items.get(position);
        String title = m.group ? (m.conversationTitle + " · " + m.sender) : m.conversationTitle;
        holder.title.setText(title);
        holder.body.setText(m.body);
        holder.time.setText(df.format(new Date(m.timestamp)));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        final TextView title;
        final TextView body;
        final TextView time;

        VH(@NonNull View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.tvTitle);
            body = itemView.findViewById(R.id.tvBody);
            time = itemView.findViewById(R.id.tvTime);
        }
    }
}
