package com.example.se_proj.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.se_proj.databinding.ItemFacultyRequestBinding;
import com.example.se_proj.models.VisitorRequest;
import com.example.se_proj.rules.RequestStatus;
import com.example.se_proj.rules.UiFormatUtils;

import java.util.List;

/**
 * Read-only adapter for the student dashboard "My Guests" history list.
 *
 * <p><b>Design pattern:</b> RecyclerView Adapter/ViewHolder.
 * The adapter maps domain models to row views and delegates user actions
 * back to the owning Activity through callback interfaces.</p>
 */
public class StudentGuestLogAdapter extends RecyclerView.Adapter<StudentGuestLogAdapter.ViewHolder> {

    private List<VisitorRequest> requests;

    /**
     * @param requests initial list of the student's guest history
     */
    public StudentGuestLogAdapter(List<VisitorRequest> requests) {
        this.requests = requests;
    }

    /** ViewHolder wrapping view-binding references for a single guest history row. */
    public static class ViewHolder extends RecyclerView.ViewHolder {
        final ItemFacultyRequestBinding binding;
        /**
         * Creates a new adapter component instance.
         * @param binding the value for {@code binding}
         */
        public ViewHolder(@NonNull ItemFacultyRequestBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
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
        ItemFacultyRequestBinding binding = ItemFacultyRequestBinding
                .inflate(LayoutInflater.from(parent.getContext()), parent, false);
        return new ViewHolder(binding);
    }
    /**
     * Binds the model at the given position into the supplied ViewHolder.
     * @param holder the ViewHolder whose views should be populated
     * @param position the adapter position to bind
     */
    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        VisitorRequest request = requests.get(position);
        holder.binding.tvGuestName.setText(request.getGuestName());
        holder.binding.tvVisitInfo.setText(UiFormatUtils.formatFacultyVisitInfo(request));
        holder.binding.chipStatus.setText(RequestStatus.normalize(request.getStatus()).toUpperCase());

        // History view only: hide action buttons.
        holder.binding.btnEdit.setVisibility(View.GONE);
        holder.binding.btnCancel.setVisibility(View.GONE);
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
     * Replaces the adapter data with a new snapshot.
     *
     * @param newRequests updated guest history list
     */
    public void updateData(List<VisitorRequest> newRequests) {
        requests = newRequests;
        notifyDataSetChanged();
    }
}
