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
 *
 * <p><b>Design pattern:</b> RecyclerView Adapter/ViewHolder.
 * The adapter maps domain models to row views and delegates user actions
 * back to the owning Activity through callback interfaces.</p>
 */
public class ParkingVehicleAdapter extends RecyclerView.Adapter<ParkingVehicleAdapter.ViewHolder> {

    private List<RegisteredVehicle> vehicles;
    /**
     * Creates a new adapter component instance.
     * @param vehicles the initial data set to display
     */
    public ParkingVehicleAdapter(List<RegisteredVehicle> vehicles) {
        this.vehicles = vehicles;
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView tvParkingPlate;
        final TextView tvParkingModel;
        final TextView tvParkingStudentName;
        final TextView tvParkingStudentRoll;
        /**
         * Creates a new adapter component instance.
         * @param view the value for {@code view}
         */
        public ViewHolder(@NonNull View view) {
            super(view);
            tvParkingPlate = view.findViewById(R.id.tvParkingPlate);
            tvParkingModel = view.findViewById(R.id.tvParkingModel);
            tvParkingStudentName = view.findViewById(R.id.tvParkingStudentName);
            tvParkingStudentRoll = view.findViewById(R.id.tvParkingStudentRoll);
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
                .inflate(R.layout.item_parking_vehicle, parent, false);
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
        holder.tvParkingPlate.setText(vehicle.getLicensePlate());
        holder.tvParkingModel.setText(vehicle.getCarModel());
        holder.tvParkingStudentName.setText(vehicle.getStudentName());
        holder.tvParkingStudentRoll.setText(vehicle.getStudentRollNo());
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
