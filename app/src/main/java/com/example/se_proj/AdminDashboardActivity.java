package com.example.se_proj;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.se_proj.adapters.VisitorRequestAdapter;
import com.example.se_proj.databinding.ActivityAdminDashboardBinding;
import com.example.se_proj.models.AuditLog;
import com.example.se_proj.models.VisitorRequest;
import com.example.se_proj.rules.RequestStatus;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Admin control panel for approving/rejecting visitor requests and monitoring live summary stats.
 *
 * <p>Design note: controller-style Activity that binds Firestore listeners to UI widgets and emits
 * audit events for admin actions.</p>
 *
 * <p>Outstanding issues: status changes and audit-log writes are separate operations (not atomic),
 * so partial failure can leave request state and audit trail temporarily inconsistent.</p>
 */
public class AdminDashboardActivity extends AppCompatActivity {

    private ActivityAdminDashboardBinding binding;
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private VisitorRequestAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityAdminDashboardBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.toolbar.setNavigationOnClickListener(v -> finish());
        binding.toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_logout) {
                FirebaseAuth.getInstance().signOut();
                Intent intent = new Intent(this, LoginActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
                finish();
                return true;
            }
            return false;
        });

        setupRecyclerView();
        setupSummaryStats();
        ensureParkingDocument();
        fetchPendingRequests();

        binding.btnViewAudit.setOnClickListener(v ->
                startActivity(new Intent(this, AdminAuditActivity.class)));
    }

    private void setupRecyclerView() {
        adapter = new VisitorRequestAdapter(
                new ArrayList<>(),
                request -> handleApprove(request),
                request -> handleReject(request)
        );
        binding.rvRequests.setLayoutManager(new LinearLayoutManager(this));
        binding.rvRequests.setAdapter(adapter);
    }

    private void setupSummaryStats() {
        db.collection("visitor_requests")
                .whereEqualTo("onCampus", true)
                .addSnapshotListener((snapshots, e) -> {
                    int count = snapshots != null ? snapshots.size() : 0;
                    binding.tvActiveGuests.setText(String.valueOf(count));
                });

        db.collection("system_metadata").document("parking_status")
                .addSnapshotListener((snapshot, e) -> {
                    if (snapshot != null && snapshot.exists()) {
                        long occupancy = snapshot.getLong("currentOccupancy") != null
                                ? snapshot.getLong("currentOccupancy") : 0;
                        long capacity = snapshot.getLong("maxCapacity") != null
                                ? snapshot.getLong("maxCapacity") : 200;
                        binding.tvParkingStatus.setText(occupancy + "/" + capacity);
                    }
                });
    }

    private void fetchPendingRequests() {
        db.collection("visitor_requests")
                .whereIn("status", Arrays.asList(RequestStatus.PENDING, RequestStatus.PENDING_ADHOC))
                .addSnapshotListener((snapshots, e) -> {
                    if (e != null) {
                        Log.w("AdminDashboard", "Listen failed.", e);
                        return;
                    }

                    List<VisitorRequest> pendingList = new ArrayList<>();
                    if (snapshots != null) {
                        for (DocumentSnapshot doc : snapshots.getDocuments()) {
                            VisitorRequest request = doc.toObject(VisitorRequest.class);
                            if (request != null) {
                                // Explicitly set the requestId from the document ID
                                pendingList.add(request.withRequestId(doc.getId()));
                            }
                        }
                    }
                    adapter.updateData(pendingList);
                });
    }

    private void handleApprove(VisitorRequest request) {
        updateRequestStatus(request, RequestStatus.APPROVED);
    }

    private void handleReject(VisitorRequest request) {
        updateRequestStatus(request, RequestStatus.REJECTED);
    }

    private void updateRequestStatus(VisitorRequest request, String newStatus) {
        String requestId = request.getRequestId();
        if (requestId.isEmpty()) {
            Toast.makeText(this, "Error: Request ID is missing from document",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        db.collection("visitor_requests").document(requestId)
                .update("status", newStatus)
                .addOnSuccessListener(unused -> {
                    logAdminAction(request, newStatus.toUpperCase());
                    String display = newStatus.isEmpty() ? newStatus
                            : Character.toUpperCase(newStatus.charAt(0)) + newStatus.substring(1);
                    Toast.makeText(this, "Request " + display, Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(error -> {
                    Log.e("AdminDashboard", "Failed to update ID: " + requestId, error);
                    Toast.makeText(this, "Firebase Error: " + error.getMessage(),
                            Toast.LENGTH_LONG).show();
                });
    }

    private void logAdminAction(VisitorRequest request, String action) {
        String uid = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid() : "";
        AuditLog log = new AuditLog(
                "",
                request.getGuestName(),
                request.getGuestCNIC(),
                request.getHostId(),
                "ADMIN_" + action,
                "Action taken by Security Admin",
                uid,
                Timestamp.now()
        );
        db.collection("access_logs").add(log)
                .addOnFailureListener(e ->
                        Log.e("AdminDashboard", "Failed to write audit log", e));
    }

    private void ensureParkingDocument() {
        com.google.firebase.firestore.DocumentReference docRef =
                db.collection("system_metadata").document("parking_status");
        docRef.get().addOnSuccessListener(snapshot -> {
            if (!snapshot.exists()) {
                Map<String, Object> data = new HashMap<>();
                data.put("currentOccupancy", 0L);
                data.put("maxCapacity", 200L);
                docRef.set(data);
            }
        });
    }
}
