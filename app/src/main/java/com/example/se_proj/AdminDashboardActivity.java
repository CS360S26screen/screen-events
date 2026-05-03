package com.example.se_proj;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.se_proj.adapters.CarRegistrationRequestAdapter;
import com.example.se_proj.adapters.VisitorRequestAdapter;
import com.example.se_proj.databinding.ActivityAdminDashboardBinding;
import com.example.se_proj.models.AuditLog;
import com.example.se_proj.models.CarRegistrationRequest;
import com.example.se_proj.models.RegisteredVehicle;
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
 * Admin control panel for approving/rejecting visitor requests, managing car registration
 * requests, and monitoring live summary stats.
 *
 * <p>Car registration flow: student/faculty submit a {@link CarRegistrationRequest}; admin
 * approves here which creates a {@link RegisteredVehicle} document, or rejects which closes
 * the request without any vehicle record.</p>
 *
 * <p>Outstanding: status changes and audit-log writes are separate Firestore operations
 * (not atomic), so partial failure can leave state temporarily inconsistent.</p>
 */
public class AdminDashboardActivity extends AppCompatActivity {

    private ActivityAdminDashboardBinding binding;
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private VisitorRequestAdapter adapter;
    private CarRegistrationRequestAdapter carAdapter;

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

        setupVisitorRecyclerView();
        setupCarRequestRecyclerView();
        setupSummaryStats();
        ensureParkingDocument();
        fetchPendingRequests();
        fetchPendingCarRequests();

        binding.btnViewAudit.setOnClickListener(v ->
                startActivity(new Intent(this, AdminAuditActivity.class)));

        binding.btnWingAccess.setOnClickListener(v ->
                startActivity(new Intent(this, AdminWingAccessActivity.class)));
    }

    // -------------------------------------------------------------------------
    // RecyclerView setup
    // -------------------------------------------------------------------------

    private void setupVisitorRecyclerView() {
        adapter = new VisitorRequestAdapter(
                new ArrayList<>(),
                request -> handleApprove(request),
                request -> handleReject(request)
        );
        binding.rvRequests.setLayoutManager(new LinearLayoutManager(this));
        binding.rvRequests.setAdapter(adapter);
    }

    private void setupCarRequestRecyclerView() {
        carAdapter = new CarRegistrationRequestAdapter(
                new ArrayList<>(),
                request -> approveCarRequest(request),
                request -> rejectCarRequest(request)
        );
        binding.rvCarRequests.setLayoutManager(new LinearLayoutManager(this));
        binding.rvCarRequests.setAdapter(carAdapter);
    }

    // -------------------------------------------------------------------------
    // Summary stats
    // -------------------------------------------------------------------------

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

    // -------------------------------------------------------------------------
    // Visitor requests
    // -------------------------------------------------------------------------

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
            Toast.makeText(this, "Error: Request ID is missing", Toast.LENGTH_SHORT).show();
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
                "", request.getGuestName(), request.getGuestCNIC(), request.getHostId(),
                "ADMIN_" + action, "Action taken by Security Admin", uid, Timestamp.now()
        );
        db.collection("access_logs").add(log)
                .addOnFailureListener(e ->
                        Log.e("AdminDashboard", "Failed to write audit log", e));
    }

    // -------------------------------------------------------------------------
    // Car registration requests
    // -------------------------------------------------------------------------

    private void fetchPendingCarRequests() {
        db.collection("car_registration_requests")
                .whereEqualTo("status", "pending")
                .addSnapshotListener((snapshots, e) -> {
                    if (e != null) {
                        Log.w("AdminDashboard", "Car requests listen failed.", e);
                        return;
                    }

                    List<CarRegistrationRequest> list = new ArrayList<>();
                    if (snapshots != null) {
                        for (DocumentSnapshot doc : snapshots.getDocuments()) {
                            CarRegistrationRequest r = doc.toObject(CarRegistrationRequest.class);
                            if (r != null) list.add(r);
                        }
                    }
                    carAdapter.updateData(list);
                    binding.tvCarRequestsEmpty.setVisibility(
                            list.isEmpty() ? View.VISIBLE : View.GONE);
                    binding.rvCarRequests.setVisibility(
                            list.isEmpty() ? View.GONE : View.VISIBLE);
                });
    }

    private void approveCarRequest(CarRegistrationRequest request) {
        // Create RegisteredVehicle document
        RegisteredVehicle vehicle = new RegisteredVehicle(
                "", request.getOwnerRollNo(), request.getOwnerName(),
                request.getStudentUid(), request.getLicensePlate(),
                request.getCarModel(), false, null, null
        );

        db.collection("registered_vehicles").add(vehicle)
                .addOnSuccessListener(docRef ->
                        db.collection("car_registration_requests")
                                .document(request.getRequestId())
                                .update("status", "approved")
                                .addOnSuccessListener(unused ->
                                        Toast.makeText(this, "Car approved and registered",
                                                Toast.LENGTH_SHORT).show())
                                .addOnFailureListener(e ->
                                        Log.e("AdminDashboard", "Failed to mark approved", e)))
                .addOnFailureListener(e -> {
                    Log.e("AdminDashboard", "Failed to create vehicle", e);
                    Toast.makeText(this, "Failed to approve: " + e.getMessage(),
                            Toast.LENGTH_LONG).show();
                });
    }

    private void rejectCarRequest(CarRegistrationRequest request) {
        db.collection("car_registration_requests")
                .document(request.getRequestId())
                .update("status", "rejected")
                .addOnSuccessListener(unused ->
                        Toast.makeText(this, "Car request rejected", Toast.LENGTH_SHORT).show())
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Failed: " + e.getMessage(),
                                Toast.LENGTH_SHORT).show());
    }

    // -------------------------------------------------------------------------
    // Parking document bootstrap
    // -------------------------------------------------------------------------

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
