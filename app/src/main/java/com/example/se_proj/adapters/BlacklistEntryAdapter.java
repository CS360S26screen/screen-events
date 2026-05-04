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

/**
 * RecyclerView adapter responsible for presenting row data in the BlacklistEntryAdapter list.
 *
 * <p><b>Design pattern:</b> RecyclerView Adapter/ViewHolder.
 * The adapter maps domain models to row views and delegates user actions
 * back to the owning Activity through callback interfaces.</p>
 */
public class BlacklistEntryAdapter extends RecyclerView.Adapter<BlacklistEntryAdapter.ViewHolder> {

    /**
     * Callback used when an admin chooses to remove a blacklist entry.
     */
    public interface OnRemoveClickListener {
        /**
         * Handles a remove action for the selected entry.
         *
         * @param entry blacklist entry selected by the user.
         */
        void onRemove(BlacklistEntry entry);
    }

    private final List<BlacklistEntry> entries;
    private final OnRemoveClickListener removeListener;
    private static final SimpleDateFormat DATE_FMT = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());
    /**
     * Creates a new adapter component instance.
     * @param entries the initial data set to display
     * @param removeListener the value for {@code removeListener}
     */
    public BlacklistEntryAdapter(List<BlacklistEntry> entries, OnRemoveClickListener removeListener) {
        this.entries = entries;
        this.removeListener = removeListener;
    }
    /**
     * Inflates a row layout and creates its ViewHolder.
     * @param parent the parent view group used to inflate the row layout
     * @param viewType the RecyclerView view type for the requested row
     * @return a ViewHolder for the inflated row view
     */
    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_blacklist_entry, parent, false);
        return new ViewHolder(v);
    }
    /**
     * Binds the model at the given position into the supplied ViewHolder.
     * @param h the ViewHolder whose views should be populated
     * @param position the adapter position to bind
     */
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
    /**
     * Returns the number of rows currently managed by this adapter.
     * @return the current number of rows
     */
    @Override
    public int getItemCount() {
        return entries.size();
    }
    /**
     * Replaces the adapter data set and refreshes the visible list.
     * @param newEntries the replacement data set to display
     */
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
