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
 */
public class WingAccessRequestAdapter
        extends RecyclerView.Adapter<WingAccessRequestAdapter.ViewHolder> {

    public interface OnApproveClickListener {
        void onApprove(WingAccessRequest request);
    }

    public interface OnRejectClickListener {
        void onReject(WingAccessRequest request);
    }

    private List<WingAccessRequest> requests;
    private final OnApproveClickListener onApproveClick;
    private final OnRejectClickListener onRejectClick;

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

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_wing_access_request, parent, false);
        return new ViewHolder(view);
    }

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

    @Override
    public int getItemCount() {
        return requests.size();
    }

    public void updateData(List<WingAccessRequest> newRequests) {
        requests = newRequests;
        notifyDataSetChanged();
    }
}
