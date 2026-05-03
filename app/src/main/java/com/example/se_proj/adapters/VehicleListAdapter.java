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
 */
public class VehicleListAdapter extends RecyclerView.Adapter<VehicleListAdapter.ViewHolder> {

    public interface OnDeleteClickListener {
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

        public ViewHolder(@NonNull View view) {
            super(view);
            tvLicensePlate = view.findViewById(R.id.tvLicensePlate);
            tvCarModel = view.findViewById(R.id.tvCarModel);
            chipOnCampus = view.findViewById(R.id.chipOnCampus);
            btnDeleteVehicle = view.findViewById(R.id.btnDeleteVehicle);
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_registered_vehicle, parent, false);
        return new ViewHolder(view);
    }

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

    @Override
    public int getItemCount() {
        return vehicles.size();
    }

    public void updateData(List<RegisteredVehicle> newVehicles) {
        vehicles = newVehicles;
        notifyDataSetChanged();
    }
}
