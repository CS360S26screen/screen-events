package com.example.se_proj.adapters;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.se_proj.databinding.ItemAuditLogBinding;
import com.example.se_proj.models.AuditLog;
import com.example.se_proj.rules.UiFormatUtils;
import com.google.android.material.card.MaterialCardView;

import java.util.List;

/**
 * RecyclerView adapter for security audit records and computed overstay alerts.
 *
 * <p>Design note: Adapter/ViewHolder pattern with presentation decisions delegated to
 * {@link UiFormatUtils} for consistent timestamp/state formatting.</p>
 *
 * <p>Outstanding issues: hard-coded color values should be replaced with theme resources to
 * support dark mode and centralized design tokens.</p>
 *
 * <p><b>Design pattern:</b> RecyclerView Adapter/ViewHolder.
 * The adapter maps domain models to row views and delegates user actions
 * back to the owning Activity through callback interfaces.</p>
 */
public class AuditLogAdapter extends RecyclerView.Adapter<AuditLogAdapter.ViewHolder> {

    private List<AuditLog> logs;
    private boolean isOverstayView;

    /**
     * @param logs          initial list of audit log entries
     * @param isOverstayView {@code true} to apply overstay visual state to all rows
     */
    public AuditLogAdapter(List<AuditLog> logs, boolean isOverstayView) {
        this.logs = logs;
        this.isOverstayView = isOverstayView;
    }

    /** ViewHolder containing view-binding references for a single audit-log card. */
    public static class ViewHolder extends RecyclerView.ViewHolder {
        final ItemAuditLogBinding binding;
        /**
         * Creates a new adapter component instance.
         * @param binding the value for {@code binding}
         */
        public ViewHolder(@NonNull ItemAuditLogBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
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
        ItemAuditLogBinding binding = ItemAuditLogBinding
                .inflate(LayoutInflater.from(parent.getContext()), parent, false);
        return new ViewHolder(binding);
    }
    /**
     * Binds the model at the given position into the supplied ViewHolder.
     * @param holder the ViewHolder whose views should be populated
     * @param position the adapter position to bind
     */
    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        AuditLog log = logs.get(position);
        holder.binding.tvVisitorName.setText(log.getVisitorName());
        holder.binding.tvCnic.setText("CNIC: " + log.getVisitorCNIC());
        holder.binding.tvAction.setText("Action: " + log.getAction());
        holder.binding.tvHostId.setText("Host ID: " + log.getHostId());
        holder.binding.tvTimestamp.setText(UiFormatUtils.formatAuditTimestamp(log.getTimestamp()));

        if (log.getReason() != null && !log.getReason().isEmpty()) {
            holder.binding.tvReason.setVisibility(View.VISIBLE);
            holder.binding.tvReason.setText("Reason: " + log.getReason());
        } else {
            holder.binding.tvReason.setVisibility(View.GONE);
        }

        MaterialCardView card = (MaterialCardView) holder.binding.getRoot();
        UiFormatUtils.AuditVisualState state =
                UiFormatUtils.resolveAuditVisualState(log.getAction(), isOverstayView);

        if (state == UiFormatUtils.AuditVisualState.OVERSTAYING) {
            card.setCardBackgroundColor(Color.parseColor("#FFCDD2")); // Red tint
        } else if (state == UiFormatUtils.AuditVisualState.ENTRY) {
            card.setCardBackgroundColor(Color.parseColor("#C8E6C9")); // Green tint
        } else if (state == UiFormatUtils.AuditVisualState.DENIED) {
            card.setCardBackgroundColor(Color.parseColor("#FFE0B2")); // Orange tint
        } else {
            card.setCardBackgroundColor(Color.WHITE);
        }
    }
    /**
     * Returns the number of rows currently managed by this adapter.
     * @return the current number of rows
     */
    @Override
    public int getItemCount() {
        return logs.size();
    }

    /**
     * Updates log rows and toggles overstay visual mode for subsequent binds.
     *
     * @param newLogs  updated list of audit log entries
     * @param overstay {@code true} to highlight all rows as overstay events
     */
    public void updateData(List<AuditLog> newLogs, boolean overstay) {
        logs = newLogs;
        isOverstayView = overstay;
        notifyDataSetChanged();
    }
}
