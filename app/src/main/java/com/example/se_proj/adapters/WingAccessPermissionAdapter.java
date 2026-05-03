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
 */
public class WingAccessPermissionAdapter
        extends RecyclerView.Adapter<WingAccessPermissionAdapter.ViewHolder> {

    public interface OnRevokeClickListener {
        void onRevoke(WingAccessPermission permission);
    }

    private List<WingAccessPermission> permissions;
    private final OnRevokeClickListener onRevokeClick;

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

        public ViewHolder(@NonNull View view) {
            super(view);
            tvPermStudentName = view.findViewById(R.id.tvPermStudentName);
            tvPermRoll = view.findViewById(R.id.tvPermRoll);
            tvPermWing = view.findViewById(R.id.tvPermWing);
            tvPermExpiry = view.findViewById(R.id.tvPermExpiry);
            btnRevokePermission = view.findViewById(R.id.btnRevokePermission);
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_wing_access_permission, parent, false);
        return new ViewHolder(view);
    }

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

    @Override
    public int getItemCount() {
        return permissions.size();
    }

    public void updateData(List<WingAccessPermission> newPermissions) {
        permissions = newPermissions;
        notifyDataSetChanged();
    }
}
