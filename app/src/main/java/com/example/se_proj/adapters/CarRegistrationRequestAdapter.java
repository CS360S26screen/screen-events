package com.example.se_proj.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.se_proj.R;
import com.example.se_proj.models.CarRegistrationRequest;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;

import java.util.List;

/**
 * RecyclerView adapter for the admin view of pending car registration requests.
 *
 * <p>Displays the license plate, car model, owner name/roll, and role chip.
 * Approve and Reject callbacks are delegated to the host Activity.</p>
 */
public class CarRegistrationRequestAdapter
        extends RecyclerView.Adapter<CarRegistrationRequestAdapter.ViewHolder> {

    /** Callback fired when admin taps Approve. */
    public interface OnApproveClickListener {
        void onApprove(CarRegistrationRequest request);
    }

    /** Callback fired when admin taps Reject. */
    public interface OnRejectClickListener {
        void onReject(CarRegistrationRequest request);
    }

    private List<CarRegistrationRequest> requests;
    private final OnApproveClickListener onApprove;
    private final OnRejectClickListener onReject;

    public CarRegistrationRequestAdapter(List<CarRegistrationRequest> requests,
                                         OnApproveClickListener onApprove,
                                         OnRejectClickListener onReject) {
        this.requests = requests;
        this.onApprove = onApprove;
        this.onReject = onReject;
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView tvAdminCarPlate;
        final TextView tvAdminCarModel;
        final TextView tvAdminCarOwner;
        final Chip chipOwnerRole;
        final MaterialButton btnApproveCarRequest;
        final MaterialButton btnRejectCarRequest;

        public ViewHolder(@NonNull View view) {
            super(view);
            tvAdminCarPlate = view.findViewById(R.id.tvAdminCarPlate);
            tvAdminCarModel = view.findViewById(R.id.tvAdminCarModel);
            tvAdminCarOwner = view.findViewById(R.id.tvAdminCarOwner);
            chipOwnerRole = view.findViewById(R.id.chipOwnerRole);
            btnApproveCarRequest = view.findViewById(R.id.btnApproveCarRequest);
            btnRejectCarRequest = view.findViewById(R.id.btnRejectCarRequest);
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_car_reg_request_admin, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        CarRegistrationRequest req = requests.get(position);
        holder.tvAdminCarPlate.setText(req.getLicensePlate());
        holder.tvAdminCarModel.setText(req.getCarModel());
        holder.tvAdminCarOwner.setText(req.getOwnerName() + " — " + req.getOwnerRollNo());
        holder.chipOwnerRole.setText(req.getOwnerRole());
        holder.btnApproveCarRequest.setOnClickListener(v -> onApprove.onApprove(req));
        holder.btnRejectCarRequest.setOnClickListener(v -> onReject.onReject(req));
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
