package com.example.se_proj.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.se_proj.R;
import com.example.se_proj.models.BlacklistEntry;
import com.google.android.material.button.MaterialButton;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

public class BlacklistEntryAdapter extends RecyclerView.Adapter<BlacklistEntryAdapter.ViewHolder> {

    public interface OnRemoveClickListener {
        void onRemove(BlacklistEntry entry);
    }

    private final List<BlacklistEntry> entries;
    private final OnRemoveClickListener removeListener;
    private static final SimpleDateFormat DATE_FMT = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());

    public BlacklistEntryAdapter(List<BlacklistEntry> entries, OnRemoveClickListener removeListener) {
        this.entries = entries;
        this.removeListener = removeListener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_blacklist_entry, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder h, int position) {
        BlacklistEntry entry = entries.get(position);

        h.tvEntityName.setText(entry.getEntityName());
        h.tvEntityId.setText(entry.getEntityId());
        h.tvEntityType.setText(entry.getEntityType().toUpperCase(Locale.getDefault()));
        h.tvReason.setText("Reason: " + entry.getReason());

        String banLabel = BlacklistEntry.BAN_TEMPORARY.equals(entry.getBanType())
                ? "Ban: Temporary (7 days)"
                : "Ban: Permanent";
        h.tvBanType.setText(banLabel);

        if (entry.getAddedAt() != null) {
            h.tvAddedAt.setText("Added: " + DATE_FMT.format(entry.getAddedAt().toDate()));
        } else {
            h.tvAddedAt.setText("");
        }

        h.btnRemove.setOnClickListener(v -> removeListener.onRemove(entry));
    }

    @Override
    public int getItemCount() {
        return entries.size();
    }

    public void updateData(List<BlacklistEntry> newEntries) {
        entries.clear();
        entries.addAll(newEntries);
        notifyDataSetChanged();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvEntityName, tvEntityId, tvEntityType, tvReason, tvBanType, tvAddedAt;
        MaterialButton btnRemove;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvEntityName = itemView.findViewById(R.id.tvEntityName);
            tvEntityId   = itemView.findViewById(R.id.tvEntityId);
            tvEntityType = itemView.findViewById(R.id.tvEntityType);
            tvReason     = itemView.findViewById(R.id.tvReason);
            tvBanType    = itemView.findViewById(R.id.tvBanType);
            tvAddedAt    = itemView.findViewById(R.id.tvAddedAt);
            btnRemove    = itemView.findViewById(R.id.btnRemove);
        }
    }
}
