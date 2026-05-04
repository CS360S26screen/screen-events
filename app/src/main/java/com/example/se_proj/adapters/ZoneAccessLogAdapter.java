package com.example.se_proj.adapters;

import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.se_proj.R;
import com.example.se_proj.models.ZoneAccessLog;
import com.google.android.material.chip.Chip;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

/**
 * Adapter for wing/lab access attempt logs shown on the admin wing access management screen.
 *
 * <p>ALLOWED results are shown with green chips; DENIED results with red chips.</p>
 *
 * <p><b>Design pattern:</b> RecyclerView Adapter/ViewHolder.
 * The adapter maps domain models to row views and delegates user actions
 * back to the owning Activity through callback interfaces.</p>
 */
public class ZoneAccessLogAdapter extends RecyclerView.Adapter<ZoneAccessLogAdapter.ViewHolder> {

    private List<ZoneAccessLog> logs;
    /**
     * Creates a new adapter component instance.
     * @param logs the initial data set to display
     */
    public ZoneAccessLogAdapter(List<ZoneAccessLog> logs) {
        this.logs = logs;
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView tvZoneLogRoll;
        final TextView tvZoneLogWing;
        final TextView tvZoneLogReason;
        final Chip chipZoneResult;
        final TextView tvZoneLogTime;
        /**
         * Creates a new adapter component instance.
         * @param view the value for {@code view}
         */
        public ViewHolder(@NonNull View view) {
            super(view);
            tvZoneLogRoll = view.findViewById(R.id.tvZoneLogRoll);
            tvZoneLogWing = view.findViewById(R.id.tvZoneLogWing);
            tvZoneLogReason = view.findViewById(R.id.tvZoneLogReason);
            chipZoneResult = view.findViewById(R.id.chipZoneResult);
            tvZoneLogTime = view.findViewById(R.id.tvZoneLogTime);
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
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_zone_access_log, parent, false);
        return new ViewHolder(view);
    }
    /**
     * Binds the model at the given position into the supplied ViewHolder.
     * @param holder the ViewHolder whose views should be populated
     * @param position the adapter position to bind
     */
    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ZoneAccessLog log = logs.get(position);
        holder.tvZoneLogRoll.setText(log.getStudentRollNo());
        holder.tvZoneLogWing.setText(log.getWing());
        holder.tvZoneLogReason.setText(log.getReason());
        holder.chipZoneResult.setText(log.getResult());

        boolean allowed = "ALLOWED".equals(log.getResult());
        int bgColor = allowed ? R.color.status_approved_bg : R.color.status_denied_bg;
        int textColor = allowed ? R.color.status_approved_text : R.color.status_denied_text;
        holder.chipZoneResult.setChipBackgroundColor(
                ColorStateList.valueOf(ContextCompat.getColor(holder.itemView.getContext(), bgColor)));
        holder.chipZoneResult.setTextColor(
                ContextCompat.getColor(holder.itemView.getContext(), textColor));

        if (log.getTimestamp() != null) {
            String formatted = new SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault())
                    .format(log.getTimestamp().toDate());
            holder.tvZoneLogTime.setText(formatted);
        } else {
            holder.tvZoneLogTime.setText("");
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
     * Replaces the adapter data set and refreshes the visible list.
     * @param newLogs the replacement data set to display
     */
    public void updateData(List<ZoneAccessLog> newLogs) {
        logs = newLogs;
        notifyDataSetChanged();
    }
}
