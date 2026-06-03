package com.synrax.ss.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.button.MaterialButton;
import com.synrax.ss.R;
import com.synrax.ss.data.model.Event;
import java.util.List;

/**
 * EventAdapter - Binds the user's recent events history list inside HomeActivity.
 */
public class EventAdapter extends RecyclerView.Adapter<EventAdapter.EventViewHolder> {

    private final List<Event> eventList;
    private final OnEventClickListener listener;

    public interface OnEventClickListener {
        void onEventClick(Event event);
    }

    public EventAdapter(List<Event> eventList, OnEventClickListener listener) {
        this.eventList = eventList;
        this.listener = listener;
    }

    @NonNull
    @Override
    public EventViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_event, parent, false);
        return new EventViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull EventViewHolder holder, int position) {
        Event event = eventList.get(position);
        holder.bind(event, listener);
    }

    @Override
    public int getItemCount() {
        return eventList.size();
    }

    static class EventViewHolder extends RecyclerView.ViewHolder {
        private final TextView txtEventName;
        private final TextView txtEventCode;
        private final TextView txtEventExpiry;
        private final MaterialButton btnActionView;

        public EventViewHolder(@NonNull View itemView) {
            super(itemView);
            txtEventName = itemView.findViewById(R.id.txt_event_name);
            txtEventCode = itemView.findViewById(R.id.txt_event_code);
            txtEventExpiry = itemView.findViewById(R.id.txt_event_expiry);
            btnActionView = itemView.findViewById(R.id.btn_action_view);
        }

        public void bind(final Event event, final OnEventClickListener listener) {
            txtEventName.setText(event.getName());
            txtEventCode.setText("Room Code: " + event.getId());
            
            String expiry = event.getExpiresAt();
            if (expiry != null && !expiry.isEmpty()) {
                // Formatting display date/time for user readability
                String formatted = expiry.replace("T", " ");
                if (formatted.contains(".")) {
                    formatted = formatted.substring(0, formatted.indexOf("."));
                }
                txtEventExpiry.setText("Expires: " + formatted);
            } else {
                txtEventExpiry.setText("Expiry: N/A");
            }

            View.OnClickListener clickListener = v -> {
                if (listener != null) {
                    listener.onEventClick(event);
                }
            };
            btnActionView.setOnClickListener(clickListener);
            itemView.setOnClickListener(clickListener);
        }
    }
}
