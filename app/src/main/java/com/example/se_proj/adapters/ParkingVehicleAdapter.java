package com.example.se_proj.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.se_proj.R;
import com.example.se_proj.models.RegisteredVehicle;

import java.util.List;

/**
 * Read-only adapter for the live vehicle-on-campus list shown in {@link com.example.se_proj.MainParkingActivity}.
 *
 * <p>Each row shows the vehicle's plate, model, student name, and student roll number.</p>
 */
public class ParkingVehicleAdapter extends RecyclerView.Adapter<ParkingVehicleAdapter.ViewHolder> {

    private List<RegisteredVehicle> vehicles;

    public ParkingVehicleAdapter(List<RegisteredVehicle> vehicles) {
        this.vehicles = vehicles;
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView tvParkingPlate;
        final TextView tvParkingModel;
        final TextView tvParkingStudentName;
        final TextView tvParkingStudentRoll;

        public ViewHolder(@NonNull View view) {
            super(view);
            tvParkingPlate = view.findViewById(R.id.tvParkingPlate);
            tvParkingModel = view.findViewById(R.id.tvParkingModel);
            tvParkingStudentName = view.findViewById(R.id.tvParkingStudentName);
            tvParkingStudentRoll = view.findViewById(R.id.tvParkingStudentRoll);
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_parking_vehicle, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        RegisteredVehicle vehicle = vehicles.get(position);
        holder.tvParkingPlate.setText(vehicle.getLicensePlate());
        holder.tvParkingModel.setText(vehicle.getCarModel());
        holder.tvParkingStudentName.setText(vehicle.getStudentName());
        holder.tvParkingStudentRoll.setText(vehicle.getStudentRollNo());
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
