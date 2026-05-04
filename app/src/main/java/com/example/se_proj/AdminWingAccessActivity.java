package com.example.se_proj;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.se_proj.adapters.WingAccessPermissionAdapter;
import com.example.se_proj.adapters.WingAccessRequestAdapter;
import com.example.se_proj.adapters.ZoneAccessLogAdapter;
import com.example.se_proj.databinding.ActivityAdminWingAccessBinding;
import com.example.se_proj.models.WingAccessPermission;
import com.example.se_proj.models.WingAccessRequest;
import com.example.se_proj.models.ZoneAccessLog;
import com.google.android.material.tabs.TabLayout;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * Admin screen for managing wing/lab access.
 *
 * <p>Three tabs:
 * <ul>
 *   <li><b>Pending</b> — approve or reject queued wing access requests.</li>
 *   <li><b>Permissions</b> — view active permissions; revoke any of them.</li>
 *   <li><b>Logs</b> — read-only list of every access attempt logged by the wing scanner.</li>
 * </ul>
 * </p>
 */
public class AdminWingAccessActivity extends AppCompatActivity {

    private static final String TAG = "AdminWingAccess";

    private ActivityAdminWingAccessBinding binding;
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    private WingAccessRequestAdapter requestAdapter;
    private WingAccessPermissionAdapter permissionAdapter;
    private ZoneAccessLogAdapter logAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityAdminWingAccessBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.toolbar.setNavigationOnClickListener(v -> finish());

        setupRecyclerViews();
        setupTabLayout();
        loadPendingRequests();
    }

    private void setupRecyclerViews() {
        requestAdapter = new WingAccessRequestAdapter(
                new ArrayList<>(),
                this::showApprovalDialog,
                this::confirmRejectRequest);
        binding.rvPendingRequests.setLayoutManager(new LinearLayoutManager(this));
        binding.rvPendingRequests.setAdapter(requestAdapter);

        permissionAdapter = new WingAccessPermissionAdapter(
                new ArrayList<>(),
                this::confirmRevokePermission);
        binding.rvPermissions.setLayoutManager(new LinearLayoutManager(this));
        binding.rvPermissions.setAdapter(permissionAdapter);

        logAdapter = new ZoneAccessLogAdapter(new ArrayList<>());
        binding.rvZoneLogs.setLayoutManager(new LinearLayoutManager(this));
        binding.rvZoneLogs.setAdapter(logAdapter);
    }

    private void setupTabLayout() {
        binding.tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                int pos = tab.getPosition();
                binding.layoutPending.setVisibility(pos == 0 ? View.VISIBLE : View.GONE);
                binding.layoutPermissions.setVisibility(pos == 1 ? View.VISIBLE : View.GONE);
                binding.layoutLogs.setVisibility(pos == 2 ? View.VISIBLE : View.GONE);

                if (pos == 0) loadPendingRequests();
                else if (pos == 1) loadActivePermissions();
                else loadZoneLogs();
            }

            @Override public void onTabUnselected(TabLayout.Tab tab) {}
            @Override public void onTabReselected(TabLayout.Tab tab) {}
        });
    }

    // -------------------------------------------------------------------------
    // Data loading
    // -------------------------------------------------------------------------

    private void loadPendingRequests() {
        db.collection("wing_access_requests")
                .whereEqualTo("status", "pending")
                .addSnapshotListener((snapshots, e) -> {
                    if (e != null) { Log.w(TAG, "Listen failed", e); return; }
                    List<WingAccessRequest> list = new ArrayList<>();
                    if (snapshots != null) {
                        for (DocumentSnapshot doc : snapshots.getDocuments()) {
                            WingAccessRequest r = doc.toObject(WingAccessRequest.class);
                            if (r != null) list.add(r);
                        }
                    }
                    requestAdapter.updateData(list);
                    binding.tvPendingEmpty.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
                    binding.rvPendingRequests.setVisibility(list.isEmpty() ? View.GONE : View.VISIBLE);
                });
    }

    private void loadActivePermissions() {
        db.collection("wing_access_permissions")
                .whereEqualTo("active", true)
                .addSnapshotListener((snapshots, e) -> {
                    if (e != null) { Log.w(TAG, "Listen failed", e); return; }
                    List<WingAccessPermission> list = new ArrayList<>();
                    if (snapshots != null) {
                        for (DocumentSnapshot doc : snapshots.getDocuments()) {
                            WingAccessPermission p = doc.toObject(WingAccessPermission.class);
                            if (p != null) list.add(p);
                        }
                    }
                    permissionAdapter.updateData(list);
                    binding.tvPermissionsEmpty.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
                    binding.rvPermissions.setVisibility(list.isEmpty() ? View.GONE : View.VISIBLE);
                });
    }

    private void loadZoneLogs() {
        db.collection("zone_access_logs")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(100)
                .addSnapshotListener((snapshots, e) -> {
                    if (e != null) { Log.w(TAG, "Listen failed", e); return; }
                    List<ZoneAccessLog> list = new ArrayList<>();
                    if (snapshots != null) {
                        for (DocumentSnapshot doc : snapshots.getDocuments()) {
                            ZoneAccessLog log = doc.toObject(ZoneAccessLog.class);
                            if (log != null) list.add(log);
                        }
                    }
                    logAdapter.updateData(list);
                    binding.tvLogsEmpty.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
                    binding.rvZoneLogs.setVisibility(list.isEmpty() ? View.GONE : View.VISIBLE);
                });
    }

    // -------------------------------------------------------------------------
    // Approval flow
    // -------------------------------------------------------------------------

    private void showApprovalDialog(WingAccessRequest request) {
        new AlertDialog.Builder(this)
                .setTitle("Approve Wing Access")
                .setMessage("Grant " + request.getStudentName()
                        + " (" + request.getStudentRollNo() + ")"
                        + " access to " + request.getWing() + "?")
                .setPositiveButton("Lifetime Access",
                        (d, w) -> approveRequest(request, true, null))
                .setNeutralButton("Set Expiry Date",
                        (d, w) -> showExpiryDatePicker(request))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showExpiryDatePicker(WingAccessRequest request) {
        Calendar cal = Calendar.getInstance();
        new DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
            String expiry = String.format(Locale.getDefault(), "%02d/%02d/%d",
                    dayOfMonth, month + 1, year);
            approveRequest(request, false, expiry);
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH),
                cal.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void approveRequest(WingAccessRequest request, boolean lifetime, String expiryDate) {
        String uid = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid() : "";

        WingAccessPermission permission = new WingAccessPermission(
                "", request.getStudentRollNo(), request.getStudentName(),
                request.getWing(), uid, lifetime, expiryDate, Timestamp.now(), true
        );

        db.collection("wing_access_permissions").add(permission)
                .addOnSuccessListener(docRef ->
                        db.collection("wing_access_requests")
                                .document(request.getRequestId())
                                .update("status", "approved")
                                .addOnSuccessListener(u ->
                                        Toast.makeText(this, "Access granted",
                                                Toast.LENGTH_SHORT).show())
                                .addOnFailureListener(e ->
                                        Log.e(TAG, "Failed to update request status", e)))
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to create permission", e);
                    Toast.makeText(this, "Failed to approve: " + e.getMessage(),
                            Toast.LENGTH_SHORT).show();
                });
    }

    // -------------------------------------------------------------------------
    // Reject flow
    // -------------------------------------------------------------------------

    private void confirmRejectRequest(WingAccessRequest request) {
        new AlertDialog.Builder(this)
                .setTitle("Reject Request")
                .setMessage("Reject wing access request from "
                        + request.getStudentName() + " for " + request.getWing() + "?")
                .setPositiveButton("Reject", (d, w) ->
                        db.collection("wing_access_requests")
                                .document(request.getRequestId())
                                .update("status", "rejected")
                                .addOnSuccessListener(u ->
                                        Toast.makeText(this, "Request rejected",
                                                Toast.LENGTH_SHORT).show())
                                .addOnFailureListener(e ->
                                        Toast.makeText(this, "Failed: " + e.getMessage(),
                                                Toast.LENGTH_SHORT).show()))
                .setNegativeButton("Cancel", null)
                .show();
    }

    // -------------------------------------------------------------------------
    // Revoke flow
    // -------------------------------------------------------------------------

    private void confirmRevokePermission(WingAccessPermission permission) {
        new AlertDialog.Builder(this)
                .setTitle("Revoke Permission")
                .setMessage("Revoke " + permission.getStudentName()
                        + "'s access to " + permission.getWing() + "?")
                .setPositiveButton("Revoke", (d, w) ->
                        db.collection("wing_access_permissions")
                                .document(permission.getPermissionId())
                                .update("active", false)
                                .addOnSuccessListener(u ->
                                        Toast.makeText(this, "Permission revoked",
                                                Toast.LENGTH_SHORT).show())
                                .addOnFailureListener(e ->
                                        Toast.makeText(this, "Failed: " + e.getMessage(),
                                                Toast.LENGTH_SHORT).show()))
                .setNegativeButton("Cancel", null)
                .show();
    }
}
