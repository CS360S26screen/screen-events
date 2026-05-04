package com.example.se_proj.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.se_proj.R;
import com.example.se_proj.models.WingAccessPermission;

import java.util.List;

/**
 * Adapter for active wing access permissions shown on the admin wing access management screen.
 *
 * <p><b>Design pattern:</b> RecyclerView Adapter/ViewHolder.
 * The adapter maps domain models to row views and delegates user actions
 * back to the owning Activity through callback interfaces.</p>
 */
public class WingAccessPermissionAdapter
        extends RecyclerView.Adapter<WingAccessPermissionAdapter.ViewHolder> {

    /**
     * Callback used when an admin revokes a wing access permission.
     */
    public interface OnRevokeClickListener {
        /**
         * Handles revocation for the selected permission.
         *
         * @param permission wing access permission selected by the user.
         */
        void onRevoke(WingAccessPermission permission);
    }

    private List<WingAccessPermission> permissions;
    private final OnRevokeClickListener onRevokeClick;
    /**
     * Creates a new adapter component instance.
     * @param permissions the initial data set to display
     * @param onRevokeClick the value for {@code onRevokeClick}
     */
    public WingAccessPermissionAdapter(List<WingAccessPermission> permissions,
                                        OnRevokeClickListener onRevokeClick) {
        this.permissions = permissions;
        this.onRevokeClick = onRevokeClick;
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView tvPermStudentName;
        final TextView tvPermRoll;
        final TextView tvPermWing;
        final TextView tvPermExpiry;
        final Button btnRevokePermission;
        /**
         * Creates a new adapter component instance.
         * @param view the value for {@code view}
         */
        public ViewHolder(@NonNull View view) {
            super(view);
            tvPermStudentName = view.findViewById(R.id.tvPermStudentName);
            tvPermRoll = view.findViewById(R.id.tvPermRoll);
            tvPermWing = view.findViewById(R.id.tvPermWing);
            tvPermExpiry = view.findViewById(R.id.tvPermExpiry);
            btnRevokePermission = view.findViewById(R.id.btnRevokePermission);
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
                .inflate(R.layout.item_wing_access_permission, parent, false);
        return new ViewHolder(view);
    }
    /**
     * Binds the model at the given position into the supplied ViewHolder.
     * @param holder the ViewHolder whose views should be populated
     * @param position the adapter position to bind
     */
    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        WingAccessPermission perm = permissions.get(position);
        holder.tvPermStudentName.setText(perm.getStudentName());
        holder.tvPermRoll.setText(perm.getStudentRollNo());
        holder.tvPermWing.setText(perm.getWing());

        if (perm.isLifetime()) {
            holder.tvPermExpiry.setText("Lifetime Access");
        } else {
            holder.tvPermExpiry.setText("Expires: " + perm.getExpiryDate());
        }

        holder.btnRevokePermission.setOnClickListener(v -> onRevokeClick.onRevoke(perm));
    }
    /**
     * Returns the number of rows currently managed by this adapter.
     * @return the current number of rows
     */
    @Override
    public int getItemCount() {
        return permissions.size();
    }
    /**
     * Replaces the adapter data set and refreshes the visible list.
     * @param newPermissions the replacement data set to display
     */
    public void updateData(List<WingAccessPermission> newPermissions) {
        permissions = newPermissions;
        notifyDataSetChanged();
    }
}
