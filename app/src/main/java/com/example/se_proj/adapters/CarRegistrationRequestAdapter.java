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
 *
 * <p><b>Design pattern:</b> RecyclerView Adapter/ViewHolder.
 * The adapter maps domain models to row views and delegates user actions
 * back to the owning Activity through callback interfaces.</p>
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
    /**
     * Creates a new adapter component instance.
     * @param requests the initial data set to display
     * @param onApprove the value for {@code onApprove}
     * @param onReject the value for {@code onReject}
     */
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
        /**
         * Creates a new adapter component instance.
         * @param view the value for {@code view}
         */
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
                .inflate(R.layout.item_car_reg_request_admin, parent, false);
        return new ViewHolder(view);
    }
    /**
     * Binds the model at the given position into the supplied ViewHolder.
     * @param holder the ViewHolder whose views should be populated
     * @param position the adapter position to bind
     */
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
    public void updateData(List<CarRegistrationRequest> newRequests) {
        requests = newRequests;
        notifyDataSetChanged();
    }
}
