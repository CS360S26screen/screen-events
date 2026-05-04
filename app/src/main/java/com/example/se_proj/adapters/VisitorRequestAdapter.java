package com.example.se_proj.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.se_proj.R;
import com.example.se_proj.models.VisitorRequest;
import com.example.se_proj.rules.RequestStatus;
import com.example.se_proj.rules.UiFormatUtils;

import java.util.List;

/**
 * RecyclerView adapter for pending visitor requests shown on the admin approval screen.
 *
 * <p>Design note: classic Adapter/ViewHolder pattern with callback injection for approve/reject
 * actions so decision handling stays in the hosting Activity.</p>
 *
 * <p>Outstanding issues: uses {@code notifyDataSetChanged()} for all updates; consider DiffUtil
 * for smoother animations and lower bind cost on large lists.</p>
 *
 * <p><b>Design pattern:</b> RecyclerView Adapter/ViewHolder.
 * The adapter maps domain models to row views and delegates user actions
 * back to the owning Activity through callback interfaces.</p>
 */
public class VisitorRequestAdapter extends RecyclerView.Adapter<VisitorRequestAdapter.ViewHolder> {

    /**
     * Callback invoked when the approve button is tapped for a request.
     */
    public interface OnApproveClickListener {
        void onApprove(VisitorRequest request);
    }

    /**
     * Callback invoked when the reject button is tapped for a request.
     */
    public interface OnRejectClickListener {
        void onReject(VisitorRequest request);
    }

    private List<VisitorRequest> requests;
    private final OnApproveClickListener onApproveClick;
    private final OnRejectClickListener onRejectClick;

    /**
     * @param requests       initial list of pending requests
     * @param onApproveClick callback for approve-button taps
     * @param onRejectClick  callback for reject-button taps
     */
    public VisitorRequestAdapter(List<VisitorRequest> requests,
                                  OnApproveClickListener onApproveClick,
                                  OnRejectClickListener onRejectClick) {
        this.requests = requests;
        this.onApproveClick = onApproveClick;
        this.onRejectClick = onRejectClick;
    }

    /** Holds references to each request row's UI controls. */
    public static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView tvGuestName;
        final TextView tvHostInfo;
        final TextView tvTimeWindow;
        final TextView chipStatus;
        final Button btnApprove;
        final Button btnReject;
        /**
         * Creates a new adapter component instance.
         * @param view the value for {@code view}
         */
        public ViewHolder(@NonNull View view) {
            super(view);
            tvGuestName = view.findViewById(R.id.tvGuestName);
            tvHostInfo = view.findViewById(R.id.tvHostInfo);
            tvTimeWindow = view.findViewById(R.id.tvTimeWindow);
            chipStatus = view.findViewById(R.id.chipStatus);
            btnApprove = view.findViewById(R.id.btnApprove);
            btnReject = view.findViewById(R.id.btnReject);
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
                .inflate(R.layout.item_visitor_request, parent, false);
        return new ViewHolder(view);
    }
    /**
     * Binds the model at the given position into the supplied ViewHolder.
     * @param holder the ViewHolder whose views should be populated
     * @param position the adapter position to bind
     */
    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        VisitorRequest request = requests.get(position);
        holder.tvGuestName.setText(request.getGuestName());
        holder.tvHostInfo.setText("Host: " + request.getHostId());
        holder.tvTimeWindow.setText(UiFormatUtils.formatFacultyVisitInfo(request));
        holder.chipStatus.setText(RequestStatus.normalize(request.getStatus()).toUpperCase());

        holder.btnApprove.setOnClickListener(v -> onApproveClick.onApprove(request));
        holder.btnReject.setOnClickListener(v -> onRejectClick.onReject(request));
    }
    /**
     * Returns the number of rows currently managed by this adapter.
     * @return the current number of rows
     */
    @Override
    public int getItemCount() {
        return requests.size();
    }

    /** Replaces the current data set and refreshes the list rendering. */
    public void updateData(List<VisitorRequest> newRequests) {
        requests = newRequests;
        notifyDataSetChanged();
    }
}
