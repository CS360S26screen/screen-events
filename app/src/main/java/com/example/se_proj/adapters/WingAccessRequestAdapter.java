package com.example.se_proj.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.se_proj.R;
import com.example.se_proj.models.WingAccessRequest;
import com.google.android.material.chip.Chip;

import java.util.List;

/**
 * Adapter for pending wing access requests shown on the admin wing access management screen.
 *
 * <p><b>Design pattern:</b> RecyclerView Adapter/ViewHolder.
 * The adapter maps domain models to row views and delegates user actions
 * back to the owning Activity through callback interfaces.</p>
 */
public class WingAccessRequestAdapter
        extends RecyclerView.Adapter<WingAccessRequestAdapter.ViewHolder> {

    /**
     * Callback used when an admin approves a wing access request.
     */
    public interface OnApproveClickListener {
        /**
         * Handles approval for the selected wing access request.
         *
         * @param request wing access request selected by the user.
         */
        void onApprove(WingAccessRequest request);
    }

    /**
     * Callback used when an admin rejects a wing access request.
     */
    public interface OnRejectClickListener {
        /**
         * Handles rejection for the selected wing access request.
         *
         * @param request wing access request selected by the user.
         */
        void onReject(WingAccessRequest request);
    }

    private List<WingAccessRequest> requests;
    private final OnApproveClickListener onApproveClick;
    private final OnRejectClickListener onRejectClick;
    /**
     * Creates a new adapter component instance.
     * @param requests the initial data set to display
     * @param onApproveClick the value for {@code onApproveClick}
     * @param onRejectClick the value for {@code onRejectClick}
     */
    public WingAccessRequestAdapter(List<WingAccessRequest> requests,
                                     OnApproveClickListener onApproveClick,
                                     OnRejectClickListener onRejectClick) {
        this.requests = requests;
        this.onApproveClick = onApproveClick;
        this.onRejectClick = onRejectClick;
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView tvWingReqStudentName;
        final TextView tvWingReqRoll;
        final Chip chipWingReqType;
        final TextView tvWingReqWing;
        final TextView tvWingReqReason;
        final Button btnWingApprove;
        final Button btnWingReject;
        /**
         * Creates a new adapter component instance.
         * @param view the value for {@code view}
         */
        public ViewHolder(@NonNull View view) {
            super(view);
            tvWingReqStudentName = view.findViewById(R.id.tvWingReqStudentName);
            tvWingReqRoll = view.findViewById(R.id.tvWingReqRoll);
            chipWingReqType = view.findViewById(R.id.chipWingReqType);
            tvWingReqWing = view.findViewById(R.id.tvWingReqWing);
            tvWingReqReason = view.findViewById(R.id.tvWingReqReason);
            btnWingApprove = view.findViewById(R.id.btnWingApprove);
            btnWingReject = view.findViewById(R.id.btnWingReject);
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
                .inflate(R.layout.item_wing_access_request, parent, false);
        return new ViewHolder(view);
    }
    /**
     * Binds the model at the given position into the supplied ViewHolder.
     * @param holder the ViewHolder whose views should be populated
     * @param position the adapter position to bind
     */
    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        WingAccessRequest request = requests.get(position);
        holder.tvWingReqStudentName.setText(request.getStudentName());
        holder.tvWingReqRoll.setText(request.getStudentRollNo());
        holder.tvWingReqWing.setText(request.getWing());
        holder.chipWingReqType.setText(request.getRequesterType().toUpperCase());
        String reason = request.getReason();
        if (reason != null && !reason.isEmpty()) {
            holder.tvWingReqReason.setVisibility(View.VISIBLE);
            holder.tvWingReqReason.setText("Reason: " + reason);
        } else {
            holder.tvWingReqReason.setVisibility(View.GONE);
        }

        holder.btnWingApprove.setOnClickListener(v -> onApproveClick.onApprove(request));
        holder.btnWingReject.setOnClickListener(v -> onRejectClick.onReject(request));
    }
    /**
     * Returns the number of rows currently managed by this adapter.
     * @return the current number of rows
     */
    @Override
    public int getItemCount() {
        return requests.size();
    }
    /**
     * Replaces the adapter data set and refreshes the visible list.
     * @param newRequests the replacement data set to display
     */
    public void updateData(List<WingAccessRequest> newRequests) {
        requests = newRequests;
        notifyDataSetChanged();
    }
}
