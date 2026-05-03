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
import com.example.se_proj.models.CarRegistrationRequest;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;

import java.util.List;

/**
 * RecyclerView adapter showing a student's or faculty's pending car registration requests.
 *
 * <p>Each item shows the plate, car model, a status chip (PENDING / APPROVED / REJECTED),
 * and a "Cancel" button that is visible only for pending items.</p>
 */
public class PendingCarRequestAdapter
        extends RecyclerView.Adapter<PendingCarRequestAdapter.ViewHolder> {

    /** Callback fired when the user taps "Cancel" on a pending request. */
    public interface OnCancelClickListener {
        void onCancel(CarRegistrationRequest request);
    }

    private List<CarRegistrationRequest> requests;
    private final OnCancelClickListener onCancelClick;

    public PendingCarRequestAdapter(List<CarRegistrationRequest> requests,
                                    OnCancelClickListener onCancelClick) {
        this.requests = requests;
        this.onCancelClick = onCancelClick;
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView tvPendingPlate;
        final TextView tvPendingModel;
        final Chip chipPendingStatus;
        final MaterialButton btnCancelRequest;

        public ViewHolder(@NonNull View view) {
            super(view);
            tvPendingPlate = view.findViewById(R.id.tvPendingPlate);
            tvPendingModel = view.findViewById(R.id.tvPendingModel);
            chipPendingStatus = view.findViewById(R.id.chipPendingStatus);
            btnCancelRequest = view.findViewById(R.id.btnCancelRequest);
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_pending_car_request, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        CarRegistrationRequest req = requests.get(position);
        holder.tvPendingPlate.setText(req.getLicensePlate());
        holder.tvPendingModel.setText(req.getCarModel());

        int bgRes, textRes;
        switch (req.getStatus()) {
            case "approved":
                bgRes = R.color.status_approved_bg;
                textRes = R.color.status_approved_text;
                holder.chipPendingStatus.setText("APPROVED");
                holder.btnCancelRequest.setVisibility(View.GONE);
                break;
            case "rejected":
                bgRes = R.color.status_denied_bg;
                textRes = R.color.status_denied_text;
                holder.chipPendingStatus.setText("REJECTED");
                holder.btnCancelRequest.setVisibility(View.GONE);
                break;
            default:
                bgRes = R.color.status_pending_bg;
                textRes = R.color.status_pending_text;
                holder.chipPendingStatus.setText("PENDING");
                holder.btnCancelRequest.setVisibility(View.VISIBLE);
                break;
        }

        holder.chipPendingStatus.setChipBackgroundColor(
                ColorStateList.valueOf(ContextCompat.getColor(holder.itemView.getContext(), bgRes)));
        holder.chipPendingStatus.setTextColor(
                ContextCompat.getColor(holder.itemView.getContext(), textRes));

        holder.btnCancelRequest.setOnClickListener(v -> onCancelClick.onCancel(req));
    }

    @Override
    public int getItemCount() {
        return requests.size();
    }

    public void updateData(List<CarRegistrationRequest> newRequests) {
        requests = newRequests;
        notifyDataSetChanged();
    }
}
