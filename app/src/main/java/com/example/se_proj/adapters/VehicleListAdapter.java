package com.example.se_proj.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.se_proj.R;
import com.example.se_proj.models.RegisteredVehicle;
import com.google.android.material.chip.Chip;

import java.util.List;

/**
 * Adapter for a user's admin-approved vehicle list in the "My Cars" tab.
 *
 * <p>Shows each car's plate, model, and on-campus chip. The Remove button is hidden
 * by default since approved vehicles can only be removed by admin; pass
 * {@code showDelete=true} in the constructor to re-enable it for contexts that allow
 * direct removal (e.g. admin panel).</p>
 *
 * <p><b>Design pattern:</b> RecyclerView Adapter/ViewHolder.
 * The adapter maps domain models to row views and delegates user actions
 * back to the owning Activity through callback interfaces.</p>
 */
public class VehicleListAdapter extends RecyclerView.Adapter<VehicleListAdapter.ViewHolder> {

    /**
     * Callback used when a vehicle row exposes a delete/remove action.
     */
    public interface OnDeleteClickListener {
        /**
         * Handles deletion for the selected vehicle.
         *
         * @param vehicle registered vehicle selected by the user.
         */
        void onDelete(RegisteredVehicle vehicle);
    }

    private List<RegisteredVehicle> vehicles;
    private final boolean showDelete;
    private final OnDeleteClickListener onDeleteClick;

    /** Constructs the adapter with the Remove button hidden (read-only mode). */
    public VehicleListAdapter(List<RegisteredVehicle> vehicles,
                               OnDeleteClickListener onDeleteClick) {
        this(vehicles, false, onDeleteClick);
    }

    /**
     * @param showDelete pass {@code true} to show the Remove button on each item
     */
    public VehicleListAdapter(List<RegisteredVehicle> vehicles, boolean showDelete,
                               OnDeleteClickListener onDeleteClick) {
        this.vehicles = vehicles;
        this.showDelete = showDelete;
        this.onDeleteClick = onDeleteClick;
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView tvLicensePlate;
        final TextView tvCarModel;
        final Chip chipOnCampus;
        final Button btnDeleteVehicle;
        /**
         * Creates a new adapter component instance.
         * @param view the value for {@code view}
         */
        public ViewHolder(@NonNull View view) {
            super(view);
            tvLicensePlate = view.findViewById(R.id.tvLicensePlate);
            tvCarModel = view.findViewById(R.id.tvCarModel);
            chipOnCampus = view.findViewById(R.id.chipOnCampus);
            btnDeleteVehicle = view.findViewById(R.id.btnDeleteVehicle);
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
                .inflate(R.layout.item_registered_vehicle, parent, false);
        return new ViewHolder(view);
    }
    /**
     * Binds the model at the given position into the supplied ViewHolder.
     * @param holder the ViewHolder whose views should be populated
     * @param position the adapter position to bind
     */
    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        RegisteredVehicle vehicle = vehicles.get(position);
        holder.tvLicensePlate.setText(vehicle.getLicensePlate());
        holder.tvCarModel.setText(vehicle.getCarModel());

        if (vehicle.isOnCampus()) {
            holder.chipOnCampus.setVisibility(View.VISIBLE);
            holder.chipOnCampus.setText("ON CAMPUS");
        } else {
            holder.chipOnCampus.setVisibility(View.GONE);
        }

        if (showDelete) {
            holder.btnDeleteVehicle.setVisibility(View.VISIBLE);
            holder.btnDeleteVehicle.setOnClickListener(v -> onDeleteClick.onDelete(vehicle));
        } else {
            holder.btnDeleteVehicle.setVisibility(View.GONE);
        }
    }
    /**
     * Returns the number of rows currently managed by this adapter.
     * @return the current number of rows
     */
    @Override
    public int getItemCount() {
        return vehicles.size();
    }
    /**
     * Replaces the adapter data set and refreshes the visible list.
     * @param newVehicles the replacement data set to display
     */
    public void updateData(List<RegisteredVehicle> newVehicles) {
        vehicles = newVehicles;
        notifyDataSetChanged();
    }
}
