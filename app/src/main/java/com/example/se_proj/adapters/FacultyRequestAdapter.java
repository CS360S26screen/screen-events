package com.example.se_proj.adapters;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.se_proj.databinding.ItemFacultyRequestBinding;
import com.example.se_proj.models.VisitorRequest;
import com.example.se_proj.rules.RequestStatus;
import com.example.se_proj.rules.UiFormatUtils;

import java.util.List;

/**
 * RecyclerView adapter for faculty-owned requests in the "My Requests" screen.
 *
 * <p>Design note: Adapter/ViewHolder pattern with intent-revealing callbacks for edit and cancel
 * operations, keeping business decisions outside the adapter.</p>
 *
 * <p>Outstanding issues: row updates are not diffed and all item buttons remain active during
 * in-flight writes, which can trigger duplicate taps.</p>
 */
public class FacultyRequestAdapter extends RecyclerView.Adapter<FacultyRequestAdapter.ViewHolder> {

    /**
     * Callback invoked when the edit button is tapped for a request.
     */
    public interface OnEditClickListener {
        void onEdit(VisitorRequest request);
    }

    /**
     * Callback invoked when the cancel button is tapped for a request.
     */
    public interface OnCancelClickListener {
        void onCancel(VisitorRequest request);
    }

    private List<VisitorRequest> requests;
    private final OnEditClickListener onEditClick;
    private final OnCancelClickListener onCancelClick;

    /**
     * @param requests      initial list of faculty requests
     * @param onEditClick   callback for edit-button taps
     * @param onCancelClick callback for cancel-button taps
     */
    public FacultyRequestAdapter(List<VisitorRequest> requests,
                                  OnEditClickListener onEditClick,
                                  OnCancelClickListener onCancelClick) {
        this.requests = requests;
        this.onEditClick = onEditClick;
        this.onCancelClick = onCancelClick;
    }

    /** ViewHolder wrapping view-binding references for a single faculty request row. */
    public static class ViewHolder extends RecyclerView.ViewHolder {
        final ItemFacultyRequestBinding binding;

        public ViewHolder(@NonNull ItemFacultyRequestBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemFacultyRequestBinding binding = ItemFacultyRequestBinding
                .inflate(LayoutInflater.from(parent.getContext()), parent, false);
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        VisitorRequest request = requests.get(position);
        holder.binding.tvGuestName.setText(request.getGuestName());
        holder.binding.tvVisitInfo.setText(UiFormatUtils.formatFacultyVisitInfo(request));
        holder.binding.chipStatus.setText(RequestStatus.normalize(request.getStatus()).toUpperCase());

        holder.binding.btnEdit.setOnClickListener(v -> onEditClick.onEdit(request));
        holder.binding.btnCancel.setOnClickListener(v -> onCancelClick.onCancel(request));
    }

    @Override
    public int getItemCount() {
        return requests.size();
    }

    /** Replaces the adapter data with a new snapshot from Firestore. */
    public void updateData(List<VisitorRequest> newRequests) {
        requests = newRequests;
        notifyDataSetChanged();
    }
}
